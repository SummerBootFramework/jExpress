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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.annotation.Nullable;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
//@ImportResource(SummerApplication.CFG_GRPCCLIENT)
abstract public class GRPCClientConfig extends BootConfig {

    public static void main(String[] args) {
        class a extends GRPCClientConfig {
        }
        String t = generateTemplate(a.class);
        System.out.println(t);
    }

    protected static final String FILENAME_TRUSTSTORE_4CLIENT = "truststore_grpc_client.p12";

    protected final static String ID = "gRpc.client";

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

    public enum DefaultTrustStore {
        JDK, InsecureTrustAll
    }

    protected GRPCClientConfig() {
    }

    //1. NIO Network Listeners
    @ConfigHeader(title = "1. " + ID + " provider",
            format = "server1:port1, server2:port2, ..., serverN:portN",
            example = "localhost:8424, remotehost:8425, 127.0.0.1:8426")
    @Config(key = ID + ".LoadBalancing.servers", predefinedValue = "0.0.0.0:8424, 0.0.0.0:8425", required = false)
    protected volatile List<InetSocketAddress> loadBalancingServers;
    @Config(key = ID + ".LoadBalancing.scheme", defaultValue = "grpc", desc = "In case you have more than one gRPC client needs to connect to different gRPC services, you can set this to distinguish them")
    protected volatile String loadBalancingTargetScheme = "grpc";

    @Config(key = ID + ".LoadBalancing.policy", defaultValue = "ROUND_ROBIN", desc = "available options: ROUND_ROBIN, PICK_FIRST")
    protected volatile LoadBalancingPolicy loadBalancingPolicy;

    protected volatile NameResolverProvider nameResolverProvider;

    //1. gRPC connection
    @Config(key = ID + ".target.url", defaultValue = "grpc:///",
            desc = "grpc:///\n"
                    + "grpc://127.0.0.1:8424\n"
                    + "unix:/tmp/grpcsrver.socket")
    protected volatile URI uri;

    @Config(key = ID + ".ssl.Protocols", defaultValue = "TLSv1.3", desc = DESC_TLS_PROTOCOL)// "TLSv1.2, TLSv1.3"
    protected String[] tlsProtocols;
    @Config(key = ID + ".ssl.ciphers")
    protected List<String> ciphers;
    @Config(key = ID + ".ssl.Provider", defaultValue = "OPENSSL", desc = "ssl provider: OPENSSL (default), OPENSSL_REFCNT,  JDK")
    protected SslProvider sslProvider = SslProvider.OPENSSL;

