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

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HttpNioChannelInitializer extends NioChannelInitializer {

    private final static ChannelHandler DefaultFileUploadRejector = new BootHttpFileUploadRejector();

    private static final Logger LoggingHandlerLogger = LogManager.getLogger(LoggingHandler.class);

    @Inject
    @Named("BootHttpPingHandler")
    private ChannelHandler defaultHttpPingHandler;

    @Inject
    @Named("BootHttpRequestHandler")
    private ChannelHandler defaultHttpRequestHandler;

    private final ChannelHandler defaultLoggingHandler = new LoggingHandler(LogLevel.DEBUG);

    @Override
    protected void initChannelPipeline(ChannelPipeline channelPipeline, NioConfig nioCfg) {
        ChannelHandler ch;

        // 0. logging
        if (LoggingHandlerLogger.isDebugEnabled()) {
            channelPipeline.addLast(defaultLoggingHandler);
        }

        // 1*. Heartbeat: Non-HTTP
        if (nioCfg.getReaderIdleSeconds() > 0) {
            if (namedReadIdle != null) {
                for (String named : namedReadIdle) {
                    ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                    channelPipeline.addLast("tcp-ping_" + named, ch);
                }
            } else {
                channelPipeline.addLast("tcp-ping", new HeartbeatRecIdleStateHandler(nioCfg.getReaderIdleSeconds()));
            }
        }
        if (nioCfg.getWriterIdleSeconds() > 0) {
            if (namedWriteIdle != null) {
                for (String named : namedWriteIdle) {
                    ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                    channelPipeline.addLast("tcp-pong_" + named, ch);
                }
            } else {
                channelPipeline.addLast("tcp-pong", new HeartbeatSentIdleStateHandler(nioCfg.getWriterIdleSeconds()));
            }
        }

        // 2. HTTP base: codec, chunked
        channelPipeline.addLast("http-codec", new HttpServerCodec(nioCfg.getHttpServerCodec_MaxInitialLineLength(), nioCfg.getHttpServerCodec_MaxHeaderSize(), nioCfg.getHttpServerCodec_MaxChunkSize()));// to support both HTTP encode and decode in one handler for performance
        channelPipeline.addLast("http-chunked", new ChunkedWriteHandler());// to support large file transfer
        //channelPipeline.addLast(new HttpContentCompressor());

        // 3*. File upload: after codec, chunked and before aggregator
        if (namedFileUpload != null) {
            for (String named : namedFileUpload) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                channelPipeline.addLast("FileUpload_" + named, ch);// to support file upload, must before HttpObjectAggregator
            }
        } else {
            channelPipeline.addLast("FileUploadRejector", DefaultFileUploadRejector);
        }

        // 4. HTTP base: aggregator
        channelPipeline.addLast("http-aggregator", new HttpObjectAggregator(nioCfg.getHttpObjectAggregatorMaxContentLength()));// to merge multple messages into single request or response

        // 5*. websocket
        if (namedWebsocket != null) {
            boolean isWebSocketCompress = nioCfg.isWebSocketCompress();
            if (isWebSocketCompress) {
                channelPipeline.addLast(new WebSocketServerCompressionHandler());
            }

            boolean allowExtensions = nioCfg.isWebSocketAllowExtensions();
            int maxFrameSize = nioCfg.getWebSocketMaxFrameSize();
            boolean allowMaskMismatch = nioCfg.isWebSocketAllowMaskMismatch();
            boolean checkStartsWith = nioCfg.isWebSocketCheckStartsWith();
            boolean dropPongFrames = nioCfg.isWebSocketDropPongFrames();
            long handshakeTimeoutMillis = nioCfg.getWebSocketHandshakeTimeoutMillis();
            boolean isWebSocketServerProtocolHandlerRequired = true;
            for (String named : namedWebsocket) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                if (ch != null) {
                    if (isWebSocketServerProtocolHandlerRequired) {
                        String webSocketURI = named;
                        String subprotocols = null;
                        channelPipeline.addLast(new WebSocketServerProtocolHandler(webSocketURI, subprotocols, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));
                        isWebSocketServerProtocolHandlerRequired = false;
                    }
                    channelPipeline.addLast("Websocket_" + named, ch);
                }
            }
        }

        // 6*. Ping
        if (namedPing != null) {
            for (String named : namedPing) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                channelPipeline.addLast("Ping_" + named, ch);
            }
        } else {
            channelPipeline.addLast("Ping", defaultHttpPingHandler);
        }

        // 7*. Tell the pipeline to run My Business Logic Handler's event handler methods in a different thread than an I/O thread, so that the I/O thread is not blocked by a time-consuming task. If the business logic is fully asynchronous or finished very quickly, no need to specify a group.
        if (namedBusiness != null) {
            for (String named : namedBusiness) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                channelPipeline.addLast("Biz_" + named, ch);
            }
        } else {
            channelPipeline.addLast("Biz", defaultHttpRequestHandler);
        }
    }

}
