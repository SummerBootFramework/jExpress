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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @param <T> Success (JSON) result type
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RpcResult<T> implements AutoCloseable {

    protected final Long index;
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
    protected final SessionContext context;

    // for streaming response: if the caller forgets to close the RpcResult or consume the stream, this will close it when GC collects the RpcResult
    private static final Logger log = LogManager.getLogger(RpcResult.class);
    private static final Cleaner STREAM_CLEANER = Cleaner.create();
    private final Cleaner.Cleanable cleanable;
    private final StreamCloseAction closeAction;

    @Override
    public void close() throws IOException {
        if (cleanable != null) {
            // 1. Mark it as an "explicit, normal shutdown" to inform the Cleaner not to issue leak warnings.
            closeAction.markExplicitlyClosed();
            // 2. Trigger `clean()` to close the stream and unregister from the `Cleaner` queue.
            cleanable.clean();
            if (closeAction.lastException != null) {
                throw closeAction.lastException;
            }
        } else {
            closeHttpResponseStream(httpResponse);
        }
    }

    public void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
        }
    }

    public static IOException closeHttpResponseStream(HttpResponse<?> httpResponse) {
        if (httpResponse != null && httpResponse.body() instanceof InputStream inputStream) {
            try {
                inputStream.close();
            } catch (IOException closeEx) {
                return closeEx;
            }
        }
        return null;
    }

    enum ContentType {JSON, XML, OTHER}

    protected ObjectMapper httpClientConfiguredObjectMapper = null;

    @Deprecated(since = "2.7.4", forRemoval = false)
    public RpcResult(HttpRequest originRequest, String originRequestBody, HttpResponse<String> httpResponse, boolean remoteSuccess) {
        this(null, originRequest, originRequestBody, httpResponse, remoteSuccess, false, null, null);
    }

    public RpcResult(Long index, HttpRequest originRequest, String originRequestBody, HttpResponse<?> httpResponse, boolean remoteSuccess, boolean isStreaming, HttpClientConfig httpClientConfig, SessionContext context) {
        this.index = index;
        this.originRequest = originRequest;
        this.originRequestBody = originRequestBody;
        this.httpResponse = httpResponse;
        this.httpStatusCode = httpResponse.statusCode();
        this.context = context;
        this.httpStatus = HttpResponseStatus.valueOf(httpStatusCode);
        this.remoteSuccess = remoteSuccess;
        this.isStreaming = isStreaming;
        final String requestId = index == null ? "" : index + "rpc: ";
        if (isStreaming) {
            if (remoteSuccess) {
                rpcResponseBody = null;
            } else if (httpResponse.body() != null && httpResponse.body() instanceof InputStream inputStream) {
                // read the stream to a string for error handling, since we cannot deserialize a streaming response
                try (InputStream is = inputStream) {
                    rpcResponseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    throw new UncheckedIOException(requestId + "Failed to read the stream while building RpcResult (" + httpStatus + ")", ex);
                }
            } else {
                rpcResponseBody = null;
            }
        } else {
            this.rpcResponseBody = String.valueOf(httpResponse.body());
        }


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
        } else { // 100% not happen, but just in case, we will default to JSON
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

        // cleaner for streaming response: if the caller forgets to close the RpcResult or consume the stream, this will close it when GC collects the RpcResult
        if (isStreaming && remoteSuccess && httpResponse != null && httpResponse.body() instanceof InputStream inputStream) {
            // Extract the description as a plain string in advance to prevent memory leaks caused by a static inner class holding a reference to `originRequest`.
            String reqDesc = originRequest != null ? (originRequest.method() + " " + originRequest.uri()) : "unknown request";
            this.closeAction = new StreamCloseAction(inputStream, reqDesc);
            this.cleanable = STREAM_CLEANER.register(this, this.closeAction);
        } else {
            this.closeAction = null;
            this.cleanable = null;
        }
    }

    public long index() {
        return index;
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

    public boolean isStreaming() {
        return isStreaming;
    }

    public SessionContext getContext() {
        return context;
    }

    public ObjectMapper getHttpClientConfiguredObjectMapper() {
        return httpClientConfiguredObjectMapper;
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
            successResponse = deserialize(httpClientConfiguredObjectMapper, null, successResponseClass, true, context);
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
        return deserialize(httpClientConfiguredObjectMapper, null, responseClass, true, context);
    }

    public <R> R deserialize(Class<R> responseClass) {
        return deserialize(httpClientConfiguredObjectMapper, responseClass);
    }

    public <R> R deserialize(JavaType responseType) {
        return deserialize(httpClientConfiguredObjectMapper, responseType);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, Class<R> responseClass) {
        return deserialize(jacksonMapper, null, responseClass, true, context);
    }

    public <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType) {
        return deserialize(jacksonMapper, responseType, null, true, context);
    }


    // 💡 for debugging:  Maximum number of characters to retrieve for reconstruction (typically 4KB–8KB is sufficient to clearly identify a malformed JSON structure)
    private static final int SNAPSHOT_SIZE = 8 * 1024;

    public <R> R deserialize(ObjectMapper jacksonMapper, JavaType responseType, Class<R> responseClass, boolean doValidation, final SessionContext context) {
        boolean processStreaming = isStreaming && remoteSuccess;
        if (responseClass == null && responseType == null || !processStreaming && StringUtils.isBlank(rpcResponseBody)) {
            closeQuietly();
            return null;
        }

        final String requestId = index == null ? "" : index + "rpc: ";
        R ret;
        try {
            if (processStreaming) {
                if (httpResponse == null || !(httpResponse.body() instanceof InputStream rawInputStream)) {
                    return null;
                }
                //try (InputStream rawInputStream = (InputStream) httpResponse.body()) {
                try (this) { // to trigger this.close() and unregister the Cleaner when done
                    // 💡 1. Create a tiny memory pocket, used exclusively for recording an 8KB snapshot of the data.
                    ByteArrayOutputStream debugSnapshot = new ByteArrayOutputStream(SNAPSHOT_SIZE);

                    // 💡 2. Wrapping the raw network stream: while Jackson reads and parses the data, simultaneously write a copy to a snapshot.
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
                        if (context != null) {
                            context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA, jsonSnapshotStr);
                        }
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
                String beanValidationError = BeanUtil.getBeanValidationResult(ret);
                if (beanValidationError != null) {
                    if (context != null) {
                        if (processStreaming) {
                            String jsonSnapshotStr = BeanUtil.toJson(ret, false, false);
                            context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA + ".stream.validationFailed", jsonSnapshotStr);
                        }
                        Err e = new Err(BootErrorCode.HTTPCLIENT_INVALID_RESPONSE_FORMAT, null, requestId + "Invalid HTTP client JSON response", null, beanValidationError);
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
        private final InputStream stream;
        private final String requestDesc;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile boolean explicitlyClosed = false;
        private volatile IOException lastException;

        StreamCloseAction(InputStream stream, String requestDesc) {
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
                } catch (IOException e) {
                    this.lastException = e;
                    if (!explicitlyClosed) {
                        log.warn("Failed to close leaked streaming response for request: [{}]", requestDesc, e);
                    }
                }
            }
        }
    }
}


