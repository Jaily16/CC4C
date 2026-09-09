package com.cc4c.support.cache;

import java.time.Duration;

/** 定义缓存数据、代次和锁所需的原子存取能力；故障旁路由 BusinessCache 协调。 */
public interface BusinessCacheStore {
    /**
     * 读取指定缓存键的字符串值。
     *
     * @param key 完整缓存数据键
     * @return 键值，键不存在时为空
     */
    String get(String key);

    /**
     * 写入字符串值并设置有效期。
     *
     * @param key 完整缓存数据键
     * @param value 待封装或存储的缓存值
     * @param ttl 本次写入的基础有效期
     */
    void set(String key, String value, Duration ttl);

    /**
     * 仅在键不存在时原子写入值及有效期，用于锁领取。
     *
     * @param key 完整缓存数据键
     * @param value 待封装或存储的缓存值
     * @param ttl 本次写入的基础有效期
     * @return 成功写入时为 true
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * 原子递增整型键，用于推进分区代次。
     *
     * @param key 完整缓存数据键
     * @return 递增后的计数
     */
    long increment(String key);

    /**
     * 删除指定缓存键。
     *
     * @param key 完整缓存数据键
     */
    void delete(String key);

    /**
     * 仅当键值与预期令牌一致时原子删除，避免释放其他工作者的锁。
     *
     * @param key 完整缓存数据键
     * @param expectedValue 仅允许删除匹配此令牌的键
     * @return 比较匹配且完成删除时为 true
     */
    boolean compareAndDelete(String key, String expectedValue);

    /**
     * 删除匹配前缀的缓存键，调用方负责提供正确隔离范围。
     *
     * @param prefix 调用方批准的隔离键前缀
     * @return 实际删除键数
     */
    long deleteByPrefix(String prefix);

    /**
     * 提供默认连通性结果；真实远端存储实现应覆盖此方法。
     *
     * @return 默认返回 true
     */
    default boolean ping() {
        return true;
    }
}
