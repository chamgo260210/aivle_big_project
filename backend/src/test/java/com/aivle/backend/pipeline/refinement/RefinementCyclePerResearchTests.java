package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersion;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionTaskFactory;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>조사를 다시 돌리면 다듬기도 다시 돈다.</b>
 *
 * <p>2026-08-16 실측 결함. 라운드는 「선택당 정확히 한 번」만 걸렸다 — 폴러 중복을 막으려던
 * 규칙인데, <b>조사를 다시 돌려도 다듬기가 안 걸린다</b>는 뜻이 됐다. 프로젝트 3은 그날
 * 사업 검증이 <b>다섯 번</b> 성공했는데 다듬기 라운드는 <b>이틀 전 것 하나</b>뿐이었고,
 * 한 화면에서 왼쪽(시장조사)은 오늘, 오른쪽(다듬어진 컨셉)은 이틀 전을 말하고 있었다.
 * 그 사실을 알리는 문구도 어디에도 없었다.
 *
 * <p>그렇다고 무조건 걸면 폴러가 누를 때마다 <b>유료 호출</b>이 반복된다. 그래서 판정 재료를
 * 「라운드가 있나」가 아니라 <b>「어느 조사판을 근거로 만든 라운드인가」</b>로 바꿨다.
 */
class RefinementCyclePerResearchTests {

    private record Fixture(ConceptRefinementService service,
                           ConceptRefinementRoundRepository rounds,
                           List<ConceptRefinementRound> saved) { }

    /** 라운드가 아예 없는 경우. */
    private Fixture 라운드없이(Integer nowVersion) {
        return 만든다(false, null, nowVersion);
    }

    /** 라운드가 하나 있는 경우. */
    private Fixture 만든다(Integer roundVersion, Integer nowVersion) {
        return 만든다(true, roundVersion, nowVersion);
    }

    /**
     * @param hasRound     이미 라운드가 있나
     * @param roundVersion 그 라운드가 새긴 조사판. {@code null} 이면 V31 이전 행이다
     * @param nowVersion   지금 화면이 읽는 조사판. {@code null} 이면 검증을 돌린 적이 없다
     */
    private Fixture 만든다(boolean hasRound, Integer roundVersion, Integer nowVersion) {
        ObjectMapper mapper = new ObjectMapper();
        var rounds = mock(ConceptRefinementRoundRepository.class);
        var selections = mock(ConceptPortfolioSelectionRepository.class);
        var versions = mock(MarketResearchVersionRepository.class);
        var tasks = mock(ConceptPortfolioSelectionTaskFactory.class);

        List<ConceptRefinementRound> existing = new ArrayList<>();
        if (hasRound) {
            existing.add(ConceptRefinementRound.of(42L, 17L, 1, "[]", "[]", roundVersion));
        }
        when(rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(17L))
            .thenReturn(existing.isEmpty() ? Optional.empty() : Optional.of(existing.get(0)));
        when(rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(17L)).thenReturn(existing);

        if (nowVersion != null) {
            MarketResearchVersion version = mock(MarketResearchVersion.class);
            when(version.getVersionNumber()).thenReturn(nowVersion);
            when(version.getResultJson()).thenReturn("{}");
            when(versions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(
                eq(42L), eq(MarketResearchRun.Kind.VALIDATION))).thenReturn(Optional.of(version));
        }

        ConceptPortfolioSelection selection = mock(ConceptPortfolioSelection.class);
        when(selection.getId()).thenReturn(17L);
        when(selection.getProjectId()).thenReturn(42L);
        when(selection.getSelectedByUserId()).thenReturn(7L);
        when(selection.getHypothesisRevision()).thenReturn(3);
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(42L))
            .thenReturn(Optional.of(selection));
        when(selections.findById(17L)).thenReturn(Optional.of(selection));

        var selectionService = mock(ConceptPortfolioSelectionService.class);
        when(selectionService.refinementInput(eq("REFINE_FROM_MARKET"), any()))
            .thenAnswer(call -> mapper.createObjectNode());
        // 걸었는지 «여부»만 보는 시험이라 실행 자체는 흉내만 낸다.
        when(tasks.create(anyLong(), any(), eq("REFINE_FROM_MARKET"), any(), anyString(), isNull()))
            .thenReturn(mock(com.aivle.backend.taskrun.domain.TaskRun.class));

        var service = new ConceptRefinementService(
            selections, rounds, versions, tasks,
            mock(ConceptPortfolioDeltaLegalReviewRepository.class),
            mock(ConceptLegalRegulatoryReportRepository.class),
            mock(ConceptRefinementFinalRepository.class),
            selectionService, mock(TaskRunRepository.class),
            mock(ConceptRefinementApplyService.class), mapper);
        return new Fixture(service, rounds, existing);
    }

    @Test
    void 새_조사판이_오면_다듬기를_다시_건다() {
        Fixture f = 만든다(1, 2);
        assertThat(f.service().startFirstRound(42L))
            .as("조사가 새로 돌았는데 안 걸면 화면 절반이 옛 조사 기준으로 남는다")
            .isPresent();
    }

    @Test
    void 같은_조사판이면_두_번_걸지_않는다() {
        Fixture f = 만든다(2, 2);
        assertThat(f.service().startFirstRound(42L))
            .as("폴러가 누를 때마다 걸면 유료 호출이 사용자 의도 없이 반복된다")
            .isEmpty();
    }

    @Test
    void 옛_주기는_물러난다_지워지지_않는다() {
        Fixture f = 만든다(1, 2);
        f.service().startFirstRound(42L);
        assertThat(f.saved().get(0).getDeletedAt())
            .as("안 물리면 라운드 번호가 이어져 상한 3을 옛 주기가 다 써 버린다")
            .isNotNull();
    }

    @Test
    void 조사판을_모르는_V31_이전_라운드도_물러난다() {
        // V31 이전 행은 `research_version` 이 NULL 이다. 「모른다」를 「같다」로 읽으면
        // 그 프로젝트는 영영 새 주기를 못 받는다 — 프로젝트 3이 정확히 그 상태였다.
        Fixture f = 만든다(null, 2);
        assertThat(f.service().startFirstRound(42L)).isPresent();
    }

    @Test
    void 라운드가_아예_없으면_첫_라운드를_건다() {
        assertThat(라운드없이(2).service().startFirstRound(42L)).isPresent();
    }

    @Test
    void 다른_제안_받기도_새_조사판이면_새_주기를_연다() {
        // 옛 주기를 이어가면 「다른 제안」을 눌렀는데 **낡은 조사 기준** 제안이 오고,
        // 옛 주기가 상한 3을 이미 썼으면 아예 눌러지지도 않는다.
        Fixture f = 만든다(1, 2);
        assertThat(f.service().retryRound(7L, 42L, 17L)).isNotNull();
        assertThat(f.saved().get(0).getDeletedAt())
            .as("새 주기를 열었으면 옛 주기는 물러나야 한다")
            .isNotNull();
    }

    @Test
    void 검증을_돌린_적이_없으면_옛_규칙대로_한_번만_건다() {
        // 조사판 번호가 없으면 「같은 판인가」를 물을 수 없다. 그때 새 주기를 열면
        // 폴링마다 라운드가 서고 **유료 호출이 사용자 의도 없이 반복된다.**
        assertThat(만든다(1, null).service().startFirstRound(42L)).isEmpty();
    }
}
