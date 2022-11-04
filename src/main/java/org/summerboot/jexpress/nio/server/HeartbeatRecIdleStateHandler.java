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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HeartbeatRecIdleStateHandler extends IdleStateHandler {

    private static final Logger log = LogManager.getLogger(HeartbeatRecIdleStateHandler.class.getName());
    // Failure counter: did not receive the ping request sent by the client

    private final int readerIdleTime;// home divice=45/180/wechat=300;

    public HeartbeatRecIdleStateHandler(int readerIdleTime) {
        super(readerIdleTime, 0, 0, TimeUnit.SECONDS);
        this.readerIdleTime = readerIdleTime;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        if (evt.state() == IdleState.READER_IDLE) {
            // close channel when idle MAX_UN_REC_PING_TIMES, client needs to reconnect
            log.info(() -> "READER_IDLE(" + readerIdleTime + ") close channel" + ctx);
            ctx.close();
        }
    }
}