    //2. TRC (The Remote Caller) keystore    
    protected static final String KEY_kmf_key = ID + ".ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = ID + ".ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = ID + ".ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = ID + ".ssl.KeyPwd";

    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF_CLIENT,
            callbackMethodName4Dump = "generateTemplate_keystore")
    //@JsonIgnore
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + DEFAULT_DEC_VALUE);
        sb.append(KEY_kmf_AliasKey + "=server3_4096.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    //3. TRC (The Remote Caller) truststore
    protected static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF_CLIENT)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=" + FILENAME_TRUSTSTORE_4CLIENT + "\n");
        sb.append(KEY_tmf_StorePwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    @Config(key = ID + ".ssl.overrideAuthority", predefinedValue = "server2.4096.jexpress.org",
            desc = "This value tells the channel's security layer what hostname or SNI (Server Name Indication)) to expect in the server's TLS certificate, regardless of the actual address you are connecting to. The certificate validation process will then proceed as usual against your trust store, but the final hostname check will use the value you provide instead of the connection address. Set server certificate DNS name here when server is not yet running on its certificate Subject Alternative Names (SAN)")
    protected volatile String overrideAuthority;

    @Config(key = ID + ".ssl.TrustStore.Default", defaultValue = "JDK", desc = "Only used when trust store is not specified, available options: JDK, InsecureTrustAll")
    protected volatile DefaultTrustStore defaultTrustStore = DefaultTrustStore.JDK;

    @JsonIgnore
    protected volatile NettyChannelBuilder channelBuilder;

    @ConfigHeader(title = "4. " + ID + " Channel Settings",
            desc = "The following settings are for NettyChannelBuilder, which is used to create a gRPC channel")
    @Config(key = ID + ".channel.userAgent", desc = "string: default null")
    protected volatile String userAgent = null;
    @Config(key = ID + ".channel.maxInboundMessageSize", desc = "int: default 4194304 if not set")
    protected volatile Integer maxInboundMessageSize = null;//4194304;
    @Config(key = ID + ".channel.maxHeaderListSize", desc = "int: default 8192 if not set")
    protected volatile Integer maxHeaderListSize = null;//8192;
    @Config(key = ID + ".channel.perRpcBufferLimit", desc = "long: default 1048576L if not set")
    protected volatile Long perRpcBufferLimit = null;//1048576L;
    @Config(key = ID + ".channel.maxHedgedAttempts", desc = "int: default 5 if not set")
    protected volatile Integer maxHedgedAttempts = null;//5;

    @Config(key = ID + ".channel.idleTimeoutSeconds", desc = "long: default 1800 (30 minutes) if not set")
    protected volatile Long idleTimeoutSeconds = null;//TimeUnit.MINUTES.toSeconds(30L);
    @Config(key = ID + ".channel.keepAliveWithoutCalls", desc = "boolean: default false if not set. keepAliveWithoutCalls is used when you are willing to spend client, server, and network resources to have lower latency for very infrequent RPCs")
    protected volatile Boolean keepAliveWithoutCalls = null;//false
    @Config(key = ID + ".channel.keepAliveTimeSeconds", desc = "long: default Long.MAX_VALUE (never) if not set. The interval in seconds between PING frames.")
    protected volatile Long keepAliveTimeSeconds = null;//Long.MAX_VALUE;
    @Config(key = ID + ".channel.keepAliveTimeoutSeconds", desc = "long: default 20 seconds if not set. The timeout in seconds for a PING frame to be acknowledged. If sender does not receive an acknowledgment within this time, it will close the connection.")
    protected volatile Long keepAliveTimeoutSeconds = null;//TimeUnit.SECONDS.toSeconds(20L);

    @Config(key = ID + ".channel.retryEnabled", desc = "boolean: default true if not set")
    protected volatile Boolean retryEnabled = null;// true
    @Config(key = ID + ".channel.maxRetryAttempts", desc = "int: default 5 if not set")
    protected volatile Integer maxRetryAttempts = null;//5;
    @Config(key = ID + ".channel.retryBufferSize", desc = "int: default 16777216L if not set")
    protected volatile Long retryBufferSize = null;//16777216L

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_KEYSTORE, FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_TRUSTSTORE_4CLIENT);
    }

    protected static int priority = 0;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        if (!isReal) {
            return;
        }
        NameResolverRegistry nameResolverRegistry = NameResolverRegistry.getDefaultRegistry();// Use singleton instance in new API to replace deprecated channelBuilder.nameResolverFactory(new nameResolverRegistry().asFactory());
        if (nameResolverProvider != null) {
            nameResolverRegistry.deregister(nameResolverProvider);
        }
        if (loadBalancingServers != null && !loadBalancingServers.isEmpty()) {
            nameResolverProvider = new BootLoadBalancerProvider(loadBalancingTargetScheme, ++priority, loadBalancingServers);
            nameResolverRegistry.register(nameResolverProvider);
        }
        if (tmf == null && defaultTrustStore == DefaultTrustStore.InsecureTrustAll) { // ignore Server Certificate
            tmf = io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE;
        }
        channelBuilder = initNettyChannelBuilder(nameResolverProvider, loadBalancingPolicy.getValue(), uri, kmf, tmf, overrideAuthority, ciphers, sslProvider, tlsProtocols);
        configNettyChannelBuilder(channelBuilder);
        for (GRPCClient listener : listeners) {
            listener.updateChannelBuilder(channelBuilder);
        }
    }

    protected void configNettyChannelBuilder(NettyChannelBuilder nettyChannelBuilder) {
        if (userAgent != null) {
            nettyChannelBuilder.userAgent(userAgent);
        }
        if (maxInboundMessageSize != null) {
            nettyChannelBuilder.maxInboundMessageSize(maxInboundMessageSize);
        }
        if (maxHeaderListSize != null) {
            nettyChannelBuilder.maxInboundMetadataSize(maxHeaderListSize);
        }
        if (perRpcBufferLimit != null) {
            nettyChannelBuilder.perRpcBufferLimit(perRpcBufferLimit);
        }
        if (maxHedgedAttempts != null) {
            nettyChannelBuilder.maxHedgedAttempts(maxHedgedAttempts);
        }

        // channel timeout
        if (idleTimeoutSeconds != null) {
            nettyChannelBuilder.idleTimeout(idleTimeoutSeconds, TimeUnit.SECONDS);
        }
        if (keepAliveWithoutCalls != null) {
            nettyChannelBuilder.keepAliveWithoutCalls(keepAliveWithoutCalls);
        }
        if (keepAliveTimeSeconds != null) {
            nettyChannelBuilder.keepAliveTime(keepAliveTimeSeconds, TimeUnit.SECONDS);
        }
        if (keepAliveTimeoutSeconds != null) {
            nettyChannelBuilder.keepAliveTimeout(keepAliveTimeoutSeconds, TimeUnit.SECONDS);
        }

        // channel retry
        if (retryEnabled != null) {
            if (retryEnabled) {
                nettyChannelBuilder.enableRetry();
                if (maxRetryAttempts != null) {
                    nettyChannelBuilder.maxRetryAttempts(maxRetryAttempts);
                }
                if (retryBufferSize != null) {
                    nettyChannelBuilder.retryBufferSize(retryBufferSize);
                }
            } else {
                nettyChannelBuilder.disableRetry();
            }
        }

        //nettyChannelBuilder.flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW);
        //nettyChannelBuilder.initialFlowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW);
    }

    @Override
    public void shutdown() {
    }

    private Set<GRPCClient> listeners = new HashSet<>();

    public void addConfigUpdateListener(GRPCClient listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
    }

    public void removeConfigUpdateListener(GRPCClient listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    /**
     * @param nameResolverProvider for client side load balancing
     * @param loadBalancingPolicy
     * @param uri                  The URI format should be one of grpc://host:port,
     *                             grpcs://host:port, or unix:///path/to/uds.sock
     * @param keyManagerFactory    The Remote Caller identity
     * @param trustManagerFactory  The Remote Caller trusted identities
     * @param overrideAuthority
     * @param ciphers
     * @param tlsVersionProtocols  "TLSv1.2", "TLSv1.3"
     * @return
     * @throws javax.net.ssl.SSLException
     */
    public static NettyChannelBuilder initNettyChannelBuilder(NameResolverProvider nameResolverProvider, LoadBalancingPolicy loadBalancingPolicy, URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
                                                              @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable String... tlsVersionProtocols) throws SSLException {
        return initNettyChannelBuilder(nameResolverProvider, loadBalancingPolicy.getValue(), uri, keyManagerFactory, trustManagerFactory, overrideAuthority, ciphers, SslProvider.OPENSSL, tlsVersionProtocols);
    }

    public static NettyChannelBuilder initNettyChannelBuilder(NameResolverProvider nameResolverProvider, String loadBalancingPolicy, URI uri, @Nullable KeyManagerFactory keyManagerFactory, @Nullable TrustManagerFactory trustManagerFactory,
                                                              @Nullable String overrideAuthority, @Nullable Iterable<String> ciphers, @Nullable SslProvider sslProvider, @Nullable String... tlsProtocols) throws SSLException {
        final NettyChannelBuilder channelBuilder;
        if (nameResolverProvider != null) {// use client side load balancing
            // register
            NameResolverRegistry nameResolverRegistry = NameResolverRegistry.getDefaultRegistry();// Use singleton instance in new API to replace deprecated channelBuilder.nameResolverFactory(new nameResolverRegistry().asFactory());
            nameResolverRegistry.register(nameResolverProvider);
            // init
            String target = nameResolverProvider.getDefaultScheme() + ":///"; // build target as URI
            channelBuilder = NettyChannelBuilder.forTarget(target)
                    .defaultLoadBalancingPolicy(loadBalancingPolicy);
        } else {
            switch (uri.getScheme()) {
                case "unix": //https://github.com/grpc/grpc-java/issues/1539
                    channelBuilder = NettyChannelBuilder.forAddress(new DomainSocketAddress(uri.getPath()))
                            .eventLoopGroup(new EpollEventLoopGroup())
                            .channelType(EpollDomainSocketChannel.class);
                    break;
                default:
                    String host = uri.getHost();
                    int port = uri.getPort();
                    if (host == null) {
                        throw new IllegalArgumentException("The URI format should contains host information, like <scheme>://[host:port]/[service], like grpc:///, grpc://host:port, grpcs://host:port, or unix:///path/to/uds.sock. gRpc.client.LoadBalancing.servers should be provided when host/port are not provided.");
                    }
                    channelBuilder = NettyChannelBuilder.forAddress(host, port);
                    break;
            }
        }
        if (tlsProtocols == null || tlsProtocols.length == 0 || tlsProtocols[0] == null || tlsProtocols[0].isEmpty()) {
            channelBuilder.usePlaintext();
        } else {
            final SslContextBuilder sslBuilder = GrpcSslContexts.forClient();
            // set keyManagerFactory
            sslBuilder.keyManager(keyManagerFactory);
            sslBuilder.trustManager(trustManagerFactory);
            if (overrideAuthority != null) {
                channelBuilder.overrideAuthority(overrideAuthority);
            }

            // set sslProvider
            GrpcSslContexts.configure(sslBuilder, sslProvider);
            if (tlsProtocols != null) {
                sslBuilder.protocols(tlsProtocols);
            }
            if (ciphers != null) {
                sslBuilder.ciphers(ciphers);
            }
            SslContext sslContext = sslBuilder.build();
            channelBuilder.sslContext(sslContext).useTransportSecurity();
        }
        return channelBuilder;
    }

    public List<InetSocketAddress> getLoadBalancingServers() {
        return loadBalancingServers;
    }

    public LoadBalancingPolicy getLoadBalancingPolicy() {
        return loadBalancingPolicy;
    }

    public NameResolverProvider getNameResolverProvider() {
        return nameResolverProvider;
    }

    public URI getUri() {
        return uri;
    }

    public String[] getTlsProtocols() {
        return tlsProtocols;
    }

    public List getCiphers() {
        return ciphers;
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public String getOverrideAuthority() {
        return overrideAuthority;
    }

    public DefaultTrustStore getDefaultTrustStore() {
        return defaultTrustStore;
    }

    public NettyChannelBuilder getChannelBuilder() {
        return channelBuilder;
    }

}
