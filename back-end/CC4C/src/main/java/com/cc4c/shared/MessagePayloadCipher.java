package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

@Component
public final class MessagePayloadCipher {
    static final int MAX_PLAINTEXT_BYTES = 64 * 1024;
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final String activeKeyId;
    private final Map<String, byte[]> keys;
    private final SecureRandom secureRandom = new SecureRandom();

    public MessagePayloadCipher(ObjectMapper objectMapper, MessagingProperties properties) {
        this.objectMapper = objectMapper;
        this.activeKeyId = properties.activeKeyId();
        this.keys = properties.payloadKeyMap();
        properties.moderationRecipientList();
    }

    public EncryptedMessagePayload encrypt(
            String eventId,
            String eventType,
            int schemaVersion,
            int generation,
            Instant occurredAt,
            Instant expiresAt,
            Object payload) {
        byte[] plaintext;
        try {
            plaintext = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new MessagePayloadException("INVALID_PAYLOAD", "Message payload cannot be serialized", exception);
        }
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new MessagePayloadException("PAYLOAD_TOO_LARGE", "Message payload exceeds 64 KiB");
        }
        return encryptBytes(eventId, eventType, schemaVersion, generation, occurredAt, expiresAt, plaintext);
    }

    EncryptedMessagePayload encryptBytes(
            String eventId,
            String eventType,
            int schemaVersion,
            int generation,
            Instant occurredAt,
            Instant expiresAt,
            byte[] plaintext) {
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new MessagePayloadException("PAYLOAD_TOO_LARGE", "Message payload exceeds 64 KiB");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(activeKeyId), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(eventId, eventType, schemaVersion, generation, occurredAt, expiresAt));
            return new EncryptedMessagePayload(activeKeyId, nonce, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException exception) {
            throw new MessagePayloadException("ENCRYPTION_FAILED", "Message payload encryption failed", exception);
        }
    }

    public byte[] decrypt(MessageEnvelope envelope) {
        if (envelope.ciphertext().length > MAX_PLAINTEXT_BYTES + 32) {
            throw new MessagePayloadException("PAYLOAD_TOO_LARGE", "Encrypted message payload is too large");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(envelope.keyId()),
                    new GCMParameterSpec(GCM_TAG_BITS, envelope.nonce()));
            cipher.updateAAD(aad(
                    envelope.eventId(), envelope.eventType(), envelope.schemaVersion(),
                    envelope.generation(), envelope.occurredAt(), envelope.expiresAt()));
            byte[] plaintext = cipher.doFinal(envelope.ciphertext());
            if (plaintext.length > MAX_PLAINTEXT_BYTES) {
                throw new MessagePayloadException("PAYLOAD_TOO_LARGE", "Message payload exceeds 64 KiB");
            }
            return plaintext;
        } catch (AEADBadTagException exception) {
            throw new MessagePayloadException("DECRYPTION_FAILED", "Message authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new MessagePayloadException("DECRYPTION_FAILED", "Message payload decryption failed", exception);
        }
    }

    private SecretKeySpec key(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new MessagePayloadException("UNKNOWN_KEY_ID", "Message key id is not configured");
        }
        return new SecretKeySpec(key, "AES");
    }

    private byte[] aad(
            String eventId, String eventType, int schemaVersion, int generation,
            Instant occurredAt, Instant expiresAt) {
        String value = eventId + '\n' + eventType + '\n' + schemaVersion + '\n' + generation + '\n'
                + occurredAt.toEpochMilli() + '\n' + (expiresAt == null ? "-" : expiresAt.toEpochMilli());
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
