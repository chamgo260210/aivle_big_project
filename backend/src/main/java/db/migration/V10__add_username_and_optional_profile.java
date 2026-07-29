package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class V10__add_username_and_optional_profile extends BaseJavaMigration {
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "system", "support", "help", "api", "auth", "login", "signup", "user", "users", "project", "projects", "me", "null", "undefined", "ventureverify", "venture-verify");

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN username VARCHAR(30)");
            statement.execute("ALTER TABLE users ADD COLUMN organization_name VARCHAR(120)");
            statement.execute("ALTER TABLE users ADD COLUMN department_name VARCHAR(120)");
            statement.execute("ALTER TABLE users ADD COLUMN job_title VARCHAR(120)");
        }
        backfillUsernames(context);
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE users ALTER COLUMN username SET NOT NULL");
            statement.execute("ALTER TABLE users ALTER COLUMN email DROP NOT NULL");
            statement.execute("CREATE UNIQUE INDEX uk_users_username ON users(username)");
        }
    }

    private void backfillUsernames(Context context) throws Exception {
        Set<String> used = new HashSet<>();
        try (PreparedStatement select = context.getConnection().prepareStatement("SELECT id, email FROM users ORDER BY id"); ResultSet results = select.executeQuery(); PreparedStatement update = context.getConnection().prepareStatement("UPDATE users SET username = ? WHERE id = ?")) {
            while (results.next()) {
                String username = uniqueUsername(results.getString("email"), results.getLong("id"), used);
                update.setString(1, username);
                update.setLong(2, results.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private String uniqueUsername(String email, long id, Set<String> used) {
        String localPart = email == null ? "user" : email.split("@", 2)[0];
        String base = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-").replaceAll("^[^a-z0-9]+", "");
        if (base.length() < 4 || RESERVED.contains(base)) base = "user-" + id;
        base = base.substring(0, Math.min(base.length(), 30));
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            String suffixText = "-" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 30 - suffixText.length())) + suffixText;
        }
        used.add(candidate);
        return candidate;
    }
}
