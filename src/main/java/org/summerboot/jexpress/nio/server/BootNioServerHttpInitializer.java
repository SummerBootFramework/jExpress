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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootNioServerHttpInitializer extends NioServerHttpInitializer {

    public BootNioServerHttpInitializer(SslContext nettySslContext, NioConfig nioCfg, String loadBalancingPingEndpoint) {
        super(nettySslContext, nioCfg, loadBalancingPingEndpoint);
    }

    @Override
    protected void initChannel(SocketChannel socketChannel, ChannelPipeline channelPipeline) {
        //Client Heartbeat not in my control: 
        if (nioCfg.getReaderIdleSeconds() > 0) {
            channelPipeline.addLast("tcp-pong", new HeartbeatRecIdleStateHandler(nioCfg.getReaderIdleSeconds()));
        }
        if (nioCfg.getWriterIdleSeconds() > 0) {
            channelPipeline.addLast("tcp-ping", new HeartbeatSentIdleStateHandler(nioCfg.getWriterIdleSeconds()));
        }

        //1. HTTP based handlers
        channelPipeline.addLast("http-codec", new HttpServerCodec(nioCfg.getHttpServerCodec_MaxInitialLineLength(),
                nioCfg.getHttpServerCodec_MaxHeaderSize(),
                nioCfg.getHttpServerCodec_MaxChunkSize()));// to support both HTTP encode and decode in one handler for performance
        //p.addLast(new HttpContentCompressor());
        ChannelHandler ch = nioCfg.getHttpFileUploadHandler();
        if (ch != null) {
            channelPipeline.addLast("biz-fileUploadHandler", ch);// to support file upload, must beforeHttpObjectAggregator and  ChunkedWriteHandler
        }
        channelPipeline.addLast("http-aggregator", new HttpObjectAggregator(nioCfg.getHttpObjectAggregatorMaxContentLength()));// to merge multple messages into single request or response
        channelPipeline.addLast("http-chunked", new ChunkedWriteHandler());// to support large file transfer

        //2. 
        String webSocketURI = nioCfg.getWebSocketHandlerAnnotatedName();
        if (webSocketURI != null) {
            String subprotocols = nioCfg.getWebSocketSubprotocols();
            boolean allowExtensions = nioCfg.isWebSocketAllowExtensions();
            int maxFrameSize = nioCfg.getWebSocketMaxFrameSize();
            channelPipeline.addLast(new WebSocketServerProtocolHandler(webSocketURI, subprotocols, allowExtensions, maxFrameSize));
            if (nioCfg.isWebSocketCompress()) {
                channelPipeline.addLast(new WebSocketServerCompressionHandler());
            }
            ch = nioCfg.getWebSockettHandler();
            if (ch != null) {
                channelPipeline.addLast(ch);
            }
        }

        //3. Tell the pipeline to run My Business Logic Handler's event handler methods in a different thread than an I/O thread, so that the I/O thread is not blocked by a time-consuming task.
        // If the business logic is fully asynchronous or finished very quickly, no need to specify a group.
        //p.addLast(cfg.getNioSharedChildExecutor(), "bizexe", cfg.getRequestHandler());
        //p.addLast("sslhandshake", shh);
        if (loadBalancingPingEndpoint != null) {
            ch = nioCfg.getPingHandler();
            if (ch != null) {
                channelPipeline.addLast("biz-pingHandler", ch);
            }
        }
        ch = nioCfg.getRequestHandler();
        if (ch != null) {
            channelPipeline.addLast("biz-requestHandler", ch);
        }
    }

}
