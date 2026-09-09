package com.cc4c.security;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.jackson2.SecurityJackson2Modules;

/**
 * 读写业务 Session JSON，并在类型解析位置兼容 V5 的两个身份类型。
 *
 * <p>只接受精确旧类名；不替换普通字符串、不保留旧包类，也不访问 Redis。
 * 新写入使用 V6 类名，兼容方向仅为 V6 读取 V5 数据。
 */
public final class SessionJsonRedisSerializer implements RedisSerializer<Object> {
    private static final Map<String, Class<?>> LEGACY_TYPES = Map.of(
            "com.cc4c.identity.api.Cc4cPrincipal", Cc4cPrincipal.class,
            "com.cc4c.identity.internal.Cc4cSessionAuthenticationToken", Cc4cSessionAuthenticationToken.class);

    private final ObjectMapper mapper;
    private final RedisSerializer<Object> writer;

    /**
     * 复制应用映射器并建立仅用于业务会话的类型许可和旧类型解析。
     *
     * @param source 应用映射器，原实例不会被修改
     */
    public SessionJsonRedisSerializer(ObjectMapper source) {
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.security.")
                .allowIfSubType("org.springframework.session.");
        for (Map.Entry<String, Class<?>> entry : LEGACY_TYPES.entrySet()) {
            builder.allowIfSubType(Pattern.compile(Pattern.quote(entry.getKey())));
            builder.allowIfSubType(
                    Pattern.compile(Pattern.quote(entry.getValue().getName())));
        }
        PolymorphicTypeValidator validator = builder.build();
        ObjectMapper writingMapper = source.copy();
        writingMapper.setPolymorphicTypeValidator(validator);
        writingMapper.activateDefaultTyping(validator, DefaultTyping.NON_FINAL);
        writingMapper.registerModules(SecurityJackson2Modules.getModules(Cc4cPrincipal.class.getClassLoader()));
        writingMapper.enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
        writingMapper.addHandler(new LegacySessionTypes(validator));
        writer = new GenericJackson2JsonRedisSerializer(writingMapper);
        // 既有显式身份注解使用 @class，对象容器使用数组标识；属性式读取也接受数组回退，写入约定不变。
        mapper = writingMapper.copy();
        mapper.activateDefaultTypingAsProperty(validator, DefaultTyping.NON_FINAL, "@class");
    }

    /**
     * 沿用 Spring 的 JSON 写入及空值约定，新对象写入当前完整类名。
     *
     * @param value 待写入的业务会话属性
     * @return JSON 字节；空对象对应空字节
     * @throws SerializationException 对象不能按既有 JSON 协议序列化时抛出
     */
    @Override
    public byte[] serialize(Object value) throws SerializationException {
        return writer.serialize(value);
    }

    /**
     * 通过受限映射器解析根对象及其嵌套类型，避免根类型在别名处理前被直接加载。
     *
     * @param bytes 业务会话属性的 JSON 字节
     * @return 恢复后的属性；空输入返回 null
     * @throws SerializationException JSON 无效或类型不在允许范围时抛出
     */
    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(bytes, Object.class);
        } catch (IOException exception) {
            throw new SerializationException("Cannot deserialize the business session JSON", exception);
        }
    }

    /**
     * 仅在 Jackson 报告未知类型标识时解析两个 V5 别名；其他类型仍按原失败规则处理。
     */
    private static final class LegacySessionTypes extends DeserializationProblemHandler {
        private final PolymorphicTypeValidator validator;

        /**
         * 保存与会话映射器相同的类型校验器。
         *
         * @param validator 新旧身份类型使用的精确许可规则
         */
        private LegacySessionTypes(PolymorphicTypeValidator validator) {
            this.validator = validator;
        }

        /**
         * 将精确旧类名解析到当前身份类，并再次验证基类型兼容性和目标类型许可。
         *
         * @param context 当前反序列化上下文
         * @param baseType 当前属性要求的基类型
         * @param typeId 无法加载的类型标识
         * @param resolver 当前类型解析器
         * @param failureMessage Jackson 提供的类型解析失败说明
         * @return 受限目标类型；未命中别名时返回 null，让 Jackson 拒绝未知类型
         * @throws IOException 目标不是所需基类型的子类型或未通过许可时抛出
         */
        @Override
        public JavaType handleUnknownTypeId(
                DeserializationContext context,
                JavaType baseType,
                String typeId,
                TypeIdResolver resolver,
                String failureMessage)
                throws IOException {
            Class<?> target = LEGACY_TYPES.get(typeId);
            if (target == null) {
                return null;
            }
            return context.resolveAndValidateSubType(baseType, target.getName(), validator);
        }
    }
}
