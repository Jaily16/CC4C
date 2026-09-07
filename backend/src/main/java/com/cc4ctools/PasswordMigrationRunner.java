package com.cc4ctools;

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

/**
 * PasswordMigrationRunner 负责离线维护工具的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
final class PasswordMigrationRunner implements ApplicationRunner {
    private static final Pattern DATABASE_NAME =
            Pattern.compile("^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$", Pattern.CASE_INSENSITIVE);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    /**
     * 创建 PasswordMigrationRunner 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param jdbc 调用方提供的 {@code jdbc} 值
     * @param transactionTemplate 调用方提供的 {@code transactionTemplate} 值
     * @param environment 调用方提供的 {@code environment} 值
     * @param context 调用方提供的 {@code context} 值
     */
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

    /**
     * 执行当前组件约定的单次任务，并按既有失败语义向调用方报告结果。
     *
     * @param args 调用方提供的 {@code args} 值
     */
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
        System.out.printf("Password migration completed: users=%d administrators=%d%n", users, administrators);
        context.close();
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param encoder 调用方提供的 {@code encoder} 值
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param encoder 调用方提供的 {@code encoder} 值
     * @return 按当前规则计算或读取的数值
     */
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

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     */
    private void verifyAllPasswordsMigrated() {
        Long userPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user WHERE password IS NULL OR password NOT LIKE '{bcrypt}%'", Long.class);
        Long administratorPlaintext = jdbc.queryForObject(
                "SELECT COUNT(*) FROM administrator "
                        + "WHERE admin_password IS NULL OR admin_password NOT LIKE '{bcrypt}%'",
                Long.class);
        if (userPlaintext == null
                || administratorPlaintext == null
                || userPlaintext != 0
                || administratorPlaintext != 0) {
            throw new IllegalStateException("Password migration verification failed");
        }
    }

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     */
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
                expectedHash.getBytes(StandardCharsets.US_ASCII), actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("The backup SHA-256 does not match");
        }
    }

    /**
     * 编码或保护当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param encoder 调用方提供的 {@code encoder} 值
     * @param password 仅用于当前安全校验的密码或密码摘要
     * @return 按当前协议生成或读取的字符串值
     */
    private String encode(PasswordEncoder encoder, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("A legacy password exceeds the BCrypt byte limit");
        }
        return encoder.encode(password);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param password 仅用于当前安全校验的密码或密码摘要
     */
    private void rejectUnknownEncoding(String password) {
        if (password.startsWith("{")) {
            throw new IllegalStateException("An unsupported password encoding identifier was found");
        }
    }

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param value 待处理或存储的值
     * @return 按当前协议生成或读取的字符串值
     */
    private String requiredPassword(Object value) {
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalStateException("An empty password was found");
        }
        return value.toString();
    }

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param name 调用方提供的 {@code name} 值
     * @return 按当前协议生成或读取的字符串值
     */
    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required migration setting is missing: " + name);
        }
        return value;
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param strength 调用方提供的 {@code strength} 值
     * @return 当前操作产生的 PasswordEncoder 结果
     */
    private PasswordEncoder passwordEncoder(int strength) {
        Map<String, PasswordEncoder> encoders = Map.of("bcrypt", new BCryptPasswordEncoder(strength));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param file 待校验或保存的上传文件
     * @return 按当前协议生成或读取的字符串值
     */
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
