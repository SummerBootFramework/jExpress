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
import org.summerframework.nio.server.domain.ServiceResponse;
import org.summerframework.nio.server.domain.Error;
import com.fasterxml.jackson.databind.JavaType;
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

    protected <T, E> RPCResult<T, E> rpcEx(HttpRequest req, JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, ServiceResponse serviceResponse, HttpResponseStatus... expectedStatusList) throws IOException {
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
        return this.rpcEx(req, reqbody, successResponseType, successResponseClass, errorResponseClass, serviceResponse, expectedStatusList);
    }

    /**
     *
     * @param <T>
     * @param <E>
     * @param req
     * @param requestLogInfo
     * @param successResponseType
     * @param successResponseClass
     * @param errorResponseClass
     * @param serviceResponse
     * @param expectedStatusList
     * @return
     * @throws IOException
     */
    protected <T, E> RPCResult<T, E> rpcEx(HttpRequest req, String requestLogInfo, JavaType successResponseType, Class<T> successResponseClass, Class<E> errorResponseClass, ServiceResponse serviceResponse, HttpResponseStatus... expectedStatusList) throws IOException {
        if (req == null) {
            return null;
        }

        serviceResponse.memo(RPCMemo.MEMO_RPC_REQUEST, req.toString() + " caller=" + serviceResponse.caller());
        if (requestLogInfo != null) {
            serviceResponse.memo(RPCMemo.MEMO_RPC_REQUEST_DATA, requestLogInfo);
        }
        // 3. call remote sever
        HttpResponse httpResponse;
        serviceResponse.timestampPOI(BootPOI.RPC_BEGIN);
        try {
            httpResponse = HttpConfig.CFG.getHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return onInterrupted(req, serviceResponse, ex);
        } finally {
            serviceResponse.timestampPOI(BootPOI.RPC_END);
        }
        int rpcResponseStatusCode = httpResponse.statusCode();
        String rpcResponseJsonBody = String.valueOf(httpResponse.body());
        serviceResponse.memo(RPCMemo.MEMO_RPC_RESPONSE, rpcResponseStatusCode + " " + httpResponse.headers());
        serviceResponse.memo(RPCMemo.MEMO_RPC_RESPONSE_DATA, rpcResponseJsonBody);
        HttpResponseStatus rpcHttpStatus = HttpResponseStatus.valueOf(rpcResponseStatusCode);
        serviceResponse.status(rpcHttpStatus);
        // 3a. verify result - check authorized
        boolean isRemoteSuccess = false;
        if (expectedStatusList == null || expectedStatusList.length < 1) {
            isRemoteSuccess = rpcResponseStatusCode == HttpResponseStatus.OK.code();
        } else {
            for (HttpResponseStatus expectedStatus : expectedStatusList) {// a simple loop is way faster than Arrays
                if (rpcResponseStatusCode == expectedStatus.code()) {
                    isRemoteSuccess = true;
                    break;
                }
            }
        }
        if (stopOnRPCResponseStatusCode(rpcHttpStatus, serviceResponse)) {
            return null;
        }

        // 3b. set result: 
        RPCResult rpcResult = new RPCResult(httpResponse, rpcResponseJsonBody, isRemoteSuccess);
        try {
            rpcResult.update(successResponseType, successResponseClass, errorResponseClass);
        } catch (Throwable ex) {
            rpcResult = onUnknownResponseFormat(serviceResponse, ex);
        }
        return rpcResult;
    }

    /**
     *
     * @param rpcHttpStatus
     * @param serviceResponse
     * @return true on 408 or greater than 500
     */
    protected boolean stopOnRPCResponseStatusCode(HttpResponseStatus rpcHttpStatus, ServiceResponse serviceResponse) {
        if (rpcHttpStatus.code() == HttpResponseStatus.REQUEST_TIMEOUT.code()) {// = 408
            Error e = new Error(BootErrorCode.HTTPREQUEST_TIMEOUT, null, "RPC Request Timeout", null);
            serviceResponse.status(HttpResponseStatus.GATEWAY_TIMEOUT).error(e);
            return true;
        }
        if (rpcHttpStatus.code() > HttpResponseStatus.INTERNAL_SERVER_ERROR.code()) {// > 500
            Error e = new Error(BootErrorCode.ACCESS_ERROR_RPC, "RPC Server", "RPC Server Error", null);
            serviceResponse.status(rpcHttpStatus).error(e);
            return true;
        }
        return false;
    }

    /**
     *
     * @param <T>
     * @param <E>
     * @param req
     * @param serviceResponse
     * @param ex
     * @return
     */
    protected <T, E> RPCResult<T, E> onInterrupted(HttpRequest req, ServiceResponse serviceResponse, Throwable ex) {
        Error e = new Error(BootErrorCode.APP_INTERRUPTED, null, "RPC Interrupted", ex);
        serviceResponse.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
        return null;
    }

    /**
     *
     * @param <T>
     * @param serviceResponse
     * @param ex
     * @return
     */
    protected <T extends Object> T onUnknownResponseFormat(ServiceResponse serviceResponse, Throwable ex) {
        Error e = new Error(BootErrorCode.HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT, null, "Unexpected RPC response format", ex);
        serviceResponse.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).error(e);
        return null;
    }
}
