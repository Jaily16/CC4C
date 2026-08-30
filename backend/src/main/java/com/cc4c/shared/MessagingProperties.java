package com.cc4c.shared;

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

@Validated
@ConfigurationProperties(prefix = "cc4c.messaging")
/** MessagingProperties 绑定外部配置，并集中表达运行时约束和安全默认值。 */
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

    public List<String> moderationRecipientList() {
        return parseModerationRecipients(moderationRecipients);
    }

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
