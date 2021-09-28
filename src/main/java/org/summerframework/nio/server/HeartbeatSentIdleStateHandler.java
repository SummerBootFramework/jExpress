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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class HeartbeatSentIdleStateHandler extends IdleStateHandler {

    private static final Logger log = LogManager.getLogger(HeartbeatSentIdleStateHandler.class.getName());

    private final int writerIdleTime;// home divice=45/180/wechat=300;
    private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8));

    public HeartbeatSentIdleStateHandler(int writerIdleTime) {
        super(0, writerIdleTime, 0, TimeUnit.SECONDS);
        this.writerIdleTime = writerIdleTime;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        //if (evt instanceof IdleStateEvent) {
        IdleStateEvent event = (IdleStateEvent) evt;
        if (event.state() == IdleState.WRITER_IDLE && ctx.channel().isWritable()) {
            ctx.writeAndFlush(HEARTBEAT_SEQUENCE.duplicate());
            log.info(() -> "WRITER_IDLE(" + writerIdleTime + ") hearbeat sent " + ctx);
        }
        //}
    }
}
