package com.cc4ctools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("unchecked")
class PasswordMigrationRunnerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void migratesPlaintextInBatchesAndClosesTheToolContext() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = immediateTransactions();
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        List<Map<String, Object>> userRows = List.of(Map.of("user_id", 7L, "password", "legacy-user"));
        List<Map<String, Object>> administratorRows = List.of(Map.of(
                "admin_id", "1000001",
                "admin_password", "legacy-admin"));
        when(jdbc.queryForList(startsWith("SELECT user_id"), any(Object[].class)))
                .thenReturn(userRows, List.of());
        when(jdbc.queryForList(startsWith("SELECT admin_id"), any(Object[].class)))
                .thenReturn(administratorRows, List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L, 0L);
        List<String> encodedPasswords = new ArrayList<>();
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            encodedPasswords.add(invocation.getArgument(1, String.class));
            return 1;
        });

        new PasswordMigrationRunner(jdbc, transactions, environment(), context)
                .run(new org.springframework.boot.DefaultApplicationArguments());

        assertEquals(2, encodedPasswords.size());
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("legacy-user", encodedPasswords.get(0)));
        assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("legacy-admin", encodedPasswords.get(1)));
        verify(context).close();
    }

    @Test
    void skipsAlreadyMigratedValuesSoRepeatedRunsAreIdempotent() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT user_id"), any(Object[].class)))
                .thenReturn(List.of(Map.of("user_id", 7L, "password", "{bcrypt}existing")), List.of());
        when(jdbc.queryForList(startsWith("SELECT admin_id"), any(Object[].class)))
                .thenReturn(
                        List.of(Map.of(
                                "admin_id", "1000001",
                                "admin_password", "{bcrypt}existing")),
                        List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L, 0L);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        new PasswordMigrationRunner(jdbc, immediateTransactions(), environment(), context)
                .run(new org.springframework.boot.DefaultApplicationArguments());

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(context).close();
    }

    @Test
    void rejectsUnknownEncodingWithoutStartingTheApplication() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(startsWith("SELECT user_id"), any(Object[].class)))
                .thenReturn(List.of(Map.of("user_id", 7L, "password", "{noop}legacy")));
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);

        assertThrows(IllegalStateException.class, () -> new PasswordMigrationRunner(
                        jdbc, immediateTransactions(), environment(), context)
                .run(new org.springframework.boot.DefaultApplicationArguments()));
        verify(context, never()).close();
    }

    @Test
    void rejectsUnconfirmedBackupBeforeReadingPasswords() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        MockEnvironment environment =
                environment().withProperty("CC4C_PASSWORD_MIGRATION_BACKUP_SHA256", "0".repeat(64));

        assertThrows(IllegalStateException.class, () -> new PasswordMigrationRunner(
                        jdbc, immediateTransactions(), environment, context)
                .run(new org.springframework.boot.DefaultApplicationArguments()));
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
        verify(context, never()).close();
    }

    private TransactionTemplate immediateTransactions() {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Consumer<TransactionStatus> callback = invocation.getArgument(0);
                    callback.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        return transactions;
    }

    private MockEnvironment environment() throws Exception {
        Path backup = tempDirectory.resolve("cc4c-test-backup.sql");
        Files.writeString(backup, "sanitized test backup", StandardCharsets.UTF_8);
        String hash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup)));
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/cc4c_test?useUnicode=true")
                .withProperty("CC4C_PASSWORD_MIGRATION_CONFIRM_DATABASE", "cc4c_test")
                .withProperty("CC4C_PASSWORD_MIGRATION_BACKUP_PATH", backup.toString())
                .withProperty("CC4C_PASSWORD_MIGRATION_BACKUP_SHA256", hash)
                .withProperty("CC4C_BCRYPT_STRENGTH", "4");
    }
}
