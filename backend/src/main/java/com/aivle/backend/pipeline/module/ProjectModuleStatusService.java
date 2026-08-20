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
import com.aivle.backend.pipeline.market.TwinSurveyRun;
import com.aivle.backend.pipeline.market.TwinSurveyRunRepository;
import com.aivle.backend.pipeline.market.TwinSurveyVersionRepository;
import com.aivle.backend.pipeline.market.MarketInterviewRun;
import com.aivle.backend.pipeline.market.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository;
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
    private final TwinSurveyRunRepository twinSurveyRunRepository;
    private final TwinSurveyVersionRepository twinSurveyVersionRepository;
    private final MarketInterviewRunRepository marketInterviewRunRepository;
    private final MarketingContentRepository marketingRepository;
    private final MarketingSourceSnapshotRepository marketingSourceRepository;
    private final TechOpsInputPreparationRepository techOpsPreparationRepository;
    private final TechOpsInputSnapshotRepository techOpsSnapshotRepository;
    private final TechOpsAdvisoryReportRepository techOpsAdvisoryReportRepository;
    private final FinancialInputPreparationRepository financialPreparationRepository;
    private final FinancialInputSnapshotRepository financialSnapshotRepository;
    private final TaskRunRepository taskRunRepository;
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
        TaskRun marketingStrategyTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.MARKETING_STRATEGY_GENERATION).orElse(null);
        TaskRun marketingVisualTask = marketing == null ? null : taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.MARKETING_VISUAL_GENERATION, "MARKETING_VISUAL", marketing.getId()).orElse(null);
        var marketingSource = selectedSnapshot == null ? null
            : marketingSourceRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var techOpsPreparation = techOpsPreparationRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(projectId).orElse(null);
        var techOpsSnapshot = techOpsSnapshotRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId).orElse(null);
        var techOpsAdvisory = techOpsAdvisoryReportRepository
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        TaskRun techOpsAdvisoryTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.TECH_OPS_ADVISORY).orElse(null);
        TaskRun launchTechnologyTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.LAUNCH_TECHNOLOGY_READINESS).orElse(null);
        TaskRun launchOperationsTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.LAUNCH_OPERATIONS_READINESS).orElse(null);
        TaskRun finalReportTask = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION).orElse(null);
        var userDocumentPreparation = financialPreparationRepository
            .findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, "USER_DOCUMENT_INPUT").orElse(null);
        var financialPreparation = userDocumentPreparation != null ? userDocumentPreparation
            : currentMarketVersion == null || currentBusinessVersion == null ? null : financialPreparationRepository
                .findFirstByProjectIdAndSourceMarketResearchVersionIdAndSourceBusinessModelVersionIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                    projectId, currentMarketVersion.getId(), currentBusinessVersion.getId())
                .orElse(null);
        var userDocumentSnapshot = financialSnapshotRepository
            .findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, "USER_DOCUMENT_INPUT").orElse(null);
        var financialSnapshot = userDocumentSnapshot != null ? userDocumentSnapshot
            : currentMarketVersion == null || currentBusinessVersion == null ? null : financialSnapshotRepository
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
        boolean refinedConceptConfirmed = selectedSnapshot != null && selectedSnapshot.isRefinementApplied();
        PipelineModuleStatus interviewStatus = !refinedConceptConfirmed ? PipelineModuleStatus.NOT_READY
            : interviewRun == null ? PipelineModuleStatus.READY : interviewStatus(interviewRun);
        TaskRun activeInterviewTask = interviewRun == null ? null : activeTask(interviewRun.getTaskRun());
        PipelineModuleStatus marketingStatus = marketingStatus(selectedSnapshot != null, marketing,
            marketingSource == null ? null : marketingSource.getId(), marketingVisualTask,
            marketingStrategyTask);
        TaskRun activeMarketingTask = activeTask(marketingVisualTask) != null ? marketingVisualTask
            : marketing != null && marketing.getTaskRunId() != null
                ? taskRunRepository.findById(marketing.getTaskRunId()).filter(task -> !task.terminal()).orElse(null)
                : activeTask(marketingStrategyTask);
        boolean techOpsAdvisoryStale = techOpsAdvisory != null && (techOpsSnapshot == null
            || !techOpsSnapshot.getId().equals(techOpsAdvisory.getTechOpsInputSnapshotId()));
        PipelineModuleStatus techOpsStatus = techOpsPreparation == null ? PipelineModuleStatus.READY
            : techOpsSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : techOpsAdvisoryStale ? PipelineModuleStatus.STALE
            : techOpsAdvisoryTask == null ? PipelineModuleStatus.READY : taskStatus(techOpsAdvisoryTask.getState());
        PipelineModuleStatus launchStatus = aggregateLaunchStatus(launchTechnologyTask, launchOperationsTask);
        PipelineModuleStatus financialBaseStatus = financialPreparation == null ? PipelineModuleStatus.READY
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
            response(projectId, PipelineModuleType.CONCEPT_REFINEMENT, refinementStatus,
                portfolioSelection == null ? List.of("conceptPortfolioSelectionId")
                    : refinementRounds.isEmpty() ? List.of("conceptRefinementRound") : List.of(),
                new NextAction("컨셉 다듬기", "/concept-refinement"),
                lastRefinementRound == null ? null : String.valueOf(lastRefinementRound.getId()),
                null,
                portfolioSelection == null ? null : String.valueOf(portfolioSelection.getId()), null, null,
                lastRefinementRound == null ? null : lastRefinementRound.getCreatedAt()),
            response(projectId, PipelineModuleType.TECH_OPS, techOpsStatus,
                techOpsSnapshot == null ? List.of("techOpsInput") : List.of(),
                new NextAction("기술·운영 분석", "/tech-ops"),
                techOpsAdvisoryTask == null ? null : techOpsAdvisoryTask.getId(),
                activeTask(techOpsAdvisoryTask) == null ? null : techOpsAdvisoryTask.getId(),
                techOpsSnapshot == null ? null : techOpsSnapshot.getId(), null, null,
                techOpsAdvisoryTask == null ? techOpsPreparation == null ? null : techOpsPreparation.getUpdatedAt()
                    : techOpsAdvisoryTask.getUpdatedAt()),
            response(projectId, PipelineModuleType.FINANCE, financialStatus,
                financialSnapshot == null ? List.of("financialInputDocument")
                    : financialTask == null ? List.of("financialAnalysisReport") : List.of(),
                new NextAction("재무 분석", "/finance"), activeFinancialTask == null
                    ? financialTask == null ? null : financialTask.getId() : activeFinancialTask.getId(),
                activeFinancialTask == null ? null : activeFinancialTask.getId(),
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null,
                financialTask == null ? financialPreparation == null ? null : financialPreparation.getUpdatedAt() : financialTask.getUpdatedAt()),
            response(projectId, PipelineModuleType.LAUNCH_READINESS,
                launchReadinessStatus(launchTechnologyTask, launchOperationsTask),
                List.of(), new NextAction("선택 분석", "/launch-readiness"),
                latestId(launchTechnologyTask, launchOperationsTask),
                latestActiveId(launchTechnologyTask, launchOperationsTask), null, null, null,
                latestUpdatedAt(launchTechnologyTask, launchOperationsTask)),
            response(projectId, PipelineModuleType.MARKET_INTERVIEW, interviewStatus,
                !refinedConceptConfirmed ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("시장 인터뷰", "/market-interview"),
                interviewRun == null ? null : String.valueOf(interviewRun.getId()),
                activeInterviewTask == null ? null : activeInterviewTask.getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                interviewRun == null ? null : interviewRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("마케팅 전략·콘텐츠", "/marketing"),
                marketing == null ? marketingStrategyTask == null ? null : marketingStrategyTask.getId() : marketing.getId(),
                activeMarketingTask == null ? null : activeMarketingTask.getId(),
                marketingSource == null ? selectedSnapshot == null ? null : selectedSnapshot.getId()
                    : marketingSource.getId(), null, null,
                latestUpdatedAt(marketingStrategyTask, marketingVisualTask) == null
                    ? marketing == null ? null : marketing.getUpdatedAt()
                    : latestUpdatedAt(marketingStrategyTask, marketingVisualTask)),
            // 최종 보고서는 여태 이 목록에 «아예 없었다». 그래서 화면의 6단계는 무엇을 해도
            // 상태를 알 수 없었고 여정에 완료가 뜨지 않았다.
            response(projectId, PipelineModuleType.FINAL_REPORT, finalReportStatus(finalReportTask),
                List.of(), new NextAction("최종 보고서", "/final-report"),
                finalReportTask == null ? null : finalReportTask.getId(),
                activeTask(finalReportTask) == null ? null : finalReportTask.getId(), null, null, null,
                finalReportTask == null ? null : finalReportTask.getUpdatedAt())
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

    private PipelineModuleStatus aggregateLaunchStatus(TaskRun technology, TaskRun operations) {
        List<TaskRun> tasks = java.util.stream.Stream.of(technology, operations).filter(java.util.Objects::nonNull).toList();
        if (tasks.isEmpty()) return PipelineModuleStatus.READY;
        if (tasks.stream().anyMatch(task -> List.of(TaskRunState.FAILED, TaskRunState.CANCELLED, TaskRunState.TIMED_OUT).contains(task.getState()))) return PipelineModuleStatus.FAILED;
        if (tasks.stream().anyMatch(task -> task.getState() == TaskRunState.NEEDS_INPUT)) return PipelineModuleStatus.NEEDS_INPUT;
        if (tasks.stream().anyMatch(task -> task.getState() == TaskRunState.RUNNING)) return PipelineModuleStatus.RUNNING;
        if (tasks.stream().anyMatch(task -> List.of(TaskRunState.QUEUED, TaskRunState.READY).contains(task.getState()))) return PipelineModuleStatus.QUEUED;
        return technology != null && operations != null && tasks.stream().allMatch(task -> task.getState() == TaskRunState.SUCCEEDED)
            ? PipelineModuleStatus.COMPLETED : PipelineModuleStatus.READY;
    }

    private TaskRun latest(TaskRun left, TaskRun right) {
        if (left == null) return right; if (right == null) return left;
        return left.getUpdatedAt().isAfter(right.getUpdatedAt()) ? left : right;
    }
    private String latestId(TaskRun left, TaskRun right) { TaskRun value = latest(left, right); return value == null ? null : value.getId(); }
    private String latestActiveId(TaskRun left, TaskRun right) { TaskRun value = latest(activeTask(left), activeTask(right)); return value == null ? null : value.getId(); }
    private LocalDateTime latestUpdatedAt(TaskRun left, TaskRun right) { TaskRun value = latest(left, right); return value == null ? null : value.getUpdatedAt(); }

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

    private PipelineModuleStatus twinStatus(TwinSurveyRun run, boolean stale) {
        if (stale) return PipelineModuleStatus.STALE;
        return switch (run.getTaskRun().getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED, CANCELLED, TIMED_OUT -> PipelineModuleStatus.FAILED;
        };
    }

    private PipelineModuleStatus marketingStatus(boolean hasCurrentConcept, MarketingContent content,
            String marketingSourceSnapshotId, TaskRun visualTask, TaskRun strategyTask) {
        if (!hasCurrentConcept) return PipelineModuleStatus.NOT_READY;
        if (activeTask(strategyTask) != null) return taskStatus(strategyTask.getState());
        if (content == null) {
            if (strategyTask == null || strategyTask.getState() == TaskRunState.SUCCEEDED) {
                return PipelineModuleStatus.READY;
            }
            return taskStatus(strategyTask.getState());
        }
        if (marketingSourceSnapshotId == null) return PipelineModuleStatus.STALE;
        if (!marketingSourceSnapshotId.equals(content.getMarketingSourceSnapshotId())) return PipelineModuleStatus.STALE;
        PipelineModuleStatus contentStatus = switch (content.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case COMPLETED, FINALIZED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
            case STALE -> PipelineModuleStatus.STALE;
        };
        if (visualTask == null || contentStatus != PipelineModuleStatus.COMPLETED) return contentStatus;
        return switch (visualTask.getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case FAILED, CANCELLED, TIMED_OUT -> contentStatus;
            case SUCCEEDED, NEEDS_INPUT -> contentStatus;
        };
    }

    /**
     * 시장 인터뷰 칸의 상태. **실행의 TaskRun 상태가 전부다.**
     *
     * 트윈 조사와 달리 STALE 갈래가 없다 — `MarketInterviewRun` 은 씨앗 스냅샷을 붙들지
     * 않는다. 인터뷰가 응답자에게 보이는 것은 **화면이 그때 보낸 컨셉보드**이고, 그것이
     * 낡았는지는 시드가 아니라 그 보드가 정한다.
     */
    /**
     * 출시 준비는 기술·운영 두 분석으로 이루어진다.
     *
     * <p>예전에는 이 칸이 <b>무조건 {@code READY}</b> 였다. 둘 다 성공해도 화면의 여정에는
     * 영영 「완료」가 뜨지 않았다 — 사용자가 일을 끝냈는데 끝난 표시가 안 났다.
     * 두 분석의 실제 TaskRun 상태로 판정한다.
     */
    private PipelineModuleStatus launchReadinessStatus(TaskRun technology, TaskRun operations) {
        if (activeTask(technology) != null || activeTask(operations) != null) return PipelineModuleStatus.RUNNING;
        boolean technologyDone = technology != null && technology.getState() == TaskRunState.SUCCEEDED;
        boolean operationsDone = operations != null && operations.getState() == TaskRunState.SUCCEEDED;
        if (technologyDone && operationsDone) return PipelineModuleStatus.COMPLETED;
        // 하나만 끝난 상태를 FAILED 로 접지 않는다 — 나머지 하나는 아직 «할 수 있는» 일이다.
        if (technologyDone || operationsDone) return PipelineModuleStatus.READY;
        boolean anyFailed = (technology != null && technology.terminal() && technology.getState() != TaskRunState.SUCCEEDED)
            || (operations != null && operations.terminal() && operations.getState() != TaskRunState.SUCCEEDED);
        return anyFailed ? PipelineModuleStatus.FAILED : PipelineModuleStatus.READY;
    }

    private PipelineModuleStatus finalReportStatus(TaskRun task) {
        if (task == null) return PipelineModuleStatus.READY;
        return switch (task.getState()) {
            case QUEUED, READY -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case NEEDS_INPUT -> PipelineModuleStatus.NEEDS_INPUT;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED, CANCELLED, TIMED_OUT -> PipelineModuleStatus.FAILED;
        };
    }

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
     * **사용자가 직접 시작하는 칸이 아니다.** 라운드를 거는 것은 BM 채택뿐이라, 라운드가
     * 없으면 `READY` 가 아니라 **`NOT_READY`** 다 — 화면에 「시작하기」를 세워도 누를 문이 없다.
     *
     * ⚠ 「고칠 것이 없었다」와 「법이 막았다」·「3라운드에 못 풀었다」도 **끝난 것**으로 센다.
     *   최종 확정 행은 서술문이나 오버레이가 있을 때만 생기므로, 그것만 `COMPLETED` 의
     *   조건으로 삼으면 제안 0건으로 끝난 프로젝트는 **영영 완료가 안 된다.**
     *
     * ⚠ 더 돌 수 있는지는 `ConceptRefinementService.canRunAnotherRound` 것을 그대로 쓴다 —
     *   사본을 두면 워커와 화면이 갈린다.
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

    private ProjectModuleStatusResponse response(Long projectId, PipelineModuleType module,
            PipelineModuleStatus status, List<String> requiredInputs, NextAction nextAction,
            String activeRunId, String activeTaskRunId, String sourceSnapshotId,
            String confirmedSnapshotId, Long eligibleCount, LocalDateTime updatedAt) {
        return new ProjectModuleStatusResponse(projectId, module, status, status.getLabelKey(),
            List.copyOf(requiredInputs), nextAction, activeRunId, activeTaskRunId, activeTaskRunId,
            sourceSnapshotId, confirmedSnapshotId, eligibleCount, updatedAt);
    }
}
