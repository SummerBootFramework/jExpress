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
package org.summerboot.jexpress.nio.server;

import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.nio.server.multipart.MultipartUtil;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@ChannelHandler.Sharable
@Singleton
public class BootHttpFileUploadRejector extends SimpleChannelInboundHandler<HttpObject> {

    protected static Logger log = LogManager.getLogger(BootHttpFileUploadRejector.class.getName());

    protected static final boolean AUTO_RELEASE = false;

    public BootHttpFileUploadRejector() {
        super(AUTO_RELEASE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) {
        NioHttpUtil.onExceptionCaught(ctx, ex, log);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject httpObject) throws Exception {
        boolean isMultipart = false;
        if (httpObject instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) httpObject;
            isMultipart = MultipartUtil.isMultipart(request);
            if (isMultipart) {
                ReferenceCountUtil.release(httpObject);
                //NioHttpUtil.sendError(ctx, HttpResponseStatus.FORBIDDEN, BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, "file upload not supported", null);
                //TimeUnit.MILLISECONDS.sleep(1000);// give it time to flush the error message to client
                ctx.channel().close();// the only way to stop uploading is to close socket 
                return;
            }
        }
        if (!isMultipart) {
            //pass to next Handler
            ctx.fireChannelRead(httpObject);
        }
    }
}
