package com.aivle.backend.journey;

import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장조사(1단계) · BM 캔버스(2단계). <b>패턴 B</b> — 큐에 넣고 워커가 돌린다.
 *
 * <p>{@link LegalPrecheckService} 의 구조를 따른다: {@code start} 는 TaskRun 만 만들고,
 * 상태 전이는 {@code current()} 가 불릴 때 {@link #synchronize} 가 한다.
 * <b>지연 반영이라 화면이 폴링해야 전이한다</b> — 패턴 B 에서 가장 헷갈리는 지점이다.
 */
@Service
public class MarketResearchService {

    private static final Logger log = LoggerFactory.getLogger(MarketResearchService.class);
    private static final String SCHEMA_VERSION = "1.0";

    private final ProjectRepository projects;
    private final MarketResearchRunRepository runs;
    private final MarketResearchVersionRepository versions;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRuns;
    private final com.aivle.backend.taskrun.service.CanonicalInputHasher hasher;
    private final MarketResearchInputFactory inputs;
    private final BmPlanPreparationService bmPlans;
    private final com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup seeds;
    private final ResearchConceptFactory concepts;
    private final ResearchCompetitorSeedService competitorSeeds;
    private final com.aivle.backend.pipeline.refinement.ConceptRefinementService refinement;
    private final ObjectMapper mapper;

    public MarketResearchService(ProjectRepository projects, MarketResearchRunRepository runs,
                                 MarketResearchVersionRepository versions, TaskResultRepository taskResults,
                                 TaskRunService taskRuns,
                                 com.aivle.backend.taskrun.service.CanonicalInputHasher hasher,
                                 MarketResearchInputFactory inputs,
                                 BmPlanPreparationService bmPlans,
                                 com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup seeds,
                                 ResearchConceptFactory concepts,
                                 ResearchCompetitorSeedService competitorSeeds,
                                 com.aivle.backend.pipeline.refinement.ConceptRefinementService refinement,
                                 ObjectMapper mapper) {
        this.projects = projects; this.runs = runs; this.versions = versions;
        this.taskResults = taskResults; this.taskRuns = taskRuns; this.hasher = hasher;
        this.inputs = inputs; this.bmPlans = bmPlans; this.seeds = seeds;
        this.concepts = concepts; this.competitorSeeds = competitorSeeds;
        this.refinement = refinement; this.mapper = mapper;
    }

    /**
     * 1단계 — 시장조사 전 구간.
     *
     * <p><b>갈래가 하나다.</b> 확정된 Market Seed 를 {@code concept.json} 으로 바꿔 실어
     * 보낸다. 이름표는 시드의 컨셉 식별자이고 그것이 원장 이름이 된다. AI 쪽에 그 원장이
     * 없으면 <b>수집부터</b> 돈다(LLM ≈80회 · 유료).
     *
     * <p>⚠ <b>화면이 보낸 {@code concept}·{@code conceptId} 는 언제나 무시한다.</b>
     * 예전에는 시드가 없으면 화면이 보낸 <b>견본 이름표로 떨어졌고</b>, 그것이
     * 「되돌리기 안전판」이라고 적혀 있었다. <b>안전판이 아니라 조용한 오답 장치였다</b> —
     * 2026-08-12 실측: 사업안 B 를 선택했지만 아직 확정 전이라(status
     * {@code LEGAL_REPORT_READY}) 시드가 없었고, 그래서 <b>미용실 노쇼 견본 원장</b>이
     * 재채점돼 「TAM 10.2억원 · 6/6 · SUCCEEDED」가 나왔다. 사용자는 냉동 간편식 사업안의
     * 결과라고 읽는다. 실패보다 나쁜 것은 <b>남의 자료로 성공했다고 말하는 것</b>이다.
     *
     * <p>그래서 시드가 없으면 <b>실패시킨다</b>(절대 규칙 — 조용한 기본값을 만들지 않는다).
     * 되돌릴 곳은 견본이 아니라 「사업안을 확정하라」는 말이다.
     */
    @Transactional
    public RunView startFull(Long ownerId, Long projectId, JsonNode concept, String conceptId, String asOf) {
        Project project = owned(ownerId, projectId);
        var seed = seeds.current(projectId).orElseThrow(() -> new BusinessException(
            ErrorCode.RESOURCE_NOT_FOUND,
            "확정된 사업안이 없다 — 사업안을 선택하고 확정한 뒤에 시장조사를 실행해야 한다"));
        String label = seeds.conceptIdOf(seed);
        JsonNode payload = concepts.build(label, mapper.readTree(seed.getSnapshotJson()),
            bmPlans.current(projectId).constraints(),
            competitorSeeds.conceptBlock(projectId));
        log.info("Market research runs on the confirmed business plan projectId={} conceptId={} portfolio={}",
            projectId, label, seeds.isPortfolio(seed));
        String input = inputs.full(payload, label, asOf);
        return start(ownerId, project, MarketResearchRun.Kind.FULL, null, input, label);
    }

    /**
     * 사업 검증 — <b>한 번 눌러 두 걸음</b>(시장조사 → BM 캔버스)을 돈다.
     *
     * <p>{@link #startFull} 과 같은 게이트를 쓴다. 확정된 사업안이 없으면 실패시킨다 —
     * 견본으로 떨어뜨리면 <b>남의 자료로 성공했다고 말하는 것</b>이 된다.
     *
     * <p>⚠ BM 걸음이 죽으면 조사 결과도 채택되지 않는다(TaskRun 하나에 채택은 한 번).
     * 완화책은 원장 재사용이다 — 원장 이름이 {@code conceptId} 이고 그 값은 사업안의
     * {@code portfolioConceptId} 라 <b>시드가 재발급돼도 같다</b>. 다시 눌러도 수집(유료)을
     * 다시 사지 않고 재채점만 한다.
     */
    @Transactional
    public RunView startValidation(Long ownerId, Long projectId, String asOf) {
        Project project = owned(ownerId, projectId);
        var seed = seeds.current(projectId).orElseThrow(() -> new BusinessException(
            ErrorCode.RESOURCE_NOT_FOUND,
            "확정된 사업안이 없다 — 사업안을 선택하고 확정한 뒤에 사업 검증을 실행해야 한다"));
        String label = seeds.conceptIdOf(seed);
        JsonNode payload = concepts.build(label, mapper.readTree(seed.getSnapshotJson()),
            bmPlans.current(projectId).constraints(),
            competitorSeeds.conceptBlock(projectId));
        log.info("Business validation runs on the confirmed business plan projectId={} conceptId={} portfolio={}",
            projectId, label, seeds.isPortfolio(seed));
        // 계획 4칸은 뒤 걸음(BM)이 쓴다. 한 입력에 같이 실어 보낸다 — 실행이 하나라
        // 중간에 다시 받을 자리가 없다.
        var plan = bmPlans.forExecution(projectId).orElse(null);
        String input = inputs.validation(payload, label, asOf,
            plan == null ? null : plan.plan(), plan == null ? null : plan.constraints());
        return start(ownerId, project, TaskType.BUSINESS_VALIDATION,
            MarketResearchRun.Kind.VALIDATION, null, input, label);
    }

    /**
     * 2단계 — 「다음」을 눌렀을 때. 1단계 결과를 근거로 캔버스를 만든다.
     *
     * <p>1단계가 성공해 있어야 한다. 없으면 근거 없는 캔버스가 되고, 그건 이 파이프라인이
     * 없애려는 실패 그 자체다.
     */
    @Transactional
    public RunView startBm(Long ownerId, Long projectId, String asOf) {
        Project project = owned(ownerId, projectId);
        MarketResearchVersion source = versions
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.FULL)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "시장조사 결과가 없다 — 1단계를 먼저 실행해야 한다"));
        // ⚠ **1단계가 쓴 이름표를 그대로 잇는다.** 클라이언트가 보낸 conceptId 로 덮지 않는다 —
        //    1단계와 다른 컨셉으로 판정하면 「관측은 A, 잣대는 B」가 되고, 그것이
        //    되짚기가 카페 컨셉을 집던 사고와 같은 종류다.
        //    이전에는 결과의 runId 를 sourceRun 으로 넘겼는데 그건 taskAttemptId 이지
        //    runs/ 밑 디렉터리가 아니라서 AI 쪽이 400 을 냈다. 원장은 이름표가 정한다.
        String label = mapper.readTree(source.getResultJson()).path("conceptId").asText();
        if (label == null || label.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "1단계 결과에 conceptId 가 없다 — 이어붙일 컨셉을 알 수 없다");
        }
        // ⚠ **1단계 결과가 «지금 확정된 사업안» 의 것인지 대조한다**(2026-08-12 실측).
        //    위 주석은 「클라이언트가 보낸 conceptId 로 덮지 않는다」까지만 지켰는데,
        //    **1단계 결과 자체가 남의 컨셉이면 그대로 이어진다.** 실제로 그렇게 돌았다:
        //    사업안 B 의 1단계가 아직 안 끝난 시점에 BM 을 눌렀더니 가장 최신 FULL 결과가
        //    미용실 견본이었고, BM 캔버스가 `conceptId=beauty-noshow` 로 나왔다.
        //    사업안을 바꾸면 옛 1단계 결과가 그대로 남으므로 재현 조건이 흔하다.
        String current = seeds.current(projectId).map(seeds::conceptIdOf).orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "확정된 사업안이 없다 — 사업안을 확정한 뒤에 BM 분석을 실행해야 한다"));
        if (!current.equals(label)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "1단계 결과가 지금 사업안의 것이 아니다 — 이 사업안으로 시장조사를 먼저 실행해야 한다");
        }
        // 사용자가 앞 화면에서 채운 실행 계획. 없으면 기존 경로 그대로다 —
        // 계획 칸은 견본의 `_bm_plan` 이 있으면 그것으로, 없으면 빈 채로 나간다.
        var plan = bmPlans.forExecution(projectId).orElse(null);
        String input = plan == null
            ? inputs.bm(label, asOf)
            : inputs.bm(label, asOf, plan.plan(), plan.constraints());
        return start(ownerId, project, MarketResearchRun.Kind.BM, source.getSourceRun(), input, label);
    }

    /**
     * BM 앞 단계의 실행 계획 — 읽기.
     *
     * <p>소유 검사를 여기서 한 번 하고 보관은 {@link BmPlanPreparationService} 가 한다.
     * 준비물의 정규화 규칙을 이 서비스가 또 갖게 하면 「비었다」의 정의가 두 곳이 된다.
     */
    @Transactional(readOnly = true)
    public BmPlanPreparationService.PlanView currentPlan(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return bmPlans.current(projectId);
    }

    /** 경쟁 씨앗 — 읽기. 소유 검사만 여기서 하고 정규화는 씨앗 서비스가 한다. */
    @Transactional(readOnly = true)
    public ResearchCompetitorSeedService.SeedsView currentSeeds(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        return competitorSeeds.current(projectId);
    }

    @Transactional
    public ResearchCompetitorSeedService.SeedsView saveSeeds(Long ownerId, Long projectId,
                                                             JsonNode payload) {
        owned(ownerId, projectId);
        return competitorSeeds.replace(projectId, ownerId, payload);
    }

    @Transactional
    public BmPlanPreparationService.PlanView savePlan(Long ownerId, Long projectId,
                                                      JsonNode plan, JsonNode constraints) {
        owned(ownerId, projectId);
        return bmPlans.save(projectId, ownerId, plan, constraints);
    }

    private RunView start(Long ownerId, Project project, MarketResearchRun.Kind kind,
                          MarketResearchRun sourceRun, String inputJson, String conceptId) {
        return start(ownerId, project, TaskType.MARKET_RESEARCH, kind, sourceRun, inputJson, conceptId);
    }

    private RunView start(Long ownerId, Project project, TaskType taskType, MarketResearchRun.Kind kind,
                          MarketResearchRun sourceRun, String inputJson, String conceptId) {
        String inputHash = hasher.hash(taskType, SCHEMA_VERSION, "ko-KR", inputJson);
        // ⚠ **nonce 가 필요하다.** 「누를 때마다 새로 실행」이라 같은 컨셉이면 canonicalInputHash 가
        //    같고, `idx_task_runs_active_conflict` 와 `TaskRunService.create` 의 중복 방지에 걸린다.
        //    마케팅 리포트가 같은 이유로 UUID 를 쓴다.
        String nonce = UUID.randomUUID().toString();
        TaskRun task = taskRuns.create(ownerId, project.getId(), taskType,
            "MARKET_RESEARCH_" + kind.name(), conceptId, inputJson, inputHash, nonce, nonce, 1);
        return runView(runs.save(MarketResearchRun.create(project, kind, sourceRun, task, inputHash)));
    }

    /** 화면이 폴링하는 자리. <b>여기서 상태가 전이한다.</b> */
    @Transactional
    public CurrentView current(Long ownerId, Long projectId, MarketResearchRun.Kind kind) {
        owned(ownerId, projectId);
        MarketResearchRun run = runs
            .findTopByProjectIdAndKindAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, kind)
            .orElse(null);
        if (run == null) return new CurrentView(null, null);
        synchronize(run);
        MarketResearchVersion version = versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        return new CurrentView(runView(run), version == null ? null : versionView(version));
    }

    private void synchronize(MarketResearchRun run) {
        TaskRun task = run.getTaskRun();
        TaskRunState state = task.getState();
        if (state == TaskRunState.QUEUED || state == TaskRunState.READY) return;
        if (state == TaskRunState.RUNNING) { run.running(); runs.save(run); return; }
        if (state == TaskRunState.FAILED || state == TaskRunState.TIMED_OUT
            || state == TaskRunState.CANCELLED) {
            if (run.getState() != MarketResearchRun.State.FAILED) {
                log.warn("Market research task failed projectId={} runId={} kind={} taskRunId={} errorCode={} retryable={}",
                    run.getProject().getId(), run.getId(), run.getKind(), task.getId(),
                    task.getLastErrorCode(), task.isRetryable());
                run.fail(task.getLastErrorCode());
                runs.save(run);
            }
            return;
        }
        if (state != TaskRunState.SUCCEEDED
            || versions.findBySourceRunIdAndDeletedAtIsNull(run.getId()).isPresent()) return;
        TaskResult result = task.getFinalResultId() == null ? null
            : taskResults.findById(task.getFinalResultId()).orElse(null);
        if (result == null) return;
        materialize(run, mapper.readTree(result.getResultJson()));
        run.succeed();
        runs.save(run);
        startRefinementLoop(run);
    }

    /**
     * 사업 검증이 끝난 <b>바로 그 자리</b>에서 컨셉 다듬기 라운드 1을 건다.
     *
     * <p>왜 여기인가. 「방금 검증이 끝났다」를 아는 곳은 여기뿐이다 —
     * {@link #synchronize} 는 버전이 이미 있으면 위에서 되돌아가므로 이 줄은
     * <b>한 실행당 정확히 한 번</b> 지나간다. 다듬기 워커는 이미 있는 라운드만 다음으로 밀 뿐
     * 라운드 1을 거는 자리가 없어, 다듬기가 영원히 시작되지 않았다(2026-08-13 실측).
     *
     * <p>{@code VALIDATION} 과 {@code BM} 에서 건다. 옛 두 걸음짜리 실행에서 「검증이 끝났다」가
     * 성립하는 자리가 <b>바로 BM 걸음이 끝나는 순간</b>이다 — 다듬기 재료(게이트 사유·캔버스·
     * 근거)가 그때 다 갖춰진다. {@code ConceptRefinementService.material} 도 이미 VALIDATION 이
     * 없으면 BM 을 읽도록 돼 있어, 재료 쪽은 처음부터 두 갈래를 다 보고 있었다.
     *
     * <p>⚠ 예전에는 VALIDATION 만 걸었다. 「두 곳에서 걸면 라운드 1이 두 번 선다」는 우려였는데,
     * {@code startFirstRound} 가 <b>라운드가 하나라도 있으면 건너뛴다</b> —
     * 그 우려는 이미 막혀 있다. FULL 은 여전히 안 건다(캔버스가 없어 재료가 반쪽이다).
     *
     * <p>⚠ <b>여기서 죽어도 검증 결과는 살아야 한다.</b> 다듬기는 뒤따르는 부가 걸음이지
     * 채택의 조건이 아니다. 사유는 화면이 아니라 로그로 보낸다.
     */
    private void startRefinementLoop(MarketResearchRun run) {
        if (run.getKind() != MarketResearchRun.Kind.VALIDATION
            && run.getKind() != MarketResearchRun.Kind.BM) return;
        try {
            refinement.startFirstRound(run.getProject().getId()).ifPresent(task ->
                log.info("Concept refinement round 1 queued projectId={} researchRunId={} taskRunId={}",
                    run.getProject().getId(), run.getId(), task.getId()));
        } catch (RuntimeException e) {
            // 확정된 선택이 없거나(조용히 건너뜀은 서비스 몫), 선택이 이미 다른 액션을 물고
            // 있는 경우다. 어느 쪽이든 검증 결과를 되돌릴 이유가 아니다.
            log.warn("Concept refinement round 1 could not be queued projectId={} researchRunId={} reason={}",
                run.getProject().getId(), run.getId(), e.toString());
        }
    }

    /**
     * <b>결과를 쪼개지 않는다.</b> 통째로 저장하고 목록용 스칼라만 따로 센다.
     *
     * <p>{@code caveatCount} 를 세는 이유는 <b>경계가 0으로 떨어지는 것을 눈으로 보기 위해서다</b>.
     * JSON 안에 묻혀 있으면 아무도 안 본다.
     */
    private void materialize(MarketResearchRun run, JsonNode result) {
        int evidenceCount = result.path("evidence").size();
        int caveatCount = 0;
        for (JsonNode item : result.path("evidence")) caveatCount += item.path("caveats").size();
        for (JsonNode cell : result.path("canvas").path("cells")) caveatCount += cell.path("caveats").size();

        Integer filled = null, partial = null, missing = null;
        if (result.path("scorecard").isArray()) {
            filled = 0; partial = 0; missing = 0;
            for (JsonNode item : result.get("scorecard")) {
                switch (item.path("state").asText()) {
                    case "FILLED" -> filled++;
                    case "PARTIAL" -> partial++;
                    case "MISSING" -> missing++;
                    default -> { }        // REPORTED(⑦행)는 성적에 안 센다
                }
            }
        }
        JsonNode bm = result.path("bm");
        MarketResearchVersion.Summary summary = new MarketResearchVersion.Summary(
            filled, partial, missing,
            bm.isObject() ? bm.path("decision").asText(null) : null,
            bm.isObject() ? bm.path("confidence").asText(null) : null,
            evidenceCount, caveatCount);

        int number = Math.toIntExact(versions.countByProjectIdAndKindAndDeletedAtIsNull(
            run.getProject().getId(), run.getKind()) + 1);
        versions.save(MarketResearchVersion.of(run.getProject(), run, number,
            result.toString(), summary));
        if (caveatCount == 0 && evidenceCount > 0) {
            // 근거가 있는데 경계가 하나도 없으면 **소실을 의심해야 한다**. 막지는 않는다 —
            // 경계가 진짜로 없는 관측도 있다. 다만 조용히 지나가지 않는다.
            log.warn("Market research result has evidence but no caveats projectId={} runId={} kind={} evidence={}",
                run.getProject().getId(), run.getId(), run.getKind(), evidenceCount);
        }
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
    }

    private RunView runView(MarketResearchRun run) {
        return new RunView(run.getId(), run.getKind().name(), run.getState().name(),
            run.getTaskRun().getId(), run.getTaskRun().getState().name(),
            run.getErrorCode(), safeErrorReason(run.getTaskRun().getLastErrorReason()), run.getTaskRun().isRetryable());
    }

    /** Only contract-level reasons are returned; provider details and input text stay server-side. */
    private String safeErrorReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "FIELD_CONSTRAINT_VIOLATION", "HASH_MISMATCH", "UNKNOWN_FIELD",
                 "CHUNK_COUNT_EXCEEDED", "CHUNK_SEQUENCE_INVALID", "REQUEST_CONTRACT_INVALID",
                 "REQUEST_DEADLINE_EXCEEDED", "AI_CONFIGURATION_INVALID" -> reason;
            default -> null;
        };
    }

    private VersionView versionView(MarketResearchVersion version) {
        return new VersionView(version.getId(), version.getKind().name(), version.getVersionNumber(),
            mapper.readTree(version.getResultJson()),
            version.getEvidenceCount(), version.getCaveatCount(),
            version.getDecision(), version.getConfidence(),
            version.getFilledCount(), version.getPartialCount(), version.getMissingCount());
    }

    public record RunView(Long id, String kind, String state, String taskRunId, String taskState,
                          String errorCode, String errorReason, boolean retryable) { }

    /** {@code result} 는 계약 그대로다 — 백엔드가 다시 가공하지 않는다. */
    public record VersionView(Long id, String kind, Integer versionNumber, JsonNode result,
                              Integer evidenceCount, Integer caveatCount,
                              String decision, String confidence,
                              Integer filledCount, Integer partialCount, Integer missingCount) { }

    public record CurrentView(RunView run, VersionView version) { }
}
