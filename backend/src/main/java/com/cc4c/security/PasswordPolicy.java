package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** 校验新写入密码的 Unicode 字符数和 BCrypt 可接受的 UTF-8 字节上限。 */
public final class PasswordPolicy {
    /** 禁止实例化静态密码规则工具。 */
    private PasswordPolicy() {}

    /**
     * 要求密码为 8 至 64 个 Unicode 码点且不超过 72 个 UTF-8 字节，不满足时抛出 400。
     *
     * @param password 待写入的非空明文密码，不得记录
     */
    public static void requireWritable(String password) {
        int characters = password.codePointCount(0, password.length());
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (characters < 8 || characters > 64 || bytes > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, BusinessCode.VALIDATION_ERROR, "密码必须为 8–64 个字符且 UTF-8 编码不超过 72 字节");
        }
    }
}
