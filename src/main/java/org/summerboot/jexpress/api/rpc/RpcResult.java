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
package org.summerboot.jexpress.api.rpc;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ServiceErrorConvertible;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;
import org.summerboot.jexpress.util.lang.BeanUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;


/**
 * @param <T> Success (JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RpcResult<T> {

    protected final HttpRequest originRequest;
    protected final String originRequestBody;
    protected final HttpResponse<?> httpResponse;
    protected final String rpcResponseBody;
    protected final int httpStatusCode;
    protected final HttpResponseStatus httpStatus;
    protected final boolean remoteSuccess;
    protected T successResponse;
    protected final ContentType contentType;
    protected final boolean isStreaming;

    enum ContentType {JSON, XML, OTHER}

    protected ObjectMapper httpClientConfiguredObjectMapper = null;

    public RpcResult(HttpRequest originRequest, String originRequestBody, HttpResponse<String> httpResponse, boolean remoteSuccess) {
        this(originRequest, originRequestBody, httpResponse, remoteSuccess, null);
    }

    public RpcResult(HttpRequest originRequest, String originRequestBody, HttpResponse<String> httpResponse, boolean remoteSuccess, HttpClientConfig httpClientConfig) {
        this(originRequest, originRequestBody, httpResponse, remoteSuccess, false, httpClientConfig);
    }

    public RpcResult(HttpRequest originRequest, String originRequestBody, HttpResponse<?> httpResponse, boolean remoteSuccess, boolean isStreaming, HttpClientConfig httpClientConfig) {
        this.originRequest = originRequest;
        this.originRequestBody = originRequestBody;
        this.httpResponse = httpResponse;
        this.rpcResponseBody = httpResponse == null || isStreaming ? null : String.valueOf(httpResponse.body());
        this.httpStatusCode = httpResponse == null ? 0 : httpResponse.statusCode();
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;
        this.isStreaming = isStreaming;

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
                case JSON, OTHER -> BeanUtil.buildJsonMapper(TimeZone.getDefault(), true, false, false, true, true).build();
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

    // Debug 现场还原的最大字符数（通常 4KB~8KB 足够看清出错的 JSON 结构了）
    private static final int SNAPSHOT_SIZE = 8 * 1024;

    protected <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, boolean doValidation, final SessionContext context) {
        if (responseClass == null && responseType == null || !isStreaming && StringUtils.isBlank(rpcResponseBody)) {
            return null;
        }
        R ret;

        try {
            if (isStreaming) {
                try (InputStream rawInputStream = (InputStream) httpResponse.body()) {
                    // 💡 1. 创建一个极小的内存口袋，只用来抄录前 8KB 的数据快照
                    ByteArrayOutputStream debugSnapshot = new ByteArrayOutputStream(SNAPSHOT_SIZE);

                    // 2. 包装原始网络流：在 Jackson 边读边解的过程中，顺便往快照里写一份
                    InputStream wrappedStream = new InputStream() {
                        private int bytesCopied = 0;

                        @Override
                        public int read() throws java.io.IOException {
                            int b = rawInputStream.read();
                            if (b != -1 && bytesCopied < SNAPSHOT_SIZE) {
                                debugSnapshot.write(b);
                                bytesCopied++;
                            }
                            return b;
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws java.io.IOException {
                            int readBytes = rawInputStream.read(b, off, len);
                            if (readBytes > 0 && bytesCopied < SNAPSHOT_SIZE) {
                                int availableSpace = SNAPSHOT_SIZE - bytesCopied;
                                int bytesToCopy = Math.min(readBytes, availableSpace);
                                debugSnapshot.write(b, off, bytesToCopy);
                                bytesCopied += bytesToCopy;
                            }
                            return readBytes;
                        }
                    };

                    //3. read and deserialize the JSON from the wrapped stream
                    try {
                        ret = responseClass == null
                                ? jacksonMapper.readValue(wrappedStream, responseType)
                                : jacksonMapper.readValue(wrappedStream, responseClass);
                    } catch (JacksonException ex) {
                        String jsonSnapshotStr = debugSnapshot.toString(StandardCharsets.UTF_8);
                        context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA, jsonSnapshotStr);
                        throw ex;
                    }
                }
            } else {
                ret = responseClass == null
                        ? jacksonMapper.readValue(rpcResponseBody, responseType)
                        : jacksonMapper.readValue(rpcResponseBody, responseClass);
            }
            if (remoteSuccess) {
                try {
                    successResponse = (T) ret;
                } catch (ClassCastException ex) {
                    // ignore, just return ret
                }
            }
            if (doValidation) {
                String error = BeanUtil.getBeanValidationResult(ret);
                if (error != null) {
                    if (context != null) {
                        if (isStreaming) {
                            String jsonSnapshotStr = BeanUtil.toJson(ret, false, false);
                            context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA, jsonSnapshotStr);
                        }
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


