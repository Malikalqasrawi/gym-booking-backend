package com.mycompany.gymbooking.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema changes that ddl-auto=update can't make, since it never alters existing columns.
 * Older databases have bookings.status as a MySQL ENUM, which rejects newer statuses such as PAID.
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
