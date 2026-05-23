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
import org.summerboot.jexpress.nio.server.websocket.WebSocketAuthHandler_OTT;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HttpNioChannelInitializer extends NioChannelInitializer {

    protected final static ChannelHandler DefaultFileUploadRejector = new BootHttpFileUploadRejector();

    protected static final Logger LoggingHandlerLogger = LogManager.getLogger(LoggingHandler.class);

    @Inject
    @Named("BootHttpPingHandler")
    protected ChannelHandler defaultHttpPingHandler;

    @Inject
    @Named("BootHttpRequestHandler")
    protected ChannelHandler defaultHttpRequestHandler;

    protected final ChannelHandler defaultLoggingHandler = new LoggingHandler(LogLevel.DEBUG);

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
        if (namedFileUpload != null && !namedFileUpload.isEmpty()) {
            for (String named : namedFileUpload) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                channelPipeline.addLast("FileUpload_" + named, ch);// to support file upload, must before HttpObjectAggregator
            }
        } else {
            channelPipeline.addLast("FileUploadRejector", DefaultFileUploadRejector);
        }

        // 4. HTTP base: aggregator
        channelPipeline.addLast("http-aggregator", new HttpObjectAggregator(nioCfg.getHttpObjectAggregatorMaxContentLength()));// to merge multple messages into single request or response

        // 5. websocket
        if (namedWebsocket != null) {
            boolean isWebSocketCompress = nioCfg.isWebSocketCompress();
            if (isWebSocketCompress) {
                channelPipeline.addLast(new WebSocketServerCompressionHandler(nioCfg.getMaxCompressAllocation()));
            }

            boolean allowExtensions = nioCfg.isWebSocketAllowExtensions();
            int maxFrameSize = nioCfg.getWebSocketMaxFrameSize();
            boolean allowMaskMismatch = nioCfg.isWebSocketAllowMaskMismatch();
            boolean checkStartsWith = nioCfg.isWebSocketCheckStartsWith();
            boolean dropPongFrames = nioCfg.isWebSocketDropPongFrames();
            long handshakeTimeoutMillis = nioCfg.getWebSocketHandshakeTimeoutMs();

            // 1. 【核心第一步】加入动态路由认证处理器
            // 该处理器执行完毕后，会根据路径，动态往管道最后添加 ChatModuleHandler 或 GameModuleHandler
            channelPipeline.addLast(WebSocketAuthHandler_OTT.CHANNEL_NAME, new WebSocketAuthHandler_OTT(injector, namedWebsocket));

            // 2. 【核心第二步】添加官方协议处理器
            // 关键点：我们将路径设置为 null。设置为 null 意味着它不会主动去拦截并匹配固定 URL，
            // 而是只要看到带有符合标准的 WebSocket Upgrade 请求头，它就会自动在原地执行握手升级！
            // 这样无论我们前面把 URI 改成 /ws/chat 还是 /ws/game，它都能兼容升级。
            String webSocketURI = WebSocketAuthHandler_OTT.WS_PATH;
            channelPipeline.addLast(WebSocketAuthHandler_OTT.CHANNEL_CHANNEL_NAME_NEXT, new WebSocketServerProtocolHandler(webSocketURI, null, allowExtensions, maxFrameSize, allowMaskMismatch, checkStartsWith, dropPongFrames, handshakeTimeoutMillis));

            // 3. 注意：这里【不要】像之前一样 addLast(new BusinessHandler) 了。
            // 因为具体的业务 Handler 已经被 AuthHandler 在第一步动态注入到最末尾了。
            //channelPipeline.addLast(new ChatModuleHandler());
            List<String> orderedNames = new ArrayList<>(namedWebsocket);
            orderedNames.sort(String.CASE_INSENSITIVE_ORDER);
            for (String named : orderedNames) {
                if (true) break;
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                if (ch != null) {
                    channelPipeline.addLast("Websocket_" + named, ch);
                    //channelPipeline.addLast("Websocket_" + named, new ChatModuleHandler());
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

        // 7*. Tell the pipeline to run My Business Logic Handler's event handler methods in a different thread than an I/O thread, so that the I/O thread is not blocked by a time-consuming task.
        // If the business logic is fully asynchronous or finished very quickly, no need to specify a group.
        if (namedBusiness != null) {
            List<String> orderedNames = new ArrayList<>(namedBusiness);
            orderedNames.sort(String.CASE_INSENSITIVE_ORDER);
            for (String named : orderedNames) {
                ch = injector.getInstance(Key.get(ChannelHandler.class, Names.named(named)));
                if (ch != null) {
                    channelPipeline.addLast("Biz_" + named, ch);
                }
            }
        }
        channelPipeline.addLast("Biz_jExpress", defaultHttpRequestHandler);
    }

}
