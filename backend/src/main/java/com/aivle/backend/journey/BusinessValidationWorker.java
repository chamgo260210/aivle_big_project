package com.aivle.backend.journey;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 사업 검증 큐 폴러 겸 실행기. {@link MarketResearchWorker} 를 베꼈다.
 *
 * <p><b>이 빈이 없으면 TaskRun 이 영원히 QUEUED 로 남는다.</b> 공용 워커는 없고,
 * 새 TaskType 은 자기 폴러를 만들어야 한다.
 *
 * <p>결과 검증도 <b>여기 안에</b> 있다. 빠뜨리면 AI 호출은 성공하고 결과만 조용히 버려진다.
 * 검증기는 시장조사 것을 그대로 쓴다 — 봉투가 같기 때문이다({@code mode} 만 {@code VALIDATION}).
 */
@Component
public class BusinessValidationWorker {
    /**
     * <b>한 실행에 두 걸음이 들어간다.</b> 실측: 시장조사 FULL 약 <b>23분</b>
     * (run 15: 07:36:53→07:59:42) + BM 18~39초.
     *
     * <p>그래서 예산이 시장조사 워커(20분)보다 길다. 짧게 두면 뒤 걸음이 시작조차 못 하고,
     * 그때 잃는 것은 <b>이미 지불한 수집 비용</b>이다.
     *
     * <p>lease 는 예산보다 넉넉해야 한다 — 같거나 짧으면 정상 실행이 만료로 회수돼
     * 중복 실행된다. 여기서 중복은 23분짜리를 한 번 더 태우는 것이다.
     *
     * <p>★ <b>판 ㊺ — 33분 → 60분.</b> 위의 23분은 <b>예산 270 · {@code gpt-4o-mini}</b> 로
     * 잰 값이고, 이 판에서 두 가지가 동시에 커졌다:
     *
     * <ul>
     *   <li>호출 수 <b>266 → 470</b>(재질문이 문서의 46%에만 닿던 것을 전량으로) — <b>≈1.8배</b></li>
     *   <li>발췌 모델이 <b>추론 모델</b>({@code gpt-5.6-luna})이 됐다 — 호출당 시간도 는다</li>
     * </ul>
     *
     * <p>⚠ <b>넘기면 그냥 실패가 아니다.</b> {@code REQUEST_DEADLINE_EXCEEDED} 는 retryable 이라
     * <b>같은 것을 한 번 더 태운다</b> — 이미 지불한 수집 비용을 잃고 그만큼을 또 쓴다.
     * 그래서 <b>넉넉한 쪽으로 틀린다.</b> 예산은 상한이지 지출이 아니라, 빨리 끝나면 빨리 끝난다.
     *
     * <p>⚠ 60분은 <b>산수지 실측이 아니다</b>(23분 × 1.8 ≈ 41분 + 추론 모델 여유).
     * 첫 유료 재실행에서 <b>실제 벽시계를 재고 이 숫자를 고친다.</b>
     */
    private static final Duration BUDGET = Duration.ofMinutes(60);
    private static final Duration LEASE = BUDGET.plusMinutes(3);

    private static final Set<String> FORBIDDEN_FIELDS = Set.of("storageUrl", "objectKey", "presignedUrl",
        "localPath", "fileBytes", "base64", "prompt", "rawProviderResponse", "credential");

    private final TaskRunService service;
    private final InternalAiExecutionClient client;
    private final ObjectMapper mapper;

    public BusinessValidationWorker(TaskRunService service, InternalAiExecutionClient client, ObjectMapper mapper) {
        this.service = service;
        this.client = client;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${app.task-run.business-validation-poll-interval-ms:2000}")
    public void poll() {
        processOne();
    }

    public boolean processOne() {
        TaskRunService.Claim claim = service.claimNext(
            TaskType.BUSINESS_VALIDATION, "business-validation-worker", LEASE, BUDGET);
        if (claim == null) return false;
        service.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI call must run outside a DB transaction");
        try {
            TaskRun run = service.getOwnedForWorker(claim.taskRunId());
            ExecutionResponse response = client.execute(run, claim.taskAttemptId(), LocalDateTime.now().plus(BUDGET));
            try {
                // 이 검증이 없으면 결과가 조용히 폐기된다 — 컴파일도 테스트도 안 깨지고 AI 비용만 쓴다.
                MarketResearchContract.validate(response.result());
                rejectForbiddenFields(response.result());
            } catch (ExecutionFailure invalidResult) {
                String safePayload = response.result() == null ? "{}" : mapper.writeValueAsString(response.result());
                service.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), safePayload,
                    response.resultSchemaVersion() == null ? "1.0" : response.resultSchemaVersion(),
                    invalidResult.reason());
                return true;
            }
            service.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(),
                mapper.writeValueAsString(response.result()), response.canonicalInputHash(), response.resultSchemaVersion());
        } catch (ExecutionFailure failure) {
            if ("RESULT_SCHEMA_INVALID".equals(failure.code()))
                service.rejectAndFail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(), "{}", "1.0", failure.reason());
            else service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                failure.code(), failure.reason(), failure.retryable());
        } catch (TaskRunFailure failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "RESULT_SCHEMA_INVALID", failure.getReason(), false);
        } catch (RuntimeException failure) {
            service.fail(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken(),
                "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", true);
        }
        return true;
    }

    private void rejectForbiddenFields(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (FORBIDDEN_FIELDS.contains(name))
                    throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_UNKNOWN_FIELD", false);
                rejectForbiddenFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::rejectForbiddenFields);
        }
    }
}
