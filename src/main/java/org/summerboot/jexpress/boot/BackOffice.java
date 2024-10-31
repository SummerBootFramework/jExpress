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
package org.summerboot.jexpress.boot;

import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.util.ReflectionUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BackOffice extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(BackOffice.class);
        System.out.println(t);
    }

    public static final BackOffice agent = new BackOffice();

    protected BackOffice() {
        loadBalancingPingEndpoints = new ArrayList<>();
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        if (tpeCore < 1) {
            tpeCore = CPU_CORE + 1;
        }
        if (tpeMax < 1) {
            tpeMax = CPU_CORE + 1;
        }

        tpe = buildThreadPoolExecutor(tpe, "BackOffice", ThreadingMode.Mixed,
                tpeCore, tpeMax, tpeQueue, tpeKeepAliveSeconds, new ThreadPoolExecutor.DiscardPolicy(),
                prestartAllCoreThreads, allowCoreThreadTimeOut, false);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    tpe.shutdown();
                }, "ShutdownHook.BackOffice")
        );
        jboListenerLogLevel = Level.getLevel(jboListenerLogLevelString);
    }

    @Override
    public void shutdown() {
    }

    public static void execute(Runnable task) {
        if (agent.tpe == null || agent.tpe.isShutdown()) {
            throw new IllegalStateException("BackOffice.tpe is down:" + agent.tpe);
        }
        agent.tpe.execute(task);
    }

    protected ThreadPoolExecutor tpe;
    private List<String> loadBalancingPingEndpoints;
    private String version;
    private String versionShort;

    public List<String> getLoadBalancingPingEndpoints() {
        return loadBalancingPingEndpoints;
    }

    public void addLoadBalancingPingEndpoint(String loadBalancingPingEndpoint) {
        loadBalancingPingEndpoints.add(loadBalancingPingEndpoint);
    }

    public String getVersion() {
        return version;
    }

    void setVersion(String version) {
        this.version = version;
    }

    public String getVersionShort() {
        return versionShort;
    }

    void setVersionShort(String versionShort) {
        this.versionShort = versionShort;
    }

    void setRootPackageNames(Set<String> rootPackageNames) {
        this.rootPackageNames = Set.copyOf(rootPackageNames);
    }

    private static String listBootErrorCode() {
        String ret = "";
        try {
            Map<String, Integer> results = new HashMap();
            ReflectionUtil.loadFields(BootErrorCode.class, int.class, results, false);
            Map<Object, String> sorted = results
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
            StringBuilder sb = new StringBuilder().append("## Default Error Codes:").append(BR);
            sorted.forEach((key, value) -> sb.append("## ").append(key).append(": ").append(value).append(BR));
            ret = sb.toString();
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        return ret;
    }

    // 1 Override error code
    @ConfigHeader(title = "1. Override default error codes with application defined ones", desc = "To verify: java -jar <app>.jar -list SystemErrorCode [-dmain <domain>]",
            format = "CSV of <default error code>:<new error code>",
            example = "1:1001, 20:1020, 40:1040, 50:1050",
            callbackMethodName4Dump = "generateTemplate_keystore")
    @Config(key = "errorcode.override")
    private volatile Map<Integer, Integer> bootErrorCodeMapping;

    protected void generateTemplate_keystore(StringBuilder sb) {
        sb.append(listBootErrorCode()).append(System.lineSeparator());
    }

    public Map<Integer, Integer> getBootErrorCodeMapping() {
        return bootErrorCodeMapping;
    }

    @ConfigHeader(title = "2. Application Packages", desc = "Only for those applications which have more than one root package names",
            format = "CSV of string",
            example = "com.package1, com.package2")
    @Config(key = "rootpackage.names")
    private Set<String> rootPackageNames;

    @ConfigHeader(title = "3. Default Settings")
    @Config(key = "log.traceWithSystemOut", defaultValue = "false", desc = "enable trace with system out before logger enabled")
    private boolean traceWithSystemOut = false;

    @Config(key = "type.errorCodeAsInt", defaultValue = "false")
    private boolean errorCodeAsInt = false;

    @Config(key = "type.JWTAudAsCSV", defaultValue = "true", desc = "Parse JWT Audience value as CSV for JWT backward compatibility")
    private boolean jwtAudAsCSV = true;

    @Config(key = "default.interval.ConfigChangeMonitor", defaultValue = "30")
    private int CfgChangeMonitorIntervalSec = 30;

    @Config(key = "default.web.resource.ttl.sec", defaultValue = "3600")
    private long webResourceCacheTtlSec = 3600;

    @Config(key = "reflection.package.level", defaultValue = "2")
    private int reflectionPackageLevel = 2;

    @Config(key = "timeout.alert.milliseconds", defaultValue = "10000")
    private long processTimeoutMilliseconds = 10000;

    private static final String ALERT_MSG_TIMEOUT = "Note: This is a known issue in Linux systems where the/dev/random runs out of \"entropy\" and it causes the system to blockthreads. \n\tTo verify: cat /dev/random or install rng-tools and/or haveged.\n\tOr add a JVM argument to the runner: -Djava.security.egd=file:/dev/./urandom";
    @Config(key = "timeout.alert.message", defaultValue = ALERT_MSG_TIMEOUT)
    private String processTimeoutAlertMessage = ALERT_MSG_TIMEOUT;

    private static final String ALERT_MSG_PORT_IN_USE = "In order to check which application is listening on a port, you can use the following command from the command line:\n"
            + "\n"
            + "For Microsoft Windows:\n"
            + "    netstat -ano | find \"80\" | find \"LISTEN\"\n"
            + "    tasklist /fi \"PID eq <pid>\"\n"
            + "     \n"
            + "For Linux:\n"
            + "    netstat -anpe | grep \"80\" | grep \"LISTEN\" \n";
    @Config(key = "portinuse.alert.message", defaultValue = ALERT_MSG_PORT_IN_USE)
    private String portInUseAlertMessage = ALERT_MSG_PORT_IN_USE;

    @Config(key = "backoffice.executor.core", defaultValue = "3",
            desc = "0 = current computer/VM's available processors + 1")
    private int tpeCore = 3;

    @Config(key = "backoffice.executor.max", defaultValue = "3",
            desc = "0 = current computer/VM's available processors + 1")
    private int tpeMax = 3;

    @Config(key = "backoffice.executor.queue", defaultValue = "" + Integer.MAX_VALUE)
    private int tpeQueue = Integer.MAX_VALUE;

    @Config(key = "backoffice.executor.keepAliveTimeSec", defaultValue = "60")
    private int tpeKeepAliveSeconds = 60;

    @Config(key = "backoffice.executor.prestartAllCoreThreads", defaultValue = "false")
    private boolean prestartAllCoreThreads = false;

    @Config(key = "backoffice.executor.allowCoreThreadTimeOut", defaultValue = "false")
    private boolean allowCoreThreadTimeOut = false;

    @ConfigHeader(title = "4.1 Default Path/File Naming")
    @Config(key = "naming.folder.domainPrefix", defaultValue = "standalone")
    private String domainFolderPrefix = "standalone";

    @Config(key = "naming.folder.config", defaultValue = "configuration")
    private String configFolderName = "configuration";

    @Config(key = "naming.folder.log", defaultValue = "log")
    private String logFolderName = "log";

    @Config(key = "naming.folder.plugin", defaultValue = "plugin")
    private String pluginFolderName = "plugin";

    @Config(key = "naming.file.AuthConfig", defaultValue = "cfg_auth.properties")
    private String authConfigFileName = "cfg_auth.properties";

    @Config(key = "naming.file.SMTPConfig", defaultValue = "cfg_smtp.properties")
    private String smtpConfigFileName = "cfg_smtp.properties";

    @Config(key = "naming.file.NIOConfig", defaultValue = "cfg_nio.properties")
    private String nioConfigFileName = "cfg_nio.properties";

    @Config(key = "naming.file.gRPCConfig", defaultValue = "cfg_grpc.properties")
    private String gRPCConfigFileName = "cfg_grpc.properties";

    @Config(key = "HealthMonitor.PauseLockCode.viaFile", defaultValue = "PauseLockCode.file")
    private String pauseLockCodeViaFile = "PauseLockCode.file";

    @Config(key = "HealthMonitor.PauseLockCode.viaWeb", defaultValue = "PauseLockCode.web")
    private String pauseLockCodeViaWeb = "PauseLockCode.web";

    @ConfigHeader(title = "4.2 Default Log4j2.xml Variables Naming")
    @Config(key = "naming.log4j2.xml.var.logId", defaultValue = "logId")
    private String log4j2LogId = "logId";

    @Config(key = "naming.log4j2.xml.var.logPath", defaultValue = "logPath")
    private String log4j2LogFilePath = "logPath";

    @Config(key = "naming.log4j2.xml.var.appName", defaultValue = "appName")
    private String log4j2LogFileName = "appName";

    @Config(key = "naming.log4j2.xml.var.serverName", defaultValue = "serverName")
    private String log4j2ServerName = "serverName";

    @Config(key = "naming.log4j2.xml.var.appPackageName", defaultValue = "appPackageName")
    private String log4j2AppPackageName = "appPackageName";

    @Config(key = "naming.log4j2.jboListenerLogLevel", defaultValue = "DEBUG")
    private String jboListenerLogLevelString = Level.DEBUG.toString();
    private Level jboListenerLogLevel = Level.DEBUG;

    @ConfigHeader(title = "4.3 Default CLI Naming")
    @Config(key = "naming.cli.usage", defaultValue = "?")
    private String cliName_usage = "?";

    @Config(key = "naming.cli.version", defaultValue = "version")
    private String cliName_version = "version";

    @Config(key = "naming.cli.domain", defaultValue = "domain")
    private String cliName_domain = "domain";

    @Config(key = "naming.cli.cfgdir", defaultValue = "cfgdir")
    private String cliName_cfgdir = "cfgdir";

    @Config(key = "naming.cli.monitorInterval", defaultValue = "monitorInterval")
    private String cliName_monitorInterval = "monitorInterval";

    @Config(key = "naming.cli.i18n", defaultValue = "i18n")
    private String cliName_i18n = "i18n";

    @Config(key = "naming.cli.use", defaultValue = "use")
    private String cliName_use = "use";//To specify which implementation will be used via @Component.checkImplTagUsed

    @Config(key = "naming.cli.cfgdemo", defaultValue = "cfgdemo")
    private String cliName_cfgdemo = "cfgdemo";

    @Config(key = "naming.cli.list", defaultValue = "list")
    private String cliName_list = "list";

    @Config(key = "naming.cli.authfile", defaultValue = "authfile")
    private String cliName_authfile = "authfile";

    @Config(key = "naming.cli.auth", defaultValue = "auth")
    private String cliName_auth = "auth";

    @Config(key = "naming.cli.jwt", defaultValue = "jwt")
    private String cliName_jwt = "jwt";

    @Config(key = "naming.cli.encrypt", defaultValue = "encrypt")
    private String cliName_encrypt = "encrypt";

    @Config(key = "naming.cli.decrypt", defaultValue = "decrypt")
    private String cliName_decrypt = "decrypt";

    @Config(key = "naming.cli.psv", defaultValue = "psv")
    private String cliName_psv = "psv";

    @Config(key = "naming.memo.delimiter", defaultValue = ": ")
    private String memoDelimiter = ": ";

    public Set<String> getRootPackageNames() {
        return rootPackageNames;
    }

    public boolean isTraceWithSystemOut() {
        return traceWithSystemOut;
    }

    public boolean isErrorCodeAsInt() {
        return errorCodeAsInt;
    }

    public boolean isJwtAudAsCSV() {
        return jwtAudAsCSV;
    }

    public int getCfgChangeMonitorIntervalSec() {
        return CfgChangeMonitorIntervalSec;
    }

    public long getWebResourceCacheTtlSec() {
        return webResourceCacheTtlSec;
    }

    public int getReflectionPackageLevel() {
        return reflectionPackageLevel;
    }

    public long getProcessTimeoutMilliseconds() {
        return processTimeoutMilliseconds;
    }

    public String getProcessTimeoutAlertMessage() {
        return processTimeoutAlertMessage;
    }

    public String getPortInUseAlertMessage() {
        return portInUseAlertMessage;
    }

    public String getDomainFolderPrefix() {
        return domainFolderPrefix;
    }

    public String getConfigFolderName() {
        return configFolderName;
    }

    public String getLogFolderName() {
        return logFolderName;
    }

    public String getPluginFolderName() {
        return pluginFolderName;
    }

    public String getAuthConfigFileName() {
        return authConfigFileName;
    }

    public String getSmtpConfigFileName() {
        return smtpConfigFileName;
    }

    public String getNioConfigFileName() {
        return nioConfigFileName;
    }

    public String getgRPCConfigFileName() {
        return gRPCConfigFileName;
    }

    public String getPauseLockCodeViaFile() {
        return pauseLockCodeViaFile;
    }

    public String getPauseLockCodeViaWeb() {
        return pauseLockCodeViaWeb;
    }

    public String getLog4J2LogId() {
        return log4j2LogId;
    }

    public String getLog4j2LogFilePath() {
        return log4j2LogFilePath;
    }

    public String getLog4j2LogFileName() {
        return log4j2LogFileName;
    }

    public String getLog4j2ServerName() {
        return log4j2ServerName;
    }

    public String getLog4j2AppPackageName() {
        return log4j2AppPackageName;
    }

    public String getCliName_usage() {
        return cliName_usage;
    }

    public String getCliName_version() {
        return cliName_version;
    }

    public String getCliName_domain() {
        return cliName_domain;
    }

    public String getCliName_cfgdir() {
        return cliName_cfgdir;
    }

    public String getCliName_monitorInterval() {
        return cliName_monitorInterval;
    }

    public String getCliName_i18n() {
        return cliName_i18n;
    }

    public String getCliName_use() {
        return cliName_use;
    }

    public String getCliName_cfgdemo() {
        return cliName_cfgdemo;
    }

    public String getCliName_list() {
        return cliName_list;
    }

    public String getCliName_authfile() {
        return cliName_authfile;
    }

    public String getCliName_auth() {
        return cliName_auth;
    }

    public String getCliName_jwt() {
        return cliName_jwt;
    }

    public String getCliName_encrypt() {
        return cliName_encrypt;
    }

    public String getCliName_decrypt() {
        return cliName_decrypt;
    }

    public String getCliName_psv() {
        return cliName_psv;
    }

    public String getMemoDelimiter() {
        return memoDelimiter;
    }

    public Level getJobListenerLogLevel() {
        return jboListenerLogLevel;
    }
}
