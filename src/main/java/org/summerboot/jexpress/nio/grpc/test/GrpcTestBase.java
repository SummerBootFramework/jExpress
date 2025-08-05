package org.summerboot.jexpress.nio.grpc.test;

import io.grpc.BindableService;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.nio.grpc.BootLoadBalancerProvider;
import org.summerboot.jexpress.nio.grpc.GRPCClientConfig;
import org.summerboot.jexpress.nio.grpc.GRPCServer;
import org.summerboot.jexpress.security.SSLUtil;
import org.summerboot.jexpress.security.auth.LDAPAuthenticator;
import org.summerboot.jexpress.util.ApplicationUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;


public abstract class GrpcTestBase {
    public static class TestConfig {
        private final String testName;
        private final int length;
        private String configDir = "src/test/resources/config/"; // "run/standalone_dev/configuration/";
        private File keyStore = new File(configDir + "keystore.p12").getAbsoluteFile();
        private File serverTrustStore = new File(configDir + "truststore_grpc_server.p12").getAbsoluteFile();
        private File clientTrustStore = new File(configDir + "truststore_grpc_client.p12").getAbsoluteFile();
        private File emptyTrustStore = new File(configDir + "truststore_empty.p12").getAbsoluteFile();
        private String serverKeyAlias;
        private String clientKeyAlias;
        private String overrideAuthority;


        private String keyStorePassword = "changeit";
        private String keyPassword = "changeit";
        private String trustStorePassword = "changeit";

        private BindableService[] serviceImpls;

        private String host = "localhost";
        private int port = 8425;
        private String loadBalancingTargetScheme = "grpc";

        public TestConfig(int length, BindableService... serviceImpls) {
            this("gRPC with TLS " + length, length, "src/test/resources/config/", serviceImpls);
        }

        public TestConfig(String testName, int length, BindableService... serviceImpls) {
            this(testName, length, "src/test/resources/config/", serviceImpls);
        }

        public TestConfig(String testName, int length, String configDir, BindableService... serviceImpls) {
            this.testName = testName;
            this.length = length;
            this.keyStore = new File(configDir + "keystore.p12").getAbsoluteFile();
            createIfNotExist("keystore.p12", this.keyStore);

            this.serverTrustStore = new File(configDir + "truststore_server.p12").getAbsoluteFile();
            createIfNotExist("truststore.p12", this.serverTrustStore);

            this.clientTrustStore = new File(configDir + "truststore_client.p12").getAbsoluteFile();
            createIfNotExist("truststore.p12", this.clientTrustStore);

            this.emptyTrustStore = new File(configDir + "truststore_empty.p12").getAbsoluteFile();
            createIfNotExist("truststore_empty.p12", this.emptyTrustStore);

            this.serverKeyAlias = "server2_" + length + ".jexpress.org";
            this.clientKeyAlias = "server3_" + length + ".jexpress.org";
            this.overrideAuthority = "server2." + length + ".jexpress.org";

            this.serviceImpls = serviceImpls;
        }

        private void createIfNotExist(String srcFileName, File destFile) {
            String location = destFile.getParent();
            String destFileName = destFile.getName();
            ClassLoader classLoader = this.getClass().getClassLoader();
            ApplicationUtil.createIfNotExist(location, classLoader, srcFileName, destFileName);
        }

