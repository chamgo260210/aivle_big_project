package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다듬기 결과를 읽는 자리. <b>라운드 이력은 DB 에만 두고</b> 화면에는 변경 표와 판정만 준다.
 *
 * <p>왜 이력을 화면에 안 내나. 사용자가 볼 것은 「최종 컨셉이 무엇이고 무엇이 왜 바뀌었나」다.
 * 3라운드의 시행착오를 그대로 보이면 결론이 묻힌다. 다만 <b>못 푼 것은 반드시 보인다</b> —
 * 수렴 못 한 채 끝난 것을 성공처럼 보이면 그것이 조용한 거짓말이다.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectId}/concept-refinement")
public class ConceptRefinementController {

    private final ConceptRefinementService refinement;
    private final ConceptPortfolioSelectionService selectionService;
    private final com.aivle.backend.pipeline.conceptportfolio.selection.repository
        .ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final com.aivle.backend.pipeline.marketseed.repository
        .MarketAnalysisSeedSnapshotRepository seeds;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final CurrentUserProvider currentUser;

    public ConceptRefinementController(ConceptRefinementService refinement,
            ConceptPortfolioSelectionService selectionService,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptPortfolioDeltaLegalReviewRepository deltas,
            com.aivle.backend.pipeline.marketseed.repository
                .MarketAnalysisSeedSnapshotRepository seeds,
            tools.jackson.databind.ObjectMapper mapper, CurrentUserProvider currentUser) {
        this.refinement = refinement;
        this.selectionService = selectionService;
        this.deltas = deltas;
        this.seeds = seeds;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<RefinementView> current(@PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam Long selectionId,
            HttpServletRequest request) {
        currentUser.currentUserId();
        List<ConceptRefinementRound> rounds = refinement.history(selectionId);
        return ApiResponse.success(
            view(rounds, deltaLegal(selectionId), selectionId,
                refinement.retryStatus(projectId, selectionId), finalized(selectionId)),
            id(request));
    }

    /**
     * <b>실패한 라운드를 다시 건다.</b> 사용자가 눌러야만 돈다 — 자동 재시도는 없다.
     *
     * <p>이 문이 생기기 전에는 한 번 실패한 라운드가 <b>영영 다시 걸리지 않았다</b>.
     * 멱등키가 라운드로 고정이라 {@code TaskRunService} 가 FAILED 인 옛 실행을 그대로
     * 돌려줬기 때문이다. 사유는 {@link ConceptRefinementService#retryRound} 에.
     *
     * <p>돌고 있거나 · 이미 성공했거나 · 시도 상한(3)을 다 썼으면
     * {@code JOB_RETRY_NOT_ALLOWED} 로 거절한다. <b>조용히 무시하지 않는다.</b>
     */
    @PostMapping("/retry")
    public ApiResponse<RetryAccepted> retry(@PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam Long selectionId,
            HttpServletRequest request) {
        Long ownerId = currentUser.currentUserId();
        var task = refinement.retryRound(ownerId, projectId, selectionId);
        return ApiResponse.success(
            new RetryAccepted(task.getId(), refinement.retryStatus(projectId, selectionId)),
            id(request));
    }

    /** 재시도를 받았다는 표. 화면은 {@code taskRunId} 로 진행을 따라간다. */
    public record RetryAccepted(String taskRunId, ConceptRefinementService.RetryStatus retry) { }

    /**
     * <b>사람이 고른 것만 적용한다.</b> 이 판이 세우는 정의 그 자체다.
     *
     * <p>이 문이 생기기 전에는 AI 결과가 채택되는 순간 <b>전량이 자동 적용</b>됐다
     * ({@code ConceptPortfolioSelectionMaterializationService} — 코드 전체에서 그 한 곳뿐이었다).
     * 사용자는 「가격을 9,500원으로 하시죠」라는 말과 <b>이미 바뀐 값</b>을 동시에 봤다.
     *
     * <p>{@code fieldKeys} 가 빈 목록이면 <b>「전부 넘김」</b>이다 — 컨셉을 그대로 두고 루프를
     * 끝낸다. 다른 제안을 받고 싶으면 {@link #retry} 를 누른다.
     *
     * <p>⚠ 한 라운드는 <b>한 번만</b> 결정을 받는다. 두 번째는 {@code MODULE_INPUT_STALE} 로
     * 거절한다 — 두 번 적용되면 가설 확정이 두 번 걸린다.
     */
    @PostMapping("/decide")
    public ApiResponse<RefinementView> decide(@PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam Long selectionId,
            @org.springframework.web.bind.annotation.RequestBody DecideRequest body,
            HttpServletRequest request) {
        Long ownerId = currentUser.currentUserId();
        refinement.decide(ownerId, projectId, selectionId, body.round(), body.fieldKeys(),
            body.idempotencyKey());
        return current(projectId, selectionId, request);
    }

    /**
     * @param round          결정하는 라운드. 화면이 본 그 라운드가 맞는지 서버가 대조한다
     * @param fieldKeys      체크한 칸. <b>빈 목록은 「전부 넘김」</b>이고 {@code null} 과 같이 다룬다
     * @param idempotencyKey 가설 확정으로 이어질 때 그 실행의 멱등키가 된다
     */
    public record DecideRequest(int round, List<String> fieldKeys, String idempotencyKey) { }

    /**
     * 다듬기가 마지막으로 받은 <b>델타 법률 검토</b>.
     *
     * <p>⚠ <b>전체 법률보고서가 아니다.</b> 다듬기는 바뀐 가설(1~5종)만 다시 법률에 태우고
     * ({@code DELTA_LEGAL}), 그 결과의 {@code legalReview} 는 <b>그 가설들에 걸리는 법</b>만
     * 담는다. 화면 2 가 전체 보고서를 그리면 안 바뀐 조항까지 8건이 늘어서 「이번에 무엇이
     * 걸렸나」가 묻힌다(2026-08-13 실측: 사용자가 「왤케 많아」로 반려했다).
     *
     * <p>없으면 {@code null} — 아직 델타를 돈 적이 없다는 뜻이고, 화면은 그 자리를 비운다.
     * 전체 보고서로 «대신 채우지» 않는다. 그건 부분 검사를 전체 검사로 보이게 만든다.
     */
    private DeltaLegalView deltaLegal(Long selectionId) {
        List<com.aivle.backend.pipeline.conceptportfolio.selection.domain
            .ConceptPortfolioDeltaLegalReview> all =
            deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(selectionId);
        if (all.isEmpty()) return null;
        var last = all.get(all.size() - 1);
        List<String> types = new java.util.ArrayList<>();
        for (JsonNode type : mapper.readTree(last.getHypothesisTypesJson())) types.add(type.asText());
        // ⚠ 저장된 것은 검토 본문이 아니라 «액션 결과 통째»다. 그대로 넘기면 화면이
        // `officialEvidenceReferences` 를 못 찾아 «걸린 법이 없다»고 잘못 말한다.
        return new DeltaLegalView(last.getStatus(), last.isApproved(), types,
            ConceptRefinementService.unwrapLegalReview(mapper.readTree(last.getLegalReviewJson())));
    }

    /**
     * <b>시장 검증 후 최종 확정.</b> 이것이 최종 컨셉이고 하류(기술·운영·재무·마케팅)가 읽는다.
     *
     * <p>컨셉 단계의 확정은 「가설 확정」, 여기의 확정은 「시장 검증 후 최종 확정」이다.
     * 둘은 다른 일인데 <b>지나는 문은 같다</b> — 법률보고서 확정 → 시드 재발급
     * ({@code BUILD_HANDOFF}). 다듬기가 가설을 건드렸으면 {@code staleDependents()} 가 이미
     * 그 둘을 STALE 로 만들어 뒀으므로, 우회하지 않고 순서대로 다시 통과한다.
     *
     * <p>⚠ <b>컨셉 원본 candidate 는 덮지 않는다.</b> 캐노니컬 해시와 계보가 흔들린다 —
     * 바뀐 것은 가설과 BM 계획이고, 최종 컨셉은 그 둘이 얹힌 시드다.
     *
     * <p>⚠ <b>수렴하지 못했어도 막지 않는다.</b> 3라운드에 못 푼 채로도 사용자는 확정할 수
     * 있어야 한다 — 다만 무엇을 못 풀었는지를 {@link #current} 가 함께 보인다. 막아 버리면
     * 사용자가 자기 사업안을 앞으로 못 끌고 간다.
     */
    @PostMapping("/finalize")
    public ApiResponse<ConceptPortfolioSelectionApiModels.ActionAccepted> finalizeConcept(
            @PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam Long selectionId,
            @org.springframework.web.bind.annotation.RequestBody
                ConceptPortfolioSelectionApiModels.ActionRequest body,
            HttpServletRequest request) {
        Long ownerId = currentUser.currentUserId();
        // ⚠ **아직 답하지 않은 제안을 두고 확정하지 못한다.** 화면은 버튼을 감추지만
        // 그것은 화면의 예의일 뿐이고, 문은 여기서 잠근다 — 안 잠그면 curl 한 줄로
        // 「사람이 고른 것만 적용된다」가 무너진다.
        List<ConceptRefinementRound> history = refinement.history(selectionId);
        ConceptPortfolioSelectionApiModels.ActionRequest keyed = body;
        if (!history.isEmpty()) {
            ConceptRefinementRound last = history.get(history.size() - 1);
            if (last.getLegalOutcome() == null && decisionOf(last) == Decision.NONE
                    && !refinement.proposalsOf(last).isEmpty()) {
                throw new com.aivle.backend.common.exception.BusinessException(
                    com.aivle.backend.common.exception.ErrorCode.MODULE_INPUT_STALE);
            }
            // ⚠⚠ **확정 열쇠에 «어느 컨셉을 확정하는가»를 담는다.** 화면이 주는 열쇠는
            //    `refine-finalize-<선택>` 하나뿐이라, 다듬기를 한 번 더 돌려 컨셉이 바뀌어도
            //    **같은 열쇠로 다른 내용**을 확정하려 든다 → `IDEMPOTENCY_CONFLICT` 로 거절되고
            //    사용자에게는 「요청을 완료하지 못했습니다」만 뜬다. 확정 자체가 영영 막힌다
            //    (2026-08-16 실측). 화면은 조사판도 라운드도 모른다 — 아는 쪽이 담는다.
            //    ⚠ 조사판을 모르는 옛 라운드에서 라운드가 1이면 **옛 열쇠 그대로**다.
            keyed = new ConceptPortfolioSelectionApiModels.ActionRequest(
                ConceptRefinementService.applyKey(body.idempotencyKey(), last.getResearchVersion())
                    + (last.getRound() > 1 ? ":r" + last.getRound() : ""));
        }
        selectionService.finalizeReport(ownerId, projectId, selectionId);
        return ApiResponse.success(
            selectionService.finalizeMarketSeed(ownerId, projectId, selectionId, keyed), id(request));
    }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    /**
     * 이 사업안이 <b>이미 확정됐나</b>.
     *
     * <p>⚠ 이것이 없으면 확정 버튼이 확정 뒤에도 그대로 서 있고, 사용자가 다시 누르면
     * 서버가 {@code IDEMPOTENCY_CONFLICT} 로 거절한다 — <b>성공한 일을 「실패」로 보여
     * 계속 누르게 만든다</b>(2026-08-16 실측: 확정은 17:05 에 이미 성공해 있었다).
     */
    private boolean finalized(Long selectionId) {
        // 다듬기 전 최초 시드도 살아 있으므로 존재만으로는 최종 확정을 뜻하지 않는다.
        // BUILD_HANDOFF가 다듬기 이후 발급한 시드에 명시한 표를 함께 확인한다.
        return seeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selectionId)
            .filter(MarketAnalysisSeedSnapshot::isRefinementApplied)
            .isPresent();
    }

