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
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.config.BootConfig;
import static org.summerboot.jexpress.boot.config.BootConfig.generateTemplate;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@ImportResource(SummerApplication.CFG_GRPC)
public class GRPCServerConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(GRPCServerConfig.class);
        System.out.println(t);
    }

    protected final static String ID = "gRpc.server";

    public static final GRPCServerConfig cfg = new GRPCServerConfig();

    protected GRPCServerConfig() {
    }

    public enum ThreadingMode {
        CPU, IO, Mixed
    }

    private final int availableProcessors = Runtime.getRuntime().availableProcessors();

    //1. gRPC server config
    @ConfigHeader(title = "1. " + ID + " Network Listeners",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8424, 127.0.0.1:8424, 0.0.0.0:8424")
    @Config(key = ID + ".bindings", predefinedValue = "0.0.0.0:8424, 0.0.0.0:8425", required = true)
    private volatile List<InetSocketAddress> bindingAddresses;
    @Config(key = ID + ".autostart", defaultValue = "true")
    private volatile boolean autoStart;

    @Config(key = ID + ".pool.BizExecutor.mode", defaultValue = "Mixed",
            desc = "valid value = CPU (default), IO, Mixed")
    private volatile ThreadingMode threadingMode = ThreadingMode.Mixed;

    @Config(key = ID + ".pool.coreSize", predefinedValue = "0",
            desc = "coreSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int poolCoreSize = availableProcessors * 2 + 1;

    @Config(key = ID + ".pool.maxSize", predefinedValue = "0",
            desc = "maxSize 0 = current computer/VM's available processors x 2 + 1")
    private volatile int poolMaxSizeMaxSize = availableProcessors * 2 + 1;

    @Config(key = ID + ".pool.queueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    private volatile int poolQueueSize = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds", defaultValue = "60")
    private volatile long keepAliveSeconds = 60;

    //2. TRC (The Remote Callee) keystore
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
        sb.append(KEY_kmf_key + "=server_keystore.p12\n");
        sb.append(KEY_kmf_StorePwdKey + "=DEC(changeit)\n");
        sb.append(KEY_kmf_AliasKey + "=server2_4096.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + "=DEC(changeit)\n");
        generateTemplate = true;
    }

    //3. TRC (The Remote Callee) truststore    
    private static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    private static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append(KEY_tmf_key + "=truststore_4server.p12\n");
        sb.append(KEY_tmf_StorePwdKey + "=DEC(changeit)\n");
        generateTemplate = true;
    }

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist("server_keystore.p12");
        createIfNotExist("truststore_4server.p12");
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        int cpuCoreSize = availableProcessors;
        switch (threadingMode) {
            case CPU:// use CPU_Bound core + 1 when application is CPU_Bound bound
                poolCoreSize = cpuCoreSize + 1;
                poolMaxSizeMaxSize = poolCoreSize;
                break;
            case IO:// use CPU_Bound core x 2 + 1 when application is I/O bound
                poolCoreSize = cpuCoreSize * 2 + 1;
                poolMaxSizeMaxSize = poolCoreSize;
                break;
            case Mixed:// manual config is required when it is mixed
                if (poolCoreSize < 1) {
                    poolCoreSize = cpuCoreSize * 2 + 1;
                }
                if (poolMaxSizeMaxSize < 1) {
                    poolMaxSizeMaxSize = cpuCoreSize * 2 + 1;
                }
                if (poolMaxSizeMaxSize < poolCoreSize) {
                    poolMaxSizeMaxSize = poolCoreSize;
                }
                break;
        }
    }

    @Override
    public void shutdown() {
    }

    public List<InetSocketAddress> getBindingAddresses() {
        return bindingAddresses;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public ThreadingMode getThreadingMode() {
        return threadingMode;
    }

    public int getPoolCoreSize() {
        return poolCoreSize;
    }

    public int getPoolMaxSizeMaxSize() {
        return poolMaxSizeMaxSize;
    }

    public int getPoolQueueSize() {
        return poolQueueSize;
    }

    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

}