        @Override
        public String toString() {
            return "TestConfig{" +
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

        public void setConfigDir(String configDir) {
            this.configDir = configDir;
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

        public BindableService[] getServiceImpls() {
            return serviceImpls;
        }

        public void setServiceImpls(BindableService[] serviceImpls) {
            this.serviceImpls = serviceImpls;
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

        public String getLoadBalancingTargetScheme() {
            return loadBalancingTargetScheme;
        }

        public void setLoadBalancingTargetScheme(String loadBalancingTargetScheme) {
            this.loadBalancingTargetScheme = loadBalancingTargetScheme;
        }
    }

    protected abstract BindableService[] getServerImpls();

    //@Test
    public void runTest() throws GeneralSecurityException, IOException {
        TestConfig[] testConfigs = buildDefaultTestConfigs();
        for (TestConfig config : testConfigs) {
            System.out.println("Testing with config: " + config);
            test2WayAuth(config);
        }
    }

    protected TestConfig[] buildDefaultTestConfigs() {
        BindableService[] serviceImpls = getServerImpls();
        return new TestConfig[]{
                new TestConfig(2048, serviceImpls),
                new TestConfig(4096, serviceImpls)
        };
    }

    protected void test2WayAuth(TestConfig config) throws GeneralSecurityException, IOException {
        String keyStore = config.getKeyStore().getAbsolutePath();
        String serverTrustStore = config.getServerTrustStore().getAbsolutePath();
        String clientTrustStore = config.getClientTrustStore().getAbsolutePath();
        String emptyTrustStore = config.getEmptyTrustStore().getAbsolutePath();
        String serverKeyAlias = config.getServerKeyAlias();
        String clientKeyAlias = config.getClientKeyAlias();
        String overrideAuthority = config.getOverrideAuthority();

        String keyStorePassword = config.getKeyStorePassword();
        String keyPassword = config.getKeyPassword();
        String trustStorePassword = config.getTrustStorePassword();
        BindableService[] serviceImpls = config.getServiceImpls();
        String host = config.getHost();
        int port = config.getPort();
        String loadBalancingTargetScheme = config.getLoadBalancingTargetScheme();
        // server
        KeyManagerFactory kmfServer = SSLUtil.buildKeyManagerFactory(keyStore, keyStorePassword.toCharArray(), serverKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfServer = SSLUtil.buildTrustManagerFactory(serverTrustStore, trustStorePassword.toCharArray());
        // client
        KeyManagerFactory kmfClient = SSLUtil.buildKeyManagerFactory(keyStore, keyStorePassword.toCharArray(), clientKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfClient = SSLUtil.buildTrustManagerFactory(clientTrustStore, trustStorePassword.toCharArray());
        // test: 2-way TLS
        runServerAndClient(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
        // test: client verify server certificate, server trust all client certificate
        runServerAndClient(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
        // test: server verify server certificate, client trust all client certificate
        runServerAndClient(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, null, overrideAuthority);
        // test: server and client trust all certificates
        runServerAndClient(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, kmfClient, null, overrideAuthority);


        // empty trust store
        TrustManagerFactory tmfEmpty = SSLUtil.buildTrustManagerFactory(emptyTrustStore, trustStorePassword.toCharArray());
        // test: server does NOT trust client certificate, client trust server certificate
        try {
            runServerAndClient(host, port, kmfServer, tmfEmpty, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
            throw new RuntimeException("Expected exception not thrown");
        } catch (StatusRuntimeException ex) {
            System.out.println("server does NOT trust client certificate - Expected exception: " + ex);
            //assertEquals(ex.getMessage(), "UNAVAILABLE: ssl exception");
            assert ex.getMessage().equals("UNAVAILABLE: ssl exception");
        }
        // test: client does NOT trust server certificate, server trust server certificate
        try {
            runServerAndClient(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfEmpty, overrideAuthority);
        } catch (StatusRuntimeException ex) {
            System.out.println("client does NOT trust server certificate - Expected exception: " + ex);
            String receivedMessage = ex.getMessage();
            //assertTrue(receivedMessage.startsWith("UNAVAILABLE: io exception"));
            //assertEquals(receivedMessage, "UNAVAILABLE: io exception\nChannel Pipeline: [SslHandler#0, ProtocolNegotiators$ClientTlsHandler#0, WriteBufferingAndExceptionHandler#0, DefaultChannelPipeline$TailContext#0]");
            assert receivedMessage.equals("UNAVAILABLE: io exception\nChannel Pipeline: [SslHandler#0, ProtocolNegotiators$ClientTlsHandler#0, WriteBufferingAndExceptionHandler#0, DefaultChannelPipeline$TailContext#0]");
        }
    }

    protected void runServerAndClient(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService[] serviceImpls, String loadBalancingTargetScheme, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws IOException {
        // 1. start server
        GRPCServer gRPCServer = buildTestServer(host, port, kmfServer, tmfServer, serviceImpls);
        StringBuilder startingMemo = new StringBuilder();
        gRPCServer.start(false, startingMemo);
        System.out.println("gRPC Server started on " + host + ":" + port + " with " + startingMemo);


        try {
            // 2. config client
            NettyChannelBuilder channelBuilder = builderTestClient(host, port, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
            runClient(channelBuilder);
        } finally {
            gRPCServer.shutdown();
        }

    }

    protected abstract void runClient(NettyChannelBuilder channelBuilder);

    protected GRPCServer buildTestServer(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService[] serviceImpls) throws IOException {
        // 1. config server
        ThreadPoolExecutor tpe = BootConfig.buildThreadPoolExecutor("");
        ServerInterceptor serverInterceptor = new LDAPAuthenticator();
        GRPCServer gRPCServer = new GRPCServer(host, port, kmfServer, tmfServer, tpe, true, false, null, serverInterceptor);

        ServerBuilder serverBuilder = gRPCServer.getServerBuilder();
        for (BindableService serviceImpl : serviceImpls) {
            serverBuilder.addService(serviceImpl);
        }

        // 2. start server
        StringBuilder startingMemo = new StringBuilder();
        gRPCServer.start(false, startingMemo);
        return gRPCServer;
    }

    protected NettyChannelBuilder builderTestClient(String host, int port, String loadBalancingTargetScheme, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws SSLException {
        int priority = 0;
        List<InetSocketAddress> loadBalancingServers = List.of(new InetSocketAddress(host, port));
        NameResolverProvider nameResolverProvider = new BootLoadBalancerProvider(loadBalancingTargetScheme, ++priority, loadBalancingServers);
        // register
        NameResolverRegistry nameResolverRegistry = NameResolverRegistry.getDefaultRegistry();
        nameResolverRegistry.register(nameResolverProvider);
        // init
        String policy = GRPCClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
        String target = nameResolverProvider.getDefaultScheme() + ":///";
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(target).defaultLoadBalancingPolicy(policy);
        // set SSL
        final SslContextBuilder sslBuilder = GrpcSslContexts.forClient();
        sslBuilder.keyManager(kmfClient);
        //
        if (tmfClient == null) {//ignore Server Certificate
            sslBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
        } else {
            sslBuilder.trustManager(tmfClient);
            if (overrideAuthority != null) {
                channelBuilder.overrideAuthority(overrideAuthority);
            }
        }
        GrpcSslContexts.configure(sslBuilder, SslProvider.OPENSSL);
        String[] tlsVersionProtocols = {"TLSv1.3"};//{"TLSv1.2", "TLSv1.3"};
        if (tlsVersionProtocols != null) {
            sslBuilder.protocols(tlsVersionProtocols);
        }
        SslContext sslContext = sslBuilder.build();
        channelBuilder.sslContext(sslContext).useTransportSecurity();
        return channelBuilder;
    }
}

