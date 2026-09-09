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

/** 验证目标数据库与备份哈希后，分批迁移用户和管理员密码；每批独立提交。 */
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
     * 保存密码表访问、事务、迁移设置及结束时关闭的工具上下文。
     *
     * @param jdbc 密码或管理员表的 JDBC 访问器
     * @param transactionTemplate 控制单批迁移或引导写入的事务模板
     * @param environment 读取显式维护设置的 Spring 环境
     * @param context 维护工具的 Spring 上下文
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
     * 先验证数据库和备份，再迁移两张表并检查编码前缀；全部成功后输出计数并关闭上下文。
     *
     * @param args 命令行参数；本入口不自行解析
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
     * 按递增用户 ID 每批读取 100 行，在批次事务中迁移非 BCrypt 密码。
     *
     * @param encoder 写入 BCrypt 摘要的密码编码器
     * @return 本次迁移的用户密码数量
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
     * 按递增管理员 ID 每批读取 100 行，在批次事务中迁移非 BCrypt 密码。
     *
     * @param encoder 写入 BCrypt 摘要的密码编码器
     * @return 本次迁移的管理员密码数量
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

    /** 统计两张表中空密码或没有 BCrypt 前缀的记录；仍有记录时终止验收。 */
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

    /** 忽略大小写比较 URL 中的数据库名与确认值，并流式计算指定备份的 SHA-256 验证一致性。 */
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
     * 拒绝超过 72 个 UTF-8 字节的历史密码，再用指定编码器生成摘要。
     *
     * @param encoder 写入 BCrypt 摘要的密码编码器
     * @param password 待校验或编码的明文密码
     * @return 带编码标识的密码摘要
     */
    private String encode(PasswordEncoder encoder, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("A legacy password exceeds the BCrypt byte limit");
        }
        return encoder.encode(password);
    }

    /**
     * 拒绝以左花括号开头的未知编码标识，避免把已有摘要当作明文再次编码。
     *
     * @param password 待校验或编码的明文密码
     */
    private void rejectUnknownEncoding(String password) {
        if (password.startsWith("{")) {
            throw new IllegalStateException("An unsupported password encoding identifier was found");
        }
    }

    /**
     * 要求密码非 null 且非空字符串，保留原值内容。
     *
     * @param value 数据库中读取的密码字段
     * @return 原密码值的字符串表示
     */
    private String requiredPassword(Object value) {
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalStateException("An empty password was found");
        }
        return value.toString();
    }

    /**
     * 读取必需迁移设置，缺失或空白时抛出异常。
     *
     * @param name 必需迁移设置的名称
     * @return 非空白的迁移设置值
     */
    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required migration setting is missing: " + name);
        }
        return value;
    }

    /**
     * 构造仅支持 BCrypt、写入时附加编码前缀的密码编码器。
     *
     * @param strength BCrypt 工作因子，运行入口限定为 4–16
     * @return 使用指定工作因子的委托编码器
     */
    private PasswordEncoder passwordEncoder(int strength) {
        Map<String, PasswordEncoder> encoders = Map.of("bcrypt", new BCryptPasswordEncoder(strength));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /**
     * 流式读取指定数据库备份并计算 SHA-256；读取或算法错误阻止迁移。
     *
     * @param file 用于校验的数据库备份文件路径
     * @return 小写十六进制 SHA-256
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
