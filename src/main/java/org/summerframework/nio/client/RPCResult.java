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
package org.summerframework.nio.client;

import org.summerframework.boot.BootErrorCode;
import org.summerframework.nio.server.domain.ServiceContext;
import org.summerframework.nio.server.domain.Error;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 * @param <T> Success(JSON) result type
 * @param <E> Error(JSON) result type
 */
public class RPCResult<T, E> {

    public static final ObjectMapper DefaultJacksonMapper = new ObjectMapper();

    static {
        DefaultJacksonMapper.registerModule(new JavaTimeModule());
        //JacksonMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        DefaultJacksonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        DefaultJacksonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        DefaultJacksonMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        DefaultJacksonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    private final HttpResponse httpResponse;
    private final String rpcResponseBody;
    private final boolean remoteSuccess;
    private T successResponse;
    private E errorResponse;

    public RPCResult(HttpResponse httpResponse, String rpcResponseBody, boolean remoteSuccess) {
        this.httpResponse = httpResponse;
        this.rpcResponseBody = rpcResponseBody;
        this.remoteSuccess = remoteSuccess;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public String getRpcResponseBody() {
        return rpcResponseBody;
    }

    public boolean isRemoteSuccess() {
        return remoteSuccess;
    }

    public E getErrorResponse() {
        return errorResponse;
    }

    public void setErrorResponse(E errorResponse) {
        this.errorResponse = errorResponse;
    }

    public T getSuccessResponse() {
        return successResponse;
    }

    public void setSuccessResponse(T successResponse) {
        this.successResponse = successResponse;
    }

    public void update(JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass) throws JsonProcessingException {
        this.update(DefaultJacksonMapper, successResponseType, successResponseClass, errorResponseClass);
    }

    public void update(ObjectMapper jacksonMapper, JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass) throws JsonProcessingException {
        if (remoteSuccess) {
            setSuccessResponse(getRpcResponse(jacksonMapper, successResponseType, successResponseClass));
        } else {
            setErrorResponse(getRpcResponse(jacksonMapper, null, errorResponseClass));
        }
    }

    public void update(JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, final ServiceContext serviceContext) {
        this.update(DefaultJacksonMapper, successResponseType, successResponseClass, errorResponseClass, serviceContext);
    }

    public void update(ObjectMapper jacksonMapper, JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, final ServiceContext serviceContext) {
        if (remoteSuccess) {
            setSuccessResponse(getRpcResponse(jacksonMapper, successResponseType, successResponseClass, serviceContext));
        } else {
            setErrorResponse(getRpcResponse(jacksonMapper, null, errorResponseClass, serviceContext));
        }
    }

    public <R extends Object> R getRpcResponse(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, final ServiceContext serviceContext) {

        R ret;
        try {
            ret = getRpcResponse(jacksonMapper, responseType, responseClass);
        } catch (Throwable ex) {
            if (serviceContext != null) {
                Error e = new Error(BootErrorCode.HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT, null, "Unexpected RPC response format", ex);
                serviceContext.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
            }
            ret = null;
        }
        return ret;
    }

    public <R extends Object> R getRpcResponse(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass) throws JsonProcessingException {
        if (responseClass == null && responseType == null || StringUtils.isBlank(rpcResponseBody)) {
            return null;
        }
        R ret;
        if (jacksonMapper == null) {
            ret = responseClass == null
                    ? DefaultJacksonMapper.readValue(rpcResponseBody, responseType)
                    : DefaultJacksonMapper.readValue(rpcResponseBody, responseClass);
        } else {
            ret = responseClass == null
                    ? jacksonMapper.readValue(rpcResponseBody, responseType)
                    : jacksonMapper.readValue(rpcResponseBody, responseClass);
        }

        return ret;
    }
}
