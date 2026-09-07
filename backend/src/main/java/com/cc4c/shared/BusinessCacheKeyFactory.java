package com.cc4c.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 负责生成业务缓存的稳定键，集中维护命名空间、版本和哈希规则。
 */
public final class BusinessCacheKeyFactory {
    private static final String KEY_VERSION = "v1";
    private final String namespace;

    /**
     * 使用配置中的业务缓存命名空间创建键工厂。
     *
     * @param namespace 调用方提供的 {@code namespace} 值
     */
    public BusinessCacheKeyFactory(String namespace) {
        this.namespace = namespace;
    }

    /**
     * 返回指定区域的代际键。
     *
     * @param region 调用方提供的 {@code region} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String generationKey(String region) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":generation";
    }

    /**
     * 返回带当前代际和逻辑键摘要的数据键。
     *
     * @param region 调用方提供的 {@code region} 值
     * @param generation 调用方提供的 {@code generation} 值
     * @param logicalKey 调用方提供的 {@code logicalKey} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String dataKey(String region, String generation, String logicalKey) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":g" + generation + ":" + sha256(logicalKey);
    }

    /**
     * 返回分布式互斥锁键。
     *
     * @param dataKey 调用方提供的 {@code dataKey} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public String lockKey(String dataKey) {
        return dataKey + ":lock";
    }

    /**
     * 校验 BusinessCacheKeyFactory 中与 validateRegion 对应的前置条件，不满足时沿用既有失败语义。
     *
     * @param region 调用方提供的 {@code region} 值
     */
    private void validateRegion(String region) {
        if (region == null || !region.matches("[a-z0-9:-]{2,80}")) {
            throw new IllegalArgumentException("Invalid business cache region");
        }
    }

    /**
     * 执行 BusinessCacheKeyFactory 中的 sha256 职责，并保持既有权限、事务与副作用边界。
     *
     * @param value 调用方提供的 {@code value} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
