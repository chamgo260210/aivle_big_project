package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>실측 봉투</b>를 계약에 통과시킨다 — 골든 픽스처가 못 잡는 자리를 위해.
 *
 * <p>{@link MarketResearchContractTests} 는 <b>손으로 쓴</b> 픽스처를 읽는다. 그것이 정답이다 —
 * 실행 산출을 픽스처로 구우면 스키마가 아니라 「그날의 결과」를 고정하게 되기 때문이다.
 * 그런데 그 대가로 <b>손이 상상하지 못한 모양은 영원히 안 들어온다.</b> 실제 원장에는
 * 픽스처에 없는 조합이 나온다 — 값이 {@code null} 인 승격 카드, 인용이 없는 행,
 * 연도가 빈 출처 같은 것들이다.
 *
 * <p>그래서 유료 스모크가 남긴 <b>진짜 봉투</b>를 여기서 한 번 통과시킨다. 파이썬 스모크는
 * AI 서버까지만 보고, {@code exact()} 로 못박은 자바 계약은 대신하지 못한다.
 *
 * <p><b>파일이 없으면 건너뛴다.</b> 유료 실행은 아무 때나 돌 수 없으므로 이 검사를 필수로
 * 만들면 CI 가 돈을 요구하게 된다. 있을 때만 재는 것이 이 검사의 값이다.
 */
class MarketResearchLiveEnvelopeTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DIR = "ai/app/research/research2/runs-generated/";

    /**
     * ⚠ <b>두 모드를 다 본다.</b> {@code FULL} 봉투가 통과한다는 사실이 {@code VALIDATION}
     * 봉투가 통과한다는 뜻이 아니다 — 뒤엣것은 두 봉투를 {@code runner._merge} 로 합친
     * 것이고, <b>사용자가 실제로 받는 것은 그쪽</b>이다.
     */
    private static Path locate(String name) {
        String override = System.getProperty("live.envelope");
        if (override != null && !override.isBlank()) return Paths.get(override);
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve(DIR + name);
            if (Files.exists(candidate)) return candidate;
            root = root.getParent();
            if (root == null) break;
        }
        return Paths.get(DIR + name);
    }

    private static void 통과해야_한다(String name) throws Exception {
        Path path = locate(name);
        Assumptions.assumeTrue(Files.exists(path),
            "실측 봉투가 없다 — 유료 스모크를 돌린 뒤에만 재는 검사다: " + path);

        JsonNode envelope = MAPPER.readTree(Files.readString(path));
        assertThatCode(() -> MarketResearchContract.validate(envelope)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유료 FULL 실행이 실제로 낸 봉투가 계약을 통과한다")
    void liveFullEnvelopePassesTheContract() throws Exception {
        통과해야_한다("p43-smoke-01-envelope.json");
    }

    @Test
    @DisplayName("사용자가 받는 VALIDATION 봉투(FULL+BM 합침)가 계약을 통과한다")
    void liveValidationEnvelopePassesTheContract() throws Exception {
        통과해야_한다("p43-smoke-01-validation.json");
    }
}
