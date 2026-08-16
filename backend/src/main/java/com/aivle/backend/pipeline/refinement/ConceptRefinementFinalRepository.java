package com.aivle.backend.pipeline.refinement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRefinementFinalRepository extends JpaRepository<ConceptRefinementFinal, Long> {

    /** 한 선택에 하나뿐이다 — 「무엇으로 끝났나」가 둘이면 어느 것이 최종인지 모호해진다. */
    Optional<ConceptRefinementFinal> findBySelectionIdAndDeletedAtIsNull(Long selectionId);
}
