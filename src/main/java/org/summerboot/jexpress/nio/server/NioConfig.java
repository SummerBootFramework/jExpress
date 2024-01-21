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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.ssl.SslProvider;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.util.BeanUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
//@ImportResource(BootConstant.FILE_CFG_NIO)
public class NioConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(NioConfig.class);
        System.out.println(t);
    }

    public static final NioConfig cfg = new NioConfig();

    protected NioConfig() {
    }

    @Override
    public void shutdown() {
        String tn = Thread.currentThread().getName();
        if (tpe != null && !tpe.isShutdown()) {
            System.out.println(tn + ": shutdown tpe");
            tpe.shutdown();
        }
    }

    //1. NIO Network Listeners
    @ConfigHeader(title = "1. NIO Network Listeners",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8311, 127.0.0.1:8311, 0.0.0.0:8311")
    @Config(key = "nio.server.bindings", predefinedValue = "0.0.0.0:8211, 0.0.0.0:8311", required = true)
    private volatile List<InetSocketAddress> bindingAddresses;
    @Config(key = "nio.server.autostart", defaultValue = "true")
    private volatile boolean autoStart = true;

    //2. NIO Security
    @ConfigHeader(title = "2. NIO Security")

    private static final String KEY_kmf_key = "nio.server.ssl.KeyStore";
    private static final String KEY_kmf_StorePwdKey = "nio.server.ssl.KeyStorePwd";
    private static final String KEY_kmf_AliasKey = "nio.server.ssl.KeyAlias";
    private static final String KEY_kmf_AliasPwdKey = "nio.server.ssl.KeyPwd";

    @JsonIgnore
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF,
            callbackMethodName4Dump = "generateTemplate_keystore")
    private volatile KeyManagerFactory kmf = null;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
        sb.append(KEY_kmf_AliasKey + "=server1_2048.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
        generateTemplate = true;
    }

    private static final String KEY_tmf_key = "nio.server.ssl.TrustStore";
    private static final String KEY_tmf_StorePwdKey = "nio.server.ssl.TrustStorePwd";
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, //callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    private volatile TrustManagerFactory tmf = null;

    //    protected void generateTemplate_truststore(StringBuilder sb) {
