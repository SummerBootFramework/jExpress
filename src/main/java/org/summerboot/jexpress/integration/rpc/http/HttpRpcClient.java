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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class HttpRpcClient implements RpcClient {

    protected static AtomicLong requestSequence = new AtomicLong(0);

    @Override
    public <T, B> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest httpRequest, String originRequestBody, HttpResponse.BodyHandler<B> responseBodyHandler, HttpResponseStatus... goodStatus) throws IOException {
        Objects.requireNonNull(httpRequest, "httpRequest must not be null");
        Objects.requireNonNull(responseBodyHandler, "responseBodyHandler must not be null");
        HttpClientConfig httpCfg = Objects.requireNonNull(getConfig(), "HttpClientConfig must not be null");

        long requestSeq = requestSequence.incrementAndGet();
        String requestId = requestSeq + "_";
        //1. log memo
        if (context != null) {
            context.memo(requestId + RpcMemo.MEMO_RPC_REQUEST, httpRequest + " caller=" + context.caller());
            if (originRequestBody != null) {
                context.memo(requestId + RpcMemo.MEMO_RPC_REQUEST_DATA, originRequestBody);
            }
        }

        // 2. Acquire semaphore for concurrent request limit
        final Semaphore concurrentRequestLimiter = httpCfg.getConcurrentRequestLimiter();
        final Long concurrentRequestQueuingTimeoutMs = httpCfg.getConcurrentRequestQueuingTimeoutMs();
        boolean acquired = false;

        if (concurrentRequestLimiter != null) {
            if (context != null) {
                context.poi(requestId + BootPoi.RPC_QUEUING);
            }
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

        // 3. Do the actual HTTP request and handle the response
        HttpResponse<B> httpResponse = null;
        String candidateIps = "n/a";
        try {
            HttpClient sharedHttpClient = httpCfg.getHttpClient();
            if (context != null) {
                context.poi(requestId + BootPoi.RPC_BEGIN);
            }
            candidateIps = HttpClientUtil.resolveHostIps(httpRequest.uri()); // immediate to send to capture the full list of candidate IP addresses resolved for the current domain name

            // WARNING#1 - streaming: response body is not logged unless an error occurs; caller must consume or close RpcResult
            // WARNING#2 - streaming: concurrentRequestLimiter is released once headers/initial response arrive
            httpResponse = sharedHttpClient.send(httpRequest, responseBodyHandler);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            String msg = buildError(requestId, ex, "HttpClient interrupted", BootErrorCode.APP_INTERRUPTED, HttpResponseStatus.SERVICE_UNAVAILABLE, updateContext, context);
            CancellationException cancelEx = new CancellationException(msg);
            cancelEx.initCause(ex);
            throw cancelEx;
        } catch (HttpTimeoutException ex) {
            String msg = buildError(requestId, ex, "HttpClient gateway timeout [ips=" + candidateIps + "]", BootErrorCode.HTTP_REQUEST_TIMEOUT, HttpResponseStatus.GATEWAY_TIMEOUT, updateContext, context);
            throw new IOException(msg, ex);
        } /*catch (SSLException ex) {
            String candidateIps = UrlSanitizer.resolveHostIps(httpRequest.uri());
            String msg = buildError(requestId, ex, "HttpClient SSL handshake failed [ips=" + candidateIps + "]", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            throw new IOException(msg, ex);
        }*/ catch (Exception ex) {
            Throwable cause = getRootCause(ex);
            final String msg;
            if (cause instanceof HttpTimeoutException) {
                msg = buildError(requestId, ex, "HttpClient gateway timeout [ips=" + candidateIps + "]", BootErrorCode.HTTP_REQUEST_TIMEOUT, HttpResponseStatus.GATEWAY_TIMEOUT, updateContext, context);
            } else {
                msg = buildError(requestId, ex, "HttpClient exception [ips=" + candidateIps + "]", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            }
            throw new IOException(msg, ex);
        } finally {
            if (context != null) {
                context.poi(requestId + BootPoi.RPC_END);
                context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE_IPS, candidateIps);
            }
            if (acquired) {
                concurrentRequestLimiter.release();
            }
        }

        // 4. Check remote success or not
        boolean isRemoteSuccess = false;
        int statusCode = httpResponse.statusCode();
        if (goodStatus == null || goodStatus.length == 0) {
            isRemoteSuccess = (statusCode >= 200 && statusCode <= 299);
        } else {
            for (HttpResponseStatus successStatus : goodStatus) {// a simple loop is way faster than Arrays
                if (statusCode == successStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }

        // 5. Build RpcResult and return
        if (context != null) {
            context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE, HttpResponseStatus.valueOf(statusCode) + " " + httpResponse.headers());
        }
        try {
            RpcResult<T> rpcResult = new RpcResult<>(requestSeq, httpRequest, originRequestBody, httpResponse, isRemoteSuccess, httpCfg, context);
            if (context != null) {
                context.memo(requestId + RpcMemo.MEMO_RPC_RESPONSE_DATA, rpcResult.httpResponseBody());
            }
            return rpcResult;
        } catch (UncheckedIOException ex) {
            Exception closeEx = HttpClientUtil.closeHttpResponseStream(httpResponse);
            if (closeEx != null) {
                ex.addSuppressed(closeEx);
            }
            String msg = buildError(requestId, ex, "Failed to build RpcResult", BootErrorCode.IO_BASE, HttpResponseStatus.BAD_GATEWAY, updateContext, context);
            throw new IOException(msg, ex);
        } catch (Exception ex) {
            Exception closeEx = HttpClientUtil.closeHttpResponseStream(httpResponse);
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

}
