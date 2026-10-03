package com.mycompany.gymbooking.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema changes that ddl-auto=update can't make, since it never alters existing columns.
 * Older databases have bookings.status as a MySQL ENUM, which rejects newer statuses such as PAID,
 * users.verification_code as 6 characters, too short for the SHA-256 of the code stored now, and
 * the two-factor secrets as 32 characters, too short for their encrypted form.
 * Each step checks the current schema first, so it is safe to run on every startup.
 */
@Component
public class SchemaUpgrades {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrades.class);

    private final JdbcTemplate jdbc;

    public SchemaUpgrades(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void upgrade() {
        enumColumnToVarchar("bookings", "status");
        widenVarchar("users", "verification_code", 64);
        widenVarchar("users", "two_factor_secret", 128);
        widenVarchar("users", "pending_two_factor_secret", 128);
    }

    private void widenVarchar(String table, String column, int length) {
        List<Long> lengths = jdbc.queryForList("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?
                """, Long.class, table, column);
        if (lengths.isEmpty() || lengths.get(0) == null || lengths.get(0) >= length) {
            return;
        }
        // Identifiers are constants from this class, never user input
        jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(" + length + ")");
        if (column.equals("verification_code")) {
            // Codes stored before weren't hashed and can't be checked any more; a new code is needed.
            jdbc.update("UPDATE " + table + " SET " + column + " = NULL");
        }
        log.info("Database upgraded: {}.{} is now VARCHAR({})", table, column, length);
    }

    private void enumColumnToVarchar(String table, String column) {
        // No rows means the table doesn't exist yet; Hibernate will create it as VARCHAR.
        List<String> types = jdbc.queryForList("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?
                """, String.class, table, column);
        if (types.isEmpty() || !types.get(0).equalsIgnoreCase("enum")) {
            return;
        }
        // Identifiers are constants from this class, never user input
        jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(20) NOT NULL");
        log.info("Database upgraded: {}.{} is now VARCHAR(20) (it was an enum)", table, column);
    }
}
