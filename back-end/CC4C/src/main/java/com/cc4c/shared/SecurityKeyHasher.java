package com.cc4c.shared;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Component
public final class SecurityKeyHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public SecurityKeyHasher(SecurityProperties properties) {
        this.secret = properties.pepper().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate a security identifier", exception);
        }
    }
}
