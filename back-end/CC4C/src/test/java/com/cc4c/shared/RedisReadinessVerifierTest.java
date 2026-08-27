package com.cc4c.shared;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisReadinessVerifierTest {

    @Test
    void acceptsPongAndRejectsUnexpectedResponses() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG", "unexpected");
        RedisReadinessVerifier verifier = new RedisReadinessVerifier(factory);

        assertDoesNotThrow(() -> verifier.run(new DefaultApplicationArguments()));
        assertThrows(
                IllegalStateException.class,
                () -> verifier.run(new DefaultApplicationArguments()));
    }

    @Test
    void propagatesConnectionFailureInsteadOfFallingBack() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("unavailable"));

        assertThrows(
                RedisConnectionFailureException.class,
                () -> new RedisReadinessVerifier(factory)
                        .run(new DefaultApplicationArguments()));
    }
}
