package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aivle.backend.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실행 계획의 <b>정규화</b> — 「비었다」의 정의가 한 곳에 있는지 본다.
 *
 * <p>이 규칙이 층마다 다르면 화면은 「썼다」고 하고 AI 는 「없다」고 읽는다.
 * 저장·전달은 Spring 이 필요 없으므로 순수 단위로 잰다({@code FinancialControllerAsyncTests}
 * 와 같은 관례).
 */
class BmPlanPreparationServiceTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final BmPlanPreparationService service =
        new BmPlanPreparationService(mock(BmPlanPreparationRepository.class), MAPPER);

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    @Test
    @DisplayName("빈 값은 칸 자체를 만들지 않는다 — 뒷단이 채울 기회를 뺏지 않는다")
    void emptyValuesAreDropped() {
        ObjectNode out = service.normalizePlan(json("""
            {"key_activities":["","   "],"key_resources":[],
             "key_partners":["결제 대행사"],"customer_relationship":"  "}"""));

        assertThat(out.propertyNames()).containsExactly("key_partners");
        assertThat(out.get("key_partners").get(0).stringValue()).isEqualTo("결제 대행사");
    }

    @Test
    @DisplayName("앞뒤 공백을 떨어뜨린다 — 「 자동 알림 」과 「자동 알림」이 다른 값이면 안 된다")
    void valuesAreTrimmed() {
        ObjectNode out = service.normalizePlan(json("""
            {"customer_relationship":"  자동 알림  ","key_activities":["  예약 통합  "]}"""));

        assertThat(out.get("customer_relationship").stringValue()).isEqualTo("자동 알림");
        assertThat(out.get("key_activities").get(0).stringValue()).isEqualTo("예약 통합");
    }

    @Test
    @DisplayName("계약에 없는 칸은 싣지 않는다 — 수익모델은 가설 4가 이미 정한다")
    void keysOutsideTheFourAreIgnored() {
        ObjectNode out = service.normalizePlan(json("""
            {"revenue_model":"월 구독","channel":["아웃바운드"],"key_resources":["결제 연동"]}"""));

        assertThat(out.propertyNames()).containsExactly("key_resources");
    }

    @Test
    @DisplayName("⭐ 소수 예산은 400 이다 — 500 으로 새면 사용자가 무엇을 고칠지 모른다")
    void fractionalConstraintIsBadRequest() {
        assertThatThrownBy(() -> service.normalizeConstraints(json("{\"budget_krw\":5000000.5}")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("budget_krw")
            .satisfies(error -> assertThat(
                ((BusinessException) error).getErrorCode().getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("음수 인원은 거부한다 — 0 명은 있어도 -1 명은 없다")
    void negativeConstraintIsRejected() {
        assertThatThrownBy(() -> service.normalizeConstraints(json("{\"team\":-1}")))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("정수 셋은 그대로 통과한다")
    void integersPass() {
        ObjectNode out = service.normalizeConstraints(
            json("{\"budget_krw\":5000000,\"months\":10,\"team\":2}"));

        assertThat(out.get("budget_krw").asLong()).isEqualTo(5_000_000L);
        assertThat(out.get("months").asInt()).isEqualTo(10);
        assertThat(out.get("team").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 칸·null 은 조용히 건너뛴다 — 전부 선택 입력이다")
    void missingConstraintsAreOptional() {
        assertThat(service.normalizeConstraints(json("{\"months\":null}"))).isEmpty();
        assertThat(service.normalizeConstraints(null)).isEmpty();
        assertThat(service.normalizePlan(null)).isEmpty();
    }
}
