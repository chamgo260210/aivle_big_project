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

    private final RestClient client;
    private final AiServerProperties properties;
    private final ObjectMapper mapper;

    public InternalAiExecutionClient(@Qualifier("aiServerRestClient") RestClient client,
                                     AiServerProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
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
            byte[] responseBytes = client.post().uri("/internal/v1/ai/executions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.internalApiKey())
                .header("X-Correlation-Id", run.getCorrelationId())
                .body(requestBytes).retrieve().body(byte[].class);
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            return parseSuccess(responseBytes);
        } catch (RestClientResponseException responseFailure) {
            byte[] responseBytes = responseFailure.getResponseBodyAsByteArray();
            enforceSize(responseBytes, "RESPONSE_BYTES_EXCEEDED");
            throw parseFailure(responseBytes);
        }
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
            if (!INTERNAL_CODES.contains(code)) return invalid("RESULT_DOMAIN_INVARIANT_VIOLATION");
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
