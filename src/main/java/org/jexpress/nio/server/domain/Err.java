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
package org.jexpress.nio.server.domain;

import org.jexpress.util.BeanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class Err<T> {

    private int errorCode;
    private String errorTag;
    private String errorDesc;
    private String cause;
    private Throwable ex;
    @JsonIgnore
    private T attachedData;

    public Err() {
    }

    public Err(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        this.errorCode = errorCode;
        this.errorTag = errorTag;
        this.errorDesc = errorDesc;
        this.ex = ex;//keep orignal ex for stacktrace in log/email
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        if (rootCause == null) {
            rootCause = ex;
        }
        this.cause = rootCause == null
                ? null
                : rootCause.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

    protected String toStringEx() {
        if (cause == null) {
            return "{" + "\"errorCode\": " + errorCode + ", errorTag=" + errorTag + ", \"errorDesc\": \"" + errorDesc + "\"}";
        }
        return "{" + "\"errorCode\": " + errorCode + ", errorTag=" + errorTag + ", \"errorDesc\": \"" + errorDesc + "\", \"cause\": \"" + cause + "\"}";
    }

//    @Override
//    public int hashCode() {
//        int hash = 7;
//        hash = 97 * hash + this.errorCode;
//        hash = 97 * hash + Objects.hashCode(this.errorTag);
//        hash = 97 * hash + Objects.hashCode(this.errorDesc);
//        hash = 97 * hash + Objects.hashCode(this.cause);
//        hash = 97 * hash + Objects.hashCode(this.ex);
//        return hash;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) {
//            return true;
//        }
//        if (obj == null) {
//            return false;
//        }
//        if (getClass() != obj.getClass()) {
//            return false;
//        }
//        final Error other = (Error) obj;
//        if (this.errorCode != other.errorCode) {
//            return false;
//        }
//        if (!Objects.equals(this.errorTag, other.errorTag)) {
//            return false;
//        }
//        if (!Objects.equals(this.errorDesc, other.errorDesc)) {
//            return false;
//        }
//        if (!Objects.equals(this.cause, other.cause)) {
//            return false;
//        }
//        if (!Objects.equals(this.ex, other.ex)) {
//            return false;
//        }
//        return true;
//    }
    public String toJson() {
        //return AppConfig.GsonSerializeNulls.toJson(this);
        try {
            return BeanUtil.toJson(this, true, true);
        } catch (JsonProcessingException ex) {
            return toStringEx();
        }
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorTag() {
        return errorTag;
    }

    public String getErrorDesc() {
        return errorDesc;
    }

    public String getCause() {
        return cause;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorTag(String errorTag) {
        this.errorTag = errorTag;
    }

    public void setErrorDesc(String errorDesc) {
        this.errorDesc = errorDesc;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    @JsonIgnore
    public Throwable getEx() {
        return ex;
    }

    public T getAttachedData() {
        return attachedData;
    }

    public void setAttachedData(T attachedData) {
        this.attachedData = attachedData;
    }

}
