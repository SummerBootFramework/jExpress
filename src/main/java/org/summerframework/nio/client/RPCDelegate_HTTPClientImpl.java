/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.nio.client;

import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.BootPOI;
import org.summerframework.nio.server.HttpConfig;
import org.summerframework.nio.server.domain.ServiceContext;
import org.summerframework.nio.server.domain.Error;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public abstract class RPCDelegate_HTTPClientImpl {

    /**
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

    /**
     *
     * @param <T>
     * @param <E>
     * @param serviceContext
     * @param req
     * @param successStatusList
     * @return
     * @throws IOException
     */
    protected <T, E> RPCResult<T, E> rpcEx(ServiceContext serviceContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException {
        String reqbody = null;
        Optional<HttpRequest.BodyPublisher> pub = req.bodyPublisher();
        if (pub.isPresent()) {
            reqbody = pub.map(p -> {
                var bodySubscriber = BodySubscribers.ofString(StandardCharsets.UTF_8);
                var flowSubscriber = new HTTPClientStringSubscriber(bodySubscriber);
                p.subscribe(flowSubscriber);
                return bodySubscriber.getBody().toCompletableFuture().join();
            }).get();
        }
        return this.rpcEx(serviceContext, req, reqbody, successStatusList);
    }

    /**
     *
     * @param <T>
     * @param <E>
     * @param context
     * @param req
     * @param reqbody
     * @param successStatusList
     * @return a Non-Null RPCResult
     * @throws IOException
     */
    protected <T, E> RPCResult<T, E> rpcEx(ServiceContext context, HttpRequest req, String reqbody, HttpResponseStatus... successStatusList) throws IOException {
        //1. log memo
        context.memo(RPCMemo.MEMO_RPC_REQUEST, req.toString() + " caller=" + context.caller());
        if (reqbody != null) {
            context.memo(RPCMemo.MEMO_RPC_REQUEST_DATA, reqbody);
        }
        //2. call remote sever
        HttpResponse httpResponse;
        context.timestampPOI(BootPOI.RPC_BEGIN);
        try {
            httpResponse = HttpConfig.CFG.getHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return onInterrupted(req, context, ex);
        } finally {
            context.timestampPOI(BootPOI.RPC_END);
        }
        //3a. update status
        String rpcResponseJsonBody = String.valueOf(httpResponse.body());
        RPCResult rpcResult = new RPCResult(httpResponse, rpcResponseJsonBody);
        context.memo(RPCMemo.MEMO_RPC_RESPONSE, rpcResult.getStatusCode() + " " + httpResponse.headers());
        context.memo(RPCMemo.MEMO_RPC_RESPONSE_DATA, rpcResponseJsonBody);
        context.status(rpcResult.getStatus());

        // 3b. check remote success or not
        boolean isRemoteSuccess = false;
        if (successStatusList == null || successStatusList.length < 1) {
            isRemoteSuccess = rpcResult.getStatusCode() == HttpResponseStatus.OK.code();
        } else {
            for (HttpResponseStatus successStatus : successStatusList) {// a simple loop is way faster than Arrays
                if (rpcResult.getStatusCode() == successStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }
        rpcResult.setRemoteSuccess(isRemoteSuccess);

        return rpcResult;
    }

    /**
     *
     * @param <T>
     * @param <E>
     * @param req
     * @param serviceContext
     * @param ex
     * @return
     */
    protected <T, E> RPCResult<T, E> onInterrupted(HttpRequest req, ServiceContext serviceContext, Throwable ex) {
        Error e = new Error(BootErrorCode.APP_INTERRUPTED, null, "RPC Interrupted", ex);
        serviceContext.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
        return null;
    }

}
