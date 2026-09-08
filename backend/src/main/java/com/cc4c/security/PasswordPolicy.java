package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.common.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * PasswordPolicy 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
public final class PasswordPolicy {
    /**
     * 创建 PasswordPolicy 实例，不触发外部 I/O。
     */
    private PasswordPolicy() {}

    /**
     * 校验当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param password 仅用于当前安全校验的密码或密码摘要
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
