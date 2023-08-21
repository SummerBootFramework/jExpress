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

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.netty.channel.ChannelHandler;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Unique;
import org.summerboot.jexpress.boot.annotation.Version;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.boot.annotation.Service;
import org.summerboot.jexpress.boot.annotation.GrpcService;
import org.summerboot.jexpress.boot.annotation.Service.ChannelHandlerType;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.util.FormatterUtil;

/**
 * In Code We Trust
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerSingularity {

    public static final String HOST = jExpressInit();

    private static String jExpressInit() {
        String FILE_CFG_SYSTEM = "boot.conf";
        File currentDir = new File("etc").getAbsoluteFile();
        if (!currentDir.exists()) {
            currentDir.mkdirs();
        }
        File systemConfigFile = Paths.get(currentDir.getAbsolutePath(), FILE_CFG_SYSTEM).toFile();
        try {
            if (!systemConfigFile.exists()) {
                ConfigUtil.createConfigFile(BackOffice.class, currentDir, FILE_CFG_SYSTEM, false);
            }
            BackOffice.agent.load(systemConfigFile, false);// isReal:false = do not init logging
        } catch (IOException ex) {
            System.err.println("Failed to init " + systemConfigFile + ", caused by " + ex);
            System.exit(1);
        }
        return ApplicationUtil.getServerName(true);
    }

    protected static Logger log;
    protected static final File DEFAULT_CFG_DIR = new File(BootConstant.DIR_CONFIGURATION).getAbsoluteFile();
    protected static final File CURRENT_DIR = new File("").getAbsoluteFile();
    protected File userSpecifiedConfigDir;
    protected File pluginDir;

    protected final StringBuilder memo = new StringBuilder();
    protected final Class primaryClass;


    /*
     * CLI utils
     */
    protected CommandLine cli;
    protected final Options cliOptions = new Options();
    protected final HelpFormatter cliHelpFormatter = new HelpFormatter();

    /*
     * CLI results
     */
    protected Locale userSpecifiedResourceBundle;
    protected int userSpecifiedCfgMonitorIntervalSec;
    protected final Set<String> userSpecifiedImplTags = new HashSet<>();

    /*
     * Scan Results
     */
    protected String jvmStartCommand;
    protected boolean jmxRequired;
    protected String[] callerRootPackageNames;//also used by JPAHibernateConfig access to scan @Entity
    protected String appVersion = BootConstant.VERSION;
    protected String logFileName = BootConstant.VERSION;

    /*
     * Annotation scan results as CLI inputs
     */
    protected final List<String> availableUniqueTagOptions = new ArrayList();
    protected final Map<String, ConfigMetadata> scanedJExpressConfigs = new LinkedHashMap<>();
    protected final Set<String> availableImplTagOptions = new HashSet();
    protected final Set<Class<? extends BindableService>> gRPCBindableServiceImplClasses = new HashSet();
    protected final Set<Class<ServerServiceDefinition>> gRPCServerServiceDefinitionImplClasses = new HashSet();
    protected boolean hasControllers = false;
    protected boolean hasGRPCImpl = false;
    protected boolean hasAuthImpl = false;

    /*
     * Annotation scan results as BootGuiceModule input
     * Format: bindingClass <--> {key=(ImplTag+named) <--> [@Service impl classes list]}
     */
    protected final Map<Class, Map<String, List<ServiceMetadata>>> scanedServiceBindingMap = new HashMap();
    protected final Map<Service.ChannelHandlerType, Set<String>> channelHandlerNames = new HashMap();

    protected SummerSingularity(Class callerClass, String... args) {
        System.out.println("SummerApplication loading from " + HOST);
        System.setProperty(BootConstant.SYS_PROP_SERVER_NAME, HOST);// used by log4j2.xml
        primaryClass = callerClass == null
                ? this.getClass()
                : callerClass;
        singularity();
        bigBang(args);
    }

    private void singularity() {
        memo.setLength(0);
        userSpecifiedConfigDir = null;
        pluginDir = null;
        System.getProperties().remove(BootConstant.LOG4J2_KEY);
        System.getProperties().remove(BootConstant.LOG4J2_JDKADAPTER_KEY);
        System.getProperties().remove(BootConstant.SYS_PROP_APP_PACKAGE_NAME, "");// used by log4j2.xml
        System.getProperties().remove(BootConstant.SYS_PROP_LOGFILENAME, "");// used by log4j2.xml
        System.setProperty(BootConstant.LOG4J2_KEY, "");

        // CLI        
        userSpecifiedResourceBundle = null;
        userSpecifiedCfgMonitorIntervalSec = BootConstant.CFG_CHANGE_MONITOR_INTERVAL_SEC;
        userSpecifiedImplTags.clear();

        // reset Scan Results
        jvmStartCommand = null;
        jmxRequired = false;
        callerRootPackageNames = null;
        appVersion = BootConstant.VERSION;
        logFileName = BootConstant.VERSION;

        //reset Annotation scan results as CLI inputs
        availableUniqueTagOptions.clear();
        scanedJExpressConfigs.clear();
        availableImplTagOptions.clear();
        gRPCBindableServiceImplClasses.clear();
        gRPCServerServiceDefinitionImplClasses.clear();
        hasControllers = false;
        hasGRPCImpl = false;
        hasAuthImpl = false;
    }

    private <T extends SummerApplication> T bigBang(String[] args) {
        memo.append(BootConstant.BR).append("\t- deployee callerClass=").append(primaryClass.getName());
        Set<String> packageSet = new HashSet();
        Set<String> configuredPackageSet = BackOffice.agent.getRootPackageNames();
        if (configuredPackageSet != null && !configuredPackageSet.isEmpty()) {
            packageSet.addAll(configuredPackageSet);
        }
        String rootPackageName = ReflectionUtil.getRootPackageName(primaryClass, BootConstant.PACKAGE_LEVEL);
        packageSet.add(rootPackageName);
        BackOffice.agent.setRootPackageNames(packageSet);
        callerRootPackageNames = packageSet.toArray(String[]::new);

        memo.append(BootConstant.BR).append("\t- callerRootPackageName=").append(packageSet);
        StringBuilder sb = new StringBuilder();
        jmxRequired = ApplicationUtil.scanJVM_StartCommand(sb);
        jvmStartCommand = sb.toString();

        scanAnnotation_Version(primaryClass);
        System.setProperty(BootConstant.SYS_PROP_LOGFILENAME, logFileName);// used by log4j2.xml as log file name
        System.setProperty(BootConstant.SYS_PROP_APP_PACKAGE_NAME, rootPackageName);// used by log4j2.xml
        BackOffice.agent.setVersion(appVersion);
        scanArgsToInitializeLogging(args);
        /*
         * load external modules
         */
        try {
            scanPluginJars(pluginDir, true);// depends -domain or -cfgdir
        } catch (IOException ex) {
            System.out.println(ex + BootConstant.BR + "\tFailed to load plugin jar files from " + pluginDir);
            ex.printStackTrace();
            System.exit(1);
        }

        String error = scanAnnotation_Unique(callerRootPackageNames, memo);
        if (error != null) {
            System.out.println(error);
            System.exit(1);
        }
        String[] packages = FormatterUtil.arrayAdd(callerRootPackageNames, BootConstant.JEXPRESS_PACKAGE_NAME);
        scanAnnotation_JExpressConfigImportResource(packages);
        scanImplementation_gRPC(callerRootPackageNames);
        scanAnnotation_Controller(callerRootPackageNames);
        scanAnnotation_Service(callerRootPackageNames);
        scanAnnotation_DeclareRoles(callerRootPackageNames);
        return (T) this;
    }

    protected void scanAnnotation_Version(Class callerClass) {
        Version version = (Version) callerClass.getAnnotation(Version.class);
        if (version != null) {
            String logManager = version.LogManager();
            if (StringUtils.isNotBlank(logManager)) {
                System.setProperty(BootConstant.LOG4J2_JDKADAPTER_KEY, logManager);// https://logging.apache.org/log4j/log4j-2.3.2/log4j-jul/index.html
            }
            logFileName = version.logFileName();
            if (StringUtils.isBlank(logFileName)) {
                logFileName = version.value()[0];
            }
            appVersion = version.value()[0];
            BackOffice.agent.setVersionShort(appVersion);
            int versionCount = version.value().length;
            if (versionCount > 1) {
                appVersion = appVersion + " (";
                for (int i = 1; i < versionCount; i++) {
                    appVersion = appVersion + version.value()[i] + " ";
                }
                appVersion = appVersion + ")";
            }
        } else {
            logFileName = "app";
        }
        memo.append(BootConstant.BR).append("\t- callerVersion=").append(appVersion);
    }

    protected void scanArgsToInitializeLogging(String[] args) {
        /*
         * [Config File] Location - determine the configuration path: userSpecifiedConfigDir
         */
        userSpecifiedConfigDir = null;//to clear the state
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String cli = args[i];
                if (("-" + BootConstant.CLI_CONFIG_DIR).equals(cli)) {
                    String cfgDir = args[++i];
                    userSpecifiedConfigDir = new File(cfgDir).getAbsoluteFile();
                    break;
                } else if (("-" + BootConstant.CLI_CONFIG_DOMAIN).equals(cli)) {
                    String envTag = args[++i];
                    String cfgDir = /*unittestWorkingDir +*/ BootConstant.DIR_STANDALONE + "_" + envTag + File.separator + BootConstant.DIR_CONFIGURATION;
                    userSpecifiedConfigDir = new File(cfgDir).getAbsoluteFile();
                    System.setProperty("domainName", envTag);
                    break;
                }
            }
        }

        if (userSpecifiedConfigDir == null) {
            userSpecifiedConfigDir = DEFAULT_CFG_DIR;
        }
        if (!userSpecifiedConfigDir.exists()) {
            userSpecifiedConfigDir.mkdirs();
        }

