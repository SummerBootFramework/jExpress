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
package org.summerboot.jexpress.integration.httpclient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.boot.instrumentation.HTTPClientStatusListener;
import org.summerboot.jexpress.security.SSLUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
//@ImportResource(SummerApplication.CFG_HTTP)
abstract public class HttpClientConfig extends BootConfig {

    public static void main(String[] args) {
        class a extends HttpClientConfig {
        }
        String t = generateTemplate(a.class);
        System.out.println(t);
    }

    protected static final String FILENAME_KEYSTORE = "keystore.p12";
    protected static final String FILENAME_TRUSTSTORE_4CLIENT = "truststore_httpclient.p12";

    protected enum ProxyAuthenticationType {
        NONE, BASIC, DIGEST, NTLM, KERBEROS
    }

    protected enum ProxyAuthStrategy {
        /**
         * Sets Proxy-Authorization only in the request header
         */
        HEADER_ONLY,
        /**
         * Sets Authenticator only at the HttpClient level
         */
        AUTHENTICATOR_ONLY,
        /**
         * Sets Proxy-Authorization in the request header AND Authenticator at the HttpClient level
         */
        BOTH
    }

    protected HttpClientConfig() {
    }

    @Override
    protected void reset() {
        jsonParserTimeZone = TimeZone.getDefault();
        httpClientCoreSize = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time
        httpClientMaxSize = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time
    }

    @Override
    public void shutdown() {
        String tn = Thread.currentThread().getName();
        if (tpe != null && !tpe.isShutdown()) {
            System.out.println(tn + ": shutdown tpe: " + tpe);
            tpe.shutdown();
        } else {
            System.out.println(tn + ": already shutdown tpe: " + tpe);
        }
        if (ses != null && !ses.isShutdown()) {
            System.out.println(tn + ": shutdown ses: " + ses);
            ses.shutdown();
        }
    }

    //3.1 HTTP Client Security
    @ConfigHeader(title = "1. HTTP Client Security")
    @Config(key = "httpclient.ssl.protocol", defaultValue = "TLSv1.3")
    protected volatile String protocol = "TLSv1.3";

    protected static final String KEY_kmf_key = "httpclient.ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = "httpclient.ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = "httpclient.ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = "httpclient.ssl.KeyPwd";

