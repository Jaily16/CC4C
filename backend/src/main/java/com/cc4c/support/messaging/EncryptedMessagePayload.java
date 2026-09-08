package com.cc4c.support.messaging;

/**
 * EncryptedMessagePayload 以不可变结构承载共享基础设施数据，并保持现有字段语义。
 *
 * @param keyId 目标对象的稳定标识
 * @param nonce 调用方提供的 {@code nonce} 值
 * @param ciphertext 调用方提供的 {@code ciphertext} 值
 */
public record EncryptedMessagePayload(String keyId, byte[] nonce, byte[] ciphertext) {
    /**
     * 创建 EncryptedMessagePayload 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param keyId 目标对象的稳定标识
     * @param nonce 调用方提供的 {@code nonce} 值
     * @param ciphertext 调用方提供的 {@code ciphertext} 值
     */
    public EncryptedMessagePayload {
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
