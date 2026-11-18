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
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.integration.rpc.http.HttpClientUtil;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Unified RPC client interface for executing HTTP requests with built-in configuration injection,
 * streaming response handling, and safe resource cleanup during retries.
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RpcClient {

    /**
     * Returns the underlying HTTP client configuration used to populate default headers and mappers.
     *
     * @return the {@link HttpClientConfig} instance, or {@code null} if using system defaults
     */
    HttpClientConfig getConfig();

    // ==================== HttpRequest.Builder Entry Points (Applies Config Default Headers) ====================

    /**
     * Executes an HTTP request built from the given builder, expecting a {@link String} response body,
     * and automatically updates the {@link SessionContext} with request/response telemetry and errors.
     * <p>
     * Default headers from {@link #getConfig()} will be applied to the builder before building the request.
     *
     * @param context            the current session context for logging, memos, and error tracking
     * @param httpRequestBuilder the mutable request builder to configure and send
     * @param goodStatus         optional custom HTTP statuses considered successful (defaults to 2xx if empty)
     * @return an {@link RpcResult} wrapping the {@link String} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<String> rpc(SessionContext context, HttpRequest.Builder httpRequestBuilder, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, httpRequestBuilder, HttpResponse.BodyHandlers.ofString(), goodStatus);
    }

    /**
     * Executes a streaming HTTP request built from the given builder, returning the response body as an {@link InputStream},
     * and automatically updates the {@link SessionContext}.
     * <p>
     * <b>Resource Management:</b> The returned {@link RpcResult} holds an open stream on 2xx responses and must be
     * closed via {@code try-with-resources} or consumed via {@link RpcResult#deserialize}.
     *
     * @param context            the current session context
     * @param httpRequestBuilder the mutable request builder to configure and send
     * @param goodStatus         optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the {@link InputStream} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<InputStream> rpcStream(SessionContext context, HttpRequest.Builder httpRequestBuilder, HttpResponseStatus... goodStatus) throws IOException {
        return rpcStream(context, true, httpRequestBuilder, goodStatus);
    }

    /**
     * Executes a streaming HTTP request built from the given builder, returning the response body as an {@link InputStream}.
     *
     * @param context            the current session context
     * @param updateContext      whether to record RPC telemetry and errors into the {@link SessionContext}
     * @param httpRequestBuilder the mutable request builder to configure and send
     * @param goodStatus         optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the {@link InputStream} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<InputStream> rpcStream(SessionContext context, boolean updateContext, HttpRequest.Builder httpRequestBuilder, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, updateContext, httpRequestBuilder, HttpResponse.BodyHandlers.ofInputStream(), goodStatus);
    }

    /**
     * Configures the given {@link HttpRequest.Builder} with preset headers from {@link #getConfig()},
     * builds the immutable {@link HttpRequest}, and executes it using the specified {@link HttpResponse.BodyHandler}.
     *
     * @param <T>                 the type of the response body
     * @param context             the current session context
     * @param updateContext       whether to record RPC telemetry and errors into the {@link SessionContext}
     * @param httpRequestBuilder  the mutable request builder to configure and send
     * @param responseBodyHandler the handler defining how to process the response body
     * @param goodStatus          optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the response of type {@code T}
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest.Builder httpRequestBuilder, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponseStatus... goodStatus) throws IOException {
        Objects.requireNonNull(httpRequestBuilder, "httpRequestBuilder must not be null");
        HttpClientUtil.configure(httpRequestBuilder, getConfig());
        HttpRequest httpRequest = httpRequestBuilder.build();
        return rpc(context, updateContext, httpRequest, responseBodyHandler, goodStatus);
    }

    // ==================== HttpRequest Entry Points (Preserves Caller-Specified Headers) ====================

    /**
     * Executes a pre-built immutable {@link HttpRequest}, expecting a {@link String} response body,
     * and automatically updates the {@link SessionContext}.
     * <p>
     * Unlike the {@link HttpRequest.Builder} overloads, this method does <b>not</b> inject default headers
     * from {@link #getConfig()}, preserving the exact headers provided by the caller.
     *
     * @param context     the current session context
     * @param httpRequest the pre-built immutable HTTP request
     * @param goodStatus  optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the {@link String} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<String> rpc(SessionContext context, HttpRequest httpRequest, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, httpRequest, HttpResponse.BodyHandlers.ofString(), goodStatus);
    }

    /**
     * Executes a pre-built immutable {@link HttpRequest} as a stream, returning the response body as an {@link InputStream}.
     * <p>
     * Preserves the exact headers on the provided {@link HttpRequest} without applying config defaults.
     *
     * @param context     the current session context
     * @param httpRequest the pre-built immutable HTTP request
     * @param goodStatus  optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the {@link InputStream} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<InputStream> rpcStream(SessionContext context, HttpRequest httpRequest, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, httpRequest, HttpResponse.BodyHandlers.ofInputStream(), goodStatus);
    }

    /**
     * Executes a pre-built immutable {@link HttpRequest} using the specified {@link HttpResponse.BodyHandler}.
     * <p>
     * Preserves the exact headers on the provided {@link HttpRequest} without applying config defaults.
     *
     * @param <T>                 the type of the response body
     * @param context             the current session context
     * @param updateContext       whether to record RPC telemetry and errors into the {@link SessionContext}
     * @param httpRequest         the pre-built immutable HTTP request
     * @param responseBodyHandler the handler defining how to process the response body
     * @param goodStatus          optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the response of type {@code T}
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest httpRequest, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponseStatus... goodStatus) throws IOException {
        Objects.requireNonNull(httpRequest, "httpRequest must not be null");
        String originRequestBody = HttpClientUtil.getHttpRequestBody(httpRequest);
        return rpc(context, updateContext, httpRequest, originRequestBody, responseBodyHandler, goodStatus);
    }

    // ==================== RpcResult Retry Entry Points (Closes Previous Stream & Reuses Request) ====================

    /**
     * Retries a previous RPC call using the original request captured inside the given {@link RpcResult},
     * expecting a {@link String} response body and updating the {@link SessionContext}.
     * <p>
     * Automatically closes any unconsumed stream held by the previous {@code rpcResult} before retrying.
     *
     * @param context    the current session context
     * @param rpcResult  the previous {@link RpcResult} whose original request will be re-executed
     * @param goodStatus optional custom HTTP statuses considered successful
     * @return a new {@link RpcResult} wrapping the retry {@link String} response
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default RpcResult<String> rpc(SessionContext context, RpcResult<?> rpcResult, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, rpcResult, HttpResponse.BodyHandlers.ofString(), goodStatus);
    }

    /**
     * Retries a previous RPC call using the original request and cached request body from the given {@link RpcResult},
     * handling the new response with the specified {@link HttpResponse.BodyHandler}.
     * <p>
     * Automatically closes any unconsumed stream held by the previous {@code rpcResult} before retrying.
     * <p>
     * Use code with caution.
     *
     * @param <T>                 the type of the response body
     * @param context             the current session context
     * @param updateContext       whether to record RPC telemetry and errors into the {@link SessionContext}
     * @param rpcResult           the previous {@link RpcResult} to retry
     * @param responseBodyHandler the handler defining how to process the retry response body
     * @param goodStatus          optional custom HTTP statuses considered successful
     * @return a new {@link RpcResult} wrapping the retry response of type {@code T}
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    default <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, RpcResult<?> rpcResult, HttpResponse.BodyHandler responseBodyHandler, HttpResponseStatus... goodStatus) throws IOException {
        Objects.requireNonNull(rpcResult, "rpcResult must not be null");
        rpcResult.closeQuietly();
        return rpc(context, updateContext, rpcResult.originRequest(), rpcResult.originRequestBody(), responseBodyHandler, goodStatus);
    }
// ==================== Core Abstract Execution Method ====================

    /**
     * Core execution method implemented by concrete RPC clients to send the HTTP request and construct the {@link RpcResult}.
     *
     * @param <T>                 the type of the response body
     * @param context             the current session context
     * @param updateContext       whether to record RPC telemetry and errors into the {@link SessionContext}
     * @param httpRequest         the final immutable HTTP request to send
     * @param originRequestBody   the extracted request payload string for logging and auditing
     * @param responseBodyHandler the handler defining how to process the response body
     * @param goodStatus          optional custom HTTP statuses considered successful
     * @return an {@link RpcResult} wrapping the response of type {@code T}
     * @throws IOException if an I/O error occurs when sending or receiving
     */
    <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest httpRequest, String originRequestBody, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponseStatus... goodStatus) throws IOException;
}