//        if (userSpecifiedConfigDir.getAbsolutePath().equals(CURRENT_DIR.getAbsolutePath())) {
//            //set log folder inside user specified config folder
//            System.setProperty(SYS_PROP_LOGFILEPATH, userSpecifiedConfigDir.getAbsolutePath());//used by log4j2.xml
//            pluginDir = new File(userSpecifiedConfigDir.getAbsolutePath(), BootConstant.DIR_PLUGIN).getAbsoluteFile();
//        } else {
        if (!userSpecifiedConfigDir.exists() || !userSpecifiedConfigDir.isDirectory() || !userSpecifiedConfigDir.canRead()) {
            System.out.println("Could access configuration path as a folder: " + userSpecifiedConfigDir);
            System.exit(1);
        }
        //set log folder outside user specified config folder
        Random rnd = new Random();
        int number = rnd.nextInt(999999);
        String logId = String.format("%06d", number);
        // this will convert any number sequence into 6 character.
        System.setProperty(BootConstant.SYS_PROP_LOGID, logId);
        System.setProperty(BootConstant.SYS_PROP_LOGFILEPATH, userSpecifiedConfigDir.getParent() + File.separator + BootConstant.DIR_LOG);//used by log4j2.xml
        pluginDir = new File(userSpecifiedConfigDir.getParentFile(), BootConstant.DIR_PLUGIN).getAbsoluteFile();
