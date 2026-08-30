package com.cc4c.shared;

import java.time.Instant;

/** MessageEnvelope 是不可变的数据载体，保持现有字段语义和序列化契约。 */
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
    public MessageEnvelope {
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
