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
        CPU_Bound, IO_Bound, Mixed
    }

    private final int availableProcessors = Runtime.getRuntime().availableProcessors();

    //1. gRPC server config
    @ConfigHeader(title = "1. " + ID + " provider")
    @Config(key = ID + ".binding.addr")
    private volatile String bindingAddr = "127.0.0.1";
    @Config(key = ID + ".binding.port")
    private volatile int bindingPort = 8081;

    @Config(key = ID + ".pool.BizExecutor.mode",
            desc = "valid value = CPU (default), IO, Mixed")
    private volatile ThreadingMode threadingMode = ThreadingMode.CPU_Bound;

    @Config(key = ID + ".pool.coreSize")
    private volatile int poolCoreSize = availableProcessors + 1;

    @Config(key = ID + ".pool.maxSize")
    private volatile int poolMaxSizeMaxSize = availableProcessors + 1;

    @Config(key = ID + ".pool.queueSize")//2147483647
    private volatile int poolQueueSize = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds")
    private volatile long keepAliveSeconds = 60;

    @Config(key = "nio.server.health.InspectionIntervalSeconds")
    private volatile int healthInspectionIntervalSeconds = 5;

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

    @Config(key = ID + ".ssl.overrideAuthority")

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
