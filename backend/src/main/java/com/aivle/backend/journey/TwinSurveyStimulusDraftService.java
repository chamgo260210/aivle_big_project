package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.contract.TwinStimulusDraftContract;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 자극 초안 — 컨셉에서 「비교할 두 안」을 뽑아 준다.
 *
 * <p><b>패턴 A(동기 인라인)다.</b> 조사 본체와 달리 프롬프트 1회라 폴러를 둘 이유가 없고,
 * 화면은 버튼을 누른 자리에서 카드를 받아야 한다. 그래서 워커가 하던 일을 요청 스레드가
 * 그대로 한다: {@code create} → {@code claim} → {@code startExecution} →
 * {@code execute} → {@code adopt}.
 *
 * <p>⚠ <b>이 메서드에 {@code @Transactional} 을 붙이면 안 된다.</b> AI 호출은 DB 트랜잭션
 * 밖이어야 하고, 붙이면 커넥션을 수십 초 물고 있게 된다. 상태 전이는 각각
 * {@link TaskRunService} 의 트랜잭션이 맡는다. 방어는 {@link TwinSurveyWorker} 와 같다.
 */
@Service
public class TwinSurveyStimulusDraftService {

    private static final Logger log = LoggerFactory.getLogger(TwinSurveyStimulusDraftService.class);
    private static final String SCHEMA_VERSION = "1.0";
    /** 프롬프트 1회다. 제공자 타임아웃(기본 60초)보다 넉넉하되 사람이 기다릴 수 있는 범위다. */
    private static final Duration BUDGET = Duration.ofSeconds(90);
    private static final Duration LEASE = BUDGET.plusMinutes(2);

    /**
     * 확정 가격은 «월 9,900원» 같은 자유문장이라 그대로는 못 쓴다. <b>깨끗하게 읽히는 것만</b>
     * 원 단위 정수로 넘기고 나머지는 null 이다 — 「3만원」을 3으로 읽는 편보다 안 읽는 편이 낫다.
     */
    private static final Pattern PLAIN_KRW = Pattern.compile("(\\d[\\d,]*)\\s*원");
    private static final Pattern SCALED_UNIT = Pattern.compile("\\d\\s*[만억조]");
    private static final long PRICE_MAX = 100_000_000L;

    private final ProjectRepository projects;
    private final com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup seeds;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher hasher;
    private final InternalAiExecutionClient client;
    private final ObjectMapper mapper;

    public TwinSurveyStimulusDraftService(ProjectRepository projects,
                                          com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup seeds,
                                          TaskRunService taskRuns,
                                          CanonicalInputHasher hasher, InternalAiExecutionClient client,
                                          ObjectMapper mapper) {
        this.projects = projects; this.seeds = seeds;
        this.taskRuns = taskRuns; this.hasher = hasher; this.client = client; this.mapper = mapper;
    }

