package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.integration.domain.ModuleRun;
import com.aivle.backend.pipeline.integration.domain.ModuleType;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchRunRepository;
import com.aivle.backend.pipeline.market.MarketResearchVersion;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.MarketInterviewRun;
import com.aivle.backend.pipeline.market.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsAdvisoryReportRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectModuleStatusService {
    private final ProjectRepository projectRepository;
    private final IdeaBriefRepository ideaBriefRepository;
    private final ConceptPortfolioRunRepository conceptPortfolioRunRepository;
    private final ConceptPortfolioSelectionRepository conceptPortfolioSelectionRepository;
    private final ConceptSelectionRepository selectionRepository;
    private final MarketAnalysisSeedSnapshotRepository marketSeedSnapshotRepository;
    private final ModuleRunRepository moduleRunRepository;
    private final MarketResearchRunRepository marketResearchRunRepository;
    private final MarketResearchVersionRepository marketResearchVersionRepository;
    private final MarketInterviewRunRepository marketInterviewRunRepository;
    private final TwinSurveyVersionRepository twinSurveyVersionRepository;
    private final MarketingContentRepository marketingRepository;
    private final MarketingSourceSnapshotRepository marketingSourceRepository;
    private final TechOpsInputPreparationRepository techOpsPreparationRepository;
    private final TechOpsInputSnapshotRepository techOpsSnapshotRepository;
    private final TechOpsAdvisoryReportRepository techOpsAdvisoryReportRepository;
    private final FinancialInputPreparationRepository financialPreparationRepository;
    private final FinancialInputSnapshotRepository financialSnapshotRepository;
    private final TaskRunRepository taskRunRepository;
    /**
     * 다듬기 칸의 상태 재료. <b>규칙을 여기에 베끼지 않는다</b> — 「다음 라운드를 더 돌 수 있나」는
     * {@link ConceptRefinementService#canRunAnotherRound} 가 이미 정한다. 사본을 두면 갈린다.
     */
    private final ConceptRefinementService conceptRefinementService;
    private final ConceptRefinementFinalRepository conceptRefinementFinalRepository;

    public List<ProjectModuleStatusResponse> findAll(Long userId, Long projectId) {
        projectRepository.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

        IdeaBrief brief = ideaBriefRepository.findCurrentOwned(userId, projectId).orElse(null);
        ConceptPortfolioRun conceptRun = conceptPortfolioRunRepository.findCurrentOwned(userId, projectId).orElse(null);
        long eligibleCount = conceptRun == null ? 0 : conceptRun.getProducedConceptCount();
        ConceptPortfolioSelection portfolioSelection = conceptPortfolioSelectionRepository
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        var legacySelection = portfolioSelection == null
            ? selectionRepository.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId).orElse(null)
            : null;
        MarketAnalysisSeedSnapshot selectedSnapshot = portfolioSelection != null
            ? marketSeedSnapshotRepository
                .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(portfolioSelection.getId()).orElse(null)
            : legacySelection == null ? null
                : marketSeedSnapshotRepository.findBySelectionIdAndProjectIdAndDeletedAtIsNull(
                    legacySelection.getId(), projectId).orElse(null);
        MarketResearchRun marketRun = marketResearchRunRepository
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, MarketResearchRun.Kind.FULL).orElse(null);
        MarketResearchRun businessRun = marketResearchRunRepository
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, MarketResearchRun.Kind.BM).orElse(null);
        MarketResearchVersion latestMarketVersion = marketResearchVersionRepository
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.FULL).orElse(null);
        MarketResearchVersion currentMarketVersion = latestMarketVersion != null && marketRun != null
            && java.util.Objects.equals(latestMarketVersion.getSourceRun().getId(), marketRun.getId())
            && selectedSnapshot != null
            && selectedSnapshot.getId().equals(latestMarketVersion.getSourceRun().getSourceMarketSeedSnapshotId())
                ? latestMarketVersion : null;
        MarketResearchVersion currentBusinessVersion = businessRun == null || currentMarketVersion == null
            || !currentMarketVersion.getId().equals(businessRun.getSourceMarketVersionId()) ? null
            : marketResearchVersionRepository.findBySourceRunIdAndDeletedAtIsNull(businessRun.getId()).orElse(null);
        MarketInterviewRun interviewRun = marketInterviewRunRepository
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        ModuleRun techOpsRun = latestRun(projectId, ModuleType.TECH_OPS);
        MarketingContent marketing = marketingRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);
        TaskRun marketingVisualTask = marketing == null ? null : taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.MARKETING_VISUAL_GENERATION, "MARKETING_VISUAL", marketing.getId()).orElse(null);
        var marketingSource = selectedSnapshot == null ? null
            : marketingSourceRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var techOpsPreparation = selectedSnapshot == null ? null
            : techOpsPreparationRepository.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(
                projectId, selectedSnapshot.getId()).orElse(null);
        var techOpsSnapshot = selectedSnapshot == null ? null
            : techOpsSnapshotRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var techOpsAdvisory = techOpsAdvisoryReportRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        TaskRun techOpsAdvisoryTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.TECH_OPS_ADVISORY).orElse(null);
        var financialPreparation = currentMarketVersion == null || currentBusinessVersion == null
            ? null : financialPreparationRepository
                .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                    projectId, currentMarketVersion.getId(), currentBusinessVersion.getId())
                .orElse(null);
        var financialSnapshot = currentMarketVersion == null || currentBusinessVersion == null
            ? null : financialSnapshotRepository
                .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByFinalizedAtAsc(
                    projectId, currentMarketVersion.getId(), currentBusinessVersion.getId())
                .orElse(null);
        TaskRun financialTask = financialSnapshot == null ? null : taskRunRepository
            .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, "FINANCIAL_ANALYSIS_REPORT", financialSnapshot.getId()).orElse(null);
        TaskRun financialEstimateTask = financialPreparation == null || financialSnapshot != null ? null
            : latestTask(projectId, "FINANCIAL_PREPARATION", financialPreparation.getId(),
                TaskType.FINANCE_ESTIMATE);

        String confirmedBriefId = brief == null ? null : brief.getConfirmedSnapshotId();
        PipelineModuleStatus conceptStatus = conceptStatus(conceptRun, portfolioSelection, confirmedBriefId);
        PipelineModuleStatus marketStatus = selectedSnapshot == null ? PipelineModuleStatus.NOT_READY
            : marketRun == null ? PipelineModuleStatus.READY
            : analysisStatus(marketRun,
                !selectedSnapshot.getId().equals(marketRun.getSourceMarketSeedSnapshotId()));
        PipelineModuleStatus businessModelStatus = currentMarketVersion == null
            ? PipelineModuleStatus.NOT_READY
            : businessRun == null ? PipelineModuleStatus.READY
            : analysisStatus(businessRun,
                !currentMarketVersion.getId().equals(businessRun.getSourceMarketVersionId()));
        List<ConceptRefinementRound> refinementRounds = portfolioSelection == null ? List.of()
            : conceptRefinementService.history(portfolioSelection.getId());
        PipelineModuleStatus refinementStatus = refinementStatus(portfolioSelection, refinementRounds);
        ConceptRefinementRound lastRefinementRound = refinementRounds.isEmpty() ? null
            : refinementRounds.get(refinementRounds.size() - 1);
        PipelineModuleStatus interviewStatus = selectedSnapshot == null ? PipelineModuleStatus.NOT_READY
            : interviewRun == null ? PipelineModuleStatus.READY : interviewStatus(interviewRun);
        TaskRun activeInterviewTask = interviewRun == null ? null : activeTask(interviewRun.getTaskRun());
        PipelineModuleStatus marketingStatus = marketingStatus(marketing,
            marketingSource == null ? null : marketingSource.getId(), marketingVisualTask);
        boolean techOpsAdvisoryStale = techOpsAdvisory != null && (techOpsSnapshot == null
            || currentMarketVersion == null || currentBusinessVersion == null || portfolioSelection == null
            || !techOpsSnapshot.getId().equals(techOpsAdvisory.getTechOpsInputSnapshotId())
            || !currentMarketVersion.getId().equals(techOpsAdvisory.getSourceMarketResearchVersionId())
            || !currentBusinessVersion.getId().equals(techOpsAdvisory.getSourceBusinessModelVersionId())
            || !portfolioSelection.getId().equals(techOpsAdvisory.getSourcePortfolioSelectionId()));
        PipelineModuleStatus techOpsStatus = selectedSnapshot == null ? PipelineModuleStatus.NOT_READY
            : techOpsPreparation == null ? PipelineModuleStatus.READY
            : techOpsSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : currentMarketVersion == null || currentBusinessVersion == null ? PipelineModuleStatus.NOT_READY
            : techOpsAdvisoryStale ? PipelineModuleStatus.STALE
            : techOpsAdvisoryTask == null ? PipelineModuleStatus.READY
            : taskStatus(techOpsAdvisoryTask.getState());
        PipelineModuleStatus financialBaseStatus = currentMarketVersion == null || currentBusinessVersion == null
            ? PipelineModuleStatus.NOT_READY
            : financialPreparation == null ? PipelineModuleStatus.READY
            : financialSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : financialTask == null ? PipelineModuleStatus.READY : taskStatus(financialTask.getState());
        TaskRun activeFinancialReportTask = activeTask(financialTask);
        TaskRun activeFinancialTask = activeFinancialReportTask != null
            ? activeFinancialReportTask : activeTask(financialEstimateTask);
        PipelineModuleStatus financialStatus = activeFinancialReportTask != null
            ? financialBaseStatus : activeOverlay(financialBaseStatus, financialEstimateTask);

        return List.of(
            response(projectId, PipelineModuleType.IDEA, ideaStatus(brief),
                brief == null || brief.getOverviewText() == null || brief.getOverviewText().isBlank() ? List.of("ideaOverview") : List.of(),
                new NextAction("아이디어 정리", "/idea"), null,
                brief == null ? null : brief.getActiveTaskRunId(), null, confirmedBriefId, null,
                brief == null ? null : brief.getUpdatedAt()),
            response(projectId, PipelineModuleType.CONCEPT_PORTFOLIO, conceptStatus,
                confirmedBriefId == null ? List.of("ideaBriefSnapshotId") : List.of(),
                new NextAction("사업안 검토", "/concepts"),
                conceptRun == null ? null : conceptRun.getId(),
                portfolioSelection != null && portfolioSelection.getActiveTaskRunId() != null
                    ? portfolioSelection.getActiveTaskRunId()
                    : conceptRun == null ? null : conceptRun.getActiveTaskRunId(),
                conceptRun == null ? null : conceptRun.getSourceIdeaBrief().getId(), confirmedBriefId, eligibleCount,
                conceptRun == null ? null : conceptRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKET_ANALYSIS, marketStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("시장분석", "/market"), marketRun == null ? null : String.valueOf(marketRun.getId()),
                marketRun == null ? null : marketRun.getTaskRun().getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                marketRun == null ? null : marketRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.BUSINESS_MODEL, businessModelStatus,
                currentMarketVersion == null ? List.of("marketResearchVersionId") : List.of(),
                new NextAction("Business Model", "/business-model"),
                businessRun == null ? null : String.valueOf(businessRun.getId()),
                businessRun == null ? null : businessRun.getTaskRun().getId(),
                currentMarketVersion == null ? null : String.valueOf(currentMarketVersion.getId()), null, null,
                businessRun == null ? null : businessRun.getUpdatedAt()),
            // 사업 검증의 셋째 칸. 자리는 **BM 바로 뒤** — 사용자가 겪는 순서가
            // 시장분석 → BM → 다듬기다(다듬기를 거는 것도 BM 채택이다:
            // MarketResearchWorker.REFINEMENT_TRIGGER_SUBJECT).
            response(projectId, PipelineModuleType.CONCEPT_REFINEMENT, refinementStatus,
                portfolioSelection == null ? List.of("conceptPortfolioSelectionId")
                    : refinementRounds.isEmpty() ? List.of("conceptRefinementRound") : List.of(),
                new NextAction("컨셉 다듬기", "/concept-refinement"),
                lastRefinementRound == null ? null : String.valueOf(lastRefinementRound.getId()),
                null,
                portfolioSelection == null ? null : String.valueOf(portfolioSelection.getId()), null, null,
                lastRefinementRound == null ? null : lastRefinementRound.getCreatedAt()),
            response(projectId, PipelineModuleType.TECH_OPS, techOpsStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId")
                    : techOpsSnapshot == null ? List.of("techOpsRequiredFacts", "techOpsRequiredDecisions")
                    : techOpsAdvisory == null ? List.of("techOpsAdvisoryReport") : List.of(),
                new NextAction("기술·운영 상용화 자문", "/tech-ops"),
                techOpsAdvisory == null ? null : techOpsAdvisory.getId(),
                activeTask(techOpsAdvisoryTask) == null ? null : techOpsAdvisoryTask.getId(),
                techOpsSnapshot == null ? null : techOpsSnapshot.getId(), null, null,
                techOpsAdvisoryTask == null ? techOpsPreparation == null ? null : techOpsPreparation.getUpdatedAt()
                    : techOpsAdvisoryTask.getUpdatedAt()),
            response(projectId, PipelineModuleType.FINANCE, financialStatus,
                currentMarketVersion == null ? List.of("marketResearchVersionId")
                    : currentBusinessVersion == null ? List.of("businessModelVersionId")
                    : financialSnapshot == null ? List.of("financialRequiredInputs")
                    : financialTask == null ? List.of("financialAnalysisReport") : List.of(),
                new NextAction("재무 입력 준비", "/finance"), activeFinancialTask == null
                    ? financialTask == null ? null : financialTask.getId() : activeFinancialTask.getId(),
                activeFinancialTask == null ? null : activeFinancialTask.getId(),
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null,
                financialTask == null ? financialPreparation == null ? null : financialPreparation.getUpdatedAt() : financialTask.getUpdatedAt()),
            // ⚠ enum 값 이름 TWIN_SURVEY 는 **그대로 둔다** — 값 이름이 상태 API 계약이고
            //   프론트가 그 이름으로 칸을 찾는다. 옮기는 것은 **라벨과 경로뿐**이다.
            response(projectId, PipelineModuleType.TWIN_SURVEY, interviewStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("시장 인터뷰", "/market-interview"),
                interviewRun == null ? null : String.valueOf(interviewRun.getId()),
                activeInterviewTask == null ? null : activeInterviewTask.getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                interviewRun == null ? null : interviewRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                marketingSource == null ? List.of("marketingSourceSnapshotId") : List.of(),
                new NextAction("마케팅 콘텐츠", "/marketing"), marketing == null ? null : marketing.getId(),
                marketingVisualTask != null && !marketingVisualTask.terminal() ? marketingVisualTask.getId()
                    : marketing == null ? null : marketing.getTaskRunId(),
                marketingSource == null ? null : marketingSource.getId(), null, null,
                marketingVisualTask == null ? marketing == null ? null : marketing.getUpdatedAt()
                    : marketingVisualTask.getUpdatedAt())
        );
    }

    private ModuleRun latestRun(Long projectId, ModuleType type) {
        return moduleRunRepository.findFirstByProjectIdAndModuleAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, type).orElse(null);
    }

    private TaskRun latestTask(Long projectId, String subjectType, String subjectId,
            TaskType... taskTypes) {
        TaskRun task = taskRunRepository
            .findFirstByProjectIdAndTaskTypeInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, List.of(taskTypes)).orElse(null);
        return task != null && java.util.Objects.equals(subjectType, task.getSubjectType())
            && java.util.Objects.equals(subjectId, task.getSubjectId()) ? task : null;
    }

    private TaskRun activeTask(TaskRun task) {
        return task != null && (task.getState() == TaskRunState.QUEUED
            || task.getState() == TaskRunState.READY
            || task.getState() == TaskRunState.RUNNING) ? task : null;
    }

    private PipelineModuleStatus activeOverlay(PipelineModuleStatus base, TaskRun subordinate) {
        if (base == PipelineModuleStatus.NOT_READY || subordinate == null) return base;
        return switch (subordinate.getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT, SUCCEEDED, FAILED, CANCELLED, TIMED_OUT -> base;
        };
    }

    private PipelineModuleStatus ideaStatus(IdeaBrief brief) {
        if (brief == null) return PipelineModuleStatus.NEEDS_INPUT;
        return switch (brief.getStatus()) {
            case DRAFT -> brief.getOverviewText() == null || brief.getOverviewText().isBlank()
                ? PipelineModuleStatus.NEEDS_INPUT : PipelineModuleStatus.READY;
            case DERIVING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SAFETY_BLOCKED -> PipelineModuleStatus.NEEDS_INPUT;
            case READY_FOR_REVIEW -> PipelineModuleStatus.READY;
            case CONFIRMED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus conceptStatus(ConceptPortfolioRun run,
            ConceptPortfolioSelection selection, String currentBriefSnapshotId) {
        if (run == null) return currentBriefSnapshotId == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.READY;
        if (currentBriefSnapshotId != null && !currentBriefSnapshotId.equals(run.getSourceIdeaBrief().getId())) {
            return PipelineModuleStatus.STALE;
        }
        if (selection != null) {
            if (selection.getActiveTaskRunId() != null) return PipelineModuleStatus.RUNNING;
            return switch (selection.getStatus()) {
                case PENDING_HYPOTHESIS_CONFIRMATION, DELTA_LEGAL_FAILED -> PipelineModuleStatus.NEEDS_INPUT;
                case READY_FOR_MARKET -> PipelineModuleStatus.COMPLETED;
                case FAILED -> PipelineModuleStatus.FAILED;
                case STALE -> PipelineModuleStatus.STALE;
                default -> PipelineModuleStatus.READY;
            };
        }
        return switch (run.getProductStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case RESULTS_AVAILABLE, RESULTS_WITH_OPEN_INPUT -> PipelineModuleStatus.READY;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
    }

    private PipelineModuleStatus externalStatus(ModuleRun run, String currentSnapshotId) {
        if (run == null) return PipelineModuleStatus.NOT_CONNECTED;
        if (currentSnapshotId != null && !currentSnapshotId.equals(run.getInputSnapshotId())) return PipelineModuleStatus.STALE;
        return PipelineModuleStatus.valueOf(run.getStatus().name());
    }

    private PipelineModuleStatus analysisStatus(MarketResearchRun run, boolean stale) {
        if (stale) return PipelineModuleStatus.STALE;
        return switch (run.getTaskRun().getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED, CANCELLED, TIMED_OUT -> PipelineModuleStatus.FAILED;
        };
    }

    private PipelineModuleStatus taskStatus(TaskRunState state) {
        return switch (state) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED, CANCELLED, TIMED_OUT -> PipelineModuleStatus.FAILED;
        };
    }

    /**
     * 시장 인터뷰 칸의 상태. <b>실행의 TaskRun 상태가 전부다.</b>
     *
     * <p>트윈 조사와 달리 STALE 갈래가 없다 — {@link MarketInterviewRun} 은 씨앗 스냅샷을
     * 붙들지 않는다. 인터뷰가 응답자에게 보이는 것은 <b>화면이 그때 보낸 컨셉보드</b>이고
     * (「누를 때마다 새로 실행」), 그것이 낡았는지는 시드가 아니라 그 보드가 정한다.
     */
    private PipelineModuleStatus interviewStatus(MarketInterviewRun run) {
        return switch (run.getTaskRun().getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED, CANCELLED, TIMED_OUT -> PipelineModuleStatus.FAILED;
        };
    }

    /**
     * 컨셉 다듬기 칸의 상태.
     *
     * <p><b>사용자가 직접 시작하는 칸이 아니다.</b> 라운드를 거는 것은 BM 채택
     * ({@code MarketResearchWorker.REFINEMENT_TRIGGER_SUBJECT} →
     * {@link ConceptRefinementService#startFirstRoundAfterResearch})뿐이라,
     * 라운드가 없으면 {@code READY} 가 아니라 <b>{@code NOT_READY}</b> 다 — 화면에 「시작하기」를
     * 세워도 누를 문이 없다.
     *
     * <p>갈래:
     * <ul>
     *   <li>현재 사업안 선택이 없다 → {@code NOT_READY}</li>
     *   <li>라운드가 하나도 없다 → {@code NOT_READY}</li>
     *   <li>최종 확정({@link com.aivle.backend.pipeline.refinement.ConceptRefinementFinal})이 있다
     *       → {@code COMPLETED}</li>
     *   <li>마지막 라운드가 <b>열린 채</b> 제안을 들고 있고 사람이 아직 안 골랐다
     *       → {@code NEEDS_INPUT} (「고를 차례」)</li>
     *   <li>사람이 <b>전부 거절</b>했다 → {@code COMPLETED}. 라운드는 닫히지 않지만
     *       ({@link ConceptRefinementService#decide} 가 거절만 있으면 {@code recordLegal} 을 안 부른다)
     *       루프는 거기서 끝난다 — 「그만」을 「진행 중」으로 보이면 여정 2번이 영영 안 끝난다</li>
     *   <li>닫힌 라운드인데 더 돌 수 없다(상한 3 · 제안 0건 · 채택 0건)
     *       → {@code COMPLETED}. 판정은 {@link ConceptRefinementService#canRunAnotherRound} 것을
     *       그대로 쓴다 — 사본을 두면 워커와 화면이 갈린다</li>
     *   <li>그 밖 → {@code RUNNING} (워커가 다음 걸음을 걷는 중)</li>
     * </ul>
     *
     * <p>⚠ 「고칠 것이 없었다」({@code NOTHING_TO_FIX})와 「법이 막았다」·「3라운드에 못 풀었다」도
     * <b>끝난 것</b>으로 센다. 최종 확정 행은 서술문이나 오버레이가 있을 때만 생기므로
     * ({@code ConceptRefinementService.recordNarrative}) 그것만 {@code COMPLETED} 의 조건으로 삼으면
     * 제안 0건으로 끝난 프로젝트는 <b>영영 완료가 안 된다.</b>
     */
    private PipelineModuleStatus refinementStatus(ConceptPortfolioSelection selection,
            List<ConceptRefinementRound> rounds) {
        if (selection == null || rounds.isEmpty()) return PipelineModuleStatus.NOT_READY;
        if (conceptRefinementFinalRepository.findBySelectionIdAndDeletedAtIsNull(selection.getId()).isPresent()) {
            return PipelineModuleStatus.COMPLETED;
        }
        ConceptRefinementRound last = rounds.get(rounds.size() - 1);
        if (last.getLegalOutcome() == null) {
            if (last.getAcceptedFieldsJson() == null) {
                return conceptRefinementService.proposalsOf(last).isEmpty()
                    ? PipelineModuleStatus.RUNNING : PipelineModuleStatus.NEEDS_INPUT;
            }
            return conceptRefinementService.acceptedOf(last).isEmpty()
                ? PipelineModuleStatus.COMPLETED : PipelineModuleStatus.RUNNING;
        }
        return conceptRefinementService.canRunAnotherRound(selection.getId())
            ? PipelineModuleStatus.RUNNING : PipelineModuleStatus.COMPLETED;
    }

    private PipelineModuleStatus marketingStatus(MarketingContent content, String marketingSourceSnapshotId,
            TaskRun visualTask) {
        if (marketingSourceSnapshotId == null) return PipelineModuleStatus.NOT_READY;
        if (content == null) return PipelineModuleStatus.READY;
        if (!marketingSourceSnapshotId.equals(content.getMarketingSourceSnapshotId())) return PipelineModuleStatus.STALE;
        PipelineModuleStatus contentStatus = switch (content.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case COMPLETED, FINALIZED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
        if (visualTask == null || contentStatus != PipelineModuleStatus.COMPLETED) return contentStatus;
        return switch (visualTask.getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case FAILED, CANCELLED, TIMED_OUT -> contentStatus;
            case SUCCEEDED, NEEDS_INPUT -> contentStatus;
        };
    }

    private ProjectModuleStatusResponse response(Long projectId, PipelineModuleType module,
            PipelineModuleStatus status, List<String> requiredInputs, NextAction nextAction,
            String activeRunId, String activeTaskRunId, String sourceSnapshotId,
            String confirmedSnapshotId, Long eligibleCount, LocalDateTime updatedAt) {
        return new ProjectModuleStatusResponse(projectId, module, status, status.getLabelKey(),
            List.copyOf(requiredInputs), nextAction, activeRunId, activeTaskRunId, activeTaskRunId,
            sourceSnapshotId, confirmedSnapshotId, eligibleCount, updatedAt);
    }
}
