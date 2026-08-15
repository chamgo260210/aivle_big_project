package com.aivle.backend.pipeline.module;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchRunRepository;
import com.aivle.backend.journey.MarketInterviewRun;
import com.aivle.backend.journey.MarketInterviewRunRepository;
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
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse.NextAction;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final MarketingContentRepository marketingRepository;
    private final MarketingSourceSnapshotRepository marketingSourceRepository;
    private final TechOpsInputPreparationRepository techOpsPreparationRepository;
    private final TechOpsInputSnapshotRepository techOpsSnapshotRepository;
    private final FinancialInputPreparationRepository financialPreparationRepository;
    private final FinancialInputSnapshotRepository financialSnapshotRepository;
    private final FinancialAnalysisReportRepository financialAnalysisReportRepository;
    private final MarketResearchRunRepository marketResearchRunRepository;
    private final MarketInterviewRunRepository marketInterviewRunRepository;

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
        // 시장 인터뷰만 이것을 본다. 다른 칸은 「시드가 있나」로 충분하지만, 인터뷰는 그 시드의
        // 여섯 칸을 **응답자에게 그대로 보여 주므로** 어느 판의 시드인지가 결과의 뜻을 바꾼다.
        boolean refinedSeed = selectedSnapshot != null && selectedSnapshot.isRefinementApplied();
        MarketInterviewRun interviewRun = marketInterviewRunRepository
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        MarketResearchRun marketRun = latestResearchRun(projectId, MarketResearchRun.Kind.FULL);
        MarketResearchRun businessRun = latestResearchRun(projectId, MarketResearchRun.Kind.BM);
        // 2-4 부터는 한 실행(VALIDATION)이 두 걸음을 다 돈다. 옛 프로젝트에는 FULL·BM 이
        // 따로 남아 있으므로 **셋 다 본다** — 새 실행이 있으면 그것이 칸의 얼굴이고,
        // 없으면 뒤 걸음인 BM, 그것도 없으면 FULL 이다.
        MarketResearchRun unifiedRun = latestResearchRun(projectId, MarketResearchRun.Kind.VALIDATION);
        MarketResearchRun validationRun = unifiedRun != null ? unifiedRun
            : businessRun != null ? businessRun : marketRun;
        ModuleRun techOpsRun = latestRun(projectId, ModuleType.TECH_OPS);
        ModuleRun financialRun = latestRun(projectId, ModuleType.FINANCIAL_ANALYSIS);
        MarketingContent marketing = marketingRepository.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).orElse(null);
        var marketingSource = selectedSnapshot == null ? null
            : marketingSourceRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        var techOpsPreparation = selectedSnapshot == null ? null
            : techOpsPreparationRepository.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(
                projectId, selectedSnapshot.getId()).orElse(null);
        var techOpsSnapshot = selectedSnapshot == null ? null
            : techOpsSnapshotRepository.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull(
                selectedSnapshot.getId(), projectId).orElse(null);
        // 재무는 「검증을 끝낸 실행」에 매달린다 — 새 실행이 있으면 그것, 없으면 옛 BM.
        MarketResearchRun financeSourceRun = succeeded(unifiedRun) ? unifiedRun
            : succeeded(businessRun) ? businessRun : null;
        var financialPreparation = financeSourceRun == null ? null
            : financialPreparationRepository.findByProjectIdAndSourceMarketResearchRunIdAndDeletedAtIsNull(
                projectId, financeSourceRun.getId()).orElse(null);
        var financialSnapshot = financeSourceRun == null ? null
            : financialSnapshotRepository.findBySourceMarketResearchRunIdAndProjectIdAndDeletedAtIsNull(
                financeSourceRun.getId(), projectId).orElse(null);

        String confirmedBriefId = brief == null ? null : brief.getConfirmedSnapshotId();
        PipelineModuleStatus conceptStatus = conceptStatus(conceptRun, portfolioSelection, confirmedBriefId);
        // 시장조사·BM 은 외부 모듈 핸드오프가 아니라 자체 엔진(MARKET_RESEARCH TaskRun)이 돈다.
        // ⚠ 실행이 있으면 **Seed 확정 여부와 무관하게** 그 실행 상태를 보여준다. 견본 컨셉으로도
        //   돌 수 있어서, Seed 로 막아 두면 다 끝난 모듈이 「준비 전」으로 보이는 거짓말이 된다.
        PipelineModuleStatus marketingStatus = marketingStatus(marketing, marketingSource == null ? null : marketingSource.getId());
        PipelineModuleStatus techOpsStatus = selectedSnapshot == null ? PipelineModuleStatus.NOT_READY
            : techOpsPreparation == null ? PipelineModuleStatus.READY
            : techOpsSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : externalStatus(techOpsRun, techOpsSnapshot.getId());
        boolean financialReportCompleted = financialSnapshot != null && financialAnalysisReportRepository
            .findFirstByProjectIdAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, financialSnapshot.getId()).isPresent();
        // 재무는 「사업 검증」이 끝나야 열린다.
        boolean validationCompleted = financeSourceRun != null;
        PipelineModuleStatus financialStatus = !validationCompleted ? PipelineModuleStatus.NOT_READY
            : financialPreparation == null ? PipelineModuleStatus.READY
            : financialSnapshot == null ? PipelineModuleStatus.NEEDS_INPUT
            : financialReportCompleted ? PipelineModuleStatus.COMPLETED : externalStatus(financialRun, financialSnapshot.getId());

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
            // 「시장분석」과 「BM 캔버스」 두 칸은 「사업 검증」 한 칸으로 접혔다(2026-08-13).
            // ⚠ enum 값 이름 MARKET_ANALYSIS 는 그대로 둔다 — 값 이름이 곧 상태 API 계약이다
            //   (MARKET_INTERVIEW 선례: 여정 7번도 PANEL_SURVEY 이름을 남긴 채 라벨만 옮겼다).
            //   BUSINESS_MODEL 은 enum 에 남되 여기서 더는 돌려주지 않는다.
            response(projectId, PipelineModuleType.MARKET_ANALYSIS,
                validationStatus(unifiedRun, marketRun, businessRun, selectedSnapshot),
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId") : List.of(),
                new NextAction("사업 검증 실행", "/business-validation"),
                validationRun == null ? null : String.valueOf(validationRun.getId()),
                validationRun == null ? null : validationRun.getTaskRun().getId(),
                selectedSnapshot == null ? null : selectedSnapshot.getId(), null, null,
                validationRun == null ? null : validationRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.TECH_OPS, techOpsStatus,
                selectedSnapshot == null ? List.of("marketAnalysisSeedSnapshotId")
                    : techOpsSnapshot == null ? List.of("techOpsRequiredFacts", "techOpsRequiredDecisions")
                    : techOpsRun == null ? List.of("techOpsModuleConnection") : List.of(),
                new NextAction("기술·운영 입력 준비", "/tech-ops"), techOpsRun == null ? null : techOpsRun.getId(), null,
                techOpsSnapshot == null ? null : techOpsSnapshot.getId(), null, null,
                techOpsRun == null ? techOpsPreparation == null ? null : techOpsPreparation.getUpdatedAt() : techOpsRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.FINANCE, financialStatus,
                !validationCompleted ? List.of("businessValidationResult")
                    : financialSnapshot == null ? List.of("financialRequiredInputs")
                    : financialRun == null ? List.of("financialModuleConnection") : List.of(),
                new NextAction("재무 입력 준비", "/finance"), financialRun == null ? null : financialRun.getId(), null,
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null,
                financialRun == null ? financialPreparation == null ? null : financialPreparation.getUpdatedAt() : financialRun.getUpdatedAt()),
            // ⚠ 이 게이트는 **새로 만든 것**이다. 재무와 마케팅은 원래 데이터로 이어져 있지 않았다
            //   (마케팅 게이트는 selectedSnapshot 기반). 트윈 조사는 재무 다음에 서므로
            //   앞 단계의 확정물인 financialSnapshotId 를 요구한다.
            //   ⚠ **컨셉도 같이 본다.** 컨셉보드가 마켓 시드 스냅샷에서 나오므로 재무만 있고
            //   컨셉이 없으면 READY 라고 말해 놓고 자극을 만들지 못한다.
            //   requiredInputs 는 **없는 것부터** 센다 — 앞 단계를 먼저 가리켜야 길이 된다.
            //   시장조사와 같은 규칙으로, **실행이 있으면 게이트와 무관하게 그 상태를 보여준다** —
            //   막아 두면 다 끝난 모듈이 「준비 전」으로 보이는 거짓말이 된다.
            //   ⚠ **다듬기를 지난 시드만 받는다**(2026-08-15). 시드는 두 번 발급된다 — 사업안을
            //   고른 직후 한 번, 다듬기 끝에 「이 컨셉으로 확정하기」로 한 번. 앞의 것으로 열어 주면
            //   사용자는 **다듬기 전 사업안**을 소비자에게 물어보게 되고, 인터뷰는 출시 전 마지막
            //   확인이라 그 답은 쓸 데가 없다. 확정을 지나야 열린다.
            response(projectId, PipelineModuleType.PANEL_SURVEY,
                interviewOrGate(interviewRun, refinedSeed && financialSnapshot != null),
                interviewRequiredInputs(selectedSnapshot, financialSnapshot != null),
                new NextAction("시장 인터뷰", "/market-interview"),
                interviewRun == null ? null : String.valueOf(interviewRun.getId()),
                interviewRun == null ? null : interviewRun.getTaskRun().getId(),
                financialSnapshot == null ? null : financialSnapshot.getId(), null, null,
                interviewRun == null ? null : interviewRun.getUpdatedAt()),
            response(projectId, PipelineModuleType.MARKETING, marketingStatus,
                marketingSource == null ? List.of("marketingSourceSnapshotId") : List.of(),
                new NextAction("마케팅 콘텐츠", "/marketing"), marketing == null ? null : marketing.getId(),
                marketing == null ? null : marketing.getTaskRunId(), marketingSource == null ? null : marketingSource.getId(),
                null, null, marketing == null ? null : marketing.getUpdatedAt())
        );
    }

    private ModuleRun latestRun(Long projectId, ModuleType type) {
        return moduleRunRepository.findFirstByProjectIdAndModuleAndDeletedAtIsNullOrderByCreatedAtDesc(projectId, type).orElse(null);
    }

    private MarketResearchRun latestResearchRun(Long projectId, MarketResearchRun.Kind kind) {
        return marketResearchRunRepository
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, kind).orElse(null);
    }

    /**
     * 「사업 검증」 한 칸의 상태.
     *
     * <p>2-4 부터 실행은 하나(VALIDATION)다. 옛 프로젝트에는 FULL·BM 이 따로 남아 있어
     * 그 갈래도 읽는다 — 안 읽으면 다 끝낸 프로젝트가 「준비 전」으로 보인다.
     *
     * <p><b>시장조사만 끝난 상태는 검증 전체로는 아직 안 끝난 것</b>이라 COMPLETED 가 아니라
     * READY 로 내린다 — 다음 걸음이 남아 있다.
     */
    private static boolean succeeded(MarketResearchRun run) {
        return run != null && run.getState() == MarketResearchRun.State.SUCCEEDED;
    }

    private PipelineModuleStatus validationStatus(MarketResearchRun unifiedRun,
            MarketResearchRun marketRun, MarketResearchRun businessRun, MarketAnalysisSeedSnapshot seed) {
        // 한 실행이 두 걸음을 다 돈다 — 그 상태가 곧 칸의 상태다.
        if (unifiedRun != null) return researchStatus(unifiedRun);
        if (businessRun != null) return researchStatus(businessRun);
        if (marketRun != null) {
            PipelineModuleStatus status = researchStatus(marketRun);
            return status == PipelineModuleStatus.COMPLETED ? PipelineModuleStatus.READY : status;
        }
        return seed == null ? PipelineModuleStatus.NOT_READY : PipelineModuleStatus.READY;
    }

    private PipelineModuleStatus interviewOrGate(MarketInterviewRun run, boolean inputsReady) {
        if (run != null) return switch (run.getState()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
        };
        return inputsReady ? PipelineModuleStatus.READY : PipelineModuleStatus.NOT_READY;
    }

    /**
     * 빠진 것을 여정 순서대로 센다 — 컨셉이 재무보다 앞이라 먼저 나온다.
     *
     * <p>컨셉 쪽은 <b>사유를 갈라 센다.</b> 「사업안을 아직 안 골랐다」와 「골랐지만 다듬기
     * 확정을 안 지났다」는 사용자가 갈 곳이 완전히 다르다 — 앞은 사업안 화면, 뒤는 사업 검증
     * 화면의 「이 컨셉으로 확정하기」다. 한 이름으로 묶으면 어디로 가라는 말인지 알 수 없다.
     */
    private List<String> interviewRequiredInputs(MarketAnalysisSeedSnapshot seed, boolean financialReady) {
        List<String> missing = new ArrayList<>();
        if (seed == null) missing.add("marketAnalysisSeedSnapshotId");
        else if (!seed.isRefinementApplied()) missing.add("refinedConceptConfirmation");
        if (!financialReady) missing.add("financialSnapshotId");
        return missing;
    }

    private PipelineModuleStatus researchStatus(MarketResearchRun run) {
        return switch (run.getState()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case SUCCEEDED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
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

    private PipelineModuleStatus marketingStatus(MarketingContent content, String marketingSourceSnapshotId) {
        if (marketingSourceSnapshotId == null) return PipelineModuleStatus.NOT_READY;
        if (content == null) return PipelineModuleStatus.READY;
        if (!marketingSourceSnapshotId.equals(content.getMarketingSourceSnapshotId())) return PipelineModuleStatus.STALE;
        return switch (content.getStatus()) {
            case QUEUED -> PipelineModuleStatus.QUEUED;
            case RUNNING -> PipelineModuleStatus.RUNNING;
            case COMPLETED, FINALIZED -> PipelineModuleStatus.COMPLETED;
            case FAILED -> PipelineModuleStatus.FAILED;
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
