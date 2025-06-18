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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.GeoIpUtil;
import org.summerboot.jexpress.util.TimeUtil;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@ChannelHandler.Sharable
public abstract class NioServerHttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> implements ErrorAuditor {

    protected Logger log = LogManager.getLogger(this.getClass());

    protected ZoneId zoneId = ZoneId.systemDefault();

    protected static NioConfig nioCfg = NioConfig.cfg;
    protected static String protectedContectReplaceWith = "***";

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
        NioCounter.COUNTER_HIT.incrementAndGet();
        final long hitIndex = NioCounter.COUNTER_BIZ_HIT.incrementAndGet();
        final String txId = BootConstant.APP_ID + "-" + hitIndex;
        boolean isDecoderSuccess = req.decoderResult().isSuccess();

//        if (HttpUtil.is100ContinueExpected(req)) {
//            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER));
//        }
        final String protocol = req.protocolVersion().toString();
        final long dataSize = req.content().capacity();
        final HttpMethod httpMethod = req.method();
        final String httpRequestUriRaw = req.uri();
        final String httpRequestUriRawDecoded = URLDecoder.decode(httpRequestUriRaw, StandardCharsets.UTF_8);
        final boolean isKeepAlive = HttpUtil.isKeepAlive(req);
        final HttpHeaders requestHeaders = req.headers();
        final String httpPostRequestBody;// = NioHttpUtil.getHttpPostBodyString(req);
        if (HttpMethod.GET.equals(httpMethod) || HttpMethod.HEAD.equals(httpMethod)) {
            httpPostRequestBody = null;
        } else {
            httpPostRequestBody = NioHttpUtil.getHttpPostBodyString(req);
        }
        ReferenceCountUtil.release(req);

//        if (dataSize > _5MB) {
//            ServiceError e = new ServiceError(BootErrorCode.NIO_FILE_UPLOAD_EXCEED_SIZE_LIMIT, null, "Upload file cannot over 5MB", null);
//            SessionContext context = SessionContext.build(hitIndex).txt(e.toJson()).status(HttpResponseStatus.INSUFFICIENT_STORAGE).errorCode(BootErrorCode.NIO_FILE_UPLOAD_EXCEED_SIZE_LIMIT).level(Level.DEBUG);
//            NioHttpUtil.sendText(ctx, true, null, context.status(), context.txt(), context.contentType(), true);
//            return;
//        }
        final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequestUriRaw, StandardCharsets.UTF_8, true);
        final String httpRequestUri = queryStringDecoder.path();
        final String requestMetaInfo = requestMetaInfo(ctx, txId, protocol, httpMethod, httpRequestUriRaw, httpRequestUriRawDecoded, isKeepAlive, dataSize);
        log.debug(() -> requestMetaInfo);


        final SessionContext context = SessionContext.build(ctx, txId, hitIndex, start, requestHeaders, httpMethod, httpRequestUriRawDecoded, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
        //ScopedValue.where(SessionContext.SESSION_CONTEXT, context).run(() -> {
        Runnable asyncTask = () -> {
            long queuingTime = System.currentTimeMillis() - start;
            String acceptCharset = requestHeaders.get(HttpHeaderNames.ACCEPT_CHARSET);
            if (StringUtils.isNotBlank(acceptCharset)) {
                context.charsetName(acceptCharset);//.contentType(SessionContext.CONTENT_TYPE_JSON_ + acceptCharset); do not build content type with charset now, don't know charset valid or not
            }
            long responseContentLength = -1;
            Throwable ioEx = null;
            long processTime = -1;
            ProcessorSettings processorSettings = null;
            try {
                if (isDecoderSuccess) {
                    String error = GeoIpUtil.callerAddressFilter(context.remoteIP(), nioCfg.getCallerAddressFilterWhitelist(), nioCfg.getCallerAddressFilterBlacklist(), nioCfg.getCallerAddressFilterRegexPrefix(), nioCfg.getCallerAddressFilterOption());
                    if (error == null) {
                        processorSettings = service(ctx, requestHeaders, httpMethod, httpRequestUri, queryStringDecoder.parameters(), httpPostRequestBody, context);
                    } else {
                        Err err = new Err<>(BootErrorCode.AUTH_INVALID_IP, null, null, null, "Invalid IP address: " + error);
                        context.error(err).status(HttpResponseStatus.NOT_ACCEPTABLE);
                    }
                } else {
                    Throwable cause = req.decoderResult().cause();
                    Err err = new Err<>(BootErrorCode.NIO_REQUEST_BAD_ENCODING, null, cause == null ? "" : cause.getMessage(), null, cause.toString());
                    context.error(err).status(HttpResponseStatus.BAD_REQUEST);
                }
                processTime = System.currentTimeMillis() - start;
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this, processorSettings);
                context.poi(BootPOI.SERVICE_END);
            } catch (Throwable ex) {
                ioEx = ex;
                Err e = new Err<>(BootErrorCode.NIO_UNEXPECTED_SERVICE_FAILURE, null, null, ex, "Failed to send context to client");
                context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
                responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this, processorSettings);
            } finally {
                NioCounter.COUNTER_SENT.incrementAndGet();
                long responseTime = System.currentTimeMillis() - start;
                this.afterService(requestHeaders, httpMethod, httpRequestUri, queryStringDecoder.parameters(), httpPostRequestBody, context);
                String report = null;
                try {
                    boolean overtime = responseTime > nioCfg.getBizTimeoutWarnThresholdMs();
                    HttpResponseStatus status = context.status();
                    Level level = context.level();
                    if ((overtime || status.code() >= 400) && level.isLessSpecificThan(Level.WARN)) {
                        level = Level.WARN;
                    }
                    if (log.isEnabled(level)) {
                        boolean isTraceAll = BootConstant.isDebugMode();
                        if (!isTraceAll && requestHeaders.contains(HttpHeaderNames.AUTHORIZATION)) {
                            requestHeaders.set(HttpHeaderNames.AUTHORIZATION, "***");// protect authenticator token from being logged
                        }
                        Caller caller = context.caller();
                        ServiceError error = context.error();
                        int errorCount = 0;
                        if (error != null) {
                            if (error.getErrors() == null) {
                                errorCount = 1;
                            } else {
                                errorCount = Math.max(1, error.getErrors().size());
                            }
                        }

                        //response#1=200 OK, error=0, r2q=7ms, r2r=60ms, caller=aaa#bbb, received#1=GET /a
                        StringBuilder sb = new StringBuilder();
                        //line1
                        sb.append("request_").append(txId).append(".caller=").append(caller == null ? context.callerId() : caller);
                        //line2,3
                        sb.append("\n\t").append(requestMetaInfo).append("\n\tresponse_").append(txId).append("=").append(status)
                                .append(", error=").append(errorCount)
                                .append(", FullHttpRequest.t0=").append(TimeUtil.toOffsetDateTime(start, zoneId))
                                .append(", queuing=").append(queuingTime).append("ms, process=").append(processTime);
                        if (overtime) {
                            sb.append("ms, response.ot=");
                        } else {
                            sb.append("ms, response=");
                        }
                        sb.append(responseTime).append("ms, cont.len=").append(responseContentLength).append("bytes");
                        //line4
                        context.reportPOI(nioCfg, sb);
                        String sanitizedUserInput = SecurityUtil.sanitizeCRLF(httpPostRequestBody);// CWE-117 False Positive prove
                        verboseClientServerCommunication(nioCfg, requestHeaders, sanitizedUserInput, context, sb, isTraceAll);
                        context.reportMemo(sb);
                        context.reportError(sb);
                        sb.append(BootConstant.BR);
                        report = sb.toString();
                        if (!isTraceAll && processorSettings != null) {
                            //isSendRequestParsingErrorToClient
                            ProcessorSettings.LogSettings logSettings = processorSettings.getLogSettings();
                            if (logSettings != null) {
                                List<String> protectedDataFields = logSettings.getProtectDataFieldsFromLogging();
                                if (protectedDataFields != null) {
                                    for (String protectedDataField : protectedDataFields) {
                                        report = FormatterUtil.replaceDataField(report, protectedDataField, protectedContectReplaceWith);
                                    }
                                }
                            }
                        }
                        report = beforeLogging(report, requestHeaders, httpMethod, httpRequestUriRawDecoded, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
                        // should only sanitize user input: report = SecurityUtil.sanitizeCRLF(report);
                        log.log(level, "\n{}", report);// CWE-117 False Positive
                    }
                } catch (Throwable ex) {
                    log.fatal("logging failed \n{}", report, ex);// CWE-117 False Positive
                }
                try {
                    afterLogging(report, requestHeaders, httpMethod, httpRequestUriRawDecoded, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
                } catch (Throwable ex) {
                    log.error("afterLogging failed", ex);
                }
                //context.clear();
            }
        };
        try {
            nioCfg.getBizExecutor().execute(asyncTask);
        } catch (RejectedExecutionException ex) {
            long queuingTime = System.currentTimeMillis() - start;
            //SessionContext context = SessionContext.build(ctx, txId, hitIndex, start, requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
            Err e = new Err<>(BootErrorCode.NIO_TOO_MANY_REQUESTS, null, null, ex, "Too many request, try again later");
            context.error(e).status(HttpResponseStatus.TOO_MANY_REQUESTS).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this, null);

            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(txId).append("=").append(ex.toString())
                    .append("ms\n\t").append(requestMetaInfo).append("\n\tresponse#").append(txId)
                    .append("=").append(context.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(requestHeaders)
                    .append("\n\t4resp.body=").append(context.txt());
            log.fatal(sb.toString());
        } catch (Throwable ex) {
            long queuingTime = System.currentTimeMillis() - start;
            //SessionContext context = SessionContext.build(ctx, txId, hitIndex, start, requestHeaders, httpMethod, httpRequestUri, httpPostRequestBody).responseHeaders(nioCfg.getServerDefaultResponseHeaders()).clientAcceptContentType(requestHeaders.get(HttpHeaderNames.ACCEPT));
            Err e = new Err<>(BootErrorCode.NIO_UNEXPECTED_EXECUTOR_FAILURE, null, null, ex, "NIO unexpected executor failure");
            context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR).level(Level.FATAL);
            long responseContentLength = NioHttpUtil.sendResponse(ctx, isKeepAlive, context, this, null);
            StringBuilder sb = new StringBuilder();
            sb.append("request_").append(txId).append("=").append(ex.toString())
                    .append("ms\n\t").append(requestMetaInfo).append("\n\tresponse#").append(txId)
                    .append("=").append(context.status())
                    .append(", errorCode=").append(e.getErrorCode())
                    .append(", queuing=").append(queuingTime)
                    .append("ms, cont.len=").append(responseContentLength)
                    .append("\n\t1req.headers=").append(requestHeaders)
                    .append("\n\t4resp.body=").append(context.txt());
            log.fatal(sb.toString());
        }
        //});
    }

    protected final String me = ", hdl=" + this.toString();

    protected String info(ChannelHandlerContext ctx) {
        return new StringBuilder()
                .append(", chn=").append(ctx.channel())
                .append(", ctx=").append(ctx.hashCode())
                .append(me)
                .toString();
    }

    protected String requestMetaInfo(ChannelHandlerContext ctx, String hitIndex, String protol, HttpMethod httpMethod, String httpRequestUriRaw, String httpRequestUriDecoded, boolean isKeepAlive, long dataSize) {
        StringBuilder sb = new StringBuilder().append(protol)
                .append("_request_").append(hitIndex)
                .append("=").append(httpMethod).append(" ").append(httpRequestUriDecoded)
                .append(", dataSize=").append(dataSize)
                .append(", KeepAlive=").append(isKeepAlive)
                .append(", chn=").append(ctx.channel())
                .append(", ctx=").append(ctx.hashCode())
                .append(me);
        if (!httpRequestUriRaw.equals(httpRequestUriDecoded)) {
            sb.append(BootConstant.BR).append("\trawURI=").append(httpRequestUriRaw).append(BootConstant.BR);
        }
        return sb.toString();
    }

    public static void verboseClientServerCommunication(NioConfig cfg, HttpHeaders httpHeaders, String httpPostRequestBody, SessionContext context, StringBuilder sb, boolean isTraceAll) {
        boolean isInFilter = false;
        // 3a. caller filter
        Caller caller = context.caller();
        NioConfig.VerboseTargetUserType verboseTargetUserType = cfg == null ? NioConfig.VerboseTargetUserType.ignore : cfg.getFilterUserType();
        switch (verboseTargetUserType) {
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
        NioConfig.VerboseTargetCodeType verboseTargetCodeType = cfg == null ? NioConfig.VerboseTargetCodeType.all : cfg.getFilterCodeType();
        Set<Long> s = cfg == null ? null : cfg.getFilterCodeSet();
        switch (verboseTargetCodeType) {
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
            case ApplicationErrorCode:
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
        boolean isVerbose = cfg == null ? true : cfg.isVerboseReqHeader();
        sb.append("\n\t1.client_req.headers=").append((isTraceAll || context.logRequestHeader() && isVerbose) ? httpHeaders : "***");
        // 3.2 request body
        isVerbose = cfg == null ? true : cfg.isVerboseReqContent();
        sb.append("\n\t2.client_req.body=").append((isTraceAll || context.logRequestBody() && isVerbose) ? httpPostRequestBody : "***");
        // 3.3 context responseHeader
        isVerbose = cfg == null ? true : cfg.isVerboseRespHeader();
        sb.append("\n\t3.server_resp.headers=").append((isTraceAll || context.logResponseHeader() && isVerbose) ? context.responseHeaders() : "***");
        // 3.4 context body
        isVerbose = cfg == null ? true : cfg.isVerboseRespContent();
        sb.append("\n\t4.server_resp.body=").append((isTraceAll || context.logResponseBody() && isVerbose) ? context.txt() : "***");
    }

    abstract protected ProcessorSettings service(final ChannelHandlerContext ctx, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context);

    abstract protected void afterService(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context);

    abstract protected String beforeLogging(final String originallLogContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                            final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) throws Exception;

    abstract protected void afterLogging(final String logContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                         final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) throws Exception;

    protected RequestProcessor getRequestProcessor(final HttpMethod httptMethod, final String httpRequestPath) {
        return JaxRsRequestProcessorManager.getRequestProcessor(httptMethod, httpRequestPath);
    }
}
