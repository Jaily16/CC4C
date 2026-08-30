package com.cc4c.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CorsConfigurationTest {

    @Test
    void configuresExactCredentialedOriginsAndRetryAfterExposure() {
        SecurityProperties properties = properties("http://localhost:5173,https://cc4c.example.com");
        var source = new CorsConfiguration().corsConfigurationSource(properties);
        var configuration = source.getCorsConfigurations().get("/**");

        assertEquals(
                java.util.List.of("http://localhost:5173", "https://cc4c.example.com"),
                configuration.getAllowedOrigins());
        assertTrue(configuration.getAllowCredentials());
        assertEquals(java.util.List.of("Retry-After", "X-Request-ID"), configuration.getExposedHeaders());
    }

    @Test
    void rejectsWildcardOriginsWhenCredentialsAreEnabled() {
        assertThrows(IllegalStateException.class, () -> new CorsConfiguration()
                .corsConfigurationSource(properties("https://*.example.com")));
    }

    private SecurityProperties properties(String origins) {
        return new SecurityProperties(
                "test-pepper-with-at-least-thirty-two-characters", false, origins, 4, true, true, "cc4c:test:security");
    }
}
