package com.cc4c.identity.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class PasswordReadinessVerifierTest {

    @Test
    void acceptsOnlyFullyMigratedPasswordColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L, 0L);

        assertDoesNotThrow(() -> new PasswordReadinessVerifier(jdbc).run(new DefaultApplicationArguments()));
    }

    @Test
    void blocksStartupWhenAnyPasswordIsNotBcryptReady() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L, 0L);

        assertThrows(IllegalStateException.class, () -> new PasswordReadinessVerifier(jdbc)
                .run(new DefaultApplicationArguments()));
    }
}
