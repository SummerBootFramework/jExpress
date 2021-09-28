/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.i18n;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class I18nLabel {

    private final String code;
    private String key;
    private final boolean critical;
    private final String[] values;

    public I18nLabel(String code, boolean critical, String... values) {
        this.code = code;
        this.values = values;
        this.critical = critical;
        this.key = code;
        //String currentClassKey = new Exception().getStackTrace()[1].getClassName().substring(TRIM_INDEX);
        //System.out.println("currentClassKey="+currentClassKey);
        //this.key = StringUtils.replaceChars(currentClassKey, "$", "_") + "_" + code;
        //System.out.println("key="+key);
    }

    public I18nLabel(String... values) {
        this.code = null;
        this.values = values;
        this.critical = false;
        this.key = code;
        //String currentClassKey = new Exception().getStackTrace()[1].getClassName().substring(TRIM_INDEX);
        //System.out.println("currentClassKey="+currentClassKey);
        //this.key = StringUtils.replaceChars(currentClassKey, "$", "_") + "_" + code;
        //System.out.println("key="+key);
    }

    public String getCode() {
        return code;
    }

    public boolean isCritical() {
        return critical;
    }

    public String getKey() {
        return key;
    }

    public String format(ResourceBundle rb, String... args) {
        String error = rb == null ? values[0] : rb.getString(key);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    args[i] = "";
                }
                error = error.replaceAll("%ARG" + (i + 1) + "%", Matcher.quoteReplacement(args[i]));
            }
        }
        return error;
    }

    public String format(Locale locale, String... args) {
        ResourceBundle rb = locale == null ? null : AppResourceBundle.getAppBundle(locale.toLanguageTag());
        return this.format(rb, args);
    }

    public String format(String languageTag, String... args) {
        ResourceBundle rb = AppResourceBundle.getAppBundle(languageTag);
        return this.format(rb, args);
    }

    /**
     *
     * @param rootPrefix
     * @param rootI18nClass
     * @param translationIndex
     * @param translationsMapping
     */
    public static void buildTranslationsMap(String rootPrefix, Class rootI18nClass, int translationIndex, Map<String, String> translationsMapping) {
        if (!rootI18nClass.isInterface()) {
            return;
        }
        final String sn = rootI18nClass.getSimpleName();
        final String snPrefix;
        if (StringUtils.isBlank(rootPrefix)) {
            snPrefix = sn;
        } else {
            snPrefix = rootPrefix + "_" + sn;
        }
        Field[] fields = rootI18nClass.getDeclaredFields();
        for (Field field : fields) {
            try {
                //field.setAccessible(true);
                Class type = field.getType();
                if (!I18nLabel.class.equals(type)) {
                    continue;
                }
                String verName = snPrefix + "_" + field.getName();
                I18nLabel lb = (I18nLabel) field.get(null);
                lb.key = verName;
                if (translationIndex < lb.values.length) {
                    String value = lb.values[translationIndex];
//                    if (log.isDebugEnabled()) {
//                        log.debug(lb.key + "=" + value);
//                    }
                    if (!StringUtils.isBlank(value)) {
                        translationsMapping.put(lb.key, value);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                ex.printStackTrace();
                //log.fatal(sn + "." + field.getName(), ex);
            }
        }
        Class[] memberInterfaces = rootI18nClass.getDeclaredClasses();
        for (Class c : memberInterfaces) {
            buildTranslationsMap(snPrefix, c, translationIndex, translationsMapping);
        }
    }

}
