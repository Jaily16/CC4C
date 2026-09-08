package com.cc4c.support.cache;

import java.time.Duration;

/**
 * 定义或实现共享基础设施状态的基础设施存取边界。
 */
public interface BusinessCacheStore {
    /**
     * 读取业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @return 按当前协议生成或读取的字符串值
     */
    String get(String key);

    /**
     * 更新业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param value 待处理或存储的值
     * @param ttl 正向值的有效期
     */
    void set(String key, String value, Duration ttl);

    /**
     * 更新业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param value 待处理或存储的值
     * @param ttl 正向值的有效期
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * 记录业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @return 按当前规则计算或读取的数值
     */
    long increment(String key);

    /**
     * 删除或失效业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     */
    void delete(String key);

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param key 当前存取操作使用的稳定键
     * @param expectedValue 调用方提供的 {@code expectedValue} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    boolean compareAndDelete(String key, String expectedValue);

    /**
     * 删除或失效业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @param prefix 调用方提供的 {@code prefix} 值
     * @return 按当前规则计算或读取的数值
     */
    long deleteByPrefix(String prefix);

    /**
     * 执行业务缓存状态，并遵循现有命名空间、失效与故障旁路规则。
     *
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    default boolean ping() {
        return true;
    }
}