    private RefinementView view(List<ConceptRefinementRound> rounds, DeltaLegalView deltaLegal,
            Long selectionId, ConceptRefinementService.RetryStatus retry, boolean finalized) {
        if (rounds.isEmpty()) {
            // ⚠ 라운드 행은 채택 성공 때만 생긴다. 그래서 「시작 전」과 「1라운드가 실패함」이
            // 여기서는 구별되지 않는다 — 실행 쪽을 본 retry 만 그것을 안다.
            // 실패를 NOT_STARTED 로 보이면 사용자는 영원히 기다린다.
            return new RefinementView(retry.failed() ? "FAILED" : "NOT_STARTED", 0,
                List.of(), List.of(), deltaLegal, List.of(), retry, finalized);
        }
        ConceptRefinementRound last = rounds.get(rounds.size() - 1);
        String outcome = outcomeOf(last.getLegalOutcome(), rounds.size(),
            !refinement.proposalsOf(last).isEmpty(), decisionOf(last));
        // 라운드가 이미 있어도 «다음» 라운드가 실패로 끝났을 수 있다. 그때 outcomeOf 는
        // 마지막 «성공한» 라운드만 보고 RUNNING/NOTHING_TO_FIX 라고 말한다.
        if (retry.failed()) outcome = "FAILED";
        // ⚠ **서술문 번호는 «채택분» 위에서 매겨진다.** 그래서 여기서 라운드를 가로질러
        //   하나의 카운터로 센다 — 라운드마다 1부터 세면 2라운드의 1번이 1라운드의 1번을 덮는다.
        int[] narrativeRef = { 0 };
        List<Change> changes = rounds.stream()
            .flatMap(round -> changesOf(round, narrativeRef).stream())
            .toList();
        List<String> unresolved = rounds.stream()
            .flatMap(round -> unresolvedOf(round).stream())
            .toList();
        // 「컨셉에 반영했어요」는 AI 가 못 정한다 — 이 조항이 이번 다듬기를 낳았는지는
        // 제안의 `legalRef` 만 안다. 그래서 여기서 덮는다.
        markReflectedClauses(deltaLegal, changes);
        return new RefinementView(outcome, rounds.size(), changes, unresolved, deltaLegal,
            refinement.narrativeOf(selectionId), retry, finalized);
    }

