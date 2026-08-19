package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>제안이 0건인 라운드는 그 자리에서 닫는다.</b>
 *
 * <p>2026-08-15 실측 결함. AI 가 「고칠 것이 없다」는 뜻으로 빈 목록을 주면
 * {@code requireProposals} 는 통과하고, {@code apply()} 는 루프를 0회 돌아 가설 확정도
 * 법률 델타도 안 붙는다. 그러면 <b>라운드에 법률 결과를 적는 자가 아무도 없어</b> 워커는
 * 그 라운드를 영영 안 보고(닫힌 라운드만 본다), 화면은 계속 「다듬는 중」을 말한다.
 * 즉 <b>「고칠 것 없음」이라는 결말에 도달하는 경로 자체가 없었다.</b>
 *
 * <p>⚠ 닫을 때 {@code apply()} 를 부르지 않는다 — 적용할 것이 없는데 부르면 그 자리가
 * 「무엇이 적용됐나」를 세는 자리들과 어긋난다.
 */
class EmptyProposalRoundTests {
    private static final String HASH = "sha256:" + "a".repeat(64);

    @Test
    void 제안이_0건이면_라운드를_그_자리에서_닫고_적용은_부르지_않는다() {
        ObjectMapper mapper = new ObjectMapper();
        var selections = mock(ConceptPortfolioSelectionRepository.class);
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var refinementApply = mock(ConceptRefinementApplyService.class);
        var taskRuns = mock(TaskRunService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

        var service = new ConceptPortfolioSelectionMaterializationService(selections,
            mock(ConceptPortfolioHypothesisDecisionRepository.class),
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(MarketAnalysisSeedSnapshotRepository.class),
            mock(ConceptPortfolioSelectionService.class), new ConceptPortfolioJsonHasher(mapper),
            taskRuns, rounds,
            mock(ConceptRefinementService.class), mapper, clock, mock(jakarta.persistence.EntityManager.class));

        ConceptPortfolioSelection selection = ConceptPortfolioSelection.create(42L, "run", "concept-1",
            "candidate-1", HASH, HASH, "명시적 사용자 선택", HASH, "selection-key", 7L, clock.instant());
        ReflectionTestUtils.setField(selection, "id", 17L);
        ReflectionTestUtils.setField(selection, "status", ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        selection.attachTask("task-1", "REFINE_FROM_MARKET");
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));

        ConceptRefinementRound open = ConceptRefinementRound.of(42L, 17L, 1, "[]", "[]", 1);
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(Optional.of(open));

        var result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-selection-action-result-v1");
        result.put("schemaVersion", "1.0");
        result.put("action", "REFINE_FROM_MARKET");
        result.putArray("refinementProposals");
        result.putArray("driftRejections");
        ExecutionResponse response = new ExecutionResponse("internal-ai-execution-v1",
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(), "1.0", "task-1", "attempt-1",
            "corr-1", HASH, "1.0", result, null, null, null);
        TaskRunWorkerContext context = new TaskRunWorkerContext("task-1", 42L, 7L,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "17",
            "{\"action\":\"REFINE_FROM_MARKET\",\"expectedHypothesisRevision\":0,\"refinementMaterial\":{\"round\":1}}", HASH,
            "refine-key", "corr-1", "internal-ai-execution-v1", "1.0", "ko-KR", 1, 3);

        assertThat(service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"),
            context, response)).isEqualTo("REFINE_FROM_MARKET");

