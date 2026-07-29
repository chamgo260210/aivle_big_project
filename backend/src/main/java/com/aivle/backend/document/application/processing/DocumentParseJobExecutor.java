package com.aivle.backend.document.application.processing;

import com.aivle.backend.common.entity.FileStatus;
import com.aivle.backend.document.parsing.*;
import com.aivle.backend.document.structure.StructuredPlanMapper;
import com.aivle.backend.file.storage.FileStorage;
import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.integration.ai.document.AiClientException;
import com.aivle.backend.job.runner.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import com.aivle.backend.common.entity.JobType;

@Service
@RequiredArgsConstructor
public class DocumentParseJobExecutor implements DocumentJobExecutor {
    private final DocumentJobContextService contextService;
    private final FileStorage fileStorage;
    private final DocumentParser documentParser;
    private final DocumentJobProgressService progressService;
    private final DocumentStructureRequestFactory requestFactory;
    private final AiServiceClient aiServiceClient;
    private final StructuredPlanMapper structuredPlanMapper;
    private final DocumentStructureResultHasher resultHasher;
    private final StructuredPlanPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(JobClaim claim) {
        DocumentJobContext context = contextService.load(claim);
        validateStoredFile(context);
        ParsedDocument parsed = parse(context);
        progressService.parsed(claim, parsed, parseMetadataJson(parsed));

        var aiRequest = requestFactory.create(context, parsed);
        var aiResponse = callAi(aiRequest);
        progressService.aiResponded(claim, aiResponse.providerRequestId());
        var hashedResult = resultHasher.withCanonicalHash(aiResponse.result());
        var mapping = structuredPlanMapper.map(parsed, hashedResult);
        if (!mapping.mappingErrors().isEmpty()) {
            throw JobProcessingException.nonRetryable(
                "STRUCTURED_RESULT_INVALID",
                "AI 구조화 결과 검증에 실패했습니다.",
                null
            );
        }
        persistenceService.complete(claim, parsed, hashedResult, mapping);
    }

    private void validateStoredFile(DocumentJobContext context) {
        if (context.fileStatus() != FileStatus.AVAILABLE || context.encrypted()) {
            throw JobProcessingException.nonRetryable(
                "STORED_FILE_UNAVAILABLE",
                "저장된 문서를 처리할 수 없습니다.",
                null
            );
        }
    }

    private ParsedDocument parse(DocumentJobContext context) {
        DocumentParseRequest request = new DocumentParseRequest(
            context.originalFileName(),
            context.mimeType(),
            context.sizeBytes(),
            Map.of("checksumSha256", context.checksumSha256())
        );
        if (!documentParser.supports(request)) {
            throw JobProcessingException.nonRetryable(
                "DOCUMENT_FORMAT_UNSUPPORTED",
                "지원하지 않는 문서 형식입니다.",
                null
            );
        }
        try (InputStream input = fileStorage.open(context.storageKey())) {
            return documentParser.parse(input, request);
        } catch (DocumentParseException exception) {
            throw JobProcessingException.nonRetryable(
                exception.getErrorCode().name(),
                exception.getMessage(),
                exception
            );
        } catch (IOException exception) {
            throw JobProcessingException.nonRetryable(
                "STORED_FILE_MISSING",
                "저장된 문서를 열 수 없습니다.",
                exception
            );
        }
    }

    private com.aivle.backend.integration.ai.document.DocumentStructureAiResponse callAi(
        com.aivle.backend.integration.ai.document.DocumentStructureAiRequest request
    ) {
        try {
            return aiServiceClient.structureDocument(request);
        } catch (AiClientException exception) {
            throw new JobProcessingException(
                exception.getErrorCode(),
                exception.getSafeMessage(),
                exception.isRetryable(),
                exception.getRetryAfter(),
                exception
            );
        }
    }

    private String parseMetadataJson(ParsedDocument parsed) {
        DocumentParseMetadata metadata = new DocumentParseMetadata(
            parsed.parserName(),
            parsed.parserVersion(),
            parsed.totalCharacters(),
            parsed.totalBlocks(),
            parsed.warnings(),
            parsed.parsedAt(),
            parsed.parsingMetadata()
        );
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw JobProcessingException.nonRetryable(
                "PARSE_METADATA_SERIALIZATION_FAILED",
                "문서 파싱 메타데이터를 저장할 수 없습니다.",
                exception
            );
        }
    }

    @Override
    public JobType jobType() {
        return JobType.DOCUMENT_PARSE;
    }
}
