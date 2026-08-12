package com.aivle.backend.techops.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public final class TechOpsAdvisoryModels {
    private TechOpsAdvisoryModels() { }
    public record AdvisoryRequest(@NotNull JsonNode concept, @NotNull JsonNode market, @NotNull JsonNode bm, JsonNode legalHandoff) { }
    public record AdvisoryResponse(String contract, Long projectId, String generatedAt, JsonNode result) { }
}