    /**
     * 법률이 시킨 변경이 실제로 반영된 조항에 <b>{@code REFLECTED}</b> 를 찍는다.
     *
     * <p>대조는 {@code legalRef} 문자열이 조항의 법령명·조문을 <b>모두</b> 담고 있는지로 한다.
     * 느슨하게 법령명만 맞춰 찍으면 같은 법의 다른 조항까지 「반영했어요」가 되어,
     * 사실은 아직 걸려 있는 것을 다 끝난 것처럼 보인다.
     */
    static void markReflectedClauses(DeltaLegalView deltaLegal, List<Change> changes) {
        if (deltaLegal == null || !deltaLegal.legalReview().isObject()) return;
        List<String> refs = changes.stream()
            .filter(change -> "LEGAL".equals(change.source()))
            .map(Change::legalRef)
            .filter(value -> value != null && !value.isBlank())
            .toList();
        if (refs.isEmpty()) return;
        for (JsonNode clause : deltaLegal.legalReview().path("officialEvidenceReferences")) {
            String lawName = clause.path("lawName").asText("");
            String article = clause.path("articleReference").asText("");
            if (lawName.isBlank()) continue;
            boolean hit = refs.stream()
                .anyMatch(ref -> ref.contains(lawName) && (article.isBlank() || ref.contains(article)));
            if (hit && clause instanceof tools.jackson.databind.node.ObjectNode node) {
                node.put("conceptStatus", "REFLECTED");
            }
        }
    }

