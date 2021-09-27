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
package org.summerframework.nio.server;

import org.summerframework.nio.server.annotation.Controller;
import org.summerframework.nio.server.domain.Error;
import org.summerframework.nio.server.domain.ServiceError;
import org.summerframework.nio.server.domain.ServiceResponse;
import org.summerframework.security.auth.Caller;
import com.google.inject.Inject;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.BootPOI;
import org.summerframework.nio.server.ws.rs.JaxRsRequestProcessorManager;
import io.netty.handler.codec.DecoderException;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
@Sharable
public abstract class NioServerHttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    protected Logger log = LogManager.getLogger(this.getClass());

    public NioServerHttpRequestHandler() {
        super(FullHttpRequest.class, false);//set AutoRelease to false to enable keepalive
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        log.trace(() -> evt + " - " + info(ctx));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //a new client is connected
        long tc = NioServerContext.COUNTER_ACTIVE_CHANNEL.incrementAndGet();
        log.trace(() -> tc + " - " + info(ctx));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NioServerContext.COUNTER_ACTIVE_CHANNEL.decrementAndGet();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) {
        if (ex instanceof DecoderException) {
            log.warn(ctx.channel().remoteAddress() + ": " + ex);
        } else {
            log.warn(ctx.channel().remoteAddress() + ": " + ex, ex);
        }
        if (ex instanceof OutOfMemoryError) {
            ctx.close();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        log.trace(() -> info(ctx));
        ctx.flush();
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest req) {
        final long start = System.currentTimeMillis();
        if (!req.decoderResult().isSuccess()) {
            NioHttpUtil.sendError(ctx, HttpResponseStatus.BAD_REQUEST, BootErrorCode.NIO_BAD_REQUEST, "failed to decode request", null);
            return;
        }
        final NioConfig cfg = NioConfig.CFG;
        NioServerContext.COUNTER_HIT.incrementAndGet();
        final long hitIndex = NioServerContext.COUNTER_BIZ_HIT.incrementAndGet();
//        if (HttpUtil.is100ContinueExpected(req)) {
//            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER));
//        }
        long dataSize = req.content().capacity();
//        if (dataSize > _5MB) {
//            ServiceError e = new ServiceError(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, null, "Upload file cannot over 5MB", null);
//            ServiceResponse response = ServiceResponse.build(hitIndex).txt(e.toJson()).status(HttpResponseStatus.INSUFFICIENT_STORAGE).errorCode(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT).level(Level.DEBUG);
//            NioHttpUtil.sendText(ctx, true, null, response.status(), response.txt(), response.contentType(), true);
//            return;
//        }
        final HttpMethod httpMethod = req.method();
        final String httpRequestUri = req.uri();
        final boolean isKeepAlive = HttpUtil.isKeepAlive(req);
        final String infoReceived = infoReceived(ctx, hitIndex, httpMethod, httpRequestUri, isKeepAlive, dataSize);
        log.debug(() -> infoReceived);

        final HttpHeaders httpHeaders = req.headers();
        final String httpPostRequestBody;
        if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod) || HttpMethod.PATCH.equals(httpMethod) || HttpMethod.DELETE.equals(httpMethod)) {
            httpPostRequestBody = NioHttpUtil.getHttpPostBodyString(req);
        } else {
            httpPostRequestBody = null;
        }
        ReferenceCountUtil.release(req);
        final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequestUri, StandardCharsets.UTF_8);

        Runnable asyncTask = () -> {
            long queuingTime = System.currentTimeMillis() - start;
            ServiceResponse response = ServiceResponse.build(ctx, hitIndex, start).headers(HttpConfig.CFG.getServerDefaultResponseHeaders());
            String acceptCharset = httpHeaders.get(HttpHeaderNames.ACCEPT_CHARSET);
            if (StringUtils.isNotBlank(acceptCharset)) {
                response.charsetName(acceptCharset);//.contentType(ServiceResponse.CONTENT_TYPE_JSON_ + acceptCharset); do not build content type with charset now, don't know charset valid or not
            }
            long responseContentLength = -1;
            Throwable ioEx = null;
            long processTime = -1;
            try {
                service(ctx, httpHeaders, httpMethod, queryStringDecoder.path(), queryStringDecoder.parameters(), httpPostRequestBody, response);
                processTime = System.currentTimeMillis() - start;
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, response);
                response.timestampPOI(BootPOI.SERVICE_END);
            } catch (Throwable ex) {
                ioEx = ex;
                Error e = new Error(BootErrorCode.NIO_UNEXPECTED_SERVICE_FAILURE, null, "Failed to send response to client", ex);
                response.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, response);
            } finally {
                long responseTime = System.currentTimeMillis() - start;
                NioServerContext.COUNTER_SENT.incrementAndGet();
                Level level = response.level();
                String report = null;
                if (log.isEnabled(level)) {
                    Caller caller = response.caller();
                    ServiceError error = response.error();
                    boolean overtime = responseTime > cfg.getBizTimeoutWarnThreshold();
                    if (overtime && level.isLessSpecificThan(Level.WARN)) {
                        level = Level.WARN;
                    }

                    //responsed#1=200 OK, error=0, r2q=7ms, r2r=60ms, caller=aaa#bbb, received#1=GET /a
                    StringBuilder sb = new StringBuilder();
                    //line1
                    sb.append("request_").append(hitIndex).append(".caller=").append(caller == null ? response.callerId() : caller);
                    //line2,3
                    sb.append("\n\t").append(infoReceived).append("\n\tresponsed_").append(hitIndex).append("=").append(response.status())
                            .append(", error=").append(error == null ? 0 : error.getErrors().size())
                            .append(", queuing=").append(queuingTime).append("ms, process=").append(processTime);
                    if (overtime) {
                        sb.append("ms, response.ot=");
                    } else {
                        sb.append("ms, response=");
                    }
                    sb.append(responseTime).append("ms, cont.len=").append(responseContentLength).append("bytes");
                    //line4
                    response.reportPOI(cfg, sb);
                    VerboseClientServerCommunication(cfg, httpHeaders, httpPostRequestBody, response, sb);
                    response.reportMemo(sb);
                    report = scrapeLogging(sb.toString());
                    log.log(level, report, response.cause());
                }
                try {
                    onServiceFinal_ResponseSent_LogSaved(httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, response, queuingTime, processTime, responseTime, responseContentLength, report, ioEx);
                } catch (Throwable ex) {
                    log.error("report failed", ex);
                }
            }
        };
        try {
            cfg.getBizExecutor().execute(asyncTask);
        } catch (RejectedExecutionException ex) {
            long queuingTime = System.currentTimeMillis() - start;
            ServiceResponse response = ServiceResponse.build(ctx, hitIndex, start).headers(HttpConfig.CFG.getServerDefaultResponseHeaders());
            Error e = new Error(BootErrorCode.NIO_TOO_MANY_REQUESTS, null, "Too many requests", ex);
            response.error(e).status(HttpResponseStatus.TOO_MANY_REQUESTS).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, response);

            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(hitIndex).append("=").append(ex.toString())
                    .append("ms\n\t").append(infoReceived).append("\n\tresponsed#").append(hitIndex)
                    .append("=").append(response.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(httpHeaders)
                    .append("\n\t4resp.body=").append(response.txt());
            log.fatal(sb.toString());
        } catch (Throwable ex) {
            long queuingTime = System.currentTimeMillis() - start;
            ServiceResponse response = ServiceResponse.build(ctx, hitIndex, start).headers(HttpConfig.CFG.getServerDefaultResponseHeaders());
            Error e = new Error(BootErrorCode.NIO_UNEXPECTED_EXECUTOR_FAILURE, null, "NIO unexpected executor failure", ex);
            response.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, response);
            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(hitIndex).append("=").append(ex.toString())
                    .append("ms\n\t").append(infoReceived).append("\n\tresponsed#").append(hitIndex)
                    .append("=").append(response.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(httpHeaders)
                    .append("\n\t4resp.body=").append(response.txt());
            log.fatal(sb.toString());
        }
    }

    private final String me = ", hdl=" + this.toString();

    private String info(ChannelHandlerContext ctx) {
        return new StringBuilder()
                .append(", chn=").append(ctx.channel())
                .append(", ctx=").append(ctx.hashCode())
                .append(me)
                .toString();
    }

    private String infoReceived(ChannelHandlerContext ctx, long hitIndex, HttpMethod httpMethod, String httpRequestUri, boolean isKeepAlive, long dataSize) {
        return new StringBuilder()
                .append("request_").append(hitIndex)
                .append("=").append(httpMethod).append(" ").append(httpRequestUri)
                .append(", dataSize=").append(dataSize)
                .append(", KeepAlive=").append(isKeepAlive)
                .append(", chn=").append(ctx.channel())
                .append(", ctx=").append(ctx.hashCode())
                .append(me)
                .toString();
    }

    private void VerboseClientServerCommunication(NioConfig cfg, HttpHeaders httpHeaders, String httpPostRequestBody, ServiceResponse response, StringBuilder sb) {
        boolean isInFilter = false;
        // 3a. caller filter
        Caller caller = response.caller();
        switch (cfg.getFilterUserType()) {
            case ignore:
                isInFilter = true;
                break;
            case uid:
                if (StringUtils.isNotBlank(response.callerId())) {
                    isInFilter = cfg.getFilterCallerNameSet().contains(response.callerId());
                }
                break;
            case id:
                if (caller != null && caller.getId() != null) {
                    Long target = caller.getId().longValue();
                    Set<Long> s = cfg.getFilterCallerIdSet();
                    if (s == null) {
                        isInFilter = target >= cfg.getFilterCallerIdFrom() && target <= cfg.getFilterCallerIdTo();
                    } else {
                        isInFilter = s.contains(target);
                    }
                }
                break;
            case group:
                if (caller != null) {
                    isInFilter = cfg.getFilterCallerNameSet().stream().anyMatch((target) -> (caller.isInGroup(target)));
                }
                break;
            case role:
                if (caller != null) {
                    isInFilter = cfg.getFilterCallerNameSet().stream().anyMatch((target) -> (caller.isInRole(target)));
                }
                break;
        }
        if (!isInFilter) {
            return;
        }
        // 3b. code filter
        isInFilter = false;
        Set<Long> s = cfg.getFilterCodeSet();
        switch (cfg.getFilterCodeType()) {
            case all:
                isInFilter = true;
                break;
            case ignore:
                isInFilter = false;
                break;
            case HttpStatusCode:
                long target = response.status().code();
                if (s == null) {
                    isInFilter = target >= cfg.getFilterCodeRangeFrom() && target <= cfg.getFilterCodeRangeTo();
                } else {
                    isInFilter = s.contains(target);
                }
                break;
            case AppErrorCode:
                ServiceError e = response.error();
                if (e == null) {
                    break;
                }
                for (Error j : e.getErrors()) {
                    target = j.getErrorCode();
                    if (s == null) {
                        isInFilter = target >= cfg.getFilterCodeRangeFrom() && target <= cfg.getFilterCodeRangeTo();
                    } else {
                        isInFilter = s.contains(target);
                    }
                    if (isInFilter) {
                        break;
                    }
                }
//                target = response.errorCode();
//                if (s == null) {
//                    isInFilter = target >= cfg.getFilterCodeRangeFrom() && target <= cfg.getFilterCodeRangeTo();
//                } else {
//                    isInFilter = s.contains(target);
//                }
                break;
        }
        if (!isInFilter) {
            return;
        }

        // 3c. verbose aspect
        // 3.1 request headers
        if (!response.privacyReqHeader() && cfg.isVerboseReqHeader()) {
            sb.append("\n\t1.client_req.headers=").append(httpHeaders);
        }
        // 3.2 request body
        if (!response.privacyReqContent() && cfg.isVerboseReqContent()) {
            sb.append("\n\t2.client_req.body=").append(httpPostRequestBody);
        }
        // 3.3 response headers
        if (!response.privacyRespHeader() && cfg.isVerboseRespHeader()) {
            sb.append("\n\t3.server_resp.headers=").append(response.headers());
        }
        // 3.4 response body
        if (!response.privacyRespContent() && cfg.isVerboseRespContent()) {
            sb.append("\n\t4.server_resp.body=").append(response.txt());
        }
    }

    abstract protected void service(final ChannelHandlerContext ctx, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceResponse response);

    abstract protected String scrapeLogging(String originalResponse);

    abstract protected void onServiceFinal_ResponseSent_LogSaved(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
            final ServiceResponse response, long queuingTime, long processTime, long responseTime, long responseContentLength, String report, Throwable ioEx) throws Exception;

    /**
     * callback by Guice injection
     * <p>
     * triggered by
     * <code>bindControllers(binder(), "ca.projectname", Controller.class);</code>
     * to load all classes annotated with @Controller
     *
     *
     * @param controllers
     */
    @Inject
    protected void guiceCallback_RegisterControllers(@Controller Map<String, Object> controllers) {
        JaxRsRequestProcessorManager.registerControllers(controllers);
    }

    protected RequestProcessor getRequestProcessor(final HttpMethod httptMethod, final String httpRequestPath) {
        return JaxRsRequestProcessorManager.getRequestProcessor(httptMethod, httpRequestPath);
    }
}
