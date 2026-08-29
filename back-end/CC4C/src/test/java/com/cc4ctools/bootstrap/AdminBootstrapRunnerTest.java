package com.cc4ctools.bootstrap;

import com.cc4c.support.Cc4cTestInfrastructure;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AdminBootstrapRunnerTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void connectToContainerDatabase() {
        Cc4cTestInfrastructure.ensureStarted();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                Cc4cTestInfrastructure.mysqlUrl(),
                Cc4cTestInfrastructure.mysqlUsername(),
                Cc4cTestInfrastructure.mysqlPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void removeBootstrapFixture() {
        jdbc.update("DELETE FROM administrator");
    }

    @Test
    void createsBcryptAdministratorAndIsIdempotent() throws Exception {
        Path passwordFile = passwordFile("bootstrap-secret");
        run("7000001", passwordFile);
        run("7000001", passwordFile);

        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM administrator", Long.class));
        String encoded = jdbc.queryForObject(
                "SELECT admin_password FROM administrator WHERE admin_id = '7000001'",
                String.class);
        assertTrue(encoded.startsWith("{bcrypt}"));
        assertTrue(new BCryptPasswordEncoder(4).matches(
                "bootstrap-secret", encoded.substring("{bcrypt}".length())));
    }

    @Test
    void rejectsPasswordMismatchForExistingId() throws Exception {
        run("7000001", passwordFile("first-secret"));

        assertThrows(IllegalStateException.class,
                () -> run("7000001", passwordFile("second-secret")));
    }

    @Test
    void rejectsCreationWhenAnotherActiveAdministratorExists() throws Exception {
        jdbc.update("""
                INSERT INTO administrator(admin_id, admin_password, deleted)
                VALUES('7000002', '{bcrypt}fixture', 0)
                """);

        assertThrows(IllegalStateException.class,
                () -> run("7000001", passwordFile("bootstrap-secret")));
    }

    @Test
    void rejectsInvalidIdAndPasswordPolicy() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> run("admin", passwordFile("bootstrap-secret")));
        assertThrows(IllegalStateException.class,
                () -> run("7000001", passwordFile("short")));
    }

    private void run(String adminId, Path passwordFile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "bootstrap-test",
                Map.of(
                        "spring.datasource.url", Cc4cTestInfrastructure.mysqlUrl(),
                        "CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE", "cc4c_test",
                        "CC4C_ADMIN_BOOTSTRAP_ID", adminId,
                        "CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE", passwordFile.toString(),
                        "CC4C_BCRYPT_STRENGTH", "4")));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                jdbc,
                transactions,
                environment,
                mock(ConfigurableApplicationContext.class));
        runner.run(new DefaultApplicationArguments(new String[0]));
    }

    private Path passwordFile(String password) throws Exception {
        Path file = temporaryDirectory.resolve("admin-" + System.nanoTime() + ".secret");
        Files.writeString(file, password);
        return file;
    }
}
