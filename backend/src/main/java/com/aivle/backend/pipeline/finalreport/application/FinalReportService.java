package com.aivle.backend.pipeline.finalreport.application;

import static com.aivle.backend.pipeline.finalreport.api.FinalReportApiModels.*;
import static com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Binding;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import com.aivle.backend.pipeline.finalreport.domain.FinalReportSnapshot;
import com.aivle.backend.pipeline.finalreport.repository.FinalReportSnapshotRepository;
import com.aivle.backend.pipeline.finance.repository.FinancialInputSnapshotRepository;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot;
import com.aivle.backend.pipeline.launchreadiness.domain.LaunchReadinessInputSnapshot.ModuleType;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessInputSnapshotRepository;
import com.aivle.backend.pipeline.launchreadiness.repository.LaunchReadinessReportRepository;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentStatus;
import com.aivle.backend.pipeline.marketing.repository.MarketingAssetRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRevisionRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketing.strategy.repository.MarketingStrategyReportRepository;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.pipeline.market.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.market.MarketInterviewRun;
import com.aivle.backend.pipeline.market.MarketInterviewVersion;
import com.aivle.backend.pipeline.market.MarketInterviewVersionRepository;
import com.aivle.backend.pipeline.module.ProjectModuleStatusResponse;
import com.aivle.backend.pipeline.module.ProjectModuleStatusService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@Transactional(readOnly = true)
public class FinalReportService {
    private static final int MANIFEST_SCHEMA_VERSION = 2;
    private static final List<String> OPTIONAL = List.of("MARKET_INTERVIEW", "MARKETING_STRATEGY",
        "MARKETING", "MARKETING_ASSETS", "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");
    private static final java.util.Set<String> STRATEGY_CONTEXT_TYPES = java.util.Set.of(
        "CURRENT_CONCEPT", "MARKET", "BUSINESS_MODEL", "MARKET_INTERVIEW",
        "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");

    private final ProjectRepository projects;
    private final UserRepository users;
    private final CurrentConceptSourceResolver currentConcepts;
    private final MarketResearchVersionRepository marketVersions;
    private final MarketInterviewVersionRepository marketInterviews;
    private final MarketingSourceSnapshotRepository marketingSources;
    private final MarketingContentRepository marketingContents;
    private final MarketingContentRevisionRepository marketingRevisions;
    private final MarketingAssetRepository marketingAssets;
    private final MarketingStrategyReportRepository marketingStrategies;
    private final LaunchReadinessInputSnapshotRepository launchInputs;
    private final LaunchReadinessReportRepository launchReports;
    private final FinancialInputSnapshotRepository financeSnapshots;
    private final TaskRunRepository taskRuns;
    private final TaskAttemptRepository taskAttempts;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRunService;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ProjectModuleStatusService moduleStatuses;
    private final FinalReportSnapshotRepository snapshots;
    private final FinalReportComposer composer;
    private final BusinessProposalEvidenceCatalog evidenceCatalog;
    private final ObjectMapper mapper;

