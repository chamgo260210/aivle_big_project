package com.aivle.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
    void v1AndV2MigrationBytesRemainUnchanged() throws Exception {
        assertThat(hash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("15011e63b8c3e1aeed4da3839660857d3eeBDC93d47aa3f58ca06b27c96d1836".toLowerCase());
        assertThat(hash("db/migration/V2__create_simulation_report_tables.sql"))
            .isEqualTo("a9b13c0830b3ace4f2d166333ce0738fe33ef6b88d02f735e47bb4a028b5fc47");
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
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
