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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContextualizedServerCallListenerEx<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {


    protected final Logger log = LogManager.getLogger(this);

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
        } finally {
            context.detach(previous);
        }

        return listener;
    }


    private final Context context;

    private final SessionContext sessionContext;

    private String httpPostRequestBody;

    private boolean isBusinessRequest;

    public ContextualizedServerCallListenerEx(ServerCall.Listener<ReqT> delegate, Context context, SessionContext sessionContext, boolean isBusinessRequest) {
        super(delegate);
        this.context = context;
        this.sessionContext = sessionContext;
        this.isBusinessRequest = isBusinessRequest;
    }

    public void onReady() {
        Context previous = this.context.attach();

        try {
            super.onReady();
        } finally {
            this.context.detach(previous);
        }
    }

    public void onMessage(ReqT message) {
        Context previous = this.context.attach();
        if (message != null) {
            httpPostRequestBody = message.toString();
        }

        try {
            super.onMessage(message);
        } finally {
            this.context.detach(previous);
        }

    }

    public void onHalfClose() {
        Context previous = this.context.attach();

        try {
            super.onHalfClose();
        } finally {
            this.context.detach(previous);
        }
    }

    public void onCancel() {
        if (isBusinessRequest) {
            GRPCServer.getServiceCounter().incrementCancelled();
            GRPCServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.context.attach();

        try {
            super.onCancel();
        } finally {
            this.context.detach(previous);
        }
        report();

    }

    public void onComplete() {
        if (isBusinessRequest) {
            GRPCServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.context.attach();

        try {
            super.onComplete();
        } finally {
            this.context.detach(previous);
        }
        report();
    }


    protected void report() {
        if (sessionContext == null) {
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
        sb.append("request_").append(txId).append(".caller=").append(caller == null ? sessionContext.callerId() : caller);
        //line2,3
        sb.append("\n\t")
                .append("gRPC")
                .append("_request_").append(sessionContext.hit())
                .append("=").append(methodType).append(" ").append(sessionContext.uri())
                //.append(", dataSize=").append(dataSize)
                .append(", remoteAddr=").append(sessionContext.remoteIP()).append(", localAddr=").append(sessionContext.localIP()).append("\n\tresponse_").append(txId).append("=").append(sessionContext.status())
                .append(", error=").append(errorCount)
                .append(", FullHttpRequest.t0=").append(TimeUtil.toOffsetDateTime(sessionContext.startTimestamp(), zoneId))
                .append(", response=").append(responseTime).append("ms");
        //line4
        sessionContext.reportPOI(null, sb);
        String sanitizedUserInput = SecurityUtil.sanitizeCRLF(httpPostRequestBody);// CWE-117 False Positive prove
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