    public FinalReportView current(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        FinalReportSnapshot snapshot = snapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId).orElse(null);
        if (snapshot == null) return draft(project, current, 1);
        return view(current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE, snapshot, current);
    }

    public FinalReportSnapshot requireSnapshot(Long ownerId, Long projectId, String snapshotId) {
        owned(ownerId, projectId);
        return snapshots.findByIdAndProjectIdAndDeletedAtIsNull(snapshotId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public State state(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        return snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(snapshot -> current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE)
            .orElse(current.ready() ? State.READY : State.NOT_READY);
    }

    public FinalReportStatusView status(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        FinalReportSnapshot snapshot = snapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId).orElse(null);
        TaskRun latest = taskRuns.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION).orElse(null);
        TaskRun active = java.util.Optional.ofNullable(latest)
            .filter(task -> !java.util.Set.of("SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT", "NEEDS_INPUT")
                .contains(task.getState().name()))
            .orElse(null);
        State state = active != null ? State.GENERATING : snapshot == null ? (current.ready() ? State.READY : State.NOT_READY)
            : (current.ready() && exact(snapshot, current) ? State.CURRENT : State.STALE);
        List<String> available = current.sources().stream().map(ReportSource::type).distinct().toList();
        return new FinalReportStatusView(state, snapshot == null ? null : snapshot.getReportVersion(),
            snapshot == null ? null : snapshot.getGeneratedAt(), state == State.STALE,
            active == null ? null : active.getId(),
            current.blocking(), available, current.omitted(), sourceStates(current, projectId),
            latest == null ? null : latest.getId(), latest == null ? null : latest.getState().name(),
            latest == null ? null : latest.getLastErrorCode(), lastErrorReason(latest));
    }

    public CurrentSourceCatalog currentSourceCatalog(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        SourceSet current = sources(ownerId, project);
        ArrayNode manifest = mapper.createArrayNode();
        current.manifest().path("sources").forEach(item -> manifest.add(item.deepCopy()));
        ObjectNode sourceData = mapper.createObjectNode();
        current.sources().forEach(source -> sourceData.set(source.type(), source.data().deepCopy()));
        return new CurrentSourceCatalog(manifest, sourceData, sourceStates(current, projectId),
            current.blocking(), current.omitted(), current.hash(), strategySourceHash(current.sources()));
    }

    private String lastErrorReason(TaskRun latest) {
        if (latest == null || latest.getCurrentAttemptId() == null) return null;
        return taskAttempts.findByIdAndTaskRunId(latest.getCurrentAttemptId(), latest.getId())
            .map(attempt -> attempt.getErrorReason()).orElse(null);
    }

    @Transactional
    public ProposalActionResponse startProposal(Long ownerId, Long projectId, String idempotencyKey,
            String correlationId, List<String> includedOptionalSources) {
        String key = requiredKey(idempotencyKey);
        Project project = owned(ownerId, projectId);
        SourceSet current = selectSources(sources(ownerId, project), includedOptionalSources);
        if (!current.ready()) throw new BusinessException(ErrorCode.FINAL_REPORT_NOT_READY);
        int version = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(value -> value.getReportVersion() + 1).orElse(1);
        ObjectNode input = mapper.createObjectNode();
        input.put("contract", "final-business-proposal-input-v1"); input.put("projectId", projectId);
        input.put("version", version); input.put("sourceManifestHash", current.hash());
        input.set("sourceManifest", current.manifest().path("sources").deepCopy());
        ArrayNode included = input.putArray("includedSourceTypes");
        current.sources().forEach(source -> included.add(source.type()));
        ArrayNode omitted = input.putArray("omittedSourceTypes"); current.omitted().forEach(omitted::add);
        ObjectNode sourceData = input.putObject("sources");
        current.sources().forEach(source -> sourceData.set(source.type(), compactSource(source.data())));
        // ⚠ 목록은 **손질 전 원본**으로 만든다. 그래서 위의 손질이 근거를 하나도 지우지 않는다.
        ArrayNode catalog = evidenceCatalog.build(current.sources());
        input.set("evidenceCatalog", catalog);
        ArrayNode allowedEvidenceKeys = input.putArray("allowedEvidenceKeys");
        catalog.forEach(item -> allowedEvidenceKeys.add(item.path("evidenceKey").asText()));
        String inputJson = write(input);
        // 2 MB 상한에 걸리면 TaskRunService 는 「TASK_RUN_INPUT_INVALID」만 낸다 — 무엇이 큰지
        // 말해 주지 않으면 고칠 수가 없다. 조각별 크기를 남긴다.
        int inputBytes = inputJson.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > PROPOSAL_INPUT_WARN_BYTES) {
            StringBuilder breakdown = new StringBuilder();
            input.propertyNames().forEach(name -> breakdown.append(name).append('=')
                .append(write(input.path(name)).getBytes(StandardCharsets.UTF_8).length / 1024).append("kB "));
            JsonNode measured = input.path("sources");
            measured.propertyNames().forEach(name -> breakdown.append("sources.").append(name).append('=')
                .append(write(measured.path(name)).getBytes(StandardCharsets.UTF_8).length / 1024).append("kB "));
            log.warn("Final proposal input large projectId={} total={}kB {}", projectId, inputBytes / 1024, breakdown);
        }
        String inputHash = inputHasher.hash(TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION,
            "1.0", "ko-KR", inputJson);
        String reportId = current.hash().substring("sha256:".length());
        var created = taskRunService.createWithDisposition(ownerId, projectId,
            TaskType.FINAL_BUSINESS_PROPOSAL_GENERATION, "FINAL_BUSINESS_PROPOSAL", reportId,
            inputJson, inputHash, key, correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.final-report.queued", JobEvent.Status.QUEUED, null);
        return new ProposalActionResponse(reportId, created.taskRun().getId(),
            created.taskRun().getState().name(), current.hash());
    }

    @Transactional
    public String completeProposal(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response) {
        JsonNode taskInput = json(context.inputSnapshot());
        JsonNode result = response.result().deepCopy();
        validateProposal(result);
        canonicalizeEvidence(result, taskInput.path("sourceManifest"), taskInput.path("evidenceCatalog"));
        Project project = owned(context.ownerId(), context.projectId());
        List<String> selected = new ArrayList<>();
        taskInput.path("includedSourceTypes").forEach(item -> selected.add(item.asText()));
        SourceSet current = selectSources(sources(context.ownerId(), project), selected);
        if (!taskInput.path("sourceManifestHash").asText().equals(current.hash()))
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        String resultJson = write(result);
        taskRunService.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), resultJson,
            response.canonicalInputHash(), "1.0");
        int version = taskInput.path("version").asInt();
        FinalReportSnapshot saved = snapshots.findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(
            context.projectId(), context.idempotencyKey()).orElse(null);
        if (saved == null) {
            Binding binding = current.binding();
            saved = snapshots.save(FinalReportSnapshot.create(context.projectId(), version, write(current.manifest()),
                current.hash(), resultJson, Instant.now(), context.ownerId(), binding.marketSeedSnapshotId(),
                binding.selectionId(), binding.selectionRevision(), binding.bmPlanRevision(), current.bindingHash(),
                context.idempotencyKey(), context.inputHash(), MANIFEST_SCHEMA_VERSION));
        }
        return saved.getId();
    }

    @Transactional
    public void failProposal(TaskRunService.Claim claim, String code, String reason, boolean retryable) {
        taskRunService.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), code, reason, retryable);
    }

    @Transactional
    public ProposalActionResponse startReview(Long ownerId, Long projectId, String snapshotId,
            String idempotencyKey, String correlationId) {
        String key = requiredKey(idempotencyKey);
        owned(ownerId, projectId);
        FinalReportSnapshot snapshot = snapshots.findByIdAndProjectIdAndDeletedAtIsNull(snapshotId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ObjectNode input = mapper.createObjectNode();
        input.put("contract", "final-business-proposal-review-input-v1");
        input.put("projectId", projectId); input.put("snapshotId", snapshotId);
        input.put("sourceManifestHash", snapshot.getSourceManifestHash());
        input.set("sourceManifest", json(snapshot.getSourceManifestJson()).path("sources").deepCopy());
        input.set("proposal", json(snapshot.getReportJson()));
        String inputJson = write(input);
        String hash = inputHasher.hash(TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW, "1.0", "ko-KR", inputJson);
        var created = taskRunService.createWithDisposition(ownerId, projectId,
            TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW, "FINAL_BUSINESS_PROPOSAL_REVIEW", snapshotId,
            inputJson, hash, key, correlationId == null || correlationId.isBlank() ? key : correlationId, 2);
        if (created.createdNew()) publish(projectId, created.taskRun().getId(), "QUEUED",
            "job.final-report.review.queued", JobEvent.Status.QUEUED, null);
        return new ProposalActionResponse(snapshotId, created.taskRun().getId(),
            created.taskRun().getState().name(), snapshot.getSourceManifestHash());
    }

    @Transactional(readOnly = true)
    public ReviewView currentReview(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        String snapshotId = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(FinalReportSnapshot::getId).orElse(null);
        return currentReview(ownerId, projectId, snapshotId);
    }

    @Transactional(readOnly = true)
    public ReviewView currentReview(Long ownerId, Long projectId, String snapshotId) {
        owned(ownerId, projectId);
        TaskRun run = taskRuns.findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, TaskType.FINAL_BUSINESS_PROPOSAL_REVIEW).orElse(null);
        if (run == null || snapshotId == null || !snapshotId.equals(run.getSubjectId()))
            return new ReviewView(null, "NOT_STARTED", null, null);
        var adopted = taskResults.findByTaskRunId(run.getId()).stream()
            .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED).findFirst().orElse(null);
        return new ReviewView(run.getId(), run.getState().name(),
            adopted == null ? null : json(adopted.getResultJson()),
            adopted == null ? null : instant(adopted.getAdoptedAt()));
    }

    @Transactional
    public void completeReview(TaskRunService.Claim claim, TaskRunWorkerContext context,
            ExecutionResponse response) {
        JsonNode result = response.result().deepCopy();
        if (!result.isObject() || !"final-business-proposal-review-v1".equals(result.path("contract").asText()))
            throw new IllegalArgumentException("FINAL_BUSINESS_PROPOSAL_REVIEW_INVALID");
        canonicalizeEvidence(result, json(context.inputSnapshot()).path("sourceManifest"));
        taskRunService.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), write(result),
            response.canonicalInputHash(), "1.0");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Long projectId, String taskRunId, String stage, String key,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskRunId, taskRunId, stage, key,
            status, key, java.util.Map.of(), code));
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId, String idempotencyKey) {
        return generate(ownerId, projectId, idempotencyKey, OPTIONAL);
    }

    @Transactional
    public FinalReportView generate(Long ownerId, Long projectId, String idempotencyKey,
            List<String> includedOptionalSources) {
        String key = requiredKey(idempotencyKey);
        Project project = owned(ownerId, projectId);
        SourceSet allCurrent = sources(ownerId, project);
        SourceSet current = selectSources(allCurrent, includedOptionalSources);
        int nextVersion = snapshots.findFirstByProjectIdAndDeletedAtIsNullOrderByReportVersionDesc(projectId)
            .map(value -> value.getReportVersion() + 1).orElse(1);
        if (!current.ready()) return draft(project, current, nextVersion);

        String identityHash = commandIdentity(projectId, current);
        FinalReportSnapshot replay = snapshots
            .findByProjectIdAndCommandIdempotencyKeyAndDeletedAtIsNull(projectId, key).orElse(null);
        if (replay != null) {
            if (!identityHash.equals(replay.getCommandIdentityHash()))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return view(exact(replay, current) ? State.CURRENT : State.STALE, replay, current);
        }

        Instant now = Instant.now();
        ObjectNode report = composer.compose(project, nextVersion, now, current.sources());
        SourceSet beforeSave = selectSources(sources(ownerId, project), includedOptionalSources);
        if (!beforeSave.ready() || !current.hash().equals(beforeSave.hash())
                || !current.bindingHash().equals(beforeSave.bindingHash())) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "분석 결과가 변경되었습니다. 최신 자료로 다시 생성해 주세요.");
        }
        Binding binding = current.binding();
        FinalReportSnapshot saved = snapshots.save(FinalReportSnapshot.create(projectId, nextVersion,
            write(current.manifest()), current.hash(), write(report), now, ownerId,
            binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(),
            binding.bmPlanRevision(), current.bindingHash(), key, identityHash, MANIFEST_SCHEMA_VERSION));
        return view(State.CURRENT, saved, current);
    }

    private SourceSet selectSources(SourceSet source, List<String> includedOptionalSources) {
        java.util.Set<String> selectedOptional = includedOptionalSources == null ? new java.util.HashSet<>()
            : includedOptionalSources.stream().filter(OPTIONAL::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
        if (selectedOptional.contains("MARKETING")) selectedOptional.add("MARKETING_ASSETS");
        if (selectedOptional.contains("FINANCE")) selectedOptional.add("FINANCE_REPORT");
        List<ReportSource> selected = source.sources().stream()
            .filter(item -> !OPTIONAL.contains(item.type()) || selectedOptional.contains(item.type()))
            .toList();
        java.util.Set<String> includedTypes = selected.stream().map(ReportSource::type)
            .collect(java.util.stream.Collectors.toSet());
        List<String> omitted = OPTIONAL.stream().filter(type -> !includedTypes.contains(type)).toList();
        ObjectNode manifest = composer.manifest(source.binding() == null ? null : bindingJson(source.binding()), selected);
        ArrayNode omittedValues = manifest.putArray("omittedSources");
        omitted.forEach(omittedValues::add);
        return new SourceSet(selected, manifest, composer.hash(manifest), source.binding(), source.bindingHash(),
            source.readiness(), source.blocking(), omitted);
    }

    /** HTTP generation requires an explicit Idempotency-Key. */
    public FinalReportView generate(Long ownerId, Long projectId) {
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    private FinalReportView view(State state, FinalReportSnapshot snapshot, SourceSet current) {
        String generatedByName = users.findByIdAndDeletedAtIsNull(snapshot.getGeneratedBy())
            .map(user -> user.getName()).orElse("알 수 없는 사용자");
        return new FinalReportView(state, snapshot.getId(), snapshot.getReportVersion(), snapshot.getGeneratedAt(),
            snapshot.getSourceManifestHash(), json(snapshot.getSourceManifestJson()), json(snapshot.getReportJson()),
            snapshot.getGeneratedBy(), generatedByName,
            current.readiness(), combined(current), current.blocking(), current.omitted());
    }

    private FinalReportView draft(Project project, SourceSet current, int version) {
        Instant now = Instant.now();
        return new FinalReportView(current.ready() ? State.READY : State.NOT_READY, null, null, null,
            current.hash(), current.manifest(),
            composer.compose(project, version, now, current.sources()), null, null,
            current.readiness(), combined(current),
            current.blocking(), current.omitted());
    }

    private List<String> combined(SourceSet source) {
        List<String> result = new ArrayList<>(source.blocking()); result.addAll(source.omitted());
        return List.copyOf(result);
    }

    private SourceSet sources(Long ownerId, Project project) {
        Long projectId = project.getId();
        List<ReportSource> values = new ArrayList<>();
        ObjectNode projectData = mapper.createObjectNode();
        projectData.put("name", project.getTitle()); putNullable(projectData, "description", project.getDescription());
        putNullable(projectData, "industryCategory", project.getIndustryCategory());
        values.add(source("PROJECT", String.valueOf(projectId), project.getVersion().intValue(), null,
            null, instant(project.getUpdatedAt()), projectData));

        Source authority = currentSource(projectId);
        List<String> blocking = new ArrayList<>();
        if (authority == null) {
            blocking.add("CURRENT_CONCEPT");
            return sourceSet(values, null, blocking, OPTIONAL, ownerId, projectId);
        }
        Binding binding = currentConcepts.binding(authority);
        ObjectNode bindingJson = bindingJson(binding);
        ObjectNode conceptData = mapper.createObjectNode();
        conceptData.set("concept", json(authority.seed().getSnapshotJson()));
        conceptData.set("businessModel", authority.bm().plan().deepCopy());
        conceptData.set("constraints", authority.bm().constraints().deepCopy());
        values.add(source("CURRENT_CONCEPT", authority.seed().getId(), null, binding.selectionRevision(),
            authority.seed().getSnapshotHash(), authority.seed().getFinalizedAt(), conceptData));

        // 시장·BM 은 **최신 버전을 그대로** 붙든다.
        //
        // ⚠ 예전에는 `BusinessValidationSession` 이 「이 시드·이 개정으로 둘이 한 세션에서
        //   끝났다」를 증명했다. 2·4 를 main 판으로 되돌리며 그 세션 개념이 없어졌다 —
        //   main 은 시장과 BM 을 각자의 버전으로 두고 **BM 이 어느 시장 버전 위에 섰는지**를
        //   `MarketResearchRun.sourceMarketVersionId` 로 잇는다. 그러니 세션을 흉내 내지 않고
        //   그 사슬을 그대로 쓴다. 둘 중 하나라도 없으면 막는 것은 그대로다.
        marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.FULL)
            .ifPresent(version -> values.add(source("MARKET", String.valueOf(version.getId()),
                version.getVersionNumber(), null, null, instant(version.getUpdatedAt()),
                json(version.getResultJson()))));
        marketVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, MarketResearchRun.Kind.BM)
            .ifPresent(version -> values.add(source("BUSINESS_MODEL", String.valueOf(version.getId()),
                version.getVersionNumber(), null, null, instant(version.getUpdatedAt()),
                json(version.getResultJson()))));
        if (!has(values, "MARKET")) blocking.add("MARKET");
        if (!has(values, "BUSINESS_MODEL")) blocking.add("BUSINESS_MODEL");

        addMarketInterview(values, projectId, binding);
        addMarketing(values, projectId, binding);
        addLaunch(values, projectId, ModuleType.TECHNOLOGY, "LAUNCH_TECHNOLOGY");
        addLaunch(values, projectId, ModuleType.OPERATIONS, "LAUNCH_OPERATIONS");
        addFinance(values, projectId, binding);
        addMarketingStrategy(values, projectId);
        List<String> omitted = OPTIONAL.stream().filter(type -> !has(values, type)).toList();
        return sourceSet(values, bindingJson, blocking, omitted, ownerId, projectId);
    }

    /**
     * 시장 인터뷰 결과를 최종 보고서 재료로 넣는다.
     *
     * ⚠ **`binding` 으로 거르지 않는다.** main 의 `MarketInterviewRun` 은 씨앗 스냅샷을
     *   붙들지 않는다 — 인터뷰가 응답자에게 보인 것은 그때 화면이 보낸 컨셉보드이고, 그것이
     *   낡았는지는 시드가 아니라 그 보드가 정한다(그래서 STALE 갈래도 없다). 없는 계보로
     *   거르는 시늉을 하면 **항상 비는 칸**이 된다. 선택 재료라 없으면 그냥 빠진다.
     */
    private void addMarketInterview(List<ReportSource> values, Long projectId, Binding binding) {
        marketInterviews.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .filter(version -> version.getSourceRun().getState() == MarketInterviewRun.State.SUCCEEDED)
            .ifPresent(version -> values.add(source("MARKET_INTERVIEW", String.valueOf(version.getId()),
                version.getVersionNumber(), null, version.getSourceRun().getInputSnapshotHash(),
                instant(version.getSourceRun().getCompletedAt()), json(version.getResultJson()))));
    }

    private void addMarketing(List<ReportSource> values, Long projectId, Binding binding) {
        marketingSources
            .findBySourceMarketSeedSnapshotIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndProjectIdAndDeletedAtIsNull(
                binding.marketSeedSnapshotId(), binding.selectionRevision(), binding.bmPlanRevision(), projectId)
            .filter(source -> Objects.equals(source.getPortfolioSelectionId(), binding.selectionId()))
            .map(source -> marketingContents
                .findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByFinalizedAtDesc(
                    projectId, source.getId(), MarketingContentStatus.FINALIZED)
                .orElseGet(() -> marketingContents
                    .findFirstByProjectIdAndMarketingSourceSnapshotIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        projectId, source.getId(), MarketingContentStatus.COMPLETED).orElse(null)))
            .filter(Objects::nonNull)
            .ifPresent(content -> {
                boolean draft = content.getStatus() == MarketingContentStatus.COMPLETED;
                int revisionNumber = draft ? content.getCurrentRevisionNumber() : content.getFinalizedRevisionNumber();
                marketingRevisions.findByContentIdAndRevisionNumberAndDeletedAtIsNull(content.getId(), revisionNumber)
                .ifPresent(revision -> {
                    JsonNode raw = json(revision.getResultJson());
                    ObjectNode data = raw.isObject() ? (ObjectNode) raw.deepCopy() : mapper.createObjectNode().set("result", raw);
                    data.putObject("_sourceMetadata").put("draft", draft).put("status", content.getStatus().name());
                    values.add(source("MARKETING", revision.getId(), null, revision.getRevisionNumber(), null,
                        draft ? instant(content.getUpdatedAt()) : content.getFinalizedAt(), data));
                    ArrayNode assets = mapper.createArrayNode();
                    marketingAssets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId())
                        .forEach(asset -> assets.addObject().put("artifactRef", asset.getArtifactRef()));
                    if (!assets.isEmpty()) values.add(source("MARKETING_ASSETS", content.getId(), null,
                        revision.getRevisionNumber(), null, content.getFinalizedAt(), assets));
                });
            });
    }

    private void addMarketingStrategy(List<ReportSource> values, Long projectId) {
        String currentHash = strategySourceHash(values);
        marketingStrategies.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .filter(report -> currentHash.equals(report.getSourceManifestHash()))
            .ifPresent(report -> values.add(source("MARKETING_STRATEGY", report.getId(), null, null,
                composer.hash(json(report.getResultJson())), report.getGeneratedAt(), json(report.getResultJson()))));
    }

    private String strategySourceHash(List<ReportSource> values) {
        List<ReportSource> context = values.stream()
            .filter(item -> "PROJECT".equals(item.type()) || STRATEGY_CONTEXT_TYPES.contains(item.type()))
            .toList();
        return composer.hash(composer.manifest(context));
    }

    private void addLaunch(List<ReportSource> values, Long projectId, ModuleType type, String sourceType) {
        LaunchReadinessInputSnapshot input = launchInputs
            .findFirstByProjectIdAndModuleTypeAndCurrentTrueAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, type)
            .filter(value -> !value.isStale()).orElse(null);
        if (input == null) return;
        launchReports.findFirstByProjectIdAndModuleTypeAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(
                projectId, type, input.getId()).filter(report -> report.isCurrent() && !report.isStale())
            .ifPresent(report -> {
                ObjectNode data = mapper.createObjectNode(); data.set("analysis", json(report.getAnalysisJson()));
                data.set("quality", json(report.getQualityJson()));
                data.set("externalEvidence", json(report.getExternalEvidenceJson()));
                values.add(source(sourceType, report.getId(), null, input.getAttempt(), report.getResultHash(),
                    report.getCompletedAt(), data));
            });
    }

    private void addFinance(List<ReportSource> values, Long projectId, Binding binding) {
        var snapshot = financeSnapshots
            .findFirstByProjectIdAndSourceModeAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId, "USER_DOCUMENT_INPUT")
            .orElseGet(() -> financeSnapshots
                .findFirstByProjectIdAndSourceCurrentMarketSeedSnapshotIdAndSourceSelectionIdAndSourceSelectionRevisionAndSourceBmPlanRevisionAndDeletedAtIsNullOrderByFinalizedAtDesc(
                    projectId, binding.marketSeedSnapshotId(), binding.selectionId(), binding.selectionRevision(), binding.bmPlanRevision())
                .orElse(null));
        if (snapshot == null) return;
        String subjectId = "USER_DOCUMENT_INPUT".equals(snapshot.getSourceMode())
            ? "USER_DOCUMENT_INPUT" : snapshot.getId();
        taskRuns.findByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, "FINANCIAL_ANALYSIS_REPORT", subjectId).stream()
            .filter(run -> financeSnapshotMatches(run, snapshot.getId())).findFirst()
            .flatMap(run -> taskResults.findByTaskRunId(run.getId()).stream()
                .filter(result -> result.getValidationState() == TaskResultValidationState.ADOPTED).findFirst())
            .ifPresent(result -> {
                values.add(source("FINANCE", snapshot.getId(), null, null, snapshot.getSnapshotHash(),
                    snapshot.getFinalizedAt(), json(snapshot.getSnapshotJson())));
                values.add(source("FINANCE_REPORT", result.getId(), null, null, result.getResultHash(),
                    instant(result.getAdoptedAt()), json(result.getResultJson())));
            });
    }

    private boolean financeSnapshotMatches(TaskRun run, String snapshotId) {
        JsonNode input = json(run.getInputSnapshot());
        String referenced = input.path("snapshotId").asText();
        if (referenced.isBlank()) referenced = input.path("inputSnapshot").path("snapshotId").asText();
        return snapshotId.equals(referenced);
    }

    private java.util.Map<String, String> sourceStates(SourceSet current, Long projectId) {
        java.util.Map<String, String> states = new java.util.LinkedHashMap<>();
        for (String type : List.of("MARKET_INTERVIEW", "MARKETING_STRATEGY", "MARKETING",
                "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE")) states.put(type, "NOT_RUN");
        current.sources().forEach(source -> {
            if (!states.containsKey(source.type())) return;
            if ("MARKETING".equals(source.type()) && source.data().path("_sourceMetadata").path("draft").asBoolean())
                states.put(source.type(), "AVAILABLE_DRAFT");
            else if ("MARKETING".equals(source.type())) states.put(source.type(), "AVAILABLE_FINAL");
            else states.put(source.type(), "AVAILABLE");
        });
        if (!has(current.sources(), "MARKET_INTERVIEW")) marketInterviews
            .findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .ifPresent(version -> states.put("MARKET_INTERVIEW",
                switch (version.getSourceRun().getState()) {
                    case FAILED -> "FAILED"; case RUNNING, QUEUED -> "IN_PROGRESS";
                    default -> "CURRENT_RESULT_UNAVAILABLE";
                }));
        if (!has(current.sources(), "MARKETING")) marketingContents
            .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .ifPresent(content -> states.put("MARKETING", switch (content.getStatus()) {
                case FAILED -> "FAILED"; case QUEUED, RUNNING -> "IN_PROGRESS";
                case COMPLETED -> "CURRENT_RESULT_UNAVAILABLE"; default -> "CURRENT_RESULT_UNAVAILABLE";
            }));
        if (!has(current.sources(), "MARKETING_STRATEGY")) {
            var latestReport = marketingStrategies
                .findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
            var latestTask = taskRuns
                .findFirstByProjectIdAndTaskTypeAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                    projectId, TaskType.MARKETING_STRATEGY_GENERATION).orElse(null);
            if (latestTask != null && java.util.Set.of("QUEUED", "CLAIMED", "RUNNING", "READY")
                    .contains(latestTask.getState().name())) states.put("MARKETING_STRATEGY", "IN_PROGRESS");
            else if (latestTask != null && "FAILED".equals(latestTask.getState().name()))
                states.put("MARKETING_STRATEGY", latestReport == null ? "FAILED" : "UPDATE_REQUIRED");
            else if (latestReport != null) states.put("MARKETING_STRATEGY", "UPDATE_REQUIRED");
        }
        if (!has(current.sources(), "FINANCE") && financeSnapshots
            .findFirstByProjectIdAndDeletedAtIsNullOrderByFinalizedAtDesc(projectId).isPresent())
            states.put("FINANCE", "CURRENT_RESULT_UNAVAILABLE");
        return java.util.Map.copyOf(states);
    }

    private SourceSet sourceSet(List<ReportSource> sources, JsonNode bindingJson, List<String> blocking,
            List<String> omitted, Long ownerId, Long projectId) {
        ObjectNode manifest = composer.manifest(bindingJson, sources);
        ArrayNode omittedValues = manifest.putArray("omittedSources");
        omitted.forEach(omittedValues::add);
        Binding binding = bindingJson == null ? null : new Binding(bindingJson.path("marketSeedSnapshotId").asText(),
            bindingJson.path("selectionId").asLong(), bindingJson.path("selectionRevision").asInt(),
            bindingJson.path("bmPlanRevision").asInt());
        String bindingHash = bindingJson == null ? null : composer.hash(bindingJson);
        return new SourceSet(List.copyOf(sources), manifest, composer.hash(manifest), binding, bindingHash,
            readiness(moduleStatuses.findAll(ownerId, projectId)), List.copyOf(blocking), List.copyOf(omitted));
    }

    public record CurrentSourceCatalog(ArrayNode manifest, ObjectNode sources,
                                       java.util.Map<String, String> sourceStates,
                                       List<String> blockingSources, List<String> omittedSources,
                                       String hash, String strategySourceHash) {}

    private boolean exact(FinalReportSnapshot snapshot, SourceSet current) {
        if (!snapshot.hasExactLineage() || current.binding() == null) return false;
        if (!"final-business-proposal-result-v1".equals(json(snapshot.getReportJson()).path("contract").asText()))
            return false;
        JsonNode savedManifest = json(snapshot.getSourceManifestJson());
        List<String> selectedOptional = new ArrayList<>();
        savedManifest.path("sources").forEach(item -> {
            String type = item.path("type").asText();
            if (OPTIONAL.contains(type)) selectedOptional.add(type);
        });
        SourceSet comparable = selectSources(current, selectedOptional);
        Binding binding = current.binding();
        return snapshot.getSourceManifestHash().equals(comparable.hash())
            && snapshot.getSourceBindingHash().equals(current.bindingHash())
            && snapshot.getSourceMarketSeedSnapshotId().equals(binding.marketSeedSnapshotId())
            && snapshot.getSourceSelectionId().equals(binding.selectionId())
            && snapshot.getSourceSelectionRevision() == binding.selectionRevision()
            && snapshot.getSourceBmPlanRevision() == binding.bmPlanRevision();
    }

    private boolean bound(LaunchReadinessInputSnapshot value, Binding binding) {
        return Objects.equals(value.getSourceMarketSeedSnapshotId(), binding.marketSeedSnapshotId())
            && Objects.equals(value.getSourceSelectionId(), binding.selectionId())
            && Objects.equals(value.getSourceSelectionRevision(), binding.selectionRevision())
            && Objects.equals(value.getSourceBmPlanRevision(), binding.bmPlanRevision());
    }

    private ObjectNode bindingJson(Binding binding) {
        ObjectNode value = mapper.createObjectNode(); value.put("marketSeedSnapshotId", binding.marketSeedSnapshotId());
        value.put("selectionId", binding.selectionId()); value.put("selectionRevision", binding.selectionRevision());
        value.put("bmPlanRevision", binding.bmPlanRevision()); return value;
    }

    private String commandIdentity(Long projectId, SourceSet sources) {
        ObjectNode value = mapper.createObjectNode(); value.put("projectId", projectId);
        value.put("sourceBindingHash", sources.bindingHash()); value.put("sourceManifestHash", sources.hash());
        return composer.hash(value);
    }

    private void validateProposal(JsonNode result) {
        if (!result.isObject()
                || !"final-business-proposal-result-v1".equals(result.path("contract").asText())
                || !result.path("cover").isObject()
                || !result.path("executiveDecisionSummary").isObject()
                || !result.path("decisionRequest").isObject()
                || !result.path("appendix").isObject()
                || !result.path("sections").isArray()
                || result.path("sections").size() < 8
                || result.path("sections").size() > 10) {
            throw new IllegalArgumentException("FINAL_BUSINESS_PROPOSAL_RESULT_INVALID");
        }
    }

    private void canonicalizeEvidence(JsonNode result, JsonNode manifest) {
        canonicalizeEvidence(result, manifest, mapper.createArrayNode());
    }

    private void canonicalizeEvidence(JsonNode result, JsonNode manifest, JsonNode catalog) {
        java.util.Map<String, String> byType = new java.util.HashMap<>();
        java.util.Set<String> duplicateTypes = new java.util.HashSet<>();
        manifest.forEach(item -> {
            String type = item.path("type").asText();
            String reference = type + ":" + item.path("id").asText();
            if (byType.putIfAbsent(type, reference) != null) duplicateTypes.add(type);
        });
        duplicateTypes.forEach(byType::remove);
        java.util.Map<String, JsonNode> byKey = new java.util.LinkedHashMap<>();
        if (catalog != null && catalog.isArray()) catalog.forEach(item -> {
            String key = item.path("evidenceKey").asText();
            if (!key.isBlank()) byKey.put(key, item);
        });
        canonicalizeEvidenceNode(result, byType, byKey);
    }

    private void canonicalizeEvidenceNode(JsonNode node, java.util.Map<String, String> byType,
            java.util.Map<String, JsonNode> byKey) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.path("evidenceKeys").isArray()) {
                ArrayNode refs = mapper.createArrayNode();
                ArrayNode details = mapper.createArrayNode();
                java.util.Set<String> seenKeys = new java.util.LinkedHashSet<>();
                java.util.Set<String> seenRefs = new java.util.LinkedHashSet<>();
                object.path("evidenceKeys").forEach(keyNode -> {
                    String key = keyNode.asText();
                    JsonNode evidence = byKey.get(key);
                    if (evidence == null) throw new IllegalArgumentException("FINAL_REPORT_EVIDENCE_KEY_INVALID");
                    if (seenKeys.add(key)) details.add(evidence.deepCopy());
                    String reference = evidence.path("sourceType").asText() + ":" + evidence.path("sourceId").asText();
                    if (seenRefs.add(reference)) refs.add(reference);
                });
                object.set("evidenceRefs", refs);
                object.set("evidenceDetails", details);
            }
            if (object.path("evidenceSourceTypes").isArray()) {
                ArrayNode refs = object.path("evidenceRefs").isArray()
                    ? (ArrayNode) object.path("evidenceRefs") : mapper.createArrayNode();
                java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                refs.forEach(item -> seen.add(item.asText()));
                object.path("evidenceSourceTypes").forEach(typeNode -> {
                    String reference = byType.get(typeNode.asText());
                    if (reference == null) throw new IllegalArgumentException("FINAL_REPORT_EVIDENCE_INVALID");
                    if (seen.add(reference)) refs.add(reference);
                });
                object.set("evidenceRefs", refs);
            }
            java.util.List<JsonNode> children = new ArrayList<>();
            object.forEach(children::add);
            children.forEach(child -> canonicalizeEvidenceNode(child, byType, byKey));
        } else if (node.isArray()) {
            node.forEach(child -> canonicalizeEvidenceNode(child, byType, byKey));
        }
    }

    private Source currentSource(Long projectId) {
        try { return currentConcepts.currentOrNull(projectId); }
        catch (BusinessException unavailable) { return null; }
    }

    private List<ReadinessItem> readiness(List<ProjectModuleStatusResponse> statuses) {
        return statuses.stream().map(value -> new ReadinessItem(value.module().name(), value.module().name(),
            value.status() == null ? "NOT_STARTED" : value.status().name())).toList();
    }

    private boolean has(List<ReportSource> sources, String type) {
        return sources.stream().anyMatch(value -> value.type().equals(type));
    }

    private ReportSource source(String type, String id, Integer version, Integer revision,
            String hash, Instant generatedAt, JsonNode data) {
        return new ReportSource(type, id, version, revision, hash == null ? composer.hash(data) : hash, generatedAt, data);
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private String requiredKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    private JsonNode json(String value) {
        try { return value == null || value.isBlank() ? mapper.nullNode() : mapper.readTree(value); }
        catch (Exception ignored) { return mapper.getNodeFactory().textNode(value); }
    }

    /** 봉투에서 덜어낼 «근거 원본 뭉치». 값이 아니라 <b>같은 값의 사본</b>이라서 뺀다. */
    private static final List<String> BULK_SOURCE_KEYS = List.of("evidence", "upstreamReferences");
    /** 상한(2 MB)에 닿기 전에 조각별 크기를 남겨 둘 기준선. */
    private static final int PROPOSAL_INPUT_WARN_BYTES = 1_500_000;

    /**
     * 봉투의 {@code sources} 에서 <b>근거 원본 배열</b>을 덜어낸다.
     *
     * <p><b>왜.</b> 실측(2026-08-19 · 프로젝트 2)에서 같은 {@code evidence} 832 kB 가
     * <b>네 벌</b> 실렸다 — MARKET · BUSINESS_MODEL, 그리고 FINANCE 의
     * {@code upstreamReferences} 안에 {@code marketAnalysis.evidence} 와
     * {@code businessModel.result} 로 두 벌 더. 합계 <b>4,970 kB</b> 로
     * {@code InternalAiExecutionClient.MAX_JSON_BYTES}(2 MiB)와 {@code TaskRunService:300}
     * (입력 스냅샷 2 MiB)을 둘 다 넘어 <b>모듈 6 이 시작조차 못 한다.</b>
     * 덜어내면 586 kB — 여유 1,462 kB.
     *
     * <p><b>근거를 잃지 않는다.</b> 목록({@code evidenceCatalog})은 이 손질 «전» 원본인
     * {@code current.sources()} 로 만든다. 실측 563 건이 그대로 실린다. 프롬프트도 인용을
     * {@code allowedEvidenceKeys} 에서만 가져오게 못박고 있어({@code prompts.py} 13·22행)
     * 원본 배열을 직접 읽지 않는다.
     *
     * <p><b>지운 자리에 지웠다는 사실을 남긴다.</b> 조용히 사라지면 나중에 「원래 없었다」와
     * 구분되지 않는다 — 그 구분이 사라지면 보고서에 근거가 왜 적은지 아무도 못 짚는다.
     *
     * <p>⚠ 남기는 것은 <b>세지 않아도 아는 것</b>뿐이다. 지운 뭉치의 바이트 수를 적으려면
     * 그 1.7 MB 를 <b>한 번 더 직렬화</b>해야 한다 — 덜어내려고 부른 함수가 덜어낼 것을
     * 통째로 다시 만드는 셈이다. {@code size()} 와 필드 이름은 공짜다.
     */
    private JsonNode compactSource(JsonNode data) {
        if (data == null || !data.isObject()) return data == null ? null : data.deepCopy();
        ObjectNode value = (ObjectNode) data.deepCopy();
        for (String key : BULK_SOURCE_KEYS) {
            JsonNode dropped = value.remove(key);
            if (dropped == null || dropped.isNull()) continue;
            ObjectNode note = value.putObject(key + "Omitted");
            note.put("reason", "근거는 evidenceCatalog 로 싣는다. 원본 뭉치는 봉투에서 뺀다.");
            if (dropped.isArray()) note.put("count", dropped.size());
            else if (dropped.isObject()) {
                ArrayNode keys = note.putArray("keys");
                dropped.propertyNames().forEach(keys::add);
            }
        }
        return value;
    }

    private String write(JsonNode value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("최종 보고서 JSON을 저장할 수 없습니다.", error); }
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private Instant instant(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }

    private record SourceSet(List<ReportSource> sources, ObjectNode manifest, String hash,
        Binding binding, String bindingHash, List<ReadinessItem> readiness,
        List<String> blocking, List<String> omitted) {
        boolean ready() { return blocking.isEmpty() && binding != null; }
    }
}
