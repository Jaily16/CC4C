package com.cc4c.identity.internal;

import com.cc4c.identity.api.Cc4cPrincipal;
import com.cc4c.shared.Cc4cMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
final class Cc4cAuthenticationProvider implements AuthenticationProvider {
    private final IdentityService identityService;
    private final PasswordEncoder passwordEncoder;
    private final Cc4cMetrics metrics;

    @Autowired
    Cc4cAuthenticationProvider(
            IdentityService identityService,
            PasswordEncoder passwordEncoder,
            Cc4cMetrics metrics) {
        this.identityService = identityService;
        this.passwordEncoder = passwordEncoder;
        this.metrics = metrics;
    }

    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder) {
        this(identityService, passwordEncoder, Cc4cMetrics.disabled());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Cc4cAuthenticationToken request = (Cc4cAuthenticationToken) authentication;
        String role = request.requestedRole().name().toLowerCase(java.util.Locale.ROOT);
        try {
            IdentityService.AuthenticationAccount account =
                    identityService.authenticationAccount(request.requestedRole(), request.loginIdentifier())
                            .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
            String rawPassword = String.valueOf(request.getCredentials());
            if (!passwordEncoder.matches(rawPassword, account.encodedPassword())) {
                throw new BadCredentialsException("Bad credentials");
            }

            Cc4cPrincipal principal = new Cc4cPrincipal(
                    request.requestedRole(), account.id(), account.displayName());
            Cc4cSessionAuthenticationToken authenticated = new Cc4cSessionAuthenticationToken(
                    principal,
                    List.of(new SimpleGrantedAuthority(
                            "ROLE_" + request.requestedRole().name())));
            metrics.increment("cc4c.security.authentication.attempts",
                    "role", role, "outcome", "success");
            return authenticated;
        } catch (AuthenticationException exception) {
            metrics.increment("cc4c.security.authentication.attempts",
                    "role", role, "outcome", "failure");
            throw exception;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return Cc4cAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
