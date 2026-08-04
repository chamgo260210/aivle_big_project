package com.aivle.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase1CMigrationTests {
    @Autowired JdbcClient jdbcClient;

    @Test
    void v4AddsExecutionControlAndResultUniqueness() {
        assertThat(columnExists("analysis_jobs", "attempt_count")).isTrue();
        assertThat(columnExists("analysis_jobs", "next_attempt_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claimed_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claimed_by")).isTrue();
        assertThat(columnExists("analysis_jobs", "heartbeat_at")).isTrue();
        assertThat(columnExists("analysis_jobs", "claim_token")).isTrue();
        assertThat(columnExists("structured_plan_sections", "display_order")).isTrue();
        assertThat(constraintExists("uk_plan_source_document_version")).isTrue();
    }

    @Test
    void allFourMigrationsApply() {
        Integer count = jdbcClient.sql(
            "select count(*) from flyway_schema_history where success = true"
        ).query(Integer.class).single();
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    void v1V2AndV3ContentsRemainUnchanged() throws Exception {
        assertThat(hash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("596df9a0a264a23a4ebab3c0a9a15268f154653ffd2658c4a941c01ee1f15aef");
        assertThat(hash("db/migration/V2__create_simulation_report_tables.sql"))
            .isEqualTo("7cc523cdf918d0490e3e826633ca0ccb172e9fe9b78d3a3fefffb7f3e6cbe2cb");
        assertThat(hash("db/migration/V3__extend_document_processing_metadata.sql"))
            .isEqualTo("2259c4bbda17bb95c885b81e55c94973a1ba70ec03e6aa5af4b5b000c7a3067e");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcClient.sql("""
            select count(*) from information_schema.columns
            where lower(table_name) = :table and lower(column_name) = :column
            """)
            .param("table", table)
            .param("column", column)
            .query(Integer.class)
            .single();
        return count == 1;
    }

    private boolean constraintExists(String constraint) {
        Integer count = jdbcClient.sql("""
            select count(*) from information_schema.table_constraints
            where lower(constraint_name) = :constraint
            """)
            .param("constraint", constraint)
            .query(Integer.class)
            .single();
        return count == 1;
    }

    private String hash(String path) throws Exception {
        byte[] bytes = new ClassPathResource(path).getInputStream().readAllBytes();
        byte[] normalized = new String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(normalized)
        );
    }
}
