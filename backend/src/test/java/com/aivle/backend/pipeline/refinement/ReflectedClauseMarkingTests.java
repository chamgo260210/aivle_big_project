package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 「컨셉에 반영했어요」를 <b>누가 정하나</b>.
 *
 * <p>AI 는 못 정한다 — 이 조항이 이번 다듬기를 낳았는지는 제안의 {@code legalRef} 만 안다.
 * 그래서 서버가 대조해 찍는다. 나머지 두 상태({@code NEEDS_CHECK}·{@code OK})는 검토가 준
 * 그대로 둔다.
 *
 * <p>⚠ 느슨하게 찍으면 <b>아직 걸려 있는 것이 다 끝난 것처럼 보인다</b>. 그것이 이 화면에서
 * 가장 비싼 거짓말이라 대조를 좁게 잡았다.
 */
class ReflectedClauseMarkingTests {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String CLAUSES = """
        {"officialEvidenceReferences":[
          {"lawName":"식품표시광고법","articleReference":"제6조","conceptStatus":"OK"},
          {"lawName":"식품표시광고법","articleReference":"제8조","conceptStatus":"OK"},
          {"lawName":"축산물 위생관리법","articleReference":"제22조","conceptStatus":"NEEDS_CHECK"}]}
        """;

    private ConceptRefinementController.DeltaLegalView view() {
        return new ConceptRefinementController.DeltaLegalView(
            "PASSED", true, List.of("DIFFERENTIATORS"), mapper.readTree(CLAUSES));
    }

    private ConceptRefinementController.Change legalChange(String legalRef) {
        return new ConceptRefinementController.Change(1, "differentiators", "표현을 법에 맞게 고쳤어요",
            "저나트륨 건강식", "기존 대안보다 나트륨을 30% 낮춘", "기준에 못 미쳐요.",
            List.of(), "LEGAL", legalRef, null, null, "기존 대안보다 나트륨을 30% 낮춘");
    }

    private String statusAt(ConceptRefinementController.DeltaLegalView view, int index) {
        JsonNode clause = view.legalReview().path("officialEvidenceReferences").get(index);
        return clause.path("conceptStatus").asText();
    }

    @Test
    void marksOnlyTheClauseTheChangeCites() {
        var view = view();
        ConceptRefinementController.markReflectedClauses(view,
            List.of(legalChange("식품표시광고법 제6조")));
        assertThat(statusAt(view, 0)).isEqualTo("REFLECTED");
        // ⚠ 같은 법의 다른 조항은 건드리지 않는다. 여기가 느슨하면 「이 법은 다 끝났다」가 된다.
        assertThat(statusAt(view, 1)).isEqualTo("OK");
        assertThat(statusAt(view, 2)).isEqualTo("NEEDS_CHECK");
    }

    @Test
    void marketDrivenChangesNeverMarkClauses() {
        var view = view();
        var market = new ConceptRefinementController.Change(1, "price", "가격을 옮겼어요",
            "15,000원", "9,500원대", "밴드 밖이에요.", List.of("C-F001"), "MARKET", null, null, null, "9,500원대");
        ConceptRefinementController.markReflectedClauses(view, List.of(market));
        assertThat(statusAt(view, 0)).isEqualTo("OK");
    }

    @Test
    void aChangeWithoutALegalReferenceMarksNothing() {
        var view = view();
        ConceptRefinementController.markReflectedClauses(view, List.of(legalChange(null)));
        assertThat(statusAt(view, 0)).isEqualTo("OK");
        ConceptRefinementController.markReflectedClauses(view, List.of(legalChange("  ")));
        assertThat(statusAt(view, 0)).isEqualTo("OK");
    }

    @Test
    void missingDeltaIsANoOp() {
        // 델타를 돈 적이 없으면 찍을 조항도 없다 — 여기서 터지면 화면이 통째로 빨개진다.
        ConceptRefinementController.markReflectedClauses(null, List.of(legalChange("식품표시광고법 제6조")));
    }
}
