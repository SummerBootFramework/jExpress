/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.summerboot.jexpress.infra.grpc.server;


import com.google.inject.Inject;
import io.grpc.Context;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.api.auth.Authenticator;
import org.summerboot.jexpress.api.auth.Caller;
import org.summerboot.jexpress.api.cache.AuthTokenCache;
import org.summerboot.jexpress.api.common.ProcessorSettings;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.api.grpc.GrpcConstants;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.infra.grpc.server.config.GrpcServerConfig;
import org.summerboot.jexpress.integration.HealthMonitor;
import org.summerboot.jexpress.security.auth.BootAuthenticator;
import org.summerboot.jexpress.util.net.GeoIpUtil;
import org.summerboot.jexpress.util.time.TimeUtil;

import java.net.SocketAddress;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GrpcServerInterceptor implements ServerInterceptor {

    @Inject
    protected Authenticator authenticator;

    @Inject
    protected AuthTokenCache authTokenCache;

    /**
     * caller remote address
     */
    Context.Key<SocketAddress> GrpcCallerAddr = Context.key("addr");

    /**
     * gRPC JWT verification result
     */
    Context.Key<String> GrpcCallerId = Context.key("uid");

    /**
     * gRPC JWT verification result
     */
    Context.Key<Caller> GrpcCaller = Context.key("caller");

    /**
     * gRPC JWT verification
     *
     * @param <ReqT>
     * @param <RespT>
     * @param serverCall
     * @param metadata
     * @param serverCallHandler
     * @return
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        Status status = null;
        Context ctx = Context.current();
        long start = System.currentTimeMillis();
        Caller caller = null;
        String jti = null;
        try {
            GrpcServerConfig grpcServerCfg = GrpcServerConfig.cfg;
            SocketAddress remoteAddr = serverCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            String error = GeoIpUtil.callerAddressFilter(remoteAddr, grpcServerCfg.getCallerAddressFilterWhitelist(), grpcServerCfg.getCallerAddressFilterBlacklist(), grpcServerCfg.getCallerAddressFilterOption());
            if (error != null) {
                //Err err = new Err(BootErrorCode.AUTH_INVALID_IP, null, null, null, "Invalid IP address: " + error);
                status = Status.PERMISSION_DENIED.withDescription(BootAuthenticator.ERROR + "Invalid IP address: " + error);
            } else {
                ctx = ctx.withValue(GrpcCallerAddr, remoteAddr);
                String headerValueAuthorization = metadata.get(GrpcConstants.Authorization);
                if (headerValueAuthorization == null) {
                    //status = Status.UNAUTHENTICATED.withDescription(ERROR + "Authorization header is missing");
                    //return Contexts.interceptCall(Context.current(), serverCall, metadata, serverCallHandler);
                    status = null;
                } else if (!headerValueAuthorization.startsWith(GrpcConstants.BEARER_TYPE)) {
                    status = Status.UNAUTHENTICATED.withDescription(BootAuthenticator.ERROR + "Unknown authorization type, non " + GrpcConstants.BEARER_TYPE + " token provided");
                } else {
                    String jwt = headerValueAuthorization.substring(GrpcConstants.BEARER_TYPE.length()).trim();
                    SessionContext context = SessionContext.build(0);
                    caller = authenticator.verifyToken(jwt, authTokenCache, null, context);
                    if (caller == null) {
                        String desc = context.error().getErrors().get(0).getErrorDesc();
                        if (StringUtils.isBlank(desc)) {
                            desc = context.status() + "";
                        }
                        status = Status.UNAUTHENTICATED.withDescription(BootAuthenticator.ERROR + desc);
                        //throw new StatusRuntimeException(status);
                    } else {
                        ctx = ctx.withValue(GrpcCaller, caller);
                        jti = context.callerId();
                        if (jti != null) {
                            ctx = ctx.withValue(GrpcCallerId, jti);
                        }
                        //return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                        status = null;
                    }
                }
            }
        } catch (Exception ex) {
            status = Status.UNAUTHENTICATED.withDescription(BootAuthenticator.ERROR + ex.getMessage()).withCause(ex);
        }

        if (status == null) {
            // success process
            //return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
            return interceptCallEx(start, caller, jti, ctx, serverCall, metadata, serverCallHandler);
        }
        // error process
        serverCall.close(status, metadata);
        return new ServerCall.Listener<ReqT>() {
        };
    }

    protected final static Logger log = LogManager.getLogger(GrpcServerInterceptor.class);

    protected <ReqT, RespT> ServerCall.Listener<ReqT> interceptCallEx(long startTs, Caller caller, String jti, Context ctx, ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        String[] requiredHealthChecks = GrpcServer.getRequiredHealthChecks(fullMethodName);
        if (requiredHealthChecks == null) {
            requiredHealthChecks = GrpcServerConfig.cfg.getDefaultRequiredHealthChecks();
        }
        HealthMonitor.EmptyHealthCheckPolicy emptyHealthCheckPolicy = GrpcServerConfig.cfg.getEmptyHealthCheckPolicy();
        Set<String> failedHealthChecks = new HashSet<>();
        boolean isHealthCheckFailed = HealthMonitor.isRequiredHealthChecksFailed(requiredHealthChecks, emptyHealthCheckPolicy, failedHealthChecks);
        if (isHealthCheckFailed) {
            final String internalError = failedHealthChecks.toString();
            call.close(Status.FAILED_PRECONDITION.withDescription("Service health check failed by HealthMonitor: " + internalError), new Metadata());
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener(new ServerCall.Listener<ReqT>() {
            }) {
            };
        }
        if (HealthMonitor.isServicePaused()) {
            call.close(Status.UNAVAILABLE.withDescription("Service is temporarily paused by HealthMonitor: " + HealthMonitor.getStatusReasonPaused()), new Metadata());
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener(new ServerCall.Listener<ReqT>() {
            }) {
            };
        }

        Context previous;
        boolean isPing = fullMethodName == null ? true : isPing(fullMethodName);
        GrpcServerCallListener<ReqT> listener;
        final SessionContext sessionContext;
        var serverCall = call;

        try {
            if (isPing) {
                GrpcServer.getServiceCounter().incrementPing();
                sessionContext = null;
            } else {
                GrpcServer.getServiceCounter().incrementHit();
                final long hitIndex = GrpcServer.getServiceCounter().incrementBiz();
                final String txId = BootConstants.APP_ID + "-g" + hitIndex; // REF280-1
                GrpcServer.IDLE_EVENT_MONITOR.resetIdleTimer(txId + " " + fullMethodName);
                HttpHeaders httpHeaders = new DefaultHttpHeaders();
                for (String key : headers.keys()) {
                    httpHeaders.add(key, headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)));
                }


                String methodType = call.getMethodDescriptor().getType().name();
                SocketAddress remoteAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
                SocketAddress localAddr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR);
                sessionContext = new SessionContext(localAddr, remoteAddr, txId, hitIndex, startTs, httpHeaders, "gRPC HTTP/2", HttpMethod.POST, fullMethodName, null);
                sessionContext.caller(caller).callerId(jti).sessionAttribute("MethodType", methodType);
                ctx = ctx.withValue(GrpcConstants.SessionContext, sessionContext);
                serverCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void sendHeaders(Metadata responseHeaders) {
                        String headerKey_reference;
                        String headerKey_serverTimestamp;
                        ProcessorSettings processorSettings = sessionContext.processorSettings();
                        if (processorSettings == null) {
                            headerKey_reference = BootConstants.RESPONSE_HEADER_KEY_REF;
                            headerKey_serverTimestamp = BootConstants.RESPONSE_HEADER_KEY_TS;
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
            previous = ctx.attach();
        }

        try {
            ServerCall.Listener<ReqT> delegate = next.startCall(serverCall, headers);
            listener = buildListener(delegate, ctx, fullMethodName, !isPing, headers, sessionContext, caller);
            if (!isPing) {
                log.trace("interceptCall: {}", listener);
            }
        } finally {
            ctx.detach(previous);
        }

        return listener;
    }

    public boolean isPing(String fullMethodName) {
        return fullMethodName.endsWith("/ping");
    }

    protected <ReqT> GrpcServerCallListener buildListener(ServerCall.Listener<ReqT> delegate, Context ctx, String fullMethodName, boolean isBusinessRequest, Metadata metadata, SessionContext sessionContext, Caller caller) {
        return new GrpcServerCallListener<>(delegate, ctx, fullMethodName, isBusinessRequest, metadata, sessionContext, caller);
    }
}
