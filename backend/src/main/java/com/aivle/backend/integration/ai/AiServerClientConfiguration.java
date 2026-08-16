package com.aivle.backend.integration.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServerClientConfiguration {

    @Bean
    @Qualifier("aiServerRestClient")
    RestClient aiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.readTimeout());
    }

    @Bean
    @Qualifier("longRunningAiServerRestClient")
    RestClient longRunningAiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.longReadTimeout());
    }

    @Bean
    @Qualifier("marketResearchAiServerRestClient")
    RestClient marketResearchAiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.marketResearchReadTimeout());
    }

    @Bean
    @Qualifier("conceptPortfolioAiServerRestClient")
    RestClient conceptPortfolioAiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.conceptPortfolioReadTimeout());
    }

    @Bean
    @Qualifier("twinSurveyAiServerRestClient")
    RestClient twinSurveyAiServerRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.twinSurveyReadTimeout());
    }

    /**
     * 사업 검증 전용. 시장조사(FULL)와 BM 을 <b>한 실행</b>으로 잇는다.
     *
     * <p>⚠ 실측: FULL 은 약 <b>23분</b>(run 15: 07:36:53→07:59:42), BM 은 18~39초다.
     * 둘을 이으면 {@code long-read-timeout}(420s)으로는 <b>한참 모자라고</b>,
     * 그 실패가 {@code REQUEST_DEADLINE_EXCEEDED}(retryable)로 사상돼 재시도가
     * 23분짜리를 다시 태운다 — 실패하면서 비용만 배가 된다.
     *
     * <p>트윈(900s)에 얹지 않고 등급을 또 나누는 이유도 같다. 900초로는 FULL 하나도
     * 못 끝낸다.
     *
     * <p>★ <b>판 ㊺ — 2100s(35분) → 3600s(60분).</b> {@code BusinessValidationWorker.BUDGET}
     * 과 <b>같이 움직여야 한다</b> — 워커만 늘리면 HTTP 가 먼저 끊겨서 늘린 뜻이 없다.
     * 커진 이유는 그쪽에 적었다(호출 266→470 · 발췌가 추론 모델로).
     */
    @Bean
    @Qualifier("aiServerValidationRestClient")
    RestClient aiServerValidationRestClient(
        AiServerProperties properties,
        @org.springframework.beans.factory.annotation.Value(
            "${app.ai-server.validation-read-timeout:3600s}") java.time.Duration validationReadTimeout) {
        return createRestClient(properties, validationReadTimeout);
    }

    RestClient createRestClient(AiServerProperties properties) {
        return createRestClient(properties, properties.readTimeout());
    }

    RestClient createRestClient(AiServerProperties properties, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
            properties.connectTimeout()
        );
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(
                HttpHeaders.ACCEPT,
                MediaType.APPLICATION_JSON_VALUE
            )
            .build();
    }
}
