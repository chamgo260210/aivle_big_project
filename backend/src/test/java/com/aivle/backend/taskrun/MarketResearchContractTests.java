package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 계약 검증기 — <b>AI 쪽과 같은 골든 픽스처</b>를 읽는다.
 *
 * <p>「AI 는 맞다는데 백엔드가 거부」 루프를 끊는 장치다. 파일이 하나이므로
 * 한쪽만 고치면 반대쪽 테스트가 즉시 빨개진다.
 */
class MarketResearchContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode fixture(String name) throws Exception {
        // backend/ 에서 저장소 루트로 올라가 ai/tests/fixtures 를 읽는다.
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/market_research/" + name);
            if (Files.exists(candidate)) return MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: " + name);
    }

    /** 픽스처의 `_` 주석 키는 계약 밖이다 — 검증 전에 벗긴다(AI 쪽도 같은 규칙). */
    private static ObjectNode payload(String name) throws Exception {
        ObjectNode node = (ObjectNode) fixture(name);
        node.propertyNames().stream().filter(key -> key.startsWith("_")).toList()
            .forEach(node::remove);
        return node;
    }

    /**
     * VALIDATION 봉투 — <b>한 실행에 두 걸음</b>이라 {@code market}·{@code canvas}·{@code bm} 이
     * 다 찬다. 골든 픽스처는 3층 공용이라 고치지 않는다. FULL 의 근거 원장이 BM 것의
     * 상위집합이므로(BM 은 C-F007·C-F011·C-F001 만 쓴다) 그냥 얹으면 참조가 성립한다.
     */
    private static ObjectNode validationPayload() throws Exception {
        ObjectNode node = payload("full.json");
        ObjectNode bm = payload("bm.json");
        node.put("mode", "VALIDATION");
        node.set("canvas", bm.get("canvas"));
        node.set("bm", bm.get("bm"));
        return node;
    }

    @Test
    @DisplayName("⭐ VALIDATION 은 market 을 갖고 온다 — 셋이 다 차야 통과한다")
    void validationCarriesMarketCanvasAndBm() throws Exception {
        // 2026-08-13 실측 회귀: 이 갈래가 BM 과 같은 가지에 있어 market 을 null 로 강제했고,
        // 유료 실행이 71초 만에 RESULT_FIELD_CONSTRAINT_VIOLATION 으로 통째로 거부됐다.
        ObjectNode node = validationPayload();
        assertThat(node.get("market").isNull()).isFalse();       // 픽스처 전제 확인
        assertThat(node.get("canvas").isNull()).isFalse();
        assertThat(node.get("bm").isNull()).isFalse();
        assertThatCode(() -> MarketResearchContract.validate(node)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VALIDATION 인데 market 이 비면 거부한다 — 검증인데 시장이 없다는 뜻이 된다")
    void validationWithoutMarketRejected() throws Exception {
        ObjectNode node = validationPayload();
        node.putNull("market");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("BM 은 여전히 market 이 null 이어야 한다 — VALIDATION 완화가 BM 으로 새면 안 된다")
    void bmModeWithMarketRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        node.set("market", payload("full.json").get("market"));
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("FULL 인데 bm 이 차 있으면 거부한다 — 1단계는 판정을 내지 않는다")
    void fullModeWithBmRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.set("bm", payload("bm.json").get("bm"));
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("FULL 골든 픽스처가 계약을 통과한다")
    void fullFixturePasses() throws Exception {
        assertThatCode(() -> MarketResearchContract.validate(payload("full.json")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BM 골든 픽스처가 계약을 통과한다")
    void bmFixturePasses() throws Exception {
        assertThatCode(() -> MarketResearchContract.validate(payload("bm.json")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("봉투에 모르는 필드가 있으면 거부한다")
    void unknownEnvelopeFieldRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.put("extra", "x");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("칸이 8개면 거부한다 — 빠진 칸은 «없다» 가 아니라 «안 봤다» 다")
    void missingCanvasCellRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ArrayNode cells = (ArrayNode) node.get("canvas").get("cells");
        cells.remove(0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("없는 근거 id 를 인용하면 거부한다 — 고아 참조 차단")
    void orphanEvidenceReferenceRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ObjectNode cell = (ObjectNode) node.get("canvas").get("cells").get(0);
        ((ArrayNode) cell.get("marketEvidenceIds")).add("C-DOES-NOT-EXIST");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("⭐ 인용한 근거의 경계를 칸이 안 실으면 거부한다 — 이 계약의 본체")
    void droppedCaveatRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ObjectNode cell = (ObjectNode) node.get("canvas").get("cells").get(0);
        assertThat(cell.get("caveats")).isNotEmpty();       // 픽스처 전제 확인
        ((ArrayNode) cell.get("caveats")).removeAll();
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("price_base 의 성격 표시가 바뀌면 거부한다 — 잠정값이 확정 단가로 읽히면 안 된다")
    void priceBaseKindPinned() throws Exception {
        ObjectNode node = payload("full.json");
        ((ObjectNode) node.get("market").get("price")).put("baseKind", "MEAN");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("등급 어휘 밖의 값은 거부한다")
    void unknownGradeRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ((ObjectNode) node.get("evidence").get(0)).put("grade", "VERIFIED");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("7과목 중 하나가 빠지면 거부한다")
    void incompleteScorecardRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ((ArrayNode) node.get("scorecard")).remove(0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("BM 이 null 이어도 통과한다 — BM 실패로 시장조사 결과를 버리지 않는다")
    void nullBmAccepted() throws Exception {
        ObjectNode node = payload("bm.json");
        node.putNull("bm");
        assertThatCode(() -> MarketResearchContract.validate(node))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("게이트 사유가 통째로 빠지면 거부한다 — 게이트를 안 돈 결과를 받으면 안 된다")
    void missingGateReasonsRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ((ObjectNode) node.get("bm")).remove("gateReasons");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("게이트 사유가 비어 있는 것은 통과한다 — 규칙이 안 걸린 것이지 검사를 안 한 게 아니다")
    void emptyGateReasonsAccepted() throws Exception {
        ObjectNode node = payload("bm.json");
        ((ObjectNode) node.get("bm")).putArray("gateReasons");
        assertThatCode(() -> MarketResearchContract.validate(node))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AI 쪽에 없는 게이트 코드는 거부한다 — 두 목록이 갈라지면 여기서 걸린다")
    void unknownGateCodeRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ArrayNode reasons = (ArrayNode) node.get("bm").get("gateReasons");
        assertThat(reasons).isNotEmpty();                   // 픽스처 전제 확인
        ((ObjectNode) reasons.get(0)).put("code", "G99");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("AI 쪽에 없는 갈래는 거부한다 — cause 두 목록이 갈라지면 여기서 걸린다")
    void unknownGateCauseRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ArrayNode reasons = (ArrayNode) node.get("bm").get("gateReasons");
        assertThat(reasons).isNotEmpty();                   // 픽스처 전제 확인
        ((ObjectNode) reasons.get(0)).put("cause", "MAYBE");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("갈래가 빠진 사유는 거부한다 — 없으면 A급 미수집이 조용히 묻힌다")
    void missingGateCauseRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ArrayNode reasons = (ArrayNode) node.get("bm").get("gateReasons");
        ((ObjectNode) reasons.get(0)).remove("cause");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("BM 모드도 성적표를 싣는다 — 없으면 게이트가 갈래를 못 가른다")
    void bmModeCarriesScorecard() throws Exception {
        ObjectNode node = payload("bm.json");
        assertThat(node.get("mode").asText()).isEqualTo("BM");
        assertThat(node.get("scorecard").isArray()).isTrue();
        assertThat(node.get("scorecard")).isNotEmpty();
        assertThat(node.get("market").isNull()).isTrue();   // market 은 여전히 FULL 전용
        MarketResearchContract.validate(node);              // 통과해야 한다
    }

    @Test
    @DisplayName("요인 판정 어휘 밖의 값은 거부한다 — 관측·가정·가설 셋뿐이다")
    void unknownFactorBasisRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ArrayNode factors = (ArrayNode) node.get("market").get("tam").get("factors");
        assertThat(factors).isNotEmpty();                   // 픽스처 전제 확인
        ((ObjectNode) factors.get(0)).put("basis", "ASSUMED");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("요인에 계약 밖 칸이 있으면 거부한다 — 표가 조용히 넓어지지 않게")
    void unknownFactorFieldRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ObjectNode factor = (ObjectNode) node.get("market").get("tam").get("factors").get(0);
        factor.put("range", "[0.15, 0.25]");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("⭐ 「관측」인데 출처가 0곳이면 거부한다 — 표가 거짓말을 하게 된다")
    void observedFactorWithoutSourceRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ArrayNode factors = (ArrayNode) node.get("market").get("tam").get("factors");
        ObjectNode observed = null;
        for (JsonNode factor : factors) {
            if ("관측".equals(factor.get("basis").asText())) observed = (ObjectNode) factor;
        }
        assertThat(observed).as("픽스처에 관측 요인이 있어야 이 검사가 뭔가를 본다").isNotNull();
        observed.put("sourceCount", 0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("factors 가 배열이 아니면 거부한다 — null 은 «항이 없다» 와 다르다")
    void nullFactorsRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ((ObjectNode) node.get("market").get("tam")).putNull("factors");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("FULL 인데 canvas 가 차 있으면 거부한다 — 모드가 섞이면 안 된다")
    void modeMixRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.set("canvas", payload("bm.json").get("canvas"));
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }
}
