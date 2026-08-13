package com.aivle.backend.journey;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketInterviewRunRepository extends JpaRepository<MarketInterviewRun, Long> {

    /** 최신 실행. 화면의 「현재」가 이것이다. */
    @EntityGraph(attributePaths = {"project", "taskRun"})
    Optional<MarketInterviewRun> findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId);
}
