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

import java.io.File;
import java.security.SecureRandom;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface BootConstant {
    static boolean isDebugMode() {
        return BackOffice.agent.isDebugMode;
    }

    int APP_ID_VALUE = new SecureRandom().nextInt(999999);
    String APP_ID = String.format("%06d", APP_ID_VALUE);

    //version
    String VERSION = "jExpress 2.6.2";
    String JEXPRESS_PACKAGE_NAME = "org.summerboot.jexpress";

    String JSONFILTER_NAME_SERVICEERROR = "ServiceErrorFilter";

    /*
     * Runtime info
     */
    int CPU_CORE = Runtime.getRuntime().availableProcessors();
    String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    String BR = System.lineSeparator();

    //logging metadata
    String LOG4J2_KEY = "log4j.configurationFile";
    String LOG4J2_JDKADAPTER_KEY = "java.util.logging.manager";
    String LOG4J2_JDKADAPTER_VALUE = "org.apache.logging.log4j.jul.LogManager";

    /**
     * 3. jExpress Default Settings
     */
    boolean CFG_ERROR_CODE_AS_INT = BackOffice.agent.isErrorCodeAsInt();
    boolean CFG_JWT_AUD_AS_CSV = BackOffice.agent.isJwtAudAsCSV();
    long CFG_CHANGE_MONITOR_THROTTLE_MS = BackOffice.agent.getCfgChangeMonitorThrottleMillis();
    int PACKAGE_LEVEL = BackOffice.agent.getReflectionPackageLevel();
    long WEB_RESOURCE_TTL_MS = BackOffice.agent.getWebResourceCacheTtlSec() * 1000;
    String DEFAULT_MASTER_PASSWORD_FILE = "etc" + File.separator + BackOffice.agent.getDefaultMasterPasswordFile();
    String DIR_STANDALONE = BackOffice.agent.getDomainFolderPrefix();
    String DIR_CONFIGURATION = BackOffice.agent.getConfigFolderName();
    String DIR_PLUGIN = BackOffice.agent.getPluginFolderName();
    String DIR_LOG = BackOffice.agent.getLogFolderName();

    String FILE_CFG_AUTH = BackOffice.agent.getAuthConfigFileName();
    String FILE_CFG_SMTP = BackOffice.agent.getSmtpConfigFileName();
    String FILE_CFG_NIO = BackOffice.agent.getNioConfigFileName();
    String FILE_CFG_GRPC = BackOffice.agent.getgRPCConfigFileName();
    String FILE_PAUSE = BackOffice.agent.getPauseFileName();

    String RESPONSE_HEADER_KEY_REF = "X-Reference";
    String RESPONSE_HEADER_KEY_TS = "X-ServerTs";

    String PAUSE_LOCK_CODE_VIAFILE = BackOffice.agent.getPauseLockCodeViaFile();
    String PAUSE_LOCK_CODE_VIAWEB = BackOffice.agent.getPauseLockCodeViaWeb();


    /*
     * 4. jExpress Default CLI Name
     */
    String CLI_USAGE = BackOffice.agent.getCliName_usage();
    String CLI_VERSION = BackOffice.agent.getCliName_version();
    String CLI_CONFIG_DOMAIN = BackOffice.agent.getCliName_domain();
    String CLI_CONFIG_DIR = BackOffice.agent.getCliName_cfgdir();
    String CLI_CONFIG_MONITOR_INTERVAL = BackOffice.agent.getCliName_monitorInterval();
    String CLI_I8N = BackOffice.agent.getCliName_i18n();
    String CLI_USE_ALTERNATIVE = BackOffice.agent.getCliName_useAlternative();//To specify which implementation will be used via @Component.checkImplTagUsed
    String CLI_CONFIG_DEMO = BackOffice.agent.getCliName_cfgdemo();
    String CLI_LIST_UNIQUE = BackOffice.agent.getCliName_list();
    String CLI_ADMIN_PWD_FILE = BackOffice.agent.getCliName_authfile();
    String CLI_ADMIN_PWD = BackOffice.agent.getCliName_auth();
    String CLI_JWT = BackOffice.agent.getCliName_jwt();
    String CLI_ENCRYPT = BackOffice.agent.getCliName_encrypt();
    String CLI_DECRYPT = BackOffice.agent.getCliName_decrypt();
    String CLI_PSV = BackOffice.agent.getCliName_psv();
    String CLI_DEBUGMODE = BackOffice.agent.getCliName_debugMode();
    String MEMO_DELIMITER = BackOffice.agent.getMemoDelimiter();

    /*
     * 5. Log4j2.xml variables
     *
     * Pass by System.setProperty() instead of making them public static, any better idea?
     * ‘java.lang.System.getProperty()’ API underlyingly uses ‘java.util.Hashtable.get()’ API.
     * Please be advised that ‘java.util.Hashtable.get()’ is a synchronized API.
     * It means only one thread can invoke the ‘java.util.Hashtable.get()’ method at any given time.
     */
    String SYS_PROP_LOGID = BackOffice.agent.getLog4J2LogId();// "logid" // used by log4j2.xml ${sys:logid}
    String SYS_PROP_LOGFILEPATH = BackOffice.agent.getLog4j2LogFilePath();//"logPath"; // used by log4j2.xml ${sys:loggingPath}
    String SYS_PROP_LOGFILENAME = BackOffice.agent.getLog4j2LogFileName();//"appName"; // used by log4j2.xml ${sys:appappName} as log file name
    String SYS_PROP_SERVER_NAME = BackOffice.agent.getLog4j2ServerName();//"serverName"; // used by log4j2.xml ${hostName}
    String SYS_PROP_APP_PACKAGE_NAME = BackOffice.agent.getLog4j2AppPackageName();//"appPackageName"; // used by both log4j2.xml ${sys:appPackage} and JPAHibernateConfig to scan @Entity
    Level JOB_LISTENER_LOG_LEVEL = BackOffice.agent.getJobListenerLogLevel();
}
