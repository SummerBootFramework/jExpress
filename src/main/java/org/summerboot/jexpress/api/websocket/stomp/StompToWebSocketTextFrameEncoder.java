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

package org.summerboot.jexpress.api.websocket.stomp;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.stomp.StompSubframe;
import io.netty.handler.codec.stomp.StompSubframeEncoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts outbound STOMP frames to WebSocket text frames for browser STOMP-over-WebSocket clients.
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class StompToWebSocketTextFrameEncoder extends StompSubframeEncoder {

    @Override
    protected void encode(ChannelHandlerContext ctx, StompSubframe msg, List<Object> out) throws Exception {
        // 1) Temporary local buffer to hold standard ByteBuf output from parent encoder.
        List<Object> localOut = new ArrayList<>(2);

        // 2) Delegate to parent encoder for standard STOMP encoding.
        // This handles StompFrame/StompSubframe, heartbeats, header escaping, and protocol details.
        super.encode(ctx, msg, localOut);

        // 3) Wrap encoded payload as WebSocket text frames for browser transport.
        for (Object encoded : localOut) {
            if (encoded instanceof ByteBuf buf) {
                // Zero-copy wrap: keep the same ByteBuf and add a WebSocket text frame shell.
                out.add(new TextWebSocketFrame(buf));
            } else {
                // Defensive fallback: pass through unexpected output types unchanged.
                out.add(encoded);
            }
        }

        // Note: no manual ReferenceCountUtil.release(msg) is needed.
        // The parent encoder lifecycle handles message release correctly.
    }

}