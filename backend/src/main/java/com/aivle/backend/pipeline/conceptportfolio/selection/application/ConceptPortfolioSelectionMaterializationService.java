package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementApplyService;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRoundRepository;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptPortfolioSelectionMaterializationService {
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final ConceptPortfolioSelectionService selectionService;
    private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementApplyService refinementApply;
    private final com.aivle.backend.pipeline.refinement.ConceptRefinementService refinement;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptPortfolioSelectionMaterializationService(ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptPortfolioDeltaLegalReviewRepository deltas,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository marketSeeds,
            ConceptPortfolioSelectionService selectionService, ConceptPortfolioJsonHasher hasher,
            TaskRunService taskRuns, ConceptRefinementRoundRepository rounds,
            ConceptRefinementApplyService refinementApply,
            com.aivle.backend.pipeline.refinement.ConceptRefinementService refinement,
            ObjectMapper mapper, Clock clock) {
        this.selections=selections; this.hypotheses=hypotheses; this.deltas=deltas;
        this.reports=reports; this.marketSeeds=marketSeeds; this.selectionService=selectionService;
        this.hasher=hasher; this.taskRuns=taskRuns; this.rounds=rounds; this.refinementApply=refinementApply;
        this.refinement=refinement; this.mapper=mapper; this.clock=clock;
    }

    @Transactional
    public String complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        ConceptPortfolioSelection selection=locked(context);
        JsonNode result=validate(response.result());
        String action=result.path("action").asText();
        JsonNode input=mapper.readTree(context.inputSnapshot());
        require(action.equals(input.path("action").asText()));
        require(input.path("expectedHypothesisRevision").isIntegralNumber()
            && input.path("expectedHypothesisRevision").asInt() == selection.getHypothesisRevision());
        switch(action) {
            case "PREPARE_HYPOTHESES" -> {
                persistInitial(selection,result.path("hypotheses"));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                adopt(claim,context,response);
            }
            case "CONFIRM_HYPOTHESES" -> {
                applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady=selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(value->("ACCEPTED".equals(value.getDecisionStatus())||"USER_EDITED_ACCEPTED".equals(value.getDecisionStatus()))
                        && value.getFinalValueJson()!=null&&"VALID".equals(value.getSemanticStatus()));
                boolean deltaRequired=selectionService.latestRequired(selection.getId()).stream()
                    .anyMatch(value->value.isDeltaLegalRequired()&&"PENDING".equals(value.getLegalReviewStatus()));
                ConceptPortfolioSelectionStatus next=!allReady?ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION:
                    deltaRequired?ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING:ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT;
                selection.completeTask(context.taskRunId(),next,true); adopt(claim,context,response);
                if(allReady&&deltaRequired) selectionService.queueDelta(context.ownerId(),selection,
                    context.idempotencyKey()+":delta");
                // ⚠ **델타가 안 붙으면 라운드를 여기서 닫는다.** 라운드는 법률 결과가 적혀야
                // 닫히고, 그것을 적는 자리는 DELTA_LEGAL 갈래뿐이었다. 그래서 바뀐 가설이
                // 법률 민감이 아닐 때(`deltaLegalRequired=false`) 아무도 결과를 안 적었고,
                // 다듬기 워커는 「법률 결과가 적힌 라운드」만 보므로 **루프가 조용히 멈췄다**
                // (2026-08-13 실측: 라운드 1이 6분 넘게 그대로였다).
                // 다시 볼 법이 없다는 것은 «막은 법이 없다»는 뜻이므로 PASSED 로 닫는다.
                if(allReady&&!deltaRequired) closeRoundWithoutDelta(selection.getId());
            }
            case "PROPOSE_ALTERNATIVE" -> {
                JsonNode item=result.path("alternative"); require(item.isObject());
                PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
                int version=item.path("proposalVersion").asInt();
                hypotheses.save(fromJson(selection,item,type,version,null));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                staleDependents(selection.getId()); adopt(claim,context,response);
            }
            case "DELTA_LEGAL" -> {
                JsonNode delta=result.path("deltaLegalResult"); require(delta.isObject());
                boolean approved=delta.path("approved").asBoolean();
                String json=mapper.writeValueAsString(result);
                deltas.save(ConceptPortfolioDeltaLegalReview.create(selection,context.taskRunId(),
                    input.path("expectedHypothesisRevision").asInt(), delta.path("reviewToken").asText(),
                    mapper.writeValueAsString(delta.path("hypothesisTypes")),
                    delta.path("status").asText(),approved,json,hasher.hash(result)));
                if(approved) applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady = approved && selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(ConceptPortfolioHypothesisDecision::ready);
                selection.completeTask(context.taskRunId(),allReady?ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT:
                    ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED,false); adopt(claim,context,response);
                // 다듬기 루프가 돌고 있으면 **그 라운드에 결과를 적는다**. 안 적으면 라운드가
                // 영영 안 닫히고 다음 걸음이 걸리지 않는다 — 루프가 조용히 멈춘다.
                rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selection.getId())
                    .filter(open -> open.getLegalOutcome()==null)
                    .ifPresent(open -> open.recordLegal(
                        approved?ConceptRefinementRound.LegalOutcome.PASSED
                            :ConceptRefinementRound.LegalOutcome.BLOCKED,
                        mapper.writeValueAsString(delta.path("reasons"))));
            }
            case "REFINE_FROM_MARKET" -> {
                // ⚠ **결과 검증을 여기서 한다.** 다른 워커와 달리 이 갈래에는 정합 검사가 없어서,
                // 모델이 칸을 빠뜨려도 AI 호출은 성공하고 제안만 조용히 반쪽으로 저장됐다.
                requireProposals(result.path("refinementProposals"));
                // 라운드를 **DB 에 남긴다**. 워커는 폴링마다 새로 깨어나므로 라운드 번호와
                // 기각 사유를 메모리에 두면 재시작 한 번에 루프가 처음부터 다시 돈다.
                int round = input.path("refinementMaterial").path("round").asInt(1);
                rounds.save(ConceptRefinementRound.of(selection.getProjectId(), selection.getId(), round,
                    mapper.writeValueAsString(result.path("refinementProposals")),
                    mapper.writeValueAsString(result.path("driftRejections"))));
                // 상태는 그대로 둔다 — 다듬기는 선택의 «단계»가 아니라 그 위에서 도는 루프다.
                selection.completeTask(context.taskRunId(), selection.getStatus(), false);
                adopt(claim,context,response);
                // 통과분을 **바로 적용한다**. 여기서 안 하면 라운드는 기록만 되고 컨셉은
                // 그대로다 — 루프가 도는 흉내만 내게 된다.
                refinementApply.apply(context.ownerId(), selection.getProjectId(), selection.getId(),
                    result.path("refinementProposals"),
                    context.idempotencyKey()+":apply:"+round);
            }
            case "NARRATE_REFINED" -> {
                selection.completeTask(context.taskRunId(), selection.getStatus(), false);
                adopt(claim,context,response);
                // ⚠ **검증을 통과한 것만 저장한다.** 통과 못 하면 아무것도 안 남기고, 화면은
                // 칸 나열로 폴백한다 — 반쯤 맞는 문장을 컨셉 원문 자리에 세우면 그것이 곧
                // 지어낸 근거가 된다.
                JsonNode narrative = result.path("narrative");
                String conceptName = input.path("selectedCandidate").path("candidate")
                    .path("conceptName").asText("");
                if (narrativeKeepsConcept(narrative, conceptName)
                        && narrativeMatchesChanges(narrative, selection.getId())) {
                    refinement.recordNarrative(selection.getProjectId(), selection.getId(),
                        mapper.writeValueAsString(narrative));
                }
            }
            case "BUILD_HANDOFF" -> {
                JsonNode handoff=result.path("handoff"); JsonNode market=handoff.path("marketAnalysisSeedSnapshot");
                require("PASS".equals(handoff.path("compatibility").asText()));
                require("market-analysis-seed-snapshot-v1".equals(market.path("contract").asText()));
                require("2.0".equals(market.path("schemaVersion").asText()));
                var report=reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selection.getId(),"CURRENT")
                    .orElseThrow(ContractViolation::new);
                String id=market.path("snapshotId").asText(); String snapshotHash=result.path("marketSeedSnapshotHash").asText();
                require(snapshotHash.equals(hasher.productionCompatibleHash(market)));
                marketSeeds.save(MarketAnalysisSeedSnapshot.createPortfolio(id,selection.getProjectId(),selection.getId(),
                    selection.getConceptId(),report.getId(),"2.0",market.path("sourceSnapshotHash").asText(),snapshotHash,
                    mapper.writeValueAsString(market),context.ownerId(),Instant.now(clock)));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.READY_FOR_MARKET,false);
                adopt(claim,context,response);
            }
            default -> throw new ContractViolation();
        }
        return action;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim,TaskRunWorkerContext context,String code,String reason,boolean retryable){
        taskRuns.assertActiveClaim(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());
        ConceptPortfolioSelection selection=locked(context);
        String action=mapper.readTree(context.inputSnapshot()).path("action").asText();
        selection.failTask(context.taskRunId(),"DELTA_LEGAL".equals(action)?ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED:
            ConceptPortfolioSelectionStatus.FAILED,code); taskRuns.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),code,reason,retryable);
    }
    private void persistInitial(ConceptPortfolioSelection s,JsonNode array){require(array.isArray()&&array.size()==7);Set<String> types=new HashSet<>();
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());types.add(type.name());hypotheses.save(fromJson(s,item,type,1,null));}
        require(types.size()==7&&types.contains("TARGET_REGION"));}
    private void applyHypotheses(ConceptPortfolioSelection s,JsonNode array,Long user){require(array.isArray()&&array.size()==7);
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
            ConceptPortfolioHypothesisDecision current=hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(s.getId(),type).orElseThrow(ContractViolation::new);
            current.apply(nullableJson(item.get("finalValue")),item.path("source").asText(),item.path("decisionStatus").asText(),item.path("locked").asBoolean(),
                item.path("semanticStatus").asText(),item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),item.path("legalImpact").asText(),
                item.path("legalReviewStatus").asText(),item.path("deltaLegalRequired").asBoolean(),user,
                item.path("finalValue").isNull()?null:Instant.now(clock));}}
    private ConceptPortfolioHypothesisDecision fromJson(ConceptPortfolioSelection s,JsonNode item,PortfolioHypothesisType type,int version,Long user){
        return ConceptPortfolioHypothesisDecision.create(s.getId(),s.getProjectId(),s.getConceptId(),type,mapper.writeValueAsString(item.path("proposedValue")),
            nullableJson(item.get("finalValue")),item.path("source").asText(),item.path("decisionStatus").asText(),version,item.path("locked").asBoolean(),
            item.path("semanticStatus").asText("UNASSESSED"),item.path("semanticReason").isMissingNode()||item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),
            item.path("legalImpact").asText("NONE"),item.path("legalReviewStatus").asText("NOT_REQUIRED"),item.path("deltaLegalRequired").asBoolean(false),user,null);}
    private String nullableJson(JsonNode value){return value==null||value.isNull()?null:mapper.writeValueAsString(value);}
    private JsonNode validate(JsonNode result){require(result!=null&&result.isObject());require("concept-portfolio-v2-selection-action-result-v1".equals(result.path("contract").asText()));require("1.0".equals(result.path("schemaVersion").asText()));return result;}
    private ConceptPortfolioSelection locked(TaskRunWorkerContext c){ConceptPortfolioSelection s=selections.findLocked(Long.valueOf(c.subjectId())).orElseThrow(ContractViolation::new);
        require(s.isCurrent()&&s.getProjectId().equals(c.projectId())&&c.taskRunId().equals(s.getActiveTaskRunId()));return s;}
    private void adopt(TaskRunService.Claim claim,TaskRunWorkerContext c,ExecutionResponse r){taskRuns.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),mapper.writeValueAsString(r.result()),c.inputHash(),r.resultSchemaVersion());}
    private void staleDependents(Long id){reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(id,"CURRENT").forEach(ConceptLegalRegulatoryReport::markStale);marketSeeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(id).forEach(v->v.markStale(Instant.now(clock)));}
    /**
     * 제안이 <b>화면에 세울 수 있는 모양</b>인지 본다.
     *
     * <p>{@code fieldKey} 는 어디에 적용할지, {@code afterText} 는 무엇으로 바뀌었는지를 말한다.
     * 둘 중 하나라도 없으면 라운드는 저장되지만 화면은 빈 줄을 그린다 — 그것이 곧
     * 「제안은 통과했는데 아무것도 안 바뀌었다」로 읽힌다.
     *
     * <p>{@code title} 은 안 본다. 없으면 화면이 필드 라벨로 폴백하므로 <b>거짓이 되지 않는다</b>.
     */
    private static void requireProposals(JsonNode proposals){
        require(proposals.isArray());
        for(JsonNode proposal:proposals){
            require(!proposal.path("fieldKey").asText("").isBlank());
            require(!proposal.path("afterText").asText("").isBlank());
        }
    }

    /**
     * 서술문이 <b>정말 그 값을 담았는지</b> 본다. 셋 다 통과해야 저장한다.
     *
     * <ol>
     *   <li>{@code changeRef} 가 변경 번호 범위 안이고 <b>중복이 없다</b> — 같은 변경을 두 군데
     *       물들이면 어느 쪽이 그 변경인지 알 수 없다</li>
     *   <li>{@code changeRef} 붙은 조각이 그 변경의 {@code afterText} 를 <b>담고 있다</b> —
     *       이것이 「LLM 이 지어낸 문장」과 「바뀐 값을 실제로 담은 문장」을 가르는 유일한 잣대다</li>
     *   <li>조각을 다 이으면 최소한 한 조각은 남는다 — 빈 서술문을 컨셉 원문 자리에 세우지 않는다</li>
     * </ol>
     *
     * <p>비교는 <b>공백을 지운 뒤</b> 한다. 모델이 조사·띄어쓰기를 문장에 맞게 손보는 것은
     * 정상이고, 그것까지 어긋남으로 세면 서술문이 거의 항상 기각된다.
     */
    private boolean narrativeMatchesChanges(JsonNode narrative, Long selectionId){
        List<String> marks=new ArrayList<>();
        for(ConceptRefinementRound round:rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId)){
            if(round.getProposalJson()==null)continue;
            for(JsonNode proposal:mapper.readTree(round.getProposalJson()))
                // ⚠ 잣대는 값 전체가 아니라 **바뀐 말**이다. 같은 계산을 서술문 입력에도 쓴다 —
                // 두 곳이 갈리면 모델은 A 를 담으라는 말을 듣고 서버는 B 를 찾는다.
                marks.add(com.aivle.backend.pipeline.refinement.ConceptRefinementService.changeMark(
                    proposal.path("beforeText").asText(""), proposal.path("afterText").asText("")));
        }
        return narrativeMatchesChanges(narrative, marks);
    }

    /**
     * 다시 볼 법이 없어 델타가 안 붙은 라운드를 <b>PASSED 로 닫는다</b>.
     *
     * <p>열린 라운드가 없으면 아무것도 안 한다 — 다듬기와 무관한 일반 가설 확정도 이 자리를
     * 지나기 때문이다. 「사유 없음」으로 적는 이유는 <b>막은 법이 없기 때문</b>이지 검토를
     * 건너뛴 것이 아니다.
     */
    private void closeRoundWithoutDelta(Long selectionId){
        rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId)
            .filter(open -> open.getLegalOutcome()==null)
            .ifPresent(open -> open.recordLegal(ConceptRefinementRound.LegalOutcome.PASSED, "[]"));
    }

    /**
     * 서술문이 <b>같은 사업을 말하고 있는지</b> 본다.
     *
     * <p>{@code afterText} 대조는 「바뀐 값을 담았나」만 본다 — 바뀌지 <b>않은</b> 부분은
     * 아무 검사도 안 받는다. 그 틈으로 모델이 다른 사업을 써 넣을 수 있다. 사업안 이름이
     * 문단 안에 그대로 남아 있는지가 그것을 막는 <b>가장 싼 잣대</b>다.
     *
     * <p>이름을 모르면(입력에 없으면) 통과시킨다 — 없는 잣대로 기각하면 서술문이 영영 안 선다.
     */
    static boolean narrativeKeepsConcept(JsonNode narrative, String conceptName){
        String name=squeeze(conceptName);
        if(name.isEmpty())return true;
        StringBuilder whole=new StringBuilder();
        for(JsonNode segment:narrative)whole.append(segment.path("text").asText(""));
        return squeeze(whole.toString()).contains(name);
    }

    /**
     * 판정 그 자체 — <b>조회 없이</b> 돈다.
     *
     * <p>라운드 조회에서 떼어 둔 이유는 이것이 이 화면의 <b>유일한 진짜 잣대</b>이기 때문이다.
     * 여기가 느슨해지면 LLM 이 쓴 아무 문장이나 컨셉 원문 자리에 선다.
     *
     * @param afterTexts 채택된 변경의 {@code afterText} — <b>화면의 번호와 같은 순서</b>여야 한다
     */
    static boolean narrativeMatchesChanges(JsonNode narrative, List<String> afterTexts){
        if(narrative==null||!narrative.isArray()||narrative.isEmpty())return false;
        Set<Integer> seen=new HashSet<>();
        for(JsonNode segment:narrative){
            JsonNode ref=segment.path("changeRef");
            if(!ref.isInt())continue;
            int at=ref.asInt();
            if(at<1||at>afterTexts.size())return false;
            if(!seen.add(at))return false;
            String expected=squeeze(afterTexts.get(at-1));
            if(expected.isEmpty()||!squeeze(segment.path("text").asText("")).contains(expected))return false;
        }
        return true;
    }

    /** 공백을 지운 문자열. 조사·띄어쓰기 손질까지 어긋남으로 세지 않기 위한 것이다. */
    private static String squeeze(String value){return value==null?"":value.replaceAll("\\s+","");}

    private static void require(boolean condition){if(!condition)throw new ContractViolation();}
    public static final class ContractViolation extends RuntimeException { }
}
