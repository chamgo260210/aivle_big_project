package com.aivle.backend.techops.service;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
@Slf4j
public class TechOpsAdvisoryAiClient {
    private final RestClient client;
    private final AiServerProperties properties;
    public TechOpsAdvisoryAiClient(@Qualifier("aiServerLongRestClient") RestClient client, AiServerProperties properties) {
        this.client = client; this.properties = properties;
    }
    public JsonNode generate(JsonNode concept, JsonNode market, JsonNode bm, JsonNode legalHandoff) {
        if (!properties.hasInternalApiKey()) throw new IllegalStateException("AI server internal API key is missing");
        Map<String, Object> body = new HashMap<>();
        body.put("concept", concept); body.put("market", market); body.put("bm", bm); body.put("legalHandoff", legalHandoff);
        try {
            return client.post().uri("/internal/v1/tech-ops/advisory")
                .headers(h -> h.set("X-Internal-Api-Key", properties.internalApiKey()))
                .body(body)
                .retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException exception) {
            // Do not expose an upstream provider response to the browser, but retain
            // its status/body in the server log so configuration, schema, and timeout
            // failures can be distinguished during diagnosis.
            String response = exception.getResponseBodyAsString();
            log.error("Tech-ops AI endpoint rejected request: status={}, body={}",
                exception.getStatusCode(), abbreviate(response, 2_000));
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "Tech-ops advisory AI endpoint is unavailable");
        } catch (RestClientException exception) {
            log.error("Tech-ops AI endpoint connection failure", exception);
            throw new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE,
                "Tech-ops advisory AI endpoint is unavailable");
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }
}
