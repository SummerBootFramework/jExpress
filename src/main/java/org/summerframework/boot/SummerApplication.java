/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.boot;

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
import org.summerframework.boot.cli.CommandLineRunner;
import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.ConfigChangeListener;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.boot.config.ConfigUtil.ConfigLoadMode;
import org.summerframework.boot.config.SummerBootConfig;
import org.summerframework.boot.instrumentation.HealthInspector;
import org.summerframework.boot.instrumentation.HealthMonitor;
import org.summerframework.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerframework.i18n.I18n;
import org.summerframework.integration.smtp.PostOffice;
import org.summerframework.integration.smtp.SMTPConfig;
import org.summerframework.nio.server.BootHttpPingHandler;
import org.summerframework.nio.server.BootHttpRequestHandler;
import org.summerframework.nio.server.HttpConfig;
import org.summerframework.nio.server.NioConfig;
import org.summerframework.nio.server.NioServer;
import org.summerframework.nio.server.NioServerContext;
import org.summerframework.security.auth.AuthConfig;
import org.summerframework.util.ApplicationUtil;
import org.summerframework.util.FormatterUtil;
import org.summerframework.util.BeanUtil;
import org.summerframework.util.ReflectionUtil;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
abstract public class SummerApplication extends CommandLineRunner {

    protected static Logger log;

    private static final String CFG_MONITOR_INTERVAL = "cmi";
    private static final String CLI_MOCKMODE = "mock";
    private static final String CLI_ERROR_CODE = "errorcode";
    private static final String CLI_POI_LIST = "poi";
    private static final String CLI_SHOW_CONFIG = "sample";
    private static final String CLI_I8N = "i18n";

