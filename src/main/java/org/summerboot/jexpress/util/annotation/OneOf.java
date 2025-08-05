/*
 * derivatived from https://www.javatips.net/api/storm-data-contracts-master/storm-data-contracts/src/main/java/com/forter/contracts/validation/OneOf.java
 * derivatived from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-validation/src/main/java/io/dropwizard/validation/OneOf.java
 */
package org.summerboot.jexpress.util.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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

    String message() default "{One of the values is allowed, only valid options are accepted}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String[] value();


}
