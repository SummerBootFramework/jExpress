package org.summerboot.jexpress.nio.grpc;

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
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.security.SSLUtil;
import org.summerboot.jexpress.security.auth.AuthenticatorListener;
import org.summerboot.jexpress.security.auth.BootAuthenticator;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.ApplicationUtil;

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
public abstract class GRPCTestHelper {

    public static class GRPCSimpleClient {
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
        private String loadBalancingPolicy = GRPCClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
        private String loadBalancingTargetScheme = "grpc";

        public GRPCSimpleClient(int length) throws GeneralSecurityException, IOException {
            this("GRPCTestHelper " + length, length, "src/test/resources/config/");
        }

        public GRPCSimpleClient(String testName, int length) throws GeneralSecurityException, IOException {
            this(testName, length, "src/test/resources/config/");
        }

        public GRPCSimpleClient(String testName, int length, String configDir) throws GeneralSecurityException, IOException {
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
            kmfServer = SSLUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), serverKeyAlias, keyPassword.toCharArray());
            tmfServer = SSLUtil.buildTrustManagerFactory(serverTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
            // client TLS
            kmfClient = SSLUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), clientKeyAlias, keyPassword.toCharArray());
            tmfClient = SSLUtil.buildTrustManagerFactory(clientTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
            // untrusted TLS
            tmfEmpty = SSLUtil.buildTrustManagerFactory(emptyTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
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
            return "GRPCSimpleClient{" +
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
    }

    public static GRPCServer buildGRPCServer(GRPCSimpleClient config, BindableService... serviceImps) throws GeneralSecurityException, IOException {
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

        KeyManagerFactory kmfServer = SSLUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), serverKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfServer = SSLUtil.buildTrustManagerFactory(serverTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
        return buildGRPCServer(host, port, kmfServer, tmfServer, serviceImps);
    }

    public static GRPCServer buildGRPCServer(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService... serviceImps) throws IOException {
        if (serviceImps == null || serviceImps.length == 0) {
            return null;
        }
        // 1. config server
        ThreadPoolExecutor tpe = BootConfig.buildThreadPoolExecutor("");
        ServerInterceptor serverInterceptor = new BootAuthenticator<>() {
            @Override
            protected Caller authenticate(String usename, String password, Object metaData, AuthenticatorListener listener, SessionContext context) throws NamingException {
                return null;
            }
        };
        GRPCServer gRPCServer = new GRPCServer(host, port, kmfServer, tmfServer, tpe, true, false, null, serverInterceptor);

        ServerBuilder serverBuilder = gRPCServer.getServerBuilder();
        for (BindableService serviceImpl : serviceImps) {
            serverBuilder.addService(serviceImpl);
        }

        // 2. start server
        StringBuilder startingMemo = new StringBuilder();
        gRPCServer.start(false, startingMemo);
        return gRPCServer;
    }

    public static NettyChannelBuilder buildGRPCClient(GRPCSimpleClient config) throws IOException, GeneralSecurityException {
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
        KeyManagerFactory kmfClient = SSLUtil.buildKeyManagerFactory(keyStore.getAbsolutePath(), keyStorePassword.toCharArray(), clientKeyAlias, keyPassword.toCharArray());
        TrustManagerFactory tmfClient = SSLUtil.buildTrustManagerFactory(clientTrustStore.getAbsolutePath(), trustStorePassword.toCharArray());
        // client
        return buildGRPCClient(host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
    }

    public static NettyChannelBuilder buildGRPCClient(String host, int port, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws SSLException {
        String loadBalancingPolicy = GRPCClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
        String loadBalancingTargetScheme = "grpc";
        return buildGRPCClient(host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
    }

    public static NettyChannelBuilder buildGRPCClient(String host, int port, String loadBalancingPolicy, String loadBalancingTargetScheme, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws SSLException {
        int priority = 0;
        List<InetSocketAddress> loadBalancingServers = List.of(new InetSocketAddress(host, port));
        NameResolverProvider nameResolverProvider = new BootLoadBalancerProvider(loadBalancingTargetScheme, ++priority, loadBalancingServers);
        // register
        NameResolverRegistry nameResolverRegistry = NameResolverRegistry.getDefaultRegistry();
        nameResolverRegistry.register(nameResolverProvider);
        // init
        String target = nameResolverProvider.getDefaultScheme() + ":///";
        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(target).defaultLoadBalancingPolicy(loadBalancingPolicy);
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


    //@Test
    public void test2WayTLS() throws GeneralSecurityException, IOException {
        GRPCSimpleClient[] testConfigs = buildDefaultTestConfigs();
        for (GRPCSimpleClient config : testConfigs) {
            System.out.println("Testing with config: " + config);
            test2WayTLS(config);
        }
    }

    protected GRPCSimpleClient[] buildDefaultTestConfigs() throws GeneralSecurityException, IOException {
        return new GRPCSimpleClient[]{
                new GRPCSimpleClient(2048),
                new GRPCSimpleClient(4096)
        };
    }

    protected abstract BindableService[] getServerImpls();

    public void test2WayTLS(GRPCSimpleClient config) throws IOException {
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

        // test: 2-way TLS
        test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
        // test: client verify server certificate, server trust all client certificate
        test2WayTLS(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
        // test: server verify server certificate, client trust all client certificate
        test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, null, overrideAuthority);
        // test: server and client trust all certificates
        test2WayTLS(host, port, kmfServer, null, serviceImpls, loadBalancingTargetScheme, kmfClient, null, overrideAuthority);
        // test: server does NOT trust client certificate, client trust server certificate
        try {
            test2WayTLS(host, port, kmfServer, tmfEmpty, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
            throw new RuntimeException("Expected exception not thrown");
        } catch (StatusRuntimeException ex) {
            System.out.println("server does NOT trust client certificate - Expected exception: " + ex);
            //assertEquals(ex.getMessage(), "UNAVAILABLE: ssl exception");
            assert ex.getMessage().equals("UNAVAILABLE: ssl exception");
        }
        // test: client does NOT trust server certificate, server trust server certificate
        try {
            test2WayTLS(host, port, kmfServer, tmfServer, serviceImpls, loadBalancingTargetScheme, kmfClient, tmfEmpty, overrideAuthority);
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
    public void test2WayTLS(String host, int port, KeyManagerFactory kmfServer, TrustManagerFactory tmfServer, BindableService[] serviceImpls, String loadBalancingTargetScheme, KeyManagerFactory kmfClient, TrustManagerFactory tmfClient, String overrideAuthority) throws IOException {
        // 1. run server if not null
        GRPCServer gRPCServer = buildGRPCServer(host, port, kmfServer, tmfServer, serviceImpls);
        if (gRPCServer != null) {
            StringBuilder startingMemo = new StringBuilder();
            gRPCServer.start(false, startingMemo);
            System.out.println("gRPC Server started on " + host + ":" + port + " with " + startingMemo);
        }
        try {
            // 2. run client
            String loadBalancingPolicy = GRPCClientConfig.LoadBalancingPolicy.PICK_FIRST.getValue();
            NettyChannelBuilder channelBuilder = buildGRPCClient(host, port, loadBalancingPolicy, loadBalancingTargetScheme, kmfClient, tmfClient, overrideAuthority);
            runClient(channelBuilder);
        } finally {
            if (gRPCServer != null) {
                gRPCServer.shutdown();
            }
        }

    }

    protected abstract void runClient(NettyChannelBuilder channelBuilder);


}

