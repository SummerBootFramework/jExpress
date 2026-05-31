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
package org.summerboot.jexpress.core.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.util.lang.BeanUtil;
import org.summerboot.jexpress.web.jackson.AdditionalJsonFields;
import org.summerboot.jexpress.web.jackson.ErrorCodeDeserializer;
import org.summerboot.jexpress.web.jackson.ErrorCodeSerializer;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class Err extends AdditionalJsonFields {

    @JsonSerialize(using = ErrorCodeSerializer.class)
    @JsonDeserialize(using = ErrorCodeDeserializer.class)
    @JsonProperty(index = 1)
    protected String errorCode;

    @JsonProperty(index = 2)
    protected String errorTag;

    @JsonProperty(index = 3)
    protected String errorDesc;


    @JsonIgnore
    protected Throwable cause;

    @JsonIgnore
    protected Object internalInfo;

    public static final Err UNAUTHORIZED_401 = new Err(BootErrorCode.AUTH_LOGIN_FAILED, null, "Authentication Required - Unknown caller", null);

    public Err() {
    }

    public Err(int errorCode, String errorTag, String errorDesc, Throwable ex) {
        //https://www.happycoders.eu/java/how-to-convert-int-to-string-fastest/
        this("" + errorCode, errorTag, errorDesc, ex, null);
    }

    public Err(String errorCode, String errorTag, String errorDesc, Throwable ex) {
        //https://www.happycoders.eu/java/how-to-convert-int-to-string-fastest/
        this(errorCode, errorTag, errorDesc, ex, null);
    }

    public Err(int errorCode, String errorTag, String errorDesc, Throwable ex, Object internalInfo) {
        //https://www.happycoders.eu/java/how-to-convert-int-to-string-fastest/
        this("" + errorCode, errorTag, errorDesc, ex, internalInfo);
    }

    public Err(String errorCode, String errorTag, String errorDesc, Throwable ex, Object internalInfo) {
        this.errorCode = errorCode;
        this.errorTag = errorTag;
        this.errorDesc = errorDesc;
        this.cause = ex;//keep orignal cause for stacktrace in log/email
        this.internalInfo = internalInfo;

//        this._cause = cause == null
//                ? null
//                : ExceptionUtils.getStackTrace(cause);
//        Throwable rootCause = ExceptionUtils.getRootCause(cause);
//        if (rootCause == null) {
//            rootCause = cause;
//        }
//        this._cause = rootCause == null
//                ? null
//                : rootCause.toString();
    }

    //    void showRootCause(boolean isEnable) {
//        this.cause = isEnable ? this._cause : null;
//    }
    public String toJson() {
        //return AppConfig.GsonSerializeNulls.toJson(this);
        try {
            return BeanUtil.toJson(this, true, true);
        } catch (RuntimeException ex) {
            return toStringEx(false);
        }
    }

    @Override
    public String toString() {
        return toStringEx(true);
    }

    public String toStringEx(boolean isForInternalDebug) {
        if (!isForInternalDebug) {
            return "{" + "\"errorCode\":" + errorCode + ", errorTag:" + errorTag + ", \"errorDesc\":\"" + errorDesc + "\"}";
        }
        Throwable rootCause = ExceptionUtils.getRootCause(cause);
        if (rootCause == null) {
            rootCause = cause;
        }
        String trace = ExceptionUtils.getStackTrace(cause);
        return "{" + "\"errorCode\":\"" + errorCode + "\", \"errorTag\":\"" + errorTag + "\", \"errorDesc\":\"" + errorDesc + "\", \"internalInfo\":\"" + internalInfo + "\", \"cause\":\"" + rootCause + "\"}\n\t" + trace + "\n\n";
    }


    @JsonIgnore
    public int getErrorCodeInt() {
        return Integer.parseInt(errorCode);
    }

//    public void setErrorCode(int errorCode) {
//        this.errorCode = "" + errorCode;
//    }


    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorTag() {
        return errorTag;
    }

    public void setErrorTag(String errorTag) {
        this.errorTag = errorTag;
    }

    public String getErrorDesc() {
        return errorDesc;
    }

    public void setErrorDesc(String errorDesc) {
        this.errorDesc = errorDesc;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public Object getInternalInfo() {
        return internalInfo;
    }

    public void setInternalInfo(Object internalInfo) {
        this.internalInfo = internalInfo;
    }
}