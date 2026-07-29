package com.aivle.backend.integration.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AiServerMarketingClient {

    private static final MediaType UTF8_TEXT =
        new MediaType(
            "text",
            "plain",
            StandardCharsets.UTF_8
        );

    private final RestTemplate restTemplate;
    private final String generateBannerUrl;

    public AiServerMarketingClient(
        @Value("${app.ai-server.base-url}")
        String baseUrl
    ) {
        this.restTemplate = new RestTemplate();

        this.generateBannerUrl =
            baseUrl
                + "/api/v1/marketing/banners/generate";
    }

    public Map<String, Object> generateBanner(
        String promotionName,
        String mainBanner,
        String supportingCopy,
        String mood,
        String bannerFormat,
        String emphasisKeywords,
        MultipartFile image
    ) throws IOException {

        MultiValueMap<String, Object> multipartBody =
            new LinkedMultiValueMap<>();

        multipartBody.add(
            "promotion_name",
            createTextPart(promotionName)
        );

        multipartBody.add(
            "main_banner",
            createTextPart(mainBanner)
        );

        multipartBody.add(
            "supporting_copy",
            createTextPart(supportingCopy)
        );

        multipartBody.add(
            "mood",
            createTextPart(mood)
        );

        multipartBody.add(
            "banner_format",
            createTextPart(bannerFormat)
        );

        multipartBody.add(
            "emphasis_keywords",
            createTextPart(emphasisKeywords)
        );

        multipartBody.add(
            "image",
            createImagePart(image)
        );

        HttpHeaders requestHeaders =
            new HttpHeaders();

        requestHeaders.setContentType(
            MediaType.MULTIPART_FORM_DATA
        );

        HttpEntity<
            MultiValueMap<String, Object>
        > requestEntity = new HttpEntity<>(
            multipartBody,
            requestHeaders
        );

        ResponseEntity<Map<String, Object>>
            response = restTemplate.exchange(
                generateBannerUrl,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<
                    Map<String, Object>
                >() {
                }
            );

        Map<String, Object> responseBody =
            response.getBody();

        if (responseBody == null) {
            throw new IllegalStateException(
                "AI 서버에서 배너 생성 결과를 "
                    + "받지 못했습니다."
            );
        }

        return responseBody;
    }

    private HttpEntity<String> createTextPart(
        String value
    ) {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(UTF8_TEXT);

        return new HttpEntity<>(
            value,
            headers
        );
    }

    private HttpEntity<ByteArrayResource>
        createImagePart(
            MultipartFile image
        ) throws IOException {

        String filename =
            image.getOriginalFilename();

        if (
            filename == null
            || filename.isBlank()
        ) {
            filename = "uploaded-image.jpg";
        }

        FilenameByteArrayResource resource =
            new FilenameByteArrayResource(
                image.getBytes(),
                filename
            );

        HttpHeaders headers = new HttpHeaders();

        if (
            image.getContentType() != null
            && !image.getContentType().isBlank()
        ) {
            headers.setContentType(
                MediaType.parseMediaType(
                    image.getContentType()
                )
            );
        }

        return new HttpEntity<>(
            resource,
            headers
        );
    }

    private static class FilenameByteArrayResource
        extends ByteArrayResource {

        private final String filename;

        private FilenameByteArrayResource(
            byte[] imageBytes,
            String filename
        ) {
            super(imageBytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}