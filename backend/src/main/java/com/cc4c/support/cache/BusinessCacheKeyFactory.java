package com.cc4c.support.cache;

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
     * @param namespace 与业务 Session 隔离的缓存命名空间
     */
    public BusinessCacheKeyFactory(String namespace) {
        this.namespace = namespace;
    }

    /**
     * 返回指定区域的代际键。
     *
     * @param region 已校验格式的缓存分区
     * @return 由命名空间、v1、分区及 generation 组成的键
     */
    public String generationKey(String region) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":generation";
    }

    /**
     * 返回带当前代际和逻辑键摘要的数据键。
     *
     * @param region 已校验格式的缓存分区
     * @param generation 分区当前代次字符串
     * @param logicalKey 分区内的原始逻辑查询键
     * @return 带 SHA-256 逻辑摘要的数据键
     */
    public String dataKey(String region, String generation, String logicalKey) {
        validateRegion(region);
        return namespace + ":" + KEY_VERSION + ":" + region + ":g" + generation + ":" + sha256(logicalKey);
    }

    /**
     * 返回分布式互斥锁键。
     *
     * @param dataKey 已解析的完整数据键
     * @return 数据键追加 :lock 后的锁键
     */
    public String lockKey(String dataKey) {
        return dataKey + ":lock";
    }

    /**
     * 要求分区为 2 至 80 字符的小写字母、数字、冒号或连字符组合。
     *
     * @param region 已校验格式的缓存分区
     */
    private void validateRegion(String region) {
        if (region == null || !region.matches("[a-z0-9:-]{2,80}")) {
            throw new IllegalArgumentException("Invalid business cache region");
        }
    }

    /**
     * 对逻辑键 UTF-8 字节计算 SHA-256，避免将原始查询条件放入数据键。
     *
     * @param value 待摘要化的非空逻辑键
     * @return 小写十六进制摘要
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
