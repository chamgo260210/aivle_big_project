package com.aivle.backend.journey;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MARKET_RESEARCH 의 taskInput 을 만든다.
 *
 * <p>두 가지를 동시에 지켜야 한다.
 *
 * <ol>
 *   <li><b>{@code textContents} 는 taskType 과 무관하게 필수다.</b>
 *       {@code ai/app/api/executions.py} 가 모든 요청에 대해 검사하고, 청크 해시가
 *       한 글자만 어긋나도 400 이다.</li>
 *   <li><b>부동소수점을 넣으면 런타임에 500 이 난다.</b>
 *       {@code CanonicalInputHasher:64} 가 float 를 거부하는데 그 예외는
 *       {@code TaskRunFailure} 로 감싸이지 않아 컨트롤러까지 올라간다 —
 *       컴파일도 테스트도 안 잡는다.</li>
 * </ol>
 *
 * <p>⚠ 「숫자를 아예 넣지 않는다」는 <b>불가능하다</b> — {@code index}·{@code characterCount}·
 * {@code totalCharacters} 가 정수로 들어가야 한다. 해셔는 정수를 허용하고 <b>부동소수점만</b>
 * 거부한다. 그래서 규칙은 「숫자 금지」가 아니라 <b>「부동소수점 금지」</b>이고,
 * 그것을 {@link #assertNoFloatingPoint} 가 보낸 뒤가 아니라 <b>보내기 전에</b> 확인한다.
 *
 * <p>컨셉 스냅샷에 실제로 float 가 있다(예: {@code 가정_침투율 0.005}). 그래서 컨셉은
 * <b>JSON 문자열로 직렬화해 {@code textContents} 안에</b> 넣는다 — 문자열 안의 숫자는
 * 해셔가 보지 않는다.
 */
@Component
public class MarketResearchInputFactory {

    private static final int CHUNK_CHARACTERS = 16_000;

    private final ObjectMapper mapper;

    public MarketResearchInputFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 1단계 — 컨셉 전체를 문자열로 실어 보낸다.
     *
     * <p>{@code conceptId} 는 AI 쪽 {@code pipeline.CONCEPTS} 의 <b>이름표</b>다. 그 표가
     * 이름표 하나로 (컨셉 파일, 원장) 을 정하므로 백엔드는 {@code sourceRun} 을 모른다 —
     * 알 필요도 없다. 목록은 AI 가 정본이고 여기서는 문자열을 나르기만 한다.
     *
     * <p>{@code llmBudget} 없이 보내면 AI 쪽이 {@code Budget(total=0)} 으로 떨어져
     * 요약이 {@code BUDGET_EXHAUSTED} 로 조용히 빠진다. 요약은 최소 3회를 요구한다.
     *
     * <p>⚠ <b>상한이지 지출이 아니다.</b> 저장된 수집을 재채점하는 판은 예전처럼 3회만 쓴다.
     * 그런데 <b>원장이 없는 사업안</b>은 같은 요청으로 수집까지 도는데, 그쪽은
     * 하네스 3 + 수집 ≈80 + 요약 3 이 필요하다. 어느 갈래인지는 <b>AI 서버만 안다</b>
     * (원장이 있느냐가 가른다). 그래서 상한은 긴 쪽에 맞춘다 — 짧게 두면
     * {@code _collect} 가 「완주 못 할 지출은 시작하지 않는다」로 <b>시작조차 안 한다</b>.
     */
    private static final int LLM_BUDGET_FULL = 90;
    public String full(JsonNode concept, String conceptId, String asOf) {
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("concept", mapper.writeValueAsString(concept)));
        root.put("conceptId", conceptId);
        root.put("asOf", asOf);
        root.put("mode", "FULL");
        root.put("llmBudget", LLM_BUDGET_FULL);
        return finish(root);
    }

    /**
     * 사업 검증 — <b>한 실행에 두 걸음</b>(FULL → BM)이 들어간다.
     *
     * <p>봉투는 1단계와 같고 계획 4칸만 더 붙는다. AI 쪽 러너가 이 하나를 받아
     * {@code mode} 를 갈아 끼우며 두 번 부른다 — 그래서 {@code mode} 는 여기서 정하지 않고
     * 러너가 정한다. 그래도 계약상 칸은 채워야 하므로 {@code VALIDATION} 을 싣는다.
     *
     * <p>⚠ 예산은 <b>1단계 상한 그대로</b>다. BM 은 1회짜리라 여기에 더할 것이 없고,
     * 짧게 잡으면 수집이 「완주 못 할 지출은 시작하지 않는다」로 시작조차 안 한다.
     */
    public String validation(JsonNode concept, String conceptId, String asOf,
                             JsonNode planMaterial, JsonNode constraints) {
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("concept", mapper.writeValueAsString(concept)));
        root.put("conceptId", conceptId);
        root.put("asOf", asOf);
        root.put("mode", "VALIDATION");
        root.put("llmBudget", LLM_BUDGET_FULL);
        // 비어 있으면 칸 자체를 만들지 않는다 — 빈 객체를 실으면 AI 쪽에서 「사용자가
        // 안 썼다」와 「사용자가 비웠다」가 같아진다. BM 쪽 규칙과 같다.
        if (planMaterial != null && planMaterial.isObject() && !planMaterial.isEmpty()) {
            root.set("planMaterial", planMaterial);
        }
        if (constraints != null && constraints.isObject() && !constraints.isEmpty()) {
            assertIntegers(constraints);
            root.set("executionConstraints", constraints);
        }
        return finish(root);
    }

    /**
     * 2단계 — <b>이름표 하나만</b> 넘긴다.
     *
     * <p>1단계 결과({@code MarketJoinData})를 그대로 실으면 그 안의 부동소수점 31개가
     * 해셔에서 터진다. AI 서버가 원장에서 직접 읽게 하는 편이 안전하고, 그러면
     * 결과가 <b>계약 경계를 안 넘는다</b>.
     *
     * <p>{@code sourceRun} 을 싣지 않는다 — 이전에는 1단계 결과의 {@code runId} 를 넘겼는데
     * 그것은 {@code taskAttemptId} 이지 {@code runs/} 밑 디렉터리가 아니라서 400 이 났다.
     * 원장은 이름표로 정해진다.
     */
    public String bm(String conceptId, String asOf) {
        return bm(conceptId, asOf, null, null);
    }

    /**
     * 2단계 — 이름표 + <b>사용자가 채운 실행 계획</b>.
     *
     * <p>계획 4칸(활동·자원·파트너·고객 관계)은 <b>컨셉 계약이 주지 않는 값</b>이다
     * (입구계약서 §1 의 선택 필드에 없다). 그래서 화면이 따로 받아 여기로 나른다.
     *
     * <p>⚠ 계획은 <b>{@code textContents} 가 아니라 taskInput 최상위</b>로 간다. 컨셉
     * 스냅샷을 문자열로 감싼 것은 그 안에 float 31개가 있어서였는데, 계획은 짧은 문자열과
     * <b>정수</b>뿐이라 감쌀 이유가 없다. 최상위로 두면 AI 쪽이 파싱 없이 읽는다.
     *
     * <p>⚠ 비용은 <b>정수만</b>이다. 「5천만원 정도」 같은 서술을 여기로 보내면 안 된다 —
     * 숫자로 바꾸는 것은 사용자가 화면에서 할 일이고, 우리가 추측하면 사용자가 쓰지 않은
     * 정밀도를 지어내는 것이다.
     */
    public String bm(String conceptId, String asOf, JsonNode planMaterial, JsonNode constraints) {
        ObjectNode root = mapper.createObjectNode();
        // textContents 는 모드와 무관하게 필수다. BM 은 컨셉 식별자만 있으면 되지만
        // **빈 배열은 통과하지 못한다**(1~64개).
        root.set("textContents", textContents("concept-ref", "conceptId=" + conceptId));
        root.put("conceptId", conceptId);
        root.put("asOf", asOf);
        root.put("mode", "BM");
        root.put("llmBudget", 1);
        // 비어 있으면 칸 자체를 만들지 않는다 — 빈 객체를 실으면 AI 쪽에서 「사용자가
        // 안 썼다」와 「사용자가 비웠다」가 같아진다.
        if (planMaterial != null && planMaterial.isObject() && !planMaterial.isEmpty()) {
            root.set("planMaterial", planMaterial);
        }
        if (constraints != null && constraints.isObject() && !constraints.isEmpty()) {
            assertIntegers(constraints);
            root.set("executionConstraints", constraints);
        }
        return finish(root);
    }

    /**
     * 비용 세 칸은 <b>정수여야 한다.</b>
     *
     * <p>{@link #assertNoFloatingPoint} 가 어차피 막지만 메시지가 「taskInput 에 부동소수점이
     * 있다」라 사용자에게 쓸모가 없다. 여기서 먼저 잡아 <b>어느 칸인지</b>를 말한다.
     */
    private static void assertIntegers(JsonNode constraints) {
        for (String name : constraints.propertyNames()) {
            JsonNode value = constraints.get(name);
            if (value == null || value.isNull()) continue;
            if (!value.isIntegralNumber()) {
                throw new IllegalArgumentException(
                    "실행 제약 " + name + " 은 정수여야 한다 — 받은 값: " + value.asText());
            }
        }
    }

    private String finish(ObjectNode root) {
        assertNoFloatingPoint(root, "input");
        return root.toString();
    }

    private ArrayNode textContents(String contentKey, String text) {
        ObjectNode content = mapper.createObjectNode();
        content.put("contentKey", contentKey);
        content.put("contentType", "TEXT");
        content.put("language", "ko-KR");
        content.put("totalCharacters", text.codePointCount(0, text.length()));
        content.put("contentHash", sha256(text));
        ArrayNode chunks = content.putArray("chunks");
        int offset = 0;
        int index = 0;
        while (offset < text.length()) {
            int count = Math.min(CHUNK_CHARACTERS, text.codePointCount(offset, text.length()));
            int end = text.offsetByCodePoints(offset, count);
            String value = text.substring(offset, end);
            ObjectNode chunk = chunks.addObject();
            chunk.put("index", index++);
            chunk.put("text", value);
            chunk.put("characterCount", count);
            chunk.put("chunkHash", sha256(value));
            offset = end;
        }
        ArrayNode contents = mapper.createArrayNode();
        contents.add(content);
        return contents;
    }

    /**
     * 부동소수점이 하나라도 있으면 <b>여기서</b> 막는다.
     *
     * <p>안 막으면 {@code CanonicalInputHasher} 가 던지는데, 그건 감싸이지 않은
     * {@code IllegalArgumentException} 이라 사용자에게 500 으로 나간다.
     * 여기서 막으면 어느 경로에 있는 값인지가 메시지에 남는다.
     */
    static void assertNoFloatingPoint(JsonNode node, String path) {
        if (node.isFloatingPointNumber()) {
            throw new IllegalArgumentException(
                "taskInput 에 부동소수점이 있다: " + path + " = " + node.asText()
                + " — 문자열이나 정수(basis point)로 바꿔라");
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                assertNoFloatingPoint(node.get(name), path + "/" + name);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertNoFloatingPoint(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static String sha256(String text) {
        try {
            return "sha256:" + HexFormat.of().formatHex(java.security.MessageDigest
                .getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
