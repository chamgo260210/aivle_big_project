package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.refinement.ConceptRefinementRound.LegalOutcome;
import org.junit.jupiter.api.Test;

/**
 * 다듬기 화면이 <b>어떤 결말을 말하는가</b>. 이 한 줄이 사용자가 사업안을 확정할지 말지를 가른다.
 *
 * <p>2026-08-15 이전에는 두 가지가 거짓이었다:
 * <ol>
 *   <li><b>법이 막았는데 「고칠 것 없음」이라고 했다.</b> 조건이 「법률 결과가 PASSED 가
 *       아니다」였는데 화면 문구는 「시장 근거로 바꿀 것이 나오지 않았어요 — 컨셉은
 *       그대로예요」였다. 막힌 사업안을 <b>안심하고 확정하게 만드는</b> 종류의 거짓이다.</li>
 *   <li><b>진짜 「고칠 것 없음」에는 도달할 수 없었다.</b> 제안 0건이면 아무도 라운드를
 *       닫지 않아 화면이 영영 「다듬는 중」이었다({@link EmptyProposalRoundTests}).</li>
 * </ol>
 */
class RefinementOutcomeTests {

    @Test
    void 법률이_통과시키고_제안도_있으면_수렴이다() {
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.PASSED, 1, true, ConceptRefinementController.Decision.ACCEPTED))
            .isEqualTo("CONVERGED");
    }

    @Test
    void 제안이_0건인_채로_닫힌_라운드가_진짜_고칠_것_없음이다() {
        // 제안 0건 라운드는 PASSED 로 닫는다(닫지 않으면 화면이 안 끝난다).
        // 그래서 「수렴」과 「고칠 것 없음」을 가르는 것은 법률 결과가 아니라 **제안 유무**다.
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.PASSED, 1, false, ConceptRefinementController.Decision.ACCEPTED))
            .isEqualTo("NOTHING_TO_FIX");
    }

    @Test
    void 법이_막았으면_고칠_것_없음이라_하지_않는다() {
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.BLOCKED, 1, true, ConceptRefinementController.Decision.ACCEPTED))
            .isEqualTo("LEGAL_BLOCKED");
    }

    @Test
    void 법률_결과가_아직_없으면_도는_중이다() {
        assertThat(ConceptRefinementController.outcomeOf(null, 1, true, ConceptRefinementController.Decision.ACCEPTED)).isEqualTo("RUNNING");
    }

    @Test
    void 라운드_상한에_닿으면_못_푼_것이_남았다고_한다() {
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.BLOCKED,
            ConceptRefinementRound.MAX_ROUNDS, true, ConceptRefinementController.Decision.ACCEPTED))
            .isEqualTo("ROUND_LIMIT");
    }

    @Test
    void 상한에_닿아도_법률이_통과시켰으면_수렴이다() {
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.PASSED,
            ConceptRefinementRound.MAX_ROUNDS, true, ConceptRefinementController.Decision.ACCEPTED))
            .isEqualTo("CONVERGED");
    }

    // ───── 사람이 고르는 문이 생긴 뒤의 결말들 ─────

    @Test
    void 제안이_있고_아직_안_골랐으면_고를_차례다() {
        assertThat(ConceptRefinementController.outcomeOf(null, 1, true,
            ConceptRefinementController.Decision.NONE)).isEqualTo("AWAITING_DECISION");
    }

    @Test
    void 라운드_상한에_닿아도_고를_차례가_먼저다() {
        // 순서를 뒤집으면 3라운드째에 체크박스가 안 뜨고 확정 버튼만 뜬다.
        // 법이 막으면 워커가 다음 라운드를 자동으로 걸므로 3라운드는 실제로 도달한다.
        assertThat(ConceptRefinementController.outcomeOf(null, ConceptRefinementRound.MAX_ROUNDS,
            true, ConceptRefinementController.Decision.NONE)).isEqualTo("AWAITING_DECISION");
    }

    @Test
    void 전부_넘겼으면_고를_차례가_아니다() {
        assertThat(ConceptRefinementController.outcomeOf(null, 1, true,
            ConceptRefinementController.Decision.DECLINED)).isEqualTo("DECLINED");
    }

    @Test
    void 고른_값이_검사에_걸린_것은_법이_막은_것과_다르다() {
        assertThat(ConceptRefinementController.outcomeOf(LegalOutcome.FAILED, 1, true,
            ConceptRefinementController.Decision.ACCEPTED)).isEqualTo("DECISION_NOT_APPLIED");
    }

    @Test
    void 골랐고_법률을_기다리는_중이면_도는_중이다() {
        assertThat(ConceptRefinementController.outcomeOf(null, 1, true,
            ConceptRefinementController.Decision.ACCEPTED)).isEqualTo("RUNNING");
    }
}
