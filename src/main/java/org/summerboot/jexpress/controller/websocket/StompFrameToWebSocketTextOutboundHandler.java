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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.stomp.StompFrame;
import io.netty.util.CharsetUtil;

import java.util.Map;

/**
 * Converts outbound STOMP frames to WebSocket text frames for browser STOMP-over-WebSocket clients.
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class StompFrameToWebSocketTextOutboundHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (!(msg instanceof StompFrame frame)) {
            ctx.write(msg, promise);
            return;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append(frame.command().name()).append('\n');

        for (Map.Entry<CharSequence, CharSequence> h : frame.headers()) {
            sb.append(h.getKey()).append(':').append(h.getValue()).append('\n');
        }

        sb.append('\n');

        ByteBuf content = frame.content();
        if (content != null && content.isReadable()) {
            sb.append(content.toString(CharsetUtil.UTF_8));
        }

        // STOMP frame terminator
        sb.append('\u0000');

        ctx.write(new TextWebSocketFrame(sb.toString()), promise);
    }
}
