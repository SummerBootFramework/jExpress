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
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceErrorConvertible;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class RPCDelegate_HTTPClientImpl implements RPCDelegate {

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
        String proxyAuth = httpCfg.getProxyAuthorizationBasicValue();
        if (proxyAuth != null) {
            reqBuilder.setHeader("Proxy-Authorization", proxyAuth);
        }
        reqBuilder.timeout(Duration.ofMillis(httpCfg.getHttpClientTimeoutMs()));
    }

    @Override
    public <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext sessionContext, HttpRequest.Builder reqBuilder, HttpResponseStatus... successStatusList) throws IOException {
        configure(reqBuilder);
        HttpRequest req = reqBuilder.build();
        String reqbody = RPCDelegate.getHttpRequestBody(req);
        return this.rpcEx(sessionContext, req, reqbody, successStatusList);
    }

    /**
     * @param <T>
     * @param <E>
     * @param sessionContext
     * @param req
     * @param successStatusList
     * @return
     * @throws IOException
     */
    @Override
    public <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext sessionContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException {
        Optional<HttpRequest.BodyPublisher> pub = req.bodyPublisher();
        String reqbody = RPCDelegate.getHttpRequestBody(req);
        return this.rpcEx(sessionContext, req, reqbody, successStatusList);
    }

    /**
     * Need to call RPCResult.update(...) to deserialize JSON to success/error
     * result
     *
     * @param <T>
     * @param <E>
     * @param context
     * @param originRequest
     * @param originRequestBody
     * @param successStatusList
     * @return a Non-Null RPCResult
     * @throws IOException
     */
    @Override
    public <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext context, HttpRequest originRequest, String originRequestBody, HttpResponseStatus... successStatusList) throws IOException {
        //1. log memo
        context.memo(RPCMemo.MEMO_RPC_REQUEST, originRequest.toString() + " caller=" + context.caller());
        if (originRequestBody != null) {
            context.memo(RPCMemo.MEMO_RPC_REQUEST_DATA, originRequestBody);
        }
        //2. call remote sever
        HttpResponse httpResponse;
        context.poi(BootPOI.RPC_BEGIN);
        try {
            HttpClientConfig httpCfg = getHttpClientConfig();
            httpResponse = httpCfg.getHttpClient().send(originRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Err e = new Err<>(BootErrorCode.APP_INTERRUPTED, null, null, ex, "RPC Interrupted");
            context.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
            return new RPCResult<>(originRequest, originRequestBody, null, false);
        } finally {
            context.poi(BootPOI.RPC_END);
        }

        // 3a. check remote success or not
        boolean isRemoteSuccess = false;
        int statusCode = httpResponse.statusCode();
        if (successStatusList == null || successStatusList.length < 1) {
            isRemoteSuccess = statusCode == HttpResponseStatus.OK.code();
        } else {
            for (HttpResponseStatus successStatus : successStatusList) {// a simple loop is way faster than Arrays
                if (statusCode == successStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }

        //3b. update status   
        RPCResult<T, E> rpcResult = new RPCResult<>(originRequest, originRequestBody, httpResponse, isRemoteSuccess);
        String rpcResponseJsonBody = rpcResult.httpResponseBody();
        context.memo(RPCMemo.MEMO_RPC_RESPONSE, rpcResult.httpStatusCode() + " " + httpResponse.headers());
        context.memo(RPCMemo.MEMO_RPC_RESPONSE_DATA, rpcResponseJsonBody);
        // let caller decide how to process the RPCResult - rpcResult.update(successResponseClass, errorResponseClass, context);
        return rpcResult;
    }

    /**
     * Reset request
     *
     * @param context
     * @param request
     * @param successStatusList
     * @param <T>
     * @param <E>
     * @return
     * @throws IOException
     */
    @Override
    public <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext context, RPCResult<T, E> request, HttpResponseStatus... successStatusList) throws IOException {
        return this.rpcEx(context, request.getOriginRequest(), request.getOriginRequestBody(), successStatusList);
    }

}
