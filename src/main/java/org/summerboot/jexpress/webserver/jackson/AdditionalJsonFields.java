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

package org.summerboot.jexpress.webserver.jackson;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.TreeMap;

public class AdditionalJsonFields {

    @JsonProperty(index = 32767)
    protected Map<String, Object> additionalFields = new TreeMap<>();

    @JsonAnySetter
    public void adAdditionalField(String key, Object value) {
        if (additionalFields == null) {
            additionalFields = new TreeMap<>();
        }
        additionalFields.put(key, value);
    }


    /*@JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "@class"
    )*/
    //@JsonSerialize(typing = JsonSerialize.Typing.DYNAMIC)
    @JsonAnyGetter
    @Schema(hidden = true)
    public Map<String, Object> getAdditionalFields() {
        return additionalFields;
    }

    public Object getAdditionalField(String key) {
        if (additionalFields == null) {
            return null;
        }
        return additionalFields.get(key);
    }

    /*public <T> T getAdditionalField(String key) {
        return (T) additionalFields.get(key);
    }*/
}
