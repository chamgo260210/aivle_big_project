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
class Phase1BMigrationTests {
    @Autowired JdbcClient jdbcClient;

    @Test
    void v3AddsDocumentProcessingColumns() {
        assertThat(columnExists("analysis_jobs", "idempotency_key")).isTrue();
        assertThat(columnExists("analysis_jobs", "request_fingerprint")).isTrue();
        assertThat(columnExists("analysis_jobs", "source_document_version_id")).isTrue();
        assertThat(columnExists("document_versions", "parse_metadata_json")).isTrue();
        assertThat(columnExists("structured_plans", "raw_result_hash")).isTrue();
        assertThat(columnExists("structured_plan_sections", "section_code")).isTrue();
        assertThat(columnExists("missing_fields", "priority")).isTrue();
    }

    @Test
    void v3IdempotencyConstraintExists() {
        Integer count = jdbcClient.sql("""
            select count(*)
            from information_schema.table_constraints
            where lower(table_name) = 'analysis_jobs'
              and lower(constraint_name) = 'uk_job_idempotency'
              and constraint_type = 'UNIQUE'
            """).query(Integer.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void v1AndV2MigrationContentsRemainUnchanged() throws Exception {
        assertThat(hash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("596df9a0a264a23a4ebab3c0a9a15268f154653ffd2658c4a941c01ee1f15aef");
        assertThat(hash("db/migration/V2__create_simulation_report_tables.sql"))
            .isEqualTo("7cc523cdf918d0490e3e826633ca0ccb172e9fe9b78d3a3fefffb7f3e6cbe2cb");
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcClient.sql("""
            select count(*)
            from information_schema.columns
            where lower(table_name) = :table
              and lower(column_name) = :column
            """)
            .param("table", table)
            .param("column", column)
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