    /**
     * 결말은 <b>성공·실패가 아니다.</b>
     * <ul>
     *   <li>{@code CONVERGED} — 바꾼 것이 있고 법률까지 통과했다</li>
     *   <li>{@code NOTHING_TO_FIX} — 고칠 것이 없었다 (제안 0건)</li>
     *   <li>{@code LEGAL_BLOCKED} — <b>법이 막았다</b>. 사유를 함께 보인다</li>
     *   <li>{@code ROUND_LIMIT} — 3라운드에 못 풀었다. <b>사유를 함께 보인다</b></li>
     *   <li>{@code RUNNING} — 아직 법률 결과가 안 적혔다</li>
     * </ul>
     *
     * <p>⚠ <b>2026-08-15 정정.</b> 이 메서드는 「법률 결과가 PASSED 가 아니다」를 통째로
     * {@code NOTHING_TO_FIX} 라고 불렀고, 화면은 그것을 <b>「시장 근거로 바꿀 것이 나오지
     * 않았어요 — 컨셉은 그대로예요」</b>라고 적었다. 즉 <b>법에 막힌 사업안을 「괜찮다」고
     * 읽히게 만들어 사용자가 안심하고 확정하게 했다.</b>
     *
     * <p>⚠ 그리고 <b>진짜 「고칠 것 없음」에는 도달할 수 없었다</b> — 제안 0건 라운드는
     * 아무도 닫지 않아 영영 {@code RUNNING} 이었다. 이제 그 라운드를 {@code PASSED} 로 닫으므로
     * (`ConceptPortfolioSelectionMaterializationService`), 「수렴」과 「고칠 것 없음」을 가르는
     * 것은 법률 결과가 아니라 <b>제안이 있었는가</b>다.
     *
     * <p>⚠ {@code LegalOutcome.FAILED} 를 쓰는 코드는 프로덕션에 없다(enum·DB CHECK 에만 있다).
     * 없는 경로에 문구를 만들지 않고 {@code LEGAL_BLOCKED} 가 같이 받는다.
     */
    /** 사람이 그 라운드에 <b>답했는가</b>. 「아직」과 「전부 넘김」은 다른 사실이다. */
    enum Decision { NONE, ACCEPTED, DECLINED }

