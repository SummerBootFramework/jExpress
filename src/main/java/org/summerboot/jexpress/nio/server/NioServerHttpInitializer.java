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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class NioServerHttpInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = LogManager.getLogger(NioServerHttpInitializer.class.getName());

    private final SslContext nettySslContext;
    private final SSLContext jdkSslContext;
    private final boolean verifyClient;
    private final NioConfig cfg;
    private final boolean isHttpService;

    /**
     *
     * @param sharedNioExecutorGroup
     * @param sslCtx
     * @param is2WaySSL
     * @param cfg
     */
    NioServerHttpInitializer(SSLContext jdkSSLContext, SslContext nettySslContext, boolean verifyClient, NioConfig cfg) {
        this.jdkSslContext = jdkSSLContext;
        this.nettySslContext = nettySslContext;
        this.verifyClient = verifyClient;
        this.cfg = cfg;
        isHttpService = cfg.isHttpService();
    }

    private void configureSsl(SocketChannel ch, ChannelPipeline p) {
        SslHandler sslHandler = null;
        if (nettySslContext != null) {
            sslHandler = nettySslContext.newHandler(ch.alloc());
            if (cfg.isVerifyCertificateHost()) {
                SSLEngine sslEngine = sslHandler.engine();
                SSLParameters sslParameters = sslEngine.getSSLParameters();
                // only available since Java 7
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslEngine.setSSLParameters(sslParameters);
            }
        } else if (jdkSslContext != null) {
            // create SSL engine
            SSLEngine engine = jdkSslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(verifyClient);
            engine.setWantClientAuth(verifyClient);
            // specify protocols
            String[] protocols = cfg.getSslProtocols();
            if (protocols != null && protocols.length > 0) {
                engine.setEnabledProtocols(protocols);
            }
            // specify cipher suites
            String[] cipherSuites = cfg.getSslCipherSuites();
            if (cipherSuites != null && cipherSuites.length > 0) {
                engine.setEnabledCipherSuites(cipherSuites);
            }
            // Add SSL handler first to encrypt and decrypt everything.
            sslHandler = new SslHandler(engine);
            long sslHandshakeTimeout = cfg.getSslHandshakeTimeout();
            if (sslHandshakeTimeout > 0) {
                sslHandler.setHandshakeTimeout(sslHandshakeTimeout, TimeUnit.SECONDS);
            }
            // log
            if (log.isTraceEnabled()) {
                for (String s : engine.getEnabledProtocols()) {
                    log.trace("\tProtocol = " + s);
                }
                for (String s : engine.getEnabledCipherSuites()) {
                    log.trace("\tCipher = " + s);
                }
            }
        }
        if (sslHandler != null) {
            p.addLast("ssl", sslHandler);
        }
    }

//    private static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
//    private static final int DEFAULT_MAX_HEADER_SIZE = 8192;
//    private static final int DEFAULT_MAX_CHUNK_SIZE = 8192;
    @Override
    public void initChannel(SocketChannel ch) {
        long tc = NioServerContext.COUNTER_TOTAL_CHANNEL.incrementAndGet();
        log.debug(() -> tc + "[" + this.hashCode() + "]" + ch);

        ChannelPipeline p = ch.pipeline();
        configureSsl(ch, p);

        //Client Heartbeat not in my control: 
        if (cfg.getReaderIdleTime() > 0) {
            p.addLast("tcp-pong", new HeartbeatRecIdleStateHandler(cfg.getReaderIdleTime()));
        }
        if (cfg.getWriterIdleTime() > 0) {
            p.addLast("tcp-ping", new HeartbeatSentIdleStateHandler(cfg.getWriterIdleTime()));
        }
        if (isHttpService) {
            //HTTP based handlers
            p.addLast("http-codec", new HttpServerCodec(cfg.getHttpServerCodec_MaxInitialLineLength(),
                    cfg.getHttpServerCodec_MaxHeaderSize(),
                    cfg.getHttpServerCodec_MaxChunkSize()));// to support both HTTP encode and decode in one handler for performance
            //p.addLast(new HttpContentCompressor());
            ChannelHandler chl = cfg.getHttpFileUploadHandler();
            if (chl != null) {
                p.addLast("biz-fileUploadHandler", chl);// to support file upload, must beforeHttpObjectAggregator and  ChunkedWriteHandler
            }
            p.addLast("http-aggregator", new HttpObjectAggregator(cfg.getHttpObjectAggregatorMaxContentLength()));// to merge multple messages into single request or response
            p.addLast("http-chunked", new ChunkedWriteHandler());// to support large file transfer
        }
        // Tell the pipeline to run My Business Logic Handler's event handler methods in a different thread than an I/O thread, so that the I/O thread is not blocked by a time-consuming task.
        // If the business logic is fully asynchronous or finished very quickly, no need to specify a group.
        //p.addLast(cfg.getNioSharedChildExecutor(), "bizexe", cfg.getRequestHandler());
        //p.addLast("sslhandshake", shh);
        ChannelHandler chl = cfg.getPingHandler();
        if (chl != null) {
            p.addLast("biz-pingHandler", chl);
        }
        if (cfg.isCompressWebSocket()) {
            p.addLast(new WebSocketServerCompressionHandler());
        }
        p.addLast("biz-requestHandler", cfg.getRequestHandler());

    }

}
