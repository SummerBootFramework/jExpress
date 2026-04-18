package org.summerboot.jexpress.nio.server.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

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
