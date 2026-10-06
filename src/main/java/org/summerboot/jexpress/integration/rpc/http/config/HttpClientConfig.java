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
package org.summerboot.jexpress.integration.rpc.http.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.annotation.config.Config;
import org.summerboot.jexpress.annotation.config.ConfigHeader;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.infra.metrics.HttpClientStatusListener;
import org.summerboot.jexpress.security.ssl.SslUtil;
import org.summerboot.jexpress.util.concurrent.NamedDefaultThreadFactory;
import org.summerboot.jexpress.util.lang.BeanUtil;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

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

    protected static final String FILENAME_TRUSTSTORE_4CLIENT = "truststore_httpclient.p12";

    protected enum ProxyAuthenticationType {
        NONE, BASIC, DIGEST, NTLM, KERBEROS
    }

    protected enum ProxyAuthStrategy {
        /**
         * Sets Proxy-Authorization only in the request header
         */
        HEADER,
        /**
         * Sets Authenticator only at the HttpClient level
         */
        AUTHENTICATOR
    }

    protected HttpClientConfig() {
    }

    @Override
    protected void reset() {
        jsonParserTimeZone = TimeZone.getDefault();
        responseCallbackTpeCoreSize = BootConstants.CPU_CORE * 2 + 1;// how many tasks running at the same time
        responseCallbackTpeMaxSize = BootConstants.CPU_CORE * 2 + 1;// how many tasks running at the same time
        proxyAuthStrategy = ProxyAuthStrategy.AUTHENTICATOR;
    }

    @Override
    public void shutdown() {
        String tn = Thread.currentThread().getName();
        if (responseCallbackTpe != null && !responseCallbackTpe.isShutdown()) {
            System.out.println(tn + ": shutdown tpe: " + responseCallbackTpe);
            responseCallbackTpe.shutdown();
        } else {
            System.out.println(tn + ": already shutdown tpe: " + responseCallbackTpe);
        }
        if (ses != null && !ses.isShutdown()) {
            System.out.println(tn + ": shutdown ses: " + ses);
            ses.shutdown();
        }
    }

    //3.1 HTTP Client Security
    @ConfigHeader(title = "1. HTTP Client Security")
    @Config(key = "http.ssl.protocol", defaultValue = "TLSv1.3", desc = DESC_TLS_PROTOCOL)
    protected volatile String tlsProtocol = "TLSv1.3";

    protected static final String KEY_kmf_key = "httpclient.ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = "httpclient.ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = "httpclient.ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = "httpclient.ssl.KeyPwd";

    @JsonIgnore
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF_CLIENT,
            callbackMethodName4Dump = "generateTemplate_keystore")
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb, Properties currentValues) {
        appendCurrentValue(KEY_kmf_key, currentValues, FILENAME_KEYSTORE, sb);
        appendCurrentValue(KEY_kmf_StorePwdKey, currentValues, DEFAULT_DEC_VALUE, sb);
        appendCurrentValue(KEY_kmf_AliasKey, currentValues, "server3_2048.jexpress.org", sb);
        appendCurrentValue(KEY_kmf_AliasPwdKey, currentValues, DEFAULT_DEC_VALUE, sb);
        generateTemplate = true;
    }

    protected static final String KEY_tmf_key = "http.ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = "http.ssl.TrustStorePwd";
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF_CLIENT)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;


    protected void generateTemplate_truststore(StringBuilder sb, Properties currentValues) {
        appendCurrentValue(KEY_tmf_key, currentValues, FILENAME_TRUSTSTORE_4CLIENT, sb);
        appendCurrentValue(KEY_tmf_StorePwdKey, currentValues, DEFAULT_DEC_VALUE, sb);
        generateTemplate = true;
    }

    @Config(key = "http.ssl.HostnameVerification", defaultValue = "true")
    protected volatile Boolean hostnameVerification = true;

    @Config(key = "http.proxy.host")
    protected volatile String proxyHost;

    @Config(key = "http.proxy.port", defaultValue = "8080")
    protected volatile int proxyPort = 8080;

    @Config(key = "http.proxy.userName")
    protected volatile String proxyUserName;

    @JsonIgnore
    @Config(key = "http.proxy.userPwd", validate = Config.Validate.Encrypted)
    protected volatile String proxyUserPwd;

    @Config(key = "jdk.http.auth.tunneling.disabledSchemes", trim = false, defaultValue = "Basic", desc = "By default, basic authentication with the proxy is disabled when tunneling through an authenticating proxy since java 8u111. " +
            "\nTo enable basic authentication with the proxy when tunneling through an authenticating proxy, set this property to an empty string. " +
            "\nTo disable most of the authentication schemes, set this property to Basic, Digest, NTLM, or Kerberos. ")
    protected volatile String proxyAuthTunnelingDisabledSchemes = "Basic";

    @Config(key = "http.proxy.authStrategy", defaultValue = "AUTHENTICATOR",
            desc = "valid values: AUTHENTICATOR (default, sets Authenticator only at the HttpClient level), HEADER (Sets Proxy-Authorization only in the request header)")
    protected volatile ProxyAuthStrategy proxyAuthStrategy = ProxyAuthStrategy.AUTHENTICATOR;


    @JsonIgnore
    protected volatile String proxyAuthorizationBasicValue;

    //    @Config(key = "http.proxy.useAuthenticator")
