package org.summerframework.nio.grpc;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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

    protected final static String ID = "";

    protected GRPCClientConfig() {
    }

    //1. TRC
    @Memo(title = "1. " + ID + "gRpc")
    @Config(key = ID + "gRpc.url")
    protected volatile URI uri;
    @Config(key = ID + "gRpc.ssl.Protocols", defaultValue = "TLSv1.3")//"TLSv1.2, TLSv1.3"
    protected String[] sslProtocols = {"TLSv1.3"};
    @Config(key = ID + "gRpc.ssl.ciphers", required = false)
    protected List ciphers;

    //2. TRC Client keystore
    @Memo(title = "2. " + ID + "gRpc Client keystore")
    @Config(key = ID + "gRpc.ssl.KeyStore", StorePwdKey = ID + "trc.ssl.KeyStorePwd",
            AliasKey = ID + "gRpc.ssl.KeyAlias", AliasPwdKey = ID + "trc.ssl.KeyPwd", required = false)
    protected volatile KeyManagerFactory kmf;

    //3. TRC Client truststore
    @Memo(title = "3. " + ID + "gRpc Client truststore")
    @Config(key = ID + "gRpc.ssl.TrustStore", StorePwdKey = ID + "trc.ssl.TrustStorePwd", required = false)
    protected volatile TrustManagerFactory tmf;
    @Config(key = ID + "gRpc.ssl.overrideAuthority", required = false)
    protected volatile String overrideAuthority;

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
