package org.summerboot.jexpress.integration.httpclient;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.ServiceErrorConvertible;

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

    <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext sessionContext, HttpRequest.Builder reqBuilder, HttpResponseStatus... successStatusList) throws IOException;

    /**
     * @param <T>
     * @param <E>
     * @param sessionContext
     * @param req
     * @param successStatusList
     * @return
     * @throws IOException
     */
    <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext sessionContext, HttpRequest req, HttpResponseStatus... successStatusList) throws IOException;

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
    <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext context, HttpRequest originRequest, String originRequestBody, HttpResponseStatus... successStatusList) throws IOException;

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
    <T, E extends ServiceErrorConvertible> RPCResult<T, E> rpcEx(SessionContext context, RPCResult<T, E> request, HttpResponseStatus... successStatusList) throws IOException;
}
