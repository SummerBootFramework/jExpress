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
package org.summerboot.jexpress.nio.grpc;

import io.grpc.Context;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.nio.server.NioServerHttpRequestHandler;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.TimeUtil;

import java.net.SocketAddress;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContextualizedServerCallListenerEx<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {


    protected final static Logger log = LogManager.getLogger(ContextualizedServerCallListenerEx.class);

    protected static ZoneId zoneId = ZoneId.systemDefault();

    protected static String protectedContectReplaceWith = "***";

    public static boolean isPing(String methodName) {
        return methodName.endsWith("/ping");
    }

    public static <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(long startTs, Caller caller, String jti, Context context, ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String[] requiredHealthChecks = null;
        HealthMonitor.EmptyHealthCheckPolicy emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_ALL;
        Set<String> failedHealthChecks = new HashSet<>();
        boolean isHealtchCheckFailed = HealthMonitor.isRequiredHealthChecksFailed(requiredHealthChecks, emptyHealthCheckPolicy, failedHealthChecks);
        if (isHealtchCheckFailed) {
            final String internalError = failedHealthChecks.toString();
            call.close(Status.FAILED_PRECONDITION.withDescription("Service health check failed by HealthMonitor: " + internalError), new Metadata());
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(new ServerCall.Listener<ReqT>() {
            }) {
            };
        }
        if (HealthMonitor.isServicePaused()) {
            call.close(Status.UNAVAILABLE.withDescription("Service is temporarily paused by HealthMonitor: " + HealthMonitor.getStatusReasonPaused()), new Metadata());
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(new ServerCall.Listener<ReqT>() {
            }) {
            };
        }

        Context previous;
        ContextualizedServerCallListenerEx<ReqT> listener;
        final SessionContext sessionContext;
        var serverCall = call;
        boolean isPing = false;

        try {
            String methodName = call.getMethodDescriptor().getFullMethodName();
            isPing = isPing(methodName);
            if (isPing) {
                GRPCServer.getServiceCounter().incrementPing();
                sessionContext = null;
            } else {
                GRPCServer.getServiceCounter().incrementHit();
                final long hitIndex = GRPCServer.getServiceCounter().incrementBiz();
                final String txId = BootConstant.APP_ID + "-" + hitIndex;
                GRPCServer.IDLE_EVENT_MONITOR.onCall(txId);
                HttpHeaders httpHeaders = new DefaultHttpHeaders();
                for (String key : headers.keys()) {
                    httpHeaders.add(key, headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                }

                String methodType = call.getMethodDescriptor().getType().name();
                SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                SocketAddress localAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
                sessionContext = new SessionContext(localAddr, remoteAddr, txId, hitIndex, startTs, httpHeaders, "gRPC HTTP/2", HttpMethod.POST, methodName, null);
                sessionContext.caller(caller).callerId(jti).sessionAttribute("MethodType", methodType);
                context = context.withValue(GRPCServer.SessionContext, sessionContext);
                serverCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        String headerKey_reference;
                        String headerKey_serverTimestamp;
                        ProcessorSettings processorSettings = sessionContext.processorSettings();
                        if (processorSettings == null) {
                            headerKey_reference = BootConstant.RESPONSE_HEADER_KEY_REF;
                            headerKey_serverTimestamp = BootConstant.RESPONSE_HEADER_KEY_TS;
                        } else {
                            headerKey_reference = processorSettings.getHttpServiceResponseHeaderName_Reference();
                            headerKey_serverTimestamp = processorSettings.getHttpServiceResponseHeaderName_ServerTimestamp();
                        }

                        responseHeaders.put(Metadata.Key.of(headerKey_reference, Metadata.ASCII_STRING_MARSHALLER), txId);
                        responseHeaders.put(Metadata.Key.of(headerKey_serverTimestamp, Metadata.ASCII_STRING_MARSHALLER), OffsetDateTime.now().format(TimeUtil.ISO_ZONED_DATE_TIME3));

                        HttpHeaders httpHeaders = new DefaultHttpHeaders();
                        for (String key : responseHeaders.keys()) {
                            httpHeaders.add(key, responseHeaders.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                        }
                        sessionContext.responseHeaders(httpHeaders);
                        super.sendHeaders(responseHeaders);
                    }

                    @Override
                    public void sendMessage(RespT message) {
                        if (message != null) {
                            sessionContext.response(message.toString());
                        }
                        super.sendMessage(message);
                    }
                };
            }
        } finally {
            previous = context.attach();
        }

        try {
            listener = new ContextualizedServerCallListenerEx<>(next.startCall(serverCall, headers), context, sessionContext, !isPing);
            if (!isPing) {
                log.trace("interceptCall: {}", listener);
            }
        } finally {
            context.detach(previous);
        }

        return listener;
    }


    private final Context context;

    private final SessionContext sessionContext;

    private List<String> httpPostRequestBodyList; // null until first message

    private boolean isBusinessRequest;

    private final Long hit;

    /**
     * onReady()
     * ↓
     * onMessage()   ← called once per message
     * onMessage()   ← repeated for client-streaming / bidi-streaming
     * onMessage()   ← ...
     * ↓
     * onHalfClose() ← always after ALL onMessage() calls are done. Business logic is being executed within this callback and before the server sends response back to client. For unary / server-streaming, onHalfClose() is called immediately after the single onMessage() call. For client-streaming / bidi-streaming, onHalfClose() is called after the last onMessage() call.
     * ↓
     * Server calls call.close(Status.OK, ...) → status/trailers sent to client
     * ↓
     * onComplete() or onCancel()
     *
     * @param delegate
     * @param context
     * @param sessionContext
     * @param isBusinessRequest
     */
    public ContextualizedServerCallListenerEx(ServerCall.Listener<ReqT> delegate, Context context, SessionContext sessionContext, boolean isBusinessRequest) {
        super(delegate);
        this.context = context;
        this.sessionContext = sessionContext;
        this.isBusinessRequest = isBusinessRequest;
        this.hit = sessionContext == null ? null : sessionContext.hit();
    }


    @Override
    public void onReady() {
        applyLogContext("onReady", true);
        Context previous = this.context.attach();

        try {
            super.onReady();
        } finally {
            this.context.detach(previous);
            applyLogContext("onReady", false);
        }
    }

    @Override
    public void onMessage(ReqT message) {
        applyLogContext("onMessage", true);
        Context previous = this.context.attach();
        if (log.isInfoEnabled() && message != null) {
            if (httpPostRequestBodyList == null) {
                httpPostRequestBodyList = new ArrayList<>();
            }
            httpPostRequestBodyList.add(message.toString());
        }

        try {
            super.onMessage(message);
        } finally {
            this.context.detach(previous);
            applyLogContext("onMessage", false);
        }
    }

    @Override
    public void onHalfClose() {
        applyLogContext("onHalfClose", true);
        Context previous = this.context.attach();

        try {
            super.onHalfClose();
        } finally {
            this.context.detach(previous);
            applyLogContext("onHalfClose", false);
        }
    }

    @Override
    public void onCancel() {
        applyLogContext("onCancel", true);
        if (isBusinessRequest) {
            GRPCServer.getServiceCounter().incrementCancelled();
            GRPCServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.context.attach();

        try {
            super.onCancel();
        } finally {
            this.context.detach(previous);
            applyLogContext("onCancel", false, true);// log after sending the response
        }
    }

    @Override
    public void onComplete() {
        applyLogContext("onComplete", true);
        if (isBusinessRequest) {
            GRPCServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.context.attach();

        try {
            super.onComplete();
        } finally {
            this.context.detach(previous);
            applyLogContext("onComplete", false, true);// log after sending the response
        }
    }

    private void applyLogContext(String actionName, boolean isBegin) {
        this.applyLogContext(actionName, isBegin, false);
    }

    private void applyLogContext(String actionName, boolean isBegin, boolean doReport) {
        if (!isBusinessRequest) {
            return;
        }
        if (isBegin) {
            if (hit != null) {
                ThreadContext.put(BootConstant.SYS_PROP_HITINDEX, "-" + hit);// REF269-2
            }
            if (log.isTraceEnabled()) {
                log.trace("{} begin: {}", actionName, this);// after ThreadContext.put
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("{} end: {}", actionName, this);// before ThreadContext.remove
            }
            if (doReport) {
                report(actionName);
            }
            if (hit != null) {
                ThreadContext.remove(BootConstant.SYS_PROP_HITINDEX);// REF269-2
            }
        }
    }

    protected void report(String actionName) {
        if (!isBusinessRequest || sessionContext == null) {
            return;
        }
        Level level = sessionContext.level();
        if (!log.isEnabled(level)) {
            return;
        }
        long responseTime = System.currentTimeMillis() - sessionContext.startTimestamp();
        boolean isTraceAll = BootConstant.isDebugMode();
        HttpHeaders requestHeaders = sessionContext.requestHeaders();
        if (!isTraceAll && requestHeaders.contains(HttpHeaderNames.AUTHORIZATION)) {
            requestHeaders.set(HttpHeaderNames.AUTHORIZATION, "***");// protect authenticator token from being logged
        }
        Caller caller = sessionContext.caller();
        ServiceError error = sessionContext.error();
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
        String txId = sessionContext.txId();
        String methodType = sessionContext.sessionAttribute("MethodType");
        sb.append(actionName).append(" report: request_").append(txId).append(".caller=").append(caller == null ? sessionContext.callerId() : caller);
        //line2,3
        sb.append("\n\t")
                .append("gRPC")
                .append("_request_").append(sessionContext.hit())
                .append("=").append(methodType).append(" ").append(sessionContext.uriRawDecoded())
                //.append(", dataSize=").append(dataSize)
                .append(", remoteAddr=").append(sessionContext.remoteIP()).append(", localAddr=").append(sessionContext.localIP()).append("\n\tresponse_").append(txId).append("=").append(sessionContext.status())
                .append(", error=").append(errorCount)
                .append(", FullHttpRequest.t0=").append(TimeUtil.toOffsetDateTime(sessionContext.startTimestamp(), zoneId))
                .append(", response=").append(responseTime).append("ms");
        //line4
        sessionContext.reportPOI(null, sb);
        String sanitizedUserInput = null;
        if (httpPostRequestBodyList != null) {
            int size = httpPostRequestBodyList.size();
            if (size == 1) {
                sanitizedUserInput = SecurityUtil.sanitizeCRLF(httpPostRequestBodyList.get(0));// CWE-117 False Positive prove
            } else if (size > 1) {
                StringBuilder sb2 = new StringBuilder();
                for (String httpPostRequestBody : httpPostRequestBodyList) {
                    String sanitizeed = SecurityUtil.sanitizeCRLF(httpPostRequestBody);// CWE-117 False Positive prove
                    sb2.append(sanitizeed).append(BootConstant.BR);
                }
                sanitizedUserInput = sb2.toString();
            }
        }

        long requestDataBytes = 0;
        long responseDataBytes = 0;
        NioServerHttpRequestHandler.verboseClientServerCommunication(null, requestHeaders, requestDataBytes, sanitizedUserInput, responseDataBytes, sessionContext, sb, isTraceAll);
        sessionContext.reportMemo(sb, log.getLevel());
        sessionContext.reportError(sb);
        sb.append(BootConstant.BR);
        String report = sb.toString();
        ProcessorSettings processorSettings = sessionContext.processorSettings();
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
        // should only sanitize user input: report = SecurityUtil.sanitizeCRLF(report);
        log.log(level, report);// CWE-117 False Positive
    }
}