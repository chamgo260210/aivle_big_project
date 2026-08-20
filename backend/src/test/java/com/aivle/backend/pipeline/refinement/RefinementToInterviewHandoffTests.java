package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.security.CurrentUserProvider;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioDeltaLegalReviewRepository;
import com.aivle.backend.pipeline.market.MarketInterviewBoardService;
import com.aivle.backend.pipeline.marketseed.application.MarketAnalysisSeedLookup;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * <b>다듬어진 컨셉만 응답자에게 간다.</b> (2026-08-15)
 *
 * <p>왜 이 파일이 있나. 시드 스냅샷은 <b>두 번</b> 발급된다 — 사업안을 고른 직후 한 번(시장조사가
 * 이것을 먹는다), 그리고 다듬기가 끝나고 「이 컨셉으로 확정하기」를 누를 때 한 번. 두 시드는
 * 형태가 완전히 같았고, 시장 인터뷰는 그중 아무거나 집어 응답자에게 보여 줬다.
 *
 * <p>그래서 실제로 이런 일이 벌어졌다: 사업 검증이 시장 근거로 「누구를 위한 것인가」와
 * 「하는 일」을 고쳐 놓아도, 소비자는 <b>고치기 전 설명</b>을 보고 답했다. 화면에는 언제나
 * 「확정한 사업안에서 그대로 가져왔다」고만 적혀 있어 사용자가 알 방법도 없었다.
 *
 * <p>이 파일이 지키는 것은 셋이다 — <b>첫 시드는 다듬기 표시가 없다</b>,
 * <b>표시 없는 시드로는 자극을 못 만든다</b>, <b>표시 있는 시드는 여섯 칸을 정상으로 준다</b>.
 */
class RefinementToInterviewHandoffTests {

    private static final String HASH = "sha256:" + "0".repeat(64);

    private final ObjectMapper mapper = new ObjectMapper();

    /** 다듬기가 실제로 고치는 두 칸이 들어 있는 시드. 나머지 칸은 이 시험의 관심이 아니다. */
    private String snapshotJson(String targetUsers, String feature) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode concept = root.putObject("selectedConcept");
        concept.putObject("identity").put("conceptName", "1인분 냉동 간편식").put("targetUsers", targetUsers);
        ObjectNode solution = concept.putObject("solution");
        solution.put("problemScenario", "혼자 저녁을 차리기 번거롭다");
        solution.putArray("featureSet").add(feature);
        ObjectNode hypotheses = root.putObject("finalHypotheses");
        hypotheses.putObject("differentiators").put("value", "1인분 정량 설계");
        hypotheses.putObject("price").put("value", "7900원");
        return root.toString();
    }

    private MarketAnalysisSeedSnapshot seed(String snapshotJson, boolean refinementApplied) {
        return MarketAnalysisSeedSnapshot.createPortfolio("seed-1", 7L, 17L, "concept-1", "report-1",
            "2.0", HASH, HASH, snapshotJson, 3L, Instant.parse("2026-08-15T00:00:00Z"), refinementApplied);
    }

    private MarketInterviewBoardService serviceReturning(MarketAnalysisSeedSnapshot value) {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(anyLong(), anyLong()))
            .thenReturn(Optional.of(mock(Project.class)));
        MarketAnalysisSeedLookup seeds = mock(MarketAnalysisSeedLookup.class);
        when(seeds.current(anyLong())).thenReturn(Optional.ofNullable(value));
        return new MarketInterviewBoardService(projects, seeds, mapper);
    }

    private boolean finalized(MarketAnalysisSeedSnapshot value) {
        MarketAnalysisSeedSnapshotRepository seeds = mock(MarketAnalysisSeedSnapshotRepository.class);
        when(seeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(17L))
            .thenReturn(Optional.ofNullable(value));
        ConceptRefinementController controller = new ConceptRefinementController(
            mock(ConceptRefinementService.class), mock(ConceptPortfolioSelectionService.class),
            mock(ConceptPortfolioDeltaLegalReviewRepository.class), seeds, mapper,
            mock(CurrentUserProvider.class));
        return ReflectionTestUtils.invokeMethod(controller, "finalized", 17L);
    }

    @Test
    void theFirstSeedCarriesNoRefinementMark() {
        // 사업안을 고른 직후 발급되는 시드. 이 갈래에 기본값 true 가 생기면 게이트가 통째로 죽는다.
        MarketAnalysisSeedSnapshot first = MarketAnalysisSeedSnapshot.createPortfolio(
            "seed-0", 7L, 17L, "concept-1", "report-1", "2.0", HASH, HASH,
            snapshotJson("1인 가구", "1인분 정량"), 3L, Instant.parse("2026-08-15T00:00:00Z"));
        assertThat(first.isRefinementApplied()).isFalse();
        assertThat(finalized(first)).isFalse();
    }

    @Test
    void aSeedBuiltAfterRefinementIsMarked() {
        MarketAnalysisSeedSnapshot refined = seed(snapshotJson("1인 가구", "1인분 정량"), true);
        assertThat(refined.isRefinementApplied()).isTrue();
        assertThat(finalized(refined)).isTrue();
    }

    @Test
    void theStimulusIsRefusedWhileTheRefinedConceptIsUnconfirmed() {
        // ⚠ 여기가 진짜 문이다. 여정 칸 게이트만으로는 주소를 직접 치고 들어오는 길이 남는다.
        MarketInterviewBoardService service =
            serviceReturning(seed(snapshotJson("1인 가구", "1인분 정량"), false));

        assertThatThrownBy(() -> service.board(3L, 7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("이 컨셉으로 확정하기");
    }

    @Test
    void aConfirmedRefinedConceptBecomesTheStimulus() {
        // 다듬기가 고친 두 칸(targetUsers·featureSet)이 실제로 자극에 실려야 한다 —
        // 이 둘이 오버레이 경로라 조용히 빠지던 자리다.
        MarketInterviewBoardService service = serviceReturning(
            seed(snapshotJson("1인 가구 30대 직장인", "데우기 3분"), true));

        var board = service.board(3L, 7L);

        assertThat(board.path("targetUsers").asText()).isEqualTo("1인 가구 30대 직장인");
        assertThat(board.path("featureSet").get(0).asText()).isEqualTo("데우기 3분");
        assertThat(board.path("conceptName").asText()).isEqualTo("1인분 냉동 간편식");
        assertThat(board.path("priceKrw").asInt()).isEqualTo(7900);
    }

    @Test
    void noSeedAtAllStillPointsAtTheEarlierStep() {
        // 「사업안을 아직 안 골랐다」와 「골랐지만 확정을 안 지났다」는 갈 곳이 다르다.
        // 두 문구가 같아지면 사용자는 어디로 가야 할지 알 수 없다.
        assertThatThrownBy(() -> serviceReturning(null).board(3L, 7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("확정된 사업안이 없다");
    }
}
