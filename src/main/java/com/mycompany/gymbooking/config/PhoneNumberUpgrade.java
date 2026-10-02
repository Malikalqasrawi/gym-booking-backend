package com.mycompany.gymbooking.config;

import com.mycompany.gymbooking.phone.PhoneNumbers;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Converts phone numbers saved before the country picker to the international format, e.g.
 * 0791234567 to +962791234567 (numbers without a country code are Jordanian). Numbers that aren't
 * valid are left as they are. Only touches numbers without a "+", so it is safe on every start.
 */
@Component
public class PhoneNumberUpgrade implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PhoneNumberUpgrade.class);

    private final JdbcTemplate jdbc;

    public PhoneNumberUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        int converted = convert("users") + convert("branches");
        if (converted > 0) {
            log.info("Converted {} phone number(s) to the international format", converted);
        }
    }

    private int convert(String table) {
        int converted = 0;
        // Table names are constants from this class, never user input
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id, phone FROM " + table + " WHERE phone IS NOT NULL AND phone <> '' AND phone NOT LIKE '+%'")) {
            Optional<String> international = PhoneNumbers.international((String) row.get("phone"), false);
            if (international.isPresent()) {
                jdbc.update("UPDATE " + table + " SET phone = ? WHERE id = ?", international.get(), row.get("id"));
                converted++;
            }
        }
        return converted;
    }
}
