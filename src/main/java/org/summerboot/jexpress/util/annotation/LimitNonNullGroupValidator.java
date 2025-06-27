package org.summerboot.jexpress.util.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;

public class LimitNonNullGroupValidator implements ConstraintValidator<LimitNonNullGroup, Object> {

    private String[] fieldNames;

    private int expectedCount;

    @Override
    public void initialize(LimitNonNullGroup constraintAnnotation) {
        this.fieldNames = constraintAnnotation.fields();
        this.expectedCount = constraintAnnotation.limit();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return false;
        if (fieldNames == null || fieldNames.length == 0) {
            return false; // No fields specified, cannot validate
        }

        int nonNullCount = 0;

        try {
            for (String fieldName : fieldNames) {
                Field field = value.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue != null) {
                    nonNullCount++;
                    if (nonNullCount > expectedCount) {
                        return false; // Return early to improve efficiency
                    }
                }
            }
        } catch (Exception e) {
            return false; // Reflex failure is considered failure
        }

        return nonNullCount == expectedCount;
    }
}