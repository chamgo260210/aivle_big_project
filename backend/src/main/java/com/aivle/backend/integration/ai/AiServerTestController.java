package com.aivle.backend.integration.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/ai-server")
public class AiServerTestController {

    private final AiServerHealthClient aiServerHealthClient;

    public AiServerTestController(
        AiServerHealthClient aiServerHealthClient
    ) {
        this.aiServerHealthClient = aiServerHealthClient;
    }

    @GetMapping("/health")
    public AiServerHealthClient.AiServerHealthResponse checkHealth() {
        return aiServerHealthClient.checkHealth();
    }
}