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
package org.summerboot.jexpress.controller.websocket;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.handler.codec.stomp.StompSubframeAggregator;
import io.netty.handler.codec.stomp.StompSubframeDecoder;
import io.netty.handler.codec.stomp.StompSubframeEncoder;
import io.netty.util.AttributeKey;
import org.summerboot.jexpress.controller.authenticate.Authenticator;
import org.summerboot.jexpress.controller.authenticate.Caller;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.webserver.netty.NioConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class WebSocketAuthHandler_OTT extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Caller> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final String BASENAME = "ws-ott";

    protected final Injector injector;
    protected final List<String> orderedNamedWebsocket;
    protected Authenticator authenticator;

    public WebSocketAuthHandler_OTT(Injector injector, Set<String> namedWebsocket) {
        this.injector = injector;
        this.authenticator = injector.getInstance(Authenticator.class);
        orderedNamedWebsocket = new ArrayList<>(namedWebsocket);
        orderedNamedWebsocket.sort(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uriRequestedWithOTT = request.uri(); // "/ws/chat/oneTimeTicket"
            String oneTimeTicket = null;
            String uriRequested = null;

            for (String name : orderedNamedWebsocket) { // uriPredefined = "/ws/chat"
                String uriFormated = name.endsWith("/") ? name : name + "/";
                if (uriRequestedWithOTT.startsWith(uriFormated)) {
                    oneTimeTicket = uriRequestedWithOTT.substring(uriFormated.length());
                    uriRequested = name;
                    break;
                }
            }
            if (oneTimeTicket == null) {
                //sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
                //ctx.close();
                ctx.fireChannelRead(msg);
                return;
            }


            Caller caller = verifyAndDestroyTicket(oneTimeTicket); // 校验并销毁 Ticket
            if (caller == null) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
                ctx.close();
                return;
            }
            // save OTT result to channel attr
            ctx.channel().attr(USER_ID_KEY).set(caller);

            // 重写 URI，让 downstream 的 WebSocketServerProtocolHandler 能够精准匹配升级
            request.setUri(uriRequested); // "/ws/chat"

            // load settings for WebSocketServerProtocolHandler
            NioConfig nioCfg = NioConfig.cfg;
            boolean allowExtensions = nioCfg.isWebSocketAllowExtensions();
            int maxFrameSize = nioCfg.getWebSocketMaxFrameSize();
            boolean allowMaskMismatch = nioCfg.isWebSocketAllowMaskMismatch();
            boolean checkStartsWith = nioCfg.isWebSocketCheckStartsWith();
            boolean dropPongFrames = nioCfg.isWebSocketDropPongFrames();
            long handshakeTimeoutMillis = nioCfg.getWebSocketHandshakeTimeoutMs();

            // [Key Point] Dynamically adding chat-specific services to the end of the pipeline (Handler)
            ChannelHandler ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(uriRequested))); // "/ws/chat"
            Class<?> type = ReflectionUtil.getInboundType(ch, 0);
            if (type == StompFrame.class) {
                final String webSocketStompSubprotocol = nioCfg.getWebSocketStompSubprotocol();
                // 1) Upgrade to websocket at /ws-stomp
                ctx.pipeline().addAfter(BASENAME, "ws-protocol", new WebSocketServerProtocolHandler(uriRequested, webSocketStompSubprotocol, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));

                // 2) Convert WS binary/text frames to ByteBuf for STOMP
                ctx.pipeline().addAfter("ws-protocol", "ws2buf", new WebSocketFrameToByteBufHandler());

                // 3) STOMP codec
                ctx.pipeline().addAfter("ws2buf", "stomp-decoder", new StompSubframeDecoder());
                ctx.pipeline().addAfter("stomp-decoder", "stomp-encoder", new StompSubframeEncoder());
                ctx.pipeline().addAfter("stomp-encoder", "stomp-agg", new StompSubframeAggregator(maxFrameSize)); // 5MB for small images/videos/files

                // 4) STOMP business logic
                ctx.pipeline().addAfter("stomp-agg", "stomp-biz", ch); // Extends SimpleChannelInboundHandler<FullStompFrame>
            } else if (type == WebSocketFrame.class) {
                // 1) Upgrade to websocket with STOMP subprotocol
                ctx.pipeline().addAfter(BASENAME, "ws-protocol", new WebSocketServerProtocolHandler(uriRequested, null, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));
                // 2) STOMP business logic
                ctx.pipeline().addAfter("ws-protocol", "ws-biz", ch); // Extends SimpleChannelInboundHandler<WebSocketFrame>
            } else {
                WebSocketPipelinesInitializer initializer = null;
                try {
                    initializer = injector.getInstance(WebSocketPipelinesInitializer.class);
                } catch (Throwable ex) {
                    // No WebSocketPipelinesInitializer defined, skip pipeline initialization
                }
                if (initializer == null) {
                    sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED));
                    ctx.close();
                    return;
                }
                initializer.initCustomizedPipelines(ctx, BASENAME, type, uriRequested, caller);
            }

            ctx.fireChannelRead(msg);
            return;
        }

        ctx.fireChannelRead(msg);
    }

    public void initCustomizedPipelines(ChannelHandlerContext ctx, String basename, Class<?> type, String uriRequested, Caller caller) {
    }

    protected Caller verifyAndDestroyTicket(String oneTimeTicket) {
        if (authenticator == null) {
            return null;
        }
        return authenticator.oneTimeTicketVerifyAndDestroy(oneTimeTicket);
    }

    protected void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        HttpUtil.setContentLength(res, 0);
        ctx.channel().writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}