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
package org.jexpress.nio.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollDomainSocketChannel;
import io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.unix.DomainSocketAddress;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import jakarta.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @param <T>
 */
public abstract class GRPCClient<T extends GRPCClient<T>> {

    /**
     *
     * @param uri The URI format should be one of tcp://host:port, tls://host:port, or unix:///path/to/uds.sock
     * @param keyManagerFactory The Remote Caller identity
     * @param trustManagerFactory The Remote Caller trusted identities
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @return
     * @throws javax.net.ssl.SSLException
     */
    public static NettyChannelBuilder getNettyChannelBuilder(URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws SSLException {
        NettyChannelBuilder channelBuilder = null;
        switch (uri.getScheme()) {
            case "unix": //https://github.com/grpc/grpc-java/issues/1539
                channelBuilder = NettyChannelBuilder.forAddress(new DomainSocketAddress(uri.getPath()))
                        .eventLoopGroup(new EpollEventLoopGroup())
                        .channelType(EpollDomainSocketChannel.class)
                        .usePlaintext();
                break;
            case "tcp":
                channelBuilder = NettyChannelBuilder.forAddress(uri.getHost(), uri.getPort());
                channelBuilder.usePlaintext();
                break;
            case "tls":
                channelBuilder = NettyChannelBuilder.forAddress(uri.getHost(), uri.getPort());
                SslContextBuilder sslBuilder = GrpcSslContexts.forClient();
                sslBuilder.keyManager(keyManagerFactory);
                if (trustManagerFactory == null) {//ignore Server Certificate
                    sslBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
                } else {
                    sslBuilder.trustManager(trustManagerFactory);
                    if (overrideAuthority != null) {
                        channelBuilder.overrideAuthority(overrideAuthority);
                    }
                }
                sslBuilder = GrpcSslContexts.configure(sslBuilder, SslProvider.OPENSSL);
                if (tlsVersionProtocols != null) {
                    sslBuilder.protocols(tlsVersionProtocols);
                }
                if (ciphers != null) {
                    sslBuilder.ciphers(ciphers);
                }
                SslContext sslContext = sslBuilder.build();
                channelBuilder.sslContext(sslContext).useTransportSecurity();

                break;
        }
        if (channelBuilder == null) {
            throw new IllegalArgumentException("The URI format should be one of tcp://host:port, tls://host:port, or unix:///path/to/uds.sock");
        }
        return channelBuilder;
    }

    /**
     * @param uri The URI format should be one of tcp://host:port or unix:///path/to/uds.sock
     * @return
     * @throws SSLException
     */
    public static NettyChannelBuilder NettyChannelBuilder(URI uri) throws SSLException {
        return getNettyChannelBuilder(uri, null, null, null, null);
    }

    protected final URI uri;
    protected final NettyChannelBuilder channelBuilder;
    protected ManagedChannel channel;

    /**
     *
     * @param uri The URI format should be one of tcp://host:port or unix:///path/to/uds.sock
     * @throws SSLException
     */
    public GRPCClient(URI uri) throws SSLException {
        this(uri, null, null, null, null);
    }

    /**
     *
     * @param uri The URI format should be one of tcp://host:port, tls://host:port, or unix:///path/to/uds.sock
     * @param keyManagerFactory The Remote Caller identity
     * @param trustManagerFactory The Remote Caller trusted identities
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @throws SSLException
     */
    public GRPCClient(URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws SSLException {
        this(uri, getNettyChannelBuilder(uri, keyManagerFactory, trustManagerFactory, overrideAuthority, ciphers, tlsVersionProtocols));
    }

    /**
     *
     * @param uri The URI format should be one of tcp://host:port, tls://host:port, or unix:///path/to/uds.sock
     * @param channelBuilder
     */
    public GRPCClient(URI uri, NettyChannelBuilder channelBuilder) {
        this.uri = uri;
        this.channelBuilder = channelBuilder;
    }

    public T connect() {
        disconnect();
        channel = channelBuilder.build();
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        channel.shutdownNow();
                    } catch (Throwable ex) {
                    }
                }, "GRPCClient.shutdown and disconnect from " + uri));
        onConnected(channel);
        return (T) this;
    }

    /**
     *
     * @param channel
     */
    protected abstract void onConnected(ManagedChannel channel);

    public void disconnect() {
//        ManagedChannel c = (ManagedChannel) blockingStub.getChannel();
        if (channel != null) {
            try {
                channel.shutdownNow();
            } catch (Throwable ex) {
            } finally {
                channel = null;
            }
        }
    }
}
