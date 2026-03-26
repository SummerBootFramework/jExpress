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
import org.summerboot.jexpress.nio.server.SessionContext;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public interface RPCDelegate {
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

    static String getHttpRequestBody(HttpRequest req) {
        String reqBody = null;
        Optional<HttpRequest.BodyPublisher> pub = req.bodyPublisher();
        if (pub.isPresent()) {
            reqBody = pub.map(p -> {
                var bodySubscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
                var flowSubscriber = new HTTPClientStringSubscriber(bodySubscriber);
                p.subscribe(flowSubscriber);
                return bodySubscriber.getBody().toCompletableFuture().join();
            }).get();
        }
        return reqBody;
    }

    <T> RPCResult<T> rpcEx(SessionContext sessionContext, HttpRequest.Builder reqBuilder, HttpResponseStatus... successStatusList) throws IOException;

    /**
     * @param <T>
     * @param sessionContext
     * @param req
     * @param successStatusList
     * @return
     * @throws IOException
     */
    <T> RPCResult<T> rpcEx(SessionContext sessionContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException;

    /**
     * Need to call RPCResult.update(...) to deserialize JSON to success/error
     * result
     *
     * @param <T>
     * @param context
     * @param originRequest
     * @param originRequestBody
     * @param successStatusList
     * @return a Non-Null RPCResult
     * @throws IOException
     */
    <T> RPCResult<T> rpcEx(SessionContext context, HttpRequest originRequest, String originRequestBody, HttpResponseStatus... successStatusList) throws IOException;

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
    <T> RPCResult<T> rpcEx(SessionContext context, RPCResult<T> request, HttpResponseStatus... successStatusList) throws IOException;
}
