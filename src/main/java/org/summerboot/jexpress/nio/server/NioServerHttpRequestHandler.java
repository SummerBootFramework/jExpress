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
package org.summerboot.jexpress.nio.server;

import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.auth.Caller;
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
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import io.netty.handler.codec.DecoderException;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Sharable
public abstract class NioServerHttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> implements ErrorAuditor {

    protected Logger log = LogManager.getLogger(this.getClass());

    protected static NioConfig nioCfg = NioConfig.cfg;

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
        long tc = NioCounter.COUNTER_ACTIVE_CHANNEL.incrementAndGet();
        log.trace(() -> tc + " - " + info(ctx));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NioCounter.COUNTER_ACTIVE_CHANNEL.decrementAndGet();
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
        NioCounter.COUNTER_HIT.incrementAndGet();
        final long hitIndex = NioCounter.COUNTER_BIZ_HIT.incrementAndGet();
//        if (HttpUtil.is100ContinueExpected(req)) {
//            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER));
//        }
        long dataSize = req.content().capacity();
//        if (dataSize > _5MB) {
//            ServiceError e = new ServiceError(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, null, "Upload file cannot over 5MB", null);
//            ServiceContext context = ServiceContext.build(hitIndex).txt(e.toJson()).status(HttpResponseStatus.INSUFFICIENT_STORAGE).errorCode(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT).level(Level.DEBUG);
//            NioHttpUtil.sendText(ctx, true, null, context.status(), context.txt(), context.contentType(), true);
//            return;
//        }
        final HttpMethod httpMethod = req.method();
        final String httpRequestUri = req.uri();

        final boolean isKeepAlive = HttpUtil.isKeepAlive(req);
        final String requestMetaInfo = requestMetaInfo(ctx, hitIndex, httpMethod, httpRequestUri, isKeepAlive, dataSize);
        log.debug(() -> requestMetaInfo);

