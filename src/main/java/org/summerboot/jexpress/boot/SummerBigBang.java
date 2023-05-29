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
import org.summerboot.jexpress.security.JwtUtil;
import org.summerboot.jexpress.security.SecurityUtil;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Order;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import org.summerboot.jexpress.security.EncryptorUtil;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.ReflectionUtil;

/**
 * In Code We Trust
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerBigBang extends SummerSingularity {

    /*
     * CLI commands
     */
    protected static final String USAGE = "?";
    protected static final String CLI_VERSION = "version";
    protected static final String CLI_CONFIG_MONITOR_INTERVAL = "monitorInterval";
    protected static final String CLI_I8N = "i18n";
    protected static final String CLI_USE_IMPL = "use";//To specify which implementation will be used via @Component.checkImplTagUsed
    protected static final String CLI_CONFIG_DEMO = "cfgdemo";
    protected static final String CLI_LIST_UNIQUE = "list";
    protected static final String CLI_ADMIN_PWD_FILE = "authfile";
    protected static final String CLI_ADMIN_PWD = "auth";
    protected static final String CLI_JWT = "jwt";
    protected static final String CLI_ENCRYPT = "encrypt";
    protected static final String CLI_DECRYPT = "decrypt";

    private final Module userOverrideModule;
    protected Injector guiceInjector;
    protected List<SummerInitializer> summerInitializers = new ArrayList();
    protected List<SummerRunner> summerRunners = new ArrayList();


    /*
     * CLI results
     */
    protected Locale userSpecifiedResourceBundle;
    private int userSpecifiedCfgMonitorIntervalSec = 30;
    protected final Set<String> userSpecifiedImplTags = new HashSet<>();

    protected SummerBigBang(Class callerClass, Module userOverrideModule, String... args) {
        super(callerClass, args);
        this.userOverrideModule = userOverrideModule;
        singularity();
        aParallelUniverse(args);
    }

    public Injector getGuiceInjector() {
        return guiceInjector;
    }

    private void singularity() {
        guiceInjector = null;
        summerInitializers.clear();
        summerRunners.clear();
        userSpecifiedResourceBundle = null;
        userSpecifiedCfgMonitorIntervalSec = 30;
        userSpecifiedImplTags.clear();
    }

    private <T extends SummerApplication> T aParallelUniverse(String... args) {
        bigBang_LetThereBeCLI(args);
        bigBang_AndThereWasCLI();

        /*
         * 2. load configs： 
         * all configs depend on SummerBigBang.CLI_CONFIG_DOMAIN result
         * AuthConfig depends on Ioc scan result: JaxRsRequestProcessor scan @DeclareRoles to verify Role-Mapping in configuration file
         */
        loadBootConfigFiles(ConfigUtil.ConfigLoadMode.app_run);

        for (SummerInitializer summerInitializer : summerInitializers) {
            summerInitializer.initApp(userSpecifiedConfigDir);
        }

        /*
         * should be invoked after log4j was initialized to avoid caller invokes LogManager.static{}
         * on User Specified ImplTags Ready
         */
        genesis(primaryClass, userSpecifiedImplTags);//trigger subclass to init IoC container

        return (T) this;
    }

    protected CommandLine cli;
    protected final Options cliOptions = new Options();
    protected final HelpFormatter cliHelpFormatter = new HelpFormatter();

    protected void bigBang_LetThereBeCLI(String[] args) {
        memo.append("\n\t- CLI.init: args=").append(Arrays.asList(args));
        Option arg = Option.builder(USAGE)
                .desc("Usage/Help")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_VERSION)
                .desc("check application version")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_CONFIG_MONITOR_INTERVAL)
                .desc("configuration monitoring interval in second (default " + userSpecifiedCfgMonitorIntervalSec + " seconds)")
                .hasArg().argName("second")
                .build();
        cliOptions.addOption(arg);

        arg = new Option(CLI_I8N, true, "language <en | fr-CA>");
        arg.setRequired(false);
        cliOptions.addOption(arg);

        if (availableImplTagOptions != null && !availableImplTagOptions.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(availableImplTagOptions);
            arg = Option.builder(CLI_USE_IMPL).desc("launch application with selected implementations, valid values <" + validOptions + ">")
                    .hasArgs().argName("items")
                    .build();
            arg.setArgs(Option.UNLIMITED_VALUES);
            arg.setRequired(false);
            cliOptions.addOption(arg);
        }

        arg = Option.builder(CLI_CONFIG_DOMAIN)
                .desc("Start the program using the configuration in the ../standalone_<domain_name> directory. For example, if you specify --domain foo, the program will load the configuration files from the ../standalone_foo directory. If this option is not specified, the program will start using the default configuration files.")
                .hasArg().argName("domain")
                .build();
        cliOptions.addOption(arg);
        arg = Option.builder(CLI_CONFIG_DIR)
                .desc("the path to load the configuration files, or load from current folder when not specified")
                .hasArg().argName("path")
                .build();
        cliOptions.addOption(arg);

        if (scanedJExpressConfigs != null && !scanedJExpressConfigs.isEmpty()) {
            String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
            arg = Option.builder(CLI_CONFIG_DEMO)
                    .desc("Show specified configuration template (" + validOptions + "), or when specified with -" + CLI_CONFIG_DIR + " just dump all available configuration templates to the specified folder")
                    .hasArgs().argName("config").optionalArg(true)
                    .build();
            cliOptions.addOption(arg);
        }

        if (!availableUniqueTagOptions.isEmpty()) {
            arg = Option.builder(CLI_LIST_UNIQUE)
                    .desc("Show list of: " + availableUniqueTagOptions)
                    .hasArg().argName("item")
                    .build();
            cliOptions.addOption(arg);
        }

        arg = Option.builder(CLI_ADMIN_PWD_FILE)
                .desc("Specify an application configuration password in a file which contains a line: APP_ROOT_PASSWORD=<base64 encoded password>"
                        + System.lineSeparator() + "Note: Unlike the -" + CLI_ADMIN_PWD + " opton, this option protects the app config password from being exposed via ps command.")
                .hasArg().argName("file")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_ADMIN_PWD)
                .desc("Specify an application config password instead of the default one."
                        + System.lineSeparator() + "Note: This option exposes the app config password via ps command")
                .hasArg().argName("password")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_JWT)
                .desc("generate JWT root signing key with the specified algorithm <HS256, HS384, HS512>")
                .hasArg().argName("algorithm")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_ENCRYPT)
                .desc("Encrypt config file content with all \"DEC(plain text)\":"
                        + System.lineSeparator() + System.lineSeparator() + "\t -encrypt -cfgdir <path> -" + CLI_ADMIN_PWD_FILE + " <path>"
                        + System.lineSeparator() + System.lineSeparator() + "\t or"
                        + System.lineSeparator() + System.lineSeparator() + "\t -encrypt -cfgdir <path> -" + CLI_ADMIN_PWD + " <password>")
                .build();
        cliOptions.addOption(arg);

        arg = Option.builder(CLI_DECRYPT)
                .desc("Decrypt config file content with all \"ENC(encrypted text)\" using password:"
                        + System.lineSeparator() + System.lineSeparator() + System.lineSeparator() + "\t -decrypt -cfgdir <path> -" + CLI_ADMIN_PWD + " <password>")
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
            System.out.println(ex.getMessage());
            cliHelpFormatter.printHelp(appVersion, cliOptions);
            System.exit(1);
        }
    }

    protected List<SummerInitializer> scanImplementation_SummerInitializer() {
        List<SummerInitializer> summerCLIs = new ArrayList();
        Set<Class<? extends SummerInitializer>> summerCLI_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(SummerInitializer.class, callerRootPackageName);
        //prepare ordering
        Set<Integer> orderSet = new TreeSet();
        Map<Integer, List<SummerInitializer>> orderMapping = new HashMap();
        //process scan result
        for (Class<? extends SummerInitializer> c : summerCLI_ImplClasses) {
            //get order
            int order = 0;
            Order o = (Order) c.getAnnotation(Order.class);
            if (o != null) {
                order = o.value();
            }
            //get data, cannot inject due to injector is not initialized yet
            final SummerInitializer instance;
            try {
                Constructor<? extends SummerInitializer> cc = c.getConstructor();
                cc.setAccessible(true);
                instance = cc.newInstance();
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new InaccessibleObjectException("Failed to call default constructor of " + c.getName());
            }

            //sort data by order
            orderSet.add(order);
            List<SummerInitializer> sameOoderCLIs = orderMapping.get(order);
            if (sameOoderCLIs == null) {
                sameOoderCLIs = new ArrayList();
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
        boolean continueCLI = true;
        //usage
        if (cli.hasOption(USAGE)) {
            continueCLI = false;
            cliHelpFormatter.printHelp(appVersion, cliOptions);
        }
        //callerVersion
        if (cli.hasOption(CLI_VERSION)) {
            continueCLI = false;
            System.out.println(appVersion);
        }
        // generate CLI_JWT root signing key
        if (cli.hasOption(CLI_JWT)) {
            continueCLI = false;
            String algorithm = cli.getOptionValue(CLI_JWT);
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forName(algorithm);
            String jwt = JwtUtil.buildSigningKey(signatureAlgorithm);
            System.out.println(jwt);
        }
        //check unique
        if (cli.hasOption(CLI_LIST_UNIQUE)) {
            continueCLI = false;
            String tag = cli.getOptionValue(CLI_LIST_UNIQUE);
            StringBuilder sb = new StringBuilder();
            String error = scanAnnotation_Unique(callerRootPackageName, sb, tag);
            if (error != null) {
                System.out.println(error);
            } else {
                System.out.println(sb);
            }
        }
        return continueCLI;
    }

    protected void bigBang_AndThereWasCLI() {
        if (!runCLI_Utils()) {
            System.exit(0);
        }
        /*
         * [Config File] Security - init app config password
         */
        if (cli.hasOption(CLI_ADMIN_PWD_FILE)) {
            String adminPwdFile = cli.getOptionValue(CLI_ADMIN_PWD_FILE);
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(adminPwdFile);) {
                props.load(is);
            } catch (Throwable ex) {
                throw new RuntimeException("failed to load " + adminPwdFile, ex);
            }
            String adminPwd = props.getProperty("APP_ROOT_PASSWORD");
            adminPwd = SecurityUtil.base64Decode(adminPwd);
            EncryptorUtil.init(adminPwd);
        } else if (cli.hasOption(CLI_ADMIN_PWD)) {// "else" = only one option, cannot both
            String adminPwd = cli.getOptionValue(CLI_ADMIN_PWD);
            EncryptorUtil.init(adminPwd);
        }

        /*
         * [Config File] Monitoring - set configuration Change Monitor Interval
         */
        if (cli.hasOption(CLI_CONFIG_MONITOR_INTERVAL)) {
            String cmi = cli.getOptionValue(CLI_CONFIG_MONITOR_INTERVAL);
            userSpecifiedCfgMonitorIntervalSec = Integer.parseInt(cmi);
        }

        /*
         * show config on demand
         */
        if (cli.hasOption(CLI_CONFIG_DEMO) && !cli.hasOption(CLI_CONFIG_DIR) && !cli.hasOption(CLI_CONFIG_DOMAIN)) {
            String cfgName = cli.getOptionValue(CLI_CONFIG_DEMO);
            if (cfgName == null) {
                String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
                System.err.println("Missing config option, valid values <" + validOptions + ">");
                System.exit(1);
            }
            Class c = scanedJExpressConfigs.get(cfgName).cfgClass;
            if (c == null) {
                String validOptions = FormatterUtil.toCSV(scanedJExpressConfigs.keySet());
                System.err.println(cfgName + "is an invalid config option, valid values <" + validOptions + ">");
                System.exit(1);
            }
            String t = BootConfig.generateTemplate(c);
            System.out.println(t);
            System.exit(0);
        }

        log.trace(() -> I18n.info.launching.format(userSpecifiedResourceBundle) + ", cmi=" + userSpecifiedCfgMonitorIntervalSec + ", StartCommand>" + jvmStartCommand);

        /*
         * [Config File] - encrypt/decrypt
         */
        if (cli.hasOption(CLI_ENCRYPT)) {
            int updated = loadBootConfigFiles(ConfigUtil.ConfigLoadMode.cli_encrypt);
            System.out.println(System.lineSeparator() + "\t " + updated + " config items have been encrypted in " + userSpecifiedConfigDir.getAbsolutePath());
            System.exit(0);
        } else if (cli.hasOption(CLI_DECRYPT)) {
            if (cli.hasOption(CLI_ADMIN_PWD_FILE)) {
                System.err.println(System.lineSeparator() + "\t error: -" + CLI_ADMIN_PWD_FILE + " is not allowed for decryption, please private password with -" + CLI_ADMIN_PWD + " option when decrypt data");
                System.exit(1);
            }
            int updated = loadBootConfigFiles(ConfigUtil.ConfigLoadMode.cli_decrypt);
            System.out.println(System.lineSeparator() + "\t " + updated + " config items have been decrypted in " + userSpecifiedConfigDir.getAbsolutePath());
            System.exit(0);
        }

        /*
         * [IoC] - set user selected implementations to override the default
         * should be invoked before genesis was initialezed to avoid caller invoks LogManager.static{}
         */
        if (cli.hasOption(CLI_USE_IMPL)) {
            userSpecifiedImplTags.clear();
            String[] mockItemList = cli.getOptionValues(CLI_USE_IMPL);

            Set<String> mockInputValues = new HashSet(Arrays.asList(mockItemList));
            mockInputValues.remove("");
            if (availableImplTagOptions.containsAll(mockInputValues)) {
                userSpecifiedImplTags.addAll(mockInputValues);
            } else {
                Set<String> invalidOptions = new HashSet(mockInputValues);
                invalidOptions.removeAll(availableImplTagOptions);
                System.out.println("invalid -" + CLI_USE_IMPL + " value: " + FormatterUtil.toCSV(invalidOptions) + ", valid -" + CLI_USE_IMPL + " values: " + FormatterUtil.toCSV(availableImplTagOptions));
                System.exit(1);
            }
        }

        /*
         * [Config File] - generate template
         */
        if (cli.hasOption(CLI_CONFIG_DEMO)) {
            int i = 0;
            for (String cfgName : scanedJExpressConfigs.keySet()) {
                Class c = scanedJExpressConfigs.get(cfgName).cfgClass;
                try {
                    ConfigUtil.createConfigFile(c, userSpecifiedConfigDir, cfgName, true);
                } catch (IOException ex) {
                    System.out.println(ex + "\n\tFailed to generate config file (" + cfgName + ") in " + userSpecifiedConfigDir.getAbsolutePath());
                    ex.printStackTrace();
                    System.exit(1);
                }
                i++;
            }
            System.out.println("Total generated " + i + " configuration files in " + userSpecifiedConfigDir.getAbsolutePath());
            System.exit(0);
        }

        /*
         * [i8n] - determine Resourc eBundle
         */
        if (cli.hasOption(CLI_I8N)) {
            String language = cli.getOptionValue(CLI_I8N);
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
        Map<String, JExpressConfig> configs = new LinkedHashMap<>();
        int updated = 0;

        switch (mode) {
            case app_run:
                if (!hasControllers) {
                    memo.append("\n\t- cfg.loading.skip: no @Controller found, skip=").append(NioConfig.class.getSimpleName());
                    scanedJExpressConfigs.remove(NioConfig.class.getSimpleName());
                }
                if (!hasGRPCImpl) {
                    memo.append("\n\t- cfg.loading.skip: no gRPC Server stub found, skip=").append(GRPCServerConfig.class.getSimpleName());
                    scanedJExpressConfigs.remove(GRPCServerConfig.class.getSimpleName());
                }
                if (!hasAuthImpl) {
                    memo.append("\n\t- cfg.loading.skip: no @DeclareRoles or @RolesAllowed found in any @Controller, skip=").append(AuthConfig.class.getSimpleName());
                    scanedJExpressConfigs.remove(AuthConfig.class.getSimpleName());
                }
                break;
        }
        try {
            //1. get main configurations
            for (ConfigMetadata registeredAppConfig : scanedJExpressConfigs.values()) {
                if ((isUserSpecifiedImplTags(registeredAppConfig.checkImplTagUsed) == registeredAppConfig.loadWhenImplTagUsed)) {
                    JExpressConfig instance = registeredAppConfig.instance;
                    if (instance == null) {
                        instance = BootConfig.instance(registeredAppConfig.cfgClass);
                    }
                    memo.append("\n\t- cfg.loading=").append(instance).append(", info=").append(registeredAppConfig);
                    if (instance == null) {
                        continue;
                    }
                    configs.put(registeredAppConfig.configFileName, instance);
                }
            }

            //2. load configurations
            updated = ConfigUtil.loadConfigs(mode, log, userSpecifiedResourceBundle, userSpecifiedConfigDir.toPath(), configs, userSpecifiedCfgMonitorIntervalSec, userSpecifiedConfigDir);
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(userSpecifiedResourceBundle), ex);
            System.exit(1);
        }
        return updated;
    }

    //abstract protected void runCLI(CommandLine bigBang, File cfgConfigDir);
    protected boolean isUserSpecifiedImplTags(String mockItemName) {
        return userSpecifiedImplTags.contains(mockItemName);
    }

    /**
     * Triggered by CLI CLI_USE_IMPL, then to trigger subclass to init IoC
     * container.IoC container initialization should happened after CLI and load
     * configuration, it will called when SummerBigBang.CLI_USE_IMPL result is
     * ready
     *
     * @param primaryClass
     * @param userSpecifiedImplTags
     */
    protected void genesis(Class primaryClass, Set<String> userSpecifiedImplTags) {
        BootGuiceModule defaultModule = new BootGuiceModule(this, primaryClass, userSpecifiedImplTags, memo);
        ScanedGuiceModule scanedModule = new ScanedGuiceModule(scanedServiceBindingMap, userSpecifiedImplTags, memo);
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
            memo.append("\n\t- init default Ioc @Conponent");
        } else {
            memo.append("\n\t- init user overridden Ioc @Conponent via").append(userOverrideModule.getClass().getName());
        }

        // Guice.createInjector(module) --> ScanedGuiceModule.configure() --> this will trigger SummerBigBang.onGuiceInjectorCreated_ControllersInjected
        guiceInjector = Guice.createInjector(Stage.PRODUCTION, applicationModule);
        NioConfig.cfg.setGuiceInjector(guiceInjector);
        scanImplementation_SummerRunner(guiceInjector);
    }

    /**
     * callback by Guice Module.
     * <p>
     * triggered by
     * <code>Guice.createInjector(module) --> BootGuiceModule.configure() --> BootGuiceModule.scanAnnotation_BindInstance(...)</code>
     * to load all classes annotated with @Controller
     *
     *
     * @param controllers
     */
    @Inject
    protected void onGuiceInjectorCreated_ControllersInjected(@Controller Map<String, Object> controllers) {
        //1. scan and register controllers
        JaxRsRequestProcessorManager.registerControllers(controllers, memo);
    }

    protected void scanImplementation_SummerRunner(Injector injector) {
        Set<Class<? extends SummerRunner>> summerRunner_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(SummerRunner.class, callerRootPackageName);
        //prepare ordering
        Set<Integer> orderSet = new TreeSet();
        Map<Integer, List<SummerRunner>> orderMapping = new HashMap();
        //process scan result
        for (Class<? extends SummerRunner> c : summerRunner_ImplClasses) {
            //get order
            int order = 0;
            Order o = (Order) c.getAnnotation(Order.class);
            if (o != null) {
                order = o.value();
            }
            //get data
            final SummerRunner instance = injector.getInstance(c);

            //sort data by order
            orderSet.add(order);
            List<SummerRunner> sameOoderRunners = orderMapping.get(order);
            if (sameOoderRunners == null) {
                sameOoderRunners = new ArrayList();
                orderMapping.put(order, sameOoderRunners);
            }
            sameOoderRunners.add(instance);
        }
        for (int order : orderSet) {
            summerRunners.addAll(orderMapping.get(order));
        }
    }

}
