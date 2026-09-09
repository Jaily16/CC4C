package com.cc4c.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 启动时检查两张账户表的密码是否具有 bcrypt 标识，发现未迁移记录则阻止就绪。 */
@Component
final class PasswordReadinessVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    /**
     * 接入仅用于启动密码标识检查的 JDBC 执行器。
     *
     * @param jdbc 账户表查询的 JDBC 执行器
     */
    PasswordReadinessVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 统计包括逻辑删除记录在内的空密码或非 bcrypt 前缀密码；只检查标识，不验证每份哈希内容。
     *
     * @param args Spring 启动参数，本检查不使用其内容
     */
    @Override
    public void run(ApplicationArguments args) {
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
            throw new IllegalStateException(
                    "The database contains passwords that have not completed the offline BCrypt migration");
        }
    }
}
