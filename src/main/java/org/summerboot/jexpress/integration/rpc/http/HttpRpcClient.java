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
package org.summerboot.jexpress.integration.rpc.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.BootPoi;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.api.rpc.RpcClient;
import org.summerboot.jexpress.api.rpc.RpcMemo;
import org.summerboot.jexpress.api.rpc.RpcResult;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;
import org.summerboot.jexpress.security.UrlSanitizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class HttpRpcClient implements RpcClient {

    /**
     * Get the HttpClientConfig for this HttpRpcClient instance to provide access to the underlying HttpClientConfig for advanced configuration and customization
     *
     * @return the HttpClientConfig instance
     */
    protected abstract HttpClientConfig getConfig();

    protected static AtomicLong concurrentRequestCount = new AtomicLong(0);

    /**
     * set default headers; proxy auth; timeout
     *
     * @param reqBuilder
     */
    protected void configure(HttpRequest.Builder reqBuilder) {
        HttpClientConfig httpCfg = getConfig();
        Map<String, String> httpClientDefaultRequestHeaders = httpCfg.getHttpClientDefaultRequestHeaders();
        httpClientDefaultRequestHeaders.keySet().forEach(key -> {
            String value = httpClientDefaultRequestHeaders.get(key);
            reqBuilder.setHeader(key, value);
        });
        reqBuilder.timeout(Duration.ofMillis(httpCfg.getHttpRequestTimeoutMs()));
    }

    @Override
    public <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest.Builder httpRequestBuilder, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException {
        configure(httpRequestBuilder);
        HttpRequest httpRequest = httpRequestBuilder.build();
        return rpc(context, updateContext, httpRequest, isStreaming, goodStatus);
    }

    @Override
    public <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest httpRequest, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException {
        String originRequestBody = getHttpRequestBody(httpRequest);
        return rpcEx(context, updateContext, httpRequest, originRequestBody, isStreaming, goodStatus);
    }


    @Override
    public <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, RpcResult<T> rpcResult, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException {
        return rpcEx(context, updateContext, rpcResult.originRequest(), rpcResult.originRequestBody(), isStreaming, goodStatus);
    }

    /**
     *
     * @param context
     * @param updateContext     if true, the context will be updated with error and status if any error occurs, otherwise the context will not be updated
     * @param httpRequest
     * @param originRequestBody it will be used for logging only, not for sending to remote server, so it can be null if the request has no body
     * @param isStreaming       response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param goodStatus        expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    protected <T> RpcResult<T> rpcEx(SessionContext context, boolean updateContext, HttpRequest httpRequest, String originRequestBody, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException {
        long requestCount = concurrentRequestCount.incrementAndGet();
        String requestId = requestCount + "_";
        //1. log memo
        context.memo(requestId + RpcMemo.MEMO_RPC_REQUEST, httpRequest.toString() + " caller=" + context.caller());
        if (originRequestBody != null) {
            context.memo(requestId + RpcMemo.MEMO_RPC_REQUEST_DATA, originRequestBody);
        }
        //2. call remote sever
        HttpResponse<String> httpResponse1 = null;
        HttpResponse<InputStream> httpResponse2 = null;

        HttpClientConfig httpCfg = getConfig();
        final Semaphore concurrentRequestLimiter = httpCfg.getConcurrentRequestLimiter();
        final Long concurrentRequestQueuingTimeoutMs = httpCfg.getConcurrentRequestQueuingTimeoutMs();
        boolean acquired = false;

        if (concurrentRequestLimiter != null) {
            context.poi(requestId + BootPoi.RPC_QUEUING);
            try {
                if (concurrentRequestQueuingTimeoutMs == null) {
                    concurrentRequestLimiter.acquire();
                } else {
                    if (!concurrentRequestLimiter.tryAcquire(concurrentRequestQueuingTimeoutMs, TimeUnit.MILLISECONDS)) {
                        String msg = "Concurrent request limit exceeded: max = " + httpCfg.getMaxAllowedConcurrentRequests() + ", queuing timeout = " + concurrentRequestQueuingTimeoutMs + " ms";
                        msg = buildError(requestId, null, msg, BootErrorCode.HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT, HttpResponseStatus.TOO_MANY_REQUESTS, updateContext, context);
                        throw new RejectedExecutionException(msg);
                    }
                }
                acquired = true;

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                String msg = buildError(requestId, ex, "HttpClient queuing interrupted", BootErrorCode.APP_INTERRUPTED, HttpResponseStatus.SERVICE_UNAVAILABLE, updateContext, context);
                RejectedExecutionException rejectEx = new RejectedExecutionException(msg);
                rejectEx.initCause(ex);
                throw rejectEx;
            }
        }
        try {
            HttpClient sharedHttpClient = httpCfg.getHttpClient();
            context.poi(requestId + BootPoi.RPC_BEGIN);
            if (isStreaming) {
                // WARNING#1: the response body will not be logged unless an error occurs, so the caller should handle the response body stream properly to avoid resource leak
                // WARNING#2: the concurrentRequestLimiter will be released before the response body stream is closed
                httpResponse2 = sharedHttpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            } else {
                httpResponse1 = sharedHttpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            String msg = buildError(requestId, ex, "HttpClient interrupted", BootErrorCode.APP_INTERRUPTED, HttpResponseStatus.SERVICE_UNAVAILABLE, updateContext, context);
            CancellationException cancelEx = new CancellationException(msg);
            cancelEx.initCause(ex);
            throw cancelEx;
        } catch (HttpTimeoutException ex) {
            String msg = buildError(requestId, ex, "HttpClient gateway timeout", BootErrorCode.HTTP_REQUEST_TIMEOUT, HttpResponseStatus.GATEWAY_TIMEOUT, updateContext, context);
            throw new IOException(msg, ex);
        } catch (Throwable ex) {
            Throwable cause = getRootCause(ex);
            final String msg;
            if (cause instanceof HttpTimeoutException) {
                msg = buildError(requestId, ex, "HttpClient gateway timeout", BootErrorCode.HTTP_REQUEST_TIMEOUT, HttpResponseStatus.GATEWAY_TIMEOUT, updateContext, context);
            } else if (cause instanceof javax.net.ssl.SSLException) {
                // networkaddress.cache.ttl = 5
                // networkaddress.cache.negative.ttl = 2
                // At the moment a certificate becomes invalid or a handshake fails, capture the full list of candidate IP addresses resolved for the current domain name.
                String candidateIps = UrlSanitizer.resolveHostIps(httpRequest.uri());
                msg = buildError(requestId, ex, "HttpClient SSL handshake failed [ips=" + candidateIps + "]", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            } else {
                msg = buildError(requestId, ex, "HttpClient exception", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            }
            throw new IOException(msg, ex);
        } finally {
            context.poi(requestId + BootPoi.RPC_END);
            if (acquired) {
                concurrentRequestLimiter.release();
            }
        }

        // 3a. check remote success or not
        boolean isRemoteSuccess = false;
        HttpResponse<?> httpResponse = isStreaming ? httpResponse2 : httpResponse1;
        int statusCode = httpResponse.statusCode();
        if (goodStatus == null || goodStatus.length < 1) {
            isRemoteSuccess = (statusCode >= HttpResponseStatus.OK.code() && statusCode <= 299);
        } else {
            for (HttpResponseStatus successStatus : goodStatus) {// a simple loop is way faster than Arrays
                if (statusCode == successStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }

        //3b. update status
        context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE, HttpResponseStatus.valueOf(statusCode) + " " + httpResponse.headers());
        try {
            RpcResult<T> rpcResult = new RpcResult<>(requestCount, httpRequest, originRequestBody, httpResponse, isRemoteSuccess, isStreaming, getConfig(), context);
            context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE_DATA, rpcResult.httpResponseBody());
            return rpcResult;
        } catch (UncheckedIOException ex) {
            IOException closeEx = RpcResult.closeHttpResponseStream(httpResponse2);
            if (closeEx != null) {
                ex.addSuppressed(closeEx);
            }
            String msg = buildError(requestId, ex, "Failed to build RpcResult", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            throw new IOException(msg, ex);
        } catch (Throwable ex) {
            IOException closeEx = RpcResult.closeHttpResponseStream(httpResponse2);
            if (closeEx != null && closeEx != ex) {
                ex.addSuppressed(closeEx);
            }
            buildError(requestId, ex, "Failed to build RpcResult", BootErrorCode.IO_BASE, HttpResponseStatus.INTERNAL_SERVER_ERROR, updateContext, context);
            throw ex;
        }
    }

    protected static Throwable getRootCause(Throwable ex) {
        Throwable cause = ExceptionUtils.getRootCause(ex);
        if (cause == null) {
            return ex;
        }
        return cause;
    }

    protected String buildError(String requestId, Throwable ex, String title, int errorCode, HttpResponseStatus status, boolean updateContext, SessionContext context) {
        String msg;
        if (ex == null) {
            msg = title;
        } else {
            Throwable cause = getRootCause(ex);
            msg = title + ": " + cause;
        }
        context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE, msg);
        msg = requestId + "rpc: " + msg;
        if (updateContext) {
            Err e = new Err(errorCode, null, msg, ex);
            context.status(status).error(e);
        }
        return msg;
    }

    /**
     * Convert form data in key-pairs (Map) to form request body (string), also
     * need to set request header:
     * Content-Type=application/x-www-form-urlencoded
     *
     * @param data
     * @return
     */
    public static String convertFormDataToString(Map<Object, Object> data) {
        StringBuilder sb = new StringBuilder();
        data.entrySet().forEach(entry -> {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    public static String getHttpRequestBody(HttpRequest httpRequest) {
        String reqBody = null;
        Optional<HttpRequest.BodyPublisher> pub = httpRequest.bodyPublisher();
        if (pub.isPresent()) {
            reqBody = pub.map(p -> {
                var bodySubscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
                var flowSubscriber = new HttpClientStringSubscriber(bodySubscriber);
                p.subscribe(flowSubscriber);
                return bodySubscriber.getBody().toCompletableFuture().join();
            }).get();
        }
        return reqBody;
    }
}