    public JsonNode draft(Long ownerId, Long projectId) {
        Project project = owned(ownerId, projectId);
        String input = mapper.writeValueAsString(material(projectId));
        String inputHash = hasher.hash(TaskType.TWIN_STIMULUS_DRAFT, SCHEMA_VERSION, "ko-KR", input);
        // 「다시 뽑기」를 누를 수 있어야 한다 — 같은 컨셉이면 해시가 같아 nonce 없이는
        // 중복 방지에 걸린다. 조사·시장조사와 같은 이유다.
        String nonce = UUID.randomUUID().toString();
        TaskRun run = taskRuns.create(ownerId, project.getId(), TaskType.TWIN_STIMULUS_DRAFT,
            "TWIN_STIMULUS_DRAFT", String.valueOf(project.getId()), input, inputHash, nonce, nonce, 1);

        TaskRunService.Claim claim = taskRuns.claim(run.getId(), "twin-stimulus-draft", LEASE, BUDGET);
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI call must run outside a DB transaction");
        try {
            ExecutionResponse response = client.execute(taskRuns.getOwnedForWorker(claim.taskRunId()),
                claim.taskAttemptId(), LocalDateTime.now().plus(BUDGET));
            try {
                // 이 검증이 없으면 결과가 조용히 폐기된다 — 워커가 있든 없든 규율은 같다.
                TwinStimulusDraftContract.validate(response.result());
            } catch (ExecutionFailure invalid) {
                taskRuns.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                    mapper.writeValueAsString(response.result()),
                    response.resultSchemaVersion() == null ? SCHEMA_VERSION : response.resultSchemaVersion(),
                    invalid.reason());
                throw new BusinessException(ErrorCode.AI_RESULT_INVALID,
                    "자극 초안이 계약을 어겼다 — 다시 시도하라");
            }
            taskRuns.adopt(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(response.result()), response.canonicalInputHash(),
                response.resultSchemaVersion());
            return response.result();
        } catch (ExecutionFailure failure) {
            taskRuns.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
            // 「팔 수 있는 쌍이 하나도 안 나왔다」는 장애가 아니라 답이다. 화면이 그 답을
            // 「차별점을 하나 이상 확정하라」로 옮길 수 있게 코드를 그대로 올려 보낸다.
            if ("TWIN_STIMULUS_NO_SERVICEABLE_PAIR".equals(failure.reason())) {
                throw new BusinessException(ErrorCode.TWIN_STIMULUS_NO_SERVICEABLE_PAIR);
            }
            log.warn("Twin stimulus draft failed projectId={} taskRunId={} code={} reason={}",
                projectId, claim.taskRunId(), failure.code(), failure.reason());
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE, "자극 초안을 만들지 못했다");
        }
    }

    /**
     * 마켓 시드 스냅샷에서 <b>필요한 칸만</b> 꺼낸다. 스냅샷 전체를 넘기지 않는 이유는
     * 법률 근거·평가 원문까지 프롬프트에 실리기 때문이다 — 초안에 쓰이지 않는다.
     */
    private ObjectNode material(Long projectId) {
        // ⚠ **여기가 병합이 남긴 구멍이었다.** 예전에는 레거시 선택 경로만 봐서
        //    사업안(포트폴리오)으로 확정한 프로젝트의 시드를 **못 봤고**, 조용히 견본
        //    이름표로 떨어졌다 — 예외도 로그도 없었다. 조회는 이제 한 곳에만 있다.
        // ⚠ **견본 이름표 갈래를 없앴다**(2026-08-12). 예전에는 시드가 없으면 화면이 보낸
        //    견본 이름표를 그대로 넘겼는데, 그 갈래가 시장조사에서 실제로 사고를 냈다 —
        //    사업안을 선택했지만 확정 전이라 시드가 없었고, 미용실 노쇼 견본 원장이
        //    냉동 간편식 사업안의 결과로 나왔다(6/6 · SUCCEEDED). 여기도 같은 모양이었다.
        //    조용한 기본값을 만들지 않는다 — 확정 전이면 실패시킨다.
        MarketAnalysisSeedSnapshot snapshot = seeds.current(projectId).orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "확정된 사업안이 없다 — 사업안을 선택하고 확정한 뒤에 자극 초안을 만들어야 한다"));
        JsonNode seed = mapper.readTree(snapshot.getSnapshotJson());
        JsonNode concept = seed.path("selectedConcept");
        JsonNode hypotheses = seed.path("finalHypotheses");

        ObjectNode material = mapper.createObjectNode();
        material.put("conceptName", concept.path("identity").path("conceptName").asText(""));
        material.put("targetUsers", concept.path("identity").path("targetUsers").asText(""));
        material.put("problemScenario", concept.path("solution").path("problemScenario").asText(""));
        ArrayNode features = material.putArray("featureSet");
        for (JsonNode feature : concept.path("solution").path("featureSet")) {
            if (feature.isTextual() && !feature.asText().isBlank()) features.add(feature.asText());
        }
        material.put("differentiators", hypotheses.path("differentiators").path("value").asText(""));
        Long price = priceKrw(hypotheses.path("price").path("value").asText(""));
        if (price == null) material.putNull("priceKrw"); else material.put("priceKrw", price);
        if (material.path("conceptName").asText().isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "컨셉 스냅샷에 이름이 없다 — 초안을 만들 재료가 아니다");
        }
        return material;
    }

    /** @return 원 단위 정수, 또는 확실히 못 읽으면 null. */
    public static Long priceKrw(String text) {
        if (text == null || SCALED_UNIT.matcher(text).find()) return null;
        Matcher found = PLAIN_KRW.matcher(text);
        if (!found.find()) return null;
        try {
            long value = Long.parseLong(found.group(1).replace(",", ""));
            return value < 0 || value > PRICE_MAX ? null : value;
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    private Project owned(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
    }
}
