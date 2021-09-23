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

import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
@ChannelHandler.Sharable
@Singleton
public class BootHttpPingHandler extends SimpleChannelInboundHandler<HttpObject> {

    protected static Logger log = LogManager.getLogger(BootHttpPingHandler.class.getName());

    private final String pingURL;

    public BootHttpPingHandler(/*String pingURL*/) {
        super(FullHttpRequest.class, false);
        if (StringUtils.isNotBlank(NioServerContext.getWebApiContextRoot())
                && StringUtils.isNotBlank(NioServerContext.getLoadBalancerHealthCheckPath())) {
            pingURL = NioServerContext.getWebApiContextRoot() + NioServerContext.getLoadBalancerHealthCheckPath();
        } else {
            pingURL = null;
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject httpObject) throws Exception {
        boolean isPingRequest = false;
        if (pingURL != null && (httpObject instanceof HttpRequest)) {
            HttpRequest req = (HttpRequest) httpObject;
            if (HttpMethod.GET.equals(req.method()) && pingURL.equals(req.uri())) {
                isPingRequest = true;
                NioServerContext.COUNTER_PING_HIT.incrementAndGet();
                try {
                    NioHttpUtil.sendText(ctx, HttpUtil.isKeepAlive((HttpRequest) req), null, NioServer.getServiceStatus(), null, null, null, true);
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
