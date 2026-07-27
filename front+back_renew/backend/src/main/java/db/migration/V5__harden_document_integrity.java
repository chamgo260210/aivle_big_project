package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.SQLException;
import java.sql.Statement;

public class V5__harden_document_integrity extends BaseJavaMigration {
    private static final String SECTION_CODES = """
        'BUSINESS_OVERVIEW',
        'MARKET_SIZE',
        'TARGET_CUSTOMER',
        'COMPETITIVE_ANALYSIS',
        'PRODUCT_SERVICE',
        'BUSINESS_MODEL',
        'COST_PROFITABILITY',
        'SALES_GOALS_FINANCIAL_PROJECTIONS',
        'TECHNOLOGY_PRODUCTION',
        'LEGAL_PERMITS',
        'SCHEDULE_RISK',
        'EVIDENCE_LIST'
        """;

    private static final String ITEM_STATUSES = """
        'PRESENT',
        'MISSING',
        'PARTIAL',
        'INVALID',
        'UNKNOWN'
        """;

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            addSemanticConstraints(statement);
            if (isPostgreSql(context)) {
                statement.execute("""
                    CREATE UNIQUE INDEX uk_active_business_plan_per_project
                        ON project_documents(project_id, document_type)
                        WHERE deleted_at IS NULL
                          AND status = 'ACTIVE'
                          AND document_type = 'BUSINESS_PLAN'
                    """);
            }
        }
    }

    private void addSemanticConstraints(Statement statement) throws SQLException {
        statement.execute("""
            ALTER TABLE structured_plan_sections
                ADD CONSTRAINT ck_structured_section_code
                CHECK (section_code IS NULL OR section_code IN (%s))
            """.formatted(SECTION_CODES));
        statement.execute("""
            ALTER TABLE structured_plan_sections
                ADD CONSTRAINT ck_structured_item_status
                CHECK (item_status IS NULL OR item_status IN (%s))
            """.formatted(ITEM_STATUSES));
        statement.execute("""
            ALTER TABLE missing_fields
                ADD CONSTRAINT ck_missing_field_section_code
                CHECK (section_code IS NULL OR section_code IN (%s))
            """.formatted(SECTION_CODES));
    }

    private boolean isPostgreSql(Context context) throws SQLException {
        return context.getConnection()
            .getMetaData()
            .getDatabaseProductName()
            .equalsIgnoreCase("PostgreSQL");
    }
}
