package org.summerboot.jexpress.integration.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttPingSender;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.security.SSLConnectionFactory;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class MqttClientConfig extends BootConfig {
    public static void main(String[] args) {
        class a extends MqttClientConfig {
        }
        String t = generateTemplate(a.class);
        System.out.println(t);
    }

    protected MqttClientConfig() {
    }

    protected static final String FILENAME_TRUSTSTORE_4CLIENT = "truststore_mqtt_client.p12";

    protected final static String ID = "MQTT.client";

    @ConfigHeader(title = "1. " + ID + " server endpoint",
            desc = "protocol: tcp/ssl/local/ws/wss",
            format = "protocol://servername:port",
            example = "ssl://localhost:8883")// tcp/ssl/local/ws/wss
    @Config(key = ID + ".serverURI", predefinedValue = "ssl://localhost:8883", required = true)
    protected volatile String serverURI;


    @Config(key = ID + ".ssl.Protocol", defaultValue = "TLSv1.3")// "TLSv1.2, TLSv1.3"
    protected String sslProtocol;

    // 2. keystore
    protected static final String KEY_kmf_key = ID + ".ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = ID + ".ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = ID + ".ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = ID + ".ssl.KeyPwd";

    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = DESC_KMF,
            callbackMethodName4Dump = "generateTemplate_keystore")
    //@JsonIgnore
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + DEFAULT_DEC_VALUE);
        sb.append(KEY_kmf_AliasKey + "=server1_2048.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    // 3. truststore
    protected static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=" + FILENAME_TRUSTSTORE_4CLIENT + "\n");
        sb.append(KEY_tmf_StorePwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    @Config(key = ID + ".VerifyHostname", defaultValue = "true")
    protected volatile boolean verifyHostname;

    @JsonIgnore
    protected volatile SocketFactory socketFactory;

    @ConfigHeader(title = "4. " + ID + " user credential")
    @Config(key = ID + ".clientId")
    protected volatile String clientId;

    @Config(key = ID + ".username")
    protected volatile String username;

    @JsonIgnore
    @Config(key = ID + ".password", validate = Config.Validate.Encrypted)
    protected volatile String password;

    @ConfigHeader(title = "5. " + ID + " messaging")
    @Config(key = ID + ".defaultQoS", defaultValue = "2", desc = "0=At most once, Fire-and-Forget (QoS 0), 1=At least once (QoS 1), 2=Exactly once (QoS 2)")
    protected volatile int defaultQoS;


    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_KEYSTORE, FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_TRUSTSTORE_4CLIENT);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        if (kmf != null) {
            socketFactory = new SSLConnectionFactory(kmf, tmf, sslProtocol);//.getSSLSocketFactory();
        }
    }

    @Override
    public void shutdown() {
    }

    public MqttAsyncClient build() throws MqttException {
        return new MqttAsyncClient(serverURI, clientId, null, null, null);
    }


    public MqttAsyncClient build(MqttClientPersistence persistence, MqttPingSender pingSender, ScheduledExecutorService executorService) throws MqttException {
        return new MqttAsyncClient(serverURI, clientId, persistence, pingSender, executorService);
    }

    public MqttConnectionOptions buildConnectionOptions() {
        return buildConnectionOptions(this.username, this.password, this.socketFactory);
    }

    public MqttConnectionOptions buildConnectionOptions(String username, String password) {
        return buildConnectionOptions(username, password, this.socketFactory);
    }

    public MqttConnectionOptions buildConnectionOptions(String username, String password, SocketFactory socketFactory) {
        MqttConnectionOptions connOpts = new MqttConnectionOptions();
        if (StringUtils.isNotBlank(username)) {
            connOpts.setUserName(username);
            connOpts.setPassword(password.getBytes());
        }
        if (socketFactory != null) {
            connOpts.setSocketFactory(socketFactory);
//            if (tmf == null) {
//                connOpts.setHttpsHostnameVerificationEnabled(false);
//            } else {
//                connOpts.setHttpsHostnameVerificationEnabled(isVerifyHostname());
//            }
            connOpts.setHttpsHostnameVerificationEnabled(isVerifyHostname());
        }
        return connOpts;
    }

    public void shutdown(MqttAsyncClient asyncClient) throws MqttException {
        if (asyncClient != null) {
            asyncClient.disconnect();
            asyncClient.close(true);
        }
    }


    public String getServerURI() {
        return serverURI;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

    public boolean isVerifyHostname() {
        return verifyHostname;
    }

    public SocketFactory getSocketFactory() {
        return socketFactory;
    }

    public String getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getDefaultQoS() {
        return defaultQoS;
    }

}
