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
package org.summerframework.nio.client;

import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.BootPOI;
import org.summerframework.nio.server.HttpConfig;
import org.summerframework.nio.server.domain.ServiceContext;
import org.summerframework.nio.server.domain.Err;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.summerframework.nio.server.domain.ServiceErrorConvertible;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
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
    protected <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(ServiceContext serviceContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException {
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
     * Need to call RPCResult.update(...) to deserialize JSON to success/error
     * result
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
    protected <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(ServiceContext context, HttpRequest req, String reqbody, HttpResponseStatus... successStatusList) throws IOException {
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
        RPCResult rpcResult = new RPCResult(httpResponse, isRemoteSuccess);
        String rpcResponseJsonBody = rpcResult.httpResponseBody();
        context.memo(RPCMemo.MEMO_RPC_RESPONSE, rpcResult.httpStatusCode() + " " + httpResponse.headers());
        context.memo(RPCMemo.MEMO_RPC_RESPONSE_DATA, rpcResponseJsonBody);
        //rpcResult.update(successResponseClass, errorResponseClass, context);
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
    protected <T, E extends ServiceErrorConvertible> RPCResult<T, E> onInterrupted(HttpRequest req, ServiceContext serviceContext, Throwable ex) {
        Err e = new Err(BootErrorCode.APP_INTERRUPTED, null, "RPC Interrupted", ex);
        serviceContext.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
        return new RPCResult(null, false);
    }

}
