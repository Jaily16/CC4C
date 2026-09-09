package com.cc4c.security;

import com.cc4c.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 使用配置 pepper 对安全键材料和审计标识计算 HMAC-SHA256。 */
@Component
public final class SecurityKeyHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    /**
     * 将配置 pepper 的 UTF-8 字节保存在内存中用于摘要计算。
     *
     * @param properties 业务安全配置
     */
    public SecurityKeyHasher(SecurityProperties properties) {
        this.secret = properties.pepper().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 为本次调用创建 HMAC 实例，返回无填充的 URL 安全 Base64 摘要。
     *
     * @param value 非空的待摘要化字符串
     * @return 输入字符串的带秘密摘要
     */
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to calculate a security identifier", exception);
        }
    }
}
