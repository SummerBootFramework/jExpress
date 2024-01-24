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

import com.google.inject.Injector;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Service;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.Map;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class NioChannelInitializer extends ChannelInitializer<SocketChannel> {

    protected static final Logger log = LogManager.getLogger(NioChannelInitializer.class.getName());

    protected Injector injector;
    protected SslContext nettySslContext;
    protected NioConfig nioCfg;
    protected Map<Service.ChannelHandlerType, Set<String>> channelHandlerNames;

    // trade space for perofrmance
    protected Set<String> namedReadIdle;
    protected Set<String> namedWriteIdle;
    protected Set<String> namedFileUpload;
    protected Set<String> namedWebsocket;
    protected Set<String> namedPing;
    protected Set<String> namedBusiness;

    public NioChannelInitializer() {
    }

    public NioChannelInitializer init(Injector injector, Map<Service.ChannelHandlerType, Set<String>> channelHandlerNames) {
        this.injector = injector;
        this.channelHandlerNames = channelHandlerNames;
        // trade space for perofrmance
        namedReadIdle = channelHandlerNames.get(Service.ChannelHandlerType.ReadIdle);
        if (namedReadIdle != null && namedReadIdle.isEmpty()) {
            namedReadIdle = null;
        }

        namedWriteIdle = channelHandlerNames.get(Service.ChannelHandlerType.WriteIdle);
        if (namedWriteIdle != null && namedWriteIdle.isEmpty()) {
            namedWriteIdle = null;
        }

        namedFileUpload = channelHandlerNames.get(Service.ChannelHandlerType.FileUpload);
        if (namedFileUpload != null && namedFileUpload.isEmpty()) {
            namedFileUpload = null;
        }

        namedWebsocket = channelHandlerNames.get(Service.ChannelHandlerType.Websocket);
        if (namedWebsocket != null && namedWebsocket.isEmpty()) {
            namedWebsocket = null;
        }

        namedPing = channelHandlerNames.get(Service.ChannelHandlerType.Ping);
        if (namedPing != null && namedPing.isEmpty()) {
            namedPing = null;
        }

        namedBusiness = channelHandlerNames.get(Service.ChannelHandlerType.Business);
        if (namedBusiness != null && namedBusiness.isEmpty()) {
            namedBusiness = null;
        }

        return this;
    }

    public void initSSL(SslContext nettySslContext, NioConfig nioCfg) {
        this.nettySslContext = nettySslContext;
        this.nioCfg = nioCfg;
    }

    @Override
    public void initChannel(SocketChannel socketChannel) {
        long tc = NioCounter.COUNTER_TOTAL_CHANNEL.incrementAndGet();
        log.debug(() -> tc + "[" + this.hashCode() + "]" + socketChannel);

        ChannelPipeline channelPipeline = socketChannel.pipeline();
        if (nettySslContext != null) {
            initSSL_OpenSSL(socketChannel, channelPipeline);
        }
        initChannelPipeline(channelPipeline, nioCfg);
    }

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

    protected abstract void initChannelPipeline(ChannelPipeline pipeline, NioConfig nioCfg);

    /*@Deprecated
    protected void initSSL_JDK(ChannelPipeline pipeline) {
        if (ctx.jdkSslContext == null) {
            return;
        }
        // create SSL engine
        SSLEngine engine = ctx.jdkSslContext.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(ctx.verifyClient);
        engine.setWantClientAuth(ctx.verifyClient);
        // specify protocols
        String[] protocols = ctx.getNioCfg().getSslProtocols();
        if (protocols != null && protocols.length > 0) {
            engine.setEnabledProtocols(protocols);
        }
        // specify cipher suites
        String[] cipherSuites = ctx.getNioCfg().getSslCipherSuites();
        if (cipherSuites != null && cipherSuites.length > 0) {
            engine.setEnabledCipherSuites(cipherSuites);
        }
        // Add SSL handler first to encrypt and decrypt everything.
        SslHandler sslHandler = new SslHandler(engine);
        long sslHandshakeTimeoutSeconds = ctx.getNioCfg().getSslHandshakeTimeoutSeconds();
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
    }*/
}
