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

import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;
import org.summerframework.security.SSLUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
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
import org.summerframework.boot.instrumentation.HTTPClientStatusListener;
import java.net.InetSocketAddress;
import java.net.ProxySelector;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class HttpConfig extends AbstractSummerBootConfig {

    public static final HttpConfig CFG = new HttpConfig();

    public static void main(String[] args) {
        String t = generateTemplate(HttpConfig.class);
        System.out.println(t);
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

    //1. Default NIO Response HTTP Headers
    @Memo(title = "1. Default NIO Response HTTP Headers",
            desc = "put generic HTTP response headers here",
            format = "nio.DefaultResponseHttpHeaders.?=?",
            example = "nio.DefaultResponseHttpHeaders.Access-Control-Allow-Origin=https://www.courtfiling.ca\n"
            + "nio.DefaultResponseHttpHeaders.Access-Control-Allow-Headers=X-Requested-With, Content-Type, Origin, Authorization\n"
            + "nio.DefaultResponseHttpHeaders.Access-Control-Allow-Methods=PUT,GET,POST,DELETE,OPTIONS,PATCH\n"
            + "nio.DefaultResponseHttpHeaders.Access-Control-Allow-Credentials=false\n"
            + "nio.DefaultResponseHttpHeaders.Access-Control-Allow-Credentials=false\n"
            + "nio.DefaultResponseHttpHeaders.Access-Control-Max-Age=3600\n"
            + "nio.DefaultResponseHttpHeaders.Content-Security-Policy=default-src 'self';script-src 'self' www.google-analytics.com www.google.com www.gstatic. js.stripe.com ajax.cloudflare.com;style-src 'self' 'unsafe-inline' cdnjs.cloudflare.com;img-src 'self' www.google-analytics.com stats.g.doubleclick.net www.gstatic.com;font-src 'self' cdnjs.cloudflare.com fonts.gstatic.com;base-uri 'self';child-src www.google.com js.stripe.com;form-action 'self';frame-ancestors 'none';report-uri=\"https://www.courtfiling.ca/report-uri\"\n"
            + "nio.DefaultResponseHttpHeaders.X-XSS-Protection=1; mode=block\n"
            + "nio.DefaultResponseHttpHeaders.Strict-Transport-Security=max-age=31536000;includeSubDomains;preload\n"
            + "nio.DefaultResponseHttpHeaders.X-Frame-Options=sameorigin\n"
            + "nio.DefaultResponseHttpHeaders.Expect-CT=max-age=86400, enforce, report-uri=\"https://www.courtfiling.ca/report-uri\"\n"
            + "nio.DefaultResponseHttpHeaders.X-Content-Type-Options=nosniff\n"
            + "nio.DefaultResponseHttpHeaders.Feature-Policy=autoplay 'none';camera 'none' ")

    private final HttpHeaders serverDefaultResponseHeaders = new DefaultHttpHeaders(true);

    //2. Web Server Mode
    @Memo(title = "2. Web Server Mode")
    @Config(key = "nio.http.web.docroot", required = false, defaultValue = "docroot")
    private volatile String docroot;

    @Config(key = "nio.http.web.resources", required = false, defaultValue = "web-resources/errorpages")
    private volatile String webResources;

    @Config(key = "nio.http.web.welcomePage", required = false, defaultValue = "index.html")
    private volatile String welcomePage;

    @Config(key = "nio.http.web-server.tempupload", required = false, defaultValue = "tempupload")
    private volatile String tempUoloadDir;

    private volatile boolean downloadMode;
    private volatile File rootFolder;

    //3.1 HTTP Client Security
    @Memo(title = "3.1 HTTP Client Security")
    @Config(key = "httpclient.ssl.protocal", required = false, defaultValue = "TLSv1.2")
    private volatile String protocal;

    @JsonIgnore
    @Config(key = "httpclient.ssl.KeyStore", StorePwdKey = "httpclient.ssl.KeyStorePwd",
            AliasKey = "httpclient.ssl.KeyAlias", AliasPwdKey = "httpclient.ssl.KeyPwd",
            required = false)
    private volatile KeyManagerFactory kmf;

    @JsonIgnore
    @Config(key = "httpclient.ssl.TrustStore", StorePwdKey = "httpclient.ssl.TrustStorePwd", required = false)
    private volatile TrustManagerFactory tmf;

    @Config(key = "httpclient.proxy.host", required = false)
    private volatile String proxyHost;
    private volatile String currentProxyHost;

    @Config(key = "httpclient.proxy.port", required = false, defaultValue = "8080")
    private volatile int proxyPort;
    private volatile int currentProxyPort;

    //3.2 HTTP Client Performance
    @Memo(title = "3.2 HTTP Client Performance")
    private volatile HttpClient httpClient;

    @Config(key = "httpclient.timeout.ms", required = false, defaultValue = "5000")
    private volatile long httpClientTimeout;

    @Config(key = "httpclient.executor.CoreSize", required = false, defaultValue = "0",
            desc = "HTTP Client will be disabled when core size is/below 0")
    private volatile int httpClientCoreSize;// how many tasks running at the same time
    private volatile int currentCore;

    @Config(key = "httpclient.executor.MaxSize", required = false, defaultValue = "0")
    private volatile int httpClientMaxSize;// how many tasks running at the same time
    private volatile int currentMax;

    @Config(key = "httpclient.executor.QueueSize", required = false, defaultValue = "2147483647")
    private volatile int httpClientQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full
    private volatile int currentQueue;

    private ThreadPoolExecutor tpe;
    private ScheduledExecutorService ses;

    //3.3 HTTP Client Default Headers
    @Memo(title = "3.3 HTTP Client Default Headers",
            desc = "put generic HTTP Client request headers here",
            format = "httpclient.DefaultReqHttpHeaders.?=?",
            example = "httpclient.DefaultReqHttpHeaders.Accept=application/json\n"
            + "httpclient.DefaultReqHttpHeaders.Content-Type=application/json;charset=UTF-8\n"
            + "httpclient.DefaultReqHttpHeaders.Accept-Language=en-ca")
    private final Map<String, String> httpClientDefaultRequestHeaders = new HashMap<>();

    private HTTPClientStatusListener listener = null;

    public void setStatusListener(HTTPClientStatusListener l) {
        listener = l;
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
        serverDefaultResponseHeaders.clear();
        httpClientDefaultRequestHeaders.clear();
        keys.forEach((name) -> {
            if (name.startsWith("nio.DefaultResponseHttpHeaders.")) {
                String[] names = name.split("\\.");
                String headerName = names[2];
                String headerValue = props.getProperty(name);
                serverDefaultResponseHeaders.set(headerName, headerValue);
            } else if (name.startsWith("httpclient.DefaultReqHttpHeaders.")) {
                String[] names = name.split("\\.");
                String headerName = names[2];
                String headerValue = props.getProperty(name);
                httpClientDefaultRequestHeaders.put(headerName, headerValue);
            }
        });

        // 2. Web Server Mode       
        rootFolder = cfgFile.getParentFile().getParentFile();
        docroot = rootFolder.getName() + File.separator + docroot;
        downloadMode = StringUtils.isBlank(welcomePage);
        tempUoloadDir = rootFolder.getAbsolutePath() + File.separator + tempUoloadDir;
        //Path dir = Paths.get(tempUoloadDir).toAbsolutePath();
        //Files.createDirectories(dir);

//        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("r--------");
//        FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(permissions);
//        Files.createDirectory(dir, fileAttributes);
//        File dir = new File(tempUoloadDir);
//        if(!dir.exists()) {
//            dir.mkdirs();
//        }
        // 3.1 HTTP Client keystore        
        KeyManager[] keyManagers = kmf == null ? null : kmf.getKeyManagers();
        // 3.2 HTTP Client truststore        
        TrustManager[] sbsTrustManagers = tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers();
        Boolean disableHostnameVerification = true;
        SSLContext sslContext = SSLUtil.buildSSLContext(keyManagers, sbsTrustManagers, protocal, disableHostnameVerification);

        // 3.3 HTTP Client Executor
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
                        .executor(tpe)
                        .sslContext(sslContext)
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NEVER);
                if (StringUtils.isNotBlank(proxyHost)) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
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
                        log.info(() -> "HTTPClient task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
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

    public File getRootFolder() {
        return rootFolder;
    }

    public HttpHeaders getServerDefaultResponseHeaders() {
        return serverDefaultResponseHeaders;
    }

    public String getDocroot() {
        return docroot;
    }

    public String getWebResources() {
        return webResources;
    }

    public String getWelcomePage() {
        return welcomePage;
    }

    public boolean isDownloadMode() {
        return downloadMode;
    }

    // 3. HttpClient
    @JsonIgnore
    public HttpClient getHttpClient() {
        return httpClient;
    }

    public Map<String, String> getHttpClientDefaultRequestHeaders() {
        return httpClientDefaultRequestHeaders;
    }

    public String getProtocal() {
        return protocal;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public long getHttpClientTimeout() {
        return httpClientTimeout;
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

    public String getTempUoloadDir() {
        return tempUoloadDir;
    }
}
