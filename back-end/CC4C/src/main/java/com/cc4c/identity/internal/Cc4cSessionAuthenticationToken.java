package com.cc4c.identity.internal;

import com.cc4c.identity.api.Cc4cPrincipal;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.util.Collection;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
final class Cc4cSessionAuthenticationToken extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Cc4cPrincipal principal;

    @JsonCreator
    Cc4cSessionAuthenticationToken(
            @JsonProperty("principal") Cc4cPrincipal principal,
            @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
            @JsonProperty("details") Object details) {
        super(authorities);
        this.principal = principal;
        setDetails(details);
        setAuthenticated(true);
    }

    Cc4cSessionAuthenticationToken(
            Cc4cPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        this(principal, authorities, null);
    }

    @Override
    @JsonIgnore
    public Object getCredentials() {
        return null;
    }

    @Override
    public Cc4cPrincipal getPrincipal() {
        return principal;
    }
}
