package com.aivle.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase3MigrationTests {
    @Autowired JdbcClient jdbcClient;

    @Test
    void freshH2SchemaAppliesV1ThroughV9AndValidatesNewTables() {
        assertThat(jdbcClient.sql("""
            select version from flyway_schema_history
            where success = true and version is not null
            order by installed_rank desc
            limit 1
            """).query(String.class).single()).isEqualTo("9");
        assertThat(tableExists("refresh_tokens")).isTrue();
        assertThat(tableExists("audit_events")).isTrue();
        assertThat(columnExists(
            "structured_plans",
            "confirmed_by_user_id"
        )).isTrue();
        assertThat(tableExists("legal_review_questions")).isTrue();
        assertThat(columnExists("analysis_jobs", "source_structured_plan_id")).isTrue();
        assertThat(columnExists("analysis_jobs", "source_legal_review_id")).isTrue();
        assertThat(tableExists("feasibility_assessments")).isTrue();
        assertThat(tableExists("feasibility_dimension_results")).isTrue();
        assertThat(tableExists("feasibility_validation_tasks")).isTrue();
        assertThat(columnExists("analysis_jobs", "source_feasibility_assessment_id")).isTrue();
        assertThat(tableExists("baseline_personas")).isTrue();
        assertThat(tableExists("persona_recommendations")).isTrue();
        assertThat(tableExists("persona_recommendation_items")).isTrue();
        assertThat(tableExists("customer_hypotheses")).isTrue();
        assertThat(tableExists("customer_validation_plans")).isTrue();
        assertThat(tableExists("persona_validation_task_links")).isTrue();
    }

    @Test
    void v1ThroughV8MigrationBytesRemainUnchanged() throws Exception {
        assertThat(classpathHash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("15011e63b8c3e1aeed4da3839660857d3eebdc93d47aa3f58ca06b27c96d1836");
        assertThat(classpathHash(
            "db/migration/V2__create_simulation_report_tables.sql"
        )).isEqualTo(
            "a9b13c0830b3ace4f2d166333ce0738fe33ef6b88d02f735e47bb4a028b5fc47"
        );
        assertThat(classpathHash(
            "db/migration/V3__extend_document_processing_metadata.sql"
        )).isEqualTo(
            "2259c4bbda17bb95c885b81e55c94973a1ba70ec03e6aa5af4b5b000c7a3067e"
        );
        assertThat(classpathHash(
            "db/migration/V4__add_job_execution_control.sql"
        )).isEqualTo(
            "79d79eb2967961078c0834ba4a0d4ad4cf233b690779a3cee3aa842ad53685e9"
        );
        assertThat(fileHash(Path.of(
            "src/main/java/db/migration/V5__harden_document_integrity.java"
        ))).isEqualTo(
            "fc16f5ec284e0d204ad58b16e109e916fb5e68899fc6ac59694de612c86d27d8"
        );
        assertThat(classpathHash("db/migration/V6__add_auth_confirmation_audit.sql"))
            .isEqualTo("ab4682af6b5ddb65daf7e4f634d08b57519944dd3c656a5021a823a2799100fa");
        assertThat(classpathHash("db/migration/V7__add_legal_review_vertical_slice.sql"))
            .isEqualTo("e0c15e713e4eb8caa080bb8a473d44aa3f60f4e426f2e39a36c9f45d759c1968");
        assertThat(classpathHash(
            "db/migration/V8__add_feasibility_assessment_vertical_slice.sql"))
            .isEqualTo("bbaed5319c6d13ec5ca1ef025a99818c6f04c344e1cf10aaca9bb64bd07945de");
    }

    private boolean tableExists(String table) {
        return jdbcClient.sql("""
            select count(*) from information_schema.tables
            where lower(table_name) = :table
            """).param("table", table).query(Integer.class).single() == 1;
    }

    private boolean columnExists(String table, String column) {
        return jdbcClient.sql("""
            select count(*) from information_schema.columns
            where lower(table_name) = :table
              and lower(column_name) = :column
            """)
            .param("table", table)
            .param("column", column)
            .query(Integer.class)
            .single() == 1;
    }

    private String classpathHash(String path) throws Exception {
        return hash(new ClassPathResource(path).getInputStream().readAllBytes());
    }

    private String fileHash(Path path) throws Exception {
        return hash(Files.readAllBytes(path));
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }
}
