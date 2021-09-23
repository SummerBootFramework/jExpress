package org.summerframework.util.annotation;

import java.util.Arrays;
import java.util.List;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.common.base.Preconditions;

public class OneOfValidator implements ConstraintValidator<OneOf, List<String>> {

    private List<String> values;

    @Override
    public void initialize(OneOf constraintAnnotation) {
        values = Arrays.asList(constraintAnnotation.value());
        Preconditions.checkArgument(values.size() > 0, "Empty list input found in @OneOf annotation");
    }

    @Override
    public boolean isValid(List<String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        } else {
            boolean isvalid = true;
            for (String selection : value) {
                if ((isvalid) && !values.contains(selection)) {
                    isvalid = false;
                    break;
                }
            }
            return isvalid;

        }
    }

}
