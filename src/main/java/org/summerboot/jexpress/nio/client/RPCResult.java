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
package org.summerboot.jexpress.nio.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.nio.server.domain.ServiceErrorConvertible;

import java.net.http.HttpResponse;
import java.util.List;

/**
 * @param <T> Success(JSON) result type
 * @param <E> Err(JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RPCResult<T, E extends ServiceErrorConvertible> {

    private static boolean isFromJsonFailOnUnknownProperties = true;

    public static ObjectMapper DefaultJacksonMapper = new ObjectMapper();

    public static void update(ObjectMapper objectMapper) {
        objectMapper.registerModules(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, isFromJsonFailOnUnknownProperties);
    }

    public static void init(boolean fromJsonFailOnUnknownProperties, boolean fromJsonCaseInsensitive) {
        isFromJsonFailOnUnknownProperties = fromJsonFailOnUnknownProperties;
        if (fromJsonCaseInsensitive) {
            DefaultJacksonMapper = JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
        }
        update(DefaultJacksonMapper);
    }

    static {
        init(true, false);
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
                    Err e = errorResponse.toSerivceError(httpStatus);
                    context.error(e);
                } else {
                    List<Err> errors = errorResponse.toSerivceErrors(httpStatus);
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
        try {
            ret = responseClass == null
                    ? jacksonMapper.readValue(rpcResponseBody, responseType)
                    : jacksonMapper.readValue(rpcResponseBody, responseClass);

        } catch (Throwable ex) {
            if (context != null) {
                Err e = new Err(BootErrorCode.HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT, null, null, ex, "Failed to parse RPC JSON response: " + rpcResponseBody);
                context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
            }
            ret = null;
        }

        return ret;
    }
}
