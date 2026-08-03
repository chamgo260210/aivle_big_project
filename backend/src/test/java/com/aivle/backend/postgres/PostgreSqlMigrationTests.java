package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("postgres")
class PostgreSqlMigrationTests extends PostgreSqlIntegrationTestSupport {
    private static final String V25_USERNAME = "legacyv25user";

    @Test
    void v27CreatesTargetTaskRunFoundationWithoutTaskArtifacts()
        throws Exception {
        String schema = "v27_taskrun_"
            + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = flyway(schema, "27");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(27);
        assertThat(flyway.validateWithResult().validationSuccessful)
            .isTrue();

        try (Connection connection = connection(schema)) {
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema = current_schema()
                  and table_name in ('task_runs', 'task_attempts', 'task_results')
                """)).isEqualTo(3);
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'task_artifacts'
                """)).isZero();
            assertThat(count(connection, """
                select count(*) from information_schema.table_constraints
                where table_schema = current_schema()
                  and constraint_name in ('uk_task_runs_idempotency', 'uk_task_attempt_number')
                """)).isEqualTo(2);
        }
    }

    @Test
    void upgradesV26ToV27WithoutChangingLegacyRows() throws Exception {
        String schema = "v27_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway v26 = flyway(schema, "26");
        assertThat(v26.migrate().migrationsExecuted).isEqualTo(26);
        try (Connection connection = connection(schema)) {
            insertV25Rows(connection);
        }

        Flyway v27 = flyway(schema, "27");
        assertThat(v27.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v27.validateWithResult().validationSuccessful).isTrue();
        assertThat(v27.info().current().getVersion().getVersion()).isEqualTo("27");

        try (Connection connection = connection(schema)) {
            assertThat(count(connection, "select count(*) from users where username='" + V25_USERNAME + "'"))
                .isEqualTo(1);
            assertThat(count(connection, "select count(*) from projects where id=1 and owner_id=1"))
                .isEqualTo(1);
            assertThat(count(connection, "select count(*) from document_versions where id=1"))
                .isEqualTo(1);
            assertThat(count(connection, "select count(*) from analysis_jobs where id=1"))
                .isEqualTo(1);
            assertThat(count(connection, """
                select count(*) from information_schema.tables
                where table_schema=current_schema()
                  and table_name in ('task_runs','task_attempts','task_results')
                """)).isEqualTo(3);
        }
    }

    @Test
    void upgradesLegacyLocalDocumentFromV25ToV26WithoutDataLoss()
        throws Exception {
        String schema = "v26_upgrade_"
            + UUID.randomUUID().toString().replace("-", "");
        Flyway v25 = flyway(schema, "25");
        assertThat(v25.migrate().migrationsExecuted).isEqualTo(25);

        try (Connection connection = connection(schema)) {
            insertV25Rows(connection);
        }

        Flyway v26 = flyway(schema, "26");
        assertThat(v26.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v26.info().current().getVersion().getVersion())
            .isEqualTo("26");

        try (Connection connection = connection(schema)) {
            var result = connection.createStatement().executeQuery("""
                select u.username,
                       u.email,
                       p.title,
                       pd.current_version,
                       sf.original_filename,
                       sf.storage_type,
                       dv.parser_artifact_stored_file_id,
                       dv.parser_block_count,
                       dv.parser_artifact_checksum_sha256,
                       dv.parser_artifact_schema_version,
                       dv.parsed_at
                from document_versions dv
                join project_documents pd on pd.id = dv.document_id
                join projects p on p.id = pd.project_id
                join users u on u.id = p.owner_id
                join stored_files sf on sf.id = dv.stored_file_id
                where dv.id = 1
                """);
            assertThat(result.next()).isTrue();
            assertThat(result.getString("username"))
                .isEqualTo(V25_USERNAME);
            assertThat(result.getString("email"))
                .isEqualTo("legacy@example.com");
            assertThat(result.getString("title"))
                .isEqualTo("기존 프로젝트");
            assertThat(result.getInt("current_version"))
                .isEqualTo(1);
            assertThat(result.getString("original_filename"))
                .isEqualTo("기존.docx");
            assertThat(result.getString("storage_type"))
                .isEqualTo("LOCAL");
            assertThat(result.getObject(
                "parser_artifact_stored_file_id"
            )).isNull();
            assertThat(result.getObject("parser_block_count"))
                .isNull();
            assertThat(result.getString(
                "parser_artifact_checksum_sha256"
            )).isNull();
            assertThat(result.getString(
                "parser_artifact_schema_version"
            )).isNull();
            assertThat(result.getTimestamp("parsed_at")).isNull();
            assertThat(count(connection, """
                select count(*) from stored_files where id = 1
                """)).isEqualTo(1);
            assertThat(count(connection, """
                select count(*) from document_versions where id = 1
                """)).isEqualTo(1);
            System.out.println(
                "D2_PG_UPGRADE from=25 to=26 migrationsExecuted=1 "
                    + "legacyLocalRow=PASS nullableMetadata=PASS "
                    + "dataPreserved=PASS"
            );
        }
    }

    @Test
    void v26EnforcesParserArtifactMetadataConstraints()
        throws Exception {
        String schema = "v26_constraints_"
            + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = flyway(schema, "26");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(26);

        try (Connection connection = connection(schema)) {
            insertV25Rows(connection);
            insertStoredFile(connection, 2, "artifact-2");

            assertThat(connection.createStatement().executeUpdate("""
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_artifact_status = 'SUCCEEDED',
                    parser_block_count = 12,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 1
                """)).isEqualTo(1);

            assertThat(connection.createStatement().executeUpdate("""
                update document_versions
                set parser_artifact_stored_file_id = null,
                    parser_artifact_status = null,
                    parser_block_count = null,
                    parser_artifact_checksum_sha256 = null,
                    parser_artifact_schema_version = null,
                    parsed_at = null
                where id = 1
                """)).isEqualTo(1);

            assertSqlRejected(connection, "23514", """
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_block_count = 1,
                    parser_artifact_checksum_sha256 = 'INVALID',
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 1
                """);
            assertSqlRejected(connection, "23514", """
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_block_count = 1,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = null,
                    parsed_at = current_timestamp
                where id = 1
                """);
            assertSqlRejected(connection, "23503", """
                update document_versions
                set parser_artifact_stored_file_id = 999999,
                    parser_block_count = 1,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 1
                """);
            assertSqlRejected(connection, "23514", """
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_block_count = 0,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 1
                """);

            insertStoredFile(connection, 3, "source-3");
            insertDocumentVersion(connection, 2, 3, 2);
            connection.createStatement().executeUpdate("""
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_block_count = 1,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 1
                """);
            assertSqlRejected(connection, "23505", """
                update document_versions
                set parser_artifact_stored_file_id = 2,
                    parser_block_count = 1,
                    parser_artifact_checksum_sha256 = repeat('b', 64),
                    parser_artifact_schema_version = 'document-blocks-v1',
                    parsed_at = current_timestamp
                where id = 2
                """);
            System.out.println(
                "D2_PG_CONSTRAINTS validMetadata=PASS "
                    + "nullableLegacy=PASS malformedChecksum=PASS "
                    + "partialMetadata=PASS missingStoredFileFk=PASS "
                    + "duplicateArtifactFk=PASS nonPositiveBlockCount=PASS"
            );
        }
    }

    @Test
    void v9AddsPersonaCatalogRecommendationAndValidationSchema() throws Exception {
        String schema = "v9_fresh_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = flyway(schema, "9");
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
            insertPreUsernameRows(connection);
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
            insertPreUsernameRows(connection);
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

    private Connection connection(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        connection.createStatement().execute(
            "set search_path to " + schema
        );
        return connection;
    }

    private void assertSqlRejected(
        Connection connection,
        String sqlState,
        String sql
    ) {
        assertThatThrownBy(
            () -> connection.createStatement().executeUpdate(sql)
        ).isInstanceOfSatisfying(
            java.sql.SQLException.class,
            exception -> assertThat(exception.getSQLState())
                .isEqualTo(sqlState)
        );
    }

    private void insertStoredFile(
        Connection connection,
        long id,
        String storageKey
    ) throws Exception {
        connection.createStatement().executeUpdate("""
            insert into stored_files (
                id, storage_type, storage_key, original_filename,
                stored_filename, extension, mime_type, size_bytes,
                checksum_sha256, status, encrypted, created_at,
                updated_at, version
            ) values (
                %d, 'S3_COMPATIBLE', '%s', 'artifact.json',
                'artifact.json', 'json', 'application/json', 10,
                repeat('b', 64), 'AVAILABLE', false,
                current_timestamp, current_timestamp, 0
            )
            """.formatted(id, storageKey));
    }

    private void insertDocumentVersion(
        Connection connection,
        long id,
        long storedFileId,
        int versionNumber
    ) throws Exception {
        connection.createStatement().executeUpdate("""
            insert into document_versions (
                id, document_id, version_number, stored_file_id,
                parse_status, uploaded_by, uploaded_at, created_at,
                updated_at, version
            ) values (
                %d, 1, %d, %d, 'SUCCEEDED', 1, current_timestamp,
                current_timestamp, current_timestamp, 0
            )
            """.formatted(id, versionNumber, storedFileId));
    }

    private void insertPreUsernameRows(Connection connection) throws Exception {
        connection.createStatement().executeUpdate("""
            insert into users (
                id, email, password_hash, name, role, status, failed_login_count,
                created_at, updated_at, version
            ) values (
                1, 'legacy@example.com', 'hash', '기존 사용자', 'USER', 'ACTIVE', 0,
                current_timestamp, current_timestamp, 0
            )
            """);
        insertCommonPhase1Rows(connection);
    }

    private void insertV25Rows(Connection connection) throws Exception {
        connection.createStatement().executeUpdate("""
            insert into users (
                id, username, email, password_hash, name, role, status,
                failed_login_count, created_at, updated_at, version
            ) values (
                1, '%s', 'legacy@example.com', 'hash', '기존 사용자',
                'USER', 'ACTIVE', 0, current_timestamp, current_timestamp, 0
            )
            """.formatted(V25_USERNAME));
        insertCommonPhase1Rows(connection);
    }

    private void insertCommonPhase1Rows(Connection connection) throws Exception {
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
