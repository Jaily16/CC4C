package com.cc4c.shared;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * SecurityKeyHasher 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public final class SecurityKeyHasher {
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    /**
     * 创建 SecurityKeyHasher 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param properties 调用方提供的 {@code properties} 值
     */
    public SecurityKeyHasher(SecurityProperties properties) {
        this.secret = properties.pepper().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 判断 SecurityKeyHasher 中与 hash 对应的条件是否成立。
     *
     * @param value 调用方提供的 {@code value} 值
     * @return 按当前声明计算、查询或转换得到的结果
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
