package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ConceptJourneyService {
    private final ProjectRepository projects; private final IdeaVersionRepository ideaVersions; private final LegalReviewRunRepository legalRuns;
    private final ConceptGenerationRunRepository generationRuns; private final ConceptVersionRepository concepts;
    private final QuickAssessmentRunRepository quickRuns; private final QuickAssessmentRepository quickAssessments;
    private final ShortlistDecisionRepository shortlists; private final DetailedAnalysisRunRepository detailedRuns;
    private final DetailedAnalysisRepository detailedAnalyses; private final JourneyFinancialAnalysisRepository financials;
    private final ConceptSelectionRepository selections; private final TaskRunService taskRuns; private final InternalAiExecutionClient ai;
    private final CanonicalInputHasher hasher; private final ObjectMapper mapper; private final ConceptJourneyPersistenceService persistence;

    public ConceptJourneyService(ProjectRepository projects, IdeaVersionRepository ideaVersions, LegalReviewRunRepository legalRuns,
            ConceptGenerationRunRepository generationRuns, ConceptVersionRepository concepts,
            QuickAssessmentRunRepository quickRuns, QuickAssessmentRepository quickAssessments,
            ShortlistDecisionRepository shortlists, DetailedAnalysisRunRepository detailedRuns,
            DetailedAnalysisRepository detailedAnalyses, JourneyFinancialAnalysisRepository financials,
            ConceptSelectionRepository selections, TaskRunService taskRuns, InternalAiExecutionClient ai,
            CanonicalInputHasher hasher, ObjectMapper mapper, ConceptJourneyPersistenceService persistence) {
        this.projects=projects; this.ideaVersions=ideaVersions; this.legalRuns=legalRuns; this.generationRuns=generationRuns; this.concepts=concepts;
        this.quickRuns=quickRuns; this.quickAssessments=quickAssessments; this.shortlists=shortlists; this.detailedRuns=detailedRuns;
        this.detailedAnalyses=detailedAnalyses; this.financials=financials; this.selections=selections; this.taskRuns=taskRuns;
        this.ai=ai; this.hasher=hasher; this.mapper=mapper; this.persistence=persistence;
    }

    public List<ConceptView> generate(Long ownerId, Long projectId) {
        Context context=context(ownerId,projectId,true);
        List<ConceptVersion> existing=concepts.findCurrentForIdea(projectId,context.idea().getId());
        if(!existing.isEmpty()) return existing.stream().map(this::conceptView).toList();
        ConceptGenerationRun current=generationRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(current!=null&&(current.getState()==ConceptAiRunBase.State.PENDING||current.getState()==ConceptAiRunBase.State.RUNNING)) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        ConceptGenerationRun run=generationRuns.save(ConceptGenerationRun.pending(context.project(),context.idea()));
        TaskRun task=createTask(ownerId,context.project(),TaskType.CONCEPT_GENERATION,"IDEA_VERSION",context.idea().getId().toString(),conceptGenerationInput(context));
        run.start(task); generationRuns.save(run);
        try { JsonNode result=execute(task,this::validateConceptGeneration); persistence.completeGeneration(run.getId(),result); }
        catch(ExecutionFailure failure){ persistence.failGeneration(run.getId(),failure.reason()); throw publicFailure(failure); }
        catch(RuntimeException failure){ persistence.failGeneration(run.getId(),"AI_RESULT_INVALID"); throw normalized(failure); }
        return concepts(ownerId,projectId);
    }

    public List<ConceptView> concepts(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return concepts.findCurrentForIdea(projectId,context.idea().getId()).stream().map(this::conceptView).toList();
    }

    public QuickView quick(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,true); List<ConceptVersion> candidates=requireConcepts(context);
        QuickAssessmentRun current=quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(current!=null&&current.getState()==ConceptAiRunBase.State.SUCCEEDED) return quickView(current);
        if(current!=null&&(current.getState()==ConceptAiRunBase.State.PENDING||current.getState()==ConceptAiRunBase.State.RUNNING)) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        QuickAssessmentRun run=quickRuns.save(QuickAssessmentRun.pending(context.project(),context.idea()));
        TaskRun task=createTask(ownerId,context.project(),TaskType.QUICK_ASSESSMENT,"IDEA_VERSION",context.idea().getId().toString(),conceptsInput("concepts",candidates));
        run.start(task); quickRuns.save(run);
        try { JsonNode result=execute(task,value->validateQuick(value,candidates)); persistence.completeQuick(run.getId(),result); }
        catch(ExecutionFailure failure){ persistence.failQuick(run.getId(),failure.reason()); throw publicFailure(failure); }
        catch(RuntimeException failure){ persistence.failQuick(run.getId(),"AI_RESULT_INVALID"); throw normalized(failure); }
        return currentQuick(ownerId,projectId);
    }

    public QuickView currentQuick(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::quickView).orElse(null);
    }

    public ShortlistView shortlist(Long ownerId,Long projectId,ShortlistRequest request) {
        Context context=context(ownerId,projectId,false); List<Long> ids=distinctIds(request.conceptVersionIds());
        QuickAssessmentRun quick=quickRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(quick==null||quick.getState()!=ConceptAiRunBase.State.SUCCEEDED) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        if(ids.isEmpty()||concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(ids,projectId,context.idea().getId()).size()!=ids.size()) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ShortlistDecision saved=shortlists.save(ShortlistDecision.create(context.project(),context.idea(),mapper.valueToTree(ids).toString(),trim(request.reason(),2000)));
        return shortlistView(saved);
    }

    public ShortlistView currentShortlist(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::shortlistView).orElse(null);
    }

    public DetailedView detailed(Long ownerId,Long projectId,DetailedRequest request) {
        Context context=context(ownerId,projectId,true);
        ShortlistDecision shortlist=shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));
        List<Long> ids=idList(shortlist.getSelectedConceptVersionIdsJson());
        List<ConceptVersion> selected=concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(ids,projectId,context.idea().getId());
        validateFinancialInputs(request.financials(),ids);
        DetailedAnalysisRun current=detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(current!=null&&current.getState()==ConceptAiRunBase.State.SUCCEEDED) {
            Set<Long> completedIds=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(current.getId()).stream()
                .map(value->value.getConceptVersion().getId()).collect(java.util.stream.Collectors.toSet());
            if(completedIds.equals(new HashSet<>(ids))) return detailedView(current);
        }
        if(current!=null&&(current.getState()==ConceptAiRunBase.State.PENDING||current.getState()==ConceptAiRunBase.State.RUNNING)) throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
        DetailedAnalysisRun run=detailedRuns.save(DetailedAnalysisRun.pending(context.project(),context.idea()));
        TaskRun task=createTask(ownerId,context.project(),TaskType.DETAILED_ANALYSIS,"IDEA_VERSION",context.idea().getId().toString(),conceptsInput("shortlistedConcepts",selected));
        run.start(task); detailedRuns.save(run);
        try { JsonNode result=execute(task,value->validateDetailed(value,ids)); persistence.completeDetailed(run.getId(),result,request.financials()); }
        catch(ExecutionFailure failure){ persistence.failDetailed(run.getId(),failure.reason()); throw publicFailure(failure); }
        catch(RuntimeException failure){ persistence.failDetailed(run.getId(),"AI_RESULT_INVALID"); throw normalized(failure); }
        return currentDetailed(ownerId,projectId);
    }

    public DetailedView currentDetailed(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        DetailedAnalysisRun run=detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(run==null) return null;
        ShortlistDecision shortlist=shortlists.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        if(run.getState()==ConceptAiRunBase.State.SUCCEEDED&&shortlist!=null){
            Set<Long> expected=new HashSet<>(idList(shortlist.getSelectedConceptVersionIdsJson()));
            Set<Long> actual=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(run.getId()).stream().map(v->v.getConceptVersion().getId()).collect(java.util.stream.Collectors.toSet());
            if(!actual.equals(expected)) return null;
        }
        return detailedView(run);
    }

    public SelectionView select(Long ownerId,Long projectId,SelectionRequest request) {
        Context context=context(ownerId,projectId,false); String reason=trim(request.reason(),2000);
        if(reason==null||request.conceptVersionId()==null) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ConceptVersion concept=concepts.findByIdInAndProjectIdAndIdeaVersionIdAndDeletedAtIsNull(
            List.of(request.conceptVersionId()),projectId,context.idea().getId()).stream().findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));
        if(detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(currentDetailedRunId(projectId,context.idea().getId())).stream().noneMatch(v->v.getConceptVersion().getId().equals(concept.getId()))) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);
        ConceptSelection existing=selections.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).orElse(null);
        return selectionView(existing!=null?existing:selections.save(ConceptSelection.create(context.project(),context.idea(),concept,reason)));
    }

    public SelectionView currentSelection(Long ownerId,Long projectId) {
        Context context=context(ownerId,projectId,false);
        return selections.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,context.idea().getId()).map(this::selectionView).orElse(null);
    }

    private Context context(Long ownerId,Long projectId,boolean requireLegalPass) {
        Project project=projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId,ownerId).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
        IdeaVersion idea=ideaVersions.findCurrent(projectId).filter(IdeaVersion::isConfirmed).orElseThrow(()->new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        if(requireLegalPass){ LegalReviewRun legal=legalRuns.findTopByProjectIdAndIdeaVersionIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId,idea.getId(),LegalReviewRun.State.SUCCEEDED).orElseThrow(()->new BusinessException(ErrorCode.LEGAL_REVIEW_NOT_FOUND));
            if(legal.getLegalStatus()!=LegalReviewRun.LegalStatus.PASS&&legal.getLegalStatus()!=LegalReviewRun.LegalStatus.PASS_WITH_CONDITIONS) throw new BusinessException(ErrorCode.PROJECT_STAGE_INVALID); }
        return new Context(project,idea);
    }
    private List<ConceptVersion> requireConcepts(Context context){ List<ConceptVersion> values=concepts.findCurrentForIdea(context.project().getId(),context.idea().getId()); if(values.isEmpty()) throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID); return values; }
    private TaskRun createTask(Long ownerId,Project project,TaskType type,String subjectType,String subjectId,String input){ String nonce=UUID.randomUUID().toString(); return taskRuns.create(ownerId,project.getId(),type,subjectType,subjectId,input,hasher.hash(type,"1.0","ko-KR",input),nonce,nonce,1); }
    private JsonNode execute(TaskRun run,java.util.function.Consumer<JsonNode> validator){ TaskRunService.Claim claim=taskRuns.claim(run.getId(),"journey-sync",Duration.ofMinutes(2),Duration.ofMinutes(2)); taskRuns.startExecution(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken()); try{ var response=ai.execute(taskRuns.getOwnedForWorker(run.getId()),claim.taskAttemptId(),LocalDateTime.now().plusMinutes(2)); try{validator.accept(response.result());}catch(BusinessException invalid){taskRuns.rejectAndFail(run.getId(),claim.taskAttemptId(),claim.claimToken(),response.result().toString(),response.resultSchemaVersion(),"AI_RESULT_INVALID");throw invalid;} taskRuns.adopt(run.getId(),claim.taskAttemptId(),claim.claimToken(),response.result().toString(),response.canonicalInputHash(),response.resultSchemaVersion()); return response.result(); }catch(ExecutionFailure failure){taskRuns.fail(run.getId(),claim.taskAttemptId(),claim.claimToken(),failure.code(),failure.reason(),failure.retryable());throw failure;} }
    private String conceptGenerationInput(Context c){ ObjectNode value=mapper.createObjectNode(); value.put("ideaVersionId",c.idea().getId()); value.put("normalizedDescription",c.idea().getNormalizedDescription()); value.set("facts",parse(c.idea().getFactsJson())); value.set("assumptions",parse(c.idea().getAssumptionsJson())); value.set("constraints",parse(c.idea().getConstraintsJson())); return taskInput("concept-generation",value.toString()); }
    private String conceptsInput(String key,List<ConceptVersion> values){ var root=mapper.createObjectNode(); var array=root.putArray(key); values.forEach(v->{ObjectNode item=array.addObject(); item.put("conceptVersionId",v.getId()); item.put("name",v.getName()); item.put("summary",v.getOneLineSummary()); item.put("targetCustomer",v.getTargetCustomer()); item.put("valueProposition",v.getValueProposition()); item.put("revenueModel",v.getRevenueModel()); item.set("risks",parse(v.getRisksJson()));}); return taskInput(key,root.toString()); }
    private String taskInput(String key,String text){
        ObjectNode content=mapper.createObjectNode(); content.put("contentKey",key); content.put("contentType","PLAIN_TEXT");
        content.put("language","ko-KR"); content.put("totalCharacters",text.codePointCount(0,text.length())); content.put("contentHash",sha256(text));
        var chunks=content.putArray("chunks"); int offset=0,index=0;
        while(offset<text.length()){
            int count=Math.min(16_000,text.codePointCount(offset,text.length())); int next=text.offsetByCodePoints(offset,count);
            String value=text.substring(offset,next); ObjectNode chunk=chunks.addObject(); chunk.put("index",index++); chunk.put("text",value);
            chunk.put("characterCount",count); chunk.put("chunkHash",sha256(value)); offset=next;
        }
        ObjectNode root=mapper.createObjectNode(); root.putArray("textContents").add(content); return root.toString();
    }
    private String sha256(String text){try{return "sha256:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private void validateConceptGeneration(JsonNode r){if(r==null||!r.has("concepts")||!r.get("concepts").isArray()||r.get("concepts").size()!=3)throw invalid();for(JsonNode c:r.get("concepts")){for(String f:List.of("name","oneLineSummary","targetCustomer","problem","solution","valueProposition","revenueModel"))text(c,f);if(c.get("name").asText().length()>200)throw invalid();for(String f:List.of("keyFeatures","differentiators","assumptions","risks"))array(c,f);}}
    private void validateQuick(JsonNode r,List<ConceptVersion> expected){validateIds(r,"assessments",expected.stream().map(ConceptVersion::getId).toList());for(JsonNode a:r.get("assessments")){for(String f:List.of("market","customerValue","feasibility","differentiation","revenuePotential","legalRisk")){int s=a.get(f).asInt(-1);if(s<0||s>100)throw invalid();}if(!a.has("overallScore")||!a.get("overallScore").isNumber()||a.get("overallScore").asDouble()<0||a.get("overallScore").asDouble()>100)throw invalid();text(a,"summary");array(a,"strengths");array(a,"weaknesses");}}
    private void validateDetailed(JsonNode r,List<Long> ids){validateIds(r,"analyses",ids);for(JsonNode a:r.get("analyses")){for(String f:List.of("marketAnalysis","customerAnalysis","businessModelAnalysis","operationAnalysis","riskAnalysis","recommendation"))text(a,f);array(a,"assumptions");array(a,"researchNeeds");}}
    private void validateIds(JsonNode r,String field,List<Long> expected){if(r==null||!r.has(field)||!r.get(field).isArray())throw invalid();Set<Long> actual=new HashSet<>();r.get(field).forEach(v->{if(!v.has("conceptVersionId")||!v.get("conceptVersionId").canConvertToLong())throw invalid();actual.add(v.get("conceptVersionId").asLong());});if(!actual.equals(new HashSet<>(expected))||actual.size()!=r.get(field).size())throw invalid();}
    private void validateFinancialInputs(List<FinancialInput> values,List<Long> ids){if(values==null||values.size()!=ids.size()||!values.stream().map(FinancialInput::conceptVersionId).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(ids)))throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);for(FinancialInput v:values){if(v.unitPrice()==null||v.variableCostPerCustomer()==null||v.monthlyFixedCost()==null||v.initialInvestment()==null||v.unitPrice().signum()<=0||v.monthlyCustomers()<0||v.variableCostPerCustomer().signum()<0||v.monthlyFixedCost().signum()<0||v.initialInvestment().signum()<0||v.unitPrice().compareTo(v.variableCostPerCustomer())<=0)throw new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID);}}
    private String text(JsonNode n,String f){if(n==null||!n.has(f)||!n.get(f).isTextual()||n.get(f).asText().isBlank())throw invalid();return n.get(f).asText();} private void array(JsonNode n,String f){if(!n.has(f)||!n.get(f).isArray())throw invalid();}
    private BusinessException invalid(){return new BusinessException(ErrorCode.AI_RESULT_INVALID);} private BusinessException normalized(RuntimeException f){return f instanceof BusinessException b?b:new BusinessException(ErrorCode.AI_RESULT_INVALID);} private BusinessException publicFailure(ExecutionFailure f){if("AI_CONFIGURATION_INVALID".equals(f.reason()))return new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);if("AI_RESULT_INVALID".equals(f.reason())||"RESULT_SCHEMA_INVALID".equals(f.code()))return invalid();return new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE);}
    private JsonNode parse(String value){return value==null?null:mapper.readTree(value);} private String trim(String value,int max){if(value==null||value.isBlank())return null;String t=value.trim();return t.substring(0,Math.min(max,t.length()));} private List<Long> distinctIds(List<Long> ids){return ids==null?List.of():ids.stream().filter(Objects::nonNull).distinct().toList();} private List<Long> idList(String json){JsonNode n=parse(json);List<Long> ids=new ArrayList<>();n.forEach(v->ids.add(v.asLong()));return ids;} private Long currentDetailedRunId(Long p,Long i){return detailedRuns.findTopByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(p,i).filter(r->r.getState()==ConceptAiRunBase.State.SUCCEEDED).map(DetailedAnalysisRun::getId).orElseThrow(()->new BusinessException(ErrorCode.ANALYSIS_INPUT_INVALID));}

    private ConceptView conceptView(ConceptVersion v){return new ConceptView(v.getId(),v.getConcept().getId(),v.getConcept().getDisplayOrder(),v.getName(),v.getOneLineSummary(),v.getTargetCustomer(),v.getProblem(),v.getSolution(),v.getValueProposition(),v.getRevenueModel(),parse(v.getKeyFeaturesJson()),parse(v.getDifferentiatorsJson()),parse(v.getAssumptionsJson()),parse(v.getRisksJson()),v.getIdeaVersion().getId());}
    private QuickView quickView(QuickAssessmentRun run){List<QuickAssessmentView> values=quickAssessments.findByRunIdAndDeletedAtIsNullOrderByOverallScoreDesc(run.getId()).stream().map(a->new QuickAssessmentView(a.getConceptVersion().getId(),a.getConceptVersion().getName(),a.getMarketScore(),a.getCustomerValueScore(),a.getFeasibilityScore(),a.getDifferentiationScore(),a.getRevenuePotentialScore(),a.getLegalRiskScore(),a.getOverallScore(),a.getSummary(),parse(a.getStrengthsJson()),parse(a.getWeaknessesJson()))).toList();return new QuickView(run.getId(),run.getState().name(),run.getIdeaVersion().getId(),values,run.getError(),run.getCompletedAt());}
    private ShortlistView shortlistView(ShortlistDecision s){return new ShortlistView(s.getId(),s.getIdeaVersion().getId(),idList(s.getSelectedConceptVersionIdsJson()),s.getReason(),s.getCreatedAt());}
    private DetailedView detailedView(DetailedAnalysisRun run){List<DetailedItemView> items=detailedAnalyses.findByRunIdAndDeletedAtIsNullOrderById(run.getId()).stream().map(a->new DetailedItemView(a.getConceptVersion().getId(),a.getConceptVersion().getName(),a.getMarketAnalysis(),a.getCustomerAnalysis(),a.getBusinessModelAnalysis(),a.getOperationAnalysis(),a.getRiskAnalysis(),a.getRecommendation(),parse(a.getAssumptionsJson()),parse(a.getResearchNeedsJson()))).toList();Map<Long,FinancialView> finance=new HashMap<>();financials.findByProjectIdAndIdeaVersionIdAndDeletedAtIsNullOrderById(run.getProject().getId(),run.getIdeaVersion().getId()).forEach(f->finance.put(f.getConceptVersion().getId(),financialView(f)));return new DetailedView(run.getId(),run.getState().name(),run.getIdeaVersion().getId(),items,finance,run.getError(),run.getCompletedAt());}
    private FinancialView financialView(JourneyFinancialAnalysis f){return new FinancialView(f.getConceptVersion().getId(),f.getUnitPrice(),f.getMonthlyCustomers(),f.getVariableCostPerCustomer(),f.getMonthlyFixedCost(),f.getInitialInvestment(),f.getMonthlyRevenue(),f.getMonthlyVariableCost(),f.getMonthlyTotalCost(),f.getMonthlyOperatingProfit(),f.getBreakEvenCustomers(),f.getPaybackMonths());}
    private SelectionView selectionView(ConceptSelection s){return new SelectionView(s.getId(),s.getIdeaVersion().getId(),s.getConceptVersion().getId(),s.getConceptVersion().getName(),s.getReason(),s.getCreatedAt());}

    private record Context(Project project,IdeaVersion idea){}
    public record ShortlistRequest(List<Long> conceptVersionIds,String reason){}
    public record FinancialInput(Long conceptVersionId,BigDecimal unitPrice,int monthlyCustomers,BigDecimal variableCostPerCustomer,BigDecimal monthlyFixedCost,BigDecimal initialInvestment){}
    public record DetailedRequest(List<FinancialInput> financials){}
    public record SelectionRequest(Long conceptVersionId,String reason){}
    public record ConceptView(Long id,Long conceptId,int displayOrder,String name,String oneLineSummary,String targetCustomer,String problem,String solution,String valueProposition,String revenueModel,JsonNode keyFeatures,JsonNode differentiators,JsonNode assumptions,JsonNode risks,Long ideaVersionId){}
    public record QuickAssessmentView(Long conceptVersionId,String conceptName,int market,int customerValue,int feasibility,int differentiation,int revenuePotential,int legalRisk,BigDecimal overallScore,String summary,JsonNode strengths,JsonNode weaknesses){}
    public record QuickView(Long id,String state,Long ideaVersionId,List<QuickAssessmentView> assessments,String error,LocalDateTime completedAt){}
    public record ShortlistView(Long id,Long ideaVersionId,List<Long> conceptVersionIds,String reason,LocalDateTime createdAt){}
    public record DetailedItemView(Long conceptVersionId,String conceptName,String marketAnalysis,String customerAnalysis,String businessModelAnalysis,String operationAnalysis,String riskAnalysis,String recommendation,JsonNode assumptions,JsonNode researchNeeds){}
    public record FinancialView(Long conceptVersionId,BigDecimal unitPrice,int monthlyCustomers,BigDecimal variableCostPerCustomer,BigDecimal monthlyFixedCost,BigDecimal initialInvestment,BigDecimal monthlyRevenue,BigDecimal monthlyVariableCost,BigDecimal monthlyTotalCost,BigDecimal monthlyOperatingProfit,int breakEvenCustomers,BigDecimal paybackMonths){}
    public record DetailedView(Long id,String state,Long ideaVersionId,List<DetailedItemView> analyses,Map<Long,FinancialView> financials,String error,LocalDateTime completedAt){}
    public record SelectionView(Long id,Long ideaVersionId,Long conceptVersionId,String conceptName,String reason,LocalDateTime createdAt){}
}
