package com.cc4c.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 绑定消息隔离、载荷密钥、审核收件人及发布消费调度参数。
 *
 * @param namespace 当前能力的隔离命名空间
 * @param activeKeyId 新消息加密使用的活动密钥 ID
 * @param payloadKeys 分号分隔的 id=Base64 密钥环，不得记录
 * @param moderationRecipients 逗号分隔的审核通知收件邮箱
 * @param confirmTimeout 发布确认等待时长
 * @param consumerRetryDelays 三档正数消费重试延迟
 * @param pollInterval Outbox 轮询间隔
 * @param dispatcherEnabled 是否启用 Outbox 发布调度
 * @param consumersEnabled 是否启用消息消费
 */
@Validated
@ConfigurationProperties(prefix = "cc4c.messaging")
public record MessagingProperties(
        @NotBlank String namespace,
        @NotBlank String activeKeyId,
        @NotBlank String payloadKeys,
        @NotBlank String moderationRecipients,
        @NotNull Duration confirmTimeout,
        @NotNull List<Duration> consumerRetryDelays,
        @NotNull Duration pollInterval,
        boolean dispatcherEnabled,
        boolean consumersEnabled) {
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9._:-]{3,120}");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /**
     * 校验命名格式、正数超时和恰好三档重试延迟，并检查审核收件人格式。
     *
     * @param namespace 当前能力的隔离命名空间
     * @param activeKeyId 新消息加密使用的活动密钥 ID
     * @param payloadKeys 分号分隔的 id=Base64 密钥环，不得记录
     * @param moderationRecipients 逗号分隔的审核通知收件邮箱
     * @param confirmTimeout 发布确认等待时长
     * @param consumerRetryDelays 三档正数消费重试延迟
     * @param pollInterval Outbox 轮询间隔
     * @param dispatcherEnabled 是否启用 Outbox 发布调度
     * @param consumersEnabled 是否启用消息消费
     */
    public MessagingProperties {
        if (namespace != null && !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalStateException("CC4C RabbitMQ namespace contains unsupported characters");
        }
        if (activeKeyId != null && !KEY_ID.matcher(activeKeyId).matches()) {
            throw new IllegalStateException("CC4C messaging active key id is invalid");
        }
        if (confirmTimeout != null && (confirmTimeout.isZero() || confirmTimeout.isNegative())) {
            throw new IllegalStateException("CC4C messaging confirm timeout must be positive");
        }
        if (pollInterval != null && (pollInterval.isZero() || pollInterval.isNegative())) {
            throw new IllegalStateException("CC4C messaging poll interval must be positive");
        }
        if (consumerRetryDelays != null
                && (consumerRetryDelays.size() != 3
                        || consumerRetryDelays.stream().anyMatch(value -> value.isZero() || value.isNegative()))) {
            throw new IllegalStateException("Exactly three positive consumer retry delays are required");
        }
        if (moderationRecipients != null) {
            parseModerationRecipients(moderationRecipients);
        }
    }

    /**
     * 解析以分号分隔的 id=Base64 密钥项；拒绝重复 ID、非 32 字节密钥或缺失的活动密钥。
     *
     * @return 按密钥 ID 查找原始密钥字节的只读映射
     */
    public Map<String, byte[]> payloadKeyMap() {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String entry : payloadKeys.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalStateException("CC4C messaging payload key entry is invalid");
            }
            String id = entry.substring(0, separator).trim();
            if (!KEY_ID.matcher(id).matches() || result.containsKey(id)) {
                throw new IllegalStateException("CC4C messaging payload key id is invalid or duplicated");
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder()
                        .decode(entry.substring(separator + 1).trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("CC4C messaging payload key is not valid Base64", exception);
            }
            if (decoded.length != 32) {
                throw new IllegalStateException("CC4C messaging payload keys must contain exactly 32 bytes");
            }
            result.put(id, decoded);
        }
        if (!result.containsKey(activeKeyId)) {
            throw new IllegalStateException("CC4C messaging active key id is not present in the key ring");
        }
        return Map.copyOf(result);
    }

    /**
     * 返回经去空白、小写化和去重的审核通知收件人列表。
     *
     * @return 非空且邮箱格式有效的收件人列表
     */
    public List<String> moderationRecipientList() {
        return parseModerationRecipients(moderationRecipients);
    }

    /**
     * 解析逗号分隔邮箱，统一小写并去重；结果为空或存在非法邮箱时拒绝配置。
     *
     * @param rawRecipients 待解析的逗号分隔收件邮箱
     * @return 按首次出现顺序保留的有效邮箱列表
     */
    private static List<String> parseModerationRecipients(String rawRecipients) {
        List<String> recipients = Arrays.stream(rawRecipients.split(","))
                .map(String::trim)
                .map(recipient -> recipient.toLowerCase(Locale.ROOT))
                .filter(recipient -> !recipient.isEmpty())
                .distinct()
                .toList();
        if (recipients.isEmpty()
                || recipients.stream()
                        .anyMatch(recipient -> !EMAIL.matcher(recipient).matches())) {
            throw new IllegalStateException("CC4C moderation notification recipients are invalid");
        }
        return recipients;
    }
}
