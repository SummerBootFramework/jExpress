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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ServiceErrorConvertible;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.integration.rpc.http.HttpClientUtil;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;
import org.summerboot.jexpress.util.lang.BeanUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @param <T> Success (JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RpcResult<T> implements AutoCloseable {

    protected final Long requestSeq;
    protected final HttpRequest originRequest;
    protected final String originRequestBody;
    protected final HttpResponse<?> httpResponse;
    protected final String rpcResponseBody;
    protected final int httpStatusCode;
    protected final HttpResponseStatus httpStatus;
    protected final boolean remoteSuccess;
    protected T successResponse;
    protected final ContentType contentType;
    protected final Charset charset;
    protected final HttpClientUtil.ResponseBodyType responseBodyType;
    protected final SessionContext context;
    protected String beanValidationError;

    // for streaming response: if the caller forgets to close the RpcResult or consume the stream, this will close it when GC collects the RpcResult
    private static final Logger log = LogManager.getLogger(RpcResult.class);
    private static final Cleaner STREAM_CLEANER = Cleaner.create();
    private final Cleaner.Cleanable cleanable;
    private final StreamCloseAction closeAction;

    @Override
    public void close() throws Exception {
        if (closeAction != null && cleanable != null) {
            // 1. Mark it as an "explicit, normal shutdown" to inform the Cleaner not to issue leak warnings.
            closeAction.markExplicitlyClosed();
            // 2. Trigger `clean()` to close the stream and unregister from the `Cleaner` queue.
            cleanable.clean();
            if (closeAction.lastException != null) {
                throw closeAction.lastException;
            }
        } else {
            // Defensive fallback (e.g., handling unsubscription for REACTIVE_PUBLISHER)
            HttpClientUtil.closeHttpResponseStream(httpResponse);
        }
    }

    public void closeQuietly() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }


    enum ContentType {JSON, XML, OTHER}

    protected ObjectMapper httpClientConfiguredObjectMapper = null;

    @Deprecated(since = "2.7.4", forRemoval = false)
    public RpcResult(HttpRequest originRequest, String originRequestBody, HttpResponse<?> httpResponse, boolean remoteSuccess) {
        this(null, originRequest, originRequestBody, httpResponse, remoteSuccess, null, null);
    }

    public RpcResult(Long requestSeq, HttpRequest originRequest, String originRequestBody, HttpResponse<?> httpResponse, boolean remoteSuccess, HttpClientConfig httpClientConfig, SessionContext context) {
        Objects.requireNonNull(httpResponse, "httpResponse must not be null");
        this.requestSeq = requestSeq;
        this.originRequest = originRequest;
        this.originRequestBody = originRequestBody;
        this.httpResponse = httpResponse;
        this.httpStatusCode = httpResponse.statusCode();
        this.charset = HttpClientUtil.extractCharset(httpResponse.headers());
        this.context = context;
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;
        this.responseBodyType = HttpClientUtil.detectBodyType(httpResponse); // null body will return ResponseBodyType.EMPTY, which requiresClose() = false
        final String requestId = requestSeq == null ? "" : requestSeq + "rpc: ";

        HttpHeaders headers = httpResponse.headers();
        try {
            this.rpcResponseBody = HttpClientUtil.readBody(httpResponse, charset, remoteSuccess);
        } catch (IOException ex) {
            throw new UncheckedIOException(requestId + "Failed to read response while building RpcResult (" + httpStatus + ")", ex);
        }

        if (headers != null) {
            String contentTypeString = headers
                    .firstValue("Content-Type")
                    .orElse("unknown")
                    .toLowerCase(Locale.ROOT); // Locale.ROOT handles tr_TR correctly
            if (contentTypeString.contains("json")) {
                this.contentType = ContentType.JSON;
            } else if (contentTypeString.contains("xml")) {
                this.contentType = ContentType.XML;
            } else {
                this.contentType = ContentType.OTHER;
            }
        } else {
            this.contentType = ContentType.JSON;
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

        // cleaner for streaming response: if the caller forgets to close the RpcResult or consume the stream, this will close it when GC collects the RpcResult
        if (responseBodyType.requiresClose() && remoteSuccess && httpResponse.body() instanceof AutoCloseable stream) {
            // Extract the description as a plain string in advance to prevent memory leaks caused by a static inner class holding a reference to `originRequest`.
            String reqDesc = originRequest != null ? (originRequest.method() + " " + originRequest.uri()) : "unknown request";
            this.closeAction = new StreamCloseAction(stream, reqDesc);
            this.cleanable = STREAM_CLEANER.register(this, this.closeAction);
        } else {
            this.closeAction = null;
            this.cleanable = null;
        }
    }

    public long requestSeq() {
        return requestSeq;
    }

    public HttpRequest originRequest() {
        return originRequest;
    }

    public String originRequestBody() {
        return originRequestBody;
    }

    public HttpResponse httpResponse() {
        return httpResponse;
    }

    public String httpResponseBody() {
        return rpcResponseBody;
    }

    public int httpStatusCode() {
        return httpStatusCode;
    }

    public HttpResponseStatus httpStatus() {
        return httpStatus;
    }

    public boolean isRemoteSuccess() {
        return remoteSuccess;
    }

    public T successResponse() {
        return successResponse;
    }

    public ContentType contentType() {
        return contentType;
    }

    public Charset charset() {
        return charset;
    }

    public HttpClientUtil.ResponseBodyType responseBodyType() {
        return responseBodyType;
    }

    public SessionContext getContext() {
        return context;
    }

    public ObjectMapper getHttpClientConfiguredObjectMapper() {
        return httpClientConfiguredObjectMapper;
    }

    public String getBeanValidationError() {
        return beanValidationError;
    }

    /**
     *
     * @param successResponseClass
     * @return
     * @deprecated use { @link #deserialize(Class)}
     * <p>Sample code：
     * <pre>{@code
     *     if(rpcResult.isRemoteSuccess()){
     *         MySuccessObj obj = rpcResult.deserialize(MySuccessObj.class);
     *     } else {
     *         MyErrorObj err = rpcResult.deserialize(MyErrorObj.class);
     *     }
     * }</pre>
     */
    @Deprecated(since = "2.7.4", forRemoval = false)
    public RpcResult<T> update(Class<T> successResponseClass) {
        return update(successResponseClass, context);
    }

    /**
     *
     * @param successResponseClass
     * @param context
     * @return
     * @deprecated use { @link #deserialize(Class)}
     * <p>Sample code：
     * <pre>{@code
     *     if(rpcResult.isRemoteSuccess()){
     *         MySuccessObj obj = rpcResult.deserialize(MySuccessObj.class);
     *     } else {
     *         MyErrorObj err = rpcResult.deserialize(MyErrorObj.class);
     *     }
     * }</pre>
     */
    @Deprecated(since = "2.7.4", forRemoval = false)
    public RpcResult<T> update(Class<T> successResponseClass, final SessionContext context) {
        if (remoteSuccess) {
            successResponse = deserialize(httpClientConfiguredObjectMapper, successResponseClass, true, context);
        }
        return this;
    }

    /**
     *
     * @param responseClass
     * @param context
     * @param <R>
     * @return
     * @deprecated use { @link #deserialize(Class)}
     * <p>Sample code：
     * <pre>{@code
     *     if(rpcResult.isRemoteSuccess()){
     *         MySuccessObj obj = rpcResult.deserialize(MySuccessObj.class);
     *     } else {
     *         MyErrorObj err = rpcResult.deserialize(MyErrorObj.class);
     *     }
     * }</pre>
     */
    @Deprecated(since = "2.7.4", forRemoval = false)
    public <R> R deserialize(Class<R> responseClass, final SessionContext context) {
        return deserialize(httpClientConfiguredObjectMapper, responseClass, true, context);
    }

    public <R> R deserialize(Class<R> responseClass) {
        return deserialize(httpClientConfiguredObjectMapper, responseClass, true, context);
    }

    public <R> R deserialize(JavaType responseType) {
        return deserialize(httpClientConfiguredObjectMapper, responseType, true, context);
    }

    public <R> R deserialize(Class<R> responseClass, boolean failOnValidationError) {
        return deserialize(httpClientConfiguredObjectMapper, responseClass, failOnValidationError, context);
    }

    public <R> R deserialize(JavaType responseType, boolean failOnValidationError) {
        return deserialize(httpClientConfiguredObjectMapper, responseType, failOnValidationError, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, Class<R> responseClass) {
        return deserialize(jacksonMapper, responseClass, true, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType) {
        return deserialize(jacksonMapper, responseType, true, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, Class<R> responseClass, boolean failOnValidationError, final SessionContext context) {
        ObjectMapper targetMapper = (jacksonMapper != null) ? jacksonMapper : this.httpClientConfiguredObjectMapper;
        if (targetMapper == null || responseClass == null) {
            closeQuietly();
            return null;
        }
        JavaType javaType = targetMapper.constructType(responseClass);
        return deserialize(targetMapper, javaType, failOnValidationError, context);
    }

    // 💡 for debugging:  Maximum number of characters to retrieve for reconstruction (typically 4KB–8KB is sufficient to clearly identify a malformed JSON structure)
    private static final int SNAPSHOT_SIZE = 8 * 1024;

    public <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType, boolean failOnValidationError, final SessionContext context) {
        // 1. Guard against unsupported body types for synchronous JSON/XML deserialization
        if (responseBodyType == HttpClientUtil.ResponseBodyType.FILE_PATH) {
            throw new UnsupportedOperationException(
                    "Cannot deserialize a FILE_PATH response into an object. Use httpResponseBody() to retrieve the local file path instead."
            );
        }
        if (responseBodyType == HttpClientUtil.ResponseBodyType.REACTIVE_PUBLISHER && remoteSuccess) {
            // closeQuietly(); do not close here, let caller continue to subscribe to httpResponse.body() directly
            throw new UnsupportedOperationException(
                    "Cannot deserialize an active Flow.Publisher response synchronously. Subscribe to httpResponse.body() directly."
            );
        }

        ObjectMapper targetMapper = (jacksonMapper != null) ? jacksonMapper : this.httpClientConfiguredObjectMapper;
        boolean processStreaming = responseBodyType.requiresClose() && remoteSuccess;
        if (targetMapper == null || responseType == null || (!processStreaming && StringUtils.isBlank(rpcResponseBody))) {
            closeQuietly();
            return null;
        }

        final String requestId = requestSeq == null ? "" : requestSeq + "rpc: ";
        R ret;
        try {
            if (processStreaming) {
                InputStream rawInputStream = HttpClientUtil.extractStreamForDeserialization(httpResponse, charset);
                if (rawInputStream == null) {
                    closeQuietly();
                    return null;
                }

                // Use try (this) to trigger RpcResult.close(), closing the stream and unregistering the GC Cleaner
                try (this) {
                    // 💡 1. Create a tiny memory pocket, used exclusively for recording an 8KB snapshot of the data.
                    ByteArrayOutputStream debugSnapshot = new ByteArrayOutputStream(SNAPSHOT_SIZE);

                    // 💡 2. Wrap the raw network stream: while Jackson reads and parses the data, simultaneously write a copy to the snapshot.
                    InputStream wrappedStream = HttpClientUtil.wrapWithSnapshot(rawInputStream, debugSnapshot);

                    // 💡 3. Read and deserialize the JSON/XML from the wrapped stream
                    try {
                        ret = targetMapper.readValue(wrappedStream, responseType);
                    } catch (JacksonException ex) {
                        String jsonSnapshotStr = debugSnapshot.toString(charset);
                        if (context != null) {
                            context.memo(requestSeq + "_" + RpcMemo.MEMO_RPC_RESPONSE_DATA, jsonSnapshotStr);
                        }
                        throw ex;
                    }
                }
            } else {
                ret = targetMapper.readValue(rpcResponseBody, responseType);
            }
            if (remoteSuccess) {
                try {
                    @SuppressWarnings("unchecked")
                    T castRet = (T) ret;
                    this.successResponse = castRet;
                } catch (ClassCastException ignored) {
                    // Ignore if caller deserialized into a different auxiliary type
                }
            }
            this.beanValidationError = BeanUtil.getBeanValidationResult(ret);
            if (failOnValidationError && this.beanValidationError != null) {
                if (context != null) {
//                    if (processStreaming) {
//                        String jsonSnapshotStr = BeanUtil.toJson(ret, false, false);
//                        context.memo(requestSeq + "_" + RpcMemo.MEMO_RPC_RESPONSE_DATA + ".stream.validationFailed", jsonSnapshotStr);
//                    }
                    Err e = new Err(BootErrorCode.HTTPCLIENT_INVALID_RESPONSE_FORMAT, null, requestId + "Invalid HTTP client JSON response", null, beanValidationError);
                    context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
                }
                return null;
            }


            if (!remoteSuccess && context != null && ret instanceof ServiceErrorConvertible errorResponse) {
                if (errorResponse.isSingleError()) {
                    context.error(errorResponse.toServiceError(httpStatus));
                } else {
                    context.errors(errorResponse.toServiceErrors(httpStatus));
                }
            }
        } catch (Exception ex) {
            if (context != null) {
                Err e = new Err(BootErrorCode.HTTPCLIENT_UNKNOWN_RESPONSE_FORMAT, null, requestId + "Unknown HTTP client JSON response", ex, ex.toString());
                context.status(HttpResponseStatus.BAD_GATEWAY).error(e);
            }
            return null;
        }
        return ret;
    }


    /**
     * Internal class to handle closing of streaming InputStream when RpcResult is garbage collected.
     * This is a safety net to prevent resource leaks if the caller forgets to close the RpcResult or consume the stream.
     * <p>
     * It uses a Cleaner to register a cleanup action that will be executed when the RpcResult is no longer reachable. The cleanup action will close the InputStream and log a warning if it was not explicitly closed by the caller.
     * <p>
     * Note: This class is only used for streaming responses (isStreaming = true) and when the HTTP response is successful (remoteSuccess = true).
     * <p>
     * Usage:
     * <pre>{@code
     *     RpcResult<MyResponse> rpcResult = new RpcResult<>(...);
     *     // If the caller forgets to close the RpcResult or consume the stream via deserialize(), the StreamCloseAction will automatically close the InputStream when the RpcResult is garbage collected.
    */
    private static class StreamCloseAction implements Runnable {
        private final AutoCloseable stream;
        private final String requestDesc;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile boolean explicitlyClosed = false;
        private volatile Exception lastException;

        StreamCloseAction(AutoCloseable stream, String requestDesc) {
            this.stream = stream;
            this.requestDesc = requestDesc;
        }

        void markExplicitlyClosed() {
            this.explicitlyClosed = true;
        }

        @Override
        public void run() {
            if (closed.compareAndSet(false, true)) {
                // If it is not explicitly closed via `close()` or `deserialize()`, it indicates a leak that has been caught by the GC-triggered fallback mechanism.
                if (!explicitlyClosed) {
                    log.warn("Unclosed streaming HTTP response detected and closed by GC Cleaner. Caller forgot to close RpcResult or consume stream for request: [{}]", requestDesc);
                }
                try {
                    stream.close();
                } catch (Exception e) {
                    this.lastException = e;
                    if (!explicitlyClosed) {
                        log.warn("Failed to close leaked streaming response for request: [{}]", requestDesc, e);
                    }
                }
            }
        }
    }
}


