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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.summerboot.jexpress.security.auth.Caller;

public class GameModuleHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();

        if (frame instanceof TextWebSocketFrame) {
            System.out.println("[游戏模块] 收到用户 " + caller.getDisplayName() + " 的文本: " + ((TextWebSocketFrame) frame).text());
        } else if (frame instanceof BinaryWebSocketFrame) {
            System.out.println("[游戏模块] 收到用户 " + caller.getDisplayName() + " 的二进制数据流");
        }
    }
}
