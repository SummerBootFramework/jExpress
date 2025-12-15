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
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.util.GeoIpUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
//@ImportResource(BootConstant.FILE_CFG_GRPC)
public class GRPCServerConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(GRPCServerConfig.class);
        System.out.println(t);
    }

    protected static final String FILENAME_TRUSTSTORE_4SERVER = "truststore_grpc_server.p12";
    protected final static String ID = "gRpc.server";

    public static final GRPCServerConfig cfg = new GRPCServerConfig();

    protected GRPCServerConfig() {
    }

    @Override
    protected void reset() {
        tpeCore = BootConstant.CPU_CORE * 2 + 1;
        tpeMax = BootConstant.CPU_CORE * 2 + 1;
    }

    //1. gRPC server config
    @ConfigHeader(title = "1. " + ID + " Network Listeners",
            format = "ip1:port1, ip2:port2, ..., ipN:portN",
            example = "192.168.1.10:8424, 127.0.0.1:8424, 0.0.0.0:8424")
    @Config(key = ID + ".bindings", predefinedValue = "0.0.0.0:8424, 0.0.0.0:8425", required = true)
    protected volatile List<InetSocketAddress> bindingAddresses;
    @Config(key = ID + ".autostart", defaultValue = "true")
    protected volatile boolean autoStart;
    @Config(key = ID + ".idle.threshold.second", defaultValue = "59", desc = "make it prime number when you have both NIO and gRPC server running")
    protected volatile int idleThresholdSecond;

    @Config(key = ID + ".CallerAddressFilter.option", defaultValue = "String", desc = "valid value = String, HostString, HostName, AddressStirng, HostAddress, AddrHostName, CanonicalHostName")
    protected volatile GeoIpUtil.CallerAddressFilterOption CallerAddressFilterOption = GeoIpUtil.CallerAddressFilterOption.String;
    @Config(key = ID + ".CallerAddressFilter.Whitelist", desc = "Whitelist in CSV format, example: 127.0.0.1, 192\\\\.168\\\\.1\\\\.")
    protected volatile Set<String> callerAddressFilterWhitelist;
    @Config(key = ID + ".CallerAddressFilter.Blacklist", desc = "Blacklist in CSV format, example: 10.1.1.40, 192\\\\.168\\\\.2\\\\.")
    protected volatile Set<String> callerAddressFilterBlacklist;

    @Config(key = ID + ".pool.BizExecutor.mode", defaultValue = "VirtualThread",
            desc = "valid value = VirtualThread (default for Java 21+), CPU, IO and Mixed (default for old Java) \n use CPU core + 1 when application is CPU bound\n"
                    + "use CPU core x 2 + 1 when application is I/O bound\n"
                    + "need to find the best value based on your performance test result when nio.server.BizExecutor.mode=Mixed")
    protected volatile ThreadingMode tpeThreadingMode = ThreadingMode.VirtualThread;

    @Config(key = ID + ".pool.coreSize", predefinedValue = "0",
            desc = "coreSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int tpeCore = BootConstant.CPU_CORE * 2 + 1;

    @Config(key = ID + ".pool.maxSize", predefinedValue = "0",
            desc = "maxSize 0 = current computer/VM's available processors x 2 + 1")
    protected volatile int tpeMax = BootConstant.CPU_CORE * 2 + 1;

    @Config(key = ID + ".pool.queueSize", defaultValue = "" + Integer.MAX_VALUE,
            desc = "The waiting list size when the pool is full")
    protected volatile int tpeQueue = Integer.MAX_VALUE;

    @Config(key = ID + ".pool.keepAliveSeconds", defaultValue = "60")
    protected volatile long tpeKeepAliveSeconds = 60;

    @Config(key = ID + ".pool.prestartAllCoreThreads", defaultValue = "false")
    protected boolean prestartAllCoreThreads = false;

    @Config(key = ID + ".pool.allowCoreThreadTimeOut", defaultValue = "false")
    protected boolean allowCoreThreadTimeOut = false;

    protected ThreadPoolExecutor tpe = null;

    //2. TRC (The Remote Callee) keystore
    protected static final String KEY_kmf_key = ID + ".ssl.KeyStore";
    protected static final String KEY_kmf_StorePwdKey = ID + ".ssl.KeyStorePwd";
    protected static final String KEY_kmf_AliasKey = ID + ".ssl.KeyAlias";
    protected static final String KEY_kmf_AliasPwdKey = ID + ".ssl.KeyPwd";

    @ConfigHeader(title = "2. " + ID + " keystore")
    @Config(key = KEY_kmf_key, StorePwdKey = KEY_kmf_StorePwdKey, AliasKey = KEY_kmf_AliasKey, AliasPwdKey = KEY_kmf_AliasPwdKey,
            desc = "Path to key store file. Use SSL/TLS when keystore is provided, otherwise use plain socket",
            callbackMethodName4Dump = "generateTemplate_keystore")
    //@JsonIgnore
    protected volatile KeyManagerFactory kmf;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(KEY_kmf_key + "=" + FILENAME_KEYSTORE + "\n");
        sb.append(KEY_kmf_StorePwdKey + DEFAULT_DEC_VALUE);
        sb.append(KEY_kmf_AliasKey + "=server2_4096.jexpress.org\n");
        sb.append(KEY_kmf_AliasPwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    //3. TRC (The Remote Callee) truststore    
    protected static final String KEY_tmf_key = ID + ".ssl.TrustStore";
    protected static final String KEY_tmf_StorePwdKey = ID + ".ssl.TrustStorePwd";
    @ConfigHeader(title = "3. " + ID + " truststore")
    @Config(key = KEY_tmf_key, StorePwdKey = KEY_tmf_StorePwdKey, callbackMethodName4Dump = "generateTemplate_truststore",
            desc = DESC_TMF_SERVER)
    @JsonIgnore
    protected volatile TrustManagerFactory tmf;

    protected void generateTemplate_truststore(StringBuilder sb) {
        sb.append("#" + KEY_tmf_key + "=" + FILENAME_TRUSTSTORE_4SERVER + "\n");
        sb.append("#" + KEY_tmf_StorePwdKey + DEFAULT_DEC_VALUE);
        generateTemplate = true;
    }

    @Override
    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_KEYSTORE);
        createIfNotExist(FILENAME_SRC_TRUSTSTORE, FILENAME_TRUSTSTORE_4SERVER);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws IOException {
        // pre-compile regexes for whitelist and blacklist
        if (callerAddressFilterWhitelist != null) {
            for (String regex : callerAddressFilterWhitelist) {
                GeoIpUtil.matches("", regex);
            }
        }
        if (callerAddressFilterBlacklist != null) {
            for (String regex : callerAddressFilterBlacklist) {
                GeoIpUtil.matches("", regex);
            }
        }

        tpe = buildThreadPoolExecutor(tpe, "Netty-gRPC.Biz", tpeThreadingMode,
                tpeCore, tpeMax, tpeQueue, tpeKeepAliveSeconds, null,
                prestartAllCoreThreads, allowCoreThreadTimeOut, true);
    }

    @Override
    public void shutdown() {
        if (tpe != null && !tpe.isShutdown()) {
            tpe.shutdown();
        }
    }

    public List<InetSocketAddress> getBindingAddresses() {
        return bindingAddresses;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public int getIdleThresholdSecond() {
        return idleThresholdSecond;
    }

    public GeoIpUtil.CallerAddressFilterOption getCallerAddressFilterOption() {
        return CallerAddressFilterOption;
    }

    public Set<String> getCallerAddressFilterWhitelist() {
        return callerAddressFilterWhitelist;
    }

    public Set<String> getCallerAddressFilterBlacklist() {
        return callerAddressFilterBlacklist;
    }

    public ThreadingMode getTpeThreadingMode() {
        return tpeThreadingMode;
    }

    public int getTpeCore() {
        return tpeCore;
    }

    public int getTpeMax() {
        return tpeMax;
    }

    public int getTpeQueue() {
        return tpeQueue;
    }

    public long getTpeKeepAliveSeconds() {
        return tpeKeepAliveSeconds;
    }

    public ThreadPoolExecutor getTpe() {
        return tpe;
    }

    public KeyManagerFactory getKmf() {
        return kmf;
    }

    public TrustManagerFactory getTmf() {
        return tmf;
    }

}
