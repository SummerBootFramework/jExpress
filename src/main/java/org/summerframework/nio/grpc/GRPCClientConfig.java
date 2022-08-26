package org.summerframework.nio.grpc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class GRPCClientConfig extends AbstractSummerBootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(GRPCClientConfig.class);
        System.out.println(t);
    }
    public static final GRPCClientConfig CFG = new GRPCClientConfig();

    protected final static String ID = "gRpc.client";

    protected GRPCClientConfig() {
    }

    //1. gRPC
    @Memo(title = "1. " + ID + " provider")
    @Config(key = ID + ".url")
    protected volatile URI uri;
    @Config(key = ID + ".ssl.Protocols", defaultValue = "TLSv1.3")//"TLSv1.2, TLSv1.3"
    protected String[] sslProtocols = {"TLSv1.3"};
    @Config(key = ID + ".ssl.ciphers", required = false)
    protected List ciphers;

    //2. gRPC Client keystore
    @Memo(title = "2. " + ID + " keystore")
    @Config(key = ID + ".ssl.KeyStore", StorePwdKey = ID + ".ssl.KeyStorePwd",
            AliasKey = ID + ".ssl.KeyAlias", AliasPwdKey = ID + ".ssl.KeyPwd", required = false)
    @JsonIgnore
    protected volatile KeyManagerFactory kmf;

    //3. gRPC Client truststore
    @Memo(title = "3. " + ID + " truststore")
    @Config(key = ID + ".ssl.TrustStore", StorePwdKey = ID + ".ssl.TrustStorePwd", required = false)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;
    @Config(key = ID + ".ssl.overrideAuthority", required = false)
    protected volatile String overrideAuthority;

    @JsonIgnore
    protected volatile NettyChannelBuilder channelBuilder;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        channelBuilder = GRPCClient.getNettyChannelBuilder(uri, kmf, tmf, overrideAuthority, ciphers, sslProtocols);
    }

    @Override
    public void shutdown() {
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
