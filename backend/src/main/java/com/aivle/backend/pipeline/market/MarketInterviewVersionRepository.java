package com.aivle.backend.pipeline.market;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInterviewVersionRepository extends JpaRepository<MarketInterviewVersion, Long> {

    @EntityGraph(attributePaths = {"project", "sourceRun", "sourceRun.taskRun"})
    Optional<MarketInterviewVersion> findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(Long projectId);

    /** 멱등의 열쇠 — 같은 실행에 두 번 물질화하지 않는다. */
    Optional<MarketInterviewVersion> findBySourceRunIdAndDeletedAtIsNull(Long sourceRunId);

    long countByProjectIdAndDeletedAtIsNull(Long projectId);
}
