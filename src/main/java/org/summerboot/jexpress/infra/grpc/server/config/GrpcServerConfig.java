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
package org.summerboot.jexpress.infra.grpc.server.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.grpc.ServerBuilder;
import org.summerboot.jexpress.annotation.config.Config;
import org.summerboot.jexpress.annotation.config.ConfigHeader;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.infra.grpc.server.GrpcServer;
import org.summerboot.jexpress.integration.HealthMonitor;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.net.GeoIpUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
//@ImportResource(BootConstant.FILE_CFG_GRPC)
public class GrpcServerConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(GrpcServerConfig.class);
        System.out.println(t);
    }

    protected static final String FILENAME_TRUSTSTORE_4SERVER = "truststore_grpc_server.p12";
    protected final static String ID = "gRpc.server";

    public static final GrpcServerConfig cfg = new GrpcServerConfig();

    protected GrpcServerConfig() {
    }

    @Override
    protected void reset() {
        tpeCore = BootConstants.CPU_CORE * 2 + 1;
        tpeMax = BootConstants.CPU_CORE * 2 + 1;
    }

    //1. gRPC server config
    @ConfigHeader(title = "1. " + ID + " Network Listeners",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8424, 127.0.0.1:8424, 0.0.0.0:8424")
    @Config(key = ID + ".bindings", predefinedValue = "0.0.0.0:8424, 0.0.0.0:8425", required = true)
    protected volatile List<InetSocketAddress> bindingAddresses;
    @Config(key = ID + ".autostart", defaultValue = "true")
    protected volatile boolean autoStart;
    @Config(key = ID + ".idle.threshold.second", defaultValue = "59", desc = "make it prime number when you have both NIO and gRPC server running")
    protected volatile int idleThresholdSecond;

    @Config(key = ID + ".CallerAddressFilter.option", defaultValue = "String", desc = "Valid values (select only one) = String, HostString, HostName, AddressString, HostAddress, AddrHostName, CanonicalHostName")
    protected volatile GeoIpUtil.CallerAddressFilterOption CallerAddressFilterOption = GeoIpUtil.CallerAddressFilterOption.String;
    @Config(key = ID + ".CallerAddressFilter.Whitelist", desc = "Whitelist in CSV format, example: 127.0.0.1, 192\\\\.168\\\\.1\\\\.")
    protected volatile Set<String> callerAddressFilterWhitelist;
    @Config(key = ID + ".CallerAddressFilter.Blacklist", desc = "Blacklist in CSV format, example: 10.1.1.40, 192\\\\.168\\\\.2\\\\.")
    protected volatile Set<String> callerAddressFilterBlacklist;

    @Config(key = ID + ".pool.BizExecutor.mode", defaultValue = "VirtualThread",
            desc = "valid value = VirtualThread (default for Java 21+), CPU, IO and Mixed (default for old Java)\n use CPU core + 1 when application is CPU bound\n"
                    + "use CPU core x 2 + 1 when application is I/O bound\n"
                    + "need to find the best value based on your performance test result when nio.server.BizExecutor.mode=Mixed")
    protected volatile ThreadingMode tpeThreadingMode = ThreadingMode.VirtualThread;

    @Config(key = ID + ".pool.coreSize", predefinedValue = "0",
            desc = "coreSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int tpeCore = BootConstants.CPU_CORE * 2 + 1;

    @Config(key = ID + ".pool.maxSize", predefinedValue = "0",
            desc = "maxSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int tpeMax = BootConstants.CPU_CORE * 2 + 1;

    @Config(key = ID + ".pool.queueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    protected volatile int tpeQueue = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds", defaultValue = "60")
    protected volatile long tpeKeepAliveSeconds = 60;

    @Config(key = ID + ".pool.prestartAllCoreThreads", defaultValue = "false")
    protected boolean prestartAllCoreThreads = false;

    @Config(key = ID + ".pool.allowCoreThreadTimeOut", defaultValue = "false")
    protected boolean allowCoreThreadTimeOut = false;

    protected ThreadPoolExecutor tpe = null;

    //2. TRC (The Remote Callee) keystore
    protected static final String KEY_kmf_key = ID + ".ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = ID + ".ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = ID + ".ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = ID + ".ssl.KeyPwd";

    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = "Path to key store file. Use SSL/TLS when keystore is provided, otherwise use plain socket",
            callbackMethodName4Dump = "generateTemplate_keystore")
    //@JsonIgnore
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb, Properties currentValues) {
        appendCurrentValue(KEY_kmf_key, currentValues, FILENAME_KEYSTORE, sb);
        appendCurrentValue(KEY_kmf_StorePwdKey, currentValues, DEFAULT_DEC_VALUE, sb);
        appendCurrentValue(KEY_kmf_AliasKey, currentValues, "server2_4096.jexpress.org", sb);
        appendCurrentValue(KEY_kmf_AliasPwdKey, currentValues, DEFAULT_DEC_VALUE, sb);
        generateTemplate = true;
    }

    //3. TRC (The Remote Callee) truststore    
    protected static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF_SERVER)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    // 4. NettyChannelBuilder settings
    @ConfigHeader(title = "4. " + ID + " ServerBuilder Settings",
            desc = "The following settings are for ServerBuilder, which is used to config a gRPC channel")
    // Enforcement Policy:
    @Config(key = ID + ".channel.permitKeepAliveTimeSeconds", desc = "Enforcement Policy: default 300 (5 minutes) if not set. The minimum allowed interval for a client to send keepalive PINGs. If a client sets its keepAliveTime to less than 5 minutes, the server will flag it as a Ping Flood (attack) and forcefully drop the connection by sending a GOAWAY frame with the error code ENHANCE_YOUR_CALM")
    protected volatile Long permitKeepAliveTimeSeconds = null;// 300L;
    @Config(key = ID + ".channel.permitKeepAliveWithoutCalls", desc = "Enforcement Policy: default (not allowed) false if not set. Determines whether a client is allowed to send keepalive PINGs when there are no active RPC requests on the TCP connection. Since it defaults to false, if a client sends keepalive heartbeats during a completely idle period, the server will penalize it by tearing down the connection.")
    protected volatile Boolean permitKeepAliveWithoutCalls = null;// false;

    // Server Keepalive:
    @Config(key = ID + ".channel.keepAliveTimeSeconds", desc = "Server Keepalive: default 7200 (2 hours) if not set. The idle duration after which the server will actively send an HTTP/2 PING frame to the client if no traffic has been read from the connection for a full 2 hours. This is used to verify whether the client is still alive")
    protected volatile Long keepAliveTimeSeconds = null;// 7200L;
    @Config(key = ID + ".channel.keepAliveTimeoutSeconds", desc = "Server Keepalive: default 20 if not set. The maximum time the server waits for a client to reply with an ACK after sending a heartbeat PING. If no response is received within 20 seconds, the server assumes the client is unreachable due to network failure and terminates the TCP connection.")
    protected volatile Long keepAliveTimeoutSeconds = null;// 20L;

    // Connection Management:
    @Config(key = ID + ".channel.handshakeTimeoutSeconds", desc = "Connection Management: default 120 (2 minutes) if not set. The maximum allowed duration for TLS handshakes and HTTP/2 protocol negotiation when a new TCP connection is established. If the handshake blocks or lags beyond 2 minutes, the server will forcefully terminate the new connection.")
    protected volatile Long handshakeTimeoutSeconds = null;// 120L;
    @Config(key = ID + ".channel.maxConnectionIdleSeconds", desc = "Connection Management: default Long.MAX_VALUE nanoseconds (292 years) if not set. The maximum duration a connection is allowed to remain alive after the number of active RPCs drops to 0 (when the connection becomes idle). An infinite default means the server will never proactively kill a connection just because it is idle.")
    protected volatile Long maxConnectionIdleSeconds = null;// Long.MAX_VALUE;
    @Config(key = ID + ".channel.maxConnectionAgeSeconds", desc = "Connection Management: default Long.MAX_VALUE nanoseconds (292 years) if not set. The absolute maximum lifespan (age) allowed for a TCP connection. If configured (e.g., to 30 minutes), the server will gracefully close the connection via GOAWAY once that time expires—regardless of how much business traffic is flowing—forcing the client to reconnect and trigger fresh load balancing at the F5 level.")
    protected volatile Long maxConnectionAgeSeconds = null;// Long.MAX_VALUE;
    @Config(key = ID + ".channel.maxConnectionAgeGraceSeconds", desc = "Connection Management: default Long.MAX_VALUE nanoseconds (292 years) if not set. Works in tandem with maxConnectionAge. When a connection reaches its age limit and the server sends a GOAWAY, this parameter defines the grace period during which remaining active RPCs are allowed to finish. Once this buffer time expires, any uncompleted requests are forcefully terminated.")
    protected volatile Long maxConnectionAgeGraceSeconds = null;// Long.MAX_VALUE;

    // Size Protection:
    @Config(key = ID + ".channel.maxInboundMessageBytes", desc = "Size Protection: default 4,194,304 Bytes (4MB) if not set. The maximum payload size of a single gRPC message that the server is permitted to receive. This acts as a safeguard to prevent malicious or accidental oversized payloads from causing Out-Of-Memory (OOM) errors in the server's JVM.")
    protected volatile Integer maxInboundMessageBytes = null;// 4,194,304;
    @Config(key = ID + ".channel.maxInboundMetadataBytes", desc = "Size Protection: default 8,192 Bytes (8KB) if not set. The maximum cumulative size allowed for HTTP/2 headers (gRPC Metadata) (corresponds to the HTTP/2 SETTINGS_MAX_HEADER_LIST_SIZE restriction). If a client attaches massive authorization tokens or highly complex metadata exceeding 8 KiB, the server will reject the request.")
    protected volatile Integer maxInboundMetadataBytes = null;// 8,192;

    @ConfigHeader(title = "5. " + ID + " health check policy")
    @Config(key = ID + ".defaultRequiredHealthChecks", format = "Leave blank or comment out | REQUIRE_ALL | @HealthCheck.name1, @HealthCheck.name2, ..., @HealthCheck.nameN",
            desc = "This configuration can be overridden at code level by gRPC BindableService methods with @RequiresHealthCheck annotation: \nLeave blank or comment out (default, and accept request even if any @HealthCheck fails), \nor REQUIRE_ALL (if any class annotated with @HealthCheck fails the check, the request is rejected and a 9 FAILED_PRECONDITION error is returned), \nor a list of @HealthCheck.name in CSV format (if any listed @HealthCheck fails the check, the request is rejected and a 9 FAILED_PRECONDITION error is returned)")
    protected volatile String[] defaultRequiredHealthChecks;

    protected volatile HealthMonitor.EmptyHealthCheckPolicy emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_NONE;

    protected void generateTemplate_truststore(StringBuilder sb, Properties currentValues) {
        appendCurrentValue(KEY_tmf_key, currentValues, FILENAME_TRUSTSTORE_4SERVER, sb, true);
        appendCurrentValue(KEY_tmf_StorePwdKey, currentValues, DEFAULT_DEC_VALUE, sb, true);
        generateTemplate = true;
    }

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_TRUSTSTORE_4SERVER);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        // pre-compile regexes for whitelist and blacklist
        if (callerAddressFilterWhitelist != null) {
            for (String regex : callerAddressFilterWhitelist) {
                SecurityUtil.matches("", regex);
            }
        }
        if (callerAddressFilterBlacklist != null) {
            for (String regex : callerAddressFilterBlacklist) {
                SecurityUtil.matches("", regex);
            }
        }

        tpe = buildThreadPoolExecutor(tpe, "Netty-gRPC.Biz", tpeThreadingMode,
                tpeCore, tpeMax, tpeQueue, tpeKeepAliveSeconds, null,
                prestartAllCoreThreads, allowCoreThreadTimeOut, true);

        if (defaultRequiredHealthChecks == null) {
            emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_NONE;
        } else {
            if (defaultRequiredHealthChecks.length == 1) {
                String requiredHealthCheck = defaultRequiredHealthChecks[0];
                /*if (requiredHealthCheck.equalsIgnoreCase(HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_NONE.name())) {
                    emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_NONE;
                    defaultRequiredHealthChecks = null;
                } else */
                if (requiredHealthCheck.equalsIgnoreCase(HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_ALL.name())) {
                    emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_ALL;
                    defaultRequiredHealthChecks = null;
                }
            }
        }
    }

    public void configServerBuilder(GrpcServer grpcServer) {
        configServerBuilder(grpcServer.getServerBuilder());
    }

    public void configServerBuilder(ServerBuilder serverBuilder) {
        if (permitKeepAliveTimeSeconds != null) {
            // 300 秒 (5分钟): 允许客户端发送心跳 PING 的最快频率（最小间隔时间）。如果客户端设置的 keepAliveTime 小于 5 分钟，服务端会判定为 Ping Flood（洪水攻击）并强制切断连接，返回 GOAWAY (错误码：ENHANCE_YOUR_CALM)。
            serverBuilder.permitKeepAliveTime(permitKeepAliveTimeSeconds, TimeUnit.SECONDS);
        }
        if (permitKeepAliveWithoutCalls != null) {
            // false (不允许): 当 TCP 连接上没有活动的业务 RPC 请求时，是否允许客户端发送保活 PING。由于默认是 false，如果客户端在完全空闲时开启了保活心跳，也会被服务端惩罚断开
            serverBuilder.permitKeepAliveWithoutCalls(permitKeepAliveWithoutCalls);
        }
        if (keepAliveTimeSeconds != null) {
            //  7200 秒 (2小时): 服务端在连接完全没有读取到任何流量长达 2 小时后，会主动向客户端发送一个 HTTP/2 PING 报文，用来确认客户端是否还活着
            serverBuilder.keepAliveTime(keepAliveTimeSeconds, TimeUnit.SECONDS);
        }
        if (keepAliveTimeoutSeconds != null) {
            //  20 秒: 服务端主动发出去 PING 报文后，等待客户端回复 ACK 的最大时间。如果 20 秒内没有收到回应，服务端会认为由于网络故障客户端已失联，直接关闭 TCP 连接
            serverBuilder.keepAliveTimeout(keepAliveTimeoutSeconds, TimeUnit.SECONDS);
        }
        if (handshakeTimeoutSeconds != null) {
            // 120 秒 (2分钟): 新的 TCP 连接建立后，允许进行 TLS 握手及 HTTP/2 协议协商（Negotiation）的最长时间。如果由于网络慢或者握手阻塞超过 2 分钟，服务端会强行杀掉该新连接
            serverBuilder.handshakeTimeout(handshakeTimeoutSeconds, TimeUnit.SECONDS);
        }
        if (maxConnectionIdleSeconds != null) {
            // Long.MAX_VALUE 纳秒: 当连接上进行中的 RPC 数量降为 0（连接变为空闲）时，允许该连接最多存活多久。默认无限大意味着服务端不会因为空闲而主动掐断连接
            serverBuilder.maxConnectionIdle(maxConnectionIdleSeconds, TimeUnit.SECONDS);
        }
        if (maxConnectionAgeSeconds != null) {
            // Long.MAX_VALUE 纳秒: 允许一个 TCP 连接存活的绝对最大寿命（绝对年龄）。如果设置了它（例如 30 分钟），那么无论连接有多频繁的业务流量，到时间服务端也会发 GOAWAY 优雅断链，以此迫使客户端去 F5 重新做负载均衡
            serverBuilder.maxConnectionAge(maxConnectionAgeSeconds, TimeUnit.SECONDS);
        }
        if (maxConnectionAgeGraceSeconds != null) {
            // Long.MAX_VALUE 纳秒: 配合 maxConnectionAge 使用。当连接生命周期到期，服务端发送 GOAWAY 后，允许正在运行中的残留 RPC 请求继续执行的“宽限缓冲时间”。缓冲期过后，任何未完结的请求都会被强行掐断
            serverBuilder.maxConnectionAgeGrace(maxConnectionAgeGraceSeconds, TimeUnit.SECONDS);
        }
        if (maxInboundMessageBytes != null) {
            // 4,194,304 字节 (4 MiB): 服务端允许接收的单个 gRPC 消息体的最大长度。用于防止恶意超大报文撑爆服务端 JVM 内存（OOM）`
            serverBuilder.maxInboundMessageSize(maxInboundMessageBytes);
        }
        if (maxInboundMetadataBytes != null) {
            // 8,192 字节 (8 KiB): 服务端允许接收的 HTTP/2 头部（Headers / gRPC Metadata）的累计最大大小（对应 HTTP/2 的 SETTINGS_MAX_HEADER_LIST_SIZE 限制）。如果客户端带了极其庞大的权限 Token 或复杂元数据，超过 8 KiB 会被拒绝
            serverBuilder.maxInboundMetadataSize(maxInboundMetadataBytes);
        }
    }

    @Override
    public void shutdown() {
        if (tpe != null && !tpe.isShutdown()) {
            tpe.shutdown();
        }
    }

    public List<InetSocketAddress> getBindingAddresses() {
        return bindingAddresses;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public int getIdleThresholdSecond() {
        return idleThresholdSecond;
    }

    public GeoIpUtil.CallerAddressFilterOption getCallerAddressFilterOption() {
        return CallerAddressFilterOption;
    }

    public Set<String> getCallerAddressFilterWhitelist() {
        return callerAddressFilterWhitelist;
    }

    public Set<String> getCallerAddressFilterBlacklist() {
        return callerAddressFilterBlacklist;
    }

    public ThreadingMode getTpeThreadingMode() {
        return tpeThreadingMode;
    }

    public int getTpeCore() {
        return tpeCore;
    }

    public int getTpeMax() {
        return tpeMax;
    }

    public int getTpeQueue() {
        return tpeQueue;
    }

    public long getTpeKeepAliveSeconds() {
        return tpeKeepAliveSeconds;
    }

    public ThreadPoolExecutor getTpe() {
        return tpe;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public boolean isPrestartAllCoreThreads() {
        return prestartAllCoreThreads;
    }

    public boolean isAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public String[] getDefaultRequiredHealthChecks() {
        return defaultRequiredHealthChecks;
    }

    public HealthMonitor.EmptyHealthCheckPolicy getEmptyHealthCheckPolicy() {
        return emptyHealthCheckPolicy;
    }
}
