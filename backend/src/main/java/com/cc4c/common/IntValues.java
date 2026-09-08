package com.cc4c.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IntValues 负责公共技术支撑的一项明确运行职责，并保持现有外部行为不变。
 */
@Documented
@Constraint(validatedBy = IntValuesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntValues {
    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前协议生成或读取的字符串值
     */
    String message() default "must be one of the supported values";

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前方法约定返回结果集合
     */
    int[] value();

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前方法约定返回结果集合
     */
    Class<?>[] groups() default {};

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @return 按当前方法约定返回结果集合
     */
    Class<? extends Payload>[] payload() default {};
}
