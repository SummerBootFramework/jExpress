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
package org.summerboot.jexpress.nio.server.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

public class CustomizedJsonField {
    @JsonIgnore
    protected Object customizedField;

    @JsonIgnore
    private String additionalFieldName = "customizedField";

    @JsonAnyGetter
    @JsonProperty(index = 32767)
    public Map<String, Object> serializeCustomizedField() {
        if (customizedField == null) {
            return Collections.emptyMap();   // nothing written when additionalField is null
        }
        return Collections.singletonMap(additionalFieldName, customizedField);
    }

    @JsonAnySetter
    public void deserializeCustomizedField(String name, Object value) {
        this.additionalFieldName = name;
        this.customizedField = value;
    }

    public void setAdditionalFieldName(String additionalFieldName) {
        this.additionalFieldName = additionalFieldName;
    }

    public Object getCustomizedField() {
        return customizedField;
    }

    public void setCustomizedField(String additionalFieldName, Object additionalField) {
        this.customizedField = additionalField;
        this.additionalFieldName = additionalFieldName;
    }
}
