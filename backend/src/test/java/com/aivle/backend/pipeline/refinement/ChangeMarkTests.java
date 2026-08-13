package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 서술문에서 <b>초록으로 물들 말</b>을 고른다.
 *
 * <p>이 값이 두 곳에서 같이 쓰인다 — 모델에게 「이 말을 담아라」로 주고, 저장 직전에
 * 「정말 담았나」로 잰다. <b>두 곳이 갈리면</b> 모델은 A 를 담고 서버는 B 를 찾는다.
 *
 * <p>⚠ 목록 칸에서 값 전체를 잣대로 삼으면 안 된다. 차별점 넷에 하나를 더한 변경에서
 * 값 전체를 요구하면 문단이 목록을 통째로 옮겨 적어야 하고, <b>정작 더해진 항목은 빠져도
 * 통과</b>한다 — 2026-08-13 실측에서 모델이 옛 네 항목만 적고 새 항목을 빠뜨렸다.
 */
class ChangeMarkTests {

    @Test
    void picksTheAddedItemFromAWrittenList() {
        String before = "1인분 정량 설계, 10분 이내 단일 조리, 급속냉동으로 보존료 최소화";
        String after = before + ", 오피스 무인 냉동고 접근성 강화";
        assertThat(ConceptRefinementService.changeMark(before, after))
            .isEqualTo("오피스 무인 냉동고 접근성 강화");
    }

    @Test
    void fallsBackToTheWholeValueWhenNothingWasAdded() {
        // 가격처럼 값 하나가 통째로 바뀌면 그 값 전체가 잣대다.
        assertThat(ConceptRefinementService.changeMark("1팩 8,900원", "1팩 9,500원"))
            .isEqualTo("1팩 9,500원");
    }

    @Test
    void keepsParenthesisedSeparatorsInsideOneItem() {
        String before = "자사몰 정기구독";
        String after = "자사몰 정기구독, 대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑)";
        assertThat(ConceptRefinementService.changeMark(before, after))
            .isEqualTo("대형 이커머스 입점(쿠팡·마켓컬리·네이버쇼핑)");
    }

    @Test
    void anEmptyBeforeMeansTheWholeNewValueIsTheChange() {
        assertThat(ConceptRefinementService.changeMark("", "자사몰 정기구독, 쿠팡"))
            .isEqualTo("자사몰 정기구독");
    }
}