//    protected volatile boolean useAuthenticator = false;
    @Config(key = "httpclient.redirectOption", defaultValue = "NEVER")
    protected volatile HttpClient.Redirect redirectOption = HttpClient.Redirect.NEVER;

    @Config(key = "http.deserialization.CaseInsensitive", defaultValue = "false")
    protected volatile boolean deserializationCaseInsensitive = false;
    @Config(key = "http.deserialization.failOnUnknownProperties", defaultValue = "true")
    protected volatile boolean deserializationFailOnUnknownProperties = true;
    @Config(key = "http.deserialization.TimeZone", desc = "The ID for a TimeZone, either an abbreviation such as \"UTC\", a full name such as \"America/Toronto\", or a custom ID such as \"GMT-8:00\", or \"system\" as system default timezone.", defaultValue = "system")
    protected TimeZone jsonParserTimeZone = TimeZone.getDefault();

    @JsonIgnore
    protected volatile JsonMapper jsonMapper = BeanUtil.JSONMapper;

    @JsonIgnore
    protected volatile XmlMapper xmlMapper = BeanUtil.XMLMapper;

    //3.2 HTTP Client Performance    
    @ConfigHeader(title = "2. HTTP Client Performance")
    @JsonIgnore
    protected volatile HttpClient httpClient;

    @JsonIgnore
    protected volatile HttpClient.Builder builder;

    @Config(key = "connection.timeout.ms", defaultValue = "3000", desc = "The maximum time to wait for only the connection to be established, should be less than http.timeout.ms")
    protected volatile long httpConnectionTimeoutMs = 3000;

    @Config(key = "request.timeout.ms", defaultValue = "5000", desc = "The maximum time to wait from the beginning of the connection establishment until the server sends data back, this is the end-to-end timeout.")
    protected volatile long httpRequestTimeoutMs = 5000;


    @Config(key = "jdk.httpclient.maxconnections", defaultValue = "2000",
            desc = "The maximum number of TCP connections per domain determines the \"concurrency output limit\" under high concurrency: For HTTP/1.1 set to 2000 connections per domain, while HTTP/2 sets to 500 TCP connections per domain.")
    protected volatile int maxConnections = 2000;
    @Config(key = "jdk.httpclient.connectionPoolSize", defaultValue = "2500",
            desc = "The total number of connections in the connection pool, ensuring sufficient space for idle connections (slightly larger than the single-domain limit to allow for occasional minor adjustments), determines the \"connection retention and reuse capability\" after a request ends.")
    protected volatile int connectionPoolSize = 2500;
    @Config(key = "jdk.httpclient.keepalive.timeout", defaultValue = "120",
            desc = "Extending the lifespan of long-lived connections: Since it's a high-frequency access, keeping connections alive for 120 seconds allows newly arriving virtual threads to 100% utilize existing connections. For HTTP/1.1 shortens this to 60 seconds (to avoid long-term port saturation due to too many connections), while HTTP/2 keeps connections alive for 120 seconds to maximize connection reuse.")
    protected volatile long keepAliveTimeoutMs = 120;


    @Config(key = "responseCallback.executor.enabled", defaultValue = "false", desc = "enable/disable the responseCallback.executor, default is false for quick and small response body, enable it when response is slow and large, and the responseCallback is CPU bound")
    protected volatile boolean isResponseCallbackTpeEnabled = false;
    @Config(key = "responseCallback.executor.mode", defaultValue = "VirtualThread",
            desc = "valid value = VirtualThread (default), CPU, IO and Mixed \n use CPU core + 1 when application is CPU bound\n"
                    + "use CPU core x 2 + 1 when application is I/O bound\n"
                    + "need to find the best value based on your performance test result when nio.server.BizExecutor.mode=Mixed")
    protected volatile ThreadingMode responseCallbackThreadingMode = ThreadingMode.VirtualThread;

    @Config(key = "responseCallback.executor.CoreSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "CoreSize 0 = Integer.MAX_VALUE")
    protected volatile int responseCallbackTpeCoreSize = Integer.MAX_VALUE;// how many tasks running at the same time

    @Config(key = "responseCallback.executor.MaxSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "MaxSize 0 = Integer.MAX_VALUE")
    protected volatile int responseCallbackTpeMaxSize = Integer.MAX_VALUE;// how many tasks running at the same time

    @Config(key = "responseCallback.executor.QueueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    protected volatile int responseCallbackTpeQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full

    @Config(key = "responseCallback.executor.KeepAliveSec", defaultValue = "60")
    protected volatile int responseCallbackTpeKeepAliveSeconds = 60;

    @Config(key = "responseCallback.executor.prestartAllCoreThreads", defaultValue = "false")
    protected boolean responseCallbackTpePrestartAllCoreThreads = false;

    @Config(key = "responseCallback.executor.allowCoreThreadTimeOut", defaultValue = "false")
    protected boolean responseCallbackTpeAllowCoreThreadTimeOut = false;

    protected ThreadPoolExecutor responseCallbackTpe;
    @JsonIgnore
    protected ScheduledExecutorService ses;

    protected static final String HEADER_CLIENT_REQUEST = "http.DefaultReqHttpHeaders.";
    //3.3 HTTP Client Default Headers
    @ConfigHeader(title = "3. HTTP Client Default Headers",
            desc = "put generic HTTP Client request headers here",
            format = HEADER_CLIENT_REQUEST + "<request_header_name>=<request_header_value>",
            example = HEADER_CLIENT_REQUEST + "Accept=application/json\n"
                    + HEADER_CLIENT_REQUEST + "Content-Type=application/json;charset=UTF-8\n"
                    + HEADER_CLIENT_REQUEST + "Accept-Language=en-ca",
            callbackMethodName4Dump = "generateTemplate_RequestHeaders")
    protected final Map<String, String> httpClientDefaultRequestHeaders = new HashMap<>();

    protected void generateTemplate_RequestHeaders(StringBuilder sb, Properties currentValues) {
        if (!appendCurrentValue(HEADER_CLIENT_REQUEST, currentValues, sb)) {
            sb.append("#").append(HEADER_CLIENT_REQUEST).append("request_header_name=request_header_value" + BootConstants.BR);
        }
    }

    protected HttpClientStatusListener listener = null;

    public void setStatusListener(HttpClientStatusListener l) {
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

        jsonMapper = BeanUtil.buildJsonMapper(jsonParserTimeZone, deserializationFailOnUnknownProperties, deserializationCaseInsensitive, false, true, true).build();
        xmlMapper = BeanUtil.buildXmlMapper(jsonParserTimeZone, deserializationFailOnUnknownProperties, deserializationCaseInsensitive, false, true, true).build();

        final SSLContext sslContext;
        if (StringUtils.isBlank(tlsProtocol)) {
            sslContext = null;
        } else {
            if (hostnameVerification != null) {
                System.setProperty("jdk.internal.http.disableHostnameVerification", hostnameVerification ? "false" : "true");
            }
            KeyManager[] keyManagers = kmf == null ? null : kmf.getKeyManagers();
            final TrustManager[] trustManagers = tmf == null ? null : tmf.getTrustManagers();
            sslContext = SslUtil.buildSSLContext(keyManagers, trustManagers, tlsProtocol);
        }
        System.setProperty("jdk.httpclient.maxconnections", "" + maxConnections);
        System.setProperty("jdk.httpclient.connectionPoolSize", "" + connectionPoolSize);
        System.setProperty("jdk.httpclient.keepalive.timeout", "" + keepAliveTimeoutMs);

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        // 3.6 build HTTP Client
        if (!isReal) {
            return;
        }


        ThreadPoolExecutor old = responseCallbackTpe;
        int currentTpeHashCode = old == null ? -1 : old.hashCode();
        if (isResponseCallbackTpeEnabled) {
            responseCallbackTpe = buildThreadPoolExecutor(old, "HttpClient", responseCallbackThreadingMode,
                    responseCallbackTpeCoreSize, responseCallbackTpeMaxSize, responseCallbackTpeQueueSize, responseCallbackTpeKeepAliveSeconds, null,
                    responseCallbackTpePrestartAllCoreThreads, responseCallbackTpeAllowCoreThreadTimeOut, false);
        } else {
            responseCallbackTpe = null;
        }
        int newTpeHashCode = responseCallbackTpe == null ? -1 : responseCallbackTpe.hashCode();
        boolean isHttpClientSettingsChanged = newTpeHashCode != currentTpeHashCode;
        // 1. save
        ScheduledExecutorService sesold = ses;

        if (isResponseCallbackTpeEnabled) {
            builder = HttpClient.newBuilder()
                    .executor(responseCallbackTpe)
                    .version(HttpClient.Version.HTTP_2)
                    .followRedirects(redirectOption)
                    .connectTimeout(Duration.ofMillis(httpConnectionTimeoutMs));
        } else {
            builder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .followRedirects(redirectOption)
                    .connectTimeout(Duration.ofMillis(httpConnectionTimeoutMs));
        }
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        if (StringUtils.isNotBlank(proxyHost)) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }

        if (proxyAuthTunnelingDisabledSchemes == null) {
            proxyAuthTunnelingDisabledSchemes = "";
        }
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", proxyAuthTunnelingDisabledSchemes);// -Djdk.http.auth.tunneling.disabledSchemes=""
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
                switch (proxyAuthStrategy) {
                    case HEADER -> {
                        // set proxy authenticator at the request header level: reqBuilder.setHeader("Proxy-Authorization", proxyAuthorizationBasicValue);
                        String plain = proxyUserName + ":" + proxyUserPwd;
                        String encoded = new String(java.util.Base64.getEncoder().encode(plain.getBytes()));
                        proxyAuthorizationBasicValue = "Basic " + encoded;
                        httpClientDefaultRequestHeaders.put("Proxy-Authorization", proxyAuthorizationBasicValue);
                    }
                    case AUTHENTICATOR -> {
                        // set proxy authenticator at the builder level:
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
                }
            }
        } else {
            builder.proxy(ProxySelector.of((InetSocketAddress) Proxy.NO_PROXY.address()));
        }
        httpClient = builder.build();
        // 3. register new
        if (isResponseCallbackTpeEnabled) {
            ses = Executors.newSingleThreadScheduledExecutor(NamedDefaultThreadFactory.build("HttpClient.QPS_SERVICE", true));
            ses.scheduleAtFixedRate(() -> {
                int queue = responseCallbackTpe.getQueue().size();
                int active = responseCallbackTpe.getActiveCount();
                if (active > 0 || queue > 0) {
                    long task = responseCallbackTpe.getTaskCount();
                    long completed = responseCallbackTpe.getCompletedTaskCount();
                    long pool = responseCallbackTpe.getPoolSize();
                    int core = responseCallbackTpe.getCorePoolSize();
                    long max = responseCallbackTpe.getMaximumPoolSize();
                    long largest = responseCallbackTpe.getLargestPoolSize();
                    if (listener != null) {
                        listener.onHTTPClientAccessReportUpdate(task, completed, queue, active, pool, core, max, largest);
                    }
                    logger.info(() -> "HTTPClient.responseCallbackTpe task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

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

    public String getTlsProtocol() {
        return tlsProtocol;
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

    public boolean isDeserializationCaseInsensitive() {
        return deserializationCaseInsensitive;
    }

    public boolean isDeserializationFailOnUnknownProperties() {
        return deserializationFailOnUnknownProperties;
    }

    public TimeZone getJsonParserTimeZone() {
        return jsonParserTimeZone;
    }

    public JsonMapper getJsonMapper() {
        return jsonMapper;
    }

    public XmlMapper getXmlMapper() {
        return xmlMapper;
    }

    public long getHttpConnectionTimeoutMs() {
        return httpConnectionTimeoutMs;
    }

    public long getHttpRequestTimeoutMs() {
        return httpRequestTimeoutMs;
    }

    public int getResponseCallbackTpeCoreSize() {
        return responseCallbackTpeCoreSize;
    }

    public int getResponseCallbackTpeMaxSize() {
        return responseCallbackTpeMaxSize;
    }

    public int getResponseCallbackTpeQueueSize() {
        return responseCallbackTpeQueueSize;
    }

    public String getHttpClientInfo() {
        return String.valueOf(httpClient);
    }

    public String getTpeInfo() {
        return String.valueOf(responseCallbackTpe);
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public Boolean getHostnameVerification() {
        return hostnameVerification;
    }

    public HttpClient.Redirect getRedirectOption() {
        return redirectOption;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public long getKeepAliveTimeoutMs() {
        return keepAliveTimeoutMs;
    }

    public boolean isResponseCallbackTpeEnabled() {
        return isResponseCallbackTpeEnabled;
    }

    public ThreadingMode getResponseCallbackThreadingMode() {
        return responseCallbackThreadingMode;
    }

    public int getResponseCallbackTpeKeepAliveSeconds() {
        return responseCallbackTpeKeepAliveSeconds;
    }

    public boolean isResponseCallbackTpePrestartAllCoreThreads() {
        return responseCallbackTpePrestartAllCoreThreads;
    }

    public boolean isResponseCallbackTpeAllowCoreThreadTimeOut() {
        return responseCallbackTpeAllowCoreThreadTimeOut;
    }

    public ThreadPoolExecutor getResponseCallbackTpe() {
        return responseCallbackTpe;
    }

    public String getProxyAuthTunnelingDisabledSchemes() {
        return proxyAuthTunnelingDisabledSchemes;
    }
}