//        sb.append(KEY_tmf_key + "="+FILENAME_TRUSTSTORE_4SERVER+"\n");
//        sb.append(KEY_tmf_StorePwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
//        generateTemplate = true;
//    }
    @Config(key = "nio.server.ssl.VerifyCertificateHost", defaultValue = "false")
    private volatile boolean verifyCertificateHost = false;

    @Config(key = "nio.server.ssl.Provider", defaultValue = "OPENSSL")
    private volatile SslProvider sslProvider = SslProvider.OPENSSL;

    @Config(key = "nio.server.ssl.Protocols", defaultValue = "TLSv1.2, TLSv1.3")
    private String[] sslProtocols = {"TLSv1.2", "TLSv1.3"};

    @Config(key = "nio.server.ssl.CipherSuites",
            desc = "use system default ciphersuites when not specified")
    private String[] sslCipherSuites;

    //3.1 Socket controller
    @ConfigHeader(title = "3.1 Socket controller")

    @Config(key = "nio.server.socket.SO_REUSEADDR", defaultValue = "true")
    private volatile boolean soReuseAddr = true;

    @Config(key = "nio.server.socket.SO_KEEPALIVE", defaultValue = "true")
    private volatile boolean soKeepAlive = true;

    @Config(key = "nio.server.socket.TCP_NODELAY", defaultValue = "true")
    private volatile boolean soTcpNodelay = true;

    @Config(key = "nio.server.socket.SO_LINGER", defaultValue = "-1")
    private volatile int soLinger = -1;

    //3.2 Socket Performance
    @ConfigHeader(title = "3.2 Socket Performance")

    @Config(key = "nio.server.ssl.HandshakeTimeout.second", defaultValue = "30")
    private volatile int sslHandshakeTimeoutSeconds = 30;

    @Config(key = "nio.server.socket.CONNECT_TIMEOUT.second", defaultValue = "30")
    private volatile int soConnectionTimeoutSeconds = 30;

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
    @ConfigHeader(title = "4.1 Netty controller")

    @Config(key = "nio.server.multiplexer", defaultValue = "AVAILABLE")
    private volatile IoMultiplexer multiplexer = IoMultiplexer.AVAILABLE;

    @Config(key = "nio.server.httpServerCodec.MaxInitialLineLength", defaultValue = "4096")
    private volatile int httpServerCodec_MaxInitialLineLength = 4096;

    @Config(key = "nio.server.httpServerCodec.MaxHeaderSize", defaultValue = "4096")
    private volatile int httpServerCodec_MaxHeaderSize = 4096;

    @Config(key = "nio.server.httpServerCodec.MaxChunkSize", defaultValue = "4096")
    private volatile int httpServerCodec_MaxChunkSize = 4096;

    @ConfigHeader(title = "4.2 Netty Performance - NIO and Biz Exector Pool")
    @Config(key = "nio.server.EventLoopGroup.AcceptorSize", defaultValue = "0",
            desc = "AcceptorSize 0 = number of bindings")
    private volatile int nioEventLoopGroupAcceptorSize = 0;

    @Config(key = "nio.server.EventLoopGroup.WorkerSize", predefinedValue = "0",
            desc = "WorkerSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int nioEventLoopGroupWorkerSize = BootConstant.CPU_CORE * 2 + 1;
    //private volatile int nioEventLoopGroupExecutorSize;

    @Config(key = "nio.server.BizExecutor.mode", defaultValue = "Mixed",
            desc = "valid value = CPU, IO (default), Mixed\nuse CPU core + 1 when application is CPU bound\n"
                    + "use CPU core x 2 + 1 when application is I/O bound\n"
                    + "need to find the best value based on your performance test result when nio.server.BizExecutor.mode=Mixed")
    private volatile ThreadingMode tpeThreadingMode = ThreadingMode.Mixed;

    @Config(key = "nio.server.BizExecutor.CoreSize", predefinedValue = "0",
            desc = "CoreSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int tpeCore = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time

    @Config(key = "nio.server.BizExecutor.MaxSize", predefinedValue = "0",
            desc = "MaxSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int tpeMax = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time

    @Config(key = "nio.server.BizExecutor.KeepAliveSec", defaultValue = "60")
    private volatile int tpeKeepAliveSeconds = 60;

    @Config(key = "nio.server.BizExecutor.QueueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    private volatile int tpeQueue = Integer.MAX_VALUE;// waiting list size when the pool is full

    @Config(key = "nio.server.BizExecutor.prestartAllCoreThreads", defaultValue = "false")
    private boolean prestartAllCoreThreads = false;

    @Config(key = "nio.server.BizExecutor.allowCoreThreadTimeOut", defaultValue = "false")
    private boolean allowCoreThreadTimeOut = false;

    //4.2 Netty Performance - NIO and Biz Exector Pool
    private ThreadPoolExecutor tpe = null;

    @Config(key = "nio.server.BizExecutor.bizTimeoutWarnThresholdMs", defaultValue = "5000")
    private volatile long bizTimeoutWarnThresholdMs = 5000L;

    //4.3 Netty Channel Handler
    @ConfigHeader(title = "4.3 Netty Channel Handler")
    @Config(key = "nio.server.ReaderIdleSeconds", defaultValue = "0",
            desc = "rec Idle enabled only when value > 0")
    private volatile int readerIdleSeconds = 0;

    @Config(key = "nio.server.WriterIdleSeconds", defaultValue = "0",
            desc = "Sent Idle enabled only when value > 0")
    private volatile int writerIdleSeconds = 0;

    @Config(key = "nio.server.health.InspectionIntervalSeconds", defaultValue = "5")
    private volatile int healthInspectionIntervalSeconds = 5;

    @Config(key = "nio.JAX-RS.fromJson.CaseInsensitive", defaultValue = "false")
    private volatile boolean fromJsonCaseInsensitive = false;
    @Config(key = "nio.JAX-RS.fromJson.failOnUnknownProperties", defaultValue = "true")
    private volatile boolean fromJsonFailOnUnknownProperties = true;
    @Config(key = "nio.JAX-RS.toJson.IgnoreNull", defaultValue = "true")
    private volatile boolean toJsonIgnoreNull = true;
    @Config(key = "nio.JAX-RS.toJson.Pretty", defaultValue = "false")
    private volatile boolean toJsonPretty = false;

    @Config(key = "nio.WebSocket.Compress", defaultValue = "true")
    private volatile boolean webSocketCompress = true;

    @Config(key = "nio.WebSocket.AllowExtensions", defaultValue = "true")
    private volatile boolean webSocketAllowExtensions = true;

    @Config(key = "nio.WebSocket.maxFrameSize", defaultValue = "5242880")
    private volatile int webSocketMaxFrameSize = 5242880;

    @Config(key = "nio.WebSocket.AllowMaskMismatch", defaultValue = "false")
    private volatile boolean webSocketAllowMaskMismatch = false;
    @Config(key = "nio.WebSocket.CheckStartsWith", defaultValue = "false")
    private volatile boolean webSocketCheckStartsWith = false;
    @Config(key = "nio.WebSocket.DropPongFrames", defaultValue = "true")
    private volatile boolean webSocketDropPongFrames = true;
    @Config(key = "nio.WebSocket.HandshakeTimeoutMs", defaultValue = "10000")
    private volatile long webSocketHandshakeTimeoutMs = 10000L;//io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig.DEFAULT_HANDSHAKE_TIMEOUT_MILLIS;

    //5. IO Communication logging filter
    @ConfigHeader(title = "5. IO Communication logging filter")
    @Config(key = "nio.verbose.filter.usertype", defaultValue = "ignore",
            desc = "5.1 caller filter\n"
                    + "valid value = id, uid, group, role, ignore")
    private volatile VerboseTargetUserType filterUserType = VerboseTargetUserType.ignore;

    public enum VerboseTargetUserType {
        id, uid, group, role, ignore
    }

    private static final String KEY_FILTER_USERTYPE_RANGE = "nio.verbose.filter.usertype.range";
    @Config(key = KEY_FILTER_USERTYPE_RANGE,
            desc = "user range (when type=CallerId): N1 - N2 or N1, N2, ... , Nn \n"
                    + "user range (when type=CallerName): johndoe, janedoe")
    private volatile String filterUserVaue;
    private volatile Set<String> filterCallerNameSet;
    private volatile Set<Long> filterCallerIdSet;
    private volatile long filterCallerIdFrom;
    private volatile long filterCallerIdTo;

    //5.2 error code filter
    public enum VerboseTargetCodeType {
        HttpStatusCode, AppErrorCode, all, ignore
    }

    @Config(key = "nio.verbose.filter.codetype", defaultValue = "all",
            desc = "valid value = HttpStatusCode, AppErrorCode, all, ignore")
    private volatile VerboseTargetCodeType filterCodeType = VerboseTargetCodeType.all;
    private static final String KEY_FILTER_CODETYPE_RANGE = "nio.verbose.filter.codetype.range";
    @Config(key = KEY_FILTER_CODETYPE_RANGE,
            desc = "5.2 error code filter\n"
                    + "code range: N1 - N2 or N1, N2, ... , Nn")
    private volatile String filterCodeVaue;
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
    @ConfigHeader(title = "6. POI logging filter")
    @Config(key = "nio.verbose.ServiceTimePOI.type", defaultValue = "all",
            desc = "valid value = filter, all, ignore")
    private volatile VerboseTargetPOIType filterPOIType = VerboseTargetPOIType.all;

    public enum VerboseTargetPOIType {
        filter, all, ignore
    }

    @Config(key = "nio.verbose.ServiceTimePOI.filter", defaultValue = "auth.begin, auth.end, db.begin, db.end",
            desc = "CSV format")
    private volatile Set<String> filterPOISet;

    private static final String HEADER_SERVER_RESPONSE = "server.DefaultResponseHttpHeaders.";

    //7. Web Server Mode
    @ConfigHeader(title = "7. Web Server Mode")
    @Config(key = "server.http.web.docroot", defaultValue = "docroot")
    private volatile String docroot = "docroot";

    @Config(key = "server.http.web.docroot.errorPageFolderName", defaultValue = "errorpages")
    private volatile String errorPageFolderName = "errorpages";

    @Config(key = "server.http.web.welcomePage", defaultValue = "index.html")
    private volatile String welcomePage = "index.html";

    @Config(key = "server.http.web-server.tempupload", defaultValue = "tempupload")
    private volatile String tempUoload = "tempupload";

    private volatile boolean downloadMode;
    private volatile File rootFolder;

    //8. Default NIO Response HTTP Headers
    @ConfigHeader(title = "8. Default Server Response HTTP Headers",
            desc = "put generic HTTP response headers here",
            format = HEADER_SERVER_RESPONSE + "<response_header_name>=<response_header_value>",
            example = HEADER_SERVER_RESPONSE + "Access-Control-Allow-Origin=https://www.summerboot.org\n"
                    + HEADER_SERVER_RESPONSE + "Access-Control-Allow-Headers=X-Requested-With, Content-Type, Origin, Authorization\n"
                    + HEADER_SERVER_RESPONSE + "Access-Control-Allow-Methods=PUT,GET,POST,DELETE,OPTIONS,PATCH\n"
                    + HEADER_SERVER_RESPONSE + "Access-Control-Allow-Credentials=false\n"
                    + HEADER_SERVER_RESPONSE + "Access-Control-Allow-Credentials=false\n"
                    + HEADER_SERVER_RESPONSE + "Access-Control-Max-Age=3600\n"
                    + HEADER_SERVER_RESPONSE + "Content-Security-Policy=default-src 'self';script-src 'self' www.google-analytics.com www.google.com www.gstatic. js.stripe.com ajax.cloudflare.com;style-src 'self' 'unsafe-inline' cdnjs.cloudflare.com;img-src 'self' www.google-analytics.com stats.g.doubleclick.net www.gstatic.com;font-src 'self' cdnjs.cloudflare.com fonts.gstatic.com;base-uri 'self';child-src www.google.com js.stripe.com;form-action 'self';frame-ancestors 'none';report-uri=\"https://www.summerboot.org/report-uri\"\n"
                    + HEADER_SERVER_RESPONSE + "X-XSS-Protection=1; mode=block\n"
                    + HEADER_SERVER_RESPONSE + "Strict-Transport-Security=max-age=31536000;includeSubDomains;preload\n"
                    + HEADER_SERVER_RESPONSE + "X-Frame-Options=sameorigin\n"
                    + HEADER_SERVER_RESPONSE + "Expect-CT=max-age=86400, enforce, report-uri=\"https://www.summerboot.org/report-uri\"\n"
                    + HEADER_SERVER_RESPONSE + "X-Content-Type-Options=nosniff\n"
                    + HEADER_SERVER_RESPONSE + "Feature-Policy=autoplay 'none';camera 'none' ",
            callbackMethodName4Dump = "generateTemplate_ResponseHeaders")
    private HttpHeaders serverDefaultResponseHeaders;

    protected void generateTemplate_ResponseHeaders(StringBuilder sb) {
        sb.append("#").append(HEADER_SERVER_RESPONSE).append("response_header_name=response_header_value\n");
    }

    public HttpHeaders getServerDefaultResponseHeaders() {
        return serverDefaultResponseHeaders;
    }

    private String docrootDir;
    private String tempUoloadDir;

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_KEYSTORE);
        //createIfNotExist(FILENAME_TRUSTSTORE_4SERVER);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        // 7. Web Server Mode       
        rootFolder = cfgFile.getParentFile().getParentFile();
        docrootDir = null;
        docrootDir = rootFolder.getAbsolutePath() + File.separator + docroot;
        downloadMode = StringUtils.isBlank(welcomePage);
        tempUoloadDir = null;
        tempUoloadDir = rootFolder.getAbsolutePath() + File.separator + tempUoload;

        //8. Default NIO Response HTTP Headers
        serverDefaultResponseHeaders = new DefaultHttpHeaders(true);
        Set<String> _keys = props.keySet().stream().map(o -> o.toString()).collect(Collectors.toSet());
        List<String> keys = new ArrayList<>(_keys);
        keys.forEach((name) -> {
            if (name.startsWith(HEADER_SERVER_RESPONSE)) {
                String[] names = name.split("\\.");
                String headerName = names[2];
                if (headerName != null) {
                    headerName = headerName.trim();
                }
                String headerValue = props.getProperty(name);
                if (headerValue != null) {
                    headerValue = headerValue.trim();
                }
                serverDefaultResponseHeaders.set(headerName, headerValue);
            }
        });

        //4.2 Netty Performance
        if (nioEventLoopGroupAcceptorSize < 1) {
            nioEventLoopGroupAcceptorSize = bindingAddresses.size();
        }
        if (nioEventLoopGroupWorkerSize < 1) {
            nioEventLoopGroupWorkerSize = CPU_CORE * 2 + 1;
        }

        tpe = buildThreadPoolExecutor(tpe, "NIO.Biz", tpeThreadingMode,
                tpeCore, tpeMax, tpeQueue, tpeKeepAliveSeconds, new AbortPolicyWithReport("NIOBizThreadPoolExecutor"),
                prestartAllCoreThreads, allowCoreThreadTimeOut, false);
        BeanUtil.init(fromJsonFailOnUnknownProperties, fromJsonCaseInsensitive, toJsonPretty, toJsonIgnoreNull);

        //5.1 caller filter
        switch (filterUserType) {
            case id:
                filterCallerIdSet = new HashSet();
                Long[] a = helper.getAsRangeLong(props, KEY_FILTER_USERTYPE_RANGE, filterCallerIdSet);
                if (a != null) {
                    filterCallerIdFrom = a[0];
                    filterCallerIdTo = a[1];
                    filterCallerIdSet = null;
                }
                break;
            case uid:
            case group:
            case role:
                String[] na = helper.getAsCSV(props, KEY_FILTER_USERTYPE_RANGE, null);
                filterCallerNameSet = new HashSet();
                filterCallerNameSet.addAll(Arrays.asList(na));
                break;
        }

        //5.2 error code filter
        switch (filterCodeType) {
            case HttpStatusCode:
            case AppErrorCode:
                filterCodeSet = new HashSet();
                Long[] a = helper.getAsRangeLong(props, KEY_FILTER_CODETYPE_RANGE, filterCodeSet);
                if (a != null) {
                    filterCodeRangeFrom = a[0];
                    filterCodeRangeTo = a[1];
                    filterCodeSet = null;
                }
                break;
        }
    }

    public ThreadPoolExecutor getBizExecutor() {
        return tpe;
    }

    public boolean isWebSocketCompress() {
        return webSocketCompress;
    }

    public boolean isWebSocketAllowExtensions() {
        return webSocketAllowExtensions;
    }

    public int getWebSocketMaxFrameSize() {
        return webSocketMaxFrameSize;
    }

    public boolean isWebSocketAllowMaskMismatch() {
        return webSocketAllowMaskMismatch;
    }

    public boolean isWebSocketCheckStartsWith() {
        return webSocketCheckStartsWith;
    }

    public boolean isWebSocketDropPongFrames() {
        return webSocketDropPongFrames;
    }

    public long getWebSocketHandshakeTimeoutMs() {
        return webSocketHandshakeTimeoutMs;
    }

    public List<InetSocketAddress> getBindingAddresses() {
        return bindingAddresses;
    }

    public boolean isAutoStart() {
        return autoStart;
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

    public int getSslHandshakeTimeoutSeconds() {
        return sslHandshakeTimeoutSeconds;
    }

    public int getSoConnectionTimeoutSeconds() {
        return soConnectionTimeoutSeconds;
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

    public long getBizTimeoutWarnThresholdMs() {
        return bizTimeoutWarnThresholdMs;
    }

    public int getReaderIdleSeconds() {
        return readerIdleSeconds;
    }

    public int getWriterIdleSeconds() {
        return writerIdleSeconds;
    }

    public int getHealthInspectionIntervalSeconds() {
        return healthInspectionIntervalSeconds;
    }

    public boolean isFromJsonCaseInsensitive() {
        return fromJsonCaseInsensitive;
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

    public File getRootFolder() {
        return rootFolder;
    }

    public String getDocrootDir() {
        return docrootDir;
    }

    public String getErrorPageFolderName() {
        return errorPageFolderName;
    }

    public String getWelcomePage() {
        return welcomePage;
    }

    public boolean isDownloadMode() {
        return downloadMode;
    }

    public String getTempUoloadDir() {
        return tempUoloadDir;
    }
}
