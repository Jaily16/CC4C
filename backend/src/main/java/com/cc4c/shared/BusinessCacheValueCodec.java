package com.cc4c.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/** 编解码业务缓存信封，并统一无效值、负缓存和大小上限的语义。 */
/** BusinessCacheValueCodec 协调 CC4C 的一项运行职责，并保持现有外部行为不变。 */
public final class BusinessCacheValueCodec {
    /** 缓存信封的当前协议版本。 */
    public static final int ENVELOPE_VERSION = 1;

    /** 单条缓存值允许的 UTF-8 字节数上限。 */
    public static final int MAX_ENTRY_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;

    /** 使用应用共用的 JSON 映射器创建编解码器。 */
    public BusinessCacheValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 将缓存 JSON 解码为值、负缓存或缺失值。无效信封通过异常交由上层旁路。 */
    public <T> DecodedValue<T> decode(String json, JavaType type) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        if (root.path("schemaVersion").asInt(-1) != ENVELOPE_VERSION) {
            throw new JsonProcessingException("Unsupported cache envelope version") {};
        }
        if (root.path("negative").asBoolean(false)) {
            return DecodedValue.negative();
        }
        JsonNode valueNode = root.get("value");
        if (valueNode == null || valueNode.isNull()) {
            throw new JsonProcessingException("Cache value is missing") {};
        }
        try {
            return DecodedValue.value(objectMapper.readerFor(type).readValue(valueNode));
        } catch (Exception exception) {
            if (exception instanceof JsonProcessingException processingException) {
                throw processingException;
            }
            throw new JsonProcessingException("Cache value could not be decoded", exception) {};
        }
    }

    /** 将值或空 Optional 编码为带 schemaVersion=1 的缓存信封。 */
    public <T> String encode(Optional<T> value) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", ENVELOPE_VERSION);
        root.put("negative", value.isEmpty());
        if (value.isPresent()) {
            root.set("value", objectMapper.valueToTree(value.get()));
        } else {
            root.putNull("value");
        }
        return objectMapper.writeValueAsString(root);
    }

    /** 表示一次缓存解码结果，调用方据此区分命中、负缓存和缺失。 */
    /** DecodedValue 是不可变的数据载体，保持现有字段语义和序列化契约。 */
    public record DecodedValue<T>(State state, T value) {
        /** 缓存解码结果的状态。 */
        /** State 枚举稳定的状态或协议取值，避免调用方自行解释字符串。 */
        public enum State {
            VALUE,
            NEGATIVE
        }

        /** 创建普通值结果。 */
        public static <T> DecodedValue<T> value(T value) {
            return new DecodedValue<>(State.VALUE, value);
        }

        /** 创建负缓存结果。 */
        public static <T> DecodedValue<T> negative() {
            return new DecodedValue<>(State.NEGATIVE, null);
        }
    }
}
