package com.aivle.backend.integration.ai;

import com.aivle.backend.common.entity.JobStatus;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.document.structure.AiStructuredPlanItem;
import com.aivle.backend.document.structure.AiStructuredPlanResult;
import com.aivle.backend.document.structure.StructuredItemStatus;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureAiResponse;
import com.aivle.backend.report.dto.InterimReportResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
public class MockAiServiceClient implements AiServiceClient {
    @Override
    public AiJobAcceptedResponse startJob(AiJobRequest request) {
        return new AiJobAcceptedResponse("mock-" + request.jobId(), JobStatus.QUEUED);
    }

    @Override
    public AiJobStatusResponse getStatus(String externalRequestId) {
        return new AiJobStatusResponse(externalRequestId, JobStatus.QUEUED, 0,
                "mock queued", null, null, null);
    }

    @Override
    public void cancel(String externalRequestId) {
        // Mock has no external state; cancellation is intentionally idempotent.
    }

    @Override
    public DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request) {
        Integer firstSequence = request.blocks().isEmpty()
            ? null
            : request.blocks().get(0).sequence();
        List<AiStructuredPlanItem> items = request.sections().stream()
            .map(section -> new AiStructuredPlanItem(
                section.code(),
                section.displayName(),
                StructuredItemStatus.PRESENT,
                "Mock structured content for " + section.displayName(),
                "",
                null,
                List.of("Mock adapter result"),
                firstSequence == null ? List.of() : List.of(firstSequence)
            ))
            .toList();
        return new DocumentStructureAiResponse(
            new AiStructuredPlanResult(
                "mock",
                "mock-document-structure-v1",
                request.promptVersion(),
                request.parserVersion(),
                items,
                null,
                List.of("MOCK_AI_RESULT")
            ),
            "mock-structure-" + request.jobId()
        );
    }

    @Override
    public InterimReportResponse.TableRowDto compareMarket(String initialMarketJson, String aiMarketJson) {
        return new InterimReportResponse.TableRowDto("시장 규모", "국내 1조 원, 유효시장 2,000억 원 추정", "TAM: 8,500억 / SAM: 1,200억 / SOM: 150억", "초기 추정 대비 유효 시장이 40% 과대평가됨. SOM 목표 조정 필요.");
    }

    @Override
    public InterimReportResponse.TableRowDto compareBm(String initialBmJson, String aiBmJson) {
        return new InterimReportResponse.TableRowDto("가격 정책", "월 정기 구독료 15,000원 희망", "권장가: 9,900원 ~ 12,900원 (PSM 분석)", "희망가 설정 시 가격 저항선 초과로 구매 의향도 35% 급감.");
    }

    @Override
    public InterimReportResponse.TableRowDto compareFinancial(String initialFinancialJson, String aiFinancialJson) {
        return new InterimReportResponse.TableRowDto("재무", "초기 투자금 1억", "예상 BEP 14개월", "초기 투자금 대비 BEP 기간 양호");
    }

    @Override
    public List<InterimReportResponse.KpiItemDto> generateSummaryKpis(String initialPlanJson, String aiAnalysisJson) {
        return List.of(
            new InterimReportResponse.KpiItemDto("종합 스코어", "78 / 100", "text-teal-600"),
            new InterimReportResponse.KpiItemDto("검증 판정", "조건부 적합", "text-amber-600"),
            new InterimReportResponse.KpiItemDto("핵심 위험 요소", "가격 저항선 초과", "text-rose-600")
        );
    }
}
