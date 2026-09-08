package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * MessagePayloadCipher 协调 CC4C 的一项运行职责，并保持现有外部行为不变。
 */
@Component
public final class MessagePayloadCipher {
    static final int MAX_PLAINTEXT_BYTES = 64 * 1024;
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final String activeKeyId;
    private final Map<String, byte[]> keys;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建 MessagePayloadCipher 并保存其必需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     * @param properties 调用方提供的 {@code properties} 值
     */
    public MessagePayloadCipher(ObjectMapper objectMapper, MessagingProperties properties) {
        this.objectMapper = objectMapper;
        this.activeKeyId = properties.activeKeyId();
        this.keys = properties.payloadKeyMap();
        properties.moderationRecipientList();
    }

    /**
     * 按 MessagePayloadCipher 的既定规则转换输入，不记录凭据或敏感原文。
     *
     * @param eventId 目标对象的稳定标识
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
     * @param generation 调用方提供的 {@code generation} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @param payload 调用方提供的 {@code payload} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
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

    /**
     * 按 MessagePayloadCipher 的既定规则转换输入，不记录凭据或敏感原文。
     *
     * @param eventId 目标对象的稳定标识
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
     * @param generation 调用方提供的 {@code generation} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @param plaintext 调用方提供的 {@code plaintext} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
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

    /**
     * 按 MessagePayloadCipher 的既定规则转换输入，不记录凭据或敏感原文。
     *
     * @param envelope 调用方提供的 {@code envelope} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public byte[] decrypt(MessageEnvelope envelope) {
        if (envelope.ciphertext().length > MAX_PLAINTEXT_BYTES + 32) {
            throw new MessagePayloadException("PAYLOAD_TOO_LARGE", "Encrypted message payload is too large");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE, key(envelope.keyId()), new GCMParameterSpec(GCM_TAG_BITS, envelope.nonce()));
            cipher.updateAAD(aad(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.schemaVersion(),
                    envelope.generation(),
                    envelope.occurredAt(),
                    envelope.expiresAt()));
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

    /**
     * 执行 MessagePayloadCipher 中的 key 职责，并保持既有权限、事务与副作用边界。
     *
     * @param keyId 目标对象的稳定标识
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private SecretKeySpec key(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new MessagePayloadException("UNKNOWN_KEY_ID", "Message key id is not configured");
        }
        return new SecretKeySpec(key, "AES");
    }

    /**
     * 执行 MessagePayloadCipher 中的 aad 职责，并保持既有权限、事务与副作用边界。
     *
     * @param eventId 目标对象的稳定标识
     * @param eventType 调用方提供的 {@code eventType} 值
     * @param schemaVersion 调用方提供的 {@code schemaVersion} 值
     * @param generation 调用方提供的 {@code generation} 值
     * @param occurredAt 调用方提供的 {@code occurredAt} 值
     * @param expiresAt 调用方提供的 {@code expiresAt} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    private byte[] aad(
            String eventId,
            String eventType,
            int schemaVersion,
            int generation,
            Instant occurredAt,
            Instant expiresAt) {
        String value = eventId
                + '\n'
                + eventType
                + '\n'
                + schemaVersion
                + '\n'
                + generation
                + '\n'
                + occurredAt.toEpochMilli()
                + '\n'
                + (expiresAt == null ? "-" : expiresAt.toEpochMilli());
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
