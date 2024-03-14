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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.summerboot.jexpress.util.BeanUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ServiceError {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The index of requests received by current server since start")
    protected final String ref;

    @JsonIgnore
    protected Object attachedData;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "The optional error list")
    protected List<Err> errors;

    public ServiceError(String ref) {
        this.ref = ref;
    }

//    public ServiceError(int errorCode, String errorTag, String errorDesc, Throwable ex) {
//        //https://www.happycoders.eu/java/how-to-convert-int-to-string-fastest/
//        this("" + errorCode, errorTag, errorDesc, ex);
//    }
//
//    public ServiceError(String errorCode, String errorTag, String errorDesc, Throwable ex) {
//        this.ref = null;
//        if (errors == null) {
//            errors = new ArrayList();
//        }
//        this.errors.add(new Err(errorCode, errorTag, errorDesc, ex, null));
//    }

    //    ServiceError showRootCause(boolean isEnable) {
//        if (errors != null) {
//            for (var err : errors) {
//                err.showRootCause(isEnable);
//            }
//        }
//        return this;
//    }
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

    public String getRef() {
        return ref;
    }

    //    public void setRef(long ref) {
//        this.ref = BootConstant.APP_ID + "-" + ref;
//    }
    public Object getAttachedData() {
        return attachedData;
    }

    public void setAttachedData(Object attachedData) {
        this.attachedData = attachedData;
    }

    public List<Err> getErrors() {
        return errors;
    }

    public ServiceError setErrors(List<Err> errors) {
        this.errors = errors;
        return this;
    }

    public ServiceError addErrors(Err... error) {
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.addAll(Arrays.asList(error));
        return this;
    }

    public ServiceError addErrors(Collection<Err> es) {
        if (es == null || es.isEmpty()) {
            return this;
        }
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.addAll(es);
        return this;
    }

    @JsonIgnore
    public ServiceError addError(Err error) {
        if (error == null) {
            return this;
        }
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.add(error);
        return this;
    }

    public ServiceError addError(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        return addError(errorCode, errorTag, errorDesc, ex, null);
    }

    public ServiceError addError(int errorCode, String errorTag, String errorDesc, Throwable ex, String internalInfo) {
        if (errors == null) {
            errors = new ArrayList();
        }
        this.errors.add(new Err(errorCode, errorTag, errorDesc, ex, internalInfo));
        return this;
    }

}
