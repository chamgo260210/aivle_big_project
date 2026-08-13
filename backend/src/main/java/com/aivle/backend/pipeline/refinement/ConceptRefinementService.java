package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersion;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.taskrun.contract.ConceptDriftContract;
import com.aivle.backend.taskrun.domain.TaskRun;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 컨셉 다듬기 루프의 <b>한 걸음</b>을 만든다. 루프를 도는 것은 {@link ConceptRefinementWorker} 다.
 *
 * <p>왜 Spring 이 도나. 라운드마다 법률(DELTA_LEGAL)이 따라붙고, 법률은 이미 Spring 이
 * 소유한 상태 기계다. AI 쪽에서 돌리면 상태 소유자가 둘이 된다.
 *
 * <p>⚠ <b>라운드마다 BM 을 다시 돌리지 않는다.</b> 수렴한 뒤 한 번만 재검증한다 —
 * 라운드마다 돌리면 20분짜리를 세 번 태운다.
 */
@Service
public class ConceptRefinementService {

    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptRefinementRoundRepository rounds;
    private final MarketResearchVersionRepository researchVersions;
    private final ConceptPortfolioSelectionTaskFactory tasks;
    private final com.aivle.backend.pipeline.conceptportfolio.selection.repository
        .ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final com.aivle.backend.pipeline.conceptportfolio.selection.repository
        .ConceptLegalRegulatoryReportRepository reports;
    private final ConceptRefinementFinalRepository finals;
    /** 입력 뼈대(seed·selectedCandidate·baseLegalReview)를 만드는 쪽. 손으로 짓지 않는다. */
    private final com.aivle.backend.pipeline.conceptportfolio.selection.application
        .ConceptPortfolioSelectionService selectionService;
    private final ObjectMapper mapper;

    public ConceptRefinementService(ConceptPortfolioSelectionRepository selections,
            ConceptRefinementRoundRepository rounds, MarketResearchVersionRepository researchVersions,
            ConceptPortfolioSelectionTaskFactory tasks,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptPortfolioDeltaLegalReviewRepository deltas,
            com.aivle.backend.pipeline.conceptportfolio.selection.repository
                .ConceptLegalRegulatoryReportRepository reports,
            ConceptRefinementFinalRepository finals,
            com.aivle.backend.pipeline.conceptportfolio.selection.application
                .ConceptPortfolioSelectionService selectionService,
            ObjectMapper mapper) {
        this.selections = selections;
        this.rounds = rounds;
        this.researchVersions = researchVersions;
        this.tasks = tasks;
        this.deltas = deltas;
        this.reports = reports;
        this.finals = finals;
        this.selectionService = selectionService;
        this.mapper = mapper;
    }

