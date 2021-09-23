package org.summerframework.util;

import java.util.Iterator;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;

public class BeanValidationUtil {

    private BeanValidationUtil() {

    }

    public static final ValidatorFactory ValidatorFactory = Validation.buildDefaultValidatorFactory();

    public static String getBeanValidationResult(Object bean) {
        if (bean == null) {
            return "missing data";
        }
        Set<ConstraintViolation<Object>> violations = ValidatorFactory.getValidator().validate(bean);
        if (violations.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(bean.getClass().getSimpleName()).append(" Validation Failed: ");
        Iterator<ConstraintViolation<Object>> violationsIter = violations.iterator();
        while (violationsIter.hasNext()) {
            ConstraintViolation<Object> constViolation = violationsIter.next();
            sb.append(constViolation.getPropertyPath()).append("=").append(constViolation.getInvalidValue())
                    .append(" - ").append(constViolation.getMessage()).append("; ");

        }
        return sb.toString();
    }

}
