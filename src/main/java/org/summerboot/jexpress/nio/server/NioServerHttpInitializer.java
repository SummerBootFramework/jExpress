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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
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
abstract public class NioServerHttpInitializer extends ChannelInitializer<SocketChannel> {

    protected static final Logger log = LogManager.getLogger(NioServerHttpInitializer.class.getName());

    @Deprecated
    protected SSLContext jdkSslContext;
    @Deprecated
    protected boolean verifyClient;

    protected final SslContext nettySslContext;
    protected final NioConfig nioCfg;

    @Deprecated
    protected final String loadBalancingPingEndpoint;

    /**
     *
     * @param nettySslContext
     * @param nioCfg
     * @param loadBalancingPingEndpoint
     */
    public NioServerHttpInitializer(SslContext nettySslContext, NioConfig nioCfg, String loadBalancingPingEndpoint) {
        this.nettySslContext = nettySslContext;
        this.nioCfg = nioCfg;
        this.loadBalancingPingEndpoint = loadBalancingPingEndpoint;
    }

//    private static final int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4096;
//    private static final int DEFAULT_MAX_HEADER_SIZE = 8192;
//    private static final int DEFAULT_MAX_CHUNK_SIZE = 8192;
    @Override
    public void initChannel(SocketChannel socketChannel) {
        long tc = NioCounter.COUNTER_TOTAL_CHANNEL.incrementAndGet();
        log.debug(() -> tc + "[" + this.hashCode() + "]" + socketChannel);

        ChannelPipeline channelPipeline = socketChannel.pipeline();
        if (nettySslContext != null) {
            initSSL_OpenSSL(socketChannel, channelPipeline);
        }
        initChannel(socketChannel, channelPipeline);
    }

    protected abstract void initChannel(SocketChannel socketChannel, ChannelPipeline pipeline);

    protected void initSSL_OpenSSL(SocketChannel socketChannel, ChannelPipeline pipeline) {
        SslHandler sslHandler = nettySslContext.newHandler(socketChannel.alloc());
        if (nioCfg.isVerifyCertificateHost()) {
            SSLEngine sslEngine = sslHandler.engine();
            SSLParameters sslParameters = sslEngine.getSSLParameters();
            // only available since Java 7
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslEngine.setSSLParameters(sslParameters);
        }
        pipeline.addLast("ssl", sslHandler);
    }

    /**
     *
     * @param jdkSSLContext
     * @param verifyClient
     * @param nioCfg
     * @param loadBalancingEndpoint
     */
    @Deprecated
    public NioServerHttpInitializer(SSLContext jdkSSLContext, boolean verifyClient, NioConfig nioCfg, String loadBalancingPingEndpoint) {
        this.jdkSslContext = jdkSSLContext;
        this.verifyClient = verifyClient;
        this.nettySslContext = null;
        this.nioCfg = nioCfg;
        this.loadBalancingPingEndpoint = loadBalancingPingEndpoint;
    }

    @Deprecated
    protected void initSSL_JDK(ChannelPipeline pipeline) {
        if (jdkSslContext == null) {
            return;
        }
        // create SSL engine
        SSLEngine engine = jdkSslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(verifyClient);
        engine.setWantClientAuth(verifyClient);
        // specify protocols
        String[] protocols = nioCfg.getSslProtocols();
        if (protocols != null && protocols.length > 0) {
            engine.setEnabledProtocols(protocols);
        }
        // specify cipher suites
        String[] cipherSuites = nioCfg.getSslCipherSuites();
        if (cipherSuites != null && cipherSuites.length > 0) {
            engine.setEnabledCipherSuites(cipherSuites);
        }
        // Add SSL handler first to encrypt and decrypt everything.
        SslHandler sslHandler = new SslHandler(engine);
        long sslHandshakeTimeoutSeconds = nioCfg.getSslHandshakeTimeoutSeconds();
        if (sslHandshakeTimeoutSeconds > 0) {
            sslHandler.setHandshakeTimeout(sslHandshakeTimeoutSeconds, TimeUnit.SECONDS);
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
        pipeline.addLast("ssl", sslHandler);
    }

}
