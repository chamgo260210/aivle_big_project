package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.response.ApiResponse;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
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
    private final tools.jackson.databind.ObjectMapper mapper;
    private final CurrentUserProvider currentUser;

    public ConceptRefinementController(ConceptRefinementService refinement,
            ConceptPortfolioSelectionService selectionService,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptPortfolioDeltaLegalReviewRepository deltas,
            tools.jackson.databind.ObjectMapper mapper, CurrentUserProvider currentUser) {
        this.refinement = refinement;
        this.selectionService = selectionService;
        this.deltas = deltas;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<RefinementView> current(@PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam Long selectionId,
            HttpServletRequest request) {
        currentUser.currentUserId();
        List<ConceptRefinementRound> rounds = refinement.history(selectionId);
        return ApiResponse.success(view(rounds, deltaLegal(selectionId), selectionId), id(request));
    }

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
        selectionService.finalizeReport(ownerId, projectId, selectionId);
        return ApiResponse.success(
            selectionService.finalizeMarketSeed(ownerId, projectId, selectionId, body), id(request));
    }

    private String id(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    private RefinementView view(List<ConceptRefinementRound> rounds, DeltaLegalView deltaLegal,
            Long selectionId) {
        if (rounds.isEmpty()) {
            return new RefinementView("NOT_STARTED", 0, List.of(), List.of(), deltaLegal, List.of());
        }
        ConceptRefinementRound last = rounds.get(rounds.size() - 1);
        String outcome = outcomeOf(last, rounds.size());
        List<Change> changes = rounds.stream()
            .flatMap(round -> changesOf(round).stream())
            .toList();
        List<String> unresolved = rounds.stream()
            .flatMap(round -> unresolvedOf(round).stream())
            .toList();
        // 「컨셉에 반영했어요」는 AI 가 못 정한다 — 이 조항이 이번 다듬기를 낳았는지는
        // 제안의 `legalRef` 만 안다. 그래서 여기서 덮는다.
        markReflectedClauses(deltaLegal, changes);
        return new RefinementView(outcome, rounds.size(), changes, unresolved, deltaLegal,
            refinement.narrativeOf(selectionId));
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
     * 세 결말은 <b>성공·실패가 아니다.</b>
     * <ul>
     *   <li>{@code CONVERGED} — 법률까지 통과했다</li>
     *   <li>{@code NOTHING_TO_FIX} — 고칠 것이 없었다</li>
     *   <li>{@code ROUND_LIMIT} — 3라운드에 못 풀었다. <b>사유를 함께 보인다</b></li>
     * </ul>
     */
    private String outcomeOf(ConceptRefinementRound last, int roundCount) {
        if (last.getLegalOutcome() == ConceptRefinementRound.LegalOutcome.PASSED) return "CONVERGED";
        if (roundCount >= ConceptRefinementRound.MAX_ROUNDS) return "ROUND_LIMIT";
        if (last.getLegalOutcome() == null) return "RUNNING";
        return "NOTHING_TO_FIX";
    }

    private List<Change> changesOf(ConceptRefinementRound round) {
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
                node.path("source").asText("MARKET"), legalRef));
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
    private String display(JsonNode text, JsonNode raw) {
        if (text != null && text.isTextual() && !text.asText().isBlank()) return text.asText();
        if (raw == null || raw.isNull() || raw.isMissingNode()) return "";
        return raw.isTextual() ? raw.asText() : raw.toString();
    }

    /** 계약이나 법률이 막은 것 — 「이건 못 풀었다」로 함께 보인다. */
    private List<String> unresolvedOf(ConceptRefinementRound round) {
        if (round.getDriftRejectionsJson() == null) return List.of();
        List<String> lines = new java.util.ArrayList<>();
        for (JsonNode node : refinement.rejectionsOf(round)) {
            lines.add(node.path("fieldKey").asText() + " — " + node.path("rejectionReason").asText());
        }
        return lines;
    }

    /**
     * 변경 한 줄. 「가격을 시장 안으로 옮겼어요 · 15,000원 프리미엄 라인 → 9,500원대 주력」
     *
     * <p>{@code source} 가 {@code LEGAL} 이면 근거는 시장 근거가 아니라 조항이다 —
     * 그때 {@code evidenceIds} 는 비어 있는 것이 정상이고, {@code legalRef} 가 그 자리를 대신한다.
     */
    public record Change(int round, String field, String title, String before, String after,
                         String reason, List<String> evidenceIds,
                         String source, String legalRef) { }

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
    public record RefinementView(String outcome, int rounds, List<Change> changes,
                                 List<String> unresolved, DeltaLegalView deltaLegal,
                                 List<NarrativeSegment> narrative) { }
}
