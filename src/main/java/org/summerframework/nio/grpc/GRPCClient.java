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
package org.summerframework.nio.grpc;

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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 * @param <T>
 */
public abstract class GRPCClient<T extends GRPCClient<T>> {

    /**
     * <pre>
     * Creates an instance of ManagedChannel for gRPC client that will use the provided uri
     * and certificates to connect to the server.If using tcp:// or unix:// for the URL scheme, the certificate options will be ignored.
     * </pre>
     *
     * @param uri URI used to connect to the server with the format
     * scheme://path. Valid values: tcp://host:port, tls://host:port, or
     * unix:///path/to/socket
     * @param keyManagerFactory Key manager containing keys for authenticating
     * with the server.
     * @param trustManagerFactory Trust manager factory containing certificates
     * for server verification.
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @return
     * @throws IOException
     */
    public static NettyChannelBuilder getNettyChannelBuilder(URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws IOException {
        NettyChannelBuilder channelBuilder = null;
        switch (uri.getScheme()) {
            case "unix":
                try {
                channelBuilder = NettyChannelBuilder.forAddress(new DomainSocketAddress(uri.getPath()));
            } catch (Throwable ex) {
                channelBuilder = NettyChannelBuilder.forAddress(new InetSocketAddress(uri.getHost(), uri.getPort()))
                        .eventLoopGroup(new EpollEventLoopGroup())
                        .channelType(EpollDomainSocketChannel.class)
                        .usePlaintext();
            }
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
            throw new IOException("Invalid scheme specified in URI. Valid values are: tcp://, tls:// or unix://.");
        }
        return channelBuilder;
    }

    /**
     * Creates an instance of ManagedChannel for gRPC client that will use the
     * provided uri to connect to the server.
     *
     * @param uri URI used to connect to the server with the format
     * scheme://path Ex. tcp://host:port, unix:///path/to/socket
     * @return
     * @throws IOException
     */
    public static NettyChannelBuilder NettyChannelBuilder(URI uri) throws IOException {
        return getNettyChannelBuilder(uri, null, null, null, null);
    }

    protected final URI uri;
    protected final NettyChannelBuilder channelBuilder;
    protected ManagedChannel channel;

    /**
     *
     * @param uri
     * @throws IOException
     */
    public GRPCClient(URI uri) throws IOException {
        this(uri, null, null, null, null);
    }

    /**
     * <pre>
     * Creates an instance of ManagedChannel for gRPC client that will use the provided uri
     * and certificates to connect to the server.If using tcp:// or unix:// for the URL scheme, the certificate options will be ignored.
     * </pre>
     *
     * @param uri URI used to connect to the server with the format
     * scheme://path. Valid values: tcp://host:port, tls://host:port, or
     * unix:///path/to/socket
     * @param keyManagerFactory Key manager containing keys for authenticating
     * with the server.
     * @param trustManagerFactory Trust manager factory containing certificates
     * for server verification.
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @throws IOException
     */
    public GRPCClient(URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws IOException {
        this(uri, getNettyChannelBuilder(uri, keyManagerFactory, trustManagerFactory, overrideAuthority, ciphers, tlsVersionProtocols));
    }

    /**
     *
     * @param uri
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
