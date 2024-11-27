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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.summerboot.jexpress.util.BeanUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class User implements Serializable, Caller, Comparable<User> {
    protected final Long tenantId;
    protected final String tenantName;
    protected final Long id;
    protected final String uid;

    protected String displayName;
    @JsonIgnore
    protected String password;
    protected int type = 1;
    protected boolean enabled = true;
    protected Long tokenTtlSec;

    protected Set<String> groups;
    protected Map<String, Object> customizedFields;


    public User(Long id, String uid) {
        this(null, null, id, uid);
    }

    public User(@JsonProperty("tenantId") Long tenantId, @JsonProperty("tenantName") String tenantName, @JsonProperty("id") Long id, @JsonProperty("uid") String uid) {
        this.tenantId = tenantId == null ? 0L : tenantId;
        this.tenantName = tenantName == null ? "0" : tenantName;
        this.id = id == null ? 0L : id;
        this.uid = uid == null ? "0" : uid;
        this.displayName = uid;
    }

    @Override
    public String toString() {
        try {
            //return "User{" + "id=" + id + ", uid=" + uid + ", groups=" + groups + ", type=" + type + '}';
            return BeanUtil.toJson(this, false, true);
        } catch (JsonProcessingException ex) {
            return "User{" + "id=" + id + ", uid=" + uid + ", type=" + type + ", ex=" + ex + '}';
        }
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
                Long tenantId1 = this.getTenantId();
                if (tenantId1 == null) {
                    tenantId1 = 0L;
                }
                Long tenantId2 = arg0.getTenantId();
                if (tenantId2 == null) {
                    tenantId2 = 0L;
                }
                if (!tenantId1.equals(tenantId2)) {
                    return tenantId1.compareTo(tenantId2);
                }
                return id.compareTo(id2);
            }
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
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Long getTokenTtlSec() {
        return tokenTtlSec;
    }

    public void setTokenTtlSec(Long tokenTtlSec) {
        this.tokenTtlSec = tokenTtlSec;
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
        RoleMapping rm = AuthConfig.cfg.getRole(role);
        if (rm == null) {
            return false;
        }

        if (rm.getUsers().contains(uid)) {
            return true;
        }
        return rm.getGroups().stream().anyMatch((group) -> (isInGroup(group)));
    }

    public void setCustomizedFields(Map<String, Object> customizedFields) {
        this.customizedFields = customizedFields;
    }

    public Map<String, Object> getCustomizedFields() {
        return customizedFields;
    }

    @Override
    public <T> T getCustomizedField(String key) {
        if (customizedFields == null || key == null) {
            return null;
        }
        return (T) customizedFields.get(key);
    }

    @Override
    public void setCustomizedField(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        if (customizedFields == null) {
            customizedFields = new HashMap();
        }
        customizedFields.put(key, value);
    }

    @Override
    public <T> T removeCustomizedField(String key) {
        if (customizedFields == null || key == null) {
            return null;
        }
        return (T) customizedFields.remove(key);
    }

    @Override
    public Set<String> customizedFieldKeys() {
        if (customizedFields == null) {
            return null;
        }
        return customizedFields.keySet();
    }

    @Override
    public Set<Map.Entry<String, Object>> customizedFields() {
        if (customizedFields == null) {
            return null;
        }
        return customizedFields.entrySet();
    }

}
