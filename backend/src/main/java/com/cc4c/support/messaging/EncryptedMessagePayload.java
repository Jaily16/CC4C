package com.cc4c.support.messaging;

/**
 * 保存密钥 ID、nonce 和密文；构造与访问数组时都执行防御性复制。
 *
 * @param keyId 密钥环中的加密密钥 ID
 * @param nonce AES-GCM nonce 字节
 * @param ciphertext 包含认证标签的加密载荷字节
 */
public record EncryptedMessagePayload(String keyId, byte[] nonce, byte[] ciphertext) {
    /**
     * 复制传入 nonce 和密文数组，防止调用方修改已封装载荷。
     *
     * @param keyId 密钥环中的加密密钥 ID
     * @param nonce AES-GCM nonce 字节
     * @param ciphertext 包含认证标签的加密载荷字节
     */
    public EncryptedMessagePayload {
        nonce = nonce.clone();
        ciphertext = ciphertext.clone();
    }

    /**
     * 返回随机 nonce 的副本。
     *
     * @return 不共享内部存储的 nonce 字节
     */
    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    /**
     * 返回密文数组的副本。
     *
     * @return 不共享内部存储的密文字节
     */
    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}
