package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.taskrun.domain.TaskRunState;
import org.junit.jupiter.api.Test;

/**
 * 실패한 다듬기 라운드를 <b>다시 걸 수 있게</b> 만드는 키 규칙. (2026-08-14)
 *
 * <p>여기가 왜 필요했나. 멱등키가 {@code refine:<selectionId>:<round>} 로 고정이라
 * {@code TaskRunService.createWithDisposition} 이 <b>상태를 보지 않고</b> 기존 실행을 돌려준다 —
 * FAILED 여도 그렇다. 그래서 한 번 실패한 라운드는 영영 다시 걸리지 않았고, 화면은 그것을
 * 「아직 안 함」으로 보였다.
 *
 * <p>고친 방법은 멱등을 «푸는» 것이 아니라 키를 하나 «더 만드는» 것이다. 그래서 이 파일이
 * 지키는 것은 딱 둘이다 — <b>첫 시도 키는 안 바뀐다</b>, 그리고 <b>재시도는 갈라진다</b>.
 */
class RefinementRetryKeyTests {

    @Test
    void theFirstAttemptKeepsTheOldKeyShape() {
        // ⚠ 여기에 `:r1` 을 붙이면 이미 DB 에 있는 라운드들과 키가 갈려
        //    「몇 번 시도했나」가 조용히 틀린다. 옛 실행이 목록에서 통째로 사라진다.
        assertThat(ConceptRefinementService.attemptKey(4L, null, 1, 1)).isEqualTo("refine:4:1");
        assertThat(ConceptRefinementService.attemptKey(4L, null, 1, 1))
            .isEqualTo(ConceptRefinementService.roundKey(4L, null, 1));
    }

    @Test
    void retriesGetDistinctKeysSoANewRunActuallyStands() {
        assertThat(ConceptRefinementService.attemptKey(4L, null, 1, 2)).isEqualTo("refine:4:1:r2");
        assertThat(ConceptRefinementService.attemptKey(4L, null, 1, 3)).isEqualTo("refine:4:1:r3");
        assertThat(ConceptRefinementService.attemptKey(4L, null, 2, 2)).isEqualTo("refine:4:2:r2");
    }

    @Test
    void everyAttemptOfARoundSharesThePrefixThatFindsThem() {
        // 시도를 세는 조회는 접두사로 훑는다. 첫 시도가 그 접두사 «자신»이라 같이 잡혀야 한다.
        String prefix = ConceptRefinementService.roundKey(4L, null, 1);
        for (int attempt = 1; attempt <= ConceptRefinementService.MAX_ROUND_ATTEMPTS; attempt++) {
            assertThat(ConceptRefinementService.attemptKey(4L, null, 1, attempt)).startsWith(prefix);
        }
    }

    @Test
    void aDifferentRoundIsNeverSweptUpByAnotherRoundsPrefix() {
        assertThat(ConceptRefinementService.attemptKey(4L, null, 2, 1))
            .doesNotStartWith(ConceptRefinementService.roundKey(4L, null, 1));
        // 선택 4 의 접두사가 선택 41 을 줍지 않는다.
        assertThat(ConceptRefinementService.attemptKey(41L, null, 1, 1))
            .doesNotStartWith(ConceptRefinementService.roundKey(4L, null, 1));
    }

    /**
     * ★ <b>조사판이 다르면 키도 갈린다.</b> (2026-08-16)
     *
     * <p>이것이 없으면 V31 의 「조사판마다 다시 돈다」가 <b>조용히 죽는다</b>. 새 주기는
     * 라운드를 1부터 다시 세는데, 키가 옛 주기의 라운드 1과 같으면
     * {@code createWithDisposition} 이 <b>옛 TaskRun 을 재생</b>하고 팩토리가
     * 「replay authority mismatch」를 던진다. 그 예외는 로그로만 삼켜지므로
     * <b>사용자에게는 아무 일도 안 일어난 것처럼 보인다.</b>
     */
    @Test
    void aNewResearchCycleNeverReusesTheOldCyclesKey() {
        String 옛주기 = ConceptRefinementService.roundKey(4L, null, 1);
        String 새주기 = ConceptRefinementService.roundKey(4L, 8, 1);
        assertThat(새주기).isNotEqualTo(옛주기);
        // 접두사 조회가 서로를 줍지 않아야 「몇 번 시도했나」가 주기별로 맞는다.
        assertThat(ConceptRefinementService.attemptKey(4L, 8, 1, 2)).startsWith(새주기);
        assertThat(ConceptRefinementService.attemptKey(4L, 8, 1, 2)).doesNotStartWith(옛주기);
        assertThat(ConceptRefinementService.roundKey(4L, 9, 1)).isNotEqualTo(새주기);
    }

    /**
     * ★ <b>결정 → 가설 확정</b> 열쇠도 주기마다 갈려야 한다. (2026-08-16)
     *
     * <p>화면이 주는 열쇠는 {@code refine-decide-<선택>-<라운드>} 다. 새 주기는 라운드를
     * 1부터 다시 세므로 옛 주기의 결정과 <b>같은 열쇠</b>가 되고, 고른 칸이 다르면 입력 해시가
     * 달라 {@code IDEMPOTENCY_CONFLICT} 로 거절된다 — 사용자에게는 「요청을 완료하지
     * 못했습니다」만 뜨고 <b>새 주기의 첫 반영이 통째로 막힌다.</b>
     */
    @Test
    void aDecisionInANewCycleNeverCollidesWithTheOldCyclesApplyKey() {
        String 화면열쇠 = "refine-decide-4-1";
        assertThat(ConceptRefinementService.applyKey(화면열쇠, 8))
            .isNotEqualTo(ConceptRefinementService.applyKey(화면열쇠, null));
        assertThat(ConceptRefinementService.applyKey(화면열쇠, 9))
            .isNotEqualTo(ConceptRefinementService.applyKey(화면열쇠, 8));
        // 옛 라운드는 모양이 그대로여야 이미 선 실행과 안 갈린다.
        assertThat(ConceptRefinementService.applyKey(화면열쇠, null)).isEqualTo(화면열쇠);
    }

    @Test
    void aVersionlessRoundKeepsTheShapeAlreadyInTheDatabase() {
        // V31 이전 행은 조사판을 모른다. 그 키가 갈리면 옛 실행이 목록에서 사라진다.
        assertThat(ConceptRefinementService.roundKey(4L, null, 1)).isEqualTo("refine:4:1");
    }

    @Test
    void onlyFinishedRunsCountAsTerminal() {
        // 아직 살아 있는 시도가 하나라도 있으면 재시도를 «막아야» 한다 —
        // 사용자가 성급히 두 번 누르면 같은 라운드가 두 번 돌고 둘 다 유료다.
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.QUEUED)).isFalse();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.READY)).isFalse();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.RUNNING)).isFalse();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.NEEDS_INPUT)).isFalse();

        assertThat(ConceptRefinementService.isTerminal(TaskRunState.SUCCEEDED)).isTrue();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.FAILED)).isTrue();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.CANCELLED)).isTrue();
        assertThat(ConceptRefinementService.isTerminal(TaskRunState.TIMED_OUT)).isTrue();
    }

    @Test
    void everyStateIsClassified() {
        // 상태가 하나 늘었는데 여기에 안 적으면 「안 끝난 것」으로 취급돼 재시도가 영영 막힌다.
        for (TaskRunState state : TaskRunState.values()) {
            ConceptRefinementService.isTerminal(state);
        }
        assertThat(TaskRunState.values()).hasSize(8);
    }
}
