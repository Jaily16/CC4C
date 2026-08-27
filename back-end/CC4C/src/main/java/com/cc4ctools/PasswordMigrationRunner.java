package com.cc4ctools;

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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
final class PasswordMigrationRunner implements ApplicationRunner {
    private static final Pattern DATABASE_NAME = Pattern.compile(
            "^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    PasswordMigrationRunner(
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
        validateBackupAndDatabase();
        int strength = Integer.parseInt(environment.getProperty("CC4C_BCRYPT_STRENGTH", "12"));
        if (strength < 4 || strength > 16) {
            throw new IllegalStateException("CC4C_BCRYPT_STRENGTH must be between 4 and 16");
        }
        PasswordEncoder encoder = passwordEncoder(strength);

        long users = migrateNumericTable(encoder);
        long administrators = migrateAdministratorTable(encoder);
        verifyAllPasswordsMigrated();
        System.out.printf(
                "Password migration completed: users=%d administrators=%d%n",
                users,
                administrators);
        context.close();
    }

    private long migrateNumericTable(PasswordEncoder encoder) {
        long migrated = 0;
        long lastId = Long.MIN_VALUE;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT user_id, password FROM user WHERE user_id > ? ORDER BY user_id LIMIT ?",
                    lastId,
                    BATCH_SIZE);
            if (rows.isEmpty()) {
                return migrated;
            }
            transactionTemplate.executeWithoutResult(status -> rows.forEach(row -> {
                long id = ((Number) row.get("user_id")).longValue();
                String password = requiredPassword(row.get("password"));
                if (!password.startsWith("{bcrypt}")) {
                    rejectUnknownEncoding(password);
                    jdbc.update("UPDATE user SET password = ? WHERE user_id = ?", encode(encoder, password), id);
                }
            }));
            migrated += rows.stream()
                    .map(row -> requiredPassword(row.get("password")))
                    .filter(password -> !password.startsWith("{bcrypt}"))
                    .count();
            lastId = ((Number) rows.getLast().get("user_id")).longValue();
        }
    }

    private long migrateAdministratorTable(PasswordEncoder encoder) {
        long migrated = 0;
        String lastId = "";
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT admin_id, admin_password FROM administrator "
                            + "WHERE admin_id > ? ORDER BY admin_id LIMIT ?",
                    lastId,
                    BATCH_SIZE);
            if (rows.isEmpty()) {
                return migrated;
            }
            transactionTemplate.executeWithoutResult(status -> rows.forEach(row -> {
                String id = row.get("admin_id").toString();
                String password = requiredPassword(row.get("admin_password"));
                if (!password.startsWith("{bcrypt}")) {
                    rejectUnknownEncoding(password);
                    jdbc.update(
                            "UPDATE administrator SET admin_password = ? WHERE admin_id = ?",
                            encode(encoder, password),
                            id);
                }
            }));
            migrated += rows.stream()
                    .map(row -> requiredPassword(row.get("admin_password")))
                    .filter(password -> !password.startsWith("{bcrypt}"))
                    .count();
            lastId = rows.getLast().get("admin_id").toString();
        }
    }

    private void verifyAllPasswordsMigrated() {
        Long userPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE password IS NULL OR password NOT LIKE '{bcrypt}%'",
                Long.class);
        Long administratorPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM administrator "
                        + "WHERE admin_password IS NULL OR admin_password NOT LIKE '{bcrypt}%'",
                Long.class);
        if (userPlaintext == null || administratorPlaintext == null
                || userPlaintext != 0 || administratorPlaintext != 0) {
            throw new IllegalStateException("Password migration verification failed");
        }
    }

    private void validateBackupAndDatabase() {
        String jdbcUrl = required("spring.datasource.url");
        Matcher matcher = DATABASE_NAME.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalStateException("The datasource URL must contain an explicit MySQL database name");
        }
        String actualDatabase = matcher.group("database");
        String confirmedDatabase = required("CC4C_PASSWORD_MIGRATION_CONFIRM_DATABASE");
        if (!actualDatabase.equalsIgnoreCase(confirmedDatabase)) {
            throw new IllegalStateException("The confirmed database does not match the datasource URL");
        }

        Path backup = Path.of(required("CC4C_PASSWORD_MIGRATION_BACKUP_PATH"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(backup)) {
            throw new IllegalStateException("The confirmed backup file does not exist");
        }
        String expectedHash = required("CC4C_PASSWORD_MIGRATION_BACKUP_SHA256").toLowerCase();
        String actualHash = sha256(backup);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("The backup SHA-256 does not match");
        }
    }

    private String encode(PasswordEncoder encoder, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("A legacy password exceeds the BCrypt byte limit");
        }
        return encoder.encode(password);
    }

    private void rejectUnknownEncoding(String password) {
        if (password.startsWith("{")) {
            throw new IllegalStateException("An unsupported password encoding identifier was found");
        }
    }

    private String requiredPassword(Object value) {
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalStateException("An empty password was found");
        }
        return value.toString();
    }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required migration setting is missing: " + name);
        }
        return value;
    }

    private PasswordEncoder passwordEncoder(int strength) {
        Map<String, PasswordEncoder> encoders =
                Map.of("bcrypt", new BCryptPasswordEncoder(strength));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to verify the database backup", exception);
        }
    }
}
