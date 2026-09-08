package com.cc4c.security;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * VerificationCodeGenerator 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public class VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    /**
     * 执行 VerificationCodeGenerator 中的 generate 职责，并保持既有权限、事务与副作用边界。
     *
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
