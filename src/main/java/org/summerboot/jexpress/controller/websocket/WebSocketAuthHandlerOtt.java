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
import io.netty.channel.ChannelPipeline;
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
import io.netty.util.AttributeKey;
import org.summerboot.jexpress.security.authenticate.Authenticator;
import org.summerboot.jexpress.security.authenticate.Caller;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.webserver.netty.NioConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class WebSocketAuthHandlerOtt extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Caller> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final String BASENAME = "ws-ott";

    protected final Injector injector;
    protected final List<String> orderedNamedWebsocket;
    protected Authenticator authenticator;

    public WebSocketAuthHandlerOtt(Injector injector, Set<String> namedWebsocket) {
        this.injector = injector;
        this.authenticator = injector.getInstance(Authenticator.class);
        orderedNamedWebsocket = new ArrayList<>(namedWebsocket);
        orderedNamedWebsocket.sort(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uriRequestedWithOTT = request.uri(); // "/ws/chat/oneTimeToken"
            String oneTimeToken = null;
            String uriRequested = null;

            for (String name : orderedNamedWebsocket) { // uriPredefined = "/ws/chat"
                String uriFormated = name.endsWith("/") ? name : name + "/";
                if (uriRequestedWithOTT.startsWith(uriFormated)) {
                    oneTimeToken = uriRequestedWithOTT.substring(uriFormated.length());
                    uriRequested = name;
                    break;
                }
            }
            if (oneTimeToken == null) {
                //sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
                //ctx.close();
                ctx.fireChannelRead(msg);
                return;
            }


            Caller caller = verifyAndDestroyOneTimeToken(oneTimeToken); // verify and consume one-time token
            if (caller == null) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
                ctx.close();
                return;
            }
            // save OTT result to channel attr
            ctx.channel().attr(USER_ID_KEY).set(caller);

            // rewrite URI so downstream WebSocketServerProtocolHandler can match upgrade path exactly
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
            ChannelPipeline pipeline = ctx.pipeline();
            Class<?> type = ReflectionUtil.getInboundType(ch, 0);
            if (type == StompFrame.class) {
                final String webSocketStompSubprotocol = nioCfg.getWebSocketStompSubprotocol();
                // ==========================================
                // Layer 1: WebSocket Protocol and Conversion Layer
                // ==========================================
                // 1) Upgraded to the WebSocket protocol (responsible for: unpacking inbound WS frames and packaging outbound WS frames).
                pipeline.addAfter(BASENAME, "ws-protocol-handler", new WebSocketServerProtocolHandler(uriRequested, webSocketStompSubprotocol, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));
                // 2) [outbound] STOMP -> WebSocket text frame encoder
                pipeline.addAfter("ws-protocol-handler", "stomp-to-ws-encoder", new StompToWebSocketTextFrameEncoder());
                // 3) [inbound] WebSocket frame -> ByteBuf decoder
                pipeline.addAfter("stomp-to-ws-encoder", "ws-to-bytebuf-decoder", new WebSocketFrameToByteBufDecoder());

                // ==========================================
                // Layer 2: STOMP Protocol Parsing Layer
                // ==========================================
                // 4) [inbound] STOMP subframe decoder
                pipeline.addAfter("ws-to-bytebuf-decoder", "stomp-subframe-decoder", new StompSubframeDecoder());
                // 5) [inbound] STOMP subframe aggregator
                pipeline.addAfter("stomp-subframe-decoder", "stomp-subframe-aggregator", new StompSubframeAggregator(maxFrameSize));

                // ==========================================
                // Layer 3: Application Business Logic Layer
                // ==========================================
                // 6. STOMP business logic
                pipeline.addAfter("stomp-subframe-aggregator", "stomp-biz-handler", ch); // Extends SimpleChannelInboundHandler<FullStompFrame>
            } else if (type == WebSocketFrame.class) {
                // 1) Upgrade to websocket with STOMP subprotocol
                pipeline.addAfter(BASENAME, "ws-protocol", new WebSocketServerProtocolHandler(uriRequested, null, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));
                // 2) STOMP business logic
                pipeline.addAfter("ws-protocol", "ws-biz", ch); // Extends SimpleChannelInboundHandler<WebSocketFrame>
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

    protected Caller verifyAndDestroyOneTimeToken(String oneTimeToken) {
        if (authenticator == null) {
            return null;
        }
        return authenticator.oneTimeTokenVerifyAndDestroy(oneTimeToken);
    }

    protected void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        HttpUtil.setContentLength(res, 0);
        ctx.channel().writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}