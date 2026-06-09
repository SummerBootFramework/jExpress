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
package org.summerboot.jexpress.infra.netty;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.api.common.ServiceError;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.lifecycle.http.HttpLifecycleListener;
import org.summerboot.jexpress.infra.netty.config.NioConfig;
import org.summerboot.jexpress.infra.netty.util.NioHttpUtil;
import org.summerboot.jexpress.integration.HealthMonitor;
import org.summerboot.jexpress.util.format.ServiceErrorSanitizer;
import org.summerboot.jexpress.util.time.TimeUtil;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@ChannelHandler.Sharable
@Singleton
public class BootHttpPingHandler extends SimpleChannelInboundHandler<HttpObject> {

    protected static Logger log = LogManager.getLogger(BootHttpPingHandler.class.getName());

    protected final Set<String> pingURLs;
    protected final boolean hasPingURL;

    @Inject
    protected HttpLifecycleListener httpLifecycleListener;

    public BootHttpPingHandler(/*String pingURL*/) {
        super(FullHttpRequest.class, false);
        pingURLs = BackOffice.agent.getLoadBalancingPingEndpoints();
        hasPingURL = pingURLs != null && !pingURLs.isEmpty();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject httpObject) throws Exception {
        boolean isPingRequest = false;
        if (hasPingURL && (httpObject instanceof HttpRequest)) {
            HttpRequest req = (HttpRequest) httpObject;
            if (HttpMethod.GET.equals(req.method()) && pingURLs.contains(req.uri())) {
                isPingRequest = true;
                long hit = NioCounter.COUNTER_PING_HIT.incrementAndGet();
                try {
                    HttpResponseStatus status = HttpResponseStatus.OK;
                    String internalReason = null;// Do NOT expose internal information to external caller!
                    ServiceError se = null;
                    if (NioConfig.cfg.isPingSyncPauseStatus() && HealthMonitor.isServicePaused()) {
                        status = HttpResponseStatus.SERVICE_UNAVAILABLE;
                        se = HealthMonitor.getStatusReasonPaused();
                    } else if (!HealthMonitor.isHealthCheckSuccess()) {
                        if (HealthMonitor.isRequiredHealthChecksFailed(NioConfig.cfg.getPingSyncHealthStatus_requiredHealthChecks(), NioConfig.cfg.getEmptyHealthCheckPolicy())) {
                            status = HttpResponseStatus.BAD_GATEWAY;
                        }
                        se = HealthMonitor.getStatusReasonHealthCheck();
                    }
                    if (se == null) {
                        internalReason = null;
                    } else if (NioConfig.cfg.isPingSyncShowRootCause()) {
                        internalReason = se.toJson();
                    } else {
                        int errorFieldBitmap = ServiceErrorSanitizer.ERR_FIELD_ERROR_CODE
                                | ServiceErrorSanitizer.ERR_FIELD_ERROR_TAG
                                | ServiceErrorSanitizer.ERR_FIELD_ADDITIONAL_FIELDS;
                        ServiceError sanitized = ServiceErrorSanitizer.deepClone(se, true, errorFieldBitmap);
                        internalReason = sanitized.toJson();
                    }
                    boolean isContinue = httpLifecycleListener.beforeProcessPingRequest(ctx, req.uri(), hit, status);
                    if (isContinue) {

                        DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
                        responseHeaders.set(BootConstants.RESPONSE_HEADER_KEY_REF, BootConstants.APP_ID);
                        responseHeaders.set(BootConstants.RESPONSE_HEADER_KEY_TS, OffsetDateTime.now().format(TimeUtil.ISO_ZONED_DATE_TIME3));
                        NioHttpUtil.sendText(ctx, HttpUtil.isKeepAlive((HttpRequest) req), responseHeaders, status, internalReason, null, null, true, null);
                        httpLifecycleListener.afterSendPingResponse(ctx, req.uri(), hit, status);
                    }
                } finally {
                    ReferenceCountUtil.release(req);
                }
            }
        }
        if (!isPingRequest) {
            //pass to next Handler
            ctx.fireChannelRead(httpObject);
        }
    }

}
