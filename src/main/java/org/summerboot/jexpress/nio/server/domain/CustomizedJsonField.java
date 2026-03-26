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
    protected Object additionalField;

    @JsonIgnore
    private String additionalFieldName = "additionalField";

    @JsonAnyGetter
    @JsonProperty(index = 4)
    public Map<String, Object> serializeAdditionalField() {
        if (additionalField == null) {
            return Collections.emptyMap();   // nothing written when additionalField is null
        }
        return Collections.singletonMap(additionalFieldName, additionalField);
    }

    @JsonAnySetter
    public void deserializeAdditionalField(String name, Object value) {
        this.additionalFieldName = name;
        this.additionalField = value;
    }

    public void setAdditionalFieldName(String additionalFieldName) {
        this.additionalFieldName = additionalFieldName;
    }

    public Object getAdditionalField() {
        return additionalField;
    }

    public void setAdditionalField(Object additionalField) {
        this.additionalField = additionalField;
    }

    public void setAdditionalField(Object additionalField, String additionalFieldName) {
        this.additionalField = additionalField;
        this.additionalFieldName = additionalFieldName;
    }
}
