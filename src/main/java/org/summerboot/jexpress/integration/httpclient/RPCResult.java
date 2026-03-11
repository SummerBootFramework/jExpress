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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceErrorConvertible;
import org.summerboot.jexpress.util.BeanUtil;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.TimeZone;


/**
 * @param <T> Success (JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RPCResult<T> {

    protected final HttpRequest originRequest;
    protected final String originRequestBody;
    protected final HttpResponse httpResponse;
    protected final String rpcResponseBody;
    protected final int httpStatusCode;
    protected final HttpResponseStatus httpStatus;
    protected final boolean remoteSuccess;
    protected T successResponse;
    protected final ContentType contentType;

    enum ContentType {JSON, XML, OTHER}

    protected ObjectMapper httpClientConfiguredObjectMapper = null;

    public RPCResult(HttpRequest originRequest, String originRequestBody, HttpResponse httpResponse, boolean remoteSuccess) {
        this(originRequest, originRequestBody, httpResponse, remoteSuccess, null);
    }

    public RPCResult(HttpRequest originRequest, String originRequestBody, HttpResponse httpResponse, boolean remoteSuccess, HttpClientConfig httpClientConfig) {
        this.originRequest = originRequest;
        this.originRequestBody = originRequestBody;
        this.httpResponse = httpResponse;
        this.rpcResponseBody = httpResponse == null ? null : String.valueOf(httpResponse.body());
        this.httpStatusCode = httpResponse == null ? 0 : httpResponse.statusCode();
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;

        if (httpResponse != null) {
            HttpHeaders headers = httpResponse.headers();
            if (headers != null) {
                String contentTypeString = headers
                        .firstValue("Content-Type")
                        .orElse("unknown")
                        .toLowerCase();
                if (contentTypeString.contains("json")) {
                    contentType = ContentType.JSON;
                } else if (contentTypeString.contains("xml")) {
                    contentType = ContentType.XML;
                } else {
                    contentType = ContentType.OTHER;
                }
            } else {
                contentType = ContentType.JSON;
            }
        } else {
            contentType = ContentType.JSON;
        }
        if (httpClientConfig == null) {
            this.httpClientConfiguredObjectMapper = switch (contentType) {
                case JSON, OTHER ->
                        BeanUtil.buildJsonMapper(TimeZone.getDefault(), true, false, false, true, true).build();
                case XML -> BeanUtil.buildXmlMapper(TimeZone.getDefault(), true, false, false, true, true).build();
            };
        } else {
            this.httpClientConfiguredObjectMapper = switch (contentType) {
                case JSON, OTHER -> httpClientConfig.getJsonMapper();
                case XML -> httpClientConfig.getXmlMapper();
            };
        }
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

    public ContentType contentType() {
        return contentType;
    }

    public RPCResult<T> update(Class<T> successResponseClass, final SessionContext context) {
        return update(httpClientConfiguredObjectMapper, successResponseClass, context);
    }

    public RPCResult<T> update(JavaType successResponseType, final SessionContext context) {
        return update(httpClientConfiguredObjectMapper, successResponseType, context);
    }

    /**
     * Deserialize the response body to successResponse if remoteSuccess is true, otherwise keep successResponse as null.
     * If the deserialized response is invalid or indicates error, set error in context and return null for successResponse.
     * <p>
     * This is a convenience method for the common case where you do ONLY need to deserialize success response and leave the error in the log for receiving error response,
     * so it does not require caller to check remoteSuccess before calling this method, and it will handle both deserialization error and error response indicated by the deserialized response.
     * <p>
     * <p>
     * If you need more control, you can call the deserialize method directly, or simply leave the error response handling to the framework (by implementing ServiceErrorConvertible in the successResponseClass).
     *
     * @param jacksonMapper
     * @param successResponseClass
     * @param context
     * @return this
     */
    public RPCResult<T> update(ObjectMapper jacksonMapper, Class<T> successResponseClass, final SessionContext context) {
        if (remoteSuccess) {
            successResponse = deserialize(jacksonMapper, successResponseClass, context);
        }
        return this;
    }


    /**
     * Deserialize the response body to successResponse if remoteSuccess is true, otherwise keep successResponse as null.
     * If the deserialized response is invalid or indicates error, set error in context and return null for successResponse.
     * <p>
     * This is a convenience method for the common case where you do ONLY need to deserialize success response and leave the error in the log for receiving error response,
     * so it does not require caller to check remoteSuccess before calling this method, and it will handle both deserialization error and error response indicated by the deserialized response.
     * <p>
     * <p>
     * If you need more control, you can call the deserialize method directly, or simply leave the error response handling to the framework (by implementing ServiceErrorConvertible in the successResponseClass).
     *
     * @param jacksonMapper
     * @param successResponseType
     * @param context
     * @return this
     */
    public RPCResult<T> update(ObjectMapper jacksonMapper, JavaType successResponseType, final SessionContext context) {
        if (remoteSuccess) {
            successResponse = deserialize(jacksonMapper, successResponseType, context);
        }
        return this;
    }

    public <R> R deserialize(Class<R> responseClass, final SessionContext context) {
        return deserialize(httpClientConfiguredObjectMapper, responseClass, context);
    }

    public <R> R deserialize(JavaType responseType, final SessionContext context) {
        return deserialize(httpClientConfiguredObjectMapper, responseType, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, Class<R> responseClass, final SessionContext context) {
        return deserialize(jacksonMapper, null, responseClass, true, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType, final SessionContext context) {
        return deserialize(jacksonMapper, responseType, null, true, context);
    }

    protected <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, boolean doValidation, final SessionContext context) {
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


