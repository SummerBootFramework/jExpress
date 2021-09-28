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
package org.summerframework.nio.server.ws.rs;

import java.util.regex.Pattern;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
class MetaPathParam {

    private final int paramOrderIndex;
    private final Pattern pathParamMetaPattern;
    private final boolean ispathParamMetaRegex;

    public MetaPathParam(int index, String regex) {
        this.paramOrderIndex = index;
        ispathParamMetaRegex = regex != null;
        if (ispathParamMetaRegex) {
            regex = regex.trim();
            pathParamMetaPattern = Pattern.compile(regex);
        } else {
            pathParamMetaPattern = null;
        }
    }

    public boolean matches(String value) {
        return ispathParamMetaRegex
                ? pathParamMetaPattern.matcher(value).matches()
                : true;
    }

    public int getParamOrderIndex() {
        return paramOrderIndex;
    }
    
}
