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
package org.summerframework.security.auth;

import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public interface Caller {

    Number getTenantId();

    String getTenantName();

    Number getId();

    String getUid();

    String getPassword();

    boolean isInGroup(String group);

    boolean isInRole(String role);

    Set<String> getGroups();

    int getType();

    <T extends Object> T getProp(String key, Class<T> type);

    void putProp(String key, Object value);

    void remove(String key);

    Set<String> propKeySet();
}
