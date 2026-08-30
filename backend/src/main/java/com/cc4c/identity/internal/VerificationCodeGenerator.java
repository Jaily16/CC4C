package com.cc4c.identity.internal;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
/** VerificationCodeGenerator 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public class VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
