package com.cc4c.identity.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cc4c.security",
        name = "password-readiness-enabled",
        havingValue = "true")
final class PasswordReadinessVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    PasswordReadinessVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long userPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE password IS NULL OR password NOT LIKE '{bcrypt}%'",
                Long.class);
        Long administratorPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM administrator "
                        + "WHERE admin_password IS NULL OR admin_password NOT LIKE '{bcrypt}%'",
                Long.class);
        if (userPlaintext == null || administratorPlaintext == null
                || userPlaintext != 0 || administratorPlaintext != 0) {
            throw new IllegalStateException(
                    "The database contains passwords that have not completed the offline BCrypt migration");
        }
    }
}
