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
package org.summerboot.jexpress.nio.server.websocket;

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
import io.netty.util.AttributeKey;
import org.summerboot.jexpress.security.auth.Authenticator;
import org.summerboot.jexpress.security.auth.Caller;

import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class WebSocketAuthHandler_OTT extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Caller> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final String CHANNEL_NAME = "WebSocketAuthHandler_OTT";
    public static final String CHANNEL_CHANNEL_NAME_NEXT = "BootWebSocketServerProtocolHandler";
    public static final String WS_PATH = "/ws";
    public static final String WS_PATH_PREFIX = WS_PATH + "/";


    protected final Injector injector;
    protected final Set<String> namedWebsocket;
    protected Authenticator authenticator;

    public WebSocketAuthHandler_OTT(Injector injector, Set<String> namedWebsocket) {
        this.injector = injector;
        this.namedWebsocket = Set.copyOf(namedWebsocket);
        this.authenticator = injector.getInstance(Authenticator.class);
//        for (String s : namedWebsocket) {
//            if (s == null || !s.startsWith(WS_PATH_PREFIX + "/")) {
//                String errorMessage = "@Service(binding = ChannelHandler.class, named = \"" + s + "\", type = Service.ChannelHandlerType.Websocket): named field value must start with " + WS_PATH_PREFIX + "/, but found: " + s;
//                throw new IllegalArgumentException(errorMessage);
//            }
//            String t = s.endsWith("/") ? s : s + "/";
//            this.namedWebsocket.add(t);
//        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uriRequested = request.uri();

            // 路由分支一：如果是聊天模块的请求
            for (String uriPredefined : namedWebsocket) {
                // uriPredefined = "/ws/chat/"
                // uriRequested = "/ws/chat/oneTimeTicket"
                String uriPredefinedOTT = uriPredefined.endsWith("/") ? uriPredefined : uriPredefined + "/";
                if (uriRequested.startsWith(uriPredefinedOTT)) {
                    String oneTimeTicket = uriRequested.substring(uriPredefinedOTT.length());
                    Caller caller = verifyAndDestroyTicket(oneTimeTicket); // 校验并销毁 Ticket
                    if (caller == null) {
                        sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
                        break;
                    }
                    // save OTT result to channel attr
                    ctx.channel().attr(USER_ID_KEY).set(caller);

                    // 【核心点】动态向管道末尾添加聊天专属业务 Handler
                    //ctx.pipeline().addLast("businessHandler", new ChatModuleHandler());
                    ChannelHandler ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(uriPredefined))); // "/ws/chat"
                    ctx.pipeline().addAfter(CHANNEL_CHANNEL_NAME_NEXT, "ScannedWebSocketHandler", ch);

                    // 重写 URI，让下游的 WebSocketServerProtocolHandler 能够精准匹配升级
                    // derive baseUri without trailing slash for named lookup and rewrite
                    String baseUri = uriPredefined.endsWith("/") ? uriPredefined.substring(0, uriPredefined.length() - 1) : uriPredefined;
                    request.setUri(baseUri); // "/ws/chat"
                    ctx.fireChannelRead(msg);
                    return;
                }
            }

            // 3. 认证失败或路径不匹配：直接拒绝连接
            /*sendHttpResponse(ctx, request, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
            return;*/
        }

        ctx.fireChannelRead(msg);
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