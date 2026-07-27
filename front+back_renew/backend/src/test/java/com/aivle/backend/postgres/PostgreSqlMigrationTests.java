package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
class PostgreSqlMigrationTests extends PostgreSqlIntegrationTestSupport {
    @Test
    void v9AddsPersonaCatalogRecommendationAndValidationSchema() throws Exception {
        String schema = "v9_fresh_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = flyway(schema, null);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'legal_review_questions'
                """)).isEqualTo(1);
            assertThat(count(connection, """
                select count(*) from information_schema.columns
                where table_schema = current_schema()
                  and (
                    (table_name = 'analysis_jobs' and column_name = 'source_structured_plan_id')
                    or (table_name = 'legal_reviews' and column_name in
                      ('structured_plan_id', 'input_snapshot_json', 'prompt_hash', 'raw_result_hash'))
                  )
                """)).isEqualTo(5);
            assertThat(count(connection, """
                select count(*) from information_schema.table_constraints
                where constraint_schema = current_schema()
                  and constraint_name in
                    ('uk_legal_review_plan_prompt', 'uk_legal_finding_review_category')
                """)).isEqualTo(2);
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema = current_schema()
                  and table_name in (
                    'feasibility_assessments',
                    'feasibility_dimension_results',
                    'feasibility_validation_tasks'
                  )
                """)).isEqualTo(3);
            assertThat(count(connection, """
                select count(*) from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'analysis_jobs'
                  and column_name = 'source_legal_review_id'
                """)).isEqualTo(1);
            assertThat(count(connection, """
                select count(*) from information_schema.table_constraints
                where constraint_schema = current_schema()
                  and constraint_name in (
                    'uk_feasibility_assessment_input',
                    'uk_feasibility_dimension',
                    'uk_feasibility_task'
                  )
                """)).isEqualTo(3);
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema = current_schema()
                  and table_name in (
                    'baseline_personas',
                    'persona_recommendations',
                    'persona_recommendation_items',
                    'customer_hypotheses',
                    'customer_validation_plans',
                    'persona_validation_task_links'
                  )
                """)).isEqualTo(6);
            assertThat(count(connection, """
                select count(*) from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'analysis_jobs'
                  and column_name = 'source_feasibility_assessment_id'
                """)).isEqualTo(1);
        }
    }

    @Test
    void upgradesValidNullablePhase1RowsFromV4ToV6() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway phase1 = flyway(schema, "4");
        assertThat(phase1.migrate().migrationsExecuted).isEqualTo(4);

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            insertPhase1Rows(connection);
        }

        Flyway phase2 = flyway(schema, "6");
        assertThat(phase2.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(phase2.info().current().getVersion().getVersion()).isEqualTo("6");

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            var result = connection.createStatement().executeQuery("""
                select section_code, item_status
                from structured_plan_sections
                where id = 1
                """);
            assertThat(result.next()).isTrue();
            assertThat(result.getString("section_code")).isNull();
            assertThat(result.getString("item_status")).isNull();
        }
    }

    @Test
    void v6CreatesIntegrityAuthAndAuditSchema() throws Exception {
        String schema = "fresh_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = flyway(schema, "6");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);

        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            assertThat(count(connection, """
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = current_schema()
                  and constraint_name in (
                    'ck_structured_section_code',
                    'ck_structured_item_status',
                    'ck_missing_field_section_code'
                  )
                """)).isEqualTo(3);
            assertThat(count(connection, """
                select count(*)
                from pg_indexes
                where schemaname = current_schema()
                  and indexname = 'uk_active_business_plan_per_project'
                """)).isEqualTo(1);
            assertThat(count(connection, """
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name in ('refresh_tokens', 'audit_events')
                """)).isEqualTo(2);
            assertThat(count(connection, """
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'structured_plans'
                  and column_name = 'confirmed_by_user_id'
                """)).isEqualTo(1);
        }
    }

    @Test
    void upgradesExistingV5RowsToV6WithoutInventingConfirmation()
        throws Exception {
        String schema = "v5_upgrade_"
            + UUID.randomUUID().toString().replace("-", "");
        Flyway v5 = flyway(schema, "5");
        assertThat(v5.migrate().migrationsExecuted).isEqualTo(5);
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            insertPhase1Rows(connection);
        }

        Flyway v6 = flyway(schema, "6");
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(1);
        try (Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )) {
            connection.createStatement().execute("set search_path to " + schema);
            var result = connection.createStatement().executeQuery("""
                select confirmed_at, confirmed_by_user_id
                from structured_plans
                where id = 1
                """);
            assertThat(result.next()).isTrue();
            assertThat(result.getTimestamp("confirmed_at")).isNull();
            assertThat(result.getObject("confirmed_by_user_id")).isNull();
        }
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private int count(Connection connection, String sql) throws Exception {
        var result = connection.createStatement().executeQuery(sql);
        result.next();
        return result.getInt(1);
    }

    private void insertPhase1Rows(Connection connection) throws Exception {
        connection.createStatement().executeUpdate("""
            insert into users (
                id, email, password_hash, name, role, status, failed_login_count,
                created_at, updated_at, version
            ) values (
                1, 'legacy@example.com', 'hash', '기존 사용자', 'USER', 'ACTIVE', 0,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into projects (
                id, owner_id, title, stage, status, created_at, updated_at, version
            ) values (
                1, 1, '기존 프로젝트', 'DOCUMENT', 'ACTIVE',
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into stored_files (
                id, storage_type, storage_key, original_filename, stored_filename,
                extension, mime_type, size_bytes, checksum_sha256, status, encrypted,
                created_at, updated_at, version
            ) values (
                1, 'LOCAL', 'legacy-key', '기존.docx', 'stored.docx', 'docx',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                10, repeat('a', 64), 'ACTIVE', false,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into project_documents (
                id, project_id, document_type, current_version, status,
                created_at, updated_at, version
            ) values (
                1, 1, 'BUSINESS_PLAN', 1, 'ACTIVE',
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into document_versions (
                id, document_id, version_number, stored_file_id, parse_status,
                uploaded_by, uploaded_at, created_at, updated_at, version
            ) values (
                1, 1, 1, 1, 'SUCCEEDED', 1, current_timestamp,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into analysis_jobs (
                id, project_id, job_type, status, progress, retry_count,
                source_document_version_id, attempt_count,
                created_at, updated_at, version
            ) values (
                1, 1, 'DOCUMENT_PARSE', 'SUCCEEDED', 100, 0, 1, 1,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into structured_plans (
                id, project_id, source_document_version_id, version_number,
                status, completion_rate, confirmed_by_user,
                created_at, updated_at, version
            ) values (
                1, 1, 1, 1, 'DRAFT', 100, false,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into structured_plan_sections (
                id, structured_plan_id, section_type, title, section_code,
                item_status, display_order, created_at, updated_at, version
            ) values (
                1, 1, 'OVERVIEW', '기존 nullable 항목', null, null, 1,
                current_timestamp, current_timestamp, 0
            )
            """);
        connection.createStatement().executeUpdate("""
            insert into missing_fields (
                id, structured_plan_id, field_code, label, required, status,
                section_code, created_at, updated_at, version
            ) values (
                1, 1, 'LEGACY_FIELD', '기존 필드', true, 'OPEN', null,
                current_timestamp, current_timestamp, 0
            )
            """);
    }
}
