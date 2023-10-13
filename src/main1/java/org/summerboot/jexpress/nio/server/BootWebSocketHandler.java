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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.security.auth.Caller;

/**
 * usage example:
 *
 * <pre>
 * {@code
 *
 * add to cfg_nio.properties: nio.WebSocketHandler=/mywebsocket/demo
 *
 * @ChannelHandler.Sharable
 * @Singleton
 * @Service(binding = ChannelHandler.class, named = "/mywebsocket/demo")
 * public class MyHandler extends BootWebSocketHandler {
 *
 * @Override
 * protected Caller auth(String token) {
 * return new User(0, token);
 * }
 *
 * }
 *
 * }
 * </pre>
 *
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 2.0
 */
abstract public class BootWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    protected Logger log = LogManager.getLogger(this.getClass());
    protected static final TextWebSocketFrame MSG_AUTH_FAILED = new TextWebSocketFrame("401 Unauthorized");

    protected static final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    protected static final AttributeKey KEY_CALLER = AttributeKey.valueOf("caller");

//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
//            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
//            String uri = event.requestUri();
//            System.out.println("uri=" + uri);
//            String p = event.selectedSubprotocol();
//            System.out.println("selectedSubprotocol=" + p);
//
//            HttpHeaders headers = event.requestHeaders();
//            List<String> requestedSubprotocols = headers.getAll(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
//            System.out.println("requestedSubprotocols=" + requestedSubprotocols);
//        }
//    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            channelRead0(ctx, (TextWebSocketFrame) msg);
        } else if (msg instanceof BinaryWebSocketFrame) {
            channelRead0(ctx, (BinaryWebSocketFrame) msg);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            channelRead0(ctx, (ContinuationWebSocketFrame) msg);
        }
    }

    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
        ByteBuf bb = msg.content();
        byte[] data = ByteBufUtil.getBytes(bb);
        Runnable asyncTask = () -> {
            Caller caller = (Caller) ctx.channel().attr(KEY_CALLER).get();
            if (caller == null) {
                clients.remove(ctx.channel());
                ctx.writeAndFlush(MSG_AUTH_FAILED.retainedDuplicate());
                ctx.close();
                log.info("Binary auth failed " + ctx.channel().remoteAddress());
                return;
            }
            String responseText = onMessage(ctx, caller, data);
            if (responseText != null) {
                sendToChannel(ctx, responseText);
            }
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        String txt = msg.text();
        Runnable asyncTask = () -> {
            Caller caller = (Caller) ctx.channel().attr(KEY_CALLER).get();
            if (caller == null) {
                caller = auth(txt);//use the first message as token to auth
                if (caller == null) {
                    clients.remove(ctx.channel());
                    ctx.writeAndFlush(MSG_AUTH_FAILED.retainedDuplicate());
                    ctx.close();
                    log.info("Text auth failed " + ctx.channel().remoteAddress() + ": " + txt);
                    return;
                }
                ctx.channel().attr(KEY_CALLER).set(caller);
                String message = onCallerConnected(ctx, caller);
                if (message != null) {
                    sendToAllChannels(message, true);
                }
                return;
            }

            String responseText = onMessage(ctx, caller, txt);
            if (responseText != null) {
                sendToChannel(ctx, responseText);
            }
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    protected void channelRead0(ChannelHandlerContext ctx, ContinuationWebSocketFrame msg) throws Exception {

    }

    abstract protected Caller auth(String token);

    abstract protected String onCallerConnected(ChannelHandlerContext ctx, Caller caller);

    /**
     *
     * @param ctx
     * @param caller
     * @param txt
     * @return non-null string will send back to peer
     */
    abstract protected String onMessage(ChannelHandlerContext ctx, Caller caller, String txt);

    abstract protected String onMessage(ChannelHandlerContext ctx, Caller caller, byte[] data);

    public static void sendToChannel(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(new TextWebSocketFrame(message));
    }

    public static void sendToChannel(ChannelHandlerContext ctx, byte[] data) {
        ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data)));
    }

    public static void sendToAllChannels(String text, boolean auth) {
        TextWebSocketFrame message = new TextWebSocketFrame(text);
        sendToAllChannels(message, auth);
    }

    public static void sendToAllChannels(byte[] data, boolean auth) {
        BinaryWebSocketFrame message = new BinaryWebSocketFrame(Unpooled.copiedBuffer(data));
        sendToAllChannels(message, auth);
    }

    public static void sendToAllChannels(WebSocketFrame message, boolean auth) {
        if (auth) {
            clients.stream()
                    .filter(channel -> channel.attr(KEY_CALLER).get() != null)
                    .forEach(channel -> channel.writeAndFlush(message.retainedDuplicate()));
        } else {
            clients.stream()
                    .forEach(channel -> channel.writeAndFlush(message.retainedDuplicate()));
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        clients.add(ctx.channel());
        log.debug(() -> "handlerAdded: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug(() -> "channelActive: " + ctx.channel().remoteAddress());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        clients.remove(ctx.channel());
        log.debug(() -> "handlerRemoved: " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        clients.remove(ctx.channel());
        ctx.close();
        log.error(() -> "exceptionCaught: " + ctx.channel().remoteAddress() + " - " + cause);
    }

}
