package com.cc4c.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 约束整数必须来自指定集合；空值由其他非空约束处理，适用于字段、参数及 record 组件。 */
@Documented
@Constraint(validatedBy = IntValuesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntValues {
    /**
     * 返回集合校验失败时使用的默认提示。
     *
     * @return 默认校验提示
     */
    String message() default "must be one of the supported values";

    /**
     * 声明允许通过校验的整数集合。
     *
     * @return 允许的整数数组
     */
    int[] value();

    /**
     * 声明该约束所属的 Bean Validation 校验分组。
     *
     * @return 校验分组类型数组，默认空数组
     */
    Class<?>[] groups() default {};

    /**
     * 声明校验调用方可附加的载荷类型。
     *
     * @return 载荷类型数组，默认空数组
     */
    Class<? extends Payload>[] payload() default {};
}
