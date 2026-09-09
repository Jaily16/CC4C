package com.cc4c.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;

/** 执行 IntValues 的枚举值校验，允许 null，由独立的非空约束决定是否必填。 */
final class IntValuesValidator implements ConstraintValidator<IntValues, Integer> {
    private int[] values;

    /**
     * 从约束注解取得允许的整数集合。
     *
     * @param annotation 声明允许整数集合的 IntValues 注解
     */
    @Override
    public void initialize(IntValues annotation) {
        values = annotation.value();
    }

    /**
     * 允许空值；非空整数必须与集合中的某一项相等。
     *
     * @param value 待校验的整数，null 在本校验器中视为有效
     * @param context Bean Validation 提供的校验上下文，本实现不修改其默认违反信息
     * @return 输入为空或属于允许集合时为 true，否则为 false
     */
    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || Arrays.stream(values).anyMatch(candidate -> candidate == value);
    }
}