        final HttpHeaders requestHeaders = req.headers();
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
            ServiceContext context = ServiceContext.build(ctx, hitIndex, start, requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
            String acceptCharset = requestHeaders.get(HttpHeaderNames.ACCEPT_CHARSET);
            if (StringUtils.isNotBlank(acceptCharset)) {
                context.charsetName(acceptCharset);//.contentType(ServiceContext.CONTENT_TYPE_JSON_ + acceptCharset); do not build content type with charset now, don't know charset valid or not
            }
            long responseContentLength = -1;
            Throwable ioEx = null;
            long processTime = -1;
            try {
                service(ctx, requestHeaders, httpMethod, queryStringDecoder.path(), queryStringDecoder.parameters(), httpPostRequestBody, context);
                processTime = System.currentTimeMillis() - start;
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this);
                context.timestampPOI(BootPOI.SERVICE_END);
            } catch (Throwable ex) {
                ioEx = ex;
                Err e = new Err(BootErrorCode.NIO_UNEXPECTED_SERVICE_FAILURE, null, "Failed to send context to client", ex);
                context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this);
            } finally {
                long responseTime = System.currentTimeMillis() - start;
                NioCounter.COUNTER_SENT.incrementAndGet();
                Level level = context.level();
                String report = null;
                if (log.isEnabled(level)) {
                    Caller caller = context.caller();
                    ServiceError error = context.error();
                    boolean overtime = responseTime > nioCfg.getBizTimeoutWarnThreshold();
                    if (overtime && level.isLessSpecificThan(Level.WARN)) {
                        level = Level.WARN;
                    }

                    //responsed#1=200 OK, error=0, r2q=7ms, r2r=60ms, caller=aaa#bbb, received#1=GET /a
                    StringBuilder sb = new StringBuilder();
                    //line1
                    sb.append("request_").append(hitIndex).append(".caller=").append(caller == null ? context.callerId() : caller);
                    //line2,3
                    sb.append("\n\t").append(requestMetaInfo).append("\n\tresponsed_").append(hitIndex).append("=").append(context.status())
                            .append(", error=").append(error == null ? 0 : error.getErrors().size())
                            .append(", queuing=").append(queuingTime).append("ms, process=").append(processTime);
                    if (overtime) {
                        sb.append("ms, response.ot=");
                    } else {
                        sb.append("ms, response=");
                    }
                    sb.append(responseTime).append("ms, cont.len=").append(responseContentLength).append("bytes");
                    //line4
                    context.reportPOI(nioCfg, sb);
                    verboseClientServerCommunication(nioCfg, requestHeaders, httpPostRequestBody, context, sb);
                    context.reportMemo(sb);
                    sb.append(System.lineSeparator());
                    report = beforeLogging(sb.toString());
                    log.log(level, report, context.cause());
                }
                try {
                    afterLogging(requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, report, ioEx);
                } catch (Throwable ex) {
                    log.error("report failed", ex);
                }
                //context.clear();
            }
        };
        try {
            nioCfg.getBizExecutor().execute(asyncTask);
        } catch (RejectedExecutionException ex) {
            long queuingTime = System.currentTimeMillis() - start;
            ServiceContext context = ServiceContext.build(ctx, hitIndex, start, requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
            Err e = new Err(BootErrorCode.NIO_TOO_MANY_REQUESTS, null, "Too many requests", ex);
            context.error(e).status(HttpResponseStatus.TOO_MANY_REQUESTS).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this);

            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(hitIndex).append("=").append(ex.toString())
                    .append("ms\n\t").append(requestMetaInfo).append("\n\tresponsed#").append(hitIndex)
                    .append("=").append(context.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(requestHeaders)
                    .append("\n\t4resp.body=").append(context.txt());
            log.fatal(sb.toString());
        } catch (Throwable ex) {
            long queuingTime = System.currentTimeMillis() - start;
            ServiceContext context = ServiceContext.build(ctx, hitIndex, start, requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
            Err e = new Err(BootErrorCode.NIO_UNEXPECTED_EXECUTOR_FAILURE, null, "NIO unexpected executor failure", ex);
            context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this);
            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(hitIndex).append("=").append(ex.toString())
                    .append("ms\n\t").append(requestMetaInfo).append("\n\tresponsed#").append(hitIndex)
                    .append("=").append(context.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(requestHeaders)
                    .append("\n\t4resp.body=").append(context.txt());
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

    private String requestMetaInfo(ChannelHandlerContext ctx, long hitIndex, HttpMethod httpMethod, String httpRequestUri, boolean isKeepAlive, long dataSize) {
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

    private void verboseClientServerCommunication(NioConfig cfg, HttpHeaders httpHeaders, String httpPostRequestBody, ServiceContext context, StringBuilder sb) {
        boolean isInFilter = false;
        // 3a. caller filter
        Caller caller = context.caller();
        switch (cfg.getFilterUserType()) {
            case ignore:
                isInFilter = true;
                break;
            case uid:
                if (StringUtils.isNotBlank(context.callerId())) {
                    isInFilter = cfg.getFilterCallerNameSet().contains(context.callerId());
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
                long target = context.status().code();
                if (s == null) {
                    isInFilter = target >= cfg.getFilterCodeRangeFrom() && target <= cfg.getFilterCodeRangeTo();
                } else {
                    isInFilter = s.contains(target);
                }
                break;
            case AppErrorCode:
                ServiceError e = context.error();
                if (e == null) {
                    break;
                }
                for (Err j : e.getErrors()) {
                    String errorCode = j.getErrorCode();
                    try {
                        target = Integer.parseInt(errorCode);
                        if (s == null) {
                            isInFilter = target >= cfg.getFilterCodeRangeFrom() && target <= cfg.getFilterCodeRangeTo();
                        } else {
                            isInFilter = s.contains(target);
                        }
                    } catch (Throwable ex) {
                    }
                    if (isInFilter) {
                        break;
                    }
                }
//                target = context.errorCode();
//                if (s == null) {
//                    isInFilter = target >= instance.getFilterCodeRangeFrom() && target <= instance.getFilterCodeRangeTo();
//                } else {
//                    isInFilter = s.contains(target);
//                }
                break;
        }
        if (!isInFilter) {
            return;
        }

        // 3c. verbose aspect
        // 3.1 request responseHeader
        if (!context.privacyReqHeader() && cfg.isVerboseReqHeader()) {
            sb.append("\n\t1.client_req.headers=").append(httpHeaders);
        }
        // 3.2 request body
        if (!context.privacyReqContent() && cfg.isVerboseReqContent()) {
            sb.append("\n\t2.client_req.body=").append(httpPostRequestBody);
        }
        // 3.3 context responseHeader
        if (!context.privacyRespHeader() && cfg.isVerboseRespHeader()) {
            sb.append("\n\t3.server_resp.headers=").append(context.responseHeaders());
        }
        // 3.4 context body
        if (!context.privacyRespContent() && cfg.isVerboseRespContent()) {
            sb.append("\n\t4.server_resp.body=").append(context.txt());
        }
    }

    abstract protected void service(final ChannelHandlerContext ctx, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context);

    abstract protected String beforeLogging(String originallLogContent);

    abstract protected void afterLogging(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
            final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, String logContent, Throwable ioEx) throws Exception;

    protected RequestProcessor getRequestProcessor(final HttpMethod httptMethod, final String httpRequestPath) {
        return JaxRsRequestProcessorManager.getRequestProcessor(httptMethod, httpRequestPath);
    }
}