//        }

        /*
         * [Config File] Log4J - init
         */
        String location = userSpecifiedConfigDir.getAbsolutePath();
        ClassLoader classLoader = this.getClass().getClassLoader();
        Path logFilePath = ApplicationUtil.createIfNotExist(location, classLoader, "log4j2.xml.temp", "log4j2.xml");
        String log4j2ConfigFile = logFilePath.toString();
        System.setProperty(BootConstant.LOG4J2_KEY, log4j2ConfigFile);
        log = LogManager.getLogger(SummerApplication.class);// init log
        log.info("Logging initialized from {}", userSpecifiedConfigDir);
        Locale userSpecifiedResourceBundle = null;
        memo.append(BootConstant.BR).append("\t- ").append(I18n.info.launchingLog.format(userSpecifiedResourceBundle, System.getProperty(BootConstant.LOG4J2_KEY)));
    }

    protected void scanPluginJars(File pluginDir, boolean failOnUndefinedClasses) throws IOException {
        pluginDir.mkdirs();
        if (!pluginDir.canRead() || !pluginDir.isDirectory()) {
            memo.append(BootConstant.BR).append("\t- loadPluginJars: invalid dir ").append(pluginDir);
            return;
        }
        FileFilter fileFilter = file -> !file.isDirectory() && file.getName().endsWith(".jar");
        File[] jarFiles = pluginDir.listFiles(fileFilter);
        if (jarFiles == null || jarFiles.length < 1) {
            memo.append(BootConstant.BR).append("\t- loadPluginJars: no jar files found at ").append(pluginDir);
            return;
        }
        Set<Class<?>> pluginClasses = new HashSet<>();
        for (File jarFile : jarFiles) {
            memo.append(BootConstant.BR).append("\t- loadPluginJars: loading jar file ").append(jarFile.getAbsolutePath());
            Set<Class<?>> classes = ApplicationUtil.loadClassFromJarFile(jarFile, failOnUndefinedClasses);
            memo.append(BootConstant.BR).append("\t- loadPluginJars: loaded ").append(classes.size()).append(" classes from jar file ").append(jarFile.getAbsolutePath());
            pluginClasses.addAll(classes);
        }
        ReflectionUtil.setPluginClasses(pluginClasses);
        memo.append(BootConstant.BR).append("\t- loadPluginJars: loaded ").append(pluginClasses.size()).append(" classes from ").append(jarFiles.length).append(" jar files in ").append(pluginDir);
    }

    /**
     *
     * @param rootPackageNames
     * @param sb
     * @param displayByTags
     * @return error message
     */
    protected String scanAnnotation_Unique(String[] rootPackageNames, StringBuilder sb, String... displayByTags) {
        StringBuilder errors = new StringBuilder();
        boolean error = false;
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Unique.class, false, rootPackageNames);
        for (Class classWithUniqueValues : classes) {
            if (!classWithUniqueValues.isInterface()) {
                error = true;
                errors.append(BootConstant.BR).append("\t @Unique can only apply on interfaces, ").append(classWithUniqueValues).append(" is not an interface");
                continue;
            }
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
                    sb.append(BootConstant.BR).append(tag).append("=").append(json);
                }
            } catch (Throwable ex) {
                throw new RuntimeException("check unique failed on " + classWithUniqueValues.getName(), ex);
            }
        }

        if (error) {
            throw new RuntimeException(errors.toString());
        }
        return null;
    }

    protected void scanAnnotation_JExpressConfigImportResource(String... rootPackageNames) {
        Set<String> pakcages = Set.copyOf(List.of(rootPackageNames));
        //Set<Class<? extends JExpressConfig>> classesAll = new HashSet();//to remove duplicated
        //for (String rootPackageName : pakcages) {
        Set<Class<? extends JExpressConfig>> jExpressConfigClasses = ReflectionUtil.getAllImplementationsByInterface(JExpressConfig.class, pakcages);
        //    classesAll.addAll(jExpressConfigClasses);
        //}

        for (Class jExpressConfigClass : jExpressConfigClasses) {
            int mod = jExpressConfigClass.getModifiers();
            if (mod == 0 || Modifier.isAbstract(mod) || Modifier.isInterface(mod)) {
                continue;
            }
            String key = jExpressConfigClass.getSimpleName();
            if (scanedJExpressConfigs.containsKey(key)) {
                continue;
            }
            String configFileName = null;
            String checkImplTagUsed = "";
            boolean loadWhenImplTagUsed = false;

            ImportResource ir = (ImportResource) jExpressConfigClass.getAnnotation(ImportResource.class);
            if (ir != null) {
                configFileName = ir.value();
                checkImplTagUsed = ir.checkImplTagUsed();
                loadWhenImplTagUsed = ir.loadWhenImplTagUsed();
            } else if (jExpressConfigClass.equals(AuthConfig.class)) {
                configFileName = BootConstant.FILE_CFG_AUTH;
            } else if (jExpressConfigClass.equals(NioConfig.class)) {
                configFileName = BootConstant.FILE_CFG_NIO;
            } else if (jExpressConfigClass.equals(SMTPClientConfig.class)) {
                configFileName = BootConstant.FILE_CFG_SMTP;
            } else if (jExpressConfigClass.equals(GRPCServerConfig.class)) {
                configFileName = BootConstant.FILE_CFG_GRPC;
            } else {
                continue;
            }

            ConfigMetadata metadata = new ConfigMetadata(configFileName, jExpressConfigClass, null, checkImplTagUsed, loadWhenImplTagUsed);
            //availableAppConfigs.add(rc);
            scanedJExpressConfigs.put(key, metadata);
            memo.append(BootConstant.BR).append("\t- scan.JExpressConfig.ImportResource:").append(key).append("=").append(metadata);

            memo.append(BootConstant.BR).append("\t- cfg.scaned=").append(jExpressConfigClass.getName()).append(", file=").append(configFileName);
        }
    }

    protected void scanImplementation_gRPC(String... pakcages) {
        //gRPCBindableServiceImplClasses.addAll(ReflectionUtil.getAllImplementationsByInterface(BindableService.class, callerRootPackageNames));
        //for (String rootPackageName : pakcages) {
        Set<Class<?>> gRPCServerClasses = ReflectionUtil.getAllImplementationsByAnnotation(GrpcService.class, false, pakcages);
        for (Class gRPCServerClass : gRPCServerClasses) {
            if (BindableService.class.isAssignableFrom(gRPCServerClass)) {
                gRPCBindableServiceImplClasses.add(gRPCServerClass);
            } else if (ServerServiceDefinition.class.equals(gRPCServerClass)) {
                gRPCServerServiceDefinitionImplClasses.add(gRPCServerClass);
            }
        }
        //}
        hasGRPCImpl = !gRPCServerServiceDefinitionImplClasses.isEmpty() || !gRPCBindableServiceImplClasses.isEmpty();
    }

    protected void scanAnnotation_Controller(String... rootPackageNames) {
        //Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        //for (String rootPackageName : rootPackageNames) {
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Controller.class, false, rootPackageNames);
        //classesAll.addAll(classes);
        //}
        List<String> tags = new ArrayList();
        for (Class c : classes) {
            Controller a = (Controller) c.getAnnotation(Controller.class);
            if (a == null) {
                continue;
            }
            String implTag = a.implTag();
            tags.add(implTag);
        }
        List<String> serviceImplTags = tags.stream()
                .distinct()
                .collect(Collectors.toList());
        serviceImplTags.removeAll(Collections.singleton(null));
        serviceImplTags.removeAll(Collections.singleton(""));
        serviceImplTags.removeAll(Collections.singleton(Controller.NOT_TAGGED));
        availableImplTagOptions.addAll(serviceImplTags);
    }

    protected List<String> scanAnnotation_Service(String... rootPackageNames) {
        //Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        //for (String rootPackageName : rootPackageNames) {
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Service.class, false, rootPackageNames);
        //classesAll.addAll(classes);
        //}

        return scanAnnotation_Service(classes);
    }

    protected List<String> scanAnnotation_Service(Set<Class<?>> classesAll) {
        List<String> tags = new ArrayList();
        StringBuilder sb = new StringBuilder();
        for (Class serviceImplClass : classesAll) {
            Service serviceAnnotation = (Service) serviceImplClass.getAnnotation(Service.class);
            if (serviceAnnotation == null) {
                continue;
            }
            String named = serviceAnnotation.named().trim();
            String implTag = serviceAnnotation.implTag().trim();
            tags.add(implTag);
            String uniqueKey = "named=" + named + ", implTag=" + implTag;
            Class[] bindingClasses = serviceAnnotation.binding();
            Service.ChannelHandlerType ChannelHandlerType = serviceAnnotation.type();
            if (bindingClasses != null && bindingClasses.length > 0) {//developer specified 
                for (Class bindingClass : bindingClasses) {
                    if (!bindingClass.isAssignableFrom(serviceImplClass)) {
                        List<Class> interfaces = ReflectionUtil.getAllInterfaces(serviceImplClass, true);
                        List<Class> superclasses = ReflectionUtil.getAllSuperClasses(serviceImplClass);
                        interfaces.addAll(superclasses);
                        interfaces.remove(Object.class);
                        sb.append(BootConstant.BR).append("\t").append(serviceImplClass).append(" specifies @").append(Service.class.getSimpleName()).append("(binding=").append(bindingClass.getSimpleName()).append(".class), which is not in its Interfaces:").append(interfaces);
                        continue;
                    }
                    scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, new ServiceMetadata(serviceImplClass, named, implTag, ChannelHandlerType), sb);
                }
            } else {//bindingClass not specified by developer, use its declaired interfaces by default
                List<Class> declaredInterfaces = ReflectionUtil.getAllInterfaces(serviceImplClass, false);
                if (declaredInterfaces.isEmpty()) {
                    List<Class> superInterfaces = ReflectionUtil.getAllInterfaces(serviceImplClass.getSuperclass(), true);
                    if (superInterfaces.isEmpty()) {
                        sb.append(BootConstant.BR).append("\t").append(serviceImplClass).append(" does not implement any interfaces.");
                    } /*else if (superInterfaces.size() == 1) {
                        Class bindingClass = superInterfaces.get(0);
                        scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, serviceImplClass, named);
                    } */ else {
                        sb.append(BootConstant.BR).append("\t").append(serviceImplClass).append(" needs to specify the binding interface @").append(Service.class.getSimpleName()).append("(binding=TheMissingInterface.class), which implemented by supper class: ").append(superInterfaces);
                    }
                    continue;
                }
                for (Class bindingClass : declaredInterfaces) {
                    scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, new ServiceMetadata(serviceImplClass, named, implTag, ChannelHandlerType), sb);
                }
            }
        }
        scanAnnotation_Service_ValidateBindingMap(sb);
        //Java 17if (!sb.isEmpty()) {
        String error = sb.toString();
        if (!error.isBlank()) {
            System.out.println("IOC Code error:" + sb);
            System.exit(1);
        }
        List<String> serviceImplTags = tags.stream()
                .distinct()
                .collect(Collectors.toList());
        serviceImplTags.removeAll(Collections.singleton(null));
        serviceImplTags.removeAll(Collections.singleton(""));
        serviceImplTags.removeAll(Collections.singleton(Service.NOT_TAGGED));
        availableImplTagOptions.addAll(serviceImplTags);
        return serviceImplTags;
    }

    protected void scanAnnotation_Service_Add2BindingMap(Class bindingClass, String uniqueKey, ServiceMetadata service, StringBuilder sb) {
        memo.append(BootConstant.BR).append("\t- scan.taggedservice.add to guiceModule.bind(").append(bindingClass.getName()).append(").to(").append(service).append("), uniqueKey=").append(uniqueKey);
        Map<String, List<ServiceMetadata>> taggeServicedMap = scanedServiceBindingMap.get(bindingClass);
        if (taggeServicedMap == null) {
            taggeServicedMap = new HashMap();
            scanedServiceBindingMap.put(bindingClass, taggeServicedMap);
        }
        List<ServiceMetadata> serviceImplList = taggeServicedMap.get(uniqueKey);
        if (serviceImplList == null) {
            serviceImplList = new ArrayList();
            taggeServicedMap.put(uniqueKey, serviceImplList);
        }
        if (bindingClass.equals(ChannelHandler.class)) {
            ChannelHandlerType channelHandlerType = service.getChannelHandlerType();
            if (channelHandlerType == null || channelHandlerType == ChannelHandlerType.nptspecified) {
                sb.append(BootConstant.BR).append("\t").append(service.getServiceImplClass()).append(" needs to specify type @").append(Service.class.getSimpleName()).append("(binding=ChannelHandler.class, type=?), when binding=ChannelHandler.class");
            }
        }
        serviceImplList.add(service);
    }

    protected void scanAnnotation_Service_ValidateBindingMap(StringBuilder sb) {
        for (Class keyBindingClass : scanedServiceBindingMap.keySet()) {
            Map<String, List<ServiceMetadata>> taggeServicedMap = scanedServiceBindingMap.get(keyBindingClass);
            for (String keyImplTag : taggeServicedMap.keySet()) {
                List<ServiceMetadata> serviceImplList = taggeServicedMap.get(keyImplTag);
                int size = serviceImplList.size();
                if (size != 1) {
                    sb.append(BootConstant.BR).append("IOC ").append(keyBindingClass).append(" required a single bean, but ").append(size).append(" were found with the same named+implTag(").append(keyImplTag).append("): ").append(serviceImplList);
                }
            }
        }
    }

    protected void scanAnnotation_DeclareRoles(String... rootPackageNames) {
        Set<String> declareRoles = new TreeSet();
        //Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        //for (String rootPackageName : rootPackageNames) {
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Controller.class, false, rootPackageNames);
        //    classesAll.addAll(classes);
        //}
        hasControllers = !classes.isEmpty();
        // @DeclareRoles
        for (Class c : classes) {
            DeclareRoles drs = (DeclareRoles) c.getAnnotation(DeclareRoles.class);
            if (drs != null) {
                String[] roles = drs.value();
                declareRoles.addAll(Arrays.asList(roles));
            }
            // @RolesAllowed
            List<Method> methods = ReflectionUtil.getDeclaredAndSuperClassesMethods(c, true);
            for (Method javaMethod : methods) {
                RolesAllowed ra = javaMethod.getAnnotation(RolesAllowed.class);
                if (ra == null) {
                    continue;
                }
                String[] roles = ra.value();
                declareRoles.addAll(Arrays.asList(roles));
            }
        }
        final AuthConfig authCfg = AuthConfig.cfg;
        authCfg.addDeclareRoles(declareRoles);
        memo.append(BootConstant.BR).append("\t- scan.DeclareRoles=").append(declareRoles);

        //2. check is there any declared roles so that the auth config should be used
        hasAuthImpl = !authCfg.getDeclareRoles().isEmpty();
    }

    protected static class ConfigMetadata {

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

        @Override
        public String toString() {
            return "ConfigMetadata{" + "cfgClass=" + cfgClass.getName() + ", configFileName=" + configFileName + ", instance=" + instance + ", checkImplTagUsed=" + checkImplTagUsed + ", loadWhenImplTagUsed=" + loadWhenImplTagUsed + '}';
        }
    }

    public static class ServiceMetadata {

        final Class serviceImplClass;
        final String named;
        final String implTag;
        final ChannelHandlerType channelHandlerType;

        public ServiceMetadata(Class serviceImplClass, String named, String implTag, ChannelHandlerType channelHandlerType) {
            this.serviceImplClass = serviceImplClass;
            this.named = named;
            this.implTag = implTag;
            this.channelHandlerType = channelHandlerType;
        }

        @Override
        public String toString() {
            return "ServiceImpl{" + serviceImplClass.getName() + ", named=" + named + ", implTag=" + implTag + '}';
        }

        public Class getServiceImplClass() {
            return serviceImplClass;
        }

        public String getNamed() {
            return named;
        }

        public String getImplTag() {
            return implTag;
        }

        public ChannelHandlerType getChannelHandlerType() {
            return channelHandlerType;
        }
    }
}
