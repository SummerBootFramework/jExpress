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
    public Map<String, Object> serializeArgs() {
        if (additionalField == null) {
            return Collections.emptyMap();   // nothing written when args is null
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

    public void setAdditionalField(Object additionalFieldData, String additionalFieldName) {
        this.additionalField = additionalFieldData;
        this.additionalFieldName = additionalFieldName;
    }
}
