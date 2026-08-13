package com.aivle.backend.pipeline.marketseed.application;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 프로젝트의 <b>지금 유효한 Market Seed</b>를 찾는다. <b>답은 여기 하나뿐이어야 한다.</b>
 *
 * <p><b>왜 따로 두는가.</b> 답이 두 벌이라 실제로 틀렸다. 컨셉 v2 병합 뒤
 * {@code ProjectModuleStatusService} 는 <b>사업안(포트폴리오)</b> 경로로 시드를 찾는데
 * {@code TwinSurveyStimulusDraftService} 는 <b>레거시 선택</b> 경로만 봤다 — 그래서
 * 사업안으로 확정한 프로젝트에서 자극 초안이 <b>조용히 견본 이름표로 떨어졌다.</b>
 * 예외도 로그도 없었다. 세 번째 소비자(시장조사 배선)를 붙이면서 그 자리를 막는다.
 *
 * <p>순서는 <b>사업안이 먼저다.</b> 둘 다 있으면 새 것이 이긴다 — 레거시 선택은 도달 불가
 * 경로에 남아 있는 것이라, 그것이 이기면 사용자가 방금 확정한 사업안이 무시된다.
 */
@Component
public class MarketAnalysisSeedLookup {

    private final ConceptPortfolioSelectionRepository portfolioSelections;
    private final ConceptSelectionRepository legacySelections;
    private final MarketAnalysisSeedSnapshotRepository snapshots;

    public MarketAnalysisSeedLookup(ConceptPortfolioSelectionRepository portfolioSelections,
                                    ConceptSelectionRepository legacySelections,
                                    MarketAnalysisSeedSnapshotRepository snapshots) {
        this.portfolioSelections = portfolioSelections;
        this.legacySelections = legacySelections;
        this.snapshots = snapshots;
    }

    /** @return 확정된 시드. 아직 사업안을 확정하지 않았으면 {@code empty}. */
    public Optional<MarketAnalysisSeedSnapshot> current(Long projectId) {
        Optional<MarketAnalysisSeedSnapshot> portfolio = portfolioSelections
            .findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId)
            .flatMap(selection -> snapshots
                .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId()));
        if (portfolio.isPresent()) return portfolio;
        return legacySelections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(projectId)
            .flatMap(selection -> snapshots.findBySelectionIdAndProjectIdAndDeletedAtIsNull(
                selection.getId(), projectId));
    }

    /**
     * 시드의 <b>컨셉 식별자</b>. 시장조사 원장의 이름이 되므로 프로젝트끼리 겹치면 안 된다.
     *
     * <p>⚠ 스냅샷 본문 안의 {@code conceptId} 를 쓰지 않는다 — 그것은 AI 후보 id
     * (「C1」 같은 값)라 <b>다른 프로젝트와 겹친다.</b> 여기서 돌려주는 것은 DB 행의
     * 식별자다(포트폴리오면 UUID).
     */
    public String conceptIdOf(MarketAnalysisSeedSnapshot seed) {
        return seed.getPortfolioConceptId() != null ? seed.getPortfolioConceptId()
            : seed.getConceptId();
    }

    /** 사업안(포트폴리오) 기반 시드인가. 레거시 선택 기반이면 {@code false}. */
    public boolean isPortfolio(MarketAnalysisSeedSnapshot seed) {
        return seed.getPortfolioSelectionId() != null;
    }

    /** 현재 선택된 사업안. 화면 게이트와 같은 것을 본다. */
    public Optional<ConceptPortfolioSelection> currentPortfolioSelection(Long projectId) {
        return portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId);
    }
}
