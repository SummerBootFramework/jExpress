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
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.BootPoi;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.api.rpc.RpcDelegate;
import org.summerboot.jexpress.api.rpc.RpcMemo;
import org.summerboot.jexpress.api.rpc.RpcResult;
import org.summerboot.jexpress.integration.rpc.http.config.HttpClientConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class RpcDelegateHttpClientImpl implements RpcDelegate {

    abstract protected HttpClientConfig getHttpClientConfig();


    /**
     * set default headers; proxy auth; timeout
     *
     * @param reqBuilder
     */
    protected void configure(HttpRequest.Builder reqBuilder) {
        HttpClientConfig httpCfg = getHttpClientConfig();
        Map<String, String> httpClientDefaultRequestHeaders = httpCfg.getHttpClientDefaultRequestHeaders();
        httpClientDefaultRequestHeaders.keySet().forEach(key -> {
            String value = httpClientDefaultRequestHeaders.get(key);
            reqBuilder.setHeader(key, value);
        });
        reqBuilder.timeout(Duration.ofMillis(httpCfg.getHttpRequestTimeoutMs()));
    }

    @Override
    public <T> RpcResult<T> rpc(SessionContext context, HttpRequest.Builder httpRequestBuilder, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException {
        configure(httpRequestBuilder);
        HttpRequest httpRequest = httpRequestBuilder.build();
        return rpc(context, httpRequest, isStreaming, successStatusList);
    }


    @Override
    public <T> RpcResult<T> rpc(SessionContext context, HttpRequest httpRequest, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException {
        String originRequestBody = RpcDelegate.getHttpRequestBody(httpRequest);
        return rpcEx(context, httpRequest, originRequestBody, isStreaming, successStatusList);
    }

    @Override
    public <T> RpcResult<T> rpc(SessionContext context, RpcResult<T> rpcResult, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException {
        return rpcEx(context, rpcResult.getOriginRequest(), rpcResult.getOriginRequestBody(), isStreaming, successStatusList);
    }

    /**
     *
     * @param context
     * @param httpRequest
     * @param originRequestBody it will be used for logging only, not for sending to remote server, so it can be null if the request has no body
     * @param isStreaming       response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param successStatusList expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>               successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    protected <T> RpcResult<T> rpcEx(SessionContext context, HttpRequest httpRequest, String originRequestBody, boolean isStreaming, HttpResponseStatus... successStatusList) throws IOException {
        //1. log memo
        context.memo(RpcMemo.MEMO_RPC_REQUEST, httpRequest.toString() + " caller=" + context.caller());
        if (originRequestBody != null) {
            context.memo(RpcMemo.MEMO_RPC_REQUEST_DATA, originRequestBody);
        }
        //2. call remote sever
        HttpResponse<String> httpResponse1 = null;
        HttpResponse<InputStream> httpResponse2 = null;
        context.poi(BootPoi.RPC_BEGIN);
        try {
            HttpClientConfig httpCfg = getHttpClientConfig();
            if (isStreaming) {
                httpResponse2 = httpCfg.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            } else {
                httpResponse1 = httpCfg.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Err e = new Err(BootErrorCode.APP_INTERRUPTED, null, "Http Client Interrupted", ex);
            context.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
            RpcResult<T> rpcResult = new RpcResult<>(httpRequest, originRequestBody, null, false, getHttpClientConfig());
            return rpcResult;
        } finally {
            context.poi(BootPoi.RPC_END);
        }

        // 3a. check remote success or not
        boolean isRemoteSuccess = false;
        int statusCode = isStreaming ? httpResponse2.statusCode() : httpResponse1.statusCode();
        if (successStatusList == null || successStatusList.length < 1) {
            isRemoteSuccess = (statusCode >= HttpResponseStatus.OK.code() && statusCode <= 299);
        } else {
            for (HttpResponseStatus successStatus : successStatusList) {// a simple loop is way faster than Arrays
                if (statusCode == successStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }

        //3b. update status   
        RpcResult<T> rpcResult = new RpcResult<>(httpRequest, originRequestBody, isStreaming ? httpResponse2 : httpResponse1, isRemoteSuccess, isStreaming, getHttpClientConfig());
        String rpcResponseJsonBody = rpcResult.httpResponseBody();
        context.memo(RpcMemo.MEMO_RPC_RESPONSE, rpcResult.httpStatusCode() + " " + (isStreaming ? httpResponse2.headers() : httpResponse1.headers()));
        context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA, isStreaming ? "stream data" : rpcResponseJsonBody);

        return rpcResult;
    }
}
