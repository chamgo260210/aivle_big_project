package com.aivle.backend.report.service;

import com.aivle.backend.integration.ai.AiServiceClient;
import com.aivle.backend.report.dto.InterimReportResponse;
import com.aivle.backend.report.dto.InterimReportResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterimReportService {

    private final AiServiceClient aiServiceClient;
    private final Executor asyncExecutor; // Custom ThreadPoolTaskExecutor

    public InterimReportResponse generateInterimReport(Long projectId) {
        log.info("중간 보고서 비동기 생성 시작 - Project ID: {}", projectId);

        // 1. DB에서 초기 사업계획서 및 기존 AI 분석 JSON 조회 (Mock 데이터 대체)
        String initialPlanJson = fetchInitialPlanJson(projectId);
        String aiAnalysisJson = fetchAiAnalysisJson(projectId);

        // 2. 3개 비교 에이전트 & Summary KPI 생성을 비동기로 동시에 호출 (Parallel Execution)
        CompletableFuture<List<KpiItemDto>> kpiFuture = CompletableFuture.supplyAsync(
            () -> aiServiceClient.generateSummaryKpis(initialPlanJson, aiAnalysisJson),
            asyncExecutor
        );

        CompletableFuture<TableRowDto> marketFuture = CompletableFuture.supplyAsync(
            () -> aiServiceClient.compareMarket(initialPlanJson, aiAnalysisJson),
            asyncExecutor
        );

        CompletableFuture<TableRowDto> bmFuture = CompletableFuture.supplyAsync(
            () -> aiServiceClient.compareBm(initialPlanJson, aiAnalysisJson),
            asyncExecutor
        );

        CompletableFuture<TableRowDto> financialFuture = CompletableFuture.supplyAsync(
            () -> aiServiceClient.compareFinancial(initialPlanJson, aiAnalysisJson),
            asyncExecutor
        );

        // 3. 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(kpiFuture, marketFuture, bmFuture, financialFuture).join();

        // 4. 결과 수집
        List<KpiItemDto> kpis = kpiFuture.join();
        TableRowDto marketRow = marketFuture.join();
        TableRowDto bmRow = bmFuture.join();
        TableRowDto financialRow = financialFuture.join();

        // 5. 프론트엔드 동적 뷰어 규격(Section 기반)으로 합성
        SectionDto kpiSection = new SectionDto(
            "sec_summary",
            "KPI_GRID",
            "1. 종합 검증 요약",
            null,
            null,
            kpis,
            null
        );

        SectionDto comparisonSection = new SectionDto(
            "sec_comparison",
            "COMPARISON_TABLE",
            "2. 초기 기획 vs AI 검증 비교 (Market & BM Gap)",
            "1.5fr 2.5fr 3fr 3.5fr",
            List.of("검증 영역", "초기 작성 내용 (Input)", "AI 에이전트 검증 (Output)", "Gap 분석 및 시사점"),
            null,
            List.of(marketRow, bmRow, financialRow)
        );

        return new InterimReportResponse(
            "사업 타당성 중간 보고서",
            "Project ID: PRJ_" + projectId,
            "AI Business Validation Platform",
            List.of(kpiSection, comparisonSection)
        );
    }

    private String fetchInitialPlanJson(Long projectId) {
        // DB 조회 로직 (예: projectDocumentRepository.findByProjectId(...))
        return "{\"market\": \"국내 1조 원\", \"price\": \"15,000원\"}";
    }

    private String fetchAiAnalysisJson(Long projectId) {
        // DB 조회 로직 (예: feasibilityAnalysisRepository.findByProjectId(...))
        return "{\"sam\": \"1,200억 원\", \"recommendedPrice\": \"11,500원\"}";
    }
}
