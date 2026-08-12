package com.aivle.backend.pipeline.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptPortfolioConcept;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptPortfolioConceptRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotFactory;
import com.aivle.backend.pipeline.marketing.application.MarketingSourceSnapshotService;
import com.aivle.backend.pipeline.marketing.domain.MarketingSourceSnapshot;
import com.aivle.backend.pipeline.marketing.repository.MarketingSourceSnapshotRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.selection.domain.ConceptSelection;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;


class MarketingSourceSnapshotServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
    private final ConceptPortfolioSelectionRepository portfolioSelections = mock(ConceptPortfolioSelectionRepository.class);
    private final MarketAnalysisSeedSnapshotRepository marketSeeds = mock(MarketAnalysisSeedSnapshotRepository.class);
    private final MarketingSourceSnapshotRepository sources = mock(MarketingSourceSnapshotRepository.class);
    private final ConceptRepository concepts = mock(ConceptRepository.class);
    private final ConceptPortfolioConceptRepository portfolioConcepts = mock(ConceptPortfolioConceptRepository.class);
    private final MarketingSourceSnapshotFactory factory = mock(MarketingSourceSnapshotFactory.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketingSourceSnapshotService service = new MarketingSourceSnapshotService(projects, selections,
        portfolioSelections, marketSeeds, sources, concepts, portfolioConcepts, factory, mapper);
    private final ConceptSelection selection = mock(ConceptSelection.class);
    private final MarketAnalysisSeedSnapshot marketSeed = MarketAnalysisSeedSnapshot.create("market-seed-1", 1L, 2L,
        "concept-1", "2.0", "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64), "{}", 7L, Instant.EPOCH);

    @BeforeEach
    void currentOwnedProject() {
        Project project = mock(Project.class); User owner = mock(User.class);
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(selection.getId()).thenReturn(2L);
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(1L)).thenReturn(Optional.of(selection));
        when(marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(2L, 1L)).thenReturn(Optional.of(marketSeed));
    }
    @Test
    void repeatedFinalizationReturnsTheSameSourceSnapshot() {
        var existing = MarketingSourceSnapshot.create("source-1", 1L, "market-seed-1", 2L, "concept-1", "2.0",
            "sha256:" + "c".repeat(64), "{}", 7L, Instant.EPOCH);
        when(sources.findBySourceMarketSeedSnapshotIdAndProjectIdAndDeletedAtIsNull("market-seed-1", 1L))
            .thenReturn(Optional.of(existing));
        var result = service.finalizeSnapshot(7L, 1L);
        assertThat(result.snapshotId()).isEqualTo("source-1");
        assertThat(result.createdAt()).isEqualTo(Instant.EPOCH);
        verify(concepts, never()).findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 1L);
        verify(sources, never()).save(org.mockito.ArgumentMatchers.any());
    }
    @Test
    void currentRequiresFinalizedMarketSeed() {
        when(marketSeeds.findBySelectionIdAndProjectIdAndDeletedAtIsNull(2L, 1L)).thenReturn(Optional.empty());
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(1L, 7L)).thenReturn(Optional.of(mock(Project.class)));
        assertThatThrownBy(() -> service.current(7L, 1L)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
    }
    @Test
    void finalizesSourceFromCurrentPortfolioConcept() {
        ConceptPortfolioSelection portfolioSelection = mock(ConceptPortfolioSelection.class);
        ConceptPortfolioConcept portfolioConcept = mock(ConceptPortfolioConcept.class);
        var portfolioSeed = MarketAnalysisSeedSnapshot.createPortfolio("portfolio-market-seed", 1L, 17L,
            "portfolio-concept", "legal-report", "2.0", "sha256:" + "d".repeat(64),
            "sha256:" + "e".repeat(64), "{}", 7L, Instant.EPOCH);
        ObjectNode body = mapper.createObjectNode();
        body.put("conceptName", "실제 컨셉");

        when(portfolioSelection.getId()).thenReturn(17L);
        when(portfolioSelection.getConceptId()).thenReturn("portfolio-concept");
        when(portfolioSelections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(1L))
            .thenReturn(Optional.of(portfolioSelection));
        when(marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(17L))
            .thenReturn(Optional.of(portfolioSeed));
        when(portfolioConcepts.findByIdAndProjectIdAndDeletedAtIsNull("portfolio-concept", 1L))
            .thenReturn(Optional.of(portfolioConcept));
        when(factory.create(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(portfolioSeed), org.mockito.ArgumentMatchers.eq(portfolioConcept)))
            .thenReturn(new MarketingSourceSnapshotFactory.BuiltSnapshot(body, "sha256:" + "f".repeat(64)));
        when(sources.save(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));

        var result = service.finalizeSnapshot(7L, 1L);

        assertThat(result.selectionId()).isEqualTo(17L);
        assertThat(result.conceptId()).isEqualTo("portfolio-concept");
        ArgumentCaptor<MarketingSourceSnapshot> saved = ArgumentCaptor.forClass(MarketingSourceSnapshot.class);
        verify(sources).save(saved.capture());
        assertThat(saved.getValue().getSourceType()).isEqualTo("CONCEPT_PORTFOLIO_V2");
        assertThat(saved.getValue().getSelectionId()).isNull();
        assertThat(saved.getValue().getConceptId()).isNull();
        assertThat(saved.getValue().getPortfolioSelectionId()).isEqualTo(17L);
        assertThat(saved.getValue().getPortfolioConceptId()).isEqualTo("portfolio-concept");
        verify(concepts, never()).findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
