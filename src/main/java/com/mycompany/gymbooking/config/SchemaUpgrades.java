package com.mycompany.gymbooking.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Database changes that Hibernate's ddl-auto=update can't do by itself.
 *
 * "update" ADDS new tables and new columns, but it never CHANGES an existing column.
 * Before Stage 4, bookings.status was a MySQL enum('ACCEPTED','CANCELLED','EXPIRED','REJECTED','REQUESTED'),
 * so MySQL would refuse the new value 'PAID'. Here we turn it into VARCHAR(20) once.
 *
 * Every step checks first, so running it on every start is harmless (it does nothing the 2nd time).
 * @PostConstruct runs while the app starts, before the server accepts any request.
 * (Real projects use a migration tool like Flyway for this; one step doesn't need it yet.)
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
        // What type is the column now? (no rows = the table doesn't exist yet → Hibernate creates it as VARCHAR)
        List<String> types = jdbc.queryForList("""
                SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?
                """, String.class, table, column);
        if (types.isEmpty() || !types.get(0).equalsIgnoreCase("enum")) {
            return;
        }
        // Table and column names are fixed words from this class (never user input), so building the SQL is safe
        jdbc.execute("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(20) NOT NULL");
        log.info("Database upgraded: {}.{} is now VARCHAR(20) (it was an enum)", table, column);
    }
}
