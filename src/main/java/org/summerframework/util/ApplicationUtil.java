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
package org.summerframework.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class ApplicationUtil {

    public static Map<Object, Set<String>> checkDuplicateFields(Class errorCodeClass, Class fieldClass) throws IllegalArgumentException, IllegalAccessException {
        Map<Object, Set<String>> duplicates = new HashMap();
        Map<String, Object> errorCodes = new HashMap();
        ReflectionUtil.loadFields(errorCodeClass, fieldClass, errorCodes, true);

        Map<Object, String> temp = new HashMap();
        errorCodes.keySet().forEach((varName) -> {
            Object errorCode = errorCodes.get(varName);
            String duplicated = temp.put(errorCode, varName);
            if (duplicated != null) {
                Set<String> names = duplicates.get(errorCode);
                if (names == null) {
                    names = new HashSet();
                    duplicates.put(errorCode, names);
                }
                names.add(varName);
                names.add(duplicated);
            }
        });

        return duplicates;
    }

}
