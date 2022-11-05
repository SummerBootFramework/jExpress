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
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.summerboot.jexpress.boot.config.BootConfig;
import static org.summerboot.jexpress.boot.config.BootConfig.generateTemplate;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.Memo;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class GRPCServerConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(GRPCServerConfig.class);
        System.out.println(t);
    }
    public static final GRPCServerConfig CFG = new GRPCServerConfig();

    protected final static String ID = "gRpc.server";

    protected GRPCServerConfig() {
    }

    public enum ThreadingMode {
        CPU_Bound, IO_Bound, Mixed
    }

    //1. gRPC server config
    @Memo(title = "1. " + ID + " provider")
    @Config(key = ID + ".binding.addr")
    private volatile String bindingAddr;
    @Config(key = ID + ".binding.port")
    private volatile int bindingPort;

    @Config(key = ID + ".pool.BizExecutor.mode", defaultValue = "CPU_Bound",
            desc = "valid value = CPU (default), IO, Mixed")
    private volatile ThreadingMode threadingMode = ThreadingMode.CPU_Bound;

    @Config(key = ID + ".pool.coreSize", required = false)
    private volatile int poolCoreSize;

    @Config(key = ID + ".pool.maxSize", required = false)
    private volatile int poolMaxSizeMaxSize;

    @Config(key = ID + ".pool.queueSize", defaultValue = "2147483647")
    private volatile int poolQueueSize = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds", defaultValue = "60")
    private volatile long keepAliveSeconds = 60;

    @Config(key = "nio.server.health.InspectionIntervalSeconds", defaultValue = "5")
    private volatile int healthInspectionIntervalSeconds = 5;

    //2. TRC (The Remote Callee) keystore
    @Memo(title = "2. " + ID + " keystore")
    @Config(key = ID + ".ssl.KeyStore", StorePwdKey = ID + ".ssl.KeyStorePwd",
            AliasKey = ID + ".ssl.KeyAlias", AliasPwdKey = ID + ".ssl.KeyPwd", required = false)
    @JsonIgnore
    protected volatile KeyManagerFactory kmf;

    //3. TRC (The Remote Callee) truststore
    @Memo(title = "3. " + ID + " truststore")
    @Config(key = ID + ".ssl.TrustStore", StorePwdKey = ID + ".ssl.TrustStorePwd", required = false)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    @Config(key = ID + ".ssl.overrideAuthority", required = false)

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        int cpuCoreSize = Runtime.getRuntime().availableProcessors();
        switch (threadingMode) {
            case CPU_Bound:// use CPU_Bound core + 1 when application is CPU_Bound bound
                poolCoreSize = cpuCoreSize + 1;
                poolMaxSizeMaxSize = poolCoreSize;
                break;
            case IO_Bound:// use CPU_Bound core x 2 + 1 when application is I/O bound
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

    public String getBindingAddr() {
        return bindingAddr;
    }

    public int getBindingPort() {
        return bindingPort;
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

    public int getHealthInspectionIntervalSeconds() {
        return healthInspectionIntervalSeconds;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

}