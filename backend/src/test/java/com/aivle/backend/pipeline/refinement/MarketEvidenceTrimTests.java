package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 다듬기 재료의 근거를 <b>추린다</b>.
 *
 * <p>⚠ 2026-08-15 실측: 시장조사가 절 사실을 승격시키기 시작하면서 봉투 근거가
 * <b>422장 · 274KB</b>가 됐다(유료 실행 {@code p46-bm-01}). AI 계약
 * ({@code selection_models.RefinementMaterial.marketEvidence})은 <b>200장</b>이 상한이라
 * 그대로 넘기면 <b>컨셉 다듬기가 400 으로 죽는다.</b>
 *
 * <p>화면은 422장을 그대로 보여야 하므로 봉투가 아니라 <b>이 재료만</b> 줄인다.
 * 슬롯 근거({@code C-…})가 칸을 실제로 확인해 준 근거이므로 <b>그것부터</b> 남긴다.
 */
class MarketEvidenceTrimTests {

    private static ArrayNode envelope(int slots, int promoted) {
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (int i = 1; i <= slots; i++) out.addObject().put("id", "C-F%03d".formatted(i));
        for (int i = 1; i <= promoted; i++) out.addObject().put("id", "sec-%04d".formatted(i));
        return out;
    }

    private static java.util.List<String> ids(JsonNode node) {
        java.util.List<String> out = new java.util.ArrayList<>();
        node.forEach(item -> out.add(item.path("id").asString("")));
        return out;
    }

    /** 캔버스 9칸이 인용한 근거 목록. */
    private static JsonNode canvas(String... citedIds) {
        var root = JsonNodeFactory.instance.objectNode();
        var cell = root.putArray("cells").addObject();
        var refs = cell.putArray("marketEvidenceIds");
        for (String id : citedIds) refs.add(id);
        return root;
    }

    @Test
    void leavesASmallEnvelopeAlone() {
        // 옛 봉투(승격 이전)는 손대지 않는다 — 되돌아갈 자리를 남긴다.
        JsonNode small = envelope(26, 62);
        assertThat(trim(small, canvas("C-F001"))).isSameAs(small);
    }

    @Test
    void keepsWhatTheCanvasActuallyCited() {
        // 실측 규모: 슬롯 26 + 승격 396 = 422. 인용한 것이 먼저 살아남는다.
        JsonNode trimmed = trim(envelope(26, 396), canvas("sec-0396", "C-F026"));
        assertThat(trimmed.size()).isEqualTo(ConceptRefinementService.MARKET_EVIDENCE_LIMIT);
        assertThat(ids(trimmed)).startsWith("C-F026", "sec-0396");
    }

    @Test
    void neverExceedsTheLimitEvenWhenCitationsAloneOverflow() {
        String[] many = new String[300];
        for (int i = 0; i < many.length; i++) many[i] = "C-F%03d".formatted(i + 1);
        assertThat(trim(envelope(500, 0), canvas(many)).size())
            .isEqualTo(ConceptRefinementService.MARKET_EVIDENCE_LIMIT);
    }

    @Test
    void survivesAMissingEvidenceBlock() {
        // 옛 실행에는 `evidence` 가 없을 수 있다. 그때 죽으면 라운드가 아예 못 선다.
        assertThat(trim(JsonNodeFactory.instance.objectNode(), canvas()).isArray()).isFalse();
    }

    @Test
    void stillFillsUpWhenNothingWasCited() {
        // 인용이 0건이어도 재료를 통째로 비우지 않는다 — 다듬기가 볼 것이 사라진다.
        assertThat(trim(envelope(26, 396), canvas()).size())
            .isEqualTo(ConceptRefinementService.MARKET_EVIDENCE_LIMIT);
    }

    private static JsonNode trim(JsonNode evidence, JsonNode canvas) {
        return ConceptRefinementService.trimEvidence(evidence, canvas);
    }
}
