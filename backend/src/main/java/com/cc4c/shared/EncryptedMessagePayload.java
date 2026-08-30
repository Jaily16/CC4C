package com.cc4c.shared;

/** EncryptedMessagePayload 是不可变的数据载体，保持现有字段语义和序列化契约。 */
public record EncryptedMessagePayload(String keyId, byte[] nonce, byte[] ciphertext) {
    public EncryptedMessagePayload {
        nonce = nonce.clone();
        ciphertext = ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }
}