    /** 다듬기가 더 돌 수 있는가. 상한(3)과 「고칠 것 없음」이 여기서 갈린다. */
    @Transactional(readOnly = true)
    public boolean canRunAnotherRound(Long selectionId) {
        Optional<ConceptRefinementRound> last = rounds
            .findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId);
        if (last.isEmpty()) return true;
        if (last.get().getRound() >= ConceptRefinementRound.MAX_ROUNDS) return false;
        // 통과분이 하나도 없었으면 더 돌 이유가 없다 — 「고칠 것 없음」이다.
        return !proposalsOf(last.get()).isEmpty();
    }

    /**
     * <b>루프의 첫 문.</b> 사업 검증(VALIDATION)이 성공해 결과가 채택된 직후 라운드 1을 건다.
     *
     * <p>왜 여기인가. {@link ConceptRefinementWorker#advance}는 <b>이미 있는 라운드</b>만 다음으로
     * 민다({@code findByLegalOutcomeIsNotNull...}). 그래서 라운드 1을 거는 자리가 어디에도 없었고
     * 다듬기는 영원히 시작되지 않았다(2026-08-13 실측: {@code concept_refinement_rounds} 0행,
     * 사업 검증이 SUCCEEDED 인데도 화면은 「아직 안 함」).
     *
     * <p>루프의 재료는 시장조사·BM 판정이다({@link #material}). 그러니 <b>그것이 막 생긴 순간</b>이
     * 유일하게 정확한 시점이다 — 검증 결과 없이 라운드를 걸면 모델이 근거 없이 컨셉을 고친다.
     *
     * <p><b>정확히 한 번만 건다.</b> 이 선택에 라운드가 하나라도 있으면 조용히 건너뛴다.
     * 폴러가 다시 눌러도, 사용자가 검증을 다시 돌려도 라운드 1이 두 번 서지 않는다.
     * 그 뒤의 라운드는 {@link ConceptRefinementWorker} 몫이다 — 멱등키 모양을 그쪽과 맞춰 둔다.
     *
     * <p>⚠ <b>일부러 {@code @Transactional} 을 안 붙였다.</b> 호출자(시장조사 채택)의 트랜잭션에
     * 그대로 얹혀야 한다 — 방금 만든 {@code MarketResearchVersion} 이 아직 커밋 전이라
     * {@code REQUIRES_NEW} 로 떼면 {@link #material} 이 <b>옛 검증 결과를 읽는다</b>.
     * 그렇다고 붙여서 참여시키면, 여기서 던진 예외가 트랜잭션을 rollback-only 로 물들여
     * 호출자가 try/catch 로 삼켜도 <b>시장조사 채택이 통째로 되돌아간다</b>.
     * 애너테이션이 없으면 인터셉터가 안 끼어 그 표시가 안 붙는다.
     *
     * @return 새로 건 라운드의 TaskRun. 걸 이유가 없었으면 비어 있다.
     */
    public Optional<TaskRun> startFirstRound(Long projectId) {
        ConceptPortfolioSelection selection = selections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).orElse(null);
        if (selection == null) return Optional.empty();
        if (rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selection.getId()).isPresent()) {
            return Optional.empty();
        }
        String key = "refine:" + selection.getId() + ":1";
        return Optional.of(queueNextRound(selection.getSelectedByUserId(), projectId,
            selection.getId(), key));
    }

    /**
     * 다음 라운드를 큐에 넣는다.
     *
     * <p>재료에 <b>직전 라운드의 기각 사유</b>를 반드시 실어 보낸다. 안 실으면 모델이 같은
     * 제안을 3라운드 내내 반복하고, 라운드 상한만 태운 채 아무것도 안 고치고 끝난다.
     */
    @Transactional
    public TaskRun queueNextRound(Long ownerId, Long projectId, Long selectionId, String idempotencyKey) {
        ConceptPortfolioSelection selection = selections.findById(selectionId).orElseThrow();
        int round = rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId)
            .map(value -> value.getRound() + 1).orElse(1);

        // ⚠ 뼈대는 **선택 서비스가 만든다.** 여기서 손으로 지으면 `selectedCandidate` 를
        // 빠뜨려 AI 입력 계약이 통째로 거부한다(2026-08-13 실측).
        ObjectNode input = selectionService.refinementInput("REFINE_FROM_MARKET", selection);
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.set("refinementMaterial", material(projectId, selectionId, round));
        return tasks.create(ownerId, selection, "REFINE_FROM_MARKET", input, idempotencyKey, null);
    }

    /**
     * 한 라운드가 받는 재료.
     *
     * <p>계약(동결·다듬을 수 있는 면)을 <b>입력에 실어</b> 준다. 모델에게 무엇을 건드리면
     * 안 되는지 말해 주면 버려질 제안이 줄고, 그만큼 라운드가 덜 든다.
     */
    private ObjectNode material(Long projectId, Long selectionId, int round) {
        ObjectNode material = mapper.createObjectNode();
        material.put("round", round);

        ArrayNode frozen = material.putArray("frozenFields");
        ConceptDriftContract.FROZEN_FIELDS.stream().sorted().forEach(frozen::add);
        ObjectNode refinable = material.putObject("refinableFields");
        ConceptDriftContract.REFINABLE_FIELDS.forEach(refinable::put);

        JsonNode validation = latestValidationResult(projectId);
        if (validation != null) {
            material.set("gateReasons", validation.path("bm").path("gateReasons"));
            material.set("canvas", validation.path("canvas"));
            material.set("marketEvidence", validation.path("evidence"));
        }

        // 법률 소견 — 이것이 없으면 다듬기가 시장 근거만 보고, 「법이 막은 표현」은
        // 영영 안 고쳐진다. 광고 문구 칸은 동결이라 모델은 `differentiators` 로 우회해야 하는데,
        // 무엇이 막혔는지를 알려 주지 않으면 우회할 대상 자체를 모른다.
        material.set("legalFindings", legalFindings(selectionId));

        // 직전 라운드의 기각 사유 — 되먹이지 않으면 같은 제안이 반복된다.
        rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId).ifPresent(last -> {
            if (last.getDriftRejectionsJson() != null) {
                material.set("driftRejections", mapper.readTree(last.getDriftRejectionsJson()));
            }
            if (last.getLegalReasonsJson() != null) {
                material.set("legalRejections", mapper.readTree(last.getLegalReasonsJson()));
            }
        });
        return material;
    }

    /**
     * 지금 이 컨셉에 걸려 있는 <b>법률 소견</b>을 조항과 함께 편다.
     *
     * <p>가장 최근에 본 것이 정본이다 — 델타를 돈 적이 있으면 그것, 없으면 확정된 법률보고서.
     * 둘 다 없으면 빈 배열이다(아직 법을 본 적이 없다는 뜻이고, 그때는 시장 근거로만 다듬는다).
     *
     * <p>⚠ 조문 해설이 아니라 <b>소견</b>을 보낸다. 「법이 무엇을 정하는가」를 모델에 주면
     * 모델이 그것을 그대로 컨셉에 옮겨 적는다.
     */
    private ArrayNode legalFindings(Long selectionId) {
        ArrayNode findings = mapper.createArrayNode();
        JsonNode review = latestLegalReview(selectionId);
        if (review == null) return findings;
        for (JsonNode clause : review.path("officialEvidenceReferences")) {
            for (JsonNode finding : clause.path("findings")) {
                ObjectNode value = findings.addObject();
                value.put("lawName", clause.path("lawName").asText(""));
                value.put("articleReference", clause.path("articleReference").asText(""));
                value.put("findingType", finding.path("type").asText(""));
                value.put("topic", finding.path("topic").asText(""));
                value.put("text", finding.path("text").asText(""));
            }
        }
        return findings;
    }

    /** 델타 → 없으면 확정 보고서. 저장 모양이 서로 달라 {@link #unwrapLegalReview} 로 편다. */
    private JsonNode latestLegalReview(Long selectionId) {
        var all = deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(selectionId);
        if (!all.isEmpty()) {
            return unwrapLegalReview(mapper.readTree(all.get(all.size() - 1).getLegalReviewJson()));
        }
        return reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT")
            .map(report -> mapper.readTree(report.getReportJson()).path("finalLegalConclusion"))
            .filter(JsonNode::isObject)
            .orElse(null);
    }

    /**
     * 저장된 델타 JSON 에서 <b>법률 검토 본문</b>을 꺼낸다.
     *
     * <p>⚠ 저장된 것은 검토 본문이 아니라 <b>액션 결과 통째</b>다
     * ({@code ConceptPortfolioSelectionMaterializationService} 의 {@code DELTA_LEGAL} 갈래가
     * {@code writeValueAsString(result)} 로 넣는다). 그래서 최상위에는
     * {@code officialEvidenceReferences} 가 없고, 그걸 바로 읽으면 <b>조용히 빈 목록</b>이 된다 —
     * 화면은 「이번에 새로 걸린 법이 없어요」로 잘못 말한다.
     *
     * <p>옛 행이 본문만 담고 있을 수도 있어 둘 다 받는다.
     */
    static JsonNode unwrapLegalReview(JsonNode stored) {
        JsonNode nested = stored.path("deltaLegalResult").path("legalReview");
        if (nested.isObject()) return nested;
        JsonNode direct = stored.path("legalReview");
        return direct.isObject() ? direct : stored;
    }

    /** 검증 결과 — 새 실행(VALIDATION)이 있으면 그것, 없으면 옛 BM 것을 읽는다. */
    private JsonNode latestValidationResult(Long projectId) {
        for (MarketResearchRun.Kind kind : List.of(MarketResearchRun.Kind.VALIDATION, MarketResearchRun.Kind.BM)) {
            Optional<MarketResearchVersion> version = researchVersions
                .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, kind);
            if (version.isPresent()) return mapper.readTree(version.get().getResultJson());
        }
        return null;
    }

    /** 라운드가 통과시킨 제안. 없으면 「고칠 것 없음」이다. */
    @Transactional(readOnly = true)
    public JsonNode proposalsOf(ConceptRefinementRound round) {
        return round.getProposalJson() == null ? mapper.createArrayNode()
            : mapper.readTree(round.getProposalJson());
    }

    /** 계약이 기각한 제안과 사유. 최종 화면의 「못 푼 것」이 여기서 나온다. */
    @Transactional(readOnly = true)
    public JsonNode rejectionsOf(ConceptRefinementRound round) {
        return round.getDriftRejectionsJson() == null ? mapper.createArrayNode()
            : mapper.readTree(round.getDriftRejectionsJson());
    }

    /**
     * 서술문에서 <b>초록으로 물들 말</b>. 「이 변경이 정말 문단에 있나」를 재는 잣대이기도 하다.
     *
     * <p>목록 칸은 값 전체가 아니라 <b>더해진 항목만</b> 표시한다. 차별점 네 개에 하나를 더한
     * 변경에서 값 전체를 요구하면, 문단은 목록을 통째로 옮겨 적어야 하고 <b>정작 더해진 항목은
     * 빠져도 통과</b>한다 — 2026-08-13 실측에서 정확히 그 일이 났다(모델이 옛 네 항목만 적고
     * 새 항목을 빠뜨렸다). 더해진 항목을 재면 「무엇이 바뀌었나」를 곧장 재는 것이 된다.
     *
     * <p>더해진 것이 없으면(가격처럼 값 하나가 통째로 바뀌면) 바뀐 값 전체가 잣대다.
     */
    public static String changeMark(String beforeText, String afterText) {
        List<String> before = items(beforeText);
        List<String> after = items(afterText);
        for (String item : after) {
            if (!before.contains(item)) return item;
        }
        return afterText == null ? "" : afterText.trim();
    }

    /**
     * 「가, 나, 다」를 항목으로 편다.
     *
     * <p>안 자르는 자리가 둘이다: <b>괄호 안</b>(한 항목 안의 나열이다)과
     * <b>숫자 사이의 쉼표</b>(「8,900원」의 천 단위다 — 자르면 가격이 「1팩 8」과 「900원」이 된다).
     */
    private static List<String> items(String text) {
        List<String> values = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return values;
        StringBuilder buffer = new StringBuilder();
        int depth = 0;
        char[] chars = text.toCharArray();
        for (int at = 0; at < chars.length; at++) {
            char c = chars[at];
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth = Math.max(0, depth - 1);
            boolean thousands = c == ',' && at > 0 && at + 1 < chars.length
                && Character.isDigit(chars[at - 1]) && Character.isDigit(chars[at + 1]);
            if (depth == 0 && !thousands && (c == ',' || c == '·')) {
                if (!buffer.toString().isBlank()) values.add(buffer.toString().trim());
                buffer.setLength(0);
            } else buffer.append(c);
        }
        if (!buffer.toString().isBlank()) values.add(buffer.toString().trim());
        return values;
    }

    /** 이력 — 최종 화면의 변경 표와 「못 푼 것」 사유가 여기서 나온다. */
    @Transactional(readOnly = true)
    public List<ConceptRefinementRound> history(Long selectionId) {
        return rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId);
    }

    /**
     * 최종 컨셉 서술문. <b>없으면 빈 목록</b>이고, 그때 화면은 칸 나열로 폴백한다.
     *
     * <p>없는 경우는 셋이다: 아직 수렴 전 · 서술문이 검증을 통과 못 함 · 바뀐 것이 없음.
     * 셋 다 「비어 있다」로 같게 다뤄도 된다 — 어느 쪽이든 <b>세울 문장이 없다</b>는 뜻이다.
     */
    @Transactional(readOnly = true)
    public List<ConceptRefinementController.NarrativeSegment> narrativeOf(Long selectionId) {
        return finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .map(ConceptRefinementFinal::getNarrativeJson)
            .map(json -> {
                List<ConceptRefinementController.NarrativeSegment> segments = new java.util.ArrayList<>();
                for (JsonNode node : mapper.readTree(json)) {
                    JsonNode ref = node.path("changeRef");
                    segments.add(new ConceptRefinementController.NarrativeSegment(
                        node.path("text").asText(""), ref.isInt() ? ref.asInt() : null));
                }
                return segments;
            })
            .orElse(List.of());
    }

    /**
     * 검증을 통과한 서술문을 적는다.
     *
     * <p>⚠ <b>검증은 호출자가 이미 했다.</b> 여기서 다시 보지 않는다 — 두 곳에서 보면
     * 규칙이 갈리고, 어느 쪽이 정본인지 모르게 된다.
     */
    @Transactional
    public void recordNarrative(Long projectId, Long selectionId, String narrativeJson) {
        ConceptRefinementFinal row = finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .orElseGet(() -> finals.save(ConceptRefinementFinal.of(projectId, selectionId)));
        row.recordNarrative(narrativeJson);
        finals.save(row);
    }

    /** 최종 확정이 시드에 얹을 오버레이. 없으면 {@code null} — 얹을 것이 없다는 뜻이다. */
    @Transactional(readOnly = true)
    public JsonNode overlayOf(Long selectionId) {
        return finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .map(ConceptRefinementFinal::getOverlayJson)
            .filter(json -> json != null && !json.isBlank())
            .map(mapper::readTree)
            .orElse(null);
    }

    /**
     * 수렴한 뒤 <b>한 번만</b> 서술문을 건다.
     *
     * <p>⚠ 라운드마다 걸면 중간값이 남은 문장이 최종 문장 자리에 선다. 그리고 LLM 을
     * 세 번 태운다.
     *
     * <p>바뀐 것이 하나도 없으면 걸지 않는다 — 고칠 것이 없었던 컨셉에 새 문장을 씌우면
     * 「무엇이 바뀌었나」가 없는 채로 문장만 달라진다.
     *
     * @return 건 TaskRun. 걸 이유가 없었으면 비어 있다.
     */
    @Transactional
    public Optional<TaskRun> queueNarration(Long ownerId, Long projectId, Long selectionId) {
        ConceptPortfolioSelection selection = selections.findById(selectionId).orElseThrow();
        ArrayNode changes = mapper.createArrayNode();
        for (ConceptRefinementRound round : history(selectionId)) {
            for (JsonNode proposal : proposalsOf(round)) {
                ObjectNode change = ((ObjectNode) proposal).deepCopy();
                // 문단이 실제로 담아야 할 말. 검증도 같은 잣대를 쓴다.
                change.put("mark", changeMark(proposal.path("beforeText").asText(""),
                    proposal.path("afterText").asText("")));
                changes.add(change);
            }
        }
        if (changes.isEmpty()) return Optional.empty();
        if (finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
                .map(value -> value.getNarrativeJson() != null).orElse(false)) {
            return Optional.empty();
        }

        ObjectNode input = selectionService.refinementInput("NARRATE_REFINED", selection);
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.putObject("narrationMaterial").set("changes", changes);
        return Optional.of(tasks.create(ownerId, selection, "NARRATE_REFINED", input,
            "narrate:" + selectionId, null));
    }
}
