package com.cc4c.identity.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 校验身份认证前置条件，失败时阻止不安全的继续执行。
 */
@Component
final class PasswordReadinessVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    /**
     * 创建 PasswordReadinessVerifier 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param jdbc 调用方提供的 {@code jdbc} 值
     */
    PasswordReadinessVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 执行当前组件约定的单次任务，并按既有失败语义向调用方报告结果。
     *
     * @param args 调用方提供的 {@code args} 值
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
