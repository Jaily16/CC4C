package com.cc4c.identity.internal;

import com.cc4c.identity.api.Cc4cPrincipal;
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

    Cc4cAuthenticationProvider(IdentityService identityService, PasswordEncoder passwordEncoder) {
        this.identityService = identityService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Cc4cAuthenticationToken request = (Cc4cAuthenticationToken) authentication;
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
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return Cc4cAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
