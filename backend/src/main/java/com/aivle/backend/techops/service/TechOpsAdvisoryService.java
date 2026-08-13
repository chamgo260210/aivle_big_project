package com.aivle.backend.techops.service;

import static com.aivle.backend.techops.dto.TechOpsAdvisoryModels.*;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.journey.MarketResearchRun;
import com.aivle.backend.journey.MarketResearchVersionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TechOpsAdvisoryService {
    private final ProjectRepository projects;
    private final TechOpsAdvisoryAiClient ai;
    private final MarketResearchVersionRepository marketResearchVersions;
    private final tools.jackson.databind.ObjectMapper mapper;
    public AdvisoryResponse generate(Long ownerId, Long projectId, AdvisoryRequest request) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        var result = ai.generate(request.concept(), request.market(), request.bm(), request.legalHandoff());
        return new AdvisoryResponse("TECH_OPS_COMMERCIALIZATION_ADVISORY_V1", projectId, Instant.now().toString(), result);
    }
    public AdvisoryResponse generateFromProject(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId).orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        // TODO(concept-handoff): replace this marker with the user-confirmed concept handoff.
        // Tech-ops can currently start from market + BM even before the concept-selection integration is wired.
        var concept = mapper.createObjectNode().put("status", "CONCEPT_HANDOFF_NOT_CONNECTED");
        var bmVersion = marketResearchVersions.findTopByProjectIdAndKindAndDeletedAtIsNullOrderByVersionNumberDesc(projectId, MarketResearchRun.Kind.BM)
            .orElseThrow(() -> new BusinessException(ErrorCode.TECH_OPS_PREPARATION_REQUIRED, "Complete business-model analysis before tech-ops analysis."));
        log.info("Tech-ops advisory BM handoff loaded: projectId={}, versionId={}", projectId, bmVersion.getId());
        var bm = mapper.readTree(bmVersion.getResultJson());
        var market = bmVersion.getSourceRun() == null || bmVersion.getSourceRun().getSourceRun() == null ? mapper.createObjectNode().put("status", "MARKET_RESULT_NOT_AVAILABLE")
            : marketResearchVersions.findBySourceRunIdAndDeletedAtIsNull(bmVersion.getSourceRun().getSourceRun().getId())
                .map(x -> mapper.readTree(x.getResultJson())).orElse(mapper.createObjectNode().put("status", "MARKET_RESULT_NOT_AVAILABLE"));
        log.info("Tech-ops advisory calling AI server: projectId={}, marketAvailable={}", projectId,
            !"MARKET_RESULT_NOT_AVAILABLE".equals(market.path("status").asText()));
        var result = ai.generate(concept, market, bm, null);
        log.info("Tech-ops advisory completed: projectId={}", projectId);
        return new AdvisoryResponse("TECH_OPS_COMMERCIALIZATION_ADVISORY_V1", projectId, Instant.now().toString(), result);
    }
}
