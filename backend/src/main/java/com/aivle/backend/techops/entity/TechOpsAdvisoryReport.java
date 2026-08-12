package com.aivle.backend.techops.entity;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Immutable response contract. Persistence can be added later without changing the API shape. */
public record TechOpsAdvisoryReport(Long projectId, Instant generatedAt, JsonNode input, JsonNode result) { }
