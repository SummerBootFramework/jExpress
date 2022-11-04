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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import io.netty.channel.ChannelHandler;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.cli.CommandLineRunner;
import org.summerboot.jexpress.boot.config.BootJExpressConfig;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.ConfigUtil.ConfigLoadMode;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.smtp.BootPostOfficeImpl;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPConfig;
import org.summerboot.jexpress.nio.server.BootHttpPingHandler;
import org.summerboot.jexpress.nio.server.BootHttpRequestHandler;
import org.summerboot.jexpress.nio.server.HttpConfig;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.NioServer;
import org.summerboot.jexpress.nio.server.NioServerContext;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.boot.config.JExpressConfig;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class JExpressApplication extends CommandLineRunner {

    protected static Logger log;

    private static final String CFG_MONITOR_INTERVAL = "cmi";
    private static final String CLI_MOCKMODE = "mock";
    private static final String CLI_ERROR_CODE = "errorcode";
    private static final String CLI_POI_LIST = "poi";
    private static final String CLI_SHOW_CONFIG = "sample";
    private static final String CLI_I8N = "i18n";
    private static String callerRootPackageName;

    public static JExpressApplication bind(Class controllerScanRootClass) {
        return new JExpressApplication(controllerScanRootClass) {
            @Override
            protected void locadCustomizedConfigs(Path configFolder) {
            }

            @Override
            protected void beforeStart(CommandLine cli) {
            }
        };
    }

    /**
     * For Unit Testing only
     *
     * @param appModule application Google Inject Guice module
     * @param callerClass the main class
     * @return combined (app and framework) Google Inject Guice module
     */
    public static Module buildModule(AbstractModule appModule, Class callerClass) {
        BootGuiceModule frameworkModule = new BootGuiceModule(null, callerClass, true);
        Module finalModule = appModule == null
                ? frameworkModule
                : Modules.override(frameworkModule).with(appModule);
        return finalModule;
    }

    /*
     value passed from CLI
     */
    private static final Set<String> appMockOptions = new HashSet<>();

    /**
     * Check if a given name is provided in command line -mock option
     *
     * @param mockItemName
     * @return true if mockItemName is available in command line args
     * {@code -mock <mockItemName1, mockItemName2, ...>}
     */
    public static boolean isMockMode(String mockItemName) {
        return appMockOptions.contains(mockItemName);
    }

    public static Set<String> getAppMockOptions() {
        return Set.copyOf(appMockOptions);
    }

    public static String getCallerRootPackageName() {
        return callerRootPackageName;
    }

    //app internal
    private Locale cfgDefaultRB;
    private String envName;
    private Path cfgConfigDir;
    private int CfgMonitorInterval;
    private Injector iocInjector;
    private String cfgEnvFolderPrefix;
    private final Class controllerScanRootClass;
    //Plugin - bind
    private Class<? extends PostOffice> bindingPostOfficeClass = BootPostOfficeImpl.class;
    private AbstractModule bindingAppModule;
    private Class<? extends ChannelHandler> bindingChannelHandlerClass;
    private String bindingChannelHandlerBindingName;
    //Plugin - enable
    private Class enable_cli_errorCodeClass;
    private Class enable_cli_poiNameClass;
    private Set<String> enable_cli_validMockValues = null;
    private Class<? extends HealthInspector> enable_ping_healthInspectorClass;
    private final Map<String, Class> enable_cli_validConfigs = new LinkedHashMap<>();

    @Inject
    private PostOffice postOffice;

    @Inject
    private HealthInspector healthInspector;

    @Inject
    private ConfigChangeListener configChangeListener;

    @Inject
    private InstrumentationMgr instrumentationMgr;

    {
        enable_cli_validConfigs.put(NioConfig.class.getSimpleName(), NioConfig.class);
        enable_cli_validConfigs.put(HttpConfig.class.getSimpleName(), HttpConfig.class);
        enable_cli_validConfigs.put(SMTPConfig.class.getSimpleName(), SMTPConfig.class);
        enable_cli_validConfigs.put(AuthConfig.class.getSimpleName(), AuthConfig.class);
    }

    protected JExpressApplication() {
        this.controllerScanRootClass = this.getClass();
    }

    private JExpressApplication(Class controllerScanRootClass) {
        this.controllerScanRootClass = controllerScanRootClass;
    }

    public Locale getCfgDefaultRB() {
        return cfgDefaultRB;
    }

    public String getEnvName() {
        return envName;
    }

    public Path getCfgConfigDir() {
        return cfgConfigDir;
    }

    public String getCfgEnvFolderPrefix() {
        return cfgEnvFolderPrefix;
    }

    public Injector getIocInjector() {
        return iocInjector;
    }

    /**
     * To override the default email sender instance with the app
     *
     * @param <T>
     * @param postOfficeClass
     * @return
     */
    public <T extends JExpressApplication> T bind_AlertMessenger(Class<? extends PostOffice> postOfficeClass) {
        this.bindingPostOfficeClass = postOfficeClass;
        return (T) this;
    }

    /**
     * To bind Google Guice IoC container with the app
     *
     * @param <T>
     * @param appModule
     * @return
     */
    public <T extends JExpressApplication> T bind_GuiceModule(AbstractModule appModule) {
        this.bindingAppModule = appModule;
        return (T) this;
    }

    public <T extends JExpressApplication> T bind_NIOHandler(Class<? extends ChannelHandler> channelHandlerClass) {
        return bind_NIOHandler(channelHandlerClass, null);
    }

    /**
     * To bind a unique named NIO Request Handler with the app
     *
     * @param <T>
     * @param channelHandlerClass
     * @param channelHandlerBindingName
     * @return
     */
    public <T extends JExpressApplication> T bind_NIOHandler(Class<? extends ChannelHandler> channelHandlerClass, String channelHandlerBindingName) {
        if (StringUtils.isBlank(channelHandlerBindingName)) {
            channelHandlerBindingName = channelHandlerClass.getName();
        }
        if (BootHttpRequestHandler.class.getName().equals(channelHandlerBindingName)
                || BootHttpPingHandler.class.getName().equals(channelHandlerBindingName)) {
            throw new UnsupportedOperationException("binding name is reserved by SummerBoot Framework");
        }
        this.bindingChannelHandlerClass = channelHandlerClass;
        this.bindingChannelHandlerBindingName = channelHandlerBindingName;
        return (T) this;
    }

    private List<RegisteredAppConfig> registeredAppConfigs = null;

    private class RegisteredAppConfig {

        final String configFileName;
        final JExpressConfig config;
        final String mockName;
        final boolean registerWhenMockIsEnabled;

        public RegisteredAppConfig(String configFileName, JExpressConfig config, String mockName, boolean registerWhenMockIsEnabled) {
            this.configFileName = configFileName;
            this.config = config;
            this.mockName = mockName;
            this.registerWhenMockIsEnabled = registerWhenMockIsEnabled;
        }

    }

    /**
     * To bind a configuration file implemented by a JExpressConfig instance,
     * which will be loaded and managed by SummerBoot Application
     *
     * @param <T>
     * @param configFileName file name only, without file path
     * @param config the JExpressConfig instance
     * @return
     */
    public <T extends JExpressApplication> T bind_JExpressConfig(String configFileName, JExpressConfig config) {
        return bind_JExpressConfig(configFileName, config, null, false);
    }

    /**
     * To bind a configuration file implemented by a JExpressConfig instance,
     * which will be loaded and managed by SummerBoot Application
     *
     * @param <T>
     * @param configFileName
     * @param config
     * @param mockName
     * @param registerWhenMockIsEnabled
     * @return
     */
    public <T extends JExpressApplication> T bind_JExpressConfig(String configFileName, JExpressConfig config, String mockName, boolean registerWhenMockIsEnabled) {
        if (registeredAppConfigs == null) {
            registeredAppConfigs = new ArrayList<>();
        }
        registeredAppConfigs.add(new RegisteredAppConfig(configFileName, config, mockName, registerWhenMockIsEnabled));
        return (T) this;
    }

    /**
     * To enable -errorcode CLI to list all error codes
     *
     * @param <T>
     * @param errorCodeClass
     * @param checkDuplicated check duplicated if true
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws JsonProcessingException
     */
    public <T extends JExpressApplication> T enable_CLI_ListErrorCodes(Class errorCodeClass, boolean checkDuplicated) throws IllegalArgumentException, IllegalAccessException, JsonProcessingException {
        this.enable_cli_errorCodeClass = errorCodeClass;
        if (errorCodeClass != null && checkDuplicated) {
            Map<Object, Set<String>> duplicated = ApplicationUtil.checkDuplicateFields(errorCodeClass, int.class);
            if (!duplicated.isEmpty()) {
                String report = BeanUtil.toJson(duplicated, true, false);
                System.err.println("duplicated.AppErrorCode=" + report);
                System.exit(1);
            }
        }
        return (T) this;
    }

    /**
     * To enable -poi CLI to list all POI names
     *
     * @param <T>
     * @param poiNameClass
     * @param checkDuplicated check duplicated if true
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws JsonProcessingException
     */
    public <T extends JExpressApplication> T enable_CLI_ListPOIs(Class poiNameClass, boolean checkDuplicated) throws IllegalArgumentException, IllegalAccessException, JsonProcessingException {
        this.enable_cli_poiNameClass = poiNameClass;
        if (enable_cli_errorCodeClass != null && checkDuplicated) {
            Map<Object, Set<String>> duplicated = ApplicationUtil.checkDuplicateFields(poiNameClass, String.class);
            if (!duplicated.isEmpty()) {
                String report = BeanUtil.toJson(duplicated, true, false);
                System.err.println("duplicated.ServicePOI=" + report);
                System.exit(1);
            }
        }
        return (T) this;
    }

    /**
     * To enable mock mode
     *
     * @param <T>
     * @param enumClass the enum contains mock items
     * @return
     */
    public <T extends JExpressApplication> T enable_CLI_MockMode(Class<? extends Enum<?>> enumClass) {
        return enable_CLI_MockMode(FormatterUtil.getEnumNames(enumClass));
    }

    /**
     * To enable mock mode
     *
     * @param <T>
     * @param mockItemNames the mock item names
     * @return
     */
    public <T extends JExpressApplication> T enable_CLI_MockMode(String... mockItemNames) {
        if (mockItemNames == null || mockItemNames.length < 1) {
            return (T) this;
        }
        enable_cli_validMockValues = Set.of(mockItemNames);
        return (T) this;
    }

    /**
     * To enable viewing config setting using CLI
     *
     * @param <T>
     * @param cs
     * @return
     */
    public <T extends JExpressApplication> T enable_CLI_ViewConfig(Class... cs) {
        for (Class c : cs) {
            enable_cli_validConfigs.put(c.getSimpleName(), c);
        }
        return (T) this;
    }

    /**
     * To enable load balancer health check on the provided path via HTTP GET
     * request
     *
     * @param <T>
     * @param contextRoot the contect root like "myroot/myservice"
     * @param pingPath the ping command like "/ping", the the load balancer will
     * do the health check vis GET /myroot/myservice/ping
     * @return
     */
    public <T extends JExpressApplication> T enable_Ping_HealthCheck(String contextRoot, String pingPath) {
        return enable_Ping_HealthCheck(contextRoot, pingPath, null);
    }

    /**
     * Override load balancer health check path and inspector
     *
     * @param <T>
     * @param contextRoot
     * @param pingPath
     * @param healthInspectorClass
     * @return
     */
    public <T extends JExpressApplication> T enable_Ping_HealthCheck(String contextRoot, String pingPath, Class<? extends HealthInspector> healthInspectorClass) {
        this.enable_ping_healthInspectorClass = healthInspectorClass;
        //if (enable_ping_healthInspectorClass != null) {
        NioServerContext.setWebApiContextRoot(contextRoot);
        NioServerContext.setLoadBalancerHealthCheckPath(pingPath);
        //}
        return (T) this;
    }

    /**
     * To run the SummerBoot Application
     *
     * @param args
     * @param version
     * @throws Exception
     */
    public void run(String[] args, String version) throws Exception {
        run(args, version, true);
    }

    /**
     *
     * @param args
     * @param version
     * @param startNIO
     * @throws Exception
     */
    public void run(String[] args, String version, boolean startNIO) throws Exception {
        init(args, version, startNIO);//process CLI, load config files, init IOC
        start(version, startNIO);
    }

    /**
     * process CLI first then load configuration files without caller class,
     * init IOC
     *
     * @param args
     * @param version
     * @throws Exception
     */
    private void init(String[] args, String version, boolean startNIO) throws Exception {
        this.version = version;
        callerRootPackageName = ReflectionUtil.getRootPackageName(this.controllerScanRootClass);

        //1. process CLI
        runCLI(args, "env", "configuration");

        //2. load configs
        loadBootConfigs();
        locadCustomizedConfigs(cfgConfigDir);

        //3. initialize IOC injector
        BootGuiceModule frameworkModule = new BootGuiceModule(this, controllerScanRootClass, startNIO);
        Module overrideModule = bindingAppModule == null
                ? frameworkModule
                : Modules.override(frameworkModule).with(bindingAppModule);
        if (enable_ping_healthInspectorClass != null || bindingChannelHandlerClass != null || bindingPostOfficeClass != null) {
            Module enabledModule = new AbstractModule() {
                @Override
                protected void configure() {
                    if (enable_ping_healthInspectorClass != null) {
                        bind(HealthInspector.class).to(enable_ping_healthInspectorClass);
                    }
                    if (bindingChannelHandlerClass != null) {
                        bind(ChannelHandler.class).annotatedWith(Names.named(bindingChannelHandlerBindingName)).to(bindingChannelHandlerClass);
                    }
                    if (bindingPostOfficeClass != null) {
                        bind(PostOffice.class).to(bindingPostOfficeClass);
                    }
                }
            };
            overrideModule = Modules.override(overrideModule).with(enabledModule);
        }
        iocInjector = Guice.createInjector(overrideModule);
        NioConfig.setGuiceInjector(iocInjector);
        if (configChangeListener != null) {
            ConfigUtil.setConfigChangeListener(configChangeListener);
        }
    }

    /**
     * Initialize based on application command line args, use "env" as the
     * default envFolderPrefix,and "configuration" as the default configDirName
     *
     * <p>
     * Log4j2 will be initialized at the last step
     *
     * @param args application command line arguments
     * @param envFolderPrefix the prefix of env folder under the application
     * folder, for example: "env" is the env prefix when preLaunch\env_proc
     * @param configDirName the configuration folder name under env folder
     * @return the path of configuration folder
     * @throws java.lang.Exception
     */
    private Path runCLI(String[] args, String envFolderPrefix, String configDirName) throws Exception {
        this.cfgEnvFolderPrefix = envFolderPrefix;
        initCLIs_BootApp();
        initCLIs_App(options);
        initCLIs_BootDefault(args);//super

        //0. preLaunch default CLI commands first, they have no dependencies on logging, config loading, etc.
        processCLIs_BootDefault();//super

        //1. show config on demand
        if (cli.hasOption(CLI_SHOW_CONFIG)) {
            String cfgName = cli.getOptionValue(CLI_SHOW_CONFIG);
            Class c = enable_cli_validConfigs.get(cfgName);
            if (c == null) {
                System.err.println(cfgName + "is invalid config option");
                System.exit(1);
            } else {
                String t = BootJExpressConfig.generateTemplate(c);
                System.out.println(t);
                System.exit(0);
            }
        }

        //2. determine locale
        I18n.init(getAddtionalI18n());
        if (cli.hasOption(CLI_I8N)) {
            String language = cli.getOptionValue(CLI_I8N);
            cfgDefaultRB = Locale.forLanguageTag(language);
        } else {
            cfgDefaultRB = null;
        }

        //3. determine config Change Monitor Interval
        if (cli.hasOption(CFG_MONITOR_INTERVAL)) {
            String cmi = cli.getOptionValue(CFG_MONITOR_INTERVAL);
            CfgMonitorInterval = Integer.parseInt(cmi);
        } else {
            CfgMonitorInterval = 5;
        }

        //4. determine config folder and init logging
//        if (cli.hasOption(DOMAIN)) {
//            envName = cli.getOptionValue(DOMAIN).trim();
//            System.setProperty("envName", envName);
//        }
        if (cli.hasOption(ENV)) {
            envName = cli.getOptionValue(ENV).trim();
            cfgConfigDir = ConfigUtil.cfgRoot(cfgEnvFolderPrefix, envName, configDirName);
            File folder = cfgConfigDir.toFile();
            if (!folder.isDirectory() || !folder.exists()) {
                System.out.println("Could not find env: " + cfgConfigDir.getParent());
                System.exit(1);
            }
        } else if (cli.hasOption(DOMAIN)) {//backward compatible
            cfgEnvFolderPrefix = "standalone";
            envName = cli.getOptionValue(DOMAIN).trim();
            cfgConfigDir = ConfigUtil.cfgRoot(cfgEnvFolderPrefix, envName, configDirName);
            File folder = cfgConfigDir.toFile();
            if (!folder.isDirectory() || !folder.exists()) {
                System.out.println("Could not find env: " + cfgConfigDir.getParent());
                System.exit(1);
            }
        } else {
            envName = "prod";
            cfgConfigDir = ConfigUtil.cfgRoot(cfgEnvFolderPrefix, envName, configDirName);
            File folder = cfgConfigDir.toFile();
            if (!folder.isDirectory() || !folder.exists()) {
                cfgEnvFolderPrefix = "standalone";
                envName = "release";
                cfgConfigDir = ConfigUtil.cfgRoot(cfgEnvFolderPrefix, envName, configDirName);
                folder = cfgConfigDir.toFile();
                if (!folder.isDirectory() || !folder.exists()) {
                    System.out.println("Could not find env: " + cfgConfigDir.getParent());
                    System.exit(1);
                }
            }
        }

        System.setProperty("envName", envName);
        System.setProperty("domainName", envName);//backward compatible
        String log4j2ConfigFile = Paths.get(cfgConfigDir.toString(), "log4j2.xml").toString();
        System.setProperty(BootConstant.LOG4J2_KEY, log4j2ConfigFile);
        System.out.println(I18n.info.launchingLog.format(cfgDefaultRB, System.getProperty(BootConstant.LOG4J2_KEY)));
        log = LogManager.getLogger(JExpressApplication.class);

        //5. determine if in mock mode
        if (cli.hasOption(CLI_MOCKMODE)) {
            appMockOptions.clear();
            String[] mockItemList = cli.getOptionValues(CLI_MOCKMODE);
            Set<String> mockInputValues = Set.of(mockItemList);
            if (enable_cli_validMockValues.containsAll(mockInputValues)) {
                appMockOptions.addAll(mockInputValues);
            } else {
                Set<String> invalidOptions = new HashSet(mockInputValues);
                invalidOptions.removeAll(enable_cli_validMockValues);
                log.fatal("invalid -mock value: " + FormatterUtil.toCSV(invalidOptions) + ", valid -mock values: " + FormatterUtil.toCSV(enable_cli_validMockValues));
                System.exit(1);
            }
        }

        //6. preLaunch application CLI commands , they have dependencies on env's configDir.
        processCLIs_BootApp(cli);
        processCLIs_App(cli);

        // 7. run logging
        log.info(() -> I18n.info.launching.format(cfgDefaultRB) + ", cmi=" + CfgMonitorInterval + ", StartCommand>" + getStartCommand());

        return cfgConfigDir;
    }

    /**
     * callback to init customized CLIs
     *
     * @param options
     */
    protected void initCLIs_App(Options options) {
    }

    /**
     * callback to process customized CLIs
     *
     * @param cli
     */
    protected void processCLIs_App(CommandLine cli) {
    }

    /**
     * initialize predefined command line args initialize application CLI - add
     * CLI.options
     */
    private void initCLIs_BootApp() {
        // list error code
        if (enable_cli_errorCodeClass != null) {
            Option arg = new Option(CLI_ERROR_CODE, false, "list application error code");
            arg.setRequired(false);
            options.addOption(arg);
        }

        // list POI names
        if (enable_cli_poiNameClass != null) {
            Option arg = new Option(CLI_POI_LIST, false, "list POI names");
            arg.setRequired(false);
            options.addOption(arg);
        }

        if (enable_cli_validMockValues != null && !enable_cli_validMockValues.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(enable_cli_validMockValues);
            Option arg = Option.builder(CLI_MOCKMODE).desc("launch application in mock mode, valid values <" + validOptions + ">")
                    .hasArgs().argName("items")
                    .build();
            arg.setArgs(Option.UNLIMITED_VALUES);
            arg.setRequired(false);
            options.addOption(arg);
        }

        Option arg = Option.builder(ENV)
                .desc("Start server from " + cfgEnvFolderPrefix + "_<name>"
                        + System.lineSeparator() + System.lineSeparator() + "The -env option enables multiple env folders, this is also useful with docker, this -env option tells the application which env to use."
                        + System.lineSeparator() + System.lineSeparator() + "The <env name>: under the application folder there will be one or more folders in the format of env_<env name>, by default there will be env_prod folder, so prod is the env name of this configuration env.")
                .hasArg().argName("name")
                .build();
        options.addOption(arg);
        arg = Option.builder(DOMAIN)
                .desc("(deprecated, replaced by -env) Start server from standalone_<name>"
                        + System.lineSeparator() + System.lineSeparator() + "The -domain option enables multiple env folders, this is also useful with docker, this -domian option tells the application which env to use."
                        + System.lineSeparator() + System.lineSeparator() + "The <domain name>: under the application folder there will be one or more folders in the format of standalone_<domain name>, by default there will be standalone_release folder only when env_prod folder does not exist, so prod is the env name of this configuration env.")
                .hasArg().argName("name")
                .build();
        options.addOption(arg);

        arg = Option.builder(CFG_MONITOR_INTERVAL)
                .desc("configuration monitoring interval in second (default 5)")
                .hasArg().argName("interval")
                .build();
        options.addOption(arg);

        arg = new Option(CLI_I8N, true, "language <en | fr-CA>");
        arg.setRequired(false);
        options.addOption(arg);

        if (enable_cli_validConfigs != null && !enable_cli_validConfigs.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(enable_cli_validConfigs.keySet());
            arg = Option.builder(CLI_SHOW_CONFIG)
                    .desc("view config sample, valid values <" + validOptions + ">")
                    .hasArg().argName("config")
                    .build();
            options.addOption(arg);
        }
    }

    /**
     *
     * @return i18n class
     */
    protected Class getAddtionalI18n() {
        return null;
    }

    /**
     * preLaunch application CLI commands , they have dependencies on env's
     * configDir.
     *
     * @param cli
     * @throws java.lang.Exception
     */
    private void processCLIs_BootApp(final CommandLine cli) throws Exception {
        // encrypt/decrypt config files
        if (cli.hasOption(ENCRYPT) && cli.hasOption(ENV)) {
            boolean encrypt = Boolean.parseBoolean(cli.getOptionValue(ENCRYPT));
            if (!encrypt && cli.hasOption(ADMIN_PWD_FILE)) {
                System.err.println(System.lineSeparator() + "\t error: please private password with -auth option when decrypt data");
                System.exit(1);
            }
            int updated = loadBootConfigs(encrypt ? ConfigLoadMode.cli_encrypt : ConfigLoadMode.cli_decrypt, null);
            if (updated > 0) {
                String runtimeEnv = cli.getOptionValue(ENV).trim();
                System.out.println(System.lineSeparator() + "\t success: config files in env_" + runtimeEnv + " have been " + (encrypt ? "encrypted" : "decrypted"));
            } else {
                System.err.println(System.lineSeparator() + "\t ignored: no config file has been changed");
            }
            System.exit(0);
        }
        // list error code, POI
        try {
            if (cli.hasOption(CLI_ERROR_CODE)) {
                Map<Object, Set<String>> duplicated = ApplicationUtil.checkDuplicateFields(enable_cli_errorCodeClass, int.class);
                if (duplicated.isEmpty()) {
                    Map<String, Integer> results = new HashMap();
                    ReflectionUtil.loadFields(enable_cli_errorCodeClass, int.class, results, false);
                    Map<Object, String> sorted = results
                            .entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())
                            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
                    String json = BeanUtil.toJson(sorted, true, false);
                    System.out.println(json);
                } else {
                    String report = BeanUtil.toJson(duplicated, true, false);
                    System.out.println("duplicated.AppErrorCode=" + report);
                }
                System.exit(0);
            } else if (cli.hasOption(CLI_POI_LIST)) {
                Map<Object, Set<String>> duplicated = ApplicationUtil.checkDuplicateFields(enable_cli_poiNameClass, String.class);
                if (duplicated.isEmpty()) {
                    Map<String, String> results = new HashMap();
                    ReflectionUtil.loadFields(enable_cli_poiNameClass, String.class, results, false);
                    Map<String, String> sorted = results
                            .entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())
                            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
                    String json = BeanUtil.toJson(sorted, true, false);
                    System.out.println(json);
                } else {
                    String report = BeanUtil.toJson(duplicated, true, false);
                    System.out.println("duplicated.ServicePOI=" + report);
                }
                System.exit(0);
            }
        } catch (Throwable ex) {
            ex.printStackTrace(System.err);
        }
    }

    /**
     * initialize based on config files in configDir
     *
     * @return
     * @throws Exception
     */
    private int loadBootConfigs() throws Exception {
        return loadBootConfigs(ConfigLoadMode.app_run, configChangeListener);
    }

    /**
     * initialize based on config files in configDir
     *
     * @param mode
     * @param cfgChangeListener
     * @return
     * @throws Exception
     */
    private int loadBootConfigs(ConfigLoadMode mode, ConfigChangeListener cfgChangeListener) throws Exception {
        Map<String, JExpressConfig> configs = new LinkedHashMap<>();
        configs.put(BootConstant.CFG_AUTH, AuthConfig.CFG);
        configs.put(BootConstant.CFG_HTTP, HttpConfig.CFG);
        configs.put(BootConstant.CFG_NIO, NioConfig.CFG);
        configs.put(BootConstant.CFG_SMTP, SMTPConfig.CFG);
        int updated = 0;
        try {
            //1. get main configurations
            if (registeredAppConfigs != null) {
                for (RegisteredAppConfig registeredAppConfig : registeredAppConfigs) {
                    if ((isMockMode(registeredAppConfig.mockName) == registeredAppConfig.registerWhenMockIsEnabled)) {
                        configs.put(registeredAppConfig.configFileName, registeredAppConfig.config);
                    }
                }
            }
            //2. load configurations
            updated = ConfigUtil.loadConfigs(mode, log, cfgDefaultRB, cfgConfigDir, configs, CfgMonitorInterval);
            //3. regist listener if provided
            if (cfgChangeListener != null) {
                ConfigUtil.setConfigChangeListener(cfgChangeListener);
            }
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(cfgDefaultRB), ex);
            System.exit(1);
        }
        return updated;
    }

    /**
     * callback to initialize based on customized config files in configDir
     *
     * @param configFolder
     * @throws Exception
     */
    protected void locadCustomizedConfigs(Path configFolder) throws Exception {
    }

    /**
     * run application with ping enabled, URI as webApiContextRoot +
     * loadBalancerHealthCheckPath
     *
     * @param version
     * @param startNIO
     * @throws Exception
     */
    private void start(String version, boolean startNIO) throws Exception {
        if (postOffice != null) {
            postOffice.setAppVersion(version);
        }
        try {
            //4. preLaunch
            beforeStart(cli);

            //1. gracefully shutdown
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd E HH:mm:ss");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (postOffice != null) {
                    postOffice.sendAlertSync(SMTPConfig.CFG.getEmailToAppSupport(), "Shutdown at " + dtf.format(LocalDateTime.now()) + " - " + version, "EOM", null, false);
                }
            }, "ShutdownHook.BootApp")
            );

            //2. initialize JMX instrumentation
            if (instrumentationMgr != null/* && isJMXRequired()*/) {
                instrumentationMgr.start(BootConstant.VERSION);
            }

            //3. health inspection
            StringBuilder sb = new StringBuilder();
            sb.append(System.lineSeparator()).append(HealthMonitor.PROMPT);
            if (healthInspector != null) {
                List<Error> errors = healthInspector.ping(log);
                if (errors == null || errors.isEmpty()) {
                    sb.append("passed");
                    log.info(sb);
                } else {
                    String inspectionReport;
                    try {
                        inspectionReport = BeanUtil.toJson(errors, true, true);
                    } catch (Throwable ex) {
                        inspectionReport = "total " + errors.size();
                    }
                    sb.append(inspectionReport);
                    if (startNIO) {
                        HealthMonitor.setHealthStatus(false, sb.toString(), healthInspector);
                    } else {
                        log.warn(sb);
                    }
                }
            } else {
                sb.append("skipped");
                log.warn(sb);
            }

            //5. run NIO listening
            if (startNIO) {
                NioServer.bind();
            }

            //6. announcement
            log.info(() -> I18n.info.launched.format(cfgDefaultRB, version + " pid#" + BootConstant.PID));

            String fullConfigInfo = sb.toString();
            if (postOffice != null) {
                postOffice.sendAlertAsync(SMTPConfig.CFG.getEmailToAppSupport(), "Started at " + dtf.format(LocalDateTime.now()), fullConfigInfo, null, false);
            }
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(cfgDefaultRB), ex);
            System.exit(1);
        }
    }

    /**
     * callback before NIO binding
     *
     * @param cli
     * @throws Exception
     */
    abstract protected void beforeStart(final CommandLine cli) throws Exception;

    private Boolean jmxRequired;

    protected boolean isJMXRequired() {
//        if(log.isDebugEnabled()) {
//            return true;
//        }
        if (jmxRequired != null) {
            return jmxRequired;
        }
        jmxRequired = false;
        List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : vmArguments) {
            // if it's the agent argument : we ignore it otherwise the
            // address of the old application and the new one will be in conflict
            if (arg.contains("com.sun.management.jmxremote.port")) {
                jmxRequired = true;
                break;
            }
        }
        return jmxRequired;
    }

    /**
     * Sun property pointing the main class and its arguments. Might not be
     * defined on non Hotspot VM implementations.
     */
    private static final String SUN_JAVA_COMMAND = "sun.java.command";

    /**
     *
     * @return
     */
    private String getStartCommand() {
        //try {
        String OS = System.getProperty("os.name").toLowerCase();
        boolean isWindows = OS.contains("win");
        // java binary
        String java = System.getProperty("java.home") + "/bin/java";
        // vm arguments
        List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        StringBuffer vmArgsOneLine = new StringBuffer();
        for (String arg : vmArguments) {
            // if it's the agent argument : we ignore it otherwise the
            // address of the old application and the new one will be in conflict
            if (!arg.contains("-agentlib")) {
                vmArgsOneLine.append(arg);
                vmArgsOneLine.append(" ");
            }
            if (arg.contains("com.sun.management.jmxremote.port")) {
                jmxRequired = true;
            }
        }
        // run the command to execute, add the vm args
        final StringBuilder cmd = isWindows
                ? new StringBuilder("\"" + java + "\" " + vmArgsOneLine)
                : new StringBuilder(java + " " + vmArgsOneLine);

        // program main and program arguments
        String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");
        // program main is a jar
        if (mainCommand[0].endsWith(".jar")) {
            // if it's a jar, add -jar mainJar
            cmd.append("-jar ").append(new File(mainCommand[0]).getPath());
        } else {
            // else it's a .class, add the classpath and mainClass
            cmd.append("-cp \"").append(System.getProperty("java.class.path")).append("\" ").append(mainCommand[0]);
        }
        // finally add program arguments
        for (int i = 1; i < mainCommand.length; i++) {
            cmd.append(" ");
            cmd.append(mainCommand[i]);
        }
        return cmd.toString();
    }

//    /**
//     * Restart the current Java application
//     *
//     * @param cmd
//     * @param runBeforeRestart some custom code to be preLaunch before restarting
//     * @param reason
//     */
//    public static void restartApplication(String cli, Runnable runBeforeRestart, Object reason) {
//        // execute the command in a shutdown hook, to be sure that all the
//        // resources have been disposed before restarting the application
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void preLaunch() {
//                try {
//                    if (isWindows) {
//                        System.out.println("self restarting (" + reason + "): " + command);
//                        Runtime.getRuntime().exec(command);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        // execute some custom code before restarting
//        if (runBeforeRestart != null) {
//            runBeforeRestart.preLaunch();
//        }
//        log.fatal("self restarting (" + reason + "): " + cli.toString());
//        System.exit(0);
//    }
}
