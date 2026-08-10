package com.aivle.backend.taskrun.integration;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskRun;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class InternalAiExecutionClient {
    public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final Set<String> SUCCESS_FIELDS = Set.of(
        "contractVersion", "taskType", "taskSchemaVersion", "taskRunId", "taskAttemptId",
        "correlationId", "canonicalInputHash", "resultSchemaVersion", "result", "warnings",
        "provenance", "usage"
    );
    private static final Set<String> INTERNAL_CODES = Set.of(
        "INVALID_REQUEST", "UNAUTHORIZED_INTERNAL_CALL", "UNSUPPORTED_CONTRACT_VERSION",
        "UNSUPPORTED_TASK_TYPE", "UNSUPPORTED_TASK_SCHEMA_VERSION", "PAYLOAD_TOO_LARGE",
        "DEADLINE_EXCEEDED", "DEPENDENCY_UNAVAILABLE", "RATE_LIMITED", "EXECUTION_FAILED",
        "RESULT_SCHEMA_INVALID", "INTERNAL_ERROR"
    );
    private static final Map<String, Set<String>> ERROR_REASONS = Map.ofEntries(
        Map.entry("INVALID_REQUEST", Set.of("JSON_PARSE_FAILED", "HEADER_BODY_CORRELATION_MISMATCH",
            "UNKNOWN_FIELD", "FIELD_CONSTRAINT_VIOLATION", "HASH_MISMATCH", "CHUNK_SEQUENCE_INVALID",
            "REFERENCE_RESOLUTION_FAILED", "LEGAL_INPUT_CONTRACT_INCOMPLETE", "LEGAL_MODE_INVALID",
            "LEGAL_RERUN_CATEGORIES_INVALID", "LEGAL_CONFIRMED_FACTS_INVALID",
            "LEGAL_REGISTRY_VERSION_MISMATCH", "CONCEPT_LEGAL_VALIDATION_MODE_INVALID")),
        Map.entry("UNAUTHORIZED_INTERNAL_CALL", Set.of("SERVICE_TOKEN_MISSING", "SERVICE_TOKEN_INVALID",
            "INTERNAL_PRINCIPAL_FORBIDDEN")),
        Map.entry("UNSUPPORTED_CONTRACT_VERSION", Set.of("CONTRACT_VERSION_UNSUPPORTED")),
        Map.entry("UNSUPPORTED_TASK_TYPE", Set.of("TASK_TYPE_UNSUPPORTED")),
        Map.entry("UNSUPPORTED_TASK_SCHEMA_VERSION", Set.of("TASK_SCHEMA_VERSION_UNSUPPORTED")),
        Map.entry("PAYLOAD_TOO_LARGE", Set.of("REQUEST_BYTES_EXCEEDED", "RESPONSE_BYTES_EXCEEDED",
            "TEXT_CONTENT_COUNT_EXCEEDED", "CHUNK_COUNT_EXCEEDED", "CHUNK_CHARACTERS_EXCEEDED",
            "TOTAL_CHARACTERS_EXCEEDED", "TASK_COLLECTION_LIMIT_EXCEEDED")),
        Map.entry("DEADLINE_EXCEEDED", Set.of("REQUEST_DEADLINE_EXCEEDED")),
        Map.entry("DEPENDENCY_UNAVAILABLE", Set.of("MODEL_DEPENDENCY_UNAVAILABLE", "MCP_DEPENDENCY_UNAVAILABLE",
            "LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", "LEGAL_CONFIGURATION_INVALID",
            "MOLEG_AUTHENTICATION_FAILED", "MOLEG_REQUEST_REJECTED", "MOLEG_RESPONSE_INVALID",
            "MOLEG_DEPENDENCY_UNAVAILABLE", "MOLEG_RATE_LIMITED")),
        Map.entry("RATE_LIMITED", Set.of("DEPENDENCY_RATE_LIMITED")),
        Map.entry("EXECUTION_FAILED", Set.of("TRANSIENT_EXECUTION_FAILURE", "PERMANENT_EXECUTION_FAILURE",
            "SAFETY_POLICY_BLOCKED")),
        Map.entry("RESULT_SCHEMA_INVALID", Set.of("RESULT_UNKNOWN_FIELD", "RESULT_FIELD_CONSTRAINT_VIOLATION",
            "RESULT_REFERENCE_INVALID", "RESULT_DOMAIN_INVARIANT_VIOLATION", "AI_RESULT_INVALID",
            "LEGAL_ROUTING_CONTRACT_INVALID", "LEGAL_SCREENING_CONTRACT_INVALID",
            "LEGAL_CITATION_COVERAGE_INVALID", "LEGAL_SCREENING_FIELD_INVALID", "LEGAL_SOURCE_CONTRACT_INVALID",
            "CONCEPT_LEGAL_VALIDATION_INVALID", "CONCEPT_LEGAL_VALIDATION_CANDIDATE_KEYS_INVALID")),
        Map.entry("INTERNAL_ERROR", Set.of("UNEXPECTED_INTERNAL_ERROR"))
    );
    private static final Set<String> RETRYABLE_REASONS = Set.of(
        "REQUEST_DEADLINE_EXCEEDED", "MODEL_DEPENDENCY_UNAVAILABLE", "MCP_DEPENDENCY_UNAVAILABLE",
        "LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE", "MOLEG_DEPENDENCY_UNAVAILABLE", "MOLEG_RATE_LIMITED",
        "DEPENDENCY_RATE_LIMITED", "TRANSIENT_EXECUTION_FAILURE", "UNEXPECTED_INTERNAL_ERROR"
    );

    private final RestClient client;
    /** 오래 걸리는 작업 전용. read timeout 만 다르다. */
    private final RestClient longClient;
    private final AiServerProperties properties;
    private final ObjectMapper mapper;

    /** 짧은 클라이언트 하나만 주는 형태 — 테스트용. 긴 호출도 같은 것을 쓴다. */
    public InternalAiExecutionClient(RestClient client, AiServerProperties properties,
                                     ObjectMapper mapper) {
        this(client, client, properties, mapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InternalAiExecutionClient(@Qualifier("aiServerRestClient") RestClient client,
                                     @Qualifier("aiServerLongRestClient") RestClient longClient,
                                     AiServerProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.longClient = longClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * 어느 클라이언트로 부를지는 <b>작업 종류가 정한다</b>.
     *
     * <p>시장조사는 90~266초 걸린다. 기본 30s 로 부르면 read timeout 이 나고, 그 실패는
     * {@code REQUEST_DEADLINE_EXCEEDED}(retryable) 로 사상돼 <b>자동 재시도가 260초짜리를
     * 다시 태운다</b> — 실패하면서 비용만 배가 된다.
     */
    private RestClient clientFor(TaskRun run) {
        return run.getTaskType() == com.aivle.backend.taskrun.domain.TaskType.MARKET_RESEARCH
            ? longClient : client;
    }

    public ExecutionResponse execute(TaskRun run, String attemptId, LocalDateTime deadline) {
        if (properties.internalApiKey() == null || properties.internalApiKey().isBlank())
            throw new ExecutionFailure("UNAUTHORIZED_INTERNAL_CALL", "SERVICE_TOKEN_MISSING", false);
        JsonNode input = mapper.readTree(run.getInputSnapshot());
        Map<String, Object> body = Map.of(
            "contractVersion", "1.0", "taskType", run.getTaskType().name(),
            "taskSchemaVersion", run.getTaskSchemaVersion(), "taskRunId", run.getId(),
            "taskAttemptId", attemptId, "correlationId", run.getCorrelationId(),
            "deadlineAt", deadline.atOffset(ZoneOffset.UTC).toString(),
            "canonicalInputHash", run.getInputHash(), "locale", run.getLocale(), "input", input
        );
        byte[] requestBytes = mapper.writeValueAsBytes(body);
        enforceSize(requestBytes, "REQUEST_BYTES_EXCEEDED");
        try {
            byte[] responseBytes = clientFor(run).post().uri("/internal/v1/ai/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.internalApiKey())
                .header("X-Correlation-Id", run.getCorrelationId())
                .body(requestBytes).retrieve().body(byte[].class);
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            return validateResponse(run, attemptId, parseSuccess(responseBytes));
        } catch (RestClientResponseException responseFailure) {
            byte[] responseBytes = responseFailure.getResponseBodyAsByteArray();
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            throw parseFailure(responseBytes);
        } catch (ResourceAccessException timeoutOrConnectionFailure) {
            throw new ExecutionFailure(
                "DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", true
            );
        }
    }

    public ExecutionResponse validateResponse(TaskRun run, String attemptId, ExecutionResponse response) {
        if (!run.getContractVersion().equals(response.contractVersion())
            || !run.getTaskType().name().equals(response.taskType())
            || !run.getTaskSchemaVersion().equals(response.taskSchemaVersion())
            || !run.getId().equals(response.taskRunId())
            || !attemptId.equals(response.taskAttemptId())
            || !run.getCorrelationId().equals(response.correlationId())
            || !run.getInputHash().equals(response.canonicalInputHash())
            || !"1.0".equals(response.resultSchemaVersion())
            || response.result() == null) {
            throw invalid("RESULT_DOMAIN_INVARIANT_VIOLATION");
        }
        return response;
    }

    private ExecutionResponse parseSuccess(byte[] raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            if (!root.isObject() || !Set.copyOf(root.propertyNames()).equals(SUCCESS_FIELDS))
                throw invalid("RESULT_UNKNOWN_FIELD");
            JsonNode result = root.get("result");
            JsonNode warnings = root.get("warnings");
            JsonNode provenance = root.get("provenance");
            if (result == null || !result.isObject() || warnings == null || !warnings.isArray()
                || provenance == null || !provenance.isArray()) throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
            return new ExecutionResponse(text(root, "contractVersion"), text(root, "taskType"),
                text(root, "taskSchemaVersion"), text(root, "taskRunId"), text(root, "taskAttemptId"),
                text(root, "correlationId"), text(root, "canonicalInputHash"),
                text(root, "resultSchemaVersion"), result, warnings, provenance, root.get("usage"));
        } catch (ExecutionFailure known) {
            throw known;
        } catch (RuntimeException invalidJson) {
            throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
        }
    }

    private ExecutionFailure parseFailure(byte[] raw) {
        try {
            JsonNode error = mapper.readTree(raw).get("error");
            String code = text(error, "code");
            boolean retryable = error.has("retryable") && error.get("retryable").isBoolean()
                && error.get("retryable").asBoolean();
            JsonNode details = error.get("details");
            String reason = details != null && details.isArray() && !details.isEmpty()
                ? text(details.get(0), "reason") : "UNEXPECTED_INTERNAL_ERROR";
            if (!INTERNAL_CODES.contains(code) || !ERROR_REASONS.getOrDefault(code, Set.of()).contains(reason)
                || RETRYABLE_REASONS.contains(reason) != retryable)
                return invalid("RESULT_DOMAIN_INVARIANT_VIOLATION");
            return new ExecutionFailure(code, reason, retryable);
        } catch (ExecutionFailure known) {
            return known;
        } catch (RuntimeException invalidError) {
            return new ExecutionFailure("INTERNAL_ERROR", "UNEXPECTED_INTERNAL_ERROR", true);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw invalid("RESULT_FIELD_CONSTRAINT_VIOLATION");
        return value.asText();
    }

    private void enforceSize(byte[] bytes, String reason) {
        if (bytes == null || bytes.length > MAX_JSON_BYTES)
            throw new ExecutionFailure("PAYLOAD_TOO_LARGE", reason, false);
    }

    private ExecutionFailure invalid(String reason) {
        return new ExecutionFailure("RESULT_SCHEMA_INVALID", reason, false);
    }

    public static class ExecutionFailure extends RuntimeException {
        private final String code;
        private final String reason;
        private final boolean retryable;

        public ExecutionFailure(String code, String reason, boolean retryable) {
            super(code + ":" + reason);
            this.code = code;
            this.reason = reason;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public String reason() { return reason; }
        public boolean retryable() { return retryable; }
    }

    public record ExecutionResponse(String contractVersion, String taskType, String taskSchemaVersion,
        String taskRunId, String taskAttemptId, String correlationId, String canonicalInputHash,
        String resultSchemaVersion, JsonNode result, JsonNode warnings, JsonNode provenance, JsonNode usage) { }
}
