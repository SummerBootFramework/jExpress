/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, appVersionLong 2.0 (the
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

import com.google.inject.Inject;
import io.grpc.ServerServiceDefinition;
import org.summerboot.jexpress.security.JwtUtil;
import org.summerboot.jexpress.security.SecurityUtil;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Component;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Unique;
import org.summerboot.jexpress.boot.annotation.Version;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import org.summerboot.jexpress.security.EncryptorUtil;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.ReflectionUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class BootCLI implements BootConstant {

    protected static Logger log;

    /*
     * Only for JPAHibernateConfig access to scan @Entity
     */
    private static String callerRootPackageName;

    public static String getCallerRootPackageName() {
        return callerRootPackageName;
    }

    /*
     * CLI commands
     */
    protected static final String USAGE = "?";
    protected static final String CLI_VERSION = "version";
    protected static final String CLI_CONFIG_MONITOR_INTERVAL = "monitorInterval";
    protected static final String CLI_I8N = "i18n";
    protected static final String CLI_USE_IMPL = "use";//To specify which implementation will be used via @Component.checkImplTagUsed
    protected static final String CLI_CONFIG_DIR = "cfgdir";
    protected static final String CLI_CONFIG_DEMO = "cfgdemo";
    protected static final String CLI_LIST_UNIQUE = "unique";
    protected static final String CLI_ADMIN_PWD_FILE = "authfile";
    protected static final String CLI_ADMIN_PWD = "auth";
    protected static final String CLI_JWT = "jwt";
    protected static final String CLI_ENCRYPT = "encrypt";
    protected static final String CLI_DECRYPT = "decrypt";
    protected static final File CURRENT_DIR = new File("").getAbsoluteFile();
    protected static String appVersionLong = BootConstant.VERSION;

    public static String version() {
        return appVersionLong;
    }

    /*
     * CLI results
     */
    protected int userSpecifiedCfgMonitorIntervalSec = 30;
    protected Locale userSpecifiedResourceBundle;
    protected final Set<String> userSpecifiedImplTags = new HashSet<>();

    /*
     * Annotation scan results as CLI inputs
     */
    private final Set<String> availableImplTagOptions = new HashSet();
    private final List<String> availableUniqueTagOptions = new ArrayList();
    private final Map<String, ConfigMetadata> scanedJExpressConfigs = new LinkedHashMap<>();

    /*
     * Annotation scan results as BootGuiceModule input
     * Format: bindingClass <--> {ImplTag <--> [componentClasses list]}
     */
    protected final Map<Class, Map<String, List<Class>>> scanedComponentBbindingMap = new HashMap();

    protected final Class callerClass;
    protected String appVersionShort = BootConstant.VERSION;
    protected final StringBuilder memo = new StringBuilder();
    private boolean hasGRPCImpl = false;
    private boolean hasAuthImpl = false;

    protected boolean hasControllers = false;
    protected Boolean jmxRequired;
    protected String jvmStartCommand;
    protected File cfgConfigDir;

    protected BootCLI(Class callerClass, String... args) {
        this.callerClass = callerClass == null
                ? this.getClass()
                : callerClass;
        deployee(this.callerClass, args);
    }

    /**
     * callback by Guice Module.
     * <p>
     * triggered by
     * <code>Guice.createInjector(module) --> BootGuiceModule.configure()</code>
     * to load all classes annotated with @Controller
     *
     *
     * @param controllers
     */
    @Inject
    protected void callbackGuice_scanAnnotation_Controller(@Controller Map<String, Object> controllers) {
        //1. scan and register controllers
        hasControllers = !controllers.isEmpty();
        JaxRsRequestProcessorManager.registerControllers(controllers, memo);

        //2. check is there any declared roles so that the auth config should be used
        final AuthConfig authCfg = AuthConfig.instance(AuthConfig.class);
        hasAuthImpl = !authCfg.getDeclareRoles().isEmpty();
    }

    private void deployee(Class callerClass, String... args) {
        memo.append("\n\t- deployee callerClass=").append(this.callerClass.getName());
        jvmStartCommand = scanJVM_StartCommand();
        scanAnnotation_Version(callerClass);
        callerRootPackageName = ReflectionUtil.getRootPackageName(callerClass);
        System.setProperty("approotpackage", callerRootPackageName);
        System.setProperty("appappname", appVersionShort);
        memo.append("\n\t- callerRootPackageName=").append(callerRootPackageName);
        String error = scanAnnotation_Unique(callerRootPackageName, memo);
        if (error != null) {
            System.out.println(error);
            System.exit(1);
        }
        scanAnnotation_ConfigImportResource("org", callerRootPackageName);
        scanAnnotation_Component(callerRootPackageName);
        scanImpl_gRPC();
        runCLI_init(args);
        if (runCLI_ThenExit()) {
            System.exit(0);
        }
        runCLI_LoadBootApplicationConfig();
    }

    /**
     * Sun property pointing the main class and its arguments. Might not be
     * defined on non Hotspot VM implementations.
     */
    protected static final String SUN_JAVA_COMMAND = "sun.java.command";

    /**
     *
     * @return
     */
    protected String scanJVM_StartCommand() {
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
        // run the command to execute, bindingMetaAdd the vm args
        final StringBuilder cmd = isWindows
                ? new StringBuilder("\"" + java + "\" " + vmArgsOneLine)
                : new StringBuilder(java + " " + vmArgsOneLine);

        // program main and program arguments
        String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");
        // program main is a jar
        if (mainCommand[0].endsWith(".jar")) {
            // if it's a jar, bindingMetaAdd -jar mainJar
            cmd.append("-jar ").append(new File(mainCommand[0]).getPath());
        } else {
            // else it's a .class, bindingMetaAdd the classpath and mainClass
            cmd.append("-cp \"").append(System.getProperty("java.class.path")).append("\" ").append(mainCommand[0]);
        }
        // finally bindingMetaAdd program arguments
        for (int i = 1; i < mainCommand.length; i++) {
            cmd.append(" ");
            cmd.append(mainCommand[i]);
        }
        return cmd.toString();
    }

    private void scanAnnotation_Version(Class callerClass) {
        Version v = (Version) callerClass.getAnnotation(Version.class);
        if (v != null) {
            appVersionShort = v.logFileName();
            if (StringUtils.isBlank(appVersionShort)) {
                appVersionShort = v.value()[0];
            }
            appVersionLong = Arrays.toString(v.value());
        } else {
            appVersionShort = "app";
        }
        memo.append("\n\t- callerVersion=").append(appVersionLong);
    }

    /**
     *
     * @param rootPackageName
     * @param sb
     * @param displayByTags
     * @return error message
     */
    private String scanAnnotation_Unique(String rootPackageName, StringBuilder sb, String... displayByTags) {
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Unique.class, rootPackageName);
        for (Class classWithUniqueValues : classes) {
            Unique u = (Unique) classWithUniqueValues.getAnnotation(Unique.class);
            String tag = u.name();
            availableUniqueTagOptions.add(tag);
            Class uniqueType = u.type();
            List<String> tags = List.of(displayByTags);
            try {
                Map<Object, Set<String>> duplicated = ApplicationUtil.checkDuplicateFields(classWithUniqueValues, uniqueType);
                if (!duplicated.isEmpty()) {
                    String report = BeanUtil.toJson(duplicated, true, false);
                    return "Duplicated " + uniqueType.getSimpleName() + " values in " + classWithUniqueValues.getSimpleName() + " " + report;
                } else if (tags.contains(tag)) {
                    Map<String, Integer> results = new HashMap();
                    ReflectionUtil.loadFields(classWithUniqueValues, uniqueType, results, false);
                    Map<Object, String> sorted = results
                            .entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())
                            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
                    String json = BeanUtil.toJson(sorted, true, false);
                    sb.append("\n").append(tag).append("=").append(json);
                }
            } catch (Throwable ex) {
                throw new RuntimeException("check unique faile on package " + rootPackageName + ".*", ex);
            }
        }
        return null;
    }

    private void scanAnnotation_ConfigImportResource(String... rootPackageNames) {
        Set<String> pakcages = Set.copyOf(List.of(rootPackageNames));
        Set<Class<? extends JExpressConfig>> classesAll = new HashSet();//to remove duplicated
        for (String rootPackageName : pakcages) {
            Set<Class<? extends JExpressConfig>> jExpressConfigClasses = ReflectionUtil.getAllImplementationsByInterface(JExpressConfig.class, rootPackageName);
            classesAll.addAll(jExpressConfigClasses);
        }

        for (Class jExpressConfigClass : classesAll) {
            int mod = jExpressConfigClass.getModifiers();
            if (Modifier.isAbstract(mod) || Modifier.isInterface(mod)) {
                continue;
            }
            String key = jExpressConfigClass.getSimpleName();
            if (scanedJExpressConfigs.containsKey(key)) {
                continue;
            }
            String configFileName = null;
            ImportResource ir = (ImportResource) jExpressConfigClass.getAnnotation(ImportResource.class);
            if (ir != null) {
                configFileName = ir.value();
                String checkImplTagUsed = ir.checkImplTagUsed();
                boolean loadWhenImplTagUsed = ir.loadWhenImplTagUsed();

                ConfigMetadata metadata = new ConfigMetadata(configFileName, jExpressConfigClass, null, checkImplTagUsed, loadWhenImplTagUsed);
                //availableAppConfigs.add(rc);
                scanedJExpressConfigs.put(key, metadata);
                memo.append("\n\t- scan.bindBootConfig: configFileName=").append(configFileName).append(", class=").append(jExpressConfigClass.getName()).append(", implTag=").append(checkImplTagUsed).append(", loadWhenImplTagUsed=").append(loadWhenImplTagUsed);
            }
            memo.append("\n\t- cfg=").append(jExpressConfigClass.getName()).append(", file=").append(configFileName);
        }
    }

    /**
     * To bind a configuration file implemented by a JExpressConfig instance,
     * which will be loaded and managed by SummerBoot Application
     *
     * @param <T>
     * @param configFileName
     * @param config
     * @param checkImplTagUsed
     * @param loadWhenImplTagUsed
     * @return
     */
    public <T extends SummerApplication> T bindBootConfig(String configFileName, JExpressConfig config, String checkImplTagUsed, boolean loadWhenImplTagUsed) {
        memo.append("\n\t- bindBootConfig: configFileName=").append(configFileName).append(", config=").append(config.getClass().getName()).append(", implTag=").append(checkImplTagUsed).append(", loadWhenImplTagUsed=").append(loadWhenImplTagUsed);
        String key = config.getClass().getSimpleName();
        ConfigMetadata metadata = new ConfigMetadata(configFileName, config.getClass(), config, checkImplTagUsed, loadWhenImplTagUsed);
        scanedJExpressConfigs.put(key, metadata);
        return (T) this;
    }

    protected List<String> scanAnnotation_Component(String... rootPackageNames) {
        Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        for (String rootPackageName : rootPackageNames) {
            Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Component.class, rootPackageName);
            classesAll.addAll(classes);
        }

        List<String> tags = new ArrayList();
        StringBuilder sb = new StringBuilder();
        for (Class componentClass : classesAll) {
            Component a = (Component) componentClass.getAnnotation(Component.class);
            String implTag = a.implTag();
            Class bindingClass = a.bind();
            List<Class> interfaces = ReflectionUtil.getAllInterfaces(componentClass);
            if (bindingClass == Component.DEFAULT || bindingClass == null) {//bindingClass not specified by using the default one
                if (interfaces.size() == 1) {//happy path
                    bindingClass = interfaces.get(0);
                } else {
                    sb.append("\nClass").append(componentClass).append(" needs to specify bind class @Component(bind=TheMissing.class) due to it has multiple Interfaces:").append(interfaces);
                    continue;
                }
            } else {
                if (!interfaces.contains(bindingClass)) {
                    sb.append("\nClass").append(componentClass).append(" specifies @Component(bind=").append(bindingClass.getSimpleName()).append(".class), but the bind class is not in its Interfaces:").append(interfaces);
                    continue;
                } // else also happy path
            }
            tags.add(implTag);
            scanAnnotation_Component_Add2BindingMap(bindingClass, implTag, componentClass);
        }
        scanAnnotation_Component_ValidateBindingMap(sb);
        if (!sb.isEmpty()) {
            System.out.println("IOC Code error:" + sb);
            System.exit(1);
        }
        List<String> componentImplTags = tags.stream()
                .distinct()
                .collect(Collectors.toList());
        componentImplTags.removeAll(Collections.singleton(null));
        componentImplTags.removeAll(Collections.singleton(""));
        availableImplTagOptions.addAll(componentImplTags);
        return componentImplTags;
    }

    private void scanAnnotation_Component_Add2BindingMap(Class bindingClass, String implTag, Class componentClass) {
        memo.append("\n\t- adding to guiceModule.bind(").append(bindingClass.getName()).append(").to(").append(componentClass.getName()).append("), implTag=").append(implTag);
        Map<String, List<Class>> componentMap = scanedComponentBbindingMap.get(bindingClass);
        if (componentMap == null) {
            componentMap = new HashMap();
            scanedComponentBbindingMap.put(bindingClass, componentMap);
        }
        List<Class> componentList = componentMap.get(implTag);
        if (componentList == null) {
            componentList = new ArrayList();
            componentMap.put(implTag, componentList);
        }
        componentList.add(componentClass);
    }

    private void scanAnnotation_Component_ValidateBindingMap(StringBuilder sb) {
        for (Class bindingClass : scanedComponentBbindingMap.keySet()) {
            Map<String, List<Class>> componentMap = scanedComponentBbindingMap.get(bindingClass);
            for (String mockTag : componentMap.keySet()) {
                List<Class> componentList = componentMap.get(mockTag);
                int size = componentList.size();
                if (size != 1) {
                    sb.append("\nIOC ").append(bindingClass).append(" required a single bean, but ").append(size).append(" were found with the same mockTag(").append(mockTag).append("): ").append(componentList);
                }
            }
        }
    }

    private void scanImpl_gRPC() {
        Set<Class<? extends ServerServiceDefinition>> gRPC_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(ServerServiceDefinition.class, callerRootPackageName);
        hasGRPCImpl = gRPC_ImplClasses != null && !gRPC_ImplClasses.isEmpty();
    }

    protected CommandLine cli;
    protected final Options cliOptions = new Options();
    protected final HelpFormatter cliHelpFormatter = new HelpFormatter();

    protected void runCLI_init(String[] args) {
        checkEnvTag(args);
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
            arg = Option.builder(CLI_USE_IMPL).desc("launch application in mock mode, valid values <" + validOptions + ">")
                    .hasArgs().argName("items")
                    .build();
            arg.setArgs(Option.UNLIMITED_VALUES);
            arg.setRequired(false);
            cliOptions.addOption(arg);
        }

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
                    .desc("list unique: " + availableUniqueTagOptions)
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

        buildCLIOptions(cliOptions);

        try {
            CommandLineParser parser = new DefaultParser();
            cli = parser.parse(cliOptions, args);
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            cliHelpFormatter.printHelp(appVersionLong, cliOptions);
            System.exit(1);
        }
    }

    abstract protected void buildCLIOptions(Options options);

    protected boolean runCLI_ThenExit() {
        boolean exit = false;
        //usage
        if (cli.hasOption(USAGE)) {
            exit = true;
            cliHelpFormatter.printHelp(appVersionLong, cliOptions);
        }
        //callerVersion
        if (cli.hasOption(CLI_VERSION)) {
            exit = true;
            System.out.println(appVersionLong);
        }
        // generate CLI_JWT root signing key
        if (cli.hasOption(CLI_JWT)) {
            exit = true;
            String algorithm = cli.getOptionValue(CLI_JWT);
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forName(algorithm);
            String jwt = JwtUtil.buildSigningKey(signatureAlgorithm);
            System.out.println(jwt);
        }
        //check unique
        if (cli.hasOption(CLI_LIST_UNIQUE)) {
            exit = true;
            String tag = cli.getOptionValue(CLI_LIST_UNIQUE);
            StringBuilder sb = new StringBuilder();
            String error = scanAnnotation_Unique(callerRootPackageName, sb, tag);
            if (error != null) {
                System.out.println(error);
            } else {
                System.out.println(sb);
            }
        }
        return exit;
    }

    protected void runCLI_LoadBootApplicationConfig() {
        /*
         * [IoC] - set user selected implementations to override the default
         * should be invoked before onUserSpecifiedImplTagsReady was initialezed to avoid caller invoks LogManager.static{}
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
                System.out.println("invalid -" + CLI_USE_IMPL + " value: " + FormatterUtil.toCSV(invalidOptions) + ", valid -mock values: " + FormatterUtil.toCSV(availableImplTagOptions));
                System.exit(1);
            }
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
         * [Config File] Location - determine the configuration path: cfgConfigDir

        memo.append("\n\t- CLI.run: workingDir=").append(unittestWorkingDir);
        if (StringUtils.isBlank(unittestWorkingDir)) {
            unittestWorkingDir = "";
        } else {
            unittestWorkingDir = unittestWorkingDir + File.separator;
        }*/
        cfgConfigDir = null;//to clear the state
        if (cli.hasOption(CLI_CONFIG_DIR)) {
            String cfgDir = cli.getOptionValue(CLI_CONFIG_DIR).trim();
            cfgConfigDir = new File(cfgDir).getAbsoluteFile();
        } else if (envTag != null) {//@Deprecated backward compatible
            String cfgDir = /*unittestWorkingDir +*/ "standalone_" + envTag + File.separator + "configuration";
            cfgConfigDir = new File(cfgDir).getAbsoluteFile();
            System.setProperty("domainName", envTag);
        }
        if (cfgConfigDir == null) {
            //show config on demand
            if (cli.hasOption(CLI_CONFIG_DEMO)) {
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
            //use current folder when user not specified
            cfgConfigDir = CURRENT_DIR;
        }
        if (cfgConfigDir != null && (!cfgConfigDir.exists() || !cfgConfigDir.isDirectory() || !cfgConfigDir.canRead())) {
            System.out.println("Could access configuration path as a folder: " + cfgConfigDir);
            System.exit(1);
        }
        if (cfgConfigDir.getAbsolutePath().equals(CURRENT_DIR.getAbsolutePath())) {
            //set log folder outside user specified config folder
            System.setProperty("loggingPath", cfgConfigDir.getAbsolutePath());
        } else {
            //set log folder outside user specified config folder
            System.setProperty("loggingPath", cfgConfigDir.getParent());
        }

        /*
         * [Config File] Log4J - init
         */
        Path logFilePath = Paths.get(cfgConfigDir.toString(), "log4j2.xml");
        if (!Files.exists(logFilePath)) {
            StringBuilder log4j2XML = new StringBuilder();
            try (InputStream ioStream = this.getClass()
                    .getClassLoader()
                    .getResourceAsStream("log4j2.xml.temp");
                    InputStreamReader isr = new InputStreamReader(ioStream);
                    BufferedReader br = new BufferedReader(isr);) {
                String line;
                while ((line = br.readLine()) != null) {
                    log4j2XML.append(line).append(System.lineSeparator());
                }
                Files.writeString(logFilePath, log4j2XML);
            } catch (IOException ex) {
                System.out.println(ex + "\n\tCould generate log4j.xml at " + logFilePath);
                ex.printStackTrace();
                System.exit(1);
            }
        }
        String log4j2ConfigFile = logFilePath.toString();
        System.setProperty(BootConstant.LOG4J2_KEY, log4j2ConfigFile);
        memo.append("\n\t- ").append(I18n.info.launchingLog.format(userSpecifiedResourceBundle, System.getProperty(BootConstant.LOG4J2_KEY)));
        log = LogManager.getLogger(SummerApplication.class);
        log.info("Configuration path = {}", cfgConfigDir);
        log.info(() -> I18n.info.launching.format(userSpecifiedResourceBundle) + ", cmi=" + userSpecifiedCfgMonitorIntervalSec + ", StartCommand>" + jvmStartCommand);
        /*should be invoked after log4j was initialized to avoid caller invokes LogManager.static{}*/
        onUserSpecifiedImplTagsReady(userSpecifiedImplTags);//trigger subclass to init IoC container

        /*
         * [Config File] - encrypt/decrypt
         */
        if (cli.hasOption(CLI_ENCRYPT)) {
            int updated = loadBootConfigs(ConfigUtil.ConfigLoadMode.cli_encrypt, null);
            System.out.println(System.lineSeparator() + "\t " + updated + " config files have been decrypted in " + cfgConfigDir.getAbsolutePath());
            System.exit(0);
        } else if (cli.hasOption(CLI_DECRYPT)) {
            if (cli.hasOption(CLI_ADMIN_PWD_FILE)) {
                System.err.println(System.lineSeparator() + "\t error: -" + CLI_ADMIN_PWD_FILE + " is not allowed for decryption, please private password with -" + CLI_ADMIN_PWD + " option when decrypt data");
                System.exit(1);
            }
            int updated = loadBootConfigs(ConfigUtil.ConfigLoadMode.cli_decrypt, null);
            System.out.println(System.lineSeparator() + "\t " + updated + " config files have been decrypted in " + cfgConfigDir.getAbsolutePath());
            System.exit(0);
        }

        /*
         * [Config File] - generate template
         */
        if (cli.hasOption(CLI_CONFIG_DEMO)) {
            int i = 0;
            for (String cfgName : scanedJExpressConfigs.keySet()) {
                Class c = scanedJExpressConfigs.get(cfgName).cfgClass;
                try {
                    ConfigUtil.createConfigFile(c, cfgConfigDir, cfgName, true);
                } catch (IOException ex) {
                    System.out.println(ex + "\n\tFailed to generate config file (" + cfgName + ") in " + cfgConfigDir.getAbsolutePath());
                    ex.printStackTrace();
                    System.exit(1);
                }
                i++;
            }
            System.out.println("Total generated " + i + " configuration files in " + cfgConfigDir.getAbsolutePath());
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

        runCLI(cli, cfgConfigDir);
    }

    abstract protected void runCLI(CommandLine cli, File cfgConfigDir);

    /**
     * initialize based on config files in configDir
     *
     * @param mode
     * @param cfgChangeListener
     * @return
     */
    protected int loadBootConfigs(ConfigUtil.ConfigLoadMode mode, ConfigChangeListener cfgChangeListener) {
        Map<String, JExpressConfig> configs = new LinkedHashMap<>();
        int updated = 0;

        switch (mode) {
            case app_run:
                if (!hasGRPCImpl) {
                    scanedJExpressConfigs.remove(GRPCServerConfig.class.getSimpleName());
                }
                if (!hasAuthImpl) {
                    scanedJExpressConfigs.remove(AuthConfig.class.getSimpleName());
                }
        }
        try {
            //1. get main configurations
            for (ConfigMetadata registeredAppConfig : scanedJExpressConfigs.values()) {
                if ((isUserSpecifiedImplTags(registeredAppConfig.checkImplTagUsed) == registeredAppConfig.loadWhenImplTagUsed)) {
                    JExpressConfig instance = registeredAppConfig.instance;
                    if (instance == null) {
                        instance = BootConfig.instance(registeredAppConfig.cfgClass);
                    }
                    configs.put(registeredAppConfig.configFileName, instance);
                }
            }

            //2. load configurations
            updated = ConfigUtil.loadConfigs(mode, log, userSpecifiedResourceBundle, cfgConfigDir.toPath(), configs, userSpecifiedCfgMonitorIntervalSec, cfgConfigDir);
            //3. regist listener if provided
            if (cfgChangeListener != null) {
                ConfigUtil.setConfigChangeListener(cfgChangeListener);
            }
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(userSpecifiedResourceBundle), ex);
            System.exit(1);
        }
        return updated;
    }

    private boolean isUserSpecifiedImplTags(String mockItemName) {
        return userSpecifiedImplTags.contains(mockItemName);
    }

    /**
     * Triggered by CLI CLI_USE_IMPL, then to trigger subclass to init IoC
     * container.
     *
     * @param userSpecifiedImplTags
     */
    abstract protected void onUserSpecifiedImplTagsReady(Set<String> userSpecifiedImplTags);

    private static class ConfigMetadata {

        final Class cfgClass;
        final String configFileName;
        final JExpressConfig instance;
        final String checkImplTagUsed;
        final boolean loadWhenImplTagUsed;

        ConfigMetadata(String configFileName, Class cfgClass, JExpressConfig instance, String checkImplTagUsed, boolean loadWhenImplTagUsed) {
            this.configFileName = configFileName;
            this.cfgClass = cfgClass;
            this.instance = instance;
            this.checkImplTagUsed = checkImplTagUsed;
            this.loadWhenImplTagUsed = loadWhenImplTagUsed;
        }
    }

    /**
     * To add use impl tags
     *
     * @param <T>
     * @param enumClass the enum contains impl tag items
     * @return
     */
    public <T extends BootCLI> T addCliUseImplTags(Class<? extends Enum<?>> enumClass) {
        return addCliUseImplTags(FormatterUtil.getEnumNames(enumClass));
    }

    /**
     * To add use impl tags
     *
     * @param <T>
     * @param mockItemNames the impl tag item names
     * @return
     */
    public <T extends BootCLI> T addCliUseImplTags(String... mockItemNames) {
        if (mockItemNames == null || mockItemNames.length < 1) {
            return (T) this;
        }
        availableImplTagOptions.addAll(Set.of(mockItemNames));
        memo.append("\n\t- availableImplTagOptions=").append(availableImplTagOptions);
        return (T) this;
    }

    @Deprecated
    private String envTag;

    @Deprecated
    private void checkEnvTag(String[] args) {
        envTag = null;
        for (int i = 0; i < args.length; i++) {
            if ("-domain".equals(args[i])) {
                if ((i + 1) < args.length) {
                    envTag = args[i + 1];
                }
                break;
            }
        }
    }
}
