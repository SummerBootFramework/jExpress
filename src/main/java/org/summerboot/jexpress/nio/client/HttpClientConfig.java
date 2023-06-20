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
package org.summerboot.jexpress.nio.client;

import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.security.SSLUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.instrumentation.HTTPClientStatusListener;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.nio.server.AbortPolicyWithReport;

/**
 *
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

    protected HttpClientConfig() {
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
    @Config(key = "httpclient.ssl.protocol")
    private volatile String protocol = "TLSv1.3";

    private static final String KEY_kmf_key = "httpclient.ssl.KeyStore";
    private static final String KEY_kmf_StorePwdKey = "httpclient.ssl.KeyStorePwd";
    private static final String KEY_kmf_AliasKey = "httpclient.ssl.KeyAlias";
    private static final String KEY_kmf_AliasPwdKey = "httpclient.ssl.KeyPwd";

    @JsonIgnore
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF,
            callbackMethodName4Dump = "generateTemplate_keystore")
    private volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=server_keystore.p12\n");
        sb.append(KEY_kmf_StorePwdKey + "=DEC(changeit)\n");
        sb.append(KEY_kmf_AliasKey + "=server3_2048.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + "=DEC(changeit)\n");
        generateTemplate = true;
    }

    private static final String KEY_tmf_key = "httpclient.ssl.TrustStore";
    private static final String KEY_tmf_StorePwdKey = "httpclient.ssl.TrustStorePwd";
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, //callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    private volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=truststore_4client.p12\n");
        sb.append(KEY_tmf_StorePwdKey + "=DEC(changeit)\n");
        generateTemplate = true;
    }

    @Config(key = "httpclient.ssl.HostnameVerification")
    private volatile Boolean hostnameVerification = false;

    @Config(key = "httpclient.proxy.host")
    private volatile String proxyHost;
    private volatile String currentProxyHost;

    @Config(key = "httpclient.proxy.port")
    private volatile int proxyPort = 8080;
    private volatile int currentProxyPort;

    @Config(key = "httpclient.proxy.userName")
    private volatile String proxyUserName;

    @JsonIgnore
    @Config(key = "httpclient.proxy.userPwd", validate = Config.Validate.Encrypted)
    private volatile String proxyUserPwd;

    @JsonIgnore
    private volatile String proxyAuthorizationBasicValue;

//    @Config(key = "httpclient.proxy.useAuthenticator")
//    private volatile boolean useAuthenticator = false;
    @Config(key = "httpclient.redirectOption")
    private volatile HttpClient.Redirect redirectOption = HttpClient.Redirect.NEVER;

    @Config(key = "httpclient.fromJson.CaseInsensitive")
    private volatile boolean fromJsonCaseInsensitive = false;
    @Config(key = "httpclient.fromJson.failOnUnknownProperties")
    private volatile boolean fromJsonFailOnUnknownProperties = true;

    //3.2 HTTP Client Performance
    @ConfigHeader(title = "2. HTTP Client Performance")
    private volatile HttpClient httpClient;

    @Config(key = "httpclient.timeout.ms")
    private volatile long httpClientTimeoutMs = 5000;

    private final int availableProcessors = Runtime.getRuntime().availableProcessors();

    @Config(key = "httpclient.executor.CoreSize", predefinedValue = "0",
            desc = "CoreSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int httpClientCoreSize = availableProcessors * 2 + 1;// how many tasks running at the same time
    private volatile int currentCore;

    @Config(key = "httpclient.executor.MaxSize", predefinedValue = "0",
            desc = "MaxSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int httpClientMaxSize = availableProcessors * 2 + 1;// how many tasks running at the same time
    private volatile int currentMax;

    @Config(key = "httpclient.executor.QueueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    private volatile int httpClientQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full
    private volatile int currentQueue;

    private ThreadPoolExecutor tpe;
    @JsonIgnore
    private ScheduledExecutorService ses;

    private static final String HEADER_CLIENT_REQUEST = "httpclient.DefaultReqHttpHeaders.";
    //3.3 HTTP Client Default Headers
    @ConfigHeader(title = "3. HTTP Client Default Headers",
            desc = "put generic HTTP Client request headers here",
            format = HEADER_CLIENT_REQUEST + "<request_header_name>=<request_header_value>",
            example = HEADER_CLIENT_REQUEST + "Accept=application/json\n"
            + HEADER_CLIENT_REQUEST + "Content-Type=application/json;charset=UTF-8\n"
            + HEADER_CLIENT_REQUEST + "Accept-Language=en-ca",
            callbackMethodName4Dump = "generateTemplate_RequestHeaders")
    private final Map<String, String> httpClientDefaultRequestHeaders = new HashMap<>();

    protected void generateTemplate_RequestHeaders(StringBuilder sb) {
        sb.append("#").append(HEADER_CLIENT_REQUEST).append("request_header_name=request_header_value\n");
    }

    private HTTPClientStatusListener listener = null;

    public void setStatusListener(HTTPClientStatusListener l) {
        listener = l;
    }

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist("server_keystore.p12");
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

        RPCResult.init(fromJsonFailOnUnknownProperties, fromJsonCaseInsensitive);

        // 3.1 HTTP Client keystore        
        KeyManager[] keyManagers = kmf == null ? null : kmf.getKeyManagers();
        // 3.2 HTTP Client truststore        
        TrustManager[] trustManagers = tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers();
        SSLContext sslContext = SSLUtil.buildSSLContext(keyManagers, trustManagers, protocol);
        if (hostnameVerification != null) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", hostnameVerification ? "false" : "true");
        }

        // 3.3 HTTP Client Executor
        if (httpClientCoreSize <= 0) {
            httpClientCoreSize = availableProcessors * 2 + 1;
        }
        if (httpClientMaxSize <= 0) {
            httpClientMaxSize = availableProcessors * 2 + 1;
        }
        if (httpClientMaxSize < httpClientCoreSize) {
            helper.addError("MaxSize should not less than CoreSize");
        }
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
        boolean isHttpClientSettingsChanged = currentCore != httpClientCoreSize || currentMax != httpClientMaxSize || currentQueue != httpClientQueueSize
                || !StringUtils.equals(currentProxyHost, proxyHost) || currentProxyPort != proxyPort;
        if (httpClient == null || isHttpClientSettingsChanged) {
            // 1. save
            currentCore = httpClientCoreSize;
            currentMax = httpClientMaxSize;
            currentQueue = httpClientQueueSize;
            currentProxyHost = proxyHost;
            currentProxyPort = proxyPort;
            ThreadPoolExecutor old = tpe;
            ScheduledExecutorService sesold = ses;
            // 2. build new 
            if (httpClientCoreSize > 0) {
                tpe = new ThreadPoolExecutor(currentCore, currentMax, 60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(currentQueue), Executors.defaultThreadFactory(), new AbortPolicyWithReport("HttpClientExecutor"));

                HttpClient.Builder builder = HttpClient.newBuilder()
                        .executor(tpe);
                if (sslContext != null) {
                    builder.sslContext(sslContext);
                }
                builder.version(HttpClient.Version.HTTP_2)
                        .followRedirects(redirectOption);
                if (StringUtils.isNotBlank(proxyHost)) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
                }
                if (StringUtils.isNotBlank(proxyUserName)) {
                    if (proxyUserPwd == null) {
                        proxyUserPwd = "";
                    }
                    //1. By default, basic authentication with the proxy is disabled when tunneling through an authenticating proxy since java 8u111.
                    System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");// -Djdk.http.auth.tunneling.disabledSchemes=""
                    //2a. set proxy authenticator at the request header level: 
                    String plain = proxyUserName + ":" + proxyUserPwd;
                    String encoded = new String(java.util.Base64.getEncoder().encode(plain.getBytes()));
                    proxyAuthorizationBasicValue = "Basic " + encoded;
                    //HttpRequest.newBuilder().setHeader("Proxy-Authorization", ProxyAuthorizationValue);

//                    if (useAuthenticator) {
//                        //2b. set proxy authenticator at the HttpClient level: not flexible to deal with different remote server settings
//                        Authenticator authenticator = new Authenticator() {
//                            @Override
//                            protected PasswordAuthentication getPasswordAuthentication() {
//                                return new PasswordAuthentication(proxyUserName, proxyUserPwd.toCharArray());
//                            }
//                        };
//                        builder.authenticator(authenticator);
//                    }
                }
                httpClient = builder.build();
                // 3. register new
                ses = Executors.newSingleThreadScheduledExecutor();
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
            }
            // 4. shutdown old
            if (old != null) {
                old.shutdown();
            }
            if (sesold != null) {
                sesold.shutdown();
            }
        }
    }

    // 3. HttpClient
    @JsonIgnore
    public HttpClient getHttpClient() {
        return httpClient;
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

    public String getProxyAuthorizationBasicValue() {
        return proxyAuthorizationBasicValue;
    }

    public boolean isFromJsonCaseInsensitive() {
        return fromJsonCaseInsensitive;
    }

    public boolean isFromJsonFailOnUnknownProperties() {
        return fromJsonFailOnUnknownProperties;
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
