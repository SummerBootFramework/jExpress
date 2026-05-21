/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * derivatived from https://www.javatips.net/api/storm-data-contracts-master/storm-data-contracts/src/main/java/com/forter/contracts/validation/OneOfValidator.java
 * derivatived from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-validation/src/main/java/io/dropwizard/validation/OneOf.java
 */
package org.summerboot.jexpress.util.annotation;

import com.google.common.base.Preconditions;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

/**
 * Checks against one of the specified values. Returns isValid false if the
 * values is empty. Use @NotNull to explicitly reject null values.
 */
public class OneOfValidator implements ConstraintValidator<OneOf, List<String>> {

    protected List<String> theOneOfList;

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
