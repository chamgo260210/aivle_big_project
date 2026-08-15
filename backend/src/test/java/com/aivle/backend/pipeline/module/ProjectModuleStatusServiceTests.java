package com.aivle.backend.pipeline.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRun;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioRunStatus;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioRunRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelectionStatus;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchRunRepository;
import com.aivle.backend.journey.MarketInterviewRunRepository;
import com.aivle.backend.pipeline.integration.repository.ModuleRunRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputPreparationRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.techops.domain.TechOpsInputPreparation;
import com.aivle.backend.pipeline.finance.domain.FinancialInputPreparation;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputPreparationRepository;
import com.aivle.backend.pipeline.techops.repository.TechOpsInputSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectModuleStatusServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    private final ConceptPortfolioRunRepository conceptRuns = mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository snapshots = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final ModuleRunRepository runs = mock(ModuleRunRepository.class);
    private final MarketingContentRepository marketing = mock(MarketingContentRepository.class);
    private final MarketingSourceSnapshotRepository marketingSources = mock(MarketingSourceSnapshotRepository.class);
    private final TechOpsInputPreparationRepository techOpsPreparations = mock(TechOpsInputPreparationRepository.class);
    private final TechOpsInputSnapshotRepository techOpsSnapshots = mock(TechOpsInputSnapshotRepository.class);
    private final FinancialInputPreparationRepository financialPreparations = mock(FinancialInputPreparationRepository.class);
    private final FinancialInputSnapshotRepository financialSnapshots = mock(FinancialInputSnapshotRepository.class);
    private final FinancialAnalysisReportRepository financialAnalysisReports = mock(FinancialAnalysisReportRepository.class);
    private final MarketResearchRunRepository marketResearchRuns = mock(MarketResearchRunRepository.class);
    private final MarketInterviewRunRepository marketInterviewRuns = mock(MarketInterviewRunRepository.class);
    private final ProjectModuleStatusService service = new ProjectModuleStatusService(
        projects, briefs, conceptRuns, portfolioSelections, selections, snapshots, runs, marketing, marketingSources,
        techOpsPreparations, techOpsSnapshots, financialPreparations, financialSnapshots,
        financialAnalysisReports,
        marketResearchRuns, marketInterviewRuns);

    @Test
    void derivesIdeaAndConceptFromCanonicalDomainsWithoutProjectDescription() {
        Project project = mock(Project.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 7, 10, 0);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-snapshot");
        when(brief.getUpdatedAt()).thenReturn(updatedAt);
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getId()).thenReturn("run-1");
        when(run.getActiveTaskRunId()).thenReturn("task-1");
        when(run.getSourceIdeaBrief()).thenReturn(brief);
        when(brief.getId()).thenReturn("brief-snapshot");
        when(run.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.RUNNING);
        when(run.getProducedConceptCount()).thenReturn(3);

        var modules = service.findAll(7L, 41L);

        assertThat(modules).extracting(ProjectModuleStatusResponse::module).containsExactly(
            PipelineModuleType.IDEA, PipelineModuleType.CONCEPT_PORTFOLIO,
            PipelineModuleType.MARKET_ANALYSIS, PipelineModuleType.TECH_OPS,
            PipelineModuleType.FINANCE, PipelineModuleType.PANEL_SURVEY, PipelineModuleType.MARKETING);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.get(0).confirmedSnapshotId()).isEqualTo("brief-snapshot");
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.RUNNING);
        assertThat(modules.get(1).activeTaskRunId()).isEqualTo("task-1");
        assertThat(modules.get(1).activeJobId()).isEqualTo("task-1");
        assertThat(modules.get(1).eligibleCount()).isEqualTo(3L);
        verify(project, never()).getDescription();
    }

    @Test
    void returnsNeedsInputWhenNoIdeaBriefExists() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        var modules = service.findAll(7L, 41L);
        assertThat(modules.get(0).status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(modules.get(1).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(3).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(4).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        // 2=사업 검증, 3=기술·운영, 4=재무, 5=시장 인터뷰, 6=마케팅 — 시장분석과 BM 이
        // 한 칸으로 접히면서 인덱스가 또 한 번 당겨졌다. 칸은 일곱이다.
        assertThat(modules).hasSize(7);
        assertThat(modules.get(5).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(modules.get(6).status()).isEqualTo(PipelineModuleStatus.NOT_READY);
    }

    @Test
    void mapsCurrentV2SelectionAndMarketSeedToCanonicalPortfolioModule() {
        Project project = mock(Project.class);
        ConceptPortfolioRun run = mock(ConceptPortfolioRun.class);
        IdeaBrief brief = mock(IdeaBrief.class);
        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(project));
        when(briefs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(brief));
        when(brief.getStatus()).thenReturn(IdeaBriefStatus.CONFIRMED);
        when(brief.getConfirmedSnapshotId()).thenReturn("brief-v2");
        when(conceptRuns.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getSourceIdeaBrief()).thenReturn(brief);
        when(brief.getId()).thenReturn("brief-v2");
        when(run.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.RESULTS_AVAILABLE);
        when(run.getProducedConceptCount()).thenReturn(2);
        when(portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(selection.getId()).thenReturn(17L);
        when(selection.getStatus()).thenReturn(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        when(snapshots.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(17L)).thenReturn(Optional.of(seed));
        when(seed.getId()).thenReturn("seed-v2");

        var modules = service.findAll(7L, 41L);

        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.CONCEPT_PORTFOLIO)
            .findFirst().orElseThrow().status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(modules.stream().filter(item -> item.module() == PipelineModuleType.MARKET_ANALYSIS)
            .findFirst().orElseThrow().sourceSnapshotId()).isEqualTo("seed-v2");
        verify(selections, never()).findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L);
    }

    @Test
    void exposesIndependentTechOpsPreparationStatusAfterMarketSeedFinalization() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class); MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        TechOpsInputPreparation preparation = mock(TechOpsInputPreparation.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1");
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));
        when(techOpsPreparations.findByProjectIdAndSourceMarketSeedSnapshotIdAndDeletedAtIsNull(41L, "market-seed-1"))
            .thenReturn(Optional.of(preparation));

        var techOps = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.TECH_OPS).findFirst().orElseThrow();

        assertThat(techOps.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(techOps.requiredInputs()).containsExactly("techOpsRequiredFacts", "techOpsRequiredDecisions");
        assertThat(techOps.nextAction().route()).isEqualTo("/tech-ops");
    }

    /** 재무는 「사업 검증」이 끝나야 열린다 — 게이트는 검증 실행(BM)이지 기술·운영 스냅샷이 아니다. */
    @Test
    void exposesIndependentFinancialPreparationStatusAfterBusinessValidationSucceeds() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        FinancialInputPreparation preparation = mock(FinancialInputPreparation.class);
        succeededValidationRun(31L);
        when(financialPreparations.findByProjectIdAndSourceMarketResearchRunIdAndDeletedAtIsNull(41L, 31L))
            .thenReturn(Optional.of(preparation));

        var finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();

        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NEEDS_INPUT);
        assertThat(finance.requiredInputs()).containsExactly("financialRequiredInputs");
        assertThat(finance.nextAction().route()).isEqualTo("/finance");
    }

    /** 검증이 끝나기 전에는 재무가 「사업 검증 결과」를 가리킨다 — 없어진 BM 칸이 아니라. */
    @Test
    void financeGateNamesTheBusinessValidationResultBeforeValidationSucceeds() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));

        var finance = service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.FINANCE).findFirst().orElseThrow();

        assertThat(finance.status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(finance.requiredInputs()).containsExactly("businessValidationResult");
    }

    /**
     * 트윈 게이트는 컨셉·재무 <b>둘 다</b> 본다. 재무 스냅샷은 구조상 컨셉 없이는 생기지 않으므로
     * 상태는 어차피 NOT_READY 지만, 아무것도 없을 때 «재무 스냅샷» 하나만 가리키면
     * 사용자는 갈 수 없는 문을 본다. 빠진 것을 여정 순서대로 다 센다.
     */
    @Test
    void panelSurveyGateNamesEveryMissingInputInJourneyOrder() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));

        var twin = panelSurvey();

        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(twin.requiredInputs())
            .containsExactly("marketAnalysisSeedSnapshotId", "financialSnapshotId");
        assertThat(twin.nextAction().route()).isEqualTo("/market-interview");
    }

    @Test
    void panelSurveyGateAsksOnlyForFinanceOnceTheRefinedConceptIsConfirmed() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1");
        when(seed.isRefinementApplied()).thenReturn(true);
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));

        var twin = panelSurvey();

        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(twin.requiredInputs()).containsExactly("financialSnapshotId");
    }

    /**
     * <b>다듬기 확정을 안 지난 시드로는 인터뷰가 열리지 않는다.</b> (2026-08-15)
     *
     * <p>시드는 두 번 발급된다 — 사업안을 고른 직후 한 번, 다듬기 끝에 「이 컨셉으로 확정하기」로
     * 한 번. 앞의 것으로 열어 주면 사용자는 <b>다듬기 전 사업안</b>을 소비자에게 물어보게 되고,
     * 인터뷰는 출시 전 마지막 확인이라 그 답은 출시 판단에 쓸 수 없다.
     *
     * <p>빠진 것의 이름이 「시드가 없다」와 갈라져야 한다 — 갈 곳이 사업안 화면과 사업 검증
     * 화면으로 서로 다르다.
     */
    @Test
    void panelSurveyGateBlocksASeedThatNeverPassedRefinement() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        ConceptSelection selection = mock(ConceptSelection.class);
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(selection.getId()).thenReturn(13L); when(seed.getId()).thenReturn("market-seed-1");
        when(seed.isRefinementApplied()).thenReturn(false);
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
        when(snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(13L, 41L)).thenReturn(Optional.of(seed));

        var twin = panelSurvey();

        assertThat(twin.status()).isEqualTo(PipelineModuleStatus.NOT_READY);
        assertThat(twin.requiredInputs())
            .containsExactly("refinedConceptConfirmation", "financialSnapshotId");
    }

    /**
     * 시장조사만 끝난 상태는 「사업 검증」 전체로는 아직 안 끝난 것이다 — 다음 걸음(BM)이 남아 있다.
     * COMPLETED 로 보이면 사용자는 끝나지도 않은 검증을 끝났다고 읽는다.
     */
    @Test
    void businessValidationStaysReadyWhileOnlyTheMarketResearchLegSucceeded() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        MarketResearchRun marketRun = mock(MarketResearchRun.class);
        TaskRun marketTaskRun = taskRun("task-market");
        when(marketRun.getState()).thenReturn(MarketResearchRun.State.SUCCEEDED);
        when(marketRun.getId()).thenReturn(29L);
        when(marketRun.getTaskRun()).thenReturn(marketTaskRun);
        when(marketResearchRuns.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, MarketResearchRun.Kind.FULL)).thenReturn(Optional.of(marketRun));

        var validation = businessValidation();

        assertThat(validation.status()).isEqualTo(PipelineModuleStatus.READY);
        assertThat(validation.nextAction().route()).isEqualTo("/business-validation");
    }

    /** BM 까지 끝나야 칸이 완료다. 그 실행이 칸의 얼굴이 된다. */
    @Test
    void businessValidationCompletesOnlyOnceTheBusinessModelLegSucceeded() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        succeededValidationRun(31L);

        var validation = businessValidation();

        assertThat(validation.status()).isEqualTo(PipelineModuleStatus.COMPLETED);
        assertThat(validation.activeRunId()).isEqualTo("31");
    }

    private void succeededValidationRun(long runId) {
        MarketResearchRun businessRun = mock(MarketResearchRun.class);
        TaskRun businessTaskRun = taskRun("task-bm");
        when(businessRun.getState()).thenReturn(MarketResearchRun.State.SUCCEEDED);
        when(businessRun.getId()).thenReturn(runId);
        when(businessRun.getTaskRun()).thenReturn(businessTaskRun);
        when(marketResearchRuns.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            41L, MarketResearchRun.Kind.BM)).thenReturn(Optional.of(businessRun));
    }

    private TaskRun taskRun(String id) {
        TaskRun taskRun = mock(TaskRun.class);
        when(taskRun.getId()).thenReturn(id);
        return taskRun;
    }

    private ProjectModuleStatusResponse businessValidation() {
        return service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.MARKET_ANALYSIS).findFirst().orElseThrow();
    }

    private ProjectModuleStatusResponse panelSurvey() {
        return service.findAll(7L, 41L).stream()
            .filter(item -> item.module() == PipelineModuleType.PANEL_SURVEY).findFirst().orElseThrow();
    }

    @Test
    void hidesProjectsNotOwnedByTheCurrentUser() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAll(8L, 41L))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
