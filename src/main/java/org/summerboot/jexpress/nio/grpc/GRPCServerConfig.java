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
    @Config(key = ID + ".bindings", defaultValue = "0.0.0.0:8424")
    private volatile List<InetSocketAddress> bindingAddresses;
    @Config(key = ID + ".autostart", defaultValue = "true")
    private volatile boolean autoStart;

    @Config(key = ID + ".pool.BizExecutor.mode", defaultValue = "CPU",
            desc = "valid value = CPU (default), IO, Mixed")
    private volatile ThreadingMode threadingMode = ThreadingMode.CPU;

    @Config(key = ID + ".pool.coreSize")
    private volatile int poolCoreSize = availableProcessors + 1;

    @Config(key = ID + ".pool.maxSize")
    private volatile int poolMaxSizeMaxSize = availableProcessors + 1;

    @Config(key = ID + ".pool.queueSize", defaultValue = "" + Integer.MAX_VALUE)//2147483647
    private volatile int poolQueueSize = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds", defaultValue = "60")
    private volatile long keepAliveSeconds = 60;

    //2. TRC (The Remote Callee) keystore
    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = ID + ".ssl.KeyStore", StorePwdKey = ID + ".ssl.KeyStorePwd",
            AliasKey = ID + ".ssl.KeyAlias", AliasPwdKey = ID + ".ssl.KeyPwd")
    @JsonIgnore
    protected volatile KeyManagerFactory kmf;

    //3. TRC (The Remote Callee) truststore
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = ID + ".ssl.TrustStore", StorePwdKey = ID + ".ssl.TrustStorePwd")
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    @Override
    protected void reset() {
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        int cpuCoreSize = Runtime.getRuntime().availableProcessors();
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
