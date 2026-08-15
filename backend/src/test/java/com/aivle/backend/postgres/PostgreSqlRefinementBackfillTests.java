package com.aivle.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V30 이 <b>옛 라운드만</b> 채우는지 본다.
 *
 * <p>V29 가 「사람이 고른 칸」을 만들기 전에 닫힌 라운드들은 옛 규칙(전량 자동 적용)으로
 * 정말 전부 적용됐는데 칸이 NULL 이라, 화면이 「아직 결정 전」으로 읽어
 * <b>「고쳤으니 다시 조사하세요」 경고를 안 냈다.</b> V30 이 그것을 사실과 맞춘다.
 *
 * <p>⚠ 이 시험의 절반은 <b>안 건드리는 것</b>을 지키는 데 있다. 열린 라운드
 * ({@code legal_outcome IS NULL})까지 채우면 이 판이 세운 「사람이 고른다」는 문을
 * 마이그레이션이 뒤에서 열어 버린다 — 사용자에게 묻지도 않고 전량 적용된 것과 같아진다.
 */
@Tag("postgres")
class PostgreSqlRefinementBackfillTests extends PostgreSqlIntegrationTestSupport {

    private static final String 제안 = """
        [{"fieldKey":"price","afterText":"1팩 9,500원"},
         {"fieldKey":"channels","afterText":"온라인몰 중심"}]""";

    @Test
    void backfillFillsClosedLegacyRoundsAndLeavesOpenOnesForTheUser() throws Exception {
        String schema = "refillbf_" + UUID.randomUUID().toString().replace("-", "");
        migrate(schema, "29");

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO " + schema);
            }
            seedOwnerAndProject(connection);
            insertRound(connection, 1, 1L, "PASSED", 제안);      // 옛 라운드 — 닫혔다
            insertRound(connection, 2, 2L, null, 제안);          // 열린 라운드 — 사람 차례다
            insertRound(connection, 3, 3L, "BLOCKED", "[]");     // 닫혔는데 제안이 0건이었다

            migrate(schema, "30");

            // 순서는 안 본다 — 읽는 쪽(`acceptedOf`)이 집합으로 만든다.
            assertThat(acceptedOf(connection, 1L))
                .as("옛 라운드는 정말 전량 적용됐다 — 그렇게 적어야 「다시 조사」 경고가 산다")
                .contains("\"price\"").contains("\"channels\"");
            assertThat(acceptedOf(connection, 2L))
                .as("열린 라운드를 채우면 사람에게 묻는 문을 마이그레이션이 뒤에서 연다")
                .isNull();
            assertThat(acceptedOf(connection, 3L))
                .as("제안이 0건이던 라운드는 「고른 것 없음」이다 — NULL(미결정)이 아니다")
                .isEqualTo("[]");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .defaultSchema(schema).schemas(schema).locations("classpath:db/migration")
            .target(target).load().migrate();
    }

    /**
     * 이 시험 <b>전용 스키마에서만</b> 라운드의 외래키를 뗀다.
     *
     * <p>라운드는 선택을, 선택은 실행·프로젝트를, 실행은 아이디어 브리프를 문다. 그 사슬을
     * 전부 심으면 이 시험이 <b>남의 테이블 칼럼이 바뀔 때마다 깨진다</b> — 그런데 이 시험이
     * 보는 것은 V30 의 <b>WHERE 절 하나</b>뿐이다. 참조 무결성은 여기서 볼 것이 아니다.
     *
     * <p>⚠ 스키마는 매 실행 새로 만드는 임시본이라 운영 제약에는 손이 닿지 않는다.
     */
    private void seedOwnerAndProject(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                ALTER TABLE concept_refinement_rounds
                    DROP CONSTRAINT IF EXISTS concept_refinement_rounds_project_id_fkey,
                    DROP CONSTRAINT IF EXISTS concept_refinement_rounds_selection_id_fkey""");
        }
    }

    private void insertRound(Connection connection, int round, Long selectionId,
            String legalOutcome, String proposalJson) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO concept_refinement_rounds
                    (project_id, selection_id, round, proposal_json, legal_outcome, created_at)
                VALUES (1, ?, ?, ?, ?, now())""")) {
            statement.setLong(1, selectionId);
            statement.setInt(2, round);
            statement.setString(3, proposalJson);
            statement.setString(4, legalOutcome);
            statement.executeUpdate();
        }
    }

    private String acceptedOf(Connection connection, Long selectionId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT accepted_fields_json FROM concept_refinement_rounds WHERE selection_id = ?")) {
            statement.setLong(1, selectionId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
