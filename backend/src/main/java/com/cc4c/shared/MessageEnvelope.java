package com.cc4c.shared;

import java.time.Instant;

/**
 * MessageEnvelope 以不可变结构承载共享基础设施数据，并保持现有字段语义。
 *
 * @param eventId 异步事件的全局唯一标识
 * @param eventType 带版本的异步事件类型
 * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
 * @param generation 调用方提供的 {@code generation} 值
 * @param occurredAt 当前操作使用的时间点
 * @param expiresAt 当前操作使用的时间点
 * @param keyId 目标对象的稳定标识
 * @param nonce 调用方提供的 {@code nonce} 值
 * @param ciphertext 调用方提供的 {@code ciphertext} 值
 */
public record MessageEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        int generation,
        Instant occurredAt,
        Instant expiresAt,
        String keyId,
        byte[] nonce,
        byte[] ciphertext) {
    /**
     * 创建 MessageEnvelope 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param eventId 异步事件的全局唯一标识
     * @param eventType 带版本的异步事件类型
     * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
     * @param generation 调用方提供的 {@code generation} 值
     * @param occurredAt 当前操作使用的时间点
     * @param expiresAt 当前操作使用的时间点
     * @param keyId 目标对象的稳定标识
     * @param nonce 调用方提供的 {@code nonce} 值
     * @param ciphertext 调用方提供的 {@code ciphertext} 值
     */
    public MessageEnvelope {
        nonce = nonce.clone();
        ciphertext = ciphertext.clone();
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @return 按当前方法约定返回结果集合
     */
    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    /**
     * 执行可靠消息状态，保持事件版本、幂等、重试与确认语义。
     *
     * @return 按当前方法约定返回结果集合
     */
    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}
