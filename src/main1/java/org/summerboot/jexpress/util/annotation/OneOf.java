/*
 * derivatived from https://www.javatips.net/api/storm-data-contracts-master/storm-data-contracts/src/main/java/com/forter/contracts/validation/OneOf.java
 * derivatived from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-validation/src/main/java/io/dropwizard/validation/OneOf.java
 */
package org.summerboot.jexpress.util.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * sample:
 *
 * <pre>
 * {@code
 *
 * @Valid
 * @NotEmpty(message = "List should not be empty")
 * @OneOf(value = {"op1", "op2", "op3"}, message = "Only valid options are
 * accepted")
 * List<String> options;
 * }
 * </pre>
 */
@Target({METHOD, FIELD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = OneOfValidator.class)
@Documented
public @interface OneOf {

    String message() default "{value}";

    Class<?>[] groups() default {};

    String[] value();

    Class<? extends Payload>[] payload() default {};

}
