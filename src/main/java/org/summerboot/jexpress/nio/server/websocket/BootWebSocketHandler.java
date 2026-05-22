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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypes;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.NioHttpUtil;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.FileUtil;

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
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 2.0
 */
abstract public class BootWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    protected Logger log = LogManager.getLogger(this.getClass());
    protected static final TextWebSocketFrame MSG_AUTH_FAILED = new TextWebSocketFrame("401 Unauthorized");

    protected final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);


    protected static final Tika TIKA = new Tika();
    protected static final MimeTypes REGISTRY = MimeTypes.getDefaultMimeTypes();

    /*@Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String uri = event.requestUri();
            System.out.println("uri=" + uri);
            String selectedSubprotocol = event.selectedSubprotocol();
            System.out.println("selectedSubprotocol=" + selectedSubprotocol);

            HttpHeaders headers = event.requestHeaders();
            List<String> requestedSubprotocols = headers.getAll(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);
            for (String requestedSubprotocol : requestedSubprotocols) {
                System.out.println("requestedSubprotocols=" + requestedSubprotocol);
            }
        }
    }*/


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Runnable asyncTask = () -> {
            Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
            if (caller == null) {
                clients.remove(ctx.channel());
                ctx.writeAndFlush(MSG_AUTH_FAILED.retainedDuplicate());
                ctx.close();
                log.warn("OTT auth failed - " + ctx.channel().remoteAddress() + ": " + ctx);
                ctx.close();
                return;
            }

            clients.add(ctx.channel());
            log.trace(() -> "handlerAdded: " + ctx.channel().remoteAddress());

            String message = onCallerConnected(ctx, caller);
            if (message != null) {
                sendToAllChannels(message, true);
            }
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.trace(() -> "channelActive: " + ctx.channel().remoteAddress());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        clients.remove(ctx.channel());
        log.trace(() -> "handlerRemoved: " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        clients.remove(ctx.channel());
        //ctx.close();
        NioHttpUtil.onExceptionCaught(ctx, cause, log);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            onTextWebSocketFrame(ctx, (TextWebSocketFrame) msg);
        } else if (msg instanceof BinaryWebSocketFrame) {
            onBinaryWebSocketFrame(ctx, (BinaryWebSocketFrame) msg);
        } else if (msg instanceof ContinuationWebSocketFrame) {
            onContinuationWebSocketFrame(ctx, (ContinuationWebSocketFrame) msg);
        }
    }

    protected void onBinaryWebSocketFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame msg) throws Exception {
        ByteBuf bb = msg.content();
        byte[] data = ByteBufUtil.getBytes(bb);
        processMessage(ctx, null, data);
    }

    protected void onTextWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        String txt = msg.text();
        processMessage(ctx, txt, null);
    }

    protected void onContinuationWebSocketFrame(ChannelHandlerContext ctx, ContinuationWebSocketFrame msg) throws Exception {

    }

    protected void processMessage(ChannelHandlerContext ctx, String text, byte[] data) {
        Runnable asyncTask = () -> {
            Caller caller = (Caller) ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
            if (text != null) {
                String responseText = onMessage(ctx, caller, text);
                if (responseText != null) {
                    sendToAllChannels(responseText, true);
                }
            } else if (data != null) {
                String[] mimeType = FileUtil.getMIMEShortExtension(data);
                StringBuilder sb = new StringBuilder();
                byte[] processedData = onMessage(ctx, caller, data, mimeType[0], mimeType[1], mimeType[2], sb);
                if (processedData != null) {
                    sendToAllChannels(processedData, true);
                }
                if (!sb.isEmpty()) {
                    sendToAllChannels(sb.toString(), true);
                }
            }

        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);

    }

    protected Caller auth(Caller caller) {
        return caller;
    }

    abstract protected String onCallerConnected(ChannelHandlerContext ctx, Caller caller);

    /**
     * @param ctx
     * @param caller
     * @param txt
     * @return non-null string will send back to peer
     */
    abstract protected String onMessage(ChannelHandlerContext ctx, Caller caller, String txt);

    abstract protected byte[] onMessage(ChannelHandlerContext ctx, Caller caller, byte[] data, String mimeType, String fileType, String fileExtension, StringBuilder builder);

    public void sendToChannel(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(new TextWebSocketFrame(message));
    }

    public void sendToChannel(ChannelHandlerContext ctx, byte[] data) {
        ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(data)));
    }

    public void sendToAllChannels(String text, boolean auth) {
        TextWebSocketFrame message = new TextWebSocketFrame(text);
        sendToAllChannels(message, auth);
    }

    public void sendToAllChannels(byte[] data, boolean auth) {
        BinaryWebSocketFrame message = new BinaryWebSocketFrame(Unpooled.copiedBuffer(data));
        sendToAllChannels(message, auth);
    }

    public void sendToAllChannels(WebSocketFrame message, boolean auth) {
        if (auth) {
            clients.stream()
                    /*.filter(channel -> {
                        Caller caller = channel.attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
                        return caller != null;
                    })*/
                    .filter(channel -> channel.attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get() != null)
                    .forEach(channel -> channel.writeAndFlush(message.retainedDuplicate()));
        } else {
            clients.stream()
                    .forEach(channel -> channel.writeAndFlush(message.retainedDuplicate()));
        }
    }

}
