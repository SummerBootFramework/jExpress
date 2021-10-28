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

import org.summerframework.util.FormatterUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RoleMapping {

    public enum Type {
        users, groups
    }

    private final String roleName;
    private final Set<String> groups = new HashSet();
    private final Set<String> users = new HashSet();

    public RoleMapping(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void add(Type type, String csv) {
        String[] a = FormatterUtil.parseCsv(csv);
        switch (type) {
            case users:
                users.addAll(Arrays.asList(a));
                break;
            case groups:
                groups.addAll(Arrays.asList(a));
                break;
        }
    }

    public Set<String> getGroups() {
        return groups;
    }

    public Set<String> getUsers() {
        return users;
    }
}
