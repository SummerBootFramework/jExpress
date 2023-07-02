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
package org.summerboot.jexpress.nio.grpc;

import io.grpc.ManagedChannel;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
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
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @param <T>
 */
public abstract class GRPCClient<T extends GRPCClient<T>> {

    public enum LoadBalancingPolicy {
        ROUND_ROBIN("round_robin"), PICK_FIRST("pick_first");

        private final String value;

        private LoadBalancingPolicy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }

    private static final List<NameResolverProvider> NR_Providers = new ArrayList();

    /**
     *
     * @param nameResolverProvider for client side load balancing
     * @param loadBalancingPolicy
     * @param uri The URI format should be one of grpc://host:port,
     * grpcs://host:port, or unix:///path/to/uds.sock
     * @param keyManagerFactory The Remote Caller identity
     * @param trustManagerFactory The Remote Caller trusted identities
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @return
     * @throws javax.net.ssl.SSLException
     */
    public static NettyChannelBuilder getNettyChannelBuilder(NameResolverProvider nameResolverProvider, LoadBalancingPolicy loadBalancingPolicy, URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws SSLException {
        final NettyChannelBuilder channelBuilder;
        String target = uri.toString();//"grpcs://"+uri.getAuthority()+"/service";// "grpcs:///"
        switch (uri.getScheme()) {
            case "unix": //https://github.com/grpc/grpc-java/issues/1539
                channelBuilder = NettyChannelBuilder.forAddress(new DomainSocketAddress(uri.getPath()))
                        .eventLoopGroup(new EpollEventLoopGroup())
                        .channelType(EpollDomainSocketChannel.class)
                        .usePlaintext();
                break;
            default:
                if (nameResolverProvider != null) {
                    NameResolverRegistry nameResolverRegistry = NameResolverRegistry.getDefaultRegistry();
                    for (NameResolverProvider nrp : NR_Providers) {
                        nameResolverRegistry.deregister(nrp);
                    }
                    nameResolverRegistry.register(nameResolverProvider);// use client side load balancing        
                    NR_Providers.add(nameResolverProvider);
                    String policy = loadBalancingPolicy.getValue();
                    channelBuilder = NettyChannelBuilder.forTarget(target).defaultLoadBalancingPolicy(policy);
                } else {
                    String host = uri.getHost();
                    int port = uri.getPort();
                    if (host == null) {
                        throw new IllegalArgumentException("The URI format should contains host information, like <scheme>://[host:port]/[service], like grpc:///, grpc://host:port, grpcs://host:port, or unix:///path/to/uds.sock. gRpc.client.LoadBalancing.servers should be provided when host/port are not provided.");
                    }
                    channelBuilder = NettyChannelBuilder.forAddress(host, port);
                }
                break;
        }
        if (keyManagerFactory == null) {
            channelBuilder.usePlaintext();
        } else {
            final SslContextBuilder sslBuilder = GrpcSslContexts.forClient();
            sslBuilder.keyManager(keyManagerFactory);
            if (trustManagerFactory == null) {//ignore Server Certificate
                sslBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                sslBuilder.trustManager(trustManagerFactory);
            }
            GrpcSslContexts.configure(sslBuilder, SslProvider.OPENSSL);
            if (tlsVersionProtocols != null) {
                sslBuilder.protocols(tlsVersionProtocols);
            }
            if (ciphers != null) {
                sslBuilder.ciphers(ciphers);
            }
            SslContext sslContext = sslBuilder.build();
            channelBuilder.sslContext(sslContext).useTransportSecurity();
            if (overrideAuthority != null) {
                channelBuilder.overrideAuthority(overrideAuthority);
            }
        }
        return channelBuilder;
    }

    /**
     * @param nameResolverProvider for client side load balancing
     * @param uri The URI format should be one of grpc://host:port or
     * unix:///path/to/uds.sock
     * @return
     * @throws SSLException
     */
    public static NettyChannelBuilder NettyChannelBuilder(NameResolverProvider nameResolverProvider, URI uri) throws SSLException {
        return getNettyChannelBuilder(nameResolverProvider, LoadBalancingPolicy.ROUND_ROBIN, uri, null, null, null, null);
    }

    protected final NameResolverProvider nameResolverProvider;
    protected final URI uri;
    protected final NettyChannelBuilder channelBuilder;
    protected ManagedChannel channel;

    /**
     *
     * @param nameResolverProvider for client side load balancing
     * @param uri The URI format should be one of grpc://host:port or
     * unix:///path/to/uds.sock
     * @throws SSLException
     */
    public GRPCClient(NameResolverProvider nameResolverProvider, URI uri) throws SSLException {
        this(nameResolverProvider, uri, null, null, null, null);
    }

    /**
     *
     * @param nameResolverProvider for client side load balancing
     * @param uri The URI format should be one of grpc://host:port,
     * grpcs://host:port, or unix:///path/to/uds.sock
     * @param keyManagerFactory The Remote Caller identity
     * @param trustManagerFactory The Remote Caller trusted identities
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols "TLSv1.2", "TLSv1.3"
     * @throws SSLException
     */
    public GRPCClient(NameResolverProvider nameResolverProvider, URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
            @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws SSLException {
        this.nameResolverProvider = nameResolverProvider;
        this.uri = uri;
        this.channelBuilder = getNettyChannelBuilder(nameResolverProvider, LoadBalancingPolicy.ROUND_ROBIN, uri, keyManagerFactory, trustManagerFactory, overrideAuthority, ciphers, tlsVersionProtocols);
    }

    /**
     *
     * @param channelBuilder
     */
    public GRPCClient(NettyChannelBuilder channelBuilder) {
        this.nameResolverProvider = null;
        this.uri = null;
        this.channelBuilder = channelBuilder;
    }

    public T connect() {
        disconnect();
        channel = channelBuilder.build();
        //String info = uri == null ? channel.toString() : uri.toString();
        String info = channel.authority();
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        channel.shutdownNow();
                    } catch (Throwable ex) {
                    }
                }, "GRPCClient.shutdown and disconnect from " + info));
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
//        if(nameResolverProvider!=null) {
//            NameResolverRegistry.getDefaultRegistry().deregister(nameResolverProvider);
//        }
    }
}
