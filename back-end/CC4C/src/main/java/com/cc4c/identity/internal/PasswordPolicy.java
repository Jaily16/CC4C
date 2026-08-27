package com.cc4c.identity.internal;

import com.cc4c.shared.BusinessCode;
import com.cc4c.shared.BusinessException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

final class PasswordPolicy {
    private PasswordPolicy() {
    }

    static void requireWritable(String password) {
        int characters = password.codePointCount(0, password.length());
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (characters < 8 || characters > 64 || bytes > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    BusinessCode.VALIDATION_ERROR,
                    "密码必须为 8–64 个字符且 UTF-8 编码不超过 72 字节");
        }
    }
}
