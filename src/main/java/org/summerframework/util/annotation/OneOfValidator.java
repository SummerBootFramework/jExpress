/*
 * derivatived from https://www.javatips.net/api/storm-data-contracts-master/storm-data-contracts/src/main/java/com/forter/contracts/validation/OneOfValidator.java
 * derivatived from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-validation/src/main/java/io/dropwizard/validation/OneOf.java
 */
package org.summerframework.util.annotation;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.google.common.base.Preconditions;

/**
 * Checks against one of the specified values. Returns isValid false if the
 * values is empty. Use @NotNull to explicitly reject null values.
 */
public class OneOfValidator implements ConstraintValidator<OneOf, List<String>> {

    private List<String> theOneOfList;

    @Override
    public void initialize(OneOf constraintAnnotation) {
        theOneOfList = Arrays.asList(constraintAnnotation.value());
        Preconditions.checkArgument(theOneOfList.isEmpty(), "Empty list input found in @OneOf annotation");
    }

    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        boolean isvalid = true;
        for (String value : values) {
            if ((isvalid) && !theOneOfList.contains(value)) {
                isvalid = false;
                break;
            }
        }
        return isvalid;
    }

}