        assertThat(open.getLegalOutcome())
            .as("닫지 않으면 화면이 영영 「다듬는 중」이다")
            .isEqualTo(ConceptRefinementRound.LegalOutcome.PASSED);
        verify(refinementApply, never()).apply(any(), any(), any(), any(), any());
        verify(rounds).save(any(ConceptRefinementRound.class));
    }

    /**
     * <b>다듬기와 무관한 가설 확정이 지나가도 열린 라운드를 닫지 않는다.</b>
     *
     * <p>{@code CONFIRM_HYPOTHESES} 갈래는 다듬기 밖에서도 지나는데, 거기서 열린 라운드를
     * {@code PASSED} 로 닫으면 화면이 <b>「다듬기 완료 — 법률 검토까지 통과했어요」</b>라고
     * 말한다. <b>적용된 것은 0건인데</b> 그렇다. 게다가 그 뒤 사용자가 고르려 하면
     * 「이미 닫힌 라운드」라며 거절당한다 — 고를 기회를 통째로 잃는다.
     */
    @Test
    void 아직_아무도_고르지_않은_라운드는_가설_확정이_지나가도_안_닫힌다() {
        ObjectMapper mapper = new ObjectMapper();
        var selections = mock(ConceptPortfolioSelectionRepository.class);
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var selectionService = mock(ConceptPortfolioSelectionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

        var hypotheses = mock(ConceptPortfolioHypothesisDecisionRepository.class);
        when(hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
            eq(17L), any())).thenReturn(Optional.of(mock(ConceptPortfolioHypothesisDecision.class)));

        var service = new ConceptPortfolioSelectionMaterializationService(selections,
            hypotheses,
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(MarketAnalysisSeedSnapshotRepository.class),
            selectionService, new ConceptPortfolioJsonHasher(mapper),
            mock(TaskRunService.class), rounds,
            mock(ConceptRefinementService.class), mapper, clock, mock(jakarta.persistence.EntityManager.class));

        ConceptPortfolioSelection selection = ConceptPortfolioSelection.create(42L, "run", "concept-1",
            "candidate-1", HASH, HASH, "명시적 사용자 선택", HASH, "selection-key", 7L, clock.instant());
        ReflectionTestUtils.setField(selection, "id", 17L);
        ReflectionTestUtils.setField(selection, "status", ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION);
        selection.attachTask("task-1", "CONFIRM_HYPOTHESES");
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));
        // 가설 목록이 비면 `allMatch` 는 참, `anyMatch` 는 거짓 —
        // 즉 `allReady && !deltaRequired` 로 **옛 코드는 여기서 라운드를 닫았다**.
        // (`latestRequired` 는 package-private 이라 스텁하지 않는다. mock 기본값이 빈 목록이다.)

        ConceptRefinementRound open = ConceptRefinementRound.of(42L, 17L, 1,
            "[{\"fieldKey\":\"price\",\"afterText\":\"1팩 6,900원\"}]", "[]", 1);
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(Optional.of(open));

        var result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-selection-action-result-v1");
        result.put("schemaVersion", "1.0");
        result.put("action", "CONFIRM_HYPOTHESES");
        var array = result.putArray("hypotheses");
        for (PortfolioHypothesisType type : PortfolioHypothesisType.values()) {
            array.addObject().put("hypothesisType", type.name());
        }
        ExecutionResponse response = new ExecutionResponse("internal-ai-execution-v1",
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(), "1.0", "task-1", "attempt-1",
            "corr-1", HASH, "1.0", result, null, null, null);
        TaskRunWorkerContext context = new TaskRunWorkerContext("task-1", 42L, 7L,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "17",
            "{\"action\":\"CONFIRM_HYPOTHESES\",\"expectedHypothesisRevision\":0}", HASH,
            "confirm-key", "corr-1", "internal-ai-execution-v1", "1.0", "ko-KR", 1, 3);

        service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), context, response);

        assertThat(open.getLegalOutcome())
            .as("적용된 것이 0건인데 「법률 검토까지 통과했어요」라고 말하면 안 된다")
            .isNull();
    }

    @Test
    void 제안이_있으면_닫지_않고_사람_앞에_남긴다() {
        ObjectMapper mapper = new ObjectMapper();
        var selections = mock(ConceptPortfolioSelectionRepository.class);
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var refinementApply = mock(ConceptRefinementApplyService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

        var service = new ConceptPortfolioSelectionMaterializationService(selections,
            mock(ConceptPortfolioHypothesisDecisionRepository.class),
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(MarketAnalysisSeedSnapshotRepository.class),
            mock(ConceptPortfolioSelectionService.class), new ConceptPortfolioJsonHasher(mapper),
            mock(TaskRunService.class), rounds,
            mock(ConceptRefinementService.class), mapper, clock, mock(jakarta.persistence.EntityManager.class));

        ConceptPortfolioSelection selection = ConceptPortfolioSelection.create(42L, "run", "concept-1",
            "candidate-1", HASH, HASH, "명시적 사용자 선택", HASH, "selection-key", 7L, clock.instant());
        ReflectionTestUtils.setField(selection, "id", 17L);
        ReflectionTestUtils.setField(selection, "status", ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        selection.attachTask("task-1", "REFINE_FROM_MARKET");
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));

        ConceptRefinementRound open = ConceptRefinementRound.of(42L, 17L, 1, "[]", "[]", 1);
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(Optional.of(open));

        var result = mapper.createObjectNode();
        result.put("contract", "concept-portfolio-v2-selection-action-result-v1");
        result.put("schemaVersion", "1.0");
        result.put("action", "REFINE_FROM_MARKET");
        var proposal = result.putArray("refinementProposals").addObject();
        proposal.put("fieldKey", "price");
        proposal.put("afterText", "1팩 6,900원");
        result.putArray("driftRejections");
        ExecutionResponse response = new ExecutionResponse("internal-ai-execution-v1",
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(), "1.0", "task-1", "attempt-1",
            "corr-1", HASH, "1.0", result, null, null, null);
        TaskRunWorkerContext context = new TaskRunWorkerContext("task-1", 42L, 7L,
            TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION, "CONCEPT_PORTFOLIO_SELECTION", "17",
            "{\"action\":\"REFINE_FROM_MARKET\",\"expectedHypothesisRevision\":0,\"refinementMaterial\":{\"round\":1}}", HASH,
            "refine-key", "corr-1", "internal-ai-execution-v1", "1.0", "ko-KR", 1, 3);

        service.complete(new TaskRunService.Claim("task-1", "attempt-1", "claim"), context, response);

        assertThat(open.getLegalOutcome())
            .as("제안이 있으면 사람이 고를 때까지 열려 있어야 한다")
            .isNull();
    }
}
