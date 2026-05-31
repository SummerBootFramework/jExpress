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
package org.summerboot.jexpress.security.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.summerboot.jexpress.util.format.FormatterUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RoleMapping {

    public enum Type {
        users, groups
    }

    protected final String roleName;
    protected final Set<String> groups = new HashSet<>();
    protected final Set<String> users = new HashSet<>();

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