    @JsonIgnore
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF,
            callbackMethodName4Dump = "generateTemplate_keystore")
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + DEFAULT_DEC_VALUE);
        sb.append(KEY_kmf_AliasKey + "=server3_2048.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    protected static final String KEY_tmf_key = "httpclient.ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = "httpclient.ssl.TrustStorePwd";
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=" + FILENAME_TRUSTSTORE_4CLIENT + "\n");
        sb.append(KEY_tmf_StorePwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    @Config(key = "httpclient.ssl.HostnameVerification", defaultValue = "true")
    protected volatile Boolean hostnameVerification = true;

    @Config(key = "httpclient.proxy.host")
    protected volatile String proxyHost;

    @Config(key = "httpclient.proxy.port", defaultValue = "8080")
    protected volatile int proxyPort = 8080;

    @Config(key = "httpclient.proxy.userName")
    protected volatile String proxyUserName;

    @JsonIgnore
    @Config(key = "httpclient.proxy.userPwd", validate = Config.Validate.Encrypted)
    protected volatile String proxyUserPwd;

    @Config(key = "httpclient.proxy.authStrategy", defaultValue = "HEADER_ONLY",
            desc = "valid values: HEADER_ONLY (Sets Proxy-Authorization only in the request header), AUTHENTICATOR_ONLY (Sets Authenticator only at the HttpClient level), BOTH")
    protected volatile ProxyAuthStrategy proxyAuthStrategy = ProxyAuthStrategy.HEADER_ONLY;

    @JsonIgnore
    protected volatile String proxyAuthorizationBasicValue;

    //    @Config(key = "httpclient.proxy.useAuthenticator")
//    protected volatile boolean useAuthenticator = false;
    @Config(key = "httpclient.redirectOption", defaultValue = "NEVER")
    protected volatile HttpClient.Redirect redirectOption = HttpClient.Redirect.NEVER;

    @Config(key = "httpclient.fromJson.CaseInsensitive", defaultValue = "false")
    protected volatile boolean fromJsonCaseInsensitive = false;
    @Config(key = "httpclient.fromJson.failOnUnknownProperties", defaultValue = "true")
    protected volatile boolean fromJsonFailOnUnknownProperties = true;
    @Config(key = "httpclient.fromJson.TimeZone", desc = "The ID for a TimeZone, either an abbreviation such as \"UTC\", a full name such as \"America/Toronto\", or a custom ID such as \"GMT-8:00\", or \"system\" as system default timezone.", defaultValue = "system")
    protected TimeZone jsonParserTimeZone = TimeZone.getDefault();

    //3.2 HTTP Client Performance    
    @ConfigHeader(title = "2. HTTP Client Performance")
    @JsonIgnore
    protected volatile HttpClient httpClient;

    @JsonIgnore
    protected volatile HttpClient.Builder builder;

    @Config(key = "httpclient.timeout.connect.ms", defaultValue = "3000", desc = "The maximum time to wait for only the connection to be established, should be less than httpclient.timeout.ms")
    protected volatile long httpConnectTimeoutMs = 3000;

    @Config(key = "httpclient.timeout.ms", defaultValue = "5000", desc = "The maximum time to wait from the beginning of the connection establishment until the server sends data back, this is the end-to-end timeout.")
    protected volatile long httpClientTimeoutMs = 5000;

    @Config(key = "httpclient.executor.mode", defaultValue = "VirtualThread",
            desc = "valid value = VirtualThread (default for Java 21+), CPU, IO and Mixed (default for old Java) \n use CPU core + 1 when application is CPU bound\n"
                    + "use CPU core x 2 + 1 when application is I/O bound\n"
                    + "need to find the best value based on your performance test result when nio.server.BizExecutor.mode=Mixed")
    protected volatile ThreadingMode tpeThreadingMode = ThreadingMode.VirtualThread;

    @Config(key = "httpclient.executor.CoreSize", predefinedValue = "0",
            desc = "CoreSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int httpClientCoreSize = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time

    @Config(key = "httpclient.executor.MaxSize", predefinedValue = "0",
            desc = "MaxSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int httpClientMaxSize = BootConstant.CPU_CORE * 2 + 1;// how many tasks running at the same time

    @Config(key = "httpclient.executor.QueueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    protected volatile int httpClientQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full

    @Config(key = "httpclient.executor.KeepAliveSec", defaultValue = "60")
    protected volatile int tpeKeepAliveSeconds = 60;

    @Config(key = "httpclient.executor.prestartAllCoreThreads", defaultValue = "false")
    protected boolean prestartAllCoreThreads = false;

    @Config(key = "httpclient.executor.allowCoreThreadTimeOut", defaultValue = "false")
    protected boolean allowCoreThreadTimeOut = false;

    protected ThreadPoolExecutor tpe;
    @JsonIgnore
    protected ScheduledExecutorService ses;

    protected static final String HEADER_CLIENT_REQUEST = "httpclient.DefaultReqHttpHeaders.";
    //3.3 HTTP Client Default Headers
    @ConfigHeader(title = "3. HTTP Client Default Headers",
            desc = "put generic HTTP Client request headers here",
            format = HEADER_CLIENT_REQUEST + "<request_header_name>=<request_header_value>",
            example = HEADER_CLIENT_REQUEST + "Accept=application/json\n"
                    + HEADER_CLIENT_REQUEST + "Content-Type=application/json;charset=UTF-8\n"
                    + HEADER_CLIENT_REQUEST + "Accept-Language=en-ca",
            callbackMethodName4Dump = "generateTemplate_RequestHeaders")
    protected final Map<String, String> httpClientDefaultRequestHeaders = new HashMap<>();

    protected void generateTemplate_RequestHeaders(StringBuilder sb) {
        sb.append("#").append(HEADER_CLIENT_REQUEST).append("request_header_name=request_header_value\n");
    }

    protected HTTPClientStatusListener listener = null;

    public void setStatusListener(HTTPClientStatusListener l) {
        listener = l;
    }

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_KEYSTORE, FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_TRUSTSTORE_4CLIENT);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        // 1. Default HTTP Headers: NIO and HttpClient in same loop
        Set<String> _keys = props.keySet().stream().map(o -> o.toString()).collect(Collectors.toSet());
        List<String> keys = new ArrayList<>(_keys);
        Collections.sort(keys);
//        final List<String> defaultHttpRequestHeaders = new ArrayList();
//        if (serverDefaultResponseHeaders != null) {
//            serverDefaultResponseHeaders.clear();
//        }

        httpClientDefaultRequestHeaders.clear();
        keys.forEach((name) -> {
            if (name.startsWith(HEADER_CLIENT_REQUEST)) {
                String[] names = name.split("\\.");
                String headerName = names[2];
                String headerValue = props.getProperty(name);
                httpClientDefaultRequestHeaders.put(headerName, headerValue);
            }
        });

        RPCResult.init(jsonParserTimeZone, fromJsonFailOnUnknownProperties, fromJsonCaseInsensitive);

        // 3.1 HTTP Client keystore
        KeyManager[] keyManagers = kmf == null ? null : kmf.getKeyManagers();
        // 3.2 HTTP Client truststore
        TrustManager[] trustManagers = tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers();
        SSLContext sslContext = SSLUtil.buildSSLContext(keyManagers, trustManagers, protocol);
        if (hostnameVerification != null) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", hostnameVerification ? "false" : "true");
        }

        // 3.3 HTTP Client Executor

        // -Djdk.httpclient.keepalive.timeout=99999
        //System.setProperty("jdk.httpclient.keepalive.timeout", "99999");
        //System.setProperty("jdk.httpclient.connectionPoolSize", "1");

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        // 3.6 build HTTP Client
        if (!isReal) {
            return;
        }

        ThreadPoolExecutor old = tpe;
        int currentTpeHashCode = old == null ? -1 : old.hashCode();
        tpe = buildThreadPoolExecutor(old, "HttpClient", tpeThreadingMode,
                httpClientCoreSize, httpClientMaxSize, httpClientQueueSize, tpeKeepAliveSeconds, null,
                prestartAllCoreThreads, allowCoreThreadTimeOut, false);
        boolean isHttpClientSettingsChanged = tpe.hashCode() != currentTpeHashCode;
        // 1. save
        ScheduledExecutorService sesold = ses;
        // 2. build new
//                tpe = new ThreadPoolExecutor(currentCore, currentMax, 60L, TimeUnit.SECONDS,
//                        new LinkedBlockingQueue<>(currentQueue), new NamedDefaultThreadFactory("HttpClient"), new AbortPolicyWithReport("HttpClientExecutor"));

        builder = HttpClient.newBuilder()
                .executor(tpe)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(redirectOption)
                .connectTimeout(Duration.ofMillis(httpConnectTimeoutMs));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        if (StringUtils.isNotBlank(proxyHost)) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");// -Djdk.http.auth.tunneling.disabledSchemes=""

        if (StringUtils.isNotBlank(proxyHost)) {
            //1. By default, basic authentication with the proxy is disabled when tunneling through an authenticating proxy since java 8u111.
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
            if (proxyUserName == null) {
                proxyUserName = "";
            }
            if (proxyUserPwd == null) {
                proxyUserPwd = "";
            }
            if (StringUtils.isNotBlank(proxyUserName)) {
                if (proxyAuthStrategy == ProxyAuthStrategy.AUTHENTICATOR_ONLY || proxyAuthStrategy == ProxyAuthStrategy.BOTH) {
                    //2a. set proxy authenticator at the builder level:
                    final char[] proxyUserPwdChars = proxyUserPwd.toCharArray();
                    builder.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            if (getRequestorType() == Authenticator.RequestorType.PROXY) {
                                return new PasswordAuthentication(proxyUserName, proxyUserPwdChars);
                            }
                            return null; // for other types of authentication, return null
                        }
                    });
                }
                if (proxyAuthStrategy == ProxyAuthStrategy.HEADER_ONLY || proxyAuthStrategy == ProxyAuthStrategy.BOTH) {
                    String plain = proxyUserName + ":" + proxyUserPwd;
                    String encoded = new String(java.util.Base64.getEncoder().encode(plain.getBytes()));
                    proxyAuthorizationBasicValue = "Basic " + encoded;
                } else {
                    proxyAuthorizationBasicValue = null; // no Proxy-Authorization header
                }
            }
        } else {
            builder.proxy(ProxySelector.of((InetSocketAddress) Proxy.NO_PROXY.address()));
        }
        httpClient = builder.build();
        // 3. register new
        ses = Executors.newSingleThreadScheduledExecutor(NamedDefaultThreadFactory.build("HttpClient.QPS_SERVICE", tpeThreadingMode.equals(ThreadingMode.VirtualThread)));
        ses.scheduleAtFixedRate(() -> {
            int queue = tpe.getQueue().size();
            int active = tpe.getActiveCount();
            if (active > 0 || queue > 0) {
                long task = tpe.getTaskCount();
                long completed = tpe.getCompletedTaskCount();
                long pool = tpe.getPoolSize();
                int core = tpe.getCorePoolSize();
                long max = tpe.getMaximumPoolSize();
                long largest = tpe.getLargestPoolSize();
                if (listener != null) {
                    listener.onHTTPClientAccessReportUpdate(task, completed, queue, active, pool, core, max, largest);
                }
                logger.info(() -> "HTTPClient task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
            }
        }, 0, 1, TimeUnit.SECONDS);

        // 4. shutdown old
        if (old != null && isHttpClientSettingsChanged) {
            old.shutdown();
        }
        if (sesold != null) {
            sesold.shutdown();
        }
        System.gc();
    }

    // 3. HttpClient
    public HttpClient getHttpClient() {
        return httpClient;
    }

    public HttpClient.Builder getBuilder() {
        return builder;
    }

    public HttpClient updateBuilder(HttpClient.Builder builder) {
        this.builder = builder;
        this.httpClient = builder.build();
        return this.httpClient;
    }

    public Map<String, String> getHttpClientDefaultRequestHeaders() {
        return httpClientDefaultRequestHeaders;
    }

    public String getProtocol() {
        return protocol;
    }

    public Boolean isHostnameVerificationEnabled() {
        return hostnameVerification;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyUserName() {
        return proxyUserName;
    }

    public String getProxyUserPwd() {
        return proxyUserPwd;
    }

    public ProxyAuthStrategy getProxyAuthStrategy() {
        return proxyAuthStrategy;
    }

    public String getProxyAuthorizationBasicValue() {
        return proxyAuthorizationBasicValue;
    }

    public boolean isFromJsonCaseInsensitive() {
        return fromJsonCaseInsensitive;
    }

    public boolean isFromJsonFailOnUnknownProperties() {
        return fromJsonFailOnUnknownProperties;
    }

    public TimeZone getJsonParserTimeZone() {
        return jsonParserTimeZone;
    }

    public long getHttpConnectTimeoutMs() {
        return httpConnectTimeoutMs;
    }

    public long getHttpClientTimeoutMs() {
        return httpClientTimeoutMs;
    }

    public int getHttpClientCoreSize() {
        return httpClientCoreSize;
    }

    public int getHttpClientMaxSize() {
        return httpClientMaxSize;
    }

    public int getHttpClientQueueSize() {
        return httpClientQueueSize;
    }

    public String getHttpClientInfo() {
        return String.valueOf(httpClient);
    }

    public String getTpeInfo() {
        return String.valueOf(tpe);
    }
}
