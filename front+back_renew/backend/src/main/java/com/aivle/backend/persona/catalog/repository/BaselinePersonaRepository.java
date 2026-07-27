package com.aivle.backend.persona.catalog.repository;

import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BaselinePersonaRepository extends JpaRepository<BaselinePersona, Long> {
    long countByCatalogVersionAndDeletedAtIsNull(String catalogVersion);
    List<BaselinePersona> findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
        String catalogVersion);
    Optional<BaselinePersona> findByPersonaCodeAndCatalogVersionAndDeletedAtIsNull(
        String personaCode, String catalogVersion);
}
