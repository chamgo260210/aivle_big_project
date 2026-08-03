package com.aivle.backend.analysis.financial;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FinancialV21MigrationCompatibilityTests {
    @Test
    void upgradesLegacyFinancialRowWithoutChangingItsIdentityOrResults() throws Exception {
        String database = "financial_v21_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + database
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        migrate(url, "20");
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().executeUpdate("""
                insert into users (
                  id, username, email, password_hash, name, role, status,
                  failed_login_count, security_version, created_at, updated_at, version
                ) values (
                  1, 'legacy-owner', 'legacy@example.com', 'hash', '기존 사용자',
                  'USER', 'ACTIVE', 0, 0, current_timestamp, current_timestamp, 0
                )
                """);
            connection.createStatement().executeUpdate("""
                insert into projects (
                  id, owner_id, title, stage, status, created_at, updated_at, version
                ) values (
                  2, 1, '기존 재무 프로젝트', 'FEASIBILITY', 'ACTIVE',
                  current_timestamp, current_timestamp, 0
                )
                """);
            connection.createStatement().executeUpdate("""
                insert into analysis_jobs (
                  id, project_id, job_type, status, progress, retry_count, attempt_count,
                  created_at, updated_at, version
                ) values (
                  3, 2, 'FEASIBILITY_ANALYSIS', 'SUCCEEDED', 100, 0, 1,
                  current_timestamp, current_timestamp, 0
                )
                """);
            connection.createStatement().executeUpdate("""
                insert into financial_analyses (
                  id, project_id, analysis_job_id, status, currency,
                  analysis_period_months, expected_revenue, expected_cost,
                  break_even_point_months, assumptions_json, result_json,
                  created_at, updated_at, version
                ) values (
                  4, 2, 3, 'COMPLETED', 'KRW', 12, 1200000.00, 800000.00,
                  8, '{"legacy":true}', '{"profit":400000}',
                  current_timestamp, current_timestamp, 0
                )
                """);
        }

        Flyway upgraded = migrate(url, "21");
        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("21");
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            var row = connection.createStatement().executeQuery("""
                select id, project_id, analysis_job_id, expected_revenue, expected_cost,
                       result_json, scenarios_json, source_snapshot_json
                from financial_analyses where id = 4
                """);
            assertThat(row.next()).isTrue();
            assertThat(row.getLong("id")).isEqualTo(4L);
            assertThat(row.getLong("project_id")).isEqualTo(2L);
            assertThat(row.getLong("analysis_job_id")).isEqualTo(3L);
            assertThat(row.getBigDecimal("expected_revenue")).isEqualByComparingTo("1200000");
            assertThat(row.getBigDecimal("expected_cost")).isEqualByComparingTo("800000");
            assertThat(row.getString("result_json")).contains("400000");
            assertThat(row.getString("scenarios_json")).isEqualTo("[]");
            assertThat(row.getString("source_snapshot_json")).isEqualTo("{}");
            assertThat(columnExists(connection, "financial_analyses", "feasibility_assessment_id")).isTrue();
            assertThat(columnExists(connection, "financial_analyses", "version_number")).isTrue();
            assertThat(indexExists(connection, "idx_financial_project_deleted")).isTrue();
            assertThat(indexExists(connection, "idx_financial_project_status_updated")).isTrue();
            assertThat(indexExists(connection, "idx_financial_feasibility")).isTrue();
        }

        Flyway aligned = migrate(url, "22");
        assertThat(aligned.info().current().getVersion().getVersion()).isEqualTo("22");
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            var row = connection.createStatement().executeQuery("""
                select version_number, title, analysis_period_months, assumptions_json
                from financial_analyses where id = 4
                """);
            assertThat(row.next()).isTrue();
            assertThat(row.getInt("version_number")).isEqualTo(1);
            assertThat(row.getString("title")).isEqualTo("기존 재무 분석");
            assertThat(row.getInt("analysis_period_months")).isEqualTo(12);
            assertThat(row.getString("assumptions_json")).contains("legacy");
            assertThat(nullable(connection, "financial_analyses", "version_number")).isFalse();
            assertThat(nullable(connection, "financial_analyses", "title")).isFalse();
            assertThat(nullable(connection, "financial_analyses", "analysis_period_months")).isFalse();
            assertThat(nullable(connection, "financial_analyses", "assumptions_json")).isFalse();
        }
    }

    private Flyway migrate(String url, String target) {
        Flyway flyway = Flyway.configure()
            .dataSource(url, "sa", "")
            .target(target)
            .load();
        flyway.migrate();
        return flyway;
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        var result = connection.createStatement().executeQuery("""
            select count(*) from information_schema.columns
            where lower(table_name) = '%s' and lower(column_name) = '%s'
            """.formatted(table, column));
        result.next();
        return result.getInt(1) == 1;
    }

    private boolean indexExists(Connection connection, String index) throws Exception {
        var result = connection.createStatement().executeQuery("""
            select count(*) from information_schema.indexes
            where lower(index_name) = '%s'
            """.formatted(index));
        result.next();
        return result.getInt(1) == 1;
    }

    private boolean nullable(Connection connection, String table, String column) throws Exception {
        var result = connection.createStatement().executeQuery("""
            select is_nullable from information_schema.columns
            where lower(table_name) = '%s' and lower(column_name) = '%s'
            """.formatted(table, column));
        result.next();
        return "YES".equalsIgnoreCase(result.getString(1));
    }
}
