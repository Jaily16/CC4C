package com.cc4c.support.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

/** 编解码版本 1 的值或负缓存信封，使用显式 JavaType；大小上限由协调层写入前检查。 */
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
     * 校验信封版本后识别负缓存或按显式类型解析值；缺失普通值作为无效信封抛出。
     *
     * @param <T> 缓存值的数据类型
     * @param json 缓存信封 JSON
     * @param type 保留泛型信息的目标缓存类型
     * @return 普通值或负缓存结果，不包含 MISS 状态
     * @throws JsonProcessingException 信封无效或无法转换为指定类型时抛出
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
     * @param <T> 缓存值的数据类型
     * @param value 业务结果；空 Optional 表示对象不存在
     * @return 含 negative 标志和 value 字段的 JSON
     * @throws JsonProcessingException 无法编码缓存信封时抛出
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
     * 表示合法信封中的普通值或负缓存；缓存键缺失由协调层单独处理。
     *
     * @param <T> 缓存值的数据类型
     * @param state 读取或解码状态
     * @param value 待封装或存储的缓存值
     */
    public record DecodedValue<T>(State state, T value) {
        /** 合法信封仅有 VALUE 与 NEGATIVE 两种状态。 */
        public enum State {
            VALUE,
            NEGATIVE
        }

        /**
         * 创建普通值结果。
         *
         * @param <T> 缓存值的数据类型
         * @param value 待封装或存储的缓存值
         * @return 带 VALUE 状态的解码结果
         */
        public static <T> DecodedValue<T> value(T value) {
            return new DecodedValue<>(State.VALUE, value);
        }

        /**
         * 创建负缓存结果。
         *
         * @param <T> 缓存值的数据类型
         * @return 带 NEGATIVE 状态且值为空的解码结果
         */
        public static <T> DecodedValue<T> negative() {
            return new DecodedValue<>(State.NEGATIVE, null);
        }
    }
}
