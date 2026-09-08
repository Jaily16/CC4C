package com.cc4c.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BusinessCacheProperties 绑定外部配置，并集中表达运行时约束和安全默认值。
 *
 * @param enabled 是否启用对应受控能力
 * @param namespace 调用方提供的 {@code namespace} 值
 */
@ConfigurationProperties(prefix = "cc4c.cache")
public record BusinessCacheProperties(boolean enabled, String namespace) {}
