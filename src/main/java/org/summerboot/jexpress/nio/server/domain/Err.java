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

import org.summerboot.jexpress.util.BeanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.ExceptionUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @param <T>
 */
public class Err<T> {

    @JsonSerialize(using = ErrorCodeSerializer.class)
    @JsonDeserialize(using = ErrorCodeDeserializer.class)
    private String errorCode;
    private String errorTag;
    private String errorDesc;
    private String cause;

    @JsonIgnore
    private String _cause;

    @JsonIgnore
    private Throwable ex;

    @JsonIgnore
    private T attachedData;

    public Err() {
    }

    public Err(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        //https://www.happycoders.eu/java/how-to-convert-int-to-string-fastest/
        this("" + errorCode, errorTag, errorDesc, ex);
    }

    public Err(String errorCode, String errorTag, String errorDesc, Throwable ex) {
        this.errorCode = errorCode;
        this.errorTag = errorTag;
        this.errorDesc = errorDesc;
        this.ex = ex;//keep orignal ex for stacktrace in log/email

//        this._cause = ex == null
//                ? null
//                : ExceptionUtils.getStackTrace(ex);
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        if (rootCause == null) {
            rootCause = ex;
        }
        this._cause = rootCause == null
                ? null
                : rootCause.toString();
    }

    void showRootCause(boolean isEnable) {
        this.cause = isEnable ? this._cause : null;
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

    public String toJson() {
        //return AppConfig.GsonSerializeNulls.toJson(this);
        try {
            return BeanUtil.toJson(this, true, true);
        } catch (JsonProcessingException ex) {
            return toStringEx();
        }
    }

    public String getErrorCode() {
        return errorCode;
    }

    @JsonIgnore
    public int getErrorCodeInt() {
        return Integer.parseInt(errorCode);
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
        this.errorCode = "" + errorCode;
    }

    public void setErrorCode(String errorCode) {
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
