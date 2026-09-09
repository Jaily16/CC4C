package com.cc4ctools.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** 在确认数据库和密码策略后创建首个管理员；已有唯一同账号同密码管理员时幂等返回。 */
@Component
final class AdminBootstrapRunner implements ApplicationRunner {
    private static final Pattern DATABASE_NAME =
            Pattern.compile("^jdbc:mysql://[^/]+/(?<database>[^?;]+)(?:[?;].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADMIN_ID = Pattern.compile("^\\d{7}$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    /**
     * 保存管理员表访问、事务、引导设置及工具上下文。
     *
     * @param jdbc 密码或管理员表的 JDBC 访问器
     * @param transactionTemplate 控制单批迁移或引导写入的事务模板
     * @param environment 读取显式维护设置的 Spring 环境
     * @param context 维护工具的 Spring 上下文
     */
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

    /**
     * 验证七位 ID、数据库和密码，事务内创建或核对管理员；无论成败都关闭上下文。
     *
     * @param args 命令行参数；本入口不自行解析
     */
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
            boolean created = Boolean.TRUE.equals(
                    transactionTemplate.execute(status -> createOrVerify(adminId, password, encoder)));
            System.out.println(
                    created ? "Administrator bootstrap completed" : "Administrator bootstrap already satisfied");
        } finally {
            context.close();
        }
    }

    /**
     * 锁定管理员记录，只在没有有效管理员时插入；同 ID 唯一有效管理员且密码匹配时视为已完成。
     *
     * @param adminId 七位数字管理员 ID
     * @param password 待校验或编码的明文密码
     * @param encoder 写入 BCrypt 摘要的密码编码器
     * @return 新建时为 true，已有同一有效管理员且密码匹配时为 false
     */
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

    /** 精确比较 JDBC URL 的数据库名与引导确认值，不匹配即拒绝。 */
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

    /**
     * 读取密码文件并去除末尾换行，要求 8–64 个 Unicode 码点且不超过 72 个 UTF-8 字节。
     *
     * @return 满足长度约束的管理员明文密码
     */
    private String readPassword() {
        Path passwordFile = Path.of(required("CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE"))
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(passwordFile)) {
            throw new IllegalStateException("The administrator password file does not exist");
        }
        try {
            String password =
                    Files.readString(passwordFile, StandardCharsets.UTF_8).replaceFirst("[\\r\\n]+$", "");
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

    /**
     * 构造写入 BCrypt 编码前缀的委托密码编码器。
     *
     * @param strength BCrypt 工作因子，运行入口限定为 4–16
     * @return 使用指定工作因子的编码器
     */
    private PasswordEncoder passwordEncoder(int strength) {
        Map<String, PasswordEncoder> encoders = Map.of("bcrypt", new BCryptPasswordEncoder(strength));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /**
     * 读取必需的管理员引导设置，缺失或空白时拒绝。
     *
     * @param name 必需管理员引导设置的名称
     * @return 非空白的引导设置值
     */
    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required administrator bootstrap setting is missing: " + name);
        }
        return value;
    }
}
