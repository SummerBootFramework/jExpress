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
import org.summerboot.jexpress.integration.rpc.http.HttpClientStringSubscriber;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RpcDelegate {
    /**
     * Convert form data in key-pairs (Map) to form request body (string), also
     * need to set request header:
     * Content-Type=application/x-www-form-urlencoded
     *
     * @param data
     * @return
     */
    static String convertFormDataToString(Map<Object, Object> data) {
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

    static String getHttpRequestBody(HttpRequest httpRequest) {
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

    /**
     * 1a. RPC via HttpRequest.Builder in non-streaming mode
     *
     * @param context
     * @param httpRequestBuilder
     * @param successStatusList  expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>                successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, HttpRequest.Builder httpRequestBuilder, HttpResponseStatus... successStatusList) throws IOException {
        return rpc(context, httpRequestBuilder, false, successStatusList);
    }

    /**
     * 1b. RPC via HttpRequest.Builder
     *
     * @param context
     * @param httpRequestBuilder
     * @param isStreaming        response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param successStatusList  expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>                successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, HttpRequest.Builder httpRequestBuilder, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException;

    /**
     * 2a. RPC via HttpRequest in non-streaming mode
     *
     * @param context
     * @param httpRequest
     * @param successStatusList expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, HttpRequest httpRequest, HttpResponseStatus... successStatusList) throws IOException {
        return rpc(context, httpRequest, false, successStatusList);
    }

    /**
     * 2b. RPC via HttpRequest
     *
     * @param context
     * @param httpRequest
     * @param isStreaming       response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param successStatusList expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, HttpRequest httpRequest, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException;


    /**
     * 3a. Reset request via RpcResult in non-streaming mode
     *
     * @param context
     * @param rpcResult
     * @param successStatusList expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, RpcResult<T> rpcResult, HttpResponseStatus... successStatusList) throws IOException {
        return rpc(context, rpcResult, false, successStatusList);
    }

    /**
     * 3b. Reset request via RpcResult
     *
     * @param context
     * @param rpcResult
     * @param isStreaming       response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param successStatusList expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, RpcResult<T> rpcResult, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException;
}
