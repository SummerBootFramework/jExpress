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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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
        reqBuilder.timeout(Duration.ofMillis(httpCfg.getHttpClientTimeoutMs()));
    }

    @Override
    public <T> RpcResult<T> rpcEx(SessionContext sessionContext, HttpRequest.Builder reqBuilder, HttpResponseStatus... successStatusList) throws IOException {
        configure(reqBuilder);
        HttpRequest req = reqBuilder.build();
        String reqbody = RpcDelegate.getHttpRequestBody(req);
        return this.rpcEx(sessionContext, req, reqbody, successStatusList);
    }

    /**
     * @param <T>
     * @param sessionContext
     * @param req
     * @param successStatusList
     * @return
     * @throws IOException
     */
    @Override
    public <T> RpcResult<T> rpcEx(SessionContext sessionContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException {
        Optional<HttpRequest.BodyPublisher> pub = req.bodyPublisher();
        String reqbody = RpcDelegate.getHttpRequestBody(req);
        return this.rpcEx(sessionContext, req, reqbody, successStatusList);
    }

    /**
     * Need to call RpcResult.update(...) to deserialize JSON to success/error
     * result
     *
     * @param <T>
     * @param context
     * @param originRequest
     * @param originRequestBody
     * @param successStatusList
     * @return a Non-Null RpcResult
     * @throws IOException
     */
    @Override
    public <T> RpcResult<T> rpcEx(SessionContext context, HttpRequest originRequest, String originRequestBody, HttpResponseStatus... successStatusList) throws IOException {
        //1. log memo
        context.memo(RpcMemo.MEMO_RPC_REQUEST, originRequest.toString() + " caller=" + context.caller());
        if (originRequestBody != null) {
            context.memo(RpcMemo.MEMO_RPC_REQUEST_DATA, originRequestBody);
        }
        //2. call remote sever
        HttpResponse httpResponse;
        context.poi(BootPoi.RPC_BEGIN);
        try {
            HttpClientConfig httpCfg = getHttpClientConfig();
            httpResponse = httpCfg.getHttpClient().send(originRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Err e = new Err(BootErrorCode.APP_INTERRUPTED, null, "Http Client Interrupted", ex);
            context.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
            RpcResult<T> rpcResult = new RpcResult<>(originRequest, originRequestBody, null, false, getHttpClientConfig());
            return rpcResult;
        } finally {
            context.poi(BootPoi.RPC_END);
        }

        // 3a. check remote success or not
        boolean isRemoteSuccess = false;
        int statusCode = httpResponse.statusCode();
        if (successStatusList == null || successStatusList.length < 1) {
            //isRemoteSuccess = statusCode == HttpResponseStatus.OK.code();
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
        RpcResult<T> rpcResult = new RpcResult<>(originRequest, originRequestBody, httpResponse, isRemoteSuccess, getHttpClientConfig());
        String rpcResponseJsonBody = rpcResult.httpResponseBody();
        context.memo(RpcMemo.MEMO_RPC_RESPONSE, rpcResult.httpStatusCode() + " " + httpResponse.headers());
        context.memo(RpcMemo.MEMO_RPC_RESPONSE_DATA, rpcResponseJsonBody);
        // let caller decide how to process the RpcResult - rpcResult.update(successResponseClass, errorResponseClass, ioc);
        return rpcResult;
    }

    /**
     * Reset request
     *
     * @param context
     * @param request
     * @param successStatusList
     * @param <T>
     * @return
     * @throws IOException
     */
    @Override
    public <T> RpcResult<T> rpcEx(SessionContext context, RpcResult<T> request, HttpResponseStatus... successStatusList) throws IOException {
        return this.rpcEx(context, request.getOriginRequest(), request.getOriginRequestBody(), successStatusList);
    }

}
