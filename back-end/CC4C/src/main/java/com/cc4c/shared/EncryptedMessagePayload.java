package com.cc4c.shared;

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
