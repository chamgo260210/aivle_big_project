package com.aivle.backend.pipeline.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * <b>시장조사가 끝나면 컨셉 다듬기가 시작된다.</b>
 *
 * <p>2026-08-16 실측 결함. {@code ConceptRefinementService.startFirstRound} 를 부르는
 * 프로덕션 코드가 <b>한 곳도 없었다</b>(테스트뿐). 라운드 2 이상을 미는
 * {@code ConceptRefinementWorker} 는 <b>이미 있는 라운드</b>만 보므로, 첫 문이 없으면
 * 다듬기는 영원히 「아직 안 함」이다.
 *
 * <p>같은 워커가 FULL 과 BM 을 <b>둘 다</b> 돌린다. 가르는 것은 {@code subjectType} 하나뿐이라
 * 그 한 줄을 여기서 못 박는다.
 */
class MarketResearchRefinementTriggerTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskRunService service = mock(TaskRunService.class);
    private final InternalAiExecutionClient client = mock(InternalAiExecutionClient.class);
    private final MarketResearchService completion = mock(MarketResearchService.class);
    private final ConceptRefinementService refinement = mock(ConceptRefinementService.class);
    private final JobEventPublisher events = mock(JobEventPublisher.class);
    private final MarketResearchWorker worker = new MarketResearchWorker(
        service, client, completion, refinement, events, MAPPER);

    @Test
    void BM_이_채택되면_다듬기_첫_라운드를_건다() throws Exception {
        // ★ FULL 이 아니라 BM 이다. 다듬기 재료(캔버스·게이트 사유)는 BM 실행에서만 생기고,
        //   재료를 고르는 latestValidationVersion 은 FULL 판을 쳐다보지도 않는다.
        given("MARKET_RESEARCH_BM");

        assertThat(worker.processOne()).isTrue();

        verify(refinement).startFirstRoundAfterResearch(42L);
    }

    @Test
    void FULL_실행은_다듬기를_걸지_않는다() throws Exception {
        // FULL 과 BM 이 같은 워커를 쓴다. FULL 에서도 걸면 한 주기에 유료 다듬기가 두 번 선다 —
        // 게다가 그 한 번은 재료가 빈 채로 돈다.
        given("MARKET_RESEARCH_FULL");

        assertThat(worker.processOne()).isTrue();

        verify(refinement, never()).startFirstRoundAfterResearch(anyLong());
    }

    @Test
    void 다듬기_시작이_터져도_시장조사_채택은_성공으로_남는다() throws Exception {
        // 이미 지불한 수집을 잃으면 안 된다. 사유는 로그로만 나간다.
        given("MARKET_RESEARCH_BM");
        when(refinement.startFirstRoundAfterResearch(42L))
            .thenThrow(new IllegalStateException("refinement input contract broke"));

        assertThatCode(worker::processOne).doesNotThrowAnyException();

        verify(completion).complete(any(), any());
        verify(service, never()).fail(anyString(), anyString(), anyString(), anyString(), anyString(),
            org.mockito.ArgumentMatchers.anyBoolean());
        verify(completion, never()).materializeFailure(anyString(), anyString());
    }

    private void given(String subjectType) throws Exception {
        TaskRunService.Claim claim = new TaskRunService.Claim("task-1", "attempt-1", "token-1");
        when(service.claimNext(eq(TaskType.MARKET_RESEARCH), anyString(), any(), any())).thenReturn(claim);
        when(service.workerContext("task-1")).thenReturn(new TaskRunWorkerContext(
            "task-1", 42L, 7L, TaskType.MARKET_RESEARCH, subjectType, "concept-1",
            "{}", "hash", "idem", "corr", "v1", "1.0", "ko-KR", 1, 1));
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn("task-1");
        when(service.getOwnedForWorker("task-1")).thenReturn(run);
        when(client.execute(eq(run), eq("attempt-1"), any(LocalDateTime.class)))
            .thenReturn(new ExecutionResponse("v1", "MARKET_RESEARCH", "1.0", "task-1", "attempt-1",
                "corr", "hash", "1.0", fullResult(), null, null, null));
        when(refinement.startFirstRoundAfterResearch(anyLong())).thenReturn(Optional.empty());
    }

    /** 3층 공용 골든 픽스처. 워커 안의 계약 검증을 실제로 지나게 하려면 이것이어야 한다. */
    private static ObjectNode fullResult() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && root != null; depth++, root = root.getParent()) {
            Path candidate = root.resolve("ai/tests/fixtures/market_research/full.json");
            if (Files.exists(candidate)) {
                ObjectNode node = (ObjectNode) MAPPER.readTree(Files.readString(candidate));
                node.propertyNames().stream().filter(key -> key.startsWith("_")).toList().forEach(node::remove);
                return node;
            }
        }
        throw new IllegalStateException("Fixture not found: market_research/full.json");
    }
}
