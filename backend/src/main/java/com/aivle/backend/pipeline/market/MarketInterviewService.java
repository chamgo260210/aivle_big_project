package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장 인터뷰. <b>패턴 B</b> — 큐에 넣고 {@link MarketInterviewWorker} 가 돌린다.
 *
 * <p>{@link TwinSurveyService} 와 같은 구조다: {@code start} 는 TaskRun 만 만들고 상태
 * 전이는 {@code current()} 가 불릴 때 {@link #synchronize} 가 한다.
 * <b>지연 반영이라 화면이 폴링해야 전이한다.</b>
 */
@Service
public class MarketInterviewService {

    private static final Logger log = LoggerFactory.getLogger(MarketInterviewService.class);
    private static final String SCHEMA_VERSION = "1.0";
    /** 정성 조사의 표준 표본에 맞춘 세 값. DB CHECK·결과 계약과 셋이 맞물려 있다. */
    private static final Set<Integer> SAMPLE_SIZES = Set.of(20, 40, 80);

    private final ProjectRepository projects;
    private final MarketInterviewRunRepository runs;
    private final MarketInterviewVersionRepository versions;
    private final TaskResultRepository taskResults;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final MarketInterviewInputFactory inputs;
    private final ObjectMapper mapper;

    public MarketInterviewService(ProjectRepository projects, MarketInterviewRunRepository runs,
                                  MarketInterviewVersionRepository versions,
                                  TaskResultRepository taskResults, TaskRunService taskRuns,
                                  CanonicalInputHasher hasher, MarketInterviewInputFactory inputs,
                                  ObjectMapper mapper) {
        this.projects = projects; this.runs = runs; this.versions = versions;
        this.taskResults = taskResults; this.taskRuns = taskRuns; this.hasher = hasher;
        this.inputs = inputs; this.mapper = mapper;
    }

    @Transactional
    public RunView start(Long ownerId, Long projectId, JsonNode conceptBoard, int sampleSize) {
        Project project = owned(ownerId, projectId);
        if (!SAMPLE_SIZES.contains(sampleSize)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "표본 크기는 20·40·80 중 하나다");
        }
        String input = inputs.build(conceptBoard, sampleSize);
        String inputHash = hasher.hash(TaskType.MARKET_INTERVIEW, SCHEMA_VERSION, "ko-KR", input);
        // 「누를 때마다 새로 실행」이라 같은 컨셉보드면 canonicalInputHash 가 같다 — nonce 가
        // 없으면 중복 방지(SAME_INPUT_ACTIVE)에 걸려 두 번째 실행이 만들어지지 않는다.
        String nonce = UUID.randomUUID().toString();
        // ⚠ subjectId 는 **NOT NULL 이다**. 컴파일도 단위 테스트도 이것을 못 잡는다 —
        //   트윈 조사 때 실스택 스모크가 첫 POST 에서 500 으로 잡아냈다.
        TaskRun task = taskRuns.create(ownerId, project.getId(), TaskType.MARKET_INTERVIEW,
            "MARKET_INTERVIEW", String.valueOf(project.getId()), input, inputHash, nonce, nonce, 1);
        return runView(runs.save(MarketInterviewRun.create(project, task, inputHash, sampleSize)));
    }

    /** 화면이 폴링하는 자리. <b>여기서 상태가 전이한다.</b> */
    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        owned(ownerId, projectId);
        MarketInterviewRun run = runs
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (run == null) return new CurrentView(null, null);
        synchronize(run);
        MarketInterviewVersion version = versions
            .findBySourceRunIdAndDeletedAtIsNull(run.getId()).orElse(null);
        return new CurrentView(runView(run), version == null ? null : versionView(version));
    }

    private void synchronize(MarketInterviewRun run) {
        TaskRun task = run.getTaskRun();
        TaskRunState state = task.getState();
        if (state == TaskRunState.QUEUED || state == TaskRunState.READY) return;
        if (state == TaskRunState.RUNNING) { run.running(); runs.save(run); return; }
        if (state == TaskRunState.FAILED || state == TaskRunState.TIMED_OUT
            || state == TaskRunState.CANCELLED) {
            if (run.getState() != MarketInterviewRun.State.FAILED) {
                log.warn("Market interview task failed projectId={} runId={} taskRunId={} errorCode={} retryable={}",
                    run.getProject().getId(), run.getId(), task.getId(),
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
    }

    private void materialize(MarketInterviewRun run, JsonNode result) {
        JsonNode comprehension = result.path("comprehension");
        int answered = result.path("telemetry").path("answered").asInt();
        int misunderstood = comprehension.path("misunderstood").asInt();
        int number = Math.toIntExact(
            versions.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1);
        versions.save(MarketInterviewVersion.of(run.getProject(), run, number, result.toString(),
            new MarketInterviewVersion.Summary(result.path("sampleSize").asInt(), answered,
                result.path("themes").size(), misunderstood, result.path("caveats").size())));

        // 오해가 절반을 넘으면 컨셉이 아니라 컨셉보드를 고쳐야 한다. 실패가 아니므로 막지
        // 않지만, 조용히 지나가지도 않는다 — 이 실행의 「끌리는 점」은 읽을 값이 아니다.
        if (answered > 0 && misunderstood * 2 > answered) {
            log.info("Market interview mostly misunderstood projectId={} runId={} misunderstood={}/{}",
                run.getProject().getId(), run.getId(), misunderstood, answered);
        }
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
    }

    private RunView runView(MarketInterviewRun run) {
        return new RunView(run.getId(), run.getState().name(), run.getSampleSize(),
            run.getTaskRun().getId(), run.getTaskRun().getState().name(),
            run.getErrorCode(), run.getTaskRun().isRetryable());
    }

    private VersionView versionView(MarketInterviewVersion version) {
        return new VersionView(version.getId(), version.getVersionNumber(),
            mapper.readTree(version.getResultJson()), version.getSampleSize(),
            version.getAnsweredCount(), version.getThemeCount(),
            version.getMisunderstoodCount(), version.getCaveatCount());
    }

    public record RunView(Long id, String state, Integer sampleSize, String taskRunId, String taskState,
                          String errorCode, boolean retryable) { }

    /** {@code result} 는 계약 그대로다 — 백엔드가 다시 가공하지 않는다. */
    public record VersionView(Long id, Integer versionNumber, JsonNode result, Integer sampleSize,
                              Integer answeredCount, Integer themeCount,
                              Integer misunderstoodCount, Integer caveatCount) { }

    public record CurrentView(RunView run, VersionView version) { }
}
