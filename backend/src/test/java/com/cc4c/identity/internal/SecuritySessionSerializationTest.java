package com.cc4c.identity.internal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class SecuritySessionSerializationTest {

    @Test
    void redisJsonRoundTripPreservesTheTypedPrincipal() {
        var serializer = new SecurityConfiguration()
                .springSessionDefaultRedisSerializer(new ObjectMapper().findAndRegisterModules());
        Cc4cPrincipal principal = new Cc4cPrincipal(AccountRole.USER, "42", "Test User");
        var authentication =
                new Cc4cSessionAuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        Object restored = serializer.deserialize(serializer.serialize(context));

        SecurityContext restoredContext = assertInstanceOf(SecurityContext.class, restored);
        assertInstanceOf(
                Cc4cPrincipal.class, restoredContext.getAuthentication().getPrincipal());
    }
}
