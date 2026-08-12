package com.aivle.backend.journey;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResearchCompetitorSeedRepository extends JpaRepository<ResearchCompetitorSeed, String> {
    List<ResearchCompetitorSeed> findAllByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAsc(Long projectId);
}
