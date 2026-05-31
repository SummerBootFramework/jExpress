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

import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class MetaPathParam {

    protected final int paramOrderIndex;
    protected final Pattern pathParamMetaPattern;
    protected final boolean ispathParamMetaRegex;
    protected final boolean isLast;

    public MetaPathParam(int index, String regex, boolean isLast) {
        this.paramOrderIndex = index;
        ispathParamMetaRegex = regex != null;
        if (ispathParamMetaRegex) {
            regex = regex == null ? regex : regex.trim();
            pathParamMetaPattern = Pattern.compile(regex);
        } else {
            pathParamMetaPattern = null;
        }
        this.isLast = isLast;
    }

    public boolean matches(String value) {
        return ispathParamMetaRegex
                ? pathParamMetaPattern.matcher(value).matches()
                : true;
    }

    public int getParamOrderIndex() {
        return paramOrderIndex;
    }

    public boolean isIsLast() {
        return isLast;
    }

}
