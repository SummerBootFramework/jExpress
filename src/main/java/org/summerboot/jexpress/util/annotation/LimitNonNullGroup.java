package org.summerboot.jexpress.util.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Validation annotation to ensure that exactly a specified number of fields are non-null.
 *
 * <p>This is useful in scenarios where only one (or a specific number of) mutually exclusive fields
 * should be provided in a request or form, for example: providing exactly one contact method.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @LimitNonNull(
 *     fields = { "email", "phone", "wechatId" },
 *     exactly = 1,
 *     message = "Exactly one contact method must be provided"
 * )
 * public class ContactRequest {
 *     private String email;
 *     private String phone;
 *     private String wechatId;
 * }
 * }</pre>
 *
 * <p><strong>Validation Rule:</strong></p>
 * <ul>
 *   <li>Passes if exactly {@code N} of the specified fields are non-null</li>
 *   <li>Fails if fewer or more than {@code N} are non-null</li>
 * </ul>
 *
 * <p>This annotation must be applied at the class level. It uses reflection to count how many of the
 * specified fields are non-null during validation.</p>
 *
 * @author ChatGPT
 */
@Documented
@Constraint(validatedBy = LimitNonNullGroupValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LimitNonNullGroup {
    String message() default "Exactly one of the specified fields must be non-null";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String[] fields();

    int limit() default 1; // Optional limit to specify how many non-null fields are allowed, default is 1
}