    static String outcomeOf(ConceptRefinementRound.LegalOutcome outcome, int roundCount,
            boolean hasProposals, Decision decision) {
        if (outcome == ConceptRefinementRound.LegalOutcome.PASSED) {
            return hasProposals ? "CONVERGED" : "NOTHING_TO_FIX";
        }
        // 법을 보지도 못하고 값 검사에서 선 라운드. 「법이 막았다」와 섞으면 안 된다.
        if (outcome == ConceptRefinementRound.LegalOutcome.FAILED) return "DECISION_NOT_APPLIED";
        if (outcome == ConceptRefinementRound.LegalOutcome.BLOCKED) {
            return roundCount >= ConceptRefinementRound.MAX_ROUNDS ? "ROUND_LIMIT" : "LEGAL_BLOCKED";
        }
        // ⚠ **여기부터는 «열린» 라운드다. 결정 상태를 라운드 상한보다 «먼저» 본다.**
        //   순서를 뒤집으면 3라운드째에 `ROUND_LIMIT` 이 「고를 차례」를 통째로 잡아먹어
        //   **체크박스가 안 뜨고 확정 버튼만 뜬다**(법이 막으면 워커가 라운드를 자동으로 걸어
        //   3라운드는 실제로 도달한다).
        if (decision == Decision.DECLINED) return "DECLINED";
        if (hasProposals && decision == Decision.NONE) return "AWAITING_DECISION";
        if (roundCount >= ConceptRefinementRound.MAX_ROUNDS) return "ROUND_LIMIT";
        return "RUNNING";
    }

    /**
     * 그 라운드에 사람이 답했는가.
     *
     * <p>⚠ <b>결정 칸이 생기기 «전»에 저장된 라운드는 {@code null} 이다.</b> 그것들은 옛 규칙
     * (전량 자동 적용)으로 이미 적용된 것이라 「고를 차례」로 보이면 안 된다 — 그래서 이 판정은
     * <b>열린 라운드에서만</b> 쓰인다({@link #outcomeOf}). 닫힌 옛 라운드는 그대로 결말을 낸다.
     */
    private Decision decisionOf(ConceptRefinementRound round) {
        if (round.getAcceptedFieldsJson() == null) return Decision.NONE;
        return refinement.acceptedOf(round).isEmpty() ? Decision.DECLINED : Decision.ACCEPTED;
    }

