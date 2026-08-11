package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.journey.TwinSurveyStimulusDraftService;
import com.aivle.backend.taskrun.contract.TwinStimulusDraftContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 자극 초안 계약 — <b>AI 쪽과 같은 골든 픽스처</b>를 읽는다
 * ({@code ai/tests/test_twin_stimulus_draft.py} 와 같은 파일).
 *
 * <p>여기서 미는 경계는 스키마가 아니라 <b>조사 쪽이 거절할 모양</b>이다. 초안이
 * 통과했는데 조사가 거절하면 사용자는 막다른 길을 본다.
 */
class TwinStimulusDraftContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode payload() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/twin_survey/stimulus_draft.json");
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: twin_survey/stimulus_draft.json");
    }

    private static ObjectNode side(ObjectNode result, int pair, String name) {
        return (ObjectNode) result.get("pairs").get(pair).get(name);
    }

    @Test
    @DisplayName("골든 픽스처가 계약을 통과한다")
    void goldenPasses() throws Exception {
        assertThatCode(() -> TwinStimulusDraftContract.validate(payload())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("가격이 양쪽 다르면 거부한다 — 지불의사는 방향이 뒤집히는 것이 실측됐다")
    void differingPriceRejected() throws Exception {
        ObjectNode result = payload();
        side(result, 0, "Y").put("priceKrw", 8900);
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("속성이 둘이면 거부한다 — 다속성 경합은 측정 한계 이하다")
    void multiAttributeRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) side(result, 0, "X").get("attrs")).put("원산지", "국내산");
        ((ObjectNode) side(result, 0, "Y").get("attrs")).put("원산지", "수입산");
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("두 값이 같으면 거부한다 — 잴 차이가 없다")
    void identicalValuesRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) side(result, 0, "Y").get("attrs"))
            .put("보관 형태", side(result, 0, "X").get("attrs").get("보관 형태").asText());
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("실수 가격은 거부한다 — 통과시키면 canonical hash 가 런타임에만 터진다")
    void floatingPointPriceRejected() throws Exception {
        ObjectNode result = payload();
        side(result, 0, "X").put("priceKrw", 9900.5);
        side(result, 0, "Y").put("priceKrw", 9900.5);
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("버린 후보가 없어도 통과한다 — 빈 배열은 «경계 없음»이 아니다")
    void emptyDroppedPasses() throws Exception {
        ObjectNode result = payload();
        result.putArray("dropped");
        assertThatCode(() -> TwinStimulusDraftContract.validate(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("팔 수 있는 쌍이 하나도 없으면 거부한다 — 빈 초안은 답이 아니다")
    void emptyPairsRejected() throws Exception {
        ObjectNode result = payload();
        result.putArray("pairs");
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("확정 가격은 깨끗하게 읽히는 것만 정수로 넘긴다 — 「3만원」은 3이 아니라 null 이다")
    void freeTextPriceIsOnlyReadWhenItIsUnambiguous() {
        assertThat(TwinSurveyStimulusDraftService.priceKrw("월 9,900원")).isEqualTo(9900L);
        assertThat(TwinSurveyStimulusDraftService.priceKrw("3만원")).isNull();
        assertThat(TwinSurveyStimulusDraftService.priceKrw("건당 1,500원 수수료")).isEqualTo(1500L);
        assertThat(TwinSurveyStimulusDraftService.priceKrw("무료")).isNull();
        assertThat(TwinSurveyStimulusDraftService.priceKrw("")).isNull();
    }
}
