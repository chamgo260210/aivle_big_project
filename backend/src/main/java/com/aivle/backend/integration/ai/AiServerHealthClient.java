package com.aivle.backend.integration.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServerHealthClient {

    private final RestClient restClient;

    public AiServerHealthClient(
        @Value("${app.ai-server.base-url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    public AiServerHealthResponse checkHealth() {
        AiServerHealthResponse response = restClient.get()
            .uri("/health")
            .retrieve()
            .body(AiServerHealthResponse.class);

        if (response == null) {
            throw new IllegalStateException(
                "AI 서버에서 응답을 받지 못했습니다."
            );
        }

        return response;
    }

    public record AiServerHealthResponse(
        String status,
        String service
    ) {
    }
}