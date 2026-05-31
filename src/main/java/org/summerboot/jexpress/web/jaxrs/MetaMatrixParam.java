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
package org.summerboot.jexpress.web.jaxrs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class MetaMatrixParam {

    //"/services/test/v1;a=123;b=;c=345/aaa/{pa1}/bbb/2020;color=red";
    public static final String MATRIX_REGEX_POSTFIX = "\\s*=\\s*(.*?)[;|/]";// works for a, b, c, not work for color
    public static final String MATRIX_REGEX_POSTFIX_END = "\\s*=\\s*(.*?)$"; //works for color, not work for a, b, c, 

    protected final String key;
    protected final Pattern matrixParamPattern;
    protected final Pattern matrixParamPatternEnd;

    public MetaMatrixParam(String key) {
        this.key = key;
        matrixParamPattern = Pattern.compile(key + MATRIX_REGEX_POSTFIX);
        matrixParamPatternEnd = Pattern.compile(key + MATRIX_REGEX_POSTFIX_END);
    }

    public String value(String path) {
        String v = null;
        Matcher fm = matrixParamPattern.matcher(path);
        if (fm.find()) {
            v = fm.group(1);
        } else {
            fm = matrixParamPatternEnd.matcher(path);
            if (fm.find()) {
                v = fm.group(1);
            }
        }
        return v == null ? null : v.trim();
    }

    public String getKey() {
        return key;
    }

}
