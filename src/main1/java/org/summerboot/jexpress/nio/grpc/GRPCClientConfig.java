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
package org.summerboot.jexpress.nio.grpc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.grpc.NameResolverProvider;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
//@ImportResource(SummerApplication.CFG_GRPCCLIENT)
abstract public class GRPCClientConfig extends BootConfig {

    public static void main(String[] args) {
        class a extends GRPCClientConfig {
        }
        String t = generateTemplate(a.class);
        System.out.println(t);
    }

    protected final static String ID = "gRpc.client";

    protected GRPCClientConfig() {
    }

    //1. NIO Network Listeners
    @ConfigHeader(title = "1. " + ID + " provider",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8424, 127.0.0.1:8425, 0.0.0.0:8426")
    @Config(key = ID + ".LoadBalancing.servers", predefinedValue = "0.0.0.0:8424, 0.0.0.0:8425", required = false)
    private volatile List<InetSocketAddress> loadBalancingServers;

    @Config(key = ID + ".LoadBalancing.policy", defaultValue = "ROUND_ROBIN", desc = "available options: ROUND_ROBIN, PICK_FIRST")
    private volatile GRPCClient.LoadBalancingPolicy loadBalancingPolicy;

    private volatile NameResolverProvider nameResolverProvider;

    //1. gRPC connection
    @Config(key = ID + ".target.url", defaultValue = "grpc:///",
            desc = "grpc:///\n"
            + "grpc://127.0.0.1:8424\n"
            + "unix:/tmp/grpcsrver.socket")
    protected volatile URI uri;

    @Config(key = ID + ".ssl.Protocols", defaultValue = "TLSv1.3")// "TLSv1.2, TLSv1.3"
    protected String[] sslProtocols;
    @Config(key = ID + ".ssl.ciphers")
    protected List ciphers;

    //2. TRC (The Remote Caller) keystore    
    private static final String KEY_kmf_key = ID + ".ssl.KeyStore";
    private static final String KEY_kmf_StorePwdKey = ID + ".ssl.KeyStorePwd";
    private static final String KEY_kmf_AliasKey = ID + ".ssl.KeyAlias";
    private static final String KEY_kmf_AliasPwdKey = ID + ".ssl.KeyPwd";

    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF,
            callbackMethodName4Dump = "generateTemplate_keystore")
    //@JsonIgnore
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
        sb.append(KEY_kmf_AliasKey + "=server3_4096.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
        generateTemplate = true;
    }

    //3. TRC (The Remote Caller) truststore
    private static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    private static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=" + FILENAME_TRUSTSTORE_4CLIENT + "\n");
        sb.append(KEY_tmf_StorePwdKey + "=DEC(" + BootConstant.DEFAULT_ADMIN_MM + ")\n");
        generateTemplate = true;
    }

    @Config(key = ID + ".ssl.overrideAuthority", predefinedValue = "server2.4096.jexpress.org",
            desc = "NOT for PRODUCTION! Set server certificate DNS name here when server is not yet running on its certificate Subject Alternative Names (SAN)")
    protected volatile String overrideAuthority;

    @JsonIgnore
    protected volatile NettyChannelBuilder channelBuilder;

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        loadBalancingServers = null;
        nameResolverProvider = null;
        channelBuilder = null;
        createIfNotExist(FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_TRUSTSTORE_4CLIENT);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        if (loadBalancingServers != null && !loadBalancingServers.isEmpty()) {
            nameResolverProvider = new BootLoadBalancerProvider(uri.getScheme(), loadBalancingServers);
        }
        channelBuilder = GRPCClient.getNettyChannelBuilder(nameResolverProvider, loadBalancingPolicy, uri, kmf, tmf, overrideAuthority, ciphers, sslProtocols);
    }

    @Override
    public void shutdown() {
    }

    public List<InetSocketAddress> getLoadBalancingServers() {
        return loadBalancingServers;
    }

    public GRPCClient.LoadBalancingPolicy getLoadBalancingPolicy() {
        return loadBalancingPolicy;
    }

    public NameResolverProvider getNameResolverProvider() {
        return nameResolverProvider;
    }

    public URI getUri() {
        return uri;
    }

    public String[] getSslProtocols() {
        return sslProtocols;
    }

    public List getCiphers() {
        return ciphers;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public String getOverrideAuthority() {
        return overrideAuthority;
    }

    public NettyChannelBuilder getChannelBuilder() {
        return channelBuilder;
    }

}
