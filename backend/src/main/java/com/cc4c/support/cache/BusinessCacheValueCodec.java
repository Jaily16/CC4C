package com.cc4c.support.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/**
 * 编解码业务缓存信封，并统一无效值、负缓存和大小上限的语义。
 */
public final class BusinessCacheValueCodec {
    /** 缓存信封的当前协议版本。 */
    public static final int ENVELOPE_VERSION = 1;

    /** 单条缓存值允许的 UTF-8 字节数上限。 */
    public static final int MAX_ENTRY_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;

    /**
     * 使用应用共用的 JSON 映射器创建编解码器。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     */
    public BusinessCacheValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将缓存 JSON 解码为值、负缓存或缺失值。无效信封通过异常交由上层旁路。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param json 调用方提供的 {@code json} 值
     * @param type 调用方提供的 {@code type} 值
     * @return 按当前声明计算、查询或转换得到的结果
     * @throws JsonProcessingException 既有声明所描述的失败条件发生时抛出
     */
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

    /**
     * 将值或空 Optional 编码为带 schemaVersion=1 的缓存信封。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param value 调用方提供的 {@code value} 值
     * @return 按当前声明计算、查询或转换得到的结果
     * @throws JsonProcessingException 既有声明所描述的失败条件发生时抛出
     */
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

    /**
     * 表示一次缓存解码结果，调用方据此区分命中、负缓存和缺失。
     *
     * @param <T> 该声明处理的数据或结果类型
     * @param state 调用方提供的 {@code state} 值
     * @param value 调用方提供的 {@code value} 值
     */
    public record DecodedValue<T>(State state, T value) {
        /**
         * 缓存解码结果的状态。
         */
        public enum State {
            VALUE,
            NEGATIVE
        }

        /**
         * 创建普通值结果。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @param value 调用方提供的 {@code value} 值
         * @return 按当前声明计算、查询或转换得到的结果
         */
        public static <T> DecodedValue<T> value(T value) {
            return new DecodedValue<>(State.VALUE, value);
        }

        /**
         * 创建负缓存结果。
         *
         * @param <T> 该声明处理的数据或结果类型
         * @return 按当前声明计算、查询或转换得到的结果
         */
        public static <T> DecodedValue<T> negative() {
            return new DecodedValue<>(State.NEGATIVE, null);
        }
    }
}
