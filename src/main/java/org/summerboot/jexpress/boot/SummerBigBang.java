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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.util.Modules;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Inspector;
import org.summerboot.jexpress.boot.annotation.Order;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.quartz.QuartzUtil;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import org.summerboot.jexpress.security.EncryptorUtil;
import org.summerboot.jexpress.security.JwtUtil;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.PropertiesFile;
import org.summerboot.jexpress.util.ReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * In Code We Trust
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerBigBang extends SummerSingularity {

    protected final Module userOverrideModule;
    protected Injector guiceInjector;
    protected List<SummerInitializer> summerInitializers = new ArrayList<>();
    protected List<SummerRunner> summerRunners = new ArrayList<>();
    protected Scheduler scheduler;//scheduler = new StdSchedulerFactory().getScheduler();
    protected int schedulerTriggers = 0;

    protected SummerBigBang(Class callerClass, Module userOverrideModule, String... args) {
        super(callerClass, args);
        this.userOverrideModule = userOverrideModule;
        bang();
        aParallelUniverse(args);
    }

    public Injector getGuiceInjector() {
        return guiceInjector;
    }

    protected void bang() {
        log.trace("");
        guiceInjector = null;
        summerInitializers.clear();
        summerRunners.clear();
        schedulerTriggers = 0;
    }

    protected <T extends SummerApplication> T aParallelUniverse(String... args) {
        log.trace("");
        bigBang_LetThereBeCLI(args);
        bigBang_AndThereWasCLI();

        /*
         * 2. load configs:
         * all configs depend on SummerBigBang.CLI_CONFIG_DOMAIN result
         * AuthConfig depends on Ioc scan result: JaxRsRequestProcessor scan @DeclareRoles to verify Role-Mapping in configuration file
         */
        loadBootConfigFiles(ConfigUtil.ConfigLoadMode.app_run);

        /*
         * 3. let caller to init app
         */
        for (SummerInitializer summerInitializer : summerInitializers) {
            log.trace("initApp.before.guiceInjector: {}", summerInitializer);
            summerInitializer.initAppBeforeIoC(userSpecifiedConfigDir);
        }

        /*
         * 4. should be invoked after log4j was initialized to avoid caller invokes LogManager.static{}
         * on User Specified ImplTags Ready
         */
        genesis(primaryClass, userSpecifiedalternativeNames);//trigger subclass to init IoC container

        /*
         * 5. let caller to init app
         */
        for (SummerInitializer summerInitializer : summerInitializers) {
            log.trace("initApp.after.guiceInjector: {}", summerInitializer);
            summerInitializer.initAppAfterIoC(userSpecifiedConfigDir, guiceInjector);
        }

        return (T) this;
    }

    protected void bigBang_LetThereBeCLI(String[] args) {
        log.trace("");
        memo.append(BootConstant.BR).append("\t- CLI.init: args=").append(Arrays.asList(args));
        Option arg = Option.builder(BootConstant.CLI_USAGE)
                .desc("Usage/Help")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_VERSION)
                .desc("check application version")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_CONFIG_MONITOR_INTERVAL)
                .desc("configuration monitoring interval in second (default " + userSpecifiedCfgMonitorThrottleMillis + " seconds)")
                .hasArg().argName("second")
                .build();
        cliOptions.addOption(arg);

        arg = new Option(BootConstant.CLI_I8N, true, "language <en | fr-CA>");
        arg.setRequired(false);
        cliOptions.addOption(arg);

        if (availableImplTagOptions != null && !availableImplTagOptions.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(availableImplTagOptions);
            arg = Option.builder(BootConstant.CLI_USE_ALTERNATIVE).desc("launch application with selected implementations, valid values <" + validOptions + ">")
                    .hasArgs().argName("items")
                    .build();
            arg.setArgs(Option.UNLIMITED_VALUES);
            arg.setRequired(false);
            cliOptions.addOption(arg);
        }

        arg = Option.builder(BootConstant.CLI_CONFIG_DOMAIN)
                .desc("Start the program using the configuration in the ../" + BootConstant.DIR_STANDALONE + "_<domain_name> directory. For example, if you specify -" + BootConstant.CLI_CONFIG_DOMAIN + " foo, the program will load the configuration files from the ../" + BootConstant.DIR_STANDALONE + "_foo directory. If this option is not specified, the program will start using the default configuration files.")
                .hasArg().argName("domain suffix")
                .build();
        cliOptions.addOption(arg);
        arg = Option.builder(BootConstant.CLI_CONFIG_DIR)
                .desc("the path to load the configuration files, or load from current folder when not specified")
                .hasArg().argName("path")
                .build();
        cliOptions.addOption(arg);

        if (scanedJExpressConfigs != null && !scanedJExpressConfigs.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
            arg = Option.builder(BootConstant.CLI_CONFIG_DEMO)
                    .desc("Show specified configuration template (" + validOptions + "), or when specified with -" + BootConstant.CLI_CONFIG_DOMAIN + " just dump all available configuration templates to the specified folder")
                    .hasArgs().argName("config").optionalArg(true)
                    .build();
            cliOptions.addOption(arg);
        }

        if (!availableUniqueTagOptions.isEmpty()) {
            arg = Option.builder(BootConstant.CLI_LIST_UNIQUE)
                    .desc("Show list of: " + availableUniqueTagOptions)
                    .hasArg().argName("item")
                    .build();
            cliOptions.addOption(arg);
        }

        arg = Option.builder(BootConstant.CLI_ADMIN_PWD_FILE)
                .desc("Specify an application configuration password in a file which contains a line: APP_ROOT_PASSWORD=<base64 encoded password>"
                        + BootConstant.BR + "Note: Unlike the -" + BootConstant.CLI_ADMIN_PWD + " opton, this option protects the app config password from being exposed via ps command.")
                .hasArg().argName("file")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_ADMIN_PWD)
                .desc("Specify an application config password instead of the default one."
                        + BootConstant.BR + "Note: This option exposes the app config password via ps command")
                .hasArg().argName("password")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_JWT)
                .desc("generate JWT root signing key with the specified algorithm <HS256, HS384, HS512>")
                .hasArg().argName("algorithm")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_ENCRYPT)
                .desc("Encrypt config file content with all \"DEC(plain text)\":"
                        + BootConstant.BR + BootConstant.BR + "\t -" + BootConstant.CLI_ENCRYPT + " -" + BootConstant.CLI_CONFIG_DOMAIN + " <domain> -" + BootConstant.CLI_ADMIN_PWD_FILE + " <path>"
                        + BootConstant.BR + BootConstant.BR + "\t or"
                        + BootConstant.BR + BootConstant.BR + "\t -" + BootConstant.CLI_ENCRYPT + " -" + BootConstant.CLI_CONFIG_DOMAIN + " <domain> -" + BootConstant.CLI_ADMIN_PWD + " <password>")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_PSV)
                .hasArg().argName("envId")
                .desc("Generate configuration list in PSV format with the specified environment id"
                        + BootConstant.BR + BootConstant.BR + "\t -" + BootConstant.CLI_PSV + " <envId> -" + BootConstant.CLI_CONFIG_DOMAIN + " <domain>")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_DEBUGMODE)
                .hasArg(false)
                .desc("this will ignore @Log settings and not mask any sensitive data in logs")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(BootConstant.CLI_DECRYPT)
                .desc("Decrypt config file content with all \"ENC(encrypted text)\" using password:"
                        + BootConstant.BR + BootConstant.BR + BootConstant.BR + "\t -" + BootConstant.CLI_DECRYPT + " -" + BootConstant.CLI_CONFIG_DOMAIN + " <path> -" + BootConstant.CLI_ADMIN_PWD + " <password>")
                .build();
        cliOptions.addOption(arg);

        summerInitializers.addAll(scanImplementation_SummerInitializer());
        for (SummerInitializer summerInitializer : summerInitializers) {
            summerInitializer.initCLI(cliOptions);
        }

        try {
            CommandLineParser parser = new DefaultParser();
            cli = parser.parse(cliOptions, args);
        } catch (ParseException ex) {
            cliHelpFormatter.printHelp(appVersion, cliOptions);
            ApplicationUtil.RTO(BootErrorCode.RTO_CLI_PARSER_ERROR, ex.getMessage(), null);
        }
    }

    protected List<SummerInitializer> scanImplementation_SummerInitializer() {
        log.trace("");
        List<SummerInitializer> summerCLIs = new ArrayList<>();
        Set<Class<? extends SummerInitializer>> summerCLI_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(SummerInitializer.class, callerRootPackageNames);
        //prepare ordering
        Set<Integer> orderSet = new TreeSet<>();
        Map<Integer, List<SummerInitializer>> orderMapping = new HashMap<>();
        //process scan result
        for (Class<? extends SummerInitializer> c : summerCLI_ImplClasses) {
            //get order
            int order = 0;
            Order o = c.getAnnotation(Order.class);
            if (o != null) {
                order = o.value();
            }
            //get data, cannot inject due to injector is not initialized yet
            final SummerInitializer instance;
            try {
                Constructor<? extends SummerInitializer> cc = c.getConstructor();
                cc.setAccessible(true);
                instance = cc.newInstance();
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                     | IllegalArgumentException | InvocationTargetException ex) {
                throw new InaccessibleObjectException("Failed to call default constructor of " + c.getName());
            }

            //sort data by order
            orderSet.add(order);
            List<SummerInitializer> sameOoderCLIs = orderMapping.get(order);
            if (sameOoderCLIs == null) {
                sameOoderCLIs = new ArrayList<>();
                orderMapping.put(order, sameOoderCLIs);
            }
            sameOoderCLIs.add(instance);
        }
        for (int order : orderSet) {
            summerCLIs.addAll(orderMapping.get(order));
        }
        return summerCLIs;
    }

    protected boolean runCLI_Utils() {
        log.trace("");
        boolean continueCLI = true;
        // usage
        if (cli.hasOption(BootConstant.CLI_USAGE)) {
            continueCLI = false;
            cliHelpFormatter.printHelp(appVersion, cliOptions);
        }
        // callerVersion
        if (cli.hasOption(BootConstant.CLI_VERSION)) {
            continueCLI = false;
            System.out.println(appVersion);
        }
        // generate CLI_JWT root signing key
        if (cli.hasOption(BootConstant.CLI_JWT)) {
            continueCLI = false;
            String algorithm = cli.getOptionValue(BootConstant.CLI_JWT);// <HS256, HS384, HS512>
            MacAlgorithm signatureAlgorithm;
            switch (algorithm) {
                case "HS256" -> signatureAlgorithm = Jwts.SIG.HS256;
                case "HS384" -> signatureAlgorithm = Jwts.SIG.HS384;
                case "HS512" -> signatureAlgorithm = Jwts.SIG.HS512;
                default -> {
                    System.out.println("invalid -" + BootConstant.CLI_JWT + " value: " + algorithm + ", valid -" + BootConstant.CLI_JWT + " values: <HS256, HS384, HS512>");
                    return false;
                }
            }
            String jwt = JwtUtil.buildSigningKey(signatureAlgorithm);
            System.out.println(jwt);
        }
        // check unique
        if (cli.hasOption(BootConstant.CLI_LIST_UNIQUE)) {
            continueCLI = false;
            String tag = cli.getOptionValue(BootConstant.CLI_LIST_UNIQUE);
            StringBuilder sb = new StringBuilder();
            String error = scanAnnotation_Unique(callerRootPackageNames, sb, tag);
            if (error != null) {
                System.out.println(error);
            } else {
                System.out.println(sb);
            }
        }
        return continueCLI;
    }

    protected void bigBang_AndThereWasCLI() {
        log.trace("");
        if (!runCLI_Utils()) {
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, null, null);
        }
        /*
         * [Config File] Security - init app config password
         */
        String masterPassword = "";
        final String masterPasswordFileName;
        if (cli.hasOption(BootConstant.CLI_ADMIN_PWD_FILE)) {
            masterPasswordFileName = cli.getOptionValue(BootConstant.CLI_ADMIN_PWD_FILE);
        } else if (cli.hasOption(BootConstant.CLI_ADMIN_PWD)) {// "else" = only one option, cannot both
            masterPasswordFileName = null;
            masterPassword = cli.getOptionValue(BootConstant.CLI_ADMIN_PWD);
        } else {
            masterPasswordFileName = BootConstant.DEFAULT_MASTER_PASSWORD_FILE;
        }
        try {
            if (masterPasswordFileName != null) {
                File masterPasswordFile = new File(masterPasswordFileName).getCanonicalFile();
                MasterPassword.cfg.load(masterPasswordFile, true);
                masterPassword = MasterPassword.cfg.getMasterPassword();
            }
            EncryptorUtil.setMasterPassword(masterPassword);
        } catch (Throwable ex) {
            ApplicationUtil.RTO(BootErrorCode.RTO_CFG_LOADING_ERROR, "Failed to load master password: " + ex.toString(), null);
        }


        /*
         * [Config File] Monitoring - set configuration Change Monitor Interval
         */
        if (cli.hasOption(BootConstant.CLI_CONFIG_MONITOR_INTERVAL)) {
            String cmi = cli.getOptionValue(BootConstant.CLI_CONFIG_MONITOR_INTERVAL);
            userSpecifiedCfgMonitorThrottleMillis = Integer.parseInt(cmi);
        }

        /*
         * show config on demand
         */
        if (cli.hasOption(BootConstant.CLI_CONFIG_DEMO) && !cli.hasOption(BootConstant.CLI_CONFIG_DIR)) {
            String cfgName = cli.getOptionValue(BootConstant.CLI_CONFIG_DEMO);
            if (cfgName == null) {
                String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
                String msg = "Missing config option, valid values <" + validOptions + ">";
                ApplicationUtil.RTO(BootErrorCode.RTO_CLI_MISSING_ARG_ERROR, msg, null);
            }
            Class c = scanedJExpressConfigs.get(cfgName).cfgClass;
            if (c == null) {
                String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
                String msg = cfgName + "is an invalid config option, valid values <" + validOptions + ">";
                ApplicationUtil.RTO(BootErrorCode.RTO_CLI_INVALID_ARG_ERROR, msg, null);
            }
            String t = BootConfig.generateTemplate(c);
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, t, null);
        }

        log.trace(() -> I18n.info.launching.format(userSpecifiedResourceBundle) + ", cmi=" + userSpecifiedCfgMonitorThrottleMillis + ", StartCommand>" + jvmStartCommand);

        /*
         * [Config File] - encrypt/decrypt
         */
        if (cli.hasOption(BootConstant.CLI_ENCRYPT)) {
            int updated = loadBootConfigFiles(ConfigUtil.ConfigLoadMode.cli_encrypt);
            String msg = BootConstant.BR + "\t " + updated + " config items have been encrypted in " + userSpecifiedConfigDir.getAbsolutePath();
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, msg, null);
        } else if (cli.hasOption(BootConstant.CLI_DECRYPT)) {
            if (cli.hasOption(BootConstant.CLI_ADMIN_PWD_FILE)) {
                String msg = BootConstant.BR + "\t error: -" + BootConstant.CLI_ADMIN_PWD_FILE + " is not allowed for decryption, please private password with -" + BootConstant.CLI_ADMIN_PWD + " option when decrypt data";
                ApplicationUtil.RTO(BootErrorCode.RTO_CLI_INVALID_ARG_ERROR, msg, null);
            }
            int updated = loadBootConfigFiles(ConfigUtil.ConfigLoadMode.cli_decrypt);
            String msg = BootConstant.BR + "\t " + updated + " config items have been decrypted in " + userSpecifiedConfigDir.getAbsolutePath();
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, msg, null);
        }

        /*
         * [generate configuration list in PSV format]
         */
        if (cli.hasOption(BootConstant.CLI_PSV)) {
            String envId = cli.getOptionValue(BootConstant.CLI_PSV);
            StringBuilder sb = new StringBuilder();
            //File path = Paths.get(userSpecifiedConfigDir.getAbsolutePath(), BootConstant.DIR_CONFIGURATION).toFile();
            System.out.println("loading from " + userSpecifiedConfigDir.getAbsolutePath());
            for (final File configFile : userSpecifiedConfigDir.listFiles()) {
                if (!configFile.isFile()) {
                    continue;
                }
                String fileName = configFile.getName();
                if (!fileName.endsWith(".properties")) {
                    continue; //skip non-properties file
                }

                System.out.println("loading " + configFile.getAbsolutePath());
                try {
                    PropertiesFile propertiesFile = new PropertiesFile();
                    List<ImmutablePair<String, String>> pairs = propertiesFile.load(configFile);
                    for (ImmutablePair<String, String> pair : pairs) {
                        String key = pair.getKey();
                        String value = pair.getValue();
                        sb.append(key).append("|").append(value).append("|").append(envId).append("|").append(fileName).append(BootConstant.BR);
                    }
                } catch (IOException ex) {
                    sb.append("Failed to generate configurations list in PSV format: " + configFile.getAbsolutePath()).append(BootConstant.BR);
                    sb.append(ExceptionUtils.getRootCauseMessage(ex)).append(BootConstant.BR);
                }
            }
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, "\n\n" + sb, null);
        }

        if (cli.hasOption(BootConstant.CLI_DEBUGMODE)) {
            BackOffice.agent.isDebugMode = true;
        }

        /*
         * [IoC] - set user selected implementations to override the default
         * should be invoked before genesis was initialezed to avoid caller invoks LogManager.static{}
         */
        if (cli.hasOption(BootConstant.CLI_USE_ALTERNATIVE)) {
            userSpecifiedalternativeNames.clear();
            String[] mockItemList = cli.getOptionValues(BootConstant.CLI_USE_ALTERNATIVE);

            Set<String> mockInputValues = new HashSet<>(Arrays.asList(mockItemList));
            mockInputValues.remove("");
            if (availableImplTagOptions.containsAll(mockInputValues)) {
                userSpecifiedalternativeNames.addAll(mockInputValues);
            } else {
                Set<String> invalidOptions = new HashSet<>(mockInputValues);
                invalidOptions.removeAll(availableImplTagOptions);
                String msg = "invalid -" + BootConstant.CLI_USE_ALTERNATIVE + " value: " + FormatterUtil.toCSV(invalidOptions) + ", valid -" + BootConstant.CLI_USE_ALTERNATIVE + " values: " + FormatterUtil.toCSV(availableImplTagOptions);
                ApplicationUtil.RTO(BootErrorCode.RTO_CLI_INVALID_ALTERNATIVE_NAME_ERROR, msg, null);
            }
        }

        /*
         * [Config File] - generate template
         */
        if (cli.hasOption(BootConstant.CLI_CONFIG_DEMO)) {
            int i = 0;
            for (String cfgName : scanedJExpressConfigs.keySet()) {
                Class c = scanedJExpressConfigs.get(cfgName).cfgClass;
                try {
                    ConfigUtil.createConfigFile(c, userSpecifiedConfigDir, cfgName, true);
                } catch (IOException ex) {
                    String msg = ex + BootConstant.BR + "\tFailed to generate config file (" + cfgName + ") in " + userSpecifiedConfigDir.getAbsolutePath();
                    ApplicationUtil.RTO(BootErrorCode.RTO_CFG_GENERATE_ERROR, msg, ex);
                }
                i++;
            }
            String msg = "Total generated " + i + " configuration files in " + userSpecifiedConfigDir.getAbsolutePath();
            ApplicationUtil.RTO(BootErrorCode.RTO_CLS_EXIT, msg, null);
        }

        /*
         * [i8n] - determine Resourc eBundle
         */
        if (cli.hasOption(BootConstant.CLI_I8N)) {
            String language = cli.getOptionValue(BootConstant.CLI_I8N);
            userSpecifiedResourceBundle = Locale.forLanguageTag(language);
        } else {
            userSpecifiedResourceBundle = null;
        }
        I18n.init(getAddtionalI18n());
    }

    abstract protected Class getAddtionalI18n();

    /**
     * initialize based on config files in configDir
     *
     * @param mode
     * @return
     */
    protected int loadBootConfigFiles(ConfigUtil.ConfigLoadMode mode) {
        log.trace("");
        Map<String, JExpressConfig> configs = new LinkedHashMap<>();
        int updated = 0;

        /*switch (mode) {
            case app_run:
                if (!hasControllers) {
                    memo.append(BootConstant.BR).append("\t- cfg.loading.skip: no @Controller found, skip=").append(NioConfig.class.getSimpleName());
                    scanedJExpressConfigs.remove(NioConfig.class.getSimpleName());
                }
                if (!hasGRPCImpl) {
                    memo.append(BootConstant.BR).append("\t- cfg.loading.skip: no gRPC Server stub found, skip=").append(GRPCServerConfig.class.getSimpleName());
                    scanedJExpressConfigs.remove(GRPCServerConfig.class.getSimpleName());
                }
//                if (!hasAuthImpl) {
//                    memo.append("\n\t- agent.loading.skip: no @DeclareRoles or @RolesAllowed found in any @Controller, skip=").append(AuthConfig.class.getSimpleName());
//                    scanedJExpressConfigs.remove(AuthConfig.class.getSimpleName());
//                }
                break;
        }*/
        try {
            //1. get main configurations
            for (ConfigMetadata registeredAppConfig : scanedJExpressConfigs.values()) {
                if (isUserSpecifieduserSpecifiedalternativeNames(registeredAppConfig.whenUseAlternative) ^ registeredAppConfig.thenLoadConfig) {
                    continue;
                }
                JExpressConfig instance = registeredAppConfig.instance;
                if (instance == null) {
                    instance = BootConfig.instance(registeredAppConfig.cfgClass);
                }
                memo.append(BootConstant.BR).append("\t- cfg.loading=").append(instance).append(", info=").append(registeredAppConfig);
                if (instance == null) {
                    continue;
                }
                configs.put(registeredAppConfig.configFileName, instance);
            }

            //2. load configurations
            updated = ConfigUtil.loadConfigs(mode, log, userSpecifiedResourceBundle, userSpecifiedConfigDir.toPath(), configs, userSpecifiedCfgMonitorThrottleMillis, userSpecifiedConfigDir);
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(userSpecifiedResourceBundle), ex);
            ApplicationUtil.RTO(BootErrorCode.RTO_CFG_LOADING_ERROR, null, null);
        }
        return updated;
    }

    //abstract protected void runCLI(CommandLine bigBang, File cfgConfigDir);
    private static final String[] NA = {};

    protected boolean isUserSpecifieduserSpecifiedalternativeNames(String... alternativeNames) {
        if (alternativeNames == null) {
            alternativeNames = NA;
        }
        return Arrays.stream(alternativeNames).anyMatch(userSpecifiedalternativeNames::contains);
    }

    /**
     * Triggered by CLI CLI_USE_IMPL, then to trigger subclass to init IoC
     * container.IoC container initialization should happened after CLI and load
     * configuration, it will called when SummerBigBang.CLI_USE_IMPL result is
     * ready
     *
     * @param primaryClass
     * @param userSpecifiedalternativeNames
     */
    protected void genesis(Class primaryClass, Set<String> userSpecifiedalternativeNames) {
        log.trace("");
        BootGuiceModule defaultModule = new BootGuiceModule(this, primaryClass, userSpecifiedalternativeNames, memo);
        ScanedGuiceModule scanedModule = new ScanedGuiceModule(scanedServiceBindingMap, userSpecifiedalternativeNames, channelHandlerNames, memo);
        Module bootModule = Modules.override(defaultModule).with(scanedModule);
        Module applicationModule = userOverrideModule == null
                ? bootModule
                : Modules.override(bootModule).with(userOverrideModule);
//        if (bindingChannelHandlerClass != null) {
//            Module enabledModule = new Module() {
//                @Override
//                protected void configure() {
//                    if (bindingChannelHandlerClass != null) {
//                        bind(ChannelHandler.class).annotatedWith(Names.named(bindingChannelHandlerBindingName)).to(bindingChannelHandlerClass);
//                    }
//                }
//            };
//            guiceModule = Modules.override(guiceModule).with(enabledModule);
//        }
        if (userOverrideModule == null) {
            memo.append(BootConstant.BR).append("\t- init default Ioc @Conponent");
        } else {
            memo.append(BootConstant.BR).append("\t- init user overridden Ioc @Conponent via").append(userOverrideModule.getClass().getName());
        }

        // Guice.createInjector(module) --> ScanedGuiceModule.configure() --> this will trigger SummerBigBang.onGuiceInjectorCreated_ControllersInjected
        guiceInjector = Guice.createInjector(Stage.PRODUCTION, applicationModule);
        //NioConfig.agent.setGuiceInjector(guiceInjector);
        scanImplementation_SummerRunner(guiceInjector);
        schedulerTriggers = scanAnnotation_Scheduled(guiceInjector, callerRootPackageNames);
    }

    /**
     * callback by Guice Module.
     * <p>
     * triggered by
     * <code>Guice.createInjector(module) --> BootGuiceModule.configure() --> BootGuiceModule.scanAnnotation_BindInstance(...)</code>
     * to load all classes annotated with @Controller
     *
     * @param controllers
     */
    @Inject
    protected void onGuiceInjectorCreated_ControllersInjected(@Controller Map<String, Object> controllers) {
        log.trace("");
        //1. scan and register controllers
        JaxRsRequestProcessorManager.registerControllers(controllers, memo);
    }

    @Inject
    protected void onGuiceInjectorCreated_DefaultHealthInspectorInjected(@Inspector Map<String, Object> defaultHealthInspectors) {
        log.trace("");
        HealthMonitor.registerDefaultHealthInspectors(defaultHealthInspectors, memo);
    }

    protected void scanImplementation_SummerRunner(Injector injector) {
        log.trace("");
        Set<Class<? extends SummerRunner>> summerRunner_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(SummerRunner.class, callerRootPackageNames);
        //prepare ordering
        Set<Integer> orderSet = new TreeSet<>();
        Map<Integer, List<SummerRunner>> orderMapping = new HashMap<>();
        //process scan result
        for (Class<? extends SummerRunner> c : summerRunner_ImplClasses) {
            //get order
            int order = 0;
            Order o = c.getAnnotation(Order.class);
            if (o != null) {
                order = o.value();
            }
            //get data
            final SummerRunner instance = injector.getInstance(c);

            //sort data by order
            orderSet.add(order);
            List<SummerRunner> sameOoderRunners = orderMapping.get(order);
            if (sameOoderRunners == null) {
                sameOoderRunners = new ArrayList<>();
                orderMapping.put(order, sameOoderRunners);
            }
            sameOoderRunners.add(instance);
        }
        for (int order : orderSet) {
            summerRunners.addAll(orderMapping.get(order));
        }
    }

    protected int scanAnnotation_Scheduled(Injector injector, String... rootPackageNames) {
        log.trace("");
        int triggers = 0;
        Set<Class<? extends Job>> classes = ReflectionUtil.getAllImplementationsByInterface(Job.class, rootPackageNames);

        if (!classes.isEmpty() && scheduler == null) {
//            try {
//                scheduler = new StdSchedulerFactory().getScheduler();
//                scheduler.setJobFactory(new GuiceJobFactory(injector));
//                scheduler.getListenerManager().addJobListener(new FixedDelayJobListener());
//            } catch (SchedulerException ex) {
//                throw new RuntimeException("Filed to build a Scheduler", ex);
//            }
            scheduler = injector.getInstance(Scheduler.class);
        }
        for (Class c : classes) {
            try {
                triggers += QuartzUtil.addQuartzJob(scheduler, c);
            } catch (Throwable ex) {
                throw new RuntimeException("Filed to addQuartzJob for " + c, ex);
            }
        }
        return triggers;
    }
}
