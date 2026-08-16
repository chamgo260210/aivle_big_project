package com.aivle.backend.pipeline.refinement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptRefinementRoundRepository extends JpaRepository<ConceptRefinementRound, Long> {

    /** 이 선택의 마지막 라운드. 루프가 어디까지 왔는지는 <b>DB 가 안다</b>. */
    Optional<ConceptRefinementRound> findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(Long selectionId);

    /** 이력 전체 — 최종 화면의 변경 표와 「못 푼 것」 사유가 여기서 나온다. */
    List<ConceptRefinementRound> findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(Long selectionId);

    long countBySelectionIdAndDeletedAtIsNull(Long selectionId);

    /** 법률 결과가 적힌 라운드 — 루프가 다음 걸음을 걸 수 있는 것들이다. */
    List<ConceptRefinementRound> findByLegalOutcomeIsNotNullAndDeletedAtIsNull();
}
