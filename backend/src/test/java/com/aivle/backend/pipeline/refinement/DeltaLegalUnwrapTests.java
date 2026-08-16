package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 델타 법률 검토가 <b>어떤 모양으로 저장돼 있나</b>.
 *
 * <p>{@code concept_portfolio_delta_legal_reviews.legal_review_json} 에 들어가는 것은 검토
 * 본문이 아니라 <b>액션 결과 통째</b>다 — {@code ConceptPortfolioSelectionMaterializationService}
 * 의 {@code DELTA_LEGAL} 갈래가 {@code writeValueAsString(result)} 로 넣는다.
 *
 * <p>그래서 최상위에는 {@code officialEvidenceReferences} 가 없다. 그걸 바로 읽으면 <b>조용히
 * 빈 목록</b>이 되고, 화면은 「이번에 새로 걸린 법이 없어요」라고 <b>사실과 다르게</b> 말한다.
 * 이 테스트가 그 한 겹을 지킨다.
 */
class DeltaLegalUnwrapTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unwrapsTheActionResultThatIsActuallyStored() {
        var stored = mapper.readTree("""
            {"action":"DELTA_LEGAL","hypotheses":[],
             "deltaLegalResult":{"approved":true,"status":"PASSED",
               "legalReview":{"productionStatus":"NEEDS_FACTS",
                 "officialEvidenceReferences":[{"lawName":"식품표시광고법"}]}}}
            """);
        var review = ConceptRefinementService.unwrapLegalReview(stored);
        assertThat(review.path("productionStatus").asText()).isEqualTo("NEEDS_FACTS");
        assertThat(review.path("officialEvidenceReferences")).hasSize(1);
    }

    @Test
    void acceptsARowThatAlreadyHoldsOnlyTheReview() {
        // 옛 행이 본문만 담고 있을 수 있다. 둘 다 받아야 이력이 갈리지 않는다.
        var stored = mapper.readTree("""
            {"productionStatus":"IMPLEMENTABLE","officialEvidenceReferences":[]}
            """);
        assertThat(ConceptRefinementService.unwrapLegalReview(stored)
            .path("productionStatus").asText()).isEqualTo("IMPLEMENTABLE");
    }

    @Test
    void unwrapsAnIntermediateLegalReviewKey() {
        var stored = mapper.readTree("""
            {"legalReview":{"productionStatus":"REJECTED","officialEvidenceReferences":[]}}
            """);
        assertThat(ConceptRefinementService.unwrapLegalReview(stored)
            .path("productionStatus").asText()).isEqualTo("REJECTED");
    }
}
