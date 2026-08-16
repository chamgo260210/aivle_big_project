package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * <b>사람이 넘긴 제안이 다음 라운드로 되돌아가는가.</b>
 *
 * <p>여기가 비어 있으면 「다른 제안 받기」는 <b>거짓 버튼</b>이다. 모델은 계약 기각과 법률
 * 차단만 돌려받고 <b>사람이 무엇을 퇴짜놓았는지는 모른 채</b> 같은 것을 다시 낸다 —
 * 라운드는 셋뿐이라 그대로 상한을 태우고 아무것도 못 고친 채 끝난다.
 *
 * <p>⚠ 「아직 안 골랐다」와 「넘겼다」를 가르는 것도 여기서 잠근다. 둘을 뭉개면 사용자가
 * 답한 적 없는 것을 <b>답했다고</b> 모델에 말하게 된다.
 */
class RefinementDeclineFeedbackTests {

    private static final String 제안 = """
        [{"fieldKey":"price","title":"가격을 시장 안으로","afterText":"1팩 6,900원",
          "rationale":"편의점 도시락이 3,900~6,500원이에요."},
         {"fieldKey":"targetUsers","title":"타깃을 좁혔어요","afterText":"수도권 1인 가구",
          "rationale":"혼자 저녁 비중이 높아요."}]
        """;

    private record Fixture(ConceptRefinementService service,
                           ConceptPortfolioSelectionTaskFactory tasks) { }

    private Fixture 만든다(String acceptedFieldsJson) {
        ObjectMapper mapper = new ObjectMapper();
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var selections = mock(ConceptPortfolioSelectionRepository.class);
        var tasks = mock(ConceptPortfolioSelectionTaskFactory.class);
        var selectionService = mock(ConceptPortfolioSelectionService.class);

        ConceptRefinementRound last = ConceptRefinementRound.of(42L, 17L, 1, 제안, "[]", 1);
        if (acceptedFieldsJson != null) last.recordDecision(acceptedFieldsJson);
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(Optional.of(last));

        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getSelectedByUserId()).thenReturn(7L);
        when(selection.getHypothesisRevision()).thenReturn(3);
        when(selections.findById(17L)).thenReturn(Optional.of(selection));
        when(selectionService.refinementInput(eq("REFINE_FROM_MARKET"), any()))
            .thenAnswer(call -> mapper.createObjectNode());

        var service = new ConceptRefinementService(
            selections, rounds, mock(MarketResearchVersionRepository.class), tasks,
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(ConceptRefinementFinalRepository.class),
            selectionService, mock(TaskRunRepository.class),
            mock(ConceptRefinementApplyService.class), mapper);
        return new Fixture(service, tasks);
    }

    private JsonNode 보낸재료(Fixture f) {
        f.service().queueNextRound(7L, 42L, 17L, "key");
        ArgumentCaptor<ObjectNode> sent = ArgumentCaptor.forClass(ObjectNode.class);
        verify(f.tasks()).create(eq(7L), any(), eq("REFINE_FROM_MARKET"), sent.capture(),
            eq("key"), isNull());
        return sent.getValue().path("refinementMaterial");
    }

    @Test
    void 넘긴_제안이_다음_라운드로_되돌아간다() {
        JsonNode declined = 보낸재료(만든다("[\"targetUsers\"]")).path("userDeclined");

        assertThat(declined).hasSize(1);
        assertThat(declined.get(0).path("fieldKey").asText())
            .as("고른 칸(targetUsers)이 아니라 «넘긴» 칸이 가야 한다")
            .isEqualTo("price");
        assertThat(declined.get(0).path("afterText").asText())
            .as("모델이 「같은 값」인지 알려면 값이 실려야 한다")
            .isEqualTo("1팩 6,900원");
    }

    @Test
    void 왜_넘겼는지는_보내지_않는다() {
        // 화면이 안 묻는다. 여기서 이유를 지어 보내면 모델이 그 지어낸 이유를 근거로
        // 다음 제안을 만든다 — 근거 없는 말이 사업안까지 흘러가는 길이다.
        JsonNode one = 보낸재료(만든다("[\"targetUsers\"]")).path("userDeclined").get(0);
        assertThat(one.propertyNames())
            .containsExactlyInAnyOrder("fieldKey", "title", "afterText", "rationale");
    }

    @Test
    void 아직_안_고른_라운드는_넘긴_것이_없다() {
        assertThat(보낸재료(만든다(null)).path("userDeclined"))
            .as("「안 골랐다」를 「넘겼다」로 읽으면 답한 적 없는 것을 답했다고 말하게 된다")
            .isEmpty();
    }

    @Test
    void 전부_넘겼으면_전부_되돌아간다() {
        assertThat(보낸재료(만든다("[]")).path("userDeclined")).hasSize(2);
    }
}
