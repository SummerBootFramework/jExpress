package org.summerboot.jexpress.nio.grpc;

import io.grpc.Context;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.nio.server.NioServerHttpRequestHandler;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.TimeUtil;

import java.net.SocketAddress;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class ContextualizedServerCallListenerEx<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {


    protected final Logger log = LogManager.getLogger(this);

    protected ZoneId zoneId = ZoneId.systemDefault();


    public static <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(long startTs, Caller caller, String jti, Context context, ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Context previous;
        ContextualizedServerCallListenerEx listener;
        final ServiceContext serviceContext;
        var serverCall = call;
        try {
            String methodName = call.getMethodDescriptor().getFullMethodName();
            if (isPing(methodName)) {
                serviceContext = null;
                GRPCServer.getServiceCounter().incrementPing();
            } else {
                SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                SocketAddress localAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
                final long hitIndex = GRPCServer.getServiceCounter().incrementBiz();
                final String txId = BootConstant.APP_ID + "-" + hitIndex;
                HttpHeaders httpHeaders = new DefaultHttpHeaders();
                for (String key : headers.keys()) {
                    httpHeaders.add(key, headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                }
                serviceContext = new ServiceContext(localAddr, remoteAddr, txId, hitIndex, startTs, httpHeaders, null, methodName, null);
                serviceContext.caller(caller).callerId(jti);
                if (caller != null) {
                    context = context.withValue(GRPCServer.ServiceContext, serviceContext);
                }
                serverCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        String headerKey_reference = BootConstant.RESPONSE_HEADER_KEY_REF;
                        responseHeaders.put(Metadata.Key.of(headerKey_reference, Metadata.ASCII_STRING_MARSHALLER), txId);
                        String headerKey_serverTimestamp = BootConstant.RESPONSE_HEADER_KEY_TS;
                        responseHeaders.put(Metadata.Key.of(headerKey_serverTimestamp, Metadata.ASCII_STRING_MARSHALLER), OffsetDateTime.now().format(TimeUtil.ISO_ZONED_DATE_TIME3));

                        HttpHeaders httpHeaders = new DefaultHttpHeaders();
                        for (String key : responseHeaders.keys()) {
                            httpHeaders.add(key, responseHeaders.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                        }
                        serviceContext.responseHeaders(httpHeaders);
                        super.sendHeaders(responseHeaders);
                    }

                    @Override
                    public void sendMessage(RespT message) {
                        if (message != null) {
                            serviceContext.txt(message.toString());
                        }
                        super.sendMessage(message);
                    }
                };
            }
        } finally {
            previous = context.attach();
        }

        try {
            listener = new ContextualizedServerCallListenerEx(next.startCall(serverCall, headers), context, serviceContext);
        } finally {
            context.detach(previous);
        }

        return listener;
    }

    public static boolean isPing(String methodName) {
        return methodName.endsWith("/ping");
    }

    private final Context context;

    private final ServiceContext serviceContext;

    private String httpPostRequestBody;

    public ContextualizedServerCallListenerEx(ServerCall.Listener<ReqT> delegate, Context context, ServiceContext serviceContext) {
        super(delegate);
        this.context = context;
        this.serviceContext = serviceContext;
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
        long responseTime = System.currentTimeMillis() - serviceContext.startTimestamp();
    }

    public void onCancel() {
        GRPCServer.getServiceCounter().incrementCancelled();
        GRPCServer.getServiceCounter().incrementProcessed();
        Context previous = this.context.attach();

        try {
            super.onCancel();
        } finally {
            this.context.detach(previous);
        }
        report();

    }

    public void onComplete() {
        GRPCServer.getServiceCounter().incrementProcessed();
        Context previous = this.context.attach();

        try {
            super.onComplete();
        } finally {
            this.context.detach(previous);
        }
        report();
    }

    protected void report() {
        Level level = serviceContext.level();
        if (!log.isEnabled(level)) {
            return;
        }
        long responseTime = System.currentTimeMillis() - serviceContext.startTimestamp();
        boolean isTraceAll = log.isTraceEnabled();
        HttpHeaders requestHeaders = serviceContext.requestHeaders();
        if (!isTraceAll && requestHeaders.contains(HttpHeaderNames.AUTHORIZATION)) {
            requestHeaders.set(HttpHeaderNames.AUTHORIZATION, "***");// protect authenticator token from being logged
        }
        Caller caller = serviceContext.caller();
        ServiceError error = serviceContext.error();
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
        String txId = serviceContext.txId();
        sb.append("request_").append(txId).append(".caller=").append(caller == null ? serviceContext.callerId() : caller);
        //line2,3
        sb.append("\n\t").append("remoteAddr=").append(serviceContext.remoteIP()).append(", localAddr=").append(serviceContext.localIP()).append("\n\tresponse_").append(txId).append("=")//.append(status)
                .append(", error=").append(errorCount)
                .append(", FullHttpRequest.t0=").append(TimeUtil.toOffsetDateTime(serviceContext.startTimestamp(), zoneId))
                .append(", response=").append(responseTime).append("ms");
        //line4
        serviceContext.reportPOI(null, sb);
        NioServerHttpRequestHandler.verboseClientServerCommunication(null, requestHeaders, httpPostRequestBody, serviceContext, sb, isTraceAll);
        serviceContext.reportMemo(sb);
        serviceContext.reportError(sb);
        sb.append(BootConstant.BR);
        String report = sb.toString();
        log.log(level, report);
    }
}