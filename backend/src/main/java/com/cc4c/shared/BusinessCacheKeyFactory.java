package com.cc4c.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 负责生成业务缓存的稳定键，集中维护命名空间、版本和哈希规则。 */
/** BusinessCacheKeyFactory 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class BusinessCacheKeyFactory {
    private static final String KEY_VERSION = "v1";
    private final String namespace;

    /** 使用配置中的业务缓存命名空间创建键工厂。 */
    public BusinessCacheKeyFactory(String namespace) {
        this.namespace = namespace;
    }

    /** 返回指定区域的代际键。 */
    public String generationKey(String region) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":generation";
    }

    /** 返回带当前代际和逻辑键摘要的数据键。 */
    public String dataKey(String region, String generation, String logicalKey) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":g" + generation + ":" + sha256(logicalKey);
    }

    /** 返回分布式互斥锁键。 */
    public String lockKey(String dataKey) {
        return dataKey + ":lock";
    }

    private void validateRegion(String region) {
        if (region == null || !region.matches("[a-z0-9:-]{2,80}")) {
            throw new IllegalArgumentException("Invalid business cache region");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
