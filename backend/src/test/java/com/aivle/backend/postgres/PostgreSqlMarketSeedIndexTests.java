package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드 유일 색인이 <b>코드가 쓰는 「현재」의 정의</b>와 같은지 본다.
 *
 * <p>2026-08-15 실측 결함. 다듬기를 마치고 「이 컨셉으로 확정하기」를 다시 누르면
 * {@code ConceptPortfolioSelectionMaterializationService} 가 옛 시드를 <b>낡음(stale_at)</b>으로
 * 표시하고 새 시드를 넣는데, V15 의 색인 조건이 {@code deleted_at IS NULL} 뿐이라
 * 낡음 처리된 행이 자리를 계속 차지했다 → {@code uk_market_seed_portfolio_selection} 중복 키로
 * 확정이 실패하고 <b>시장조사를 시작할 수 없었다</b>.
 *
 * <p>조회는 전부 {@code findBy...StaleAtIsNullAndDeletedAtIsNull} 이다. 색인만 그 정의에서
 * 혼자 벗어나 있었다.
 */
@Tag("postgres")
class PostgreSqlMarketSeedIndexTests extends PostgreSqlIntegrationTestSupport {

    @Test
    void marketSeedUniqueIndexTreatsStaleRowsAsFreedSlots() throws Exception {
        String schema = "seedidx_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration")
            .load().migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            String definition = indexDefinition(connection, schema, "uk_market_seed_portfolio_selection");

            assertThat(definition)
                .as("색인이 없다면 이름이 바뀐 것이다 — 조회 조건과 짝을 다시 맞춰야 한다")
                .isNotNull();
            assertThat(definition)
                .as("낡음 처리된 시드가 자리를 계속 차지하면 재확정이 중복 키로 죽는다")
                .contains("stale_at IS NULL");
            assertThat(definition)
                .as("소프트 삭제된 시드도 자리를 비워야 한다")
                .contains("deleted_at IS NULL");
        }
    }

    private String indexDefinition(Connection connection, String schema, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = ?")) {
            statement.setString(1, schema);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
