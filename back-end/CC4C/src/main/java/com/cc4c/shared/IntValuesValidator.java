package com.cc4c.shared;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

final class IntValuesValidator implements ConstraintValidator<IntValues, Integer> {
    private int[] values;

    @Override
    public void initialize(IntValues annotation) {
        values = annotation.value();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || Arrays.stream(values).anyMatch(candidate -> candidate == value);
    }
}
