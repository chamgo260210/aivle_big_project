package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 다듬기 루프를 <b>돌리는</b> 폴러. 제안 자체는 {@code REFINE_FROM_MARKET} 액션이 만들고
 * 그 결과는 {@code ConceptPortfolioSelectionWorker} 가 이미 실어 나른다 — 이 워커는
 * <b>다음 라운드를 걸지 말지</b>만 정한다.
 *
 * <p>왜 별도 폴러인가. 라운드 사이에는 법률(DELTA_LEGAL)이 끼어들고, 그것이 끝나는 시점은
 * 이 워커가 아니라 선택 워커가 안다. 두 워커가 한 상태를 나눠 갖되 <b>겹쳐 쓰지 않는다</b> —
 * 선택 워커는 라운드를 «기록»하고, 이 워커는 라운드를 «건다».
 *
 * <p>멈추는 조건은 셋이다:
 * <ol>
 *   <li>통과한 제안이 없다 — 「고칠 것 없음」</li>
 *   <li>라운드 상한(3)에 닿았다 — 「이건 못 풀었다」를 사유와 함께 남긴다</li>
 *   <li>법률이 통과시켰다 — 수렴</li>
 * </ol>
 *
 * <p>⚠ 셋 중 어느 것도 «성공»과 «실패»가 아니다. 사용자는 최종 화면에서 무엇이 바뀌었고
 * 무엇을 못 풀었는지를 <b>같이</b> 본다.
 */
@Component
public class ConceptRefinementWorker {
    private static final Logger log = LoggerFactory.getLogger(ConceptRefinementWorker.class);

    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementService refinement;
    private final ConceptPortfolioSelectionRepository selections;

    public ConceptRefinementWorker(ConceptRefinementRoundRepository rounds,
            ConceptRefinementService refinement, ConceptPortfolioSelectionRepository selections) {
        this.rounds = rounds;
        this.refinement = refinement;
        this.selections = selections;
    }

    /**
     * 주기가 긴 이유: 한 라운드가 LLM 1회 + 법률 왕복이라 초 단위로 볼 것이 없다.
     * 짧게 폴링해 봐야 빈 조회만 늘어난다.
     */
    @Scheduled(fixedDelayString = "${app.task-run.concept-refinement-poll-interval-ms:5000}")
    public void poll() {
        // 법률 결과가 적힌 라운드만 본다 — 그 전에는 걸 수 있는 다음 걸음이 없다.
        for (ConceptRefinementRound round : rounds.findByLegalOutcomeIsNotNullAndDeletedAtIsNull()) {
            advance(round);
        }
    }

    /**
     * 한 선택의 루프를 한 걸음 민다.
     *
     * <p>⚠ 법률 결과가 아직 없으면 <b>아무것도 하지 않는다.</b> 라운드는 법률이 끝나야
     * 닫힌다 — 그 전에 다음 라운드를 걸면 두 라운드가 같은 컨셉을 동시에 고친다.
     */
    void advance(ConceptRefinementRound round) {
        if (round.getLegalOutcome() == null) return;
        // ⚠ **`FAILED` 에서 다음 라운드를 자동으로 걸지 않는다.** 그것은 「사람이 고른 값이
        // 컨셉 검사에서 서지 못했다」는 뜻이고, 그 상태에서 새 제안을 받는 것은 **유료 호출
        // 1회 + 법률 왕복**이다. 이 모듈의 규율은 「자동 재시도는 없다 — 유료 호출이 사용자
        // 의도 없이 반복되면 되짚을 수 없다」이고(`ConceptRefinementService.retryRound`),
        // 여기서만 예외를 두면 그 규율이 조용히 뚫린다. 사용자가 「다른 제안 받기」를 누르면
        // 그때 돈다.
        if (round.getLegalOutcome() == ConceptRefinementRound.LegalOutcome.FAILED) {
            log.info("Concept refinement waiting for user selectionId={} round={} reason=DECISION_NOT_APPLIED",
                round.getSelectionId(), round.getRound());
            return;
        }
        if (round.getLegalOutcome() == ConceptRefinementRound.LegalOutcome.PASSED) {
            log.info("Concept refinement converged selectionId={} round={}",
                round.getSelectionId(), round.getRound());
            narrate(round);
            return;
        }
        if (!refinement.canRunAnotherRound(round.getSelectionId())) {
            log.info("Concept refinement stopped selectionId={} round={} reason={}",
                round.getSelectionId(), round.getRound(),
                round.getRound() >= ConceptRefinementRound.MAX_ROUNDS ? "ROUND_LIMIT" : "NOTHING_TO_FIX");
            narrate(round);
            return;
        }
        ConceptPortfolioSelection selection = selections.findById(round.getSelectionId()).orElse(null);
        if (selection == null) return;
        // 멱등키에 라운드를 넣는다 — 폴러가 두 번 깨어나도 같은 라운드를 두 번 걸지 않는다.
        // ⚠ **손으로 짓지 않는다.** 예전에는 여기서 문자열을 직접 이어 붙였는데, 키 모양이
        //   조사판을 담게 되자(V31) 이 자리만 옛 모양으로 남아 조용히 갈릴 뻔했다.
        //   정본은 `ConceptRefinementService.roundKey` 하나다.
        String key = ConceptRefinementService.roundKey(
            round.getSelectionId(), round.getResearchVersion(), round.getRound() + 1);
        refinement.queueNextRound(selection.getSelectedByUserId(), selection.getProjectId(),
            selection.getId(), key);
    }

    /**
     * 루프가 끝났으니 <b>최종 컨셉 서술문</b>을 한 번 건다.
     *
     * <p>수렴이든 라운드 상한이든 「고칠 것 없음」이든 <b>끝난 것은 같다</b>. 못 푼 것이 남은
     * 채로 끝났어도 지금까지 바뀐 것은 최종 컨셉이므로 서술문은 필요하다.
     *
     * <p>⚠ <b>루프를 멈추지 않는다.</b> 서술문은 읽기용 곁가지라, 여기서 던진 예외가 폴러를
     * 죽이면 다른 선택의 다듬기까지 멈춘다. 실패하면 서술문만 없고 화면은 칸 나열로 폴백한다.
     * 멱등키가 {@code narrate:<selectionId>} 하나라 폴러가 다시 깨어나도 두 번 걸리지 않는다.
     */
    private void narrate(ConceptRefinementRound round) {
        ConceptPortfolioSelection selection = selections.findById(round.getSelectionId()).orElse(null);
        if (selection == null) return;
        try {
            refinement.queueNarration(selection.getSelectedByUserId(), selection.getProjectId(),
                selection.getId());
        } catch (RuntimeException failure) {
            log.warn("Concept refinement narration not queued selectionId={} reason={}",
                round.getSelectionId(), failure.toString());
        }
    }

    /** 이력 조회용 — 최종 화면이 쓴다. */
    public List<ConceptRefinementRound> history(Long selectionId) {
        return refinement.history(selectionId);
    }
}
