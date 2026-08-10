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
        return createRestClient(properties);
    }

    /**
     * 오래 걸리는 작업(시장조사 전 구간 90~266초) 전용 클라이언트.
     *
     * <p>기본 빈과 <b>read timeout 만</b> 다르다. 하나로 합쳐 전부 길게 잡으면
     * 짧아야 할 호출이 죽지 않고 매달려 워커가 막힌다.
     */
    @Bean
    @Qualifier("aiServerLongRestClient")
    RestClient aiServerLongRestClient(
        AiServerProperties properties,
        @org.springframework.beans.factory.annotation.Value(
            "${app.ai-server.long-read-timeout:420s}") java.time.Duration longReadTimeout) {
        // ⚠ 이 값을 `AiServerProperties` record 에 넣지 않는다. 생성자가 둘이 되면
        //    @ConfigurationProperties 가 canonical 생성자를 못 골라 바인딩이 통째로 깨진다
        //    (판 ㉝ 실측: 그 순간 ApplicationContext 가 안 떠 99개 테스트가 죽었다).
        return createRestClient(properties, longReadTimeout);
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
