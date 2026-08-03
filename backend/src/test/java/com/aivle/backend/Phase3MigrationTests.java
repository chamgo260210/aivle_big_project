package com.aivle.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase3MigrationTests {
    @Autowired JdbcClient jdbcClient;
    @Autowired DataSource dataSource;

    @Test
    void freshH2SchemaAppliesThroughV27AndValidatesOperationalTables() throws Exception {
        assertThat(jdbcClient.sql("""
            select version from flyway_schema_history
            where success = true and version is not null
            order by installed_rank desc
            limit 1
            """).query(String.class).single()).isEqualTo("27");
        assertThat(jdbcClient.sql("""
            select version from flyway_schema_history
            where success = true and version in ('13', '14', '15', '16', '17')
            order by installed_rank
            """).query(String.class).list())
            .containsExactly("13", "14", "15", "16", "17");
        assertThat(columnExists("users", "username")).isTrue();
        assertThat(columnExists("users", "organization_name")).isTrue();
        assertThat(columnExists("users", "department_name")).isTrue();
        assertThat(columnExists("users", "job_title")).isTrue();
        assertThat(tableExists("refresh_tokens")).isTrue();
        assertThat(tableExists("task_runs")).isTrue();
        assertThat(tableExists("task_attempts")).isTrue();
        assertThat(tableExists("task_results")).isTrue();
        assertThat(tableExists("task_artifacts")).isFalse();
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
        assertThat(tableExists("admin_action_tokens")).isTrue();
        assertThat(columnExists("users", "disabled_reason")).isTrue();
        assertThat(columnExists("audit_events", "before_json")).isTrue();
        assertThat(columnExists("audit_events", "after_json")).isTrue();
        assertThat(tableExists("cluster_persona_policies")).isTrue();
        assertThat(tableExists("project_persona_selections")).isTrue();
        assertThat(importedKeyExists(
            "cluster_persona_policies",
            "baseline_persona_id",
            "baseline_personas"
        )).isTrue();
        assertThat(importedKeyExists(
            "project_persona_selections",
            "project_id",
            "projects"
        )).isTrue();
        assertThat(importedKeyExists(
            "project_persona_selections",
            "baseline_persona_id",
            "baseline_personas"
        )).isTrue();
        assertThat(uniqueIndexExists(
            "project_persona_selections",
            "project_id"
        )).isTrue();
        assertThat(countRows("baseline_personas")).isEqualTo(56);
        assertThat(countRows("cluster_persona_policies")).isZero();
        assertThat(countRows("project_persona_selections")).isZero();
        assertThat(tableExists("marketing_contents")).isTrue();
        assertThat(tableExists("marketing_content_versions")).isTrue();
        assertThat(importedKeyExists(
            "marketing_contents",
            "project_id",
            "projects"
        )).isTrue();
        assertThat(importedKeyExists(
            "marketing_contents",
            "selected_persona_id",
            "baseline_personas"
        )).isTrue();
        assertThat(uniqueIndexExists(
            "marketing_content_versions",
            "marketing_content_id"
        )).isTrue();
        assertThat(countRows("marketing_contents")).isZero();
        assertThat(countRows("marketing_content_versions")).isZero();
        assertThat(tableExists("persona_panel_interviews")).isTrue();
        assertThat(tableExists("market_response_predictions")).isTrue();
        assertThat(importedKeyExists(
            "persona_panel_interviews",
            "project_id",
            "projects"
        )).isTrue();
        assertThat(importedKeyExists(
            "market_response_predictions",
            "panel_interview_id",
            "persona_panel_interviews"
        )).isTrue();
        assertThat(countRows("persona_panel_interviews")).isZero();
        assertThat(countRows("market_response_predictions")).isZero();
        assertThat(columnExists("marketing_contents", "panel_interview_id")).isTrue();
        assertThat(columnExists("marketing_contents", "market_response_id")).isTrue();
        assertThat(columnExists("marketing_contents", "source_snapshot_version")).isTrue();
        assertThat(columnExists("marketing_content_versions", "source_changed")).isTrue();
        assertThat(importedKeyExists(
            "marketing_contents",
            "panel_interview_id",
            "persona_panel_interviews"
        )).isTrue();
        assertThat(columnExists(
            "analysis_jobs",
            "rerun_of_job_id"
        )).isTrue();
        assertThat(tableExists("ai_task_results")).isTrue();
        assertThat(importedKeyExists(
            "ai_task_results",
            "analysis_job_id",
            "analysis_jobs"
        )).isTrue();
        assertThat(tableExists("ai_task_artifacts")).isTrue();
        assertThat(importedKeyExists(
            "ai_task_artifacts",
            "stored_file_id",
            "stored_files"
        )).isTrue();
        assertThat(importedKeyExists(
            "ai_task_artifacts",
            "analysis_job_id",
            "analysis_jobs"
        )).isTrue();
        assertThat(columnExists(
            "document_versions",
            "parser_artifact_stored_file_id"
        )).isTrue();
        assertThat(columnExists(
            "document_versions",
            "parser_artifact_status"
        )).isTrue();
        assertThat(columnExists(
            "document_versions",
            "parser_block_count"
        )).isTrue();
        assertThat(importedKeyExists(
            "document_versions",
            "parser_artifact_stored_file_id",
            "stored_files"
        )).isTrue();
    }

    @Test
    void v1ThroughV8MigrationContentsRemainUnchanged() throws Exception {
        assertThat(classpathHash("db/migration/V1__create_core_tables.sql"))
            .isEqualTo("596df9a0a264a23a4ebab3c0a9a15268f154653ffd2658c4a941c01ee1f15aef");
        assertThat(classpathHash(
            "db/migration/V2__create_simulation_report_tables.sql"
        )).isEqualTo(
            "7cc523cdf918d0490e3e826633ca0ccb172e9fe9b78d3a3fefffb7f3e6cbe2cb"
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

    private int countRows(String table) {
        return jdbcClient.sql("select count(*) from " + table)
            .query(Integer.class)
            .single();
    }

    private boolean importedKeyExists(
        String table,
        String foreignKeyColumn,
        String primaryKeyTable
    ) throws SQLException {
        try (
            var connection = dataSource.getConnection();
            ResultSet keys = connection.getMetaData().getImportedKeys(
                connection.getCatalog(),
                connection.getSchema(),
                table
            )
        ) {
            while (keys.next()) {
                if (foreignKeyColumn.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                    && primaryKeyTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean uniqueIndexExists(String table, String column) throws SQLException {
        try (
            var connection = dataSource.getConnection();
            ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(),
                connection.getSchema(),
                table,
                true,
                false
            )
        ) {
            while (indexes.next()) {
                if (column.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private String classpathHash(String path) throws Exception {
        return hash(new ClassPathResource(path).getInputStream().readAllBytes());
    }

    private String fileHash(Path path) throws Exception {
        return hash(Files.readAllBytes(path));
    }

    private String hash(byte[] bytes) throws Exception {
        byte[] normalized = new String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(normalized)
        );
    }
}
