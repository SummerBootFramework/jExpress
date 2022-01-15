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
package org.summerframework.nio.server;

import org.summerframework.boot.config.ConfigUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.netty.handler.ssl.SslProvider;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;
import io.netty.channel.ChannelHandler;
import org.summerframework.util.JsonUtil;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class NioConfig extends AbstractSummerBootConfig {

    public static final NioConfig CFG = new NioConfig();

    public static void main(String[] args) {
        String t = generateTemplate(NioConfig.class);
        System.out.println(t);
    }

    @Override
    public void shutdown() {
        String tn = Thread.currentThread().getName();
        NioServer.shutdown();
        if (tpe != null && !tpe.isShutdown()) {
            System.out.println(tn + ": shutdown tpe");
            tpe.shutdown();
        }
    }

    //1. NIO Network Listeners
    @Memo(title = "1. NIO Network Listeners",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8988, 127.0.0.1:8988, 0.0.0.0:8989")

    @Config(key = "nio.server.bindings"/*, defaultValue = "0.0.0.0:80"*/)
    private volatile Map<String, Integer> bindingAddresses = null;//Map.of("0.0.0.0", 80);

    //2. NIO Security
    @Memo(title = "2. NIO Security")

    @JsonIgnore
    @Config(key = "nio.server.ssl.KeyStore", StorePwdKey = "nio.server.ssl.KeyStorePwd",
            AliasKey = "nio.server.ssl.KeyAlias", AliasPwdKey = "nio.server.ssl.KeyPwd",
            required = false,
            desc = "Use SSL/TLS when key store is provided, use plain Socket if key stroe is not available")
    private volatile KeyManagerFactory kmf = null;

    @JsonIgnore
    @Config(key = "nio.server.ssl.TrustStore", StorePwdKey = "nio.server.ssl.TrustStorePwd",
            required = false,
            desc = "trust all clients when truststore is not provided")
    private volatile TrustManagerFactory tmf = null;

    @Config(key = "nio.server.ssl.VerifyCertificateHost", defaultValue = "false")
    private volatile boolean verifyCertificateHost = false;

    @Config(key = "nio.server.ssl.Provider", defaultValue = "OPENSSL")
    private volatile SslProvider sslProvider = SslProvider.OPENSSL;

    @Config(key = "nio.server.ssl.Protocols", defaultValue = "TLSv1.2, TLSv1.3")
    private String[] sslProtocols = {"TLSv1.2", "TLSv1.3"};

    @Config(key = "nio.server.ssl.CipherSuites", required = false,
            desc = "use system default ciphersuites when not specified")
    private String[] sslCipherSuites = null;

    //3.1 Socket controller
    @Memo(title = "3.1 Socket controller")

    @Config(key = "nio.server.socket.SO_REUSEADDR", defaultValue = "true")
    private volatile boolean soReuseAddr = true;

    @Config(key = "nio.server.socket.SO_KEEPALIVE", defaultValue = "true")
    private volatile boolean soKeepAlive = true;

    @Config(key = "nio.server.socket.TCP_NODELAY", defaultValue = "true")
    private volatile boolean soTcpNodelay = true;

    @Config(key = "nio.server.socket.SO_LINGER", defaultValue = "-1")
    private volatile int soLinger = -1;

    //3.2 Socket Performance
    @Memo(title = "3.2 Socket Performance")

    @Config(key = "nio.server.ssl.HandshakeTimeout.second", defaultValue = "30")
    private volatile int sslHandshakeTimeout = 30;

    @Config(key = "nio.server.socket.CONNECT_TIMEOUT.second", defaultValue = "30")
    private volatile int soConnectionTimeout = 30;

    @Config(key = "nio.server.socket.SO_BACKLOG", defaultValue = "1024")
    private volatile int soBacklog = 1024;

    @Config(key = "nio.server.socket.SO_RCVBUF", defaultValue = "1048576",
            desc = " - cat /proc/sys/net/ipv4/tcp_rmem (max 1024k)")
    private volatile int soRcvBuf = 1048576;

    @Config(key = "nio.server.socket.SO_SNDBUF", defaultValue = "1048576",
            desc = " - cat /proc/sys/net/ipv4/tcp_smem (max 1024k)")
    private volatile int soSndBuf = 1048576;
    @Config(key = "nio.server.HttpObjectAggregator.maxContentLength", defaultValue = "65536",
            desc = "default - 64kb")
    private volatile int httpObjectAggregatorMaxContentLength = 65536;

    //4.1 Netty controller
    @Memo(title = "4.1 Netty controller")

    @Config(key = "nio.server.multiplexer", defaultValue = "AVAILABLE")
    private volatile IoMultiplexer multiplexer = IoMultiplexer.AVAILABLE;

    @Config(key = "nio.server.httpServerCodec.MaxInitialLineLength", defaultValue = "4096")
    private volatile int httpServerCodec_MaxInitialLineLength = 4096;

    @Config(key = "nio.server.httpServerCodec.MaxHeaderSize", defaultValue = "4096")
    private volatile int httpServerCodec_MaxHeaderSize = 8192;

    @Config(key = "nio.server.httpServerCodec.MaxChunkSize", defaultValue = "4096")
    private volatile int httpServerCodec_MaxChunkSize = 8192;

    //4.2 Netty Performance - NIO and Biz Exector Pool
    @Memo(title = "4.2 Netty Performance - NIO and Biz Exector Pool")
    private ThreadPoolExecutor tpe = null;

    @Config(key = "nio.server.EventLoopGroup.AcceptorSize", required = false,
            desc = "default AcceptorSize = number of bindings")
    private volatile int nioEventLoopGroupAcceptorSize = 0;

    @Config(key = "nio.server.EventLoopGroup.WorkerSize", required = false,
            desc = "default WorkerSize = CPU core x2 +1")
    private volatile int nioEventLoopGroupWorkerSize = 0;
    //private volatile int nioEventLoopGroupExecutorSize;

    public enum ThreadingMode {
        CPU, IO, Mixed
    }
    @Config(key = "nio.server.BizExecutor.mode", defaultValue = "IO",
            desc = "valid value = CPU, IO (default), Mixed")
    private volatile ThreadingMode bizExecutorThreadingMode = ThreadingMode.IO;

    @Config(key = "nio.server.BizExecutor.CoreSize", defaultValue = "0",
            desc = "use CPU core + 1 when application is CPU bound\n"
            + "use CPU core x 2 + 1 when application is I/O bound\n"
            + "manual config is required when it is mixed")
    private volatile int bizExecutorCoreSize = 0;// how many tasks running at the same time
    private volatile int currentCore;

    @Config(key = "nio.server.BizExecutor.MaxSize", defaultValue = "0")
    private volatile int bizExecutorMaxSize = 0;// how many tasks running at the same time
    private volatile int currentMax;

    @Config(key = "nio.server.BizExecutor.QueueSize", defaultValue = "2147483647")
    private volatile int bizExecutorQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full
    private volatile int currentQueue;

    @Config(key = "nio.server.BizExecutor.bizTimeoutWarnThreshold", defaultValue = "5000")
    private volatile int bizTimeoutWarnThreshold = 5000;

    //4.3 Netty Channel Handler
    @Memo(title = "4.3 Netty Channel Handler")
    @Config(key = "nio.server.RreaderIdleTime", required = false,
            desc = "rec Idle enabled only when value > 0")
    private volatile int readerIdleTime = 0;

    @Config(key = "nio.server.WriterIdleTime", required = false,
            desc = "Sent Idle enabled only when value > 0")
    private volatile int writerIdleTime = 0;

    @Config(key = "nio.server.health.InspectionIntervalSeconds", defaultValue = "5")
    private volatile int healthInspectionIntervalSeconds = 5;

    private static Injector INJECTOR;

    @Config(key = "nio.HttpService.enabled", defaultValue = "true")
    private volatile boolean httpService = true;
    @Config(key = "nio.JAX-RS.fromJson.failOnUnknownProperties", defaultValue = "true")
    private volatile boolean fromJsonFailOnUnknownProperties = true;
    @Config(key = "nio.JAX-RS.toJson.IgnoreNull", defaultValue = "true")
    private volatile boolean toJsonIgnoreNull = true;
    @Config(key = "nio.JAX-RS.toJson.Pretty", defaultValue = "false")
    private volatile boolean toJsonPretty = false;

//    @Config(key = "nio.useDefaultHTTPHandler", defaultValue = "true")
//    private volatile boolean useDefaultHTTPHandler = true;
    @Config(key = "nio.HttpFileUploadHandler", required = false)
    private volatile String fielUploadHandlerAnnotatedName = null;

    @Config(key = "nio.HttpPingHandler", defaultValue = "org.summerframework.nio.server.BootHttpPingHandler")
    private volatile String pingHandlerAnnotatedName = BootHttpPingHandler.class.getName();

    @Config(key = "nio.HttpRequestHandler", defaultValue = "org.summerframework.nio.server.BootHttpRequestHandler")
    private volatile String requestHandlerAnnotatedName = BootHttpRequestHandler.class.getName();

    //5. IO Communication logging filter
    @Memo(title = "5. IO Communication logging filter")
    @Config(key = "nio.verbose.filter.usertype", defaultValue = "ignore",
            desc = "5.1 caller filter\n"
            + "valid value = id, uid, group, role, ignore")
    private volatile VerboseTargetUserType filterUserType = VerboseTargetUserType.ignore;

    public enum VerboseTargetUserType {
        id, uid, group, role, ignore
    }
    @Config(key = "nio.verbose.filter.usertype.range", required = false,
            desc = "user range (when type=CallerId): N1 - N2 or N1, N2, ... , Nn \n"
            + "user range (when type=CallerName): johndoe, janedoe")
    private volatile String filterUserVaue;
    private volatile Set<String> filterCallerNameSet;
    private volatile Set<Long> filterCallerIdSet;
    private volatile long filterCallerIdFrom;
    private volatile long filterCallerIdTo;

    //5.2 error code filter
    @Config(key = "nio.verbose.filter.codetype", defaultValue = "all",
            desc = "valid value = HttpStatusCode, AppErrorCode, all, ignore")
    private volatile VerboseTargetCodeType filterCodeType = VerboseTargetCodeType.all;
    @Config(key = "nio.verbose.filter.codetype.range", required = false,
            desc = "5.2 error code filter\n"
            + "code range: N1 - N2 or N1, N2, ... , Nn")
    private volatile String filterCodeVaue;

    public enum VerboseTargetCodeType {
        HttpStatusCode, AppErrorCode, all, ignore
    }
    private volatile Set<Long> filterCodeSet;
    private volatile long filterCodeRangeFrom;
    private volatile long filterCodeRangeTo;
    //5.3 verbose aspect
    @Config(key = "nio.verbose.aspect.ReqHeader", defaultValue = "true")
    private volatile boolean verboseReqHeader = true;
    @Config(key = "nio.verbose.aspect.ReqContent", defaultValue = "true")
    private volatile boolean verboseReqContent = true;
    @Config(key = "nio.verbose.aspect.RespHeader", defaultValue = "true")
    private volatile boolean verboseRespHeader = true;
    @Config(key = "nio.verbose.aspect.RespContent", defaultValue = "true")
    private volatile boolean verboseRespContent = true;

    //6. POI filter
    @Memo(title = "6. POI logging filter")
    @Config(key = "nio.verbose.ServiceTimePOI.type", defaultValue = "all",
            desc = "valid value = filter, all, ignore")
    private volatile VerboseTargetPOIType filterPOIType = VerboseTargetPOIType.all;

    public enum VerboseTargetPOIType {
        filter, all, ignore
    }
    @Config(key = "nio.verbose.ServiceTimePOI.filter", defaultValue = "auth.begin, auth.end, db.begin, db.end",
            desc = "CSV format")
    private volatile Set<String> filterPOISet;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        //4.2 Netty Performance
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (nioEventLoopGroupAcceptorSize < 1) {
            nioEventLoopGroupAcceptorSize = bindingAddresses.size();
        }
        if (nioEventLoopGroupWorkerSize < 1) {
            nioEventLoopGroupWorkerSize = availableProcessors * 2 + 1;
        }
        switch (bizExecutorThreadingMode) {
            case CPU:// use CPU core + 1 when application is CPU bound
                bizExecutorCoreSize = availableProcessors + 1;
                bizExecutorMaxSize = availableProcessors + 1;
                break;
            case IO:// use CPU core x 2 + 1 when application is I/O bound
                bizExecutorCoreSize = availableProcessors * 2 + 1;
                bizExecutorMaxSize = availableProcessors * 2 + 1;
                break;
            case Mixed:// manual config is required when it is mixed
                if (bizExecutorCoreSize < 1) {
                    bizExecutorCoreSize = availableProcessors * 2 + 1;
                }
                if (bizExecutorMaxSize < 1) {
                    bizExecutorMaxSize = availableProcessors * 2 + 1;
                }
                if (bizExecutorMaxSize < bizExecutorCoreSize) {
                    helper.addError("BizExecutor.MaxSize should not less than BizExecutor.CoreSize");
                }
                break;
        }
        if (currentCore != bizExecutorCoreSize || currentMax != bizExecutorMaxSize || currentQueue != bizExecutorQueueSize) {
            //update current
            currentCore = bizExecutorCoreSize;
            currentMax = bizExecutorMaxSize;
            currentQueue = bizExecutorQueueSize;
            // backup old
            ThreadPoolExecutor old = tpe;
            tpe = new ThreadPoolExecutor(currentCore, currentMax, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(currentQueue),
                    Executors.defaultThreadFactory(), new AbortPolicyWithReport("NIOBizThreadPoolExecutor"));//.DiscardOldestPolicy()
            // then shotdown old tpe
            if (old != null) {
                old.shutdown();
            }
        }

        //4.3 Netty Channel Handler
//        if (useDefaultHTTPHandler && StringUtils.isBlank(requestHandlerAnnotatedName)) {
//            requestHandlerAnnotatedName = BootHttpRequestHandler.class.getName();
//        }
//        if (useDefaultHTTPHandler && StringUtils.isBlank(pingHandlerAnnotatedName)) {
//            pingHandlerAnnotatedName = BootHttpPingHandler.class.getName();
//        }
        if (INJECTOR != null) {
            if (fielUploadHandlerAnnotatedName != null) {
                try {
                    INJECTOR.getInstance(
                            Key.get(ChannelHandler.class, Names.named(fielUploadHandlerAnnotatedName)));
                } catch (Throwable ex) {
                    helper.addError("invalid HttpFileUpload Channel Handler name(" + fielUploadHandlerAnnotatedName + "): " + ex.toString());
                }
            }
            if (pingHandlerAnnotatedName != null) {
                try {
                    INJECTOR.getInstance(
                            Key.get(ChannelHandler.class, Names.named(pingHandlerAnnotatedName)));
                } catch (Throwable ex) {
                    helper.addError("invalid Ping Channel Handler name(" + pingHandlerAnnotatedName + "): " + ex.toString());
                }
            }
            if (requestHandlerAnnotatedName != null) {
                try {
                    INJECTOR.getInstance(
                            Key.get(ChannelHandler.class, Names.named(requestHandlerAnnotatedName)));
                } catch (Throwable ex) {
                    helper.addError("invalid Request Channel Handler name(" + requestHandlerAnnotatedName + "): " + ex.toString());
                }
            }
        }
        JsonUtil.configure(fromJsonFailOnUnknownProperties, toJsonIgnoreNull, toJsonPretty);

        //5.1 caller filter
        String key;
        switch (filterUserType) {
            case id:
                key = "nio.verbose.filter.usertype.range";
                filterCallerIdSet = new HashSet();
                Long[] a = helper.getAsRangeLong(props, key, filterCallerIdSet);
                if (a != null) {
                    filterCallerIdFrom = a[0];
                    filterCallerIdTo = a[1];
                    filterCallerIdSet = null;
                }
                break;
            case uid:
            case group:
            case role:
                key = "nio.verbose.filter.usertype.range";
                String[] na = helper.getAsCSV(props, key, null);
                filterCallerNameSet = new HashSet();
                filterCallerNameSet.addAll(Arrays.asList(na));
                break;
        }

        //5.2 error code filter
        switch (filterCodeType) {
            case HttpStatusCode:
            case AppErrorCode:
                key = "nio.verbose.filter.codetype.range";
                filterCodeSet = new HashSet();
                Long[] a = helper.getAsRangeLong(props, key, filterCodeSet);
                if (a != null) {
                    filterCodeRangeFrom = a[0];
                    filterCodeRangeTo = a[1];
                    filterCodeSet = null;
                }
                break;
        }
    }

    ThreadPoolExecutor getBizExecutor() {
        return tpe;
    }

    private final static BootHttpFileUploadHandler FileUploadRejector = new BootHttpFileUploadRejector();

    public static void setGuiceInjector(Injector _injector) {
        INJECTOR = _injector;
    }

    @JsonIgnore
    public ChannelHandler getHttpFileUploadHandler() {
        if (fielUploadHandlerAnnotatedName == null) {
            return FileUploadRejector;
        }
        return INJECTOR.getInstance(Key.get(ChannelHandler.class, Names.named(fielUploadHandlerAnnotatedName)));
    }

    @JsonIgnore
    public ChannelHandler getPingHandler() {
        if (pingHandlerAnnotatedName == null) {
            return null;
        }
        return INJECTOR.getInstance(Key.get(ChannelHandler.class, Names.named(pingHandlerAnnotatedName)));
    }

    @JsonIgnore
    public ChannelHandler getRequestHandler() {
        if (requestHandlerAnnotatedName == null) {
            return null;
        }
        return INJECTOR.getInstance(Key.get(ChannelHandler.class, Names.named(requestHandlerAnnotatedName)));
    }

    public Map<String, Integer> getBindingAddresses() {
        return bindingAddresses;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public boolean isVerifyCertificateHost() {
        return verifyCertificateHost;
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    public String[] getSslProtocols() {
        return sslProtocols;
    }

    public String[] getSslCipherSuites() {
        return sslCipherSuites;
    }

    public boolean isSoReuseAddr() {
        return soReuseAddr;
    }

    public boolean isSoKeepAlive() {
        return soKeepAlive;
    }

    public boolean isSoTcpNodelay() {
        return soTcpNodelay;
    }

    public int getSoLinger() {
        return soLinger;
    }

    public int getSslHandshakeTimeout() {
        return sslHandshakeTimeout;
    }

    public int getSoConnectionTimeout() {
        return soConnectionTimeout;
    }

    public int getSoBacklog() {
        return soBacklog;
    }

    public int getSoRcvBuf() {
        return soRcvBuf;
    }

    public int getSoSndBuf() {
        return soSndBuf;
    }

    public int getHttpObjectAggregatorMaxContentLength() {
        return httpObjectAggregatorMaxContentLength;
    }

    public IoMultiplexer getMultiplexer() {
        return multiplexer;
    }

    public int getHttpServerCodec_MaxInitialLineLength() {
        return httpServerCodec_MaxInitialLineLength;
    }

    public int getHttpServerCodec_MaxHeaderSize() {
        return httpServerCodec_MaxHeaderSize;
    }

    public int getHttpServerCodec_MaxChunkSize() {
        return httpServerCodec_MaxChunkSize;
    }

    public int getNioEventLoopGroupAcceptorSize() {
        return nioEventLoopGroupAcceptorSize;
    }

    public int getNioEventLoopGroupWorkerSize() {
        return nioEventLoopGroupWorkerSize;
    }

    public ThreadingMode getBizExecutorThreadingMode() {
        return bizExecutorThreadingMode;
    }

    public int getBizExecutorCoreSize() {
        return bizExecutorCoreSize;
    }

    public int getBizExecutorMaxSize() {
        return bizExecutorMaxSize;
    }

    public int getBizExecutorQueueSize() {
        return bizExecutorQueueSize;
    }

    public int getBizTimeoutWarnThreshold() {
        return bizTimeoutWarnThreshold;
    }

    public int getReaderIdleTime() {
        return readerIdleTime;
    }

    public int getWriterIdleTime() {
        return writerIdleTime;
    }

    public int getHealthInspectionIntervalSeconds() {
        return healthInspectionIntervalSeconds;
    }

    public boolean isHttpService() {
        return httpService;
    }

    public boolean isFromJsonFailOnUnknownProperties() {
        return fromJsonFailOnUnknownProperties;
    }

    public boolean isToJsonIgnoreNull() {
        return toJsonIgnoreNull;
    }

    public boolean isToJsonPretty() {
        return toJsonPretty;
    }

//    public boolean isUseDefaultHTTPHandler() {
//        return useDefaultHTTPHandler;
//    }
    public String getFielUploadHandlerAnnotatedName() {
        return fielUploadHandlerAnnotatedName;
    }

    public String getPingHandlerAnnotatedName() {
        return pingHandlerAnnotatedName;
    }

    public String getRequestHandlerAnnotatedName() {
        return requestHandlerAnnotatedName;
    }

    public VerboseTargetUserType getFilterUserType() {
        return filterUserType;
    }

    public Set<String> getFilterCallerNameSet() {
        return filterCallerNameSet;
    }

    public Set<Long> getFilterCallerIdSet() {
        return filterCallerIdSet;
    }

    public long getFilterCallerIdFrom() {
        return filterCallerIdFrom;
    }

    public long getFilterCallerIdTo() {
        return filterCallerIdTo;
    }

    public VerboseTargetCodeType getFilterCodeType() {
        return filterCodeType;
    }

    public Set<Long> getFilterCodeSet() {
        return filterCodeSet;
    }

    public long getFilterCodeRangeFrom() {
        return filterCodeRangeFrom;
    }

    public long getFilterCodeRangeTo() {
        return filterCodeRangeTo;
    }

    public boolean isVerboseReqHeader() {
        return verboseReqHeader;
    }

    public boolean isVerboseReqContent() {
        return verboseReqContent;
    }

    public boolean isVerboseRespHeader() {
        return verboseRespHeader;
    }

    public boolean isVerboseRespContent() {
        return verboseRespContent;
    }

    public VerboseTargetPOIType getFilterPOIType() {
        return filterPOIType;
    }

    public Set<String> getFilterPOISet() {
        return filterPOISet;
    }

}
