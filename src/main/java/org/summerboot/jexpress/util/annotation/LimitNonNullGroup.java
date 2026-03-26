/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License and you have no
 * policy prohibiting employee contributions back to this file (unless the contributor to this
 * file is your current or retired employee). You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.summerboot.jexpress.util.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
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
 * @LimitNonNullGroup(
 *     fields = { "field1", "field2" },
 *     limit = 1,
 *     message = "Only one of filed1 or field2 must be provided"
 * )
 * @LimitNonNullGroup(
 *     fields = { "field3", "field4" },
 *     limit = 2,
 *     message = "Both filed3 and filed3 must be provided"
 * )
 * public class MyObject {
 *     private String field1;
 *     private String field2;
 *     private String field3;
 *     private String field4;
 * }
 * }</pre>
 *
 * <p><strong>Validation Rule:</strong></p>
 * <ul>
 *   <li>Passes if limit {@code N} of the specified fields are non-null</li>
 *   <li>Fails if fewer or more than {@code N} are non-null</li>
 * </ul>
 *
 * <p>This annotation must be applied at the class level. It uses reflection to count how many of the
 * specified fields are non-null during validation.</p>
 *
 * @author ChatGPT
 */
@Repeatable(LimitNonNullGroup.List.class)  // ← 添加这一行
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

    /**
     * Container annotation for repeatable use of {@link LimitNonNullGroup}.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        LimitNonNullGroup[] value();
    }
}
