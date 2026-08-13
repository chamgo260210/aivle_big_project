package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 컨셉보드 — 확정된 사업안에서 응답자에게 보일 여섯 칸을 꺼낸다.
 *
 * <p><b>LLM 을 부르지 않는다.</b> 우열 조사는 「비교할 두 안」을 지어내야 해서 프롬프트가
 * 한 번 필요했지만, 여기 자극은 확정된 사업안 그 자체다. 지어낼 것이 없으므로 결정론적
 * 추출로 끝난다 — TaskRun 도 남기지 않는다.
 *
 * <p>스냅샷 전체를 넘기지 않는 이유는 {@link TwinSurveyStimulusDraftService} 와 같다:
 * 법률 근거·평가 원문까지 프롬프트에 실리면 자극이 상품 설명이 아니라 사업계획서가 된다.
 *
 * <p>⚠ <b>견본 이름표 갈래를 만들지 않는다.</b> 확정 전이면 실패시킨다. 그 갈래가 실제로
 * 사고를 냈다 — 사업안을 선택했지만 확정 전이라 시드가 없었고, 미용실 노쇼 견본 원장이
 * 냉동 간편식 사업안의 결과로 나왔다(6/6 · SUCCEEDED). 조용한 기본값을 만들지 않는다.
 */
@Service
public class MarketInterviewBoardService {

    private final ProjectRepository projects;
    private final MarketAnalysisSeedLookup seeds;
    private final ObjectMapper mapper;

    public MarketInterviewBoardService(ProjectRepository projects, MarketAnalysisSeedLookup seeds,
                                       ObjectMapper mapper) {
        this.projects = projects;
        this.seeds = seeds;
        this.mapper = mapper;
    }

    /** 화면이 첫 칸을 채울 때 부른다. 사용자는 이 여섯 칸을 손본 뒤 조사를 건다. */
    public ObjectNode board(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없다"));
        return fromSeed(projectId);
    }

    private ObjectNode fromSeed(Long projectId) {
        MarketAnalysisSeedSnapshot snapshot = seeds.current(projectId).orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "확정된 사업안이 없다 — 사업안을 선택하고 확정한 뒤에 인터뷰를 걸 수 있다"));
        JsonNode seed = mapper.readTree(snapshot.getSnapshotJson());
        JsonNode concept = seed.path("selectedConcept");
        JsonNode hypotheses = seed.path("finalHypotheses");

        ObjectNode board = mapper.createObjectNode();
        board.put("conceptName", concept.path("identity").path("conceptName").asText(""));
        board.put("targetUsers", concept.path("identity").path("targetUsers").asText(""));
        board.put("problemScenario", concept.path("solution").path("problemScenario").asText(""));
        ArrayNode features = board.putArray("featureSet");
        for (JsonNode feature : concept.path("solution").path("featureSet")) {
            if (feature.isTextual() && !feature.asText().isBlank()) features.add(feature.asText());
        }
        board.put("differentiators", hypotheses.path("differentiators").path("value").asText(""));
        // 가격 파싱은 자극 초안과 **같은 규칙**을 쓴다. 두 벌로 두면 조용히 갈라진다 —
        // 「베낀 조회는 갈라진다」가 이 저장소에서 이미 세 번 일어났다.
        Long price = TwinSurveyStimulusDraftService.priceKrw(hypotheses.path("price").path("value").asText(""));
        if (price == null) board.putNull("priceKrw"); else board.put("priceKrw", price);

        if (board.path("conceptName").asText().isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "컨셉 스냅샷에 이름이 없다 — 응답자에게 보일 자극이 아니다");
        }
        return board;
    }
}
