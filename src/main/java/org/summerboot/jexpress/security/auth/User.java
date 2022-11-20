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
package org.summerboot.jexpress.security.auth;

import org.summerboot.jexpress.util.BeanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class User implements Serializable, Caller, Comparable<User> {

    protected static AuthConfig authCfg = AuthConfig.cfg;
    protected Long tenantId = 0L;
    protected String tenantName;
    protected Long id = 0L;
    protected String uid;
    @JsonIgnore
    protected String password;
    protected Set<String> groups;
    protected int type = 1;
    protected Map prop = null;

    public User(long tenantId, String tenantName, long id, String uid) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.id = id;
        this.uid = uid;
    }

    public User(long id, String uid) {
        this.id = id;
        this.uid = uid;
    }

    @Override
    public String toString() {
        try {
            //return "User{" + "id=" + id + ", uid=" + uid + ", groups=" + groups + ", type=" + type + '}';
            return BeanUtil.toJson(this);
        } catch (JsonProcessingException ex) {
            return "User{" + "id=" + id + ", uid=" + uid + ", type=" + type + ", ex=" + ex + '}';
        }
    }

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public String getTenantName() {
        return tenantName;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void addGroup(String group) {
        if (group == null) {
            return;
        }
        if (groups == null) {
            groups = new HashSet();
        }
        groups.add(group);
    }

    @Override
    public boolean isInGroup(String group) {
        return groups != null && groups.contains(group);
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    @Override
    public Set<String> getGroups() {
        return groups;
    }

    @Override
    public boolean isInRole(String role) {
        RoleMapping rm = authCfg.getRole(role);
        if (rm == null) {
            return false;
        }

        if (rm.getUsers().contains(uid)) {
            return true;
        }
        return rm.getGroups().stream().anyMatch((group) -> (isInGroup(group)));
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.tenantId);
        hash = 59 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final User other = (User) obj;
        if (!Objects.equals(this.tenantId, other.tenantId)) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(User arg0) {
        if (arg0 == null) {
            return 1;
        }
        Long id2 = arg0.getId();
        if (id == null) {
            if (id2 == null) {
                return 0;
            } else {
                return -1;
            }
        } else {
            if (id2 == null) {
                return 1;
            } else {
                return id.compareTo(id2);
            }
        }
    }

    @Override
    public <T> T getProp(String key, Class<T> type) {
        if (prop == null) {
            return null;
        }
        return (T) prop.get(key);
    }

    @Override
    public void putProp(String key, Object value) {
        if (prop == null) {
            prop = new HashMap();
        }
        prop.put(key, value);
    }

    @Override
    public void remove(String key) {
        if (prop != null) {
            prop.remove(key);
        }
    }

    @Override
    public Set<String> propKeySet() {
        if (prop == null) {
            return null;
        }
        return prop.keySet();
    }

}
