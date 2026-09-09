package com.cc4c.support.messaging;

import com.cc4c.common.MessagePayloadException;
import com.cc4c.config.MessagingProperties;
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

/** 使用 AES-GCM 加密消息 JSON，并将事件版本、代次和时间绑定为附加认证数据。 */
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
     * 载入活动密钥与已校验密钥环，同时校验审核收件人配置。
     *
     * @param objectMapper 显式信封或载荷 JSON 映射器
     * @param properties 消息命名空间、密钥及确认重试配置
     */
    public MessagePayloadCipher(ObjectMapper objectMapper, MessagingProperties properties) {
        this.objectMapper = objectMapper;
        this.activeKeyId = properties.activeKeyId();
        this.keys = properties.payloadKeyMap();
        properties.moderationRecipientList();
    }

    /**
     * 将载荷编码为 JSON，限制为 64 KiB 后交给活动密钥加密。
     *
     * @param eventId 异步事件唯一标识
     * @param eventType 三个 v1 事件类型之一
     * @param schemaVersion 消息信封模式版本，当前为 1
     * @param generation 事件代次，人工恢复时递增
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param payload 待 JSON 编码并加密的业务载荷
     * @return 包含活动密钥 ID、随机 nonce 和密文的载荷
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
     * 限制明文为 64 KiB，以活动密钥和新生成的 12 字节 nonce 执行 128 位标签的 AES-GCM 加密。
     *
     * @param eventId 异步事件唯一标识
     * @param eventType 三个 v1 事件类型之一
     * @param schemaVersion 消息信封模式版本，当前为 1
     * @param generation 事件代次，人工恢复时递增
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @param plaintext 解密后的敏感载荷字节，不得记录
     * @return 绑定事件元数据的加密载荷
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
     * 根据密钥 ID 和信封元数据验证 GCM 标签并解密，拒绝未知密钥、认证失败或超大载荷。
     *
     * @param envelope 包含事件元数据和加密载荷的信封
     * @return 最多 64 KiB 的明文字节
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
     * 从密钥环查找密钥 ID，未知 ID 转换为 UNKNOWN_KEY_ID。
     *
     * @param keyId 密钥环中的加密密钥 ID
     * @return AES 密钥描述
     */
    private SecretKeySpec key(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw new MessagePayloadException("UNKNOWN_KEY_ID", "Message key id is not configured");
        }
        return new SecretKeySpec(key, "AES");
    }

    /**
     * 按固定换行顺序编码事件 ID、类型、模式版本、代次及毫秒时间，空到期时间使用减号。
     *
     * @param eventId 异步事件唯一标识
     * @param eventType 三个 v1 事件类型之一
     * @param schemaVersion 消息信封模式版本，当前为 1
     * @param generation 事件代次，人工恢复时递增
     * @param occurredAt 业务事件发生时间
     * @param expiresAt 可空的业务有效期截止时间
     * @return 用于 GCM 验证的 UTF-8 附加数据
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
