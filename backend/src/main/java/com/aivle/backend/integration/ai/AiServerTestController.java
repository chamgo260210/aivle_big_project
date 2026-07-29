package com.aivle.backend.integration.ai;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/test/ai-server")
public class AiServerTestController {

    private final AiServerHealthClient aiServerHealthClient;
    private final AiServerMarketingClient aiServerMarketingClient;

    public AiServerTestController(
        AiServerHealthClient aiServerHealthClient,
        AiServerMarketingClient aiServerMarketingClient
    ) {
        this.aiServerHealthClient =
            aiServerHealthClient;

        this.aiServerMarketingClient =
            aiServerMarketingClient;
    }

    // 기존 AI 서버 상태 확인 API
    @GetMapping("/health")
    public AiServerHealthClient.AiServerHealthResponse
        checkHealth() {

        return aiServerHealthClient.checkHealth();
    }

    // 마케팅 배너 생성 요청 전달 테스트
    @PostMapping(
        value = "/marketing/banners/generate",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, Object> generateBanner(
        @RequestParam("promotion_name")
        String promotionName,

        @RequestParam("main_banner")
        String mainBanner,

        @RequestParam("supporting_copy")
        String supportingCopy,

        @RequestParam("mood")
        String mood,

        @RequestParam("banner_format")
        String bannerFormat,

        @RequestParam(
            value = "emphasis_keywords",
            defaultValue = ""
        )
        String emphasisKeywords,

        @RequestPart("image")
        MultipartFile image
    ) throws IOException {

        return aiServerMarketingClient.generateBanner(
            promotionName,
            mainBanner,
            supportingCopy,
            mood,
            bannerFormat,
            emphasisKeywords,
            image
        );
    }
}