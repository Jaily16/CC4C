package com.cc4c.support.messaging;

import java.time.Instant;

/**
 * 携带事件元数据及加密载荷的版本化信封；nonce 和密文在构造与访问时复制。
 *
 * @param eventId 异步事件唯一标识
 * @param eventType 三个 v1 事件类型之一
 * @param schemaVersion 消息信封模式版本，当前为 1
 * @param generation 事件代次，人工恢复时递增
 * @param occurredAt 业务事件发生时间
 * @param expiresAt 可空的业务有效期截止时间
 * @param keyId 密钥环中的加密密钥 ID
 * @param nonce AES-GCM nonce 字节
 * @param ciphertext 包含认证标签的加密载荷字节
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
     * 保存事件元数据并防御性复制 nonce 和密文数组，不执行解密。
     *
     * @param eventId 异步事件唯一标识
     * @param eventType 三个 v1 事件类型之一
     * @param schemaVersion 消息信封模式版本，当前为 1
     * @param generation 事件代次，人工恢复时递增
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param keyId 密钥环中的加密密钥 ID
     * @param nonce AES-GCM nonce 字节
     * @param ciphertext 包含认证标签的加密载荷字节
     */
    public MessageEnvelope {
        nonce = nonce.clone();
        ciphertext = ciphertext.clone();
    }

    /**
     * 返回信封 nonce 的副本。
     *
     * @return 不共享内部存储的 nonce 字节
     */
    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    /**
     * 返回信封密文的副本。
     *
     * @return 不共享内部存储的密文字节
     */
    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}
