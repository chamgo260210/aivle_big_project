package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 최종 컨셉 <b>서술문</b>의 잣대.
 *
 * <p>이 화면의 컨셉 원문 자리는 「LLM 이 쓴 문장」이 서는 유일한 곳이다. 그래서 저장 직전에
 * 결정론으로 한 번 본다 — <b>바뀐 조각이 정말 그 값을 담았는가</b>. 이 검사가 느슨해지면
 * 모델이 쓴 아무 문장이나 「우리 사업안은 이것입니다」로 걸린다.
 *
 * <p>통과하지 못하면 <b>저장하지 않는다.</b> 화면은 칸 나열로 폴백한다 — 반쯤 맞는 문장을
 * 세우는 것보다 밋밋한 사실이 낫다.
 */
class RefinedNarrativeContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode narrative(String json) {
        return mapper.readTree(json);
    }

    private boolean check(String json, List<String> afterTexts) {
        return ConceptPortfolioSelectionMaterializationService
            .narrativeMatchesChanges(narrative(json), afterTexts);
    }

    @Test
    void acceptsSegmentsThatActuallyCarryTheChangedValue() {
        assertThat(check("""
            [{"text":"바쁜 ","changeRef":null},
             {"text":"1인 가구 직장인","changeRef":1},
             {"text":"에게 ","changeRef":null},
             {"text":"9,500원대","changeRef":2},
             {"text":" 단품으로 판매해요.","changeRef":null}]
            """, List.of("1인 가구 직장인", "9,500원대"))).isTrue();
    }

    @Test
    void allowsSpacingAndParticlesAroundTheValue() {
        // 모델이 문장에 맞게 조사·띄어쓰기를 손보는 것은 정상이다. 그것까지 어긋남으로 세면
        // 서술문이 거의 항상 기각되어 이 기능이 죽은 것과 같아진다.
        assertThat(check("""
            [{"text":"가격은  9,500 원대 로 잡았어요.","changeRef":1}]
            """, List.of("9,500원대"))).isTrue();
    }

    @Test
    void rejectsSegmentThatDoesNotContainTheValue() {
        // 이것이 「지어낸 문장」을 잡는 자리다.
        assertThat(check("""
            [{"text":"프리미엄 가격대로 갑니다.","changeRef":1}]
            """, List.of("9,500원대"))).isFalse();
    }

    @Test
    void rejectsDuplicatedReference() {
        // 같은 변경을 두 군데 물들이면 「이 번호가 가리키는 곳」이 둘이 된다.
        assertThat(check("""
            [{"text":"9,500원대","changeRef":1},{"text":" 그리고 9,500원대","changeRef":1}]
            """, List.of("9,500원대"))).isFalse();
    }

    @Test
    void rejectsReferenceOutsideTheChangeList() {
        assertThat(check("""
            [{"text":"9,500원대","changeRef":2}]
            """, List.of("9,500원대"))).isFalse();
        assertThat(check("""
            [{"text":"9,500원대","changeRef":0}]
            """, List.of("9,500원대"))).isFalse();
    }

    @Test
    void rejectsWhenTheChangeHasNoDisplayValueToMatch() {
        // afterText 가 비면 대조할 것이 없다. 그때 통과시키면 검사가 사실상 없는 것이 된다.
        assertThat(check("""
            [{"text":"무엇이든 적을 수 있어요","changeRef":1}]
            """, List.of(""))).isFalse();
    }

    @Test
    void rejectsEmptyNarrative() {
        // 빈 서술문을 저장하면 화면이 폴백하지 않고 빈 문단을 세운다.
        assertThat(check("[]", List.of("9,500원대"))).isFalse();
        assertThat(ConceptPortfolioSelectionMaterializationService
            .narrativeMatchesChanges(null, List.of("9,500원대"))).isFalse();
    }

    /**
     * 바뀌지 <b>않은</b> 부분은 값 대조를 안 받는다. 그 틈으로 다른 사업이 들어오는 것을
     * 사업안 이름 하나로 막는다.
     */
    @Test
    void rejectsANarrativeThatDroppedTheConceptName() {
        var narrative = narrative("""
            [{"text":"저희 브랜드는 완전히 다른 사업을 합니다.","changeRef":null}]
            """);
        assertThat(ConceptPortfolioSelectionMaterializationService
            .narrativeKeepsConcept(narrative, "한끼정찬")).isFalse();
    }

    @Test
    void acceptsWhenTheConceptNameSurvivesEvenWithDifferentSpacing() {
        var narrative = narrative("""
            [{"text":"한끼 정찬 은 바쁜 직장인을 위한 냉동식이에요.","changeRef":null}]
            """);
        assertThat(ConceptPortfolioSelectionMaterializationService
            .narrativeKeepsConcept(narrative, "한끼정찬")).isTrue();
    }

    @Test
    void skipsTheConceptNameCheckWhenTheNameIsUnknown() {
        // 없는 잣대로 기각하면 서술문이 영영 안 선다.
        var narrative = narrative("""
            [{"text":"아무 문장","changeRef":null}]
            """);
        assertThat(ConceptPortfolioSelectionMaterializationService
            .narrativeKeepsConcept(narrative, "")).isTrue();
    }

    @Test
    void acceptsUnmarkedSegmentsWithoutChecking() {
        // 안 바뀐 구간은 대조할 값이 없다 — 컨셉을 설명하는 평범한 문장이다.
        assertThat(check("""
            [{"text":"판매는 자사몰과 쿠팡에서 시작해요.","changeRef":null}]
            """, List.of("9,500원대"))).isTrue();
    }
}
