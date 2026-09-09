package com.cc4c.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 通过 SecureRandom 生成六位数字邮箱验证码。 */
@Component
public class VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    /**
     * 在 0 至 999999 范围取随机数并补足六位数字。
     *
     * @return 含前导零的六位验证码
     */
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
