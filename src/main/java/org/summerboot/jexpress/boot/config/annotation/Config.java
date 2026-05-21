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
package org.summerboot.jexpress.boot.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
public @interface Config {

    String desc() default "";

    String format() default "";

    String example() default "";

    String key();

    boolean required() default false;

    //String requiredWhen() default "";
    String defaultValue() default "";

    boolean useInstanceDefaultValue() default true;

    String predefinedValue() default "";

    Validate validate() default Validate.None;

    String StorePwdKey() default "";

    String AliasKey() default "";

    String AliasPwdKey() default "";

    public enum Validate {
        None, Encrypted, EmailRecipients
    }

    /**
     * protected void callbackMethodName(StringBuilder sb) {
     *
     * @return
     */
    String callbackMethodName4Dump() default "";

    String collectionDelimiter() default ",";

    boolean trim() default true;

}