    public static SummerApplication bind(Class controllerScanRootClass) {
        return new SummerApplication(controllerScanRootClass) {
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

    //app internal
    private Locale cfgDefaultRB;
    private String domainName = null;
    private Path cfgConfigDir;
    private int CfgMonitorInterval;
    private Injector iocInjector;
    private String cfgDomainFolderPrefix;
    private Class controllerScanRootClass;
    //Plugin - bind
    private Class<? extends PostOffice> bindingPostOfficeClass;
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

    protected SummerApplication() {
    }

    private SummerApplication(Class controllerScanRootClass) {
        this.controllerScanRootClass = controllerScanRootClass;
    }

    public Locale getCfgDefaultRB() {
        return cfgDefaultRB;
    }

    public String getDomainName() {
        return domainName;
    }

    public Path getCfgConfigDir() {
        return cfgConfigDir;
    }

    public String getCfgDomainFolderPrefix() {
        return cfgDomainFolderPrefix;
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
    public <T extends SummerApplication> T bind_AlertMessenger(Class<? extends PostOffice> postOfficeClass) {
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
    public <T extends SummerApplication> T bind_GuiceModule(AbstractModule appModule) {
        this.bindingAppModule = appModule;
        return (T) this;
    }

    public <T extends SummerApplication> T bind_NIOHandler(Class<? extends ChannelHandler> channelHandlerClass) {
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
    public <T extends SummerApplication> T bind_NIOHandler(Class<? extends ChannelHandler> channelHandlerClass, String channelHandlerBindingName) {
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
        final SummerBootConfig config;
        final String mockName;
        final boolean registerWhenMockIsEnabled;

        public RegisteredAppConfig(String configFileName, SummerBootConfig config, String mockName, boolean registerWhenMockIsEnabled) {
            this.configFileName = configFileName;
            this.config = config;
            this.mockName = mockName;
            this.registerWhenMockIsEnabled = registerWhenMockIsEnabled;
        }

    }

    /**
     * To bind a configuration file implemented by a SummerBootConfig instance,
     * which will be loaded and managed by SummerBoot Application
     *
     * @param <T>
     * @param configFileName file name only, without file path
     * @param config the SummerBootConfig instance
     * @return
     */
    public <T extends SummerApplication> T bind_SummerBootConfig(String configFileName, SummerBootConfig config) {
        return bind_SummerBootConfig(configFileName, config, null, false);
    }

    /**
     * To bind a configuration file implemented by a SummerBootConfig instance,
     * which will be loaded and managed by SummerBoot Application
     *
     * @param <T>
     * @param configFileName
     * @param config
     * @param mockName
     * @param registerWhenMockIsEnabled
     * @return
     */
    public <T extends SummerApplication> T bind_SummerBootConfig(String configFileName, SummerBootConfig config, String mockName, boolean registerWhenMockIsEnabled) {
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
    public <T extends SummerApplication> T enable_CLI_ListErrorCodes(Class errorCodeClass, boolean checkDuplicated) throws IllegalArgumentException, IllegalAccessException, JsonProcessingException {
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
    public <T extends SummerApplication> T enable_CLI_ListPOIs(Class poiNameClass, boolean checkDuplicated) throws IllegalArgumentException, IllegalAccessException, JsonProcessingException {
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
    public <T extends SummerApplication> T enable_CLI_MockMode(Class<? extends Enum<?>> enumClass) {
        return enable_CLI_MockMode(FormatterUtil.getEnumNames(enumClass));
    }

    /**
     * To enable mock mode
     *
     * @param <T>
     * @param mockItemNames the mock item names
     * @return
     */
    public <T extends SummerApplication> T enable_CLI_MockMode(String... mockItemNames) {
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
    public <T extends SummerApplication> T enable_CLI_ViewConfig(Class... cs) {
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
    public <T extends SummerApplication> T enable_Ping_HealthCheck(String contextRoot, String pingPath) {
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
    public <T extends SummerApplication> T enable_Ping_HealthCheck(String contextRoot, String pingPath, Class<? extends HealthInspector> healthInspectorClass) {
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
     * process CLI first then load config files without caller class, init IOC
     *
     * @param args
     * @param version
     * @throws Exception
     */
    private void init(String[] args, String version, boolean startNIO) throws Exception {
        this.version = version;

        //1. process CLI
        runCLI(args, "standalone", "configuration");

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
     * Initialize based on application command line args, use "standalone" as
     * the default domainFolderPrefix,and "configuration" as the default
     * configDirName
     *
     * <p>
     * Log4j2 will be initialized at the last step
     *
     * @param args application command line arguments
     * @param domainFolderPrefix the prefix of domain folder under the
     * application folder, for example: "standalone" is the domain prefix when
     * preLaunch\standalone_proc
     * @param configDirName the configuration folder name under domain folder
     * @return the path of configuration folder
     * @throws java.lang.Exception
     */
    private Path runCLI(String[] args, String domainFolderPrefix, String configDirName) throws Exception {
        this.cfgDomainFolderPrefix = domainFolderPrefix;
        initCLIs_BootApp();
        initCLIs_App(options);
        initCLIs_BootDefault(args);//super

        // 0. preLaunch default CLI commands first, they have no dependencies on logging, config loading, etc.
        processCLIs_BootDefault();//super

        // 1. determine if in mock mode
        if (cli.hasOption(CLI_MOCKMODE)) {
            appMockOptions.clear();
            String[] mockItemList = cli.getOptionValues(CLI_MOCKMODE);
            Set<String> mockInputValues = Set.of(mockItemList);
            if (enable_cli_validMockValues.containsAll(mockInputValues)) {
                appMockOptions.addAll(mockInputValues);
            } else {
                Set<String> invalidOptions = new HashSet(mockInputValues);
                invalidOptions.removeAll(enable_cli_validMockValues);
                System.err.println("invalid -mock value: " + FormatterUtil.toCSV(invalidOptions) + ", valid -mock values: " + FormatterUtil.toCSV(enable_cli_validMockValues));
                System.exit(1);
            }
        }

        if (cli.hasOption(CLI_SHOW_CONFIG)) {
            String cfgName = cli.getOptionValue(CLI_SHOW_CONFIG);
            Class c = enable_cli_validConfigs.get(cfgName);
            if (c == null) {
                System.err.println(cfgName + "is invalid config option");
                System.exit(1);
            } else {
                String t = AbstractSummerBootConfig.generateTemplate(c);
                System.out.println(t);
                System.exit(0);
            }
        }

        // 2. determine locale
        I18n.init(getAddtionalI18n());
        if (cli.hasOption(CLI_I8N)) {
            String language = cli.getOptionValue(CLI_I8N);
            cfgDefaultRB = Locale.forLanguageTag(language);
        } else {
            cfgDefaultRB = null;
        }

        // 3. determine config Change Monitor Interval        
        if (cli.hasOption(CFG_MONITOR_INTERVAL)) {
            String cmi = cli.getOptionValue(CFG_MONITOR_INTERVAL);
            CfgMonitorInterval = Integer.parseInt(cmi);
        } else {
            CfgMonitorInterval = 5;
        }

        // 4. determine config folder        
        if (cli.hasOption(DOMAIN)) {
            domainName = cli.getOptionValue(DOMAIN).trim();
            System.setProperty("domainName", domainName);
        }
        cfgConfigDir = ConfigUtil.cfgRoot(domainFolderPrefix, domainName, configDirName);

        // 5. preLaunch application CLI commands , they have dependencies on domain's configDir.
        processCLIs_BootApp(cli);
        processCLIs_App(cli);

        // 6. All CLI is done, then use domainFolderPrefix when domainName is null
        File folder = cfgConfigDir.toFile();
        if (!folder.isDirectory() || !folder.exists()) {
            System.err.println("Could not find domain: " + cfgConfigDir.getParent());
            System.exit(1);
        }

        // 7. run logging
        String log4j2ConfigFile = Paths.get(cfgConfigDir.toString(), "log4j2.xml").toString();
        System.setProperty(BootConstant.LOG4J2_KEY, log4j2ConfigFile);
        System.out.println(I18n.info.launchingLog.format(cfgDefaultRB, System.getProperty(BootConstant.LOG4J2_KEY)));
        log = LogManager.getLogger(SummerApplication.class);
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

        Option arg = Option.builder(DOMAIN)
                .desc("Start server from " + cfgDomainFolderPrefix + "_<name>"
                        + System.lineSeparator() + System.lineSeparator() + "The -domain option enables multiple domain folders, this is also useful with docker, this -domian option tells the application which domain to use."
                        + System.lineSeparator() + System.lineSeparator() + "The <domain name>: under the application folder there will be one or more folders in the format of standalone_<domain name>, by default there will be standalone_release folder, so release is the domain name of this configuration domain.")
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
     * preLaunch application CLI commands , they have dependencies on domain's
     * configDir.
     *
     * @param cli
     * @throws java.lang.Exception
     */
    private void processCLIs_BootApp(final CommandLine cli) throws Exception {
        // encrypt/decrypt config files
        if (cli.hasOption(ENCRYPT) && cli.hasOption(DOMAIN)) {
            boolean encrypt = Boolean.parseBoolean(cli.getOptionValue(ENCRYPT));
            if (!encrypt && cli.hasOption(ADMIN_PWD_FILE)) {
                System.err.println(System.lineSeparator() + "\t error: please private password with -auth option when decrypt data");
                System.exit(1);
            }
            int updated = loadBootConfigs(encrypt ? ConfigLoadMode.cli_encrypt : ConfigLoadMode.cli_decrypt, null);
            if (updated > 0) {
                String runtimeDomain = cli.getOptionValue(DOMAIN).trim();
                System.out.println(System.lineSeparator() + "\t success: config files in standalone_" + runtimeDomain + " have been " + (encrypt ? "encrypted" : "decrypted"));
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
        Map<String, SummerBootConfig> configs = new LinkedHashMap<>();
        configs.put("cfg_auth.properties", AuthConfig.CFG);
        configs.put("cfg_http.properties", HttpConfig.CFG);
        configs.put("cfg_nio.properties", NioConfig.CFG);
        configs.put("cfg_smtp.properties", SMTPConfig.CFG);
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
            sb.append(System.lineSeparator()).append("Self Inspection ");
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
                    sb.append("failed: ").append(inspectionReport);
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
                postOffice.sendAlertAsync(SMTPConfig.CFG.getEmailToAppSupport(), "Started at " + dtf.format(LocalDateTime.now()) + " - " + version, fullConfigInfo, null, false);
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
    private final String getStartCommand() {
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