    /**
     * @param narrativeRef 라운드를 가로지르는 <b>서술문 번호 카운터</b>. 아래 ⚠ 참조.
     */
    private List<Change> changesOf(ConceptRefinementRound round, int[] narrativeRef) {
        // ⚠ **전량을 내되 「고른 것인가」를 같이 실어 보낸다.** 화면의 체크 상태가 이것이고,
        //   사용자는 자기가 «무엇을 넘겼는지»도 볼 수 있어야 한다 — 목록에서 지우면
        //   「내가 거절한 제안이 있었다」는 사실 자체가 사라진다.
        //   `null` = 아직 결정 전(옛 라운드 포함).
        java.util.Set<String> accepted = refinement.acceptedOf(round);
        boolean decided = round.getAcceptedFieldsJson() != null;
        List<Change> changes = new java.util.ArrayList<>();
        for (JsonNode node : refinement.proposalsOf(round)) {
            List<String> evidence = new java.util.ArrayList<>();
            for (JsonNode item : node.path("evidenceIds")) evidence.add(item.asText());
            String legalRef = node.path("legalRef").isTextual() ? node.path("legalRef").asText() : null;
            changes.add(new Change(round.getRound(), node.path("fieldKey").asText(),
                node.path("title").asText(""),
                display(node.path("beforeText"), node.path("currentValue")),
                display(node.path("afterText"), node.path("proposedValue")),
                node.path("rationale").asText(), evidence,
                node.path("source").asText("MARKET"), legalRef,
                decided ? accepted.contains(node.path("fieldKey").asText()) : null,
                // ⚠⚠ **서술문의 초록 조각이 착지할 번호.** 서버는 서술문 재료와 검증을
                //   «채택분만»으로 만드는데(`queueNarration`·`narrativeMatchesChanges`)
                //   화면 변경표는 «전량»을 그린다. 화면의 순번(1,2,3…)을 그대로 쓰면
                //   **초록 글씨를 눌렀을 때 사용자가 «거절한» 제안의 이유 칸으로 착지한다** —
                //   오류도 안 나고 화면만 틀린다. 그래서 여기서 «같은 규칙»으로 번호를 매겨
                //   같이 보낸다. 조건은 세 곳이 정확히 같아야 한다.
                included(round, accepted, node) ? ++narrativeRef[0] : null,
                // 값 자체. 목록이면 사람이 읽게 「, 」로 잇는다 — JSON 대괄호를 화면에 세우지 않는다.
                valueText(node.path("proposedValue"))));
        }
        return changes;
    }

