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
package org.summerframework.nio.server.domain;

import org.summerframework.util.BeanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class ServiceError {

    private long ref;
    private Object attachedData;

    private List<Error> errors;

    public ServiceError() {
    }

    public ServiceError(long ref) {
        this.ref = ref;
    }

    public ServiceError(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.add(new Error(errorCode, errorTag, errorDesc, ex));
    }

    @Override
    public String toString() {
        return "ServiceError{" + "ref=" + ref + ", attachedData=" + attachedData + ", errors=" + errors + '}';
    }

    public String toJson() {
        //return AppConfig.GsonSerializeNulls.toJson(this);
        try {
            return BeanUtil.toJson(this, true, true);
        } catch (JsonProcessingException ex) {
            return toString();
        }
    }

    public String toXML() {
        try {
            return BeanUtil.toXML(this);
        } catch (JsonProcessingException ex) {
            return toString();
        }
    }

    public long getRef() {
        return ref;
    }

    public void setRef(long ref) {
        this.ref = ref;
    }

    public Object getAttachedData() {
        return attachedData;
    }

    public void setAttachedData(Object attachedData) {
        this.attachedData = attachedData;
    }

    public List<Error> getErrors() {
        return errors;
    }

    public void setErrors(List<Error> errors) {
        this.errors = errors;
    }

    public void addErrors(Error... error) {
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.addAll(Arrays.asList(error));
    }

    public void addErrors(Collection<Error> es) {
        if (es == null || es.isEmpty()) {
            return;
        }
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.addAll(es);
    }

    @JsonIgnore
    public void addError(Error error) {
        if (error == null) {
            return;
        }
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.add(error);
    }

    public void addError(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.add(new Error(errorCode, errorTag, errorDesc, ex));
    }

}
