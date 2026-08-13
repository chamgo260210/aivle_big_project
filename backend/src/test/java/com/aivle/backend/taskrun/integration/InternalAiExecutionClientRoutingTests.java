package com.aivle.backend.taskrun.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.integration.ai.AiServerProperties;
import com.aivle.backend.taskrun.domain.TaskType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class InternalAiExecutionClientRoutingTests {
    @Test
    void routesEachSlowTaskTypeToItsOwnClient() {
        RestClient normal = RestClient.builder().build();
        RestClient longRead = RestClient.builder().build();
        RestClient survey = RestClient.builder().build();
        RestClient conceptPortfolio = RestClient.builder().build();
        RestClient validation = RestClient.builder().build();
        AiServerProperties properties = new AiServerProperties("http://localhost",
            Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ofMinutes(15), "token");
        InternalAiExecutionClient client = new InternalAiExecutionClient(
            normal, longRead, survey, conceptPortfolio, validation, properties, new ObjectMapper());

        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_RUN)).isSameAs(conceptPortfolio);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE)).isSameAs(conceptPortfolio);
        assertThat(client.clientFor(TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION)).isSameAs(conceptPortfolio);
        // 시장조사·트윈은 예산 규모가 달라 각자 클라이언트를 쓴다. 여기를 합치면
        // 짧아야 할 호출이 900초를 매달리거나, 긴 호출이 30초에 죽고 재시도로 비용이 배가 된다.
        assertThat(client.clientFor(TaskType.MARKET_RESEARCH)).isSameAs(longRead);
        assertThat(client.clientFor(TaskType.TWIN_SURVEY)).isSameAs(survey);
        // 사업 검증은 FULL(실측 23분)+BM 이라 트윈(900s)으로도 모자란다 — 또 나눈 등급이다.
        assertThat(client.clientFor(TaskType.BUSINESS_VALIDATION)).isSameAs(validation);
        assertThat(client.clientFor(TaskType.IDEA_BRIEF_DERIVATION)).isSameAs(normal);
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.conceptPortfolioReadTimeout()).isEqualTo(Duration.ofMinutes(15));
    }
}
