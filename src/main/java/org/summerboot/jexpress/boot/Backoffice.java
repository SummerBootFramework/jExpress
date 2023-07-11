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

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import org.summerboot.jexpress.boot.config.BootConfig;
import static org.summerboot.jexpress.boot.config.BootConfig.generateTemplate;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.util.ReflectionUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BackOffice extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(BackOffice.class);
        System.out.println(t);
    }

    public static final BackOffice agent = new BackOffice();

    protected BackOffice() {
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
    private String pingURL;
    private String version;
    private String versionShort;

    public String getPingURL() {
        return pingURL;
    }

    void setPingURL(String pingURL) {
        this.pingURL = pingURL;
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
            example = "10:1010, 20:1020",
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
    @Config(key = "type.errorCodeAsInt", defaultValue = "false")
    private boolean errorCodeAsInt = false;

    @Config(key = "timeout.alert.milliseconds", defaultValue = "3000")
    private long processTimeoutMilliseconds = 3000;

    private static final String ALERT_MSG_TIMEOUT = "Note: This is a known issue in Linux systems where the/dev/random runs out of \"entropy\" and it causes the system to blockthreads. \n\tTo verify: cat /dev/random or install haveged.\n";
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
            desc = "MaxSize 0 = current computer/VM's available processors + 1")
    private int tpeCore = 3;

    @Config(key = "backoffice.executor.max", defaultValue = "" + Integer.MAX_VALUE,
            desc = "MaxSize 0 = current computer/VM's available processors + 1")
    private int tpeMax = Integer.MAX_VALUE;

    @Config(key = "backoffice.executor.queue", defaultValue = "0")
    private int tpeQueue = 0;

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

    @ConfigHeader(title = "4.2 Default Log4j2.xml Variables Naming")
    @Config(key = "naming.log4j2.xml.var.logPath", defaultValue = "logPath")
    private String log4j2LogFilePath = "logPath";

    @Config(key = "naming.log4j2.xml.var.appName", defaultValue = "appName")
    private String log4j2LogFileName = "appName";

    @Config(key = "naming.log4j2.xml.var.serverName", defaultValue = "serverName")
    private String log4j2ServerName = "serverName";

    @Config(key = "naming.log4j2.xml.var.appPackageName", defaultValue = "appPackageName")
    private String log4j2AppPackageName = "appPackageName";

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

    public Set<String> getRootPackageNames() {
        return rootPackageNames;
    }

    public boolean isErrorCodeAsInt() {
        return errorCodeAsInt;
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

}
