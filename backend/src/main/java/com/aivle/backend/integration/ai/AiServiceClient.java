package com.aivle.backend.integration.ai;
import com.aivle.backend.integration.ai.dto.*;
import com.aivle.backend.integration.ai.document.DocumentStructureAiRequest;
import com.aivle.backend.integration.ai.document.DocumentStructureAiResponse;
import com.aivle.backend.report.dto.InterimReportResponse.*;

import java.util.List;

public interface AiServiceClient {
    AiJobAcceptedResponse startJob(AiJobRequest request);
    AiJobStatusResponse getStatus(String externalRequestId);
    void cancel(String externalRequestId);
    DocumentStructureAiResponse structureDocument(DocumentStructureAiRequest request);

    // 1. 시장 분야 비동기 비교
    TableRowDto compareMarket(String initialMarketJson, String aiMarketJson);

    // 2. BM 분야 비동기 비교
    TableRowDto compareBm(String initialBmJson, String aiBmJson);

    // 3. 재무 분야 비동기 비교
    TableRowDto compareFinancial(String initialFinancialJson, String aiFinancialJson);

    // 4. 총괄 요약 KPI 생성
    List<KpiItemDto> generateSummaryKpis(String initialPlanJson, String aiAnalysisJson);
}