    /**
     * 화면에 세울 한 줄. <b>사람이 읽는 문자열이 있으면 그것</b>, 없으면 원래 값을 편다.
     *
     * <p>폴백이 필요한 이유: 이 칸이 생기기 전에 저장된 라운드가 있다. 그것들은
     * {@code beforeText} 가 없어 값이 목록이면 <b>JSON 문자열이 그대로</b> 화면에 뜬다 —
     * 보기 나쁘지만 <b>거짓은 아니다</b>. 빈 줄로 두면 「안 바뀌었다」로 읽힌다.
     */
    /**
     * 값을 <b>사람이 읽는 한 줄</b>로. 목록은 「, 」로 잇는다.
     *
     * <p>⚠ {@code toString()} 을 쓰지 않는다 — 목록이 {@code ["자사몰","쿠팡"]} 처럼
     * 대괄호와 따옴표째로 화면에 선다. 값이 없으면 <b>빈 문자열</b>이고, 화면이 그때
     * 「설명」으로 되돌아간다.
     */
    private String valueText(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) return "";
        if (raw.isTextual()) return raw.asText();
        if (raw.isArray()) {
            List<String> parts = new java.util.ArrayList<>();
            for (JsonNode item : raw) parts.add(item.isTextual() ? item.asText() : item.toString());
            return String.join(", ", parts);
        }
        return raw.toString();
    }

    private String display(JsonNode text, JsonNode raw) {
        if (text != null && text.isTextual() && !text.asText().isBlank()) return text.asText();
        if (raw == null || raw.isNull() || raw.isMissingNode()) return "";
        return raw.isTextual() ? raw.asText() : raw.toString();
    }

    /**
     * 계약이나 법률이 막은 것 — 「이건 못 풀었다」로 함께 보인다.
     *
     * <p>⚠ 주석은 원래 「계약이나 <b>법률</b>이 막은 것」이라고 적고 있었는데 실제로는
     * {@code drift_rejections_json} 만 읽었다. {@code legal_reasons_json} 은 V25 부터
     * 채워지고 있었는데 <b>화면 어디로도 안 갔다.</b> 말과 코드가 갈려 있던 자리다.
     *
     * <p>⚠ <b>칸 이름을 여기서 한글로 옮기지 않는다.</b> 그 표는 프론트
     * ({@code conceptRevision.REVISION_FIELD_LABEL})에 이미 있고, 여기에 사본을 두면 두 표가
     * 갈린다 — 실제로 프론트는 {@code targetRegion} 을 「대상 지역」이라 부르는데 손으로 옮기면
     * 「타깃 지역」이 되기 쉽다. 한 화면에서 같은 칸이 두 이름으로 불리는 것은 고장이다.
     */
    private List<String> unresolvedOf(ConceptRefinementRound round) {
        List<String> lines = new java.util.ArrayList<>();
        for (JsonNode node : refinement.rejectionsOf(round)) {
            lines.add(node.path("fieldKey").asText() + " — " + node.path("rejectionReason").asText());
        }
        for (JsonNode node : refinement.legalReasonsOf(round)) {
            String text = node.isTextual() ? node.asText()
                : node.path("reason").isTextual() ? node.path("reason").asText()
                : node.path("text").isTextual() ? node.path("text").asText() : node.toString();
            if (!text.isBlank()) lines.add("법률 — " + text);
        }
        return lines;
    }

    /**
     * 변경 한 줄. 「가격을 시장 안으로 옮겼어요 · 15,000원 프리미엄 라인 → 9,500원대 주력」
     *
     * <p>{@code source} 가 {@code LEGAL} 이면 근거는 시장 근거가 아니라 조항이다 —
     * 그때 {@code evidenceIds} 는 비어 있는 것이 정상이고, {@code legalRef} 가 그 자리를 대신한다.
     */
    /**
     * 이 제안이 <b>최종 컨셉 서술문에 담기는 것</b>인가.
     *
     * <p>⚠ 이 조건은 <b>세 곳이 정확히 같아야 한다</b> — 여기,
     * {@code ConceptRefinementService.queueNarration}(모델에게 담으라고 시키는 목록),
     * {@code ConceptPortfolioSelectionMaterializationService.narrativeMatchesChanges}(담았는지 재는 잣대).
     * 갈리면 모델은 A 를 담으라는 말을 듣고 서버는 B 를 찾고 화면은 C 를 가리킨다.
     *
     * <p>결정 칸이 없던 옛 라운드({@code null})는 전량이 이미 적용된 것이라 그대로 센다.
     */
    private static boolean included(ConceptRefinementRound round, java.util.Set<String> accepted,
            JsonNode proposal) {
        return round.getAcceptedFieldsJson() == null
            || accepted.contains(proposal.path("fieldKey").asText());
    }

    /**
     * @param accepted     사람이 이 제안을 <b>골랐는가</b>. {@code null} 이면 아직 결정 전이고,
     *                     그때 화면은 체크박스를 «누를 수 있는» 상태로 그린다.
     * @param narrativeRef 서술문 조각({@code narrative[].changeRef})이 가리키는 번호.
     *                     서술문에 안 담기는 제안은 {@code null} 이다.
     */
    /**
     * @param afterValue 바뀐 뒤의 <b>값 자체</b>. {@code after} 와 다르다 — {@code after} 는
     *                   모델이 쓴 <b>사람 말</b>이라 목록 칸에서는 「…를 추가했어요」 같은
     *                   <b>설명</b>이 온다(2026-08-16 실측: 네 칸 중 셋). 「지금 → 이렇게」를
     *                   나란히 놓는 대조표는 설명이 아니라 값이 필요하다.
     */
    public record Change(int round, String field, String title, String before, String after,
                         String reason, List<String> evidenceIds,
                         String source, String legalRef, Boolean accepted, Integer narrativeRef,
                         String afterValue) { }

    /** 최종 컨셉 서술문 한 조각. {@code changeRef} 가 붙은 조각이 화면에서 초록으로 물든다. */
    public record NarrativeSegment(String text, Integer changeRef) { }

    /**
     * 델타 법률 — <b>바뀐 가설에 걸리는 법만</b>. 전체 보고서가 아니다.
     *
     * <p>{@code hypothesisTypes} 는 이번에 다시 태운 가설(1~5종)이고, {@code legalReview} 는
     * 그 범위의 검토 결과다. 화면은 이것만 그린다.
     */
    public record DeltaLegalView(String status, boolean approved,
                                 List<String> hypothesisTypes, JsonNode legalReview) { }

    /**
     * {@code narrative} 는 <b>비어 있을 수 있다</b> — 아직 수렴 전이거나, 서술문이 검증을
     * 통과하지 못한 경우다. 그때 화면은 칸 나열로 폴백한다. <b>빈 배열을 채우지 않는다.</b>
     */
    /**
     * @param finalized 이미 확정된 사업안인가. 화면은 이때 확정 버튼을 <b>안 낸다</b> —
     *                  다시 누르면 서버가 거절하고, 성공한 일이 「실패」로 보인다.
     */
    public record RefinementView(String outcome, int rounds, List<Change> changes,
                                 List<String> unresolved, DeltaLegalView deltaLegal,
                                 List<NarrativeSegment> narrative,
                                 ConceptRefinementService.RetryStatus retry,
                                 boolean finalized) { }
}
