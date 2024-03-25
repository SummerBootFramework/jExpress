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
package org.summerboot.jexpress.util;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @param <T>
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ReflectionMetadata<T> {

    protected final Class targetClass;
    protected final String fieldName;
    protected Field field;
    protected T value;

    public ReflectionMetadata(Class targetClass, String fieldName) {
        this.targetClass = targetClass;
        this.fieldName = fieldName;
        if (targetClass != null && StringUtils.isNotBlank(fieldName)) {
            try {
                field = targetClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                int modifier = field.getModifiers();
                if (Modifier.isStatic(modifier)) {
                    value = (T) field.get(null);
                } else {
                    throw new TypeNotPresentException("Wrong field modifier defined in " + targetClass.getName()
                            + "\n\t found:    " + getFieldDefinationDefault() + " " + fieldName
                            + "\n\t expected: " + getFieldDefinationExpected(true, null) + " " + fieldName, null);
                }
            } catch (NoSuchFieldException ex) {
                throw new TypeNotPresentException("No such field: " + targetClass + "." + fieldName, ex);
            } catch (IllegalAccessException ex) {
                throw new SecurityException("No access to field: " + targetClass + "." + fieldName, ex);
            }
        }
    }

    public T value() {
        return value;
    }

    public String buildClassCastExceptionDesc(String expectedType) {
        if (field == null) {
            return null;
        }
        return "Wrong field type defined in " + targetClass.getName()
                + "\n\t found:    " + getFieldDefinationDefault() + " " + fieldName
                + "\n\t expected: " + getFieldDefinationExpected(null, expectedType) + " " + fieldName;
    }

    protected String getFieldDefinationDefault() {
        return getFieldDefinationExpected(null, null);
    }

    protected String getFieldDefinationExpected(Boolean shouldBeStatic, String expectedType) {
        if (field == null) {
            return null;
        }
        // modifier
        int modifier = field.getModifiers();
        String modifierType = Modifier.toString(modifier);
        boolean isStatic = Modifier.isStatic(modifier);
        if (shouldBeStatic != null) {
            if (shouldBeStatic && !isStatic) {
                modifierType += " static";
            } else if (!shouldBeStatic && isStatic) {
                modifierType.replaceAll("static", "");
            }
        }

        // type
        Class fieldClass = field.getType();
        String fieldType;
        if (expectedType == null) {
            Class valueComponentType = fieldClass.getComponentType();
            fieldType = valueComponentType == null ? fieldClass.getName() : valueComponentType.getName();
            if (fieldClass.isArray()) {
                fieldType += "[]";
            }
        } else {
            fieldType = expectedType;
        }
        // put together
        return modifierType + " " + fieldType;
    }

}
