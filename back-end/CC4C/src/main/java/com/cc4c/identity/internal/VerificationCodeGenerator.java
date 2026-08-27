package com.cc4c.identity.internal;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
