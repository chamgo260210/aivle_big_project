package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>「AI 가 제안하고, 사람이 체크해서 고른 것만 적용된다」</b> — 이 단계의 정의를 잠근다.
 *
 * <p>이 문이 생기기 전에는 AI 결과가 채택되는 순간 <b>전량이 자동 적용</b>됐다. DB 에 물증이
 * 있다: 근거 0건 제안 2건이 최종 컨셉 문장의 가격을 8,900 → 9,500원으로 바꿨다.
 *
 * <p>라운드를 닫는 길이 넷이고, <b>그중 하나라도 빠지면 화면이 영영 안 끝난다</b>.
 * 이 저장소는 같은 병을 이미 두 번 앓았다(제안 0건 · 델타 미부착).
 */
class RefinementDecideTests {

    private static final String 제안 = """
        [{"fieldKey":"price","proposedValue":"1팩 6,900원","afterText":"1팩 6,900원"},
         {"fieldKey":"targetUsers","proposedValue":"수도권 1인 가구","afterText":"수도권 1인 가구"},
         {"fieldKey":"keyActivities","proposedValue":["냉동 물류"],"afterText":"냉동 물류"}]
        """;

    private record Fixture(ConceptRefinementService service, ConceptRefinementRound round,
                           ConceptRefinementApplyService apply) { }

    private Fixture 만든다(String proposalJson) {
        ObjectMapper mapper = new ObjectMapper();
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var apply = mock(ConceptRefinementApplyService.class);
        ConceptRefinementRound round = ConceptRefinementRound.of(42L, 17L, 1, proposalJson, "[]", 1);
        when(rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(17L))
            .thenReturn(List.of(round));
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(java.util.Optional.of(round));
        var service = new ConceptRefinementService(
            mock(ConceptPortfolioSelectionRepository.class), rounds,
            mock(MarketResearchVersionRepository.class),
            mock(ConceptPortfolioSelectionTaskFactory.class),
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(ConceptRefinementFinalRepository.class),
            mock(ConceptPortfolioSelectionService.class),
            mock(TaskRunRepository.class), apply, mapper);
        return new Fixture(service, round, apply);
    }

    @Test
    void 체크한_칸만_적용된다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of("price"), "key");

        ArgumentCaptor<JsonNode> sent = ArgumentCaptor.forClass(JsonNode.class);
        // 열쇠에 조사판이 붙는다 — 새 주기가 라운드 1로 돌아가도 옛 결정과 안 겹치게.
        verify(f.apply()).apply(eq(7L), eq(42L), eq(17L), sent.capture(), eq("key:v1"));
        assertThat(sent.getValue()).hasSize(1);
        assertThat(sent.getValue().get(0).path("fieldKey").asText()).isEqualTo("price");
        assertThat(f.round().getAcceptedFieldsJson()).isEqualTo("[\"price\"]");
    }

    @Test
    void 가설_칸을_골랐으면_라운드를_열어_둔다_법률이_닫는다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of("price"), "key");
        assertThat(f.round().getLegalOutcome())
            .as("델타 법률이 그 결과를 적을 자리를 남겨야 한다")
            .isNull();
    }

    @Test
    void 법을_다시_볼_일이_없는_칸만_골랐으면_그_자리에서_닫는다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of("targetUsers", "keyActivities"), "key");
        assertThat(f.round().getLegalOutcome())
            .as("안 닫으면 화면이 「고를 차례」에 영영 갇힌다")
            .isEqualTo(ConceptRefinementRound.LegalOutcome.PASSED);
    }

    @Test
    void 전부_거절하면_아무것도_적용하지_않고_컨셉을_그대로_둔다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of(), "key");

        verify(f.apply(), never()).apply(any(), any(), any(), any(), any());
        assertThat(f.round().getAcceptedFieldsJson())
            .as("「아직 안 골랐다」(null)와 「전부 넘겼다」([])는 다른 사실이다")
            .isEqualTo("[]");
        assertThat(f.round().getLegalOutcome()).isNull();
    }

    @Test
    void 두_번_누르면_거절한다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of("price"), "key");
        assertThatThrownBy(() -> f.service().decide(7L, 42L, 17L, 1, List.of("targetUsers"), "key2"))
            .isInstanceOf(BusinessException.class);
        // 두 번째 적용이 새지 않았다 — 샜으면 가설 확정이 두 번 걸린다.
        verify(f.apply(), times(1)).apply(any(), any(), any(), any(), any());
    }

    @Test
    void 이미_닫힌_라운드는_결정을_안_받는다() {
        Fixture f = 만든다(제안);
        f.round().recordLegal(ConceptRefinementRound.LegalOutcome.PASSED, "[]");
        assertThatThrownBy(() -> f.service().decide(7L, 42L, 17L, 1, List.of("price"), "key"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 전부_거절한_라운드_뒤에는_다음_라운드를_걸지_않는다() {
        Fixture f = 만든다(제안);
        f.service().decide(7L, 42L, 17L, 1, List.of(), "key");
        // 거절은 「더 해 보라」가 아니라 「그만」이다. 안 그러면 같은 재료로 같은 제안이 다시 온다.
        assertThat(f.service().canRunAnotherRound(17L)).isFalse();
    }
}
