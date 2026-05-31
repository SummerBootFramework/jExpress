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
package org.summerboot.jexpress.grpc.test;

import io.grpc.BindableService;
import io.grpc.NameResolverProvider;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.lifecycle.AuthenticatorListener;
import org.summerboot.jexpress.core.session.SessionContext;
import org.summerboot.jexpress.grpc.client.BootLoadBalancerProvider;
import org.summerboot.jexpress.grpc.client.GrpcClientConfig;
import org.summerboot.jexpress.grpc.server.GrpcServer;
import org.summerboot.jexpress.security.ssl.SslUtil;
import org.summerboot.jexpress.security.auth.BootAuthenticator;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.runtime.ApplicationUtil;

import javax.naming.NamingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * For testing gRPC with two-way TLS.
 * (usage and example see https://github.com/SummerBootFramework/jExpressDemo-HelloSummer/blob/main/HelloSummer-demo02/src/test/java/test/integration/grpc/GrpcTest.java)
 */
public abstract class GrpcTestHelper {

    public static class GrpcSimpleClient {
        private final String testName;
        private final int length;
        private String configDir;
        private File keyStore;
        private File serverTrustStore;
        private File clientTrustStore;
        private File emptyTrustStore;
        private String serverKeyAlias;
        private String clientKeyAlias;
        private String overrideAuthority;


        private String keyStorePassword;
        private String keyPassword;
        private String trustStorePassword;
        private KeyManagerFactory kmfServer;
        private TrustManagerFactory tmfServer;
        private KeyManagerFactory kmfClient;
        private TrustManagerFactory tmfClient;
        private TrustManagerFactory tmfEmpty;

        private String host = "localhost";
        private int port = 8425;
        private String loadBalancingPolicy = GrpcClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
        private String loadBalancingTargetScheme = "grpc";
        private String tlsProtocolClient = "TLSv1.3";

        public GrpcSimpleClient(int length) throws GeneralSecurityException, IOException {
            this("GrpcTestHelper " + length, length, "src/test/resources/config/");
        }

        public GrpcSimpleClient(String testName, int length) throws GeneralSecurityException, IOException {
            this(testName, length, "src/test/resources/config/");
        }

        public GrpcSimpleClient(String testName, int length, String configDir) throws GeneralSecurityException, IOException {
            this.testName = testName;
            this.length = length;
            this.configDir = configDir;


            this.serverKeyAlias = "server2_" + length + ".jexpress.org";
            this.clientKeyAlias = "server3_" + length + ".jexpress.org";
            this.overrideAuthority = "server2." + length + ".jexpress.org";

            ClassLoader classLoader = this.getClass().getClassLoader();
            Properties testProp = ApplicationUtil.getPropertiesFromResource("application-test.properties", classLoader);
            String defaultPassword = testProp.getProperty("test.p12.password");
            keyStorePassword = defaultPassword;
            keyPassword = defaultPassword;
            trustStorePassword = defaultPassword;
            updateTLS(configDir);
        }

        public void updateTLS(String configDir) throws GeneralSecurityException, IOException {
            keyStore = new File(configDir + "keystore.p12").getAbsoluteFile();
            createIfNotExist("keystore.p12", keyStore);

            serverTrustStore = new File(configDir + "truststore_server.p12").getAbsoluteFile();
            createIfNotExist("truststore.p12", serverTrustStore);

            clientTrustStore = new File(configDir + "truststore_client.p12").getAbsoluteFile();
            createIfNotExist("truststore.p12", clientTrustStore);

            emptyTrustStore = new File(configDir + "truststore_empty.p12").getAbsoluteFile();
            createIfNotExist("truststore_empty.p12", emptyTrustStore);

            // server TLS
            kmfServer = SslUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), serverKeyAlias, keyPassword.toCharArray());
            tmfServer = SslUtil.buildTrustManagerFactory(serverTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
            // client TLS
            kmfClient = SslUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), clientKeyAlias, keyPassword.toCharArray());
            tmfClient = SslUtil.buildTrustManagerFactory(clientTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
            // untrusted TLS
            tmfEmpty = SslUtil.buildTrustManagerFactory(emptyTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
        }

        public void createIfNotExist(String srcFileName, File destFile) {
            File parentDir = destFile.getParentFile();
            String location = destFile.getParent();
            String destFileName = destFile.getName();
            ClassLoader classLoader = this.getClass().getClassLoader();
            ApplicationUtil.createIfNotExist(location, classLoader, srcFileName, destFileName);
        }

        @Override
        public String toString() {
            return "GrpcSimpleClient{" +
                    "name=" + testName +
                    "length=" + length +
                    ", configDir='" + configDir + '\'' +
                    ", port=" + port +
                    ", host='" + host + '\'' +
                    '}';
        }

        public String getConfigDir() {
            return configDir;
        }

        public void setConfigDir(String configDir) throws GeneralSecurityException, IOException {
            this.configDir = configDir;
            updateTLS(configDir);
        }

        public File getKeyStore() {
            return keyStore;
        }

        public void setKeyStore(File keyStore) {
            this.keyStore = keyStore;
        }

        public File getServerTrustStore() {
            return serverTrustStore;
        }

        public void setServerTrustStore(File serverTrustStore) {
            this.serverTrustStore = serverTrustStore;
        }

        public File getClientTrustStore() {
            return clientTrustStore;
        }

        public void setClientTrustStore(File clientTrustStore) {
            this.clientTrustStore = clientTrustStore;
        }

        public File getEmptyTrustStore() {
            return emptyTrustStore;
        }

        public void setEmptyTrustStore(File emptyTrustStore) {
            this.emptyTrustStore = emptyTrustStore;
        }

        public String getServerKeyAlias() {
            return serverKeyAlias;
        }

        public void setServerKeyAlias(String serverKeyAlias) {
            this.serverKeyAlias = serverKeyAlias;
        }

        public String getClientKeyAlias() {
            return clientKeyAlias;
        }

        public void setClientKeyAlias(String clientKeyAlias) {
            this.clientKeyAlias = clientKeyAlias;
        }

        public String getOverrideAuthority() {
            return overrideAuthority;
        }

        public void setOverrideAuthority(String overrideAuthority) {
            this.overrideAuthority = overrideAuthority;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getLoadBalancingPolicy() {
            return loadBalancingPolicy;
        }

        public void setLoadBalancingPolicy(String loadBalancingPolicy) {
            this.loadBalancingPolicy = loadBalancingPolicy;
        }

        public String getLoadBalancingTargetScheme() {
            return loadBalancingTargetScheme;
        }

        public void setLoadBalancingTargetScheme(String loadBalancingTargetScheme) {
            this.loadBalancingTargetScheme = loadBalancingTargetScheme;
        }

        public KeyManagerFactory getKmfServer() {
            return kmfServer;
        }

        public TrustManagerFactory getTmfServer() {
            return tmfServer;
        }

        public KeyManagerFactory getKmfClient() {
            return kmfClient;
        }

        public TrustManagerFactory getTmfClient() {
            return tmfClient;
        }

        public TrustManagerFactory getTmfEmpty() {
            return tmfEmpty;
        }

        public String getTlsProtocalClient() {
            return tlsProtocolClient;
        }

        public void setTlsProtocalClient(String tlsProtocalClient) {
            this.tlsProtocolClient = tlsProtocalClient;
        }
    }

    public static GrpcServer buildGrpcServer(GrpcSimpleClient config, BindableService... serviceImps) throws GeneralSecurityException, IOException {
        if (serviceImps == null || serviceImps.length == 0) {
            return null;
        }
        String host = config.getHost();
        int port = config.getPort();
        File keyStore = config.getKeyStore();
        File serverTrustStore = config.getServerTrustStore();
        String serverKeyAlias = config.getServerKeyAlias();
        String keyStorePassword = config.getKeyStorePassword();
        String keyPassword = config.getKeyPassword();
        String trustStorePassword = config.getTrustStorePassword();

        KeyManagerFactory kmfServer = SslUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), serverKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfServer = SslUtil.buildTrustManagerFactory(serverTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
        return buildGrpcServer(host, port, kmfServer, tmfServer, serviceImps);
    }

    public static GrpcServer buildGrpcServer(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService... serviceImps) throws IOException {
        if (serviceImps == null || serviceImps.length == 0) {
            return null;
        }
        // 1. config server
        ThreadPoolExecutor tpe = BootConfig.buildThreadPoolExecutor("");
        ServerInterceptor serverInterceptor = new BootAuthenticator() {
            @Override
            protected Caller authenticate(String username, String password, Object metaData, AuthenticatorListener listener, SessionContext context) throws NamingException {
                return null;
            }
        };
        GrpcServer grpcServer = new GrpcServer(host, port, kmfServer, tmfServer, tpe, true, false, null, serverInterceptor);

        ServerBuilder serverBuilder = grpcServer.getServerBuilder();
        for (BindableService serviceImpl : serviceImps) {
            serverBuilder.addService(serviceImpl);
        }

        // 2. start server
        StringBuilder startingMemo = new StringBuilder();
        grpcServer.start(false, startingMemo);
        return grpcServer;
    }

    public static NettyChannelBuilder buildGrpcClient(GrpcSimpleClient config) throws IOException, GeneralSecurityException {
        String host = config.getHost();
        int port = config.getPort();
        String loadBalancingPolicy = config.getLoadBalancingPolicy();
        String loadBalancingTargetScheme = config.getLoadBalancingTargetScheme();
        File keyStore = config.getKeyStore();
        File clientTrustStore = config.getClientTrustStore();
        String clientKeyAlias = config.getClientKeyAlias();
        String overrideAuthority = config.getOverrideAuthority();

        String keyStorePassword = config.getKeyStorePassword();
        String keyPassword = config.getKeyPassword();
        String trustStorePassword = config.getTrustStorePassword();
        KeyManagerFactory kmfClient = SslUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), clientKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfClient = SslUtil.buildTrustManagerFactory(clientTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
        String tlsProtocal = config.getTlsProtocalClient();
        // client
        return buildGrpcClient(tlsProtocal, host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
    }

    public static NettyChannelBuilder buildGrpcClient(String tlsProtocal, String host, int port, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws SSLException {
        String loadBalancingPolicy = GrpcClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
        String loadBalancingTargetScheme = "grpc";
        return buildGrpcClient(tlsProtocal, host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
    }

    public static NettyChannelBuilder buildGrpcClient(String tlsProtocal, String host, int port, String loadBalancingPolicy, String loadBalancingTargetScheme, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws SSLException {
        int priority = 0;
        List<InetSocketAddress> loadBalancingServers = List.of(new InetSocketAddress(host, port));
        NameResolverProvider nameResolverProvider = new BootLoadBalancerProvider(loadBalancingTargetScheme, ++priority, loadBalancingServers);

        return GrpcClientConfig.initNettyChannelBuilder(nameResolverProvider, loadBalancingPolicy, null, kmfClient, tmfClient, overrideAuthority, null, SslProvider.OPENSSL, tlsProtocal);
    }


    //@Test
    public void test2WayTLS() throws GeneralSecurityException, IOException {
        GrpcSimpleClient[] testConfigs = buildDefaultTestConfigs();
        for (GrpcSimpleClient config : testConfigs) {
            System.out.println("Testing with config: " + config);
            test2WayTLS(config);
        }
    }

    protected GrpcSimpleClient[] buildDefaultTestConfigs() throws GeneralSecurityException, IOException {
        return new GrpcSimpleClient[]{
                new GrpcSimpleClient(2048),
                new GrpcSimpleClient(4096)
        };
    }

    protected abstract BindableService[] getServerImpls();

    public void test2WayTLS(GrpcSimpleClient config) throws IOException {
        String host = config.getHost();
        int port = config.getPort();
        String loadBalancingPolicy = config.getLoadBalancingPolicy();
        String loadBalancingTargetScheme = config.getLoadBalancingTargetScheme();
        String overrideAuthority = config.getOverrideAuthority();

        // server TLS
        KeyManagerFactory kmfServer = config.getKmfServer();
        TrustManagerFactory tmfServer = config.getTmfServer();
        // client TLS
        KeyManagerFactory kmfClient = config.getKmfClient();
        TrustManagerFactory tmfClient = config.getTmfClient();
        // empty trust store
        TrustManagerFactory tmfEmpty = config.getTmfEmpty();


        BindableService[] serviceImpls = getServerImpls();
        String tlsProtocalClient = "TLSv1.3";

        // test1: // test2: server verify client certificate, client verify server certificate (most restrictive)
        test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, kmfClient, tmfClient, overrideAuthority);

        // test2: server do NOT verify client certificate, client verify server certificate (common case)
        test2WayTLS(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, kmfClient, tmfClient, overrideAuthority);// client use its cert
        test2WayTLS(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, null, tmfClient, overrideAuthority);// client use JDK default cert

        // test3: server do NOT verify client certificate, client do NOT verify server certificate (plain socket)
        test2WayTLS(host, port, null, null, serviceImpls, loadBalancingTargetScheme, null, null, null, overrideAuthority);

        // test4: server do NOT verify client certificate, client verify server certificate via JDK default trust store (error expected)
        try {
            test2WayTLS(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, kmfClient, null, overrideAuthority);
        } catch (StatusRuntimeException ex) {
            Throwable cause = ExceptionUtils.getRootCause(ex);
            assert cause.getMessage().equals("unable to find valid certification path to requested target");
        }

        // test5: server verify client certificate, client do NOT verify server certificate (not possible)
        //test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, tlsProtocolClient, kmfClient, null, overrideAuthority);


        // test5: server does NOT trust client certificate, client trust server certificate
        try {
            test2WayTLS(host, port, kmfServer, tmfEmpty, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, kmfClient, tmfClient, overrideAuthority);
            throw new RuntimeException("Expected exception not thrown");
        } catch (StatusRuntimeException ex) {
            System.out.println("server does NOT trust client certificate - Expected exception: " + ex);
            //assertEquals(ex.getMessage(), "UNAVAILABLE: ssl exception");
            assert ex.getMessage().equals("UNAVAILABLE: ssl exception");
        }
        // test6: client does NOT trust server certificate, server trust server certificate
        try {
            test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, tlsProtocalClient, kmfClient, tmfEmpty, overrideAuthority);
        } catch (StatusRuntimeException ex) {
            System.out.println("client does NOT trust server certificate - Expected exception: " + ex);
            String receivedMessage = ex.getMessage();
            //assertTrue(receivedMessage.startsWith("UNAVAILABLE: io exception"));
            //assertEquals(receivedMessage, "UNAVAILABLE: io exception\nChannel Pipeline: [SslHandler#0, ProtocolNegotiators$ClientTlsHandler#0, WriteBufferingAndExceptionHandler#0, DefaultChannelPipeline$TailContext#0]");
            assert receivedMessage.equals("UNAVAILABLE: io exception\nChannel Pipeline: [SslHandler#0, ProtocolNegotiators$ClientTlsHandler#0, WriteBufferingAndExceptionHandler#0, DefaultChannelPipeline$TailContext#0]");
        }
    }

    /**
     * Run a gRPC server and client with two-way TLS.
     *
     * @param host                      the host for the server and client
     * @param port                      the port for the server and client
     * @param kmfServer                 KeyManagerFactory for the server
     * @param tmfServer                 TrustManagerFactory for the server
     * @param serviceImpls              array of BindableService implementations for the server
     * @param loadBalancingTargetScheme scheme for load balancing target
     * @param kmfClient                 KeyManagerFactory for the client
     * @param tmfClient                 TrustManagerFactory for the client
     * @param overrideAuthority         override authority for the client
     * @throws IOException if an I/O error occurs
     */
    public void test2WayTLS(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService[] serviceImpls, String loadBalancingTargetScheme, String tlsProtocalClient, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws IOException {
        // 1. run server if not null
        GrpcServer grpcServer = buildGrpcServer(host, port, kmfServer, tmfServer, serviceImpls);
        if (grpcServer != null) {
            StringBuilder startingMemo = new StringBuilder();
            grpcServer.start(false, startingMemo);
            System.out.println("gRPC Server started on " + host + ":" + port + " with " + startingMemo);
        }
        try {
            // 2. run client
            String loadBalancingPolicy = GrpcClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
            NettyChannelBuilder channelBuilder = buildGrpcClient(tlsProtocalClient, host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
            runClient(channelBuilder);
        } finally {
            if (grpcServer != null) {
                grpcServer.shutdown();
            }
        }

    }

    protected abstract void runClient(NettyChannelBuilder channelBuilder);


}

