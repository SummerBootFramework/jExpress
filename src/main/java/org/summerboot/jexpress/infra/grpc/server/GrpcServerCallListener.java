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

import io.grpc.Context;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.summerboot.jexpress.api.auth.Caller;
import org.summerboot.jexpress.api.common.ProcessorSettings;
import org.summerboot.jexpress.api.common.ServiceError;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.infra.netty.NioServerHttpRequestHandler;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.format.FormatterUtil;
import org.summerboot.jexpress.util.time.TimeUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GrpcServerCallListener<ReqT, RespT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    protected final static Logger log = LogManager.getLogger(GrpcServerCallListener.class);

    protected ZoneId zoneId = ZoneId.systemDefault();

    private final Context ctx;
    private final String fullMethodName;
    private boolean isBusinessRequest;
    private final Metadata metadata;

    private final SessionContext sessionContext;
    private final Caller caller;

    private List<String> httpPostRequestBodyList; // null until first message
    private long totalBodyLength = 0L; // O(1) real-time recording of total length


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
     * @param ctx
     * @param sessionContext
     * @param isBusinessRequest
     */
    public GrpcServerCallListener(ServerCall.Listener<ReqT> delegate, Context ctx, String fullMethodName, boolean isBusinessRequest, Metadata metadata, SessionContext sessionContext, Caller caller) {
        super(delegate);
        this.ctx = ctx;
        this.fullMethodName = fullMethodName;
        this.isBusinessRequest = isBusinessRequest;
        this.metadata = metadata;
        this.sessionContext = sessionContext;
        this.caller = caller;
        this.hit = sessionContext == null ? null : sessionContext.hit();
    }


    @Override
    public void onReady() {
        applyLogContext("onReady", true);
        Context previous = this.ctx.attach();

        try {
            super.onReady();
        } finally {
            this.ctx.detach(previous);
            applyLogContext("onReady", false);
        }
    }

    @Override
    public void onMessage(ReqT message) {
        applyLogContext("onMessage", true);
        Context previous = this.ctx.attach();
        if (isBusinessRequest && message != null && log.isInfoEnabled()) {
            if (httpPostRequestBodyList == null) {
                httpPostRequestBodyList = new ArrayList<>();
            }
            String bodyStr = message.toString();
            httpPostRequestBodyList.add(bodyStr);
            totalBodyLength += bodyStr.length(); // Required character count (for log truncation and quota estimation)
            // totalBodyLength += com.google.common.base.Utf8.encodedLength(bodyStr); // Requires the precise number of UTF-8 bytes (for scenarios involving a mix of Chinese and English)
        }

        try {
            super.onMessage(message);
        } finally {
            this.ctx.detach(previous);
            applyLogContext("onMessage", false);
        }
    }

    @Override
    public void onHalfClose() {
        applyLogContext("onHalfClose", true);
        Context previous = this.ctx.attach();

        try {
            super.onHalfClose();
        } finally {
            this.ctx.detach(previous);
            applyLogContext("onHalfClose", false);
        }
    }

    @Override
    public void onCancel() {
        applyLogContext("onCancel", true);
        if (isBusinessRequest) {
            GrpcServer.getServiceCounter().incrementCancelled();
            GrpcServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.ctx.attach();

        try {
            super.onCancel();
        } finally {
            this.ctx.detach(previous);
            applyLogContext("onCancel", false, true);// log after sending the response
        }
    }

    @Override
    public void onComplete() {
        applyLogContext("onComplete", true);
        if (isBusinessRequest) {
            GrpcServer.getServiceCounter().incrementProcessed();
        }
        Context previous = this.ctx.attach();

        try {
            super.onComplete();
        } finally {
            this.ctx.detach(previous);
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
                ThreadContext.put(BootConstants.SYS_PROP_HITINDEX, "-" + hit);// REF269-2
            }
            if (log.isTraceEnabled()) {
                log.trace("{} begin: {}", actionName, this);// after ThreadContext.put
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("{} end: {}", actionName, this);// before ThreadContext.remove
            }
            if (doReport) {
                Level level = sessionContext.level();
                String report = buildReport(actionName, level, sessionContext);
                // should only sanitize user input: report = SecurityUtil.sanitizeCRLF(report);
                log.log(level, report);// CWE-117 False Positive
            }
            if (hit != null) {
                ThreadContext.remove(BootConstants.SYS_PROP_HITINDEX);// REF269-2
            }
        }
    }

    protected String buildReport(String actionName, Level level, SessionContext sessionContext) {
        if (!isBusinessRequest || sessionContext == null) {
            return "";
        }
        if (!log.isEnabled(level)) {
            return "";
        }
        long responseTime = System.currentTimeMillis() - sessionContext.startTimestamp();
        boolean isTraceAll = BootConstants.isDebugMode();
        HttpHeaders requestHeaders = sessionContext.requestHeaders();
        if (!isTraceAll && requestHeaders.contains(HttpHeaderNames.AUTHORIZATION)) {
            requestHeaders.set(HttpHeaderNames.AUTHORIZATION, BootConstants.LOGGING_PROTECTED_CONTENT_REPLACEMENT);// protect authenticator token from being logged
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
                sanitizedUserInput = sanitizeUserInput(httpPostRequestBodyList.get(0));// CWE-117 False Positive prove
            } else if (size > 1) {
                StringBuilder sb2 = new StringBuilder();
                for (String httpPostRequestBody : httpPostRequestBodyList) {
                    String sanitizeed = sanitizeUserInput(httpPostRequestBody);// CWE-117 False Positive prove
                    sb2.append(sanitizeed).append(BootConstants.BR);
                }
                sanitizedUserInput = sb2.toString();
            }
        }

        long requestDataBytes = totalBodyLength;
        String responseBody = sessionContext.txt();
        long responseDataBytes = responseBody == null ? 0 : responseBody.length();
        buildClientServerCommunication(fullMethodName, requestHeaders, requestDataBytes, sanitizedUserInput, sessionContext.responseHeaders(), responseDataBytes, responseBody, sessionContext, sb, isTraceAll);
        sessionContext.reportMemo(sb, log.getLevel());
        sessionContext.reportError(sb);
        sb.append(BootConstants.BR);
        String report = sb.toString();
        ProcessorSettings processorSettings = sessionContext.processorSettings();
        if (!isTraceAll && processorSettings != null) {
            //isSendRequestParsingErrorToClient
            ProcessorSettings.LogSettings logSettings = processorSettings.getLogSettings();
            if (logSettings != null) {
                List<String> protectedDataFields = logSettings.getProtectDataFieldsFromLogging();
                if (protectedDataFields != null) {
                    for (String protectedDataField : protectedDataFields) {
                        report = FormatterUtil.replaceDataField(report, protectedDataField, BootConstants.LOGGING_PROTECTED_CONTENT_REPLACEMENT);
                    }
                }
            }
        }
        return report;
    }

    protected String sanitizeUserInput(String originalInput) {
        return SecurityUtil.sanitizeCRLF(originalInput);// CWE-117 False Positive prove
    }

    protected void buildClientServerCommunication(String fullMethodName, HttpHeaders requestHeaders, long requestDataBytes, String sanitizedUserInput, HttpHeaders responseHeaders, long responseDataBytes, String responseBody, SessionContext context, StringBuilder sb, boolean isTraceAll) {
        NioServerHttpRequestHandler.verboseClientServerCommunication(null, requestHeaders, requestDataBytes, sanitizedUserInput, responseHeaders, responseDataBytes, responseBody, sessionContext, sb, isTraceAll);
    }
}