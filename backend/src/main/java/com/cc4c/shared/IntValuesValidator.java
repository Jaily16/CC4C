package com.cc4c.shared;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;

/**
 * 校验共享基础设施前置条件，失败时阻止不安全的继续执行。
 */
final class IntValuesValidator implements ConstraintValidator<IntValues, Integer> {
    private int[] values;

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param annotation 调用方提供的 {@code annotation} 值
     */
    @Override
    public void initialize(IntValues annotation) {
        values = annotation.value();
    }

    /**
     * 判断当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param value 待处理或存储的值
     * @param context 调用方提供的 {@code context} 值
     * @return 条件成立时返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || Arrays.stream(values).anyMatch(candidate -> candidate == value);
    }
}
