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
package org.summerboot.jexpress.integration.httpclient;

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
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceErrorConvertible;
import org.summerboot.jexpress.util.BeanUtil;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.TimeZone;


/**
 * @param <T> Success (JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RPCResult<T> {
    /**
     * Default Jackson ObjectMapper for RPCResult
     * <p>
     * This mapper is configured to:
     * - Use the default time zone
     * - Write dates as ISO-8601 strings (not timestamps)
     * - Ignore empty beans during serialization
     * - Ignore unknown properties during deserialization
     * - Accept case-insensitive property names if configured
     */
    public static ObjectMapper DefaultJacksonMapper = new ObjectMapper();

    static {
        init(TimeZone.getDefault(), true, false);
    }

    public static void init(TimeZone timeZone, boolean fromJsonFailOnUnknownProperties, boolean fromJsonCaseInsensitive) {
        if (fromJsonCaseInsensitive) {
            DefaultJacksonMapper = JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
        }
        update(DefaultJacksonMapper, timeZone, fromJsonFailOnUnknownProperties);
    }

    protected static void update(ObjectMapper objectMapper, TimeZone timeZone, boolean isFromJsonFailOnUnknownProperties) {
        objectMapper.registerModules(new JavaTimeModule());
        objectMapper.setTimeZone(timeZone);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, isFromJsonFailOnUnknownProperties);
    }

    protected final HttpRequest originRequest;
    protected final String originRequestBody;
    protected final HttpResponse httpResponse;
    protected final String rpcResponseBody;
    protected final int httpStatusCode;
    protected final HttpResponseStatus httpStatus;
    protected final boolean remoteSuccess;
    protected T successResponse;

    public RPCResult(HttpRequest originRequest, String originRequestBody, HttpResponse httpResponse, boolean remoteSuccess) {
        this.originRequest = originRequest;
        this.originRequestBody = originRequestBody;
        this.httpResponse = httpResponse;
        this.rpcResponseBody = httpResponse == null ? null : String.valueOf(httpResponse.body());
        this.httpStatusCode = httpResponse == null ? 0 : httpResponse.statusCode();
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;
    }

    public HttpRequest getOriginRequest() {
        return originRequest;
    }

    public String getOriginRequestBody() {
        return originRequestBody;
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

    public T successResponse() {
        return successResponse;
    }

    public RPCResult<T> update(Class<T> successResponseClass, final SessionContext context) {
        if (remoteSuccess) {
            successResponse = parseJsonResponse(successResponseClass, context);
        }
        return this;
    }

    public RPCResult<T> update(JavaType successResponseType, final SessionContext context) {
        if (remoteSuccess) {
            successResponse = parseJsonResponse(successResponseType, context);
        }
        return this;
    }

    public <R> R parseJsonResponse(Class<R> responseClass, final SessionContext context) {
        return parseJsonResponse(DefaultJacksonMapper, null, responseClass, true, context);
    }

    public <R> R parseJsonResponse(JavaType responseType, final SessionContext context) {
        return parseJsonResponse(DefaultJacksonMapper, responseType, null, true, context);
    }

    public <R> R parseJsonResponse(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, boolean doValidation, final SessionContext context) {
        if (responseClass == null && responseType == null || StringUtils.isBlank(rpcResponseBody)) {
            return null;
        }
        R ret;
        try {
            ret = responseClass == null
                    ? jacksonMapper.readValue(rpcResponseBody, responseType)
                    : jacksonMapper.readValue(rpcResponseBody, responseClass);
            if (doValidation) {
                String error = BeanUtil.getBeanValidationResult(ret);
                if (error != null) {
                    if (context != null) {
                        Err e = new Err(BootErrorCode.HTTPCLIENT_INVALID_RESPONSE_FORMAT, null, "Invalid HTTP client JSON response", null, error);
                        context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
                    }
                    return null;
                }
            }
            if (!remoteSuccess && context != null && ret instanceof ServiceErrorConvertible) {
                ServiceErrorConvertible errorResponse = (ServiceErrorConvertible) ret;
                if (errorResponse.isSingleError()) {
                    Err e = errorResponse.toServiceError(httpStatus);
                    context.error(e);
                } else {
                    List<Err> errors = errorResponse.toServiceErrors(httpStatus);
                    context.errors(errors);
                }
            }
        } catch (Throwable ex) {
            if (context != null) {
                Err e = new Err(BootErrorCode.HTTPCLIENT_UNKNOWN_RESPONSE_FORMAT, null, "Unknown HTTP client JSON response", ex, ex.toString());
                context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
            }
            return null;
        }
        return ret;
    }
}
