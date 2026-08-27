package com.cc4c.identity.internal;

import com.cc4c.identity.api.AccountRole;
import com.cc4c.identity.api.Cc4cPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

final class Cc4cAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AccountRole requestedRole;
    private final String loginIdentifier;
    private Object credentials;
    private Cc4cPrincipal principal;

    Cc4cAuthenticationToken(AccountRole requestedRole, String loginIdentifier, String credentials) {
        super(null);
        this.requestedRole = requestedRole;
        this.loginIdentifier = loginIdentifier;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    Cc4cAuthenticationToken(
            Cc4cPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.requestedRole = principal.role();
        this.loginIdentifier = principal.actorId();
        this.principal = principal;
        setAuthenticated(true);
    }

    AccountRole requestedRole() {
        return requestedRole;
    }

    String loginIdentifier() {
        return loginIdentifier;
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal == null ? loginIdentifier : principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }
}
