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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.summerframework.boot.BootErrorCode;
import org.summerframework.nio.server.domain.ServiceContext;
import org.summerframework.nio.server.domain.Error;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.http.HttpResponse;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.summerframework.nio.server.domain.ServiceErrorConvertible;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 * @param <T> Success(JSON) result type
 * @param <E> Error(JSON) result type
 */
public class RPCResult<T, E extends ServiceErrorConvertible> {

    public static final ObjectMapper DefaultJacksonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    public static void registerModules(com.fasterxml.jackson.databind.Module... modules) {
        DefaultJacksonMapper.registerModules(modules);
    }

    public static void configure(SerializationFeature f, boolean state) {
        DefaultJacksonMapper.configure(f, state);
    }

    public static void configure(DeserializationFeature f, boolean state) {
        DefaultJacksonMapper.configure(f, state);
    }

    public static void configure(JsonGenerator.Feature f, boolean state) {
        DefaultJacksonMapper.configure(f, state);
    }

    public static void configure(JsonParser.Feature f, boolean state) {
        DefaultJacksonMapper.configure(f, state);
    }

    public static void fromJsonFailOnUnknownProperties(boolean state) {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, state);
    }

    static {
        registerModules(new JavaTimeModule());
        //JacksonMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    private final HttpResponse httpResponse;
    private final String rpcResponseBody;
    private final int httpStatusCode;
    private final HttpResponseStatus httpStatus;
    private final boolean remoteSuccess;
    private T successResponse;
    private E errorResponse;

    public RPCResult(HttpResponse httpResponse, boolean remoteSuccess) {
        this.httpResponse = httpResponse;
        this.rpcResponseBody = httpResponse == null ? null : String.valueOf(httpResponse.body());
        this.httpStatusCode = httpResponse == null ? 0 : httpResponse.statusCode();
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;
    }

    public HttpResponse httpResponse() {
        return httpResponse;
    }

    public HttpResponseStatus httpStatus() {
        return httpStatus;
    }

    public int httpStatusCode() {
        return httpStatusCode;
    }

    public String httpResponseBody() {
        return rpcResponseBody;
    }

    public boolean remoteSuccess() {
        return remoteSuccess;
    }

    public E errorResponse() {
        return errorResponse;
    }

    public T successResponse() {
        return successResponse;
    }

    public RPCResult<T, E> update(Class<T> successResponseClass, Class<E> errorResponseClass, final ServiceContext context) {
        return update(DefaultJacksonMapper, null, successResponseClass, errorResponseClass, context);
    }

    public RPCResult<T, E> update(JavaType successResponseType, Class<E> errorResponseClass, final ServiceContext context) {
        return update(DefaultJacksonMapper, successResponseType, null, errorResponseClass, context);
    }

    public RPCResult<T, E> update(JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, final ServiceContext context) {
        return update(DefaultJacksonMapper, successResponseType, successResponseClass, errorResponseClass, context);
    }

    public RPCResult<T, E> update(ObjectMapper jacksonMapper, JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, final ServiceContext context) {
        if (context != null) {
            context.status(httpStatus);
        }
        if (remoteSuccess) {
            successResponse = fromJson(jacksonMapper, successResponseType, successResponseClass, context);
        } else {
            errorResponse = fromJson(jacksonMapper, null, errorResponseClass, context);
            if (errorResponse != null & context != null) {
                if (errorResponse.isSingleError()) {
                    Error e = errorResponse.toSerivceError();
                    context.error(e);
                } else {
                    List<Error> errors = errorResponse.toSerivceErrors();
                    context.errors(errors);
                }
            }
        }
        return this;
    }

    protected <R extends Object> R fromJson(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, final ServiceContext context) {
        if (responseClass == null && responseType == null || StringUtils.isBlank(rpcResponseBody)) {
            return null;
        }
        R ret;
        if (jacksonMapper == null) {
            jacksonMapper = DefaultJacksonMapper;
        }
        try {
            ret = responseClass == null
                    ? jacksonMapper.readValue(rpcResponseBody, responseType)
                    : jacksonMapper.readValue(rpcResponseBody, responseClass);

        } catch (Throwable ex) {
            if (context != null) {
                Error e = new Error(BootErrorCode.HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT, "Unexpected RPC response format", rpcResponseBody, ex);
                context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
            }
            ret = null;
        }

        return ret;
    }
}
