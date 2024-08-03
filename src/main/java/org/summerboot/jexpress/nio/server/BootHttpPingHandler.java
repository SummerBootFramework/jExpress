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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.event.HttpLifecycleListener;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;

import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@ChannelHandler.Sharable
@Singleton
public class BootHttpPingHandler extends SimpleChannelInboundHandler<HttpObject> {

    protected static Logger log = LogManager.getLogger(BootHttpPingHandler.class.getName());

    protected final List<String> pingURLs;
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
                    HttpResponseStatus status;
                    final String internalReason = null;// Do NOT expose to external caller!
                    if (!HealthMonitor.isHealthCheckSuccess()) {
                        status = HttpResponseStatus.BAD_GATEWAY;
                        //internalReason = HealthMonitor.getStatusReasonHealthCheck();
                    } else if (HealthMonitor.isServicePaused()) {
                        status = HttpResponseStatus.SERVICE_UNAVAILABLE;
                        //internalReason = HealthMonitor.getStatusReasonPaused();
                    } else {
                        status = HttpResponseStatus.OK;
                    }
                    boolean isContinue = httpLifecycleListener.beforeProcessPingRequest(ctx, req.uri(), hit, status);
                    if (isContinue) {
                        NioHttpUtil.sendText(ctx, HttpUtil.isKeepAlive((HttpRequest) req), null, status, internalReason, null, null, true, null);
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
