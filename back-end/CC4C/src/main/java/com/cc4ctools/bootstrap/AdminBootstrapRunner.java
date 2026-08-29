package com.cc4ctools.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
final class AdminBootstrapRunner implements ApplicationRunner {
    private static final Pattern DATABASE_NAME = Pattern.compile(
            "^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADMIN_ID = Pattern.compile("^\\d{7}$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    AdminBootstrapRunner(
            JdbcTemplate jdbc,
            TransactionTemplate transactionTemplate,
            Environment environment,
            ConfigurableApplicationContext context) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String adminId = required("CC4C_ADMIN_BOOTSTRAP_ID");
            if (!ADMIN_ID.matcher(adminId).matches()) {
                throw new IllegalStateException("The administrator ID must contain exactly seven digits");
            }
            verifyDatabaseConfirmation();
            String password = readPassword();
            int strength = Integer.parseInt(environment.getProperty("CC4C_BCRYPT_STRENGTH", "12"));
            if (strength < 4 || strength > 16) {
                throw new IllegalStateException("CC4C_BCRYPT_STRENGTH must be between 4 and 16");
            }
            PasswordEncoder encoder = passwordEncoder(strength);
            boolean created = Boolean.TRUE.equals(transactionTemplate.execute(
                    status -> createOrVerify(adminId, password, encoder)));
            System.out.println(created
                    ? "Administrator bootstrap completed"
                    : "Administrator bootstrap already satisfied");
        } finally {
            context.close();
        }
    }

    private boolean createOrVerify(String adminId, String password, PasswordEncoder encoder) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT admin_id, admin_password, deleted FROM administrator ORDER BY admin_id FOR UPDATE");
        Map<String, Object> sameId = rows.stream()
                .filter(row -> adminId.equals(row.get("admin_id").toString()))
                .findFirst()
                .orElse(null);
        long activeCount = rows.stream()
                .filter(row -> ((Number) row.get("deleted")).intValue() == 0)
                .count();

        if (sameId != null) {
            boolean active = ((Number) sameId.get("deleted")).intValue() == 0;
            boolean onlyActiveAdministrator = active && activeCount == 1;
            String storedPassword = sameId.get("admin_password").toString();
            if (onlyActiveAdministrator && encoder.matches(password, storedPassword)) {
                return false;
            }
            throw new IllegalStateException("The requested administrator ID is already in use");
        }
        if (activeCount > 0) {
            throw new IllegalStateException("An active administrator already exists");
        }

        int inserted = jdbc.update(
                "INSERT INTO administrator(admin_id, admin_password, deleted) VALUES(?, ?, 0)",
                adminId,
                encoder.encode(password));
        if (inserted != 1) {
            throw new IllegalStateException("Administrator bootstrap did not insert exactly one row");
        }
        return true;
    }

    private void verifyDatabaseConfirmation() {
        String jdbcUrl = required("spring.datasource.url");
        Matcher matcher = DATABASE_NAME.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalStateException("The datasource URL must contain an explicit MySQL database");
        }
        String confirmation = required("CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE");
        if (!matcher.group("database").equals(confirmation)) {
            throw new IllegalStateException("The confirmed database does not match the datasource URL");
        }
    }

    private String readPassword() {
        Path passwordFile = Path.of(required("CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(passwordFile)) {
            throw new IllegalStateException("The administrator password file does not exist");
        }
        try {
            String password = Files.readString(passwordFile, StandardCharsets.UTF_8)
                    .replaceFirst("[\\r\\n]+$", "");
            int characters = password.codePointCount(0, password.length());
            int bytes = password.getBytes(StandardCharsets.UTF_8).length;
            if (characters < 8 || characters > 64 || bytes > 72) {
                throw new IllegalStateException(
                        "The administrator password must contain 8-64 characters and at most 72 UTF-8 bytes");
            }
            return password;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the administrator password file", exception);
        }
    }

    private PasswordEncoder passwordEncoder(int strength) {
        Map<String, PasswordEncoder> encoders =
                Map.of("bcrypt", new BCryptPasswordEncoder(strength));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required administrator bootstrap setting is missing: " + name);
        }
        return value;
    }
}
