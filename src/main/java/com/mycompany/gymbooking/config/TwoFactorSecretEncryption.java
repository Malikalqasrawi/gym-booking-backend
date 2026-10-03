package com.mycompany.gymbooking.config;

import com.mycompany.gymbooking.security.SecretBox;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Encrypts two-factor secrets saved before they were encrypted. Runs at every startup and only
 * changes values that aren't encrypted yet.
 */
@Component
public class TwoFactorSecretEncryption implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorSecretEncryption.class);

    private final JdbcTemplate jdbc;
    private final SecretBox secretBox;

    public TwoFactorSecretEncryption(JdbcTemplate jdbc, SecretBox secretBox) {
        this.jdbc = jdbc;
        this.secretBox = secretBox;
    }

    @Override
    public void run(String... args) {
        int encrypted = 0;
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT id, two_factor_secret, pending_two_factor_secret FROM users
                WHERE (two_factor_secret IS NOT NULL AND two_factor_secret NOT LIKE 'v1:%')
                   OR (pending_two_factor_secret IS NOT NULL AND pending_two_factor_secret NOT LIKE 'v1:%')
                """)) {
            jdbc.update("UPDATE users SET two_factor_secret = ?, pending_two_factor_secret = ? WHERE id = ?",
                    seal((String) row.get("two_factor_secret")),
                    seal((String) row.get("pending_two_factor_secret")),
                    row.get("id"));
            encrypted++;
        }
        if (encrypted > 0) {
            log.info("Encrypted the two-factor secrets of {} account(s)", encrypted);
        }
    }

    private String seal(String value) {
        return value == null || SecretBox.isSealed(value) ? value : secretBox.seal(value);
    }
}
