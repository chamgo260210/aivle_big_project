package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * taskInput 이 <b>해셔를 통과하는지</b>를 실제 컨셉으로 확인한다.
 *
 * <p>부동소수점 문제는 컴파일도 일반 테스트도 안 잡고 <b>런타임에만</b> 터진다.
 * 그래서 「진짜 컨셉 파일」을 읽어 넣는 검사가 있어야 한다 —
 * 손으로 만든 깨끗한 샘플로는 이 결함을 영원히 못 본다.
 */
class MarketResearchInputFactoryTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final MarketResearchInputFactory factory = new MarketResearchInputFactory(MAPPER);
    private final CanonicalInputHasher hasher = new CanonicalInputHasher(MAPPER);

    /** 저장소 안에서 실제 컨셉 파일을 찾는다. 없으면 그 사실이 실패로 드러나야 한다. */
    private static JsonNode realConcept() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve(
                "ai/app/research/research2/data/concept_beauty-noshow.json");
            if (Files.exists(candidate)) return MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("실제 컨셉 파일을 찾지 못했다");
    }

    @Test
    @DisplayName("실제 컨셉에는 부동소수점이 있다 — 그대로 넣으면 안 된다는 전제 확인")
    void realConceptContainsFloatingPoint() throws Exception {
        assertThatThrownBy(() ->
            MarketResearchInputFactory.assertNoFloatingPoint(realConcept(), "concept"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("부동소수점");
    }

    @Test
    @DisplayName("FULL 입력은 해셔를 통과한다 — 컨셉을 문자열로 감쌌기 때문")
    void fullInputHashes() throws Exception {
        String input = factory.full(realConcept(), "beauty-noshow", "2026-08-09");
        assertThatCode(() -> hasher.hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BM 입력은 이름표 하나만 넘긴다 — sourceRun 은 AI 쪽 표가 정한다")
    void bmInputHashes() throws Exception {
        String input = factory.bm("beauty-noshow", "2026-08-09");
        assertThatCode(() -> hasher.hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input))
            .doesNotThrowAnyException();
        JsonNode root = MAPPER.readTree(input);
        assertThat(root.get("conceptId").asText()).isEqualTo("beauty-noshow");
        assertThat(root.get("sourceRun")).isNull();
        // 예산이 없으면 AI 쪽이 Budget(total=0) 으로 떨어져 BM 이 HARD 로 죽는다.
        assertThat(root.get("llmBudget").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("FULL 예산은 수집까지 덮는다 — 짧으면 수집이 시작조차 안 된다")
    void fullCarriesBudget() throws Exception {
        JsonNode root = MAPPER.readTree(factory.full(realConcept(), "beauty-noshow", "2026-08-09"));

        // 상한이지 지출이 아니다. 재채점 판은 예전처럼 3회만 쓴다.
        // 그런데 **원장이 없는 사업안**은 같은 요청으로 수집까지 돌고, 그쪽은
        // 하네스 3 + 수집 ≈80 + 요약 3 을 요구한다. 어느 갈래인지는 AI 서버만 안다.
        // 짧게 두면 `pipeline._collect` 가 「완주 못 할 지출은 시작하지 않는다」로 멈춘다.
        assertThat(root.get("llmBudget").asInt()).isGreaterThanOrEqualTo(86);
    }

    @Test
    @DisplayName("textContents 는 두 모드 모두에 있다 — 타입 무관 필수라 빠지면 400")
    void textContentsAlwaysPresent() throws Exception {
        for (String input : new String[] {
            factory.full(realConcept(), "c", "2026-08-09"),
            factory.bm("c", "2026-08-09")}) {
            JsonNode contents = MAPPER.readTree(input).get("textContents");
            assertThat(contents.isArray()).isTrue();
            assertThat(contents).isNotEmpty();
            JsonNode chunks = contents.get(0).get("chunks");
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0).get("index").asInt()).isZero();
        }
    }

    @Test
    @DisplayName("정수는 막지 않는다 — 막으면 textContents 자체가 못 선다")
    void integersAllowed() {
        JsonNode node = MAPPER.readTree("{\"index\":0,\"characterCount\":12}");
        assertThatCode(() -> MarketResearchInputFactory.assertNoFloatingPoint(node, "x"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("깊이 묻힌 부동소수점도 경로와 함께 잡는다")
    void nestedFloatReported() {
        JsonNode node = MAPPER.readTree("{\"a\":{\"b\":[{\"침투율\":0.005}]}}");
        assertThatThrownBy(() -> MarketResearchInputFactory.assertNoFloatingPoint(node, "input"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("input/a/b[0]/침투율");
    }

    // ── 사용자가 채운 실행 계획 ────────────────────────────────────────
    @Test
    @DisplayName("실행 계획을 실어도 해셔를 통과한다 — 정수와 짧은 문자열뿐이다")
    void bmWithPlanHashes() {
        JsonNode plan = MAPPER.readTree(
            "{\"key_partners\":[\"결제 대행사\"],\"customer_relationship\":\"자동 알림\"}");
        JsonNode constraints = MAPPER.readTree(
            "{\"budget_krw\":5000000,\"months\":10,\"team\":2}");

        String input = factory.bm("beauty-noshow", "2026-08-09", plan, constraints);
        assertThatCode(() -> hasher.hash(TaskType.MARKET_RESEARCH, "1.0", "ko-KR", input))
            .doesNotThrowAnyException();

        JsonNode root = MAPPER.readTree(input);
        assertThat(root.get("planMaterial").get("key_partners")).isNotEmpty();
        assertThat(root.get("executionConstraints").get("budget_krw").asInt()).isEqualTo(5000000);
    }

    @Test
    @DisplayName("빈 계획은 칸 자체를 만들지 않는다 — 「안 썼다」와 「비웠다」를 섞지 않는다")
    void emptyPlanIsNotCarried() {
        JsonNode empty = MAPPER.createObjectNode();
        JsonNode root = MAPPER.readTree(factory.bm("c", "2026-08-09", empty, empty));
        assertThat(root.get("planMaterial")).isNull();
        assertThat(root.get("executionConstraints")).isNull();
    }

    @Test
    @DisplayName("⭐ 소수 예산은 어느 칸인지 말하며 거부한다 — 500 이 아니라 400 이 되도록")
    void fractionalConstraintNamesTheField() {
        JsonNode constraints = MAPPER.readTree("{\"budget_krw\":5000000.5}");
        assertThatThrownBy(() -> factory.bm("c", "2026-08-09", null, constraints))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("budget_krw")
            .hasMessageContaining("정수");
    }

    @Test
    @DisplayName("계획이 없으면 기존 BM 입력과 글자 하나까지 같다 — 기존 경로 무변경")
    void planlessBmIsUnchanged() {
        assertThat(factory.bm("c", "2026-08-09", null, null))
            .isEqualTo(factory.bm("c", "2026-08-09"));
    }
}
