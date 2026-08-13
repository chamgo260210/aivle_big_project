package com.aivle.backend.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장조사가 <b>무엇을 태우는가</b> — 사업안인가 견본인가.
 *
 * <p>이 갈래가 이 작업의 전부다. 예전에는 화면이 컨셉 자리에 {@code null} 을 보내고
 * 이름표가 견본을 가리켜, 사용자가 사업안을 확정해도 <b>도는 것은 미용실 견본</b>이었다.
 *
 * <p>Spring 없이 잰다 — 갈래는 서비스 안에 있고 컨텍스트를 띄울 이유가 없다
 * ({@code BmPlanPreparationServiceTests} 와 같은 관례).
 */
class MarketResearchSeedBranchTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONCEPT_ID = "7b1f0c2e-9a44-4d31-8f0e-2c5b6d7a1e90";

    private final MarketResearchInputFactory inputs = mock(MarketResearchInputFactory.class);
    private final MarketAnalysisSeedLookup seeds = mock(MarketAnalysisSeedLookup.class);
    private final BmPlanPreparationService bmPlans = mock(BmPlanPreparationService.class);
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final MarketResearchRunRepository runs = mock(MarketResearchRunRepository.class);
    private final ResearchCompetitorSeedService competitorSeeds =
        mock(ResearchCompetitorSeedService.class);

    private final MarketResearchService service = new MarketResearchService(
        projects(), runs, mock(MarketResearchVersionRepository.class),
        mock(TaskResultRepository.class), taskRuns, hasher(), inputs, bmPlans, seeds,
        new ResearchConceptFactory(MAPPER), competitorSeeds,
        mock(com.aivle.backend.pipeline.refinement.ConceptRefinementService.class), MAPPER);

    private static final String SNAPSHOT = """
        {"contract":"market-analysis-seed-snapshot-v1","schemaVersion":"2.0",
         "conceptId":"C1",
         "selectedConcept":{
           "identity":{"conceptName":"소상공인 매장 운영 SaaS","coreValue":"한 화면에서 끝낸다",
                       "targetUsers":"외식업 사업주","industryCategory":"매장 운영 소프트웨어"},
           "solution":{"problemScenario":"도구가 흩어져 있다","solutionMechanism":"POS 를 잇는다",
                       "featureSet":["주문 연동"]},
           "operation":{"operatingModel":"월 구독 SaaS","transactionFlow":["구독한다"],
                        "platformRole":"통합 처리","partnerModel":"POS 제휴",
                        "partnerRequirements":["규격 공개"]}},
         "finalHypotheses":{
           "targetRegion":{"value":"대한민국"},"revenueModel":{"value":"월 정액 구독"},
           "price":{"value":"월 49,000원"},"channels":{"value":"제휴 영업"},
           "differentiators":{"value":"마감이 빠르다"},
           "preMarketSomShare":{"value":{"targetSharePercent":0.5,"horizonYears":3,
                                         "assumptions":["수도권 중심"]}}}}""";

    private ProjectRepository projects() {
        ProjectRepository repository = mock(ProjectRepository.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(7L);
        when(repository.findByIdAndOwnerIdAndDeletedAtIsNull(anyLong(), anyLong()))
            .thenReturn(Optional.of(project));
        return repository;
    }

    private CanonicalInputHasher hasher() {
        CanonicalInputHasher value = mock(CanonicalInputHasher.class);
        when(value.hash(any(), anyString(), anyString(), anyString())).thenReturn("sha256:0");
        return value;
    }

    /** {@code start()} 가 끝까지 가도록만 채운다 — 재는 것은 그 앞의 갈래다. */
    private void wireTaskRun() {
        TaskRun task = mock(TaskRun.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getState()).thenReturn(TaskRunState.QUEUED);
        when(taskRuns.create(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(task);
        MarketResearchRun run = mock(MarketResearchRun.class);
        when(run.getId()).thenReturn(11L);
        when(run.getKind()).thenReturn(MarketResearchRun.Kind.FULL);
        when(run.getState()).thenReturn(MarketResearchRun.State.QUEUED);
        when(run.getTaskRun()).thenReturn(task);
        when(runs.save(any())).thenReturn(run);
        when(inputs.full(any(), anyString(), anyString())).thenReturn("{}");
    }

    private MarketAnalysisSeedSnapshot snapshot() {
        MarketAnalysisSeedSnapshot seed = mock(MarketAnalysisSeedSnapshot.class);
        when(seed.getSnapshotJson()).thenReturn(SNAPSHOT);
        return seed;
    }

    @Test
    @DisplayName("사업안이 확정돼 있으면 그 사업안을 태운다 — 화면이 보낸 견본 이름표는 진다")
    void aConfirmedBusinessPlanWins() {
        wireTaskRun();
        MarketAnalysisSeedSnapshot seed = snapshot();
        when(seeds.current(7L)).thenReturn(Optional.of(seed));
        when(seeds.conceptIdOf(seed)).thenReturn(CONCEPT_ID);
        when(bmPlans.current(7L)).thenReturn(new BmPlanPreparationService.PlanView(
            MAPPER.createObjectNode(),
            (tools.jackson.databind.node.ObjectNode) MAPPER.readTree(
                "{\"budget_krw\":50000000,\"months\":10,\"team\":2}"), 1));

        // 화면은 여전히 견본 이름표와 null 컨셉을 보낸다 — 그것이 지금의 화면이다.
        service.startFull(1L, 7L, MAPPER.nullNode(), "beauty-noshow", "2026-08-11");

        ArgumentCaptor<JsonNode> concept = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(inputs)
            .full(concept.capture(), label.capture(), anyString());

        // 이름표는 **원장 이름**이 된다. 스냅샷 안의 "C1"(AI 후보 id)이 아니라 DB 식별자다.
        assertThat(label.getValue()).isEqualTo(CONCEPT_ID);
        assertThat(concept.getValue().get("concept_id").stringValue()).isEqualTo(CONCEPT_ID);
        assertThat(concept.getValue().get("name").stringValue()).isEqualTo("소상공인 매장 운영 SaaS");
        // 컨셉↔원장 짝의 뿌리 — 둘이 같아야 AI 쪽 `_assert_same_concept` 가 선다.
        assertThat(concept.getValue().get("concept_id").stringValue()).isEqualTo(label.getValue());
        // 비용 세 칸은 BM 앞 화면이 채운 것이 실린다.
        assertThat(concept.getValue().at("/constraint/budget_krw").longValue()).isEqualTo(50_000_000L);
        // 씨앗을 안 적었으면 **칸 자체가 없다** — 빈 블록은 하네스에 corp_name 을 요구시킨다.
        assertThat(concept.getValue().has("_경쟁_씨앗")).isFalse();
    }

    @Test
    @DisplayName("경쟁 씨앗을 적었으면 컨셉에 실린다 — 하네스가 F_COMP subject 를 여기서 가져온다")
    void competitorSeedsRideAlong() {
        wireTaskRun();
        MarketAnalysisSeedSnapshot seed = snapshot();
        when(seeds.current(7L)).thenReturn(Optional.of(seed));
        when(seeds.conceptIdOf(seed)).thenReturn(CONCEPT_ID);
        when(bmPlans.current(7L)).thenReturn(new BmPlanPreparationService.PlanView(
            MAPPER.createObjectNode(), MAPPER.createObjectNode(), 1));
        when(competitorSeeds.conceptBlock(7L)).thenReturn(
            (tools.jackson.databind.node.ObjectNode) MAPPER.readTree(
                "{\"seeds\":[{\"이름\":\"공비서\",\"왜\":\"직접 경쟁\",\"운영사\":null}]}"));

        service.startFull(1L, 7L, MAPPER.nullNode(), "beauty-noshow", "2026-08-11");

        ArgumentCaptor<JsonNode> concept = ArgumentCaptor.forClass(JsonNode.class);
        org.mockito.Mockito.verify(inputs).full(concept.capture(), anyString(), anyString());
        assertThat(concept.getValue().at("/_경쟁_씨앗/seeds/0/이름").stringValue()).isEqualTo("공비서");
    }

    /**
     * <b>이 검사는 예전에 정확히 반대를 지켰다</b>(「사업안이 없으면 견본 경로 그대로다 —
     * 되돌리기가 여기로 복귀한다」). 그 갈래가 2026-08-12 에 실제로 사고를 냈다: 사업안 B 를
     * 선택했지만 확정 전이라 시드가 없었고, 화면이 보낸 견본 이름표가 그대로 나가
     * <b>미용실 노쇼 견본 원장</b>이 재채점돼 「TAM 10.2억원 · 6/6 · SUCCEEDED」가 나왔다.
     * 냉동 간편식 사업안의 결과로 읽힌다. <b>실패보다 나쁜 것은 남의 자료로 성공했다고
     * 말하는 것</b>이라, 안전판이 아니라 조용한 오답 장치였다.
     */
    @Test
    @DisplayName("확정된 사업안이 없으면 실패시킨다 — 견본으로 조용히 떨어지지 않는다")
    void withoutASeedItFailsLoudly() {
        when(seeds.current(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.startFull(1L, 7L, MAPPER.nullNode(), "beauty-noshow", "2026-08-11"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("확정된 사업안이 없다");

        // **실행을 만들지 않는다.** 화면이 보낸 견본 이름표는 어디에도 안 쓰인다.
        org.mockito.Mockito.verify(inputs, org.mockito.Mockito.never())
            .full(any(), anyString(), anyString());
    }
}
