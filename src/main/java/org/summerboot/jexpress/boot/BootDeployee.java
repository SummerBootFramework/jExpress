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
import io.grpc.BindableService;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Unique;
import org.summerboot.jexpress.boot.annotation.Version;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;
import org.summerboot.jexpress.nio.server.ws.rs.JaxRsRequestProcessorManager;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.boot.annotation.Service;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class BootDeployee implements BootConstant {

    protected static Logger log;

    protected final StringBuilder memo = new StringBuilder();
    protected final Class callerClass;

    /*
     * Scan Results
     */
    protected String jvmStartCommand;
    protected Boolean jmxRequired;
    protected String callerRootPackageName;//also used by JPAHibernateConfig access to scan @Entity
    protected String appVersionLong = BootConstant.VERSION;
    protected String appVersionShort = BootConstant.VERSION;
    protected List<SummerRunner> summerRunners = new ArrayList();

    /*
     * Annotation scan results as CLI inputs
     */
    protected final List<String> availableUniqueTagOptions = new ArrayList();
    protected final Map<String, ConfigMetadata> scanedJExpressConfigs = new LinkedHashMap<>();
    protected final Set<String> availableImplTagOptions = new HashSet();
    protected boolean hasControllers = false;
    protected boolean hasGRPCImpl = false;
    protected boolean hasAuthImpl = false;

    /*
     * Annotation scan results as BootGuiceModule input
     * Format: bindingClass <--> {key=(ImplTag+named) <--> [@Service impl classes list]}
     */
    protected final Map<Class, Map<String, List<ServiceMetadata>>> scanedServiceBindingMap = new HashMap();

    protected BootDeployee(Class callerClass) {
        this.callerClass = callerClass == null
                ? this.getClass()
                : callerClass;
        deployee();
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
    protected void onGuiceInjectorCreated_ControllersInjected(@Controller Map<String, Object> controllers) {
        //1. scan and register controllers
        hasControllers = !controllers.isEmpty();
        JaxRsRequestProcessorManager.registerControllers(controllers, memo);

        //2. check is there any declared roles so that the auth config should be used
        final AuthConfig authCfg = AuthConfig.instance(AuthConfig.class);
        hasAuthImpl = !authCfg.getDeclareRoles().isEmpty();
    }

    private <T extends SummerApplication> T deployee() {
        memo.append("\n\t- deployee callerClass=").append(this.callerClass.getName());
        callerRootPackageName = ReflectionUtil.getRootPackageName(callerClass);
        jvmStartCommand = scanJVM_StartCommand();
        scanAnnotation_Version(callerClass);
        System.setProperty(SYS_PROP_APP_PACKAGE_NAME, callerRootPackageName);//used by log4j2.xml
        System.setProperty(SYS_PROP_APP_NAME, appVersionShort);//used by log4j2.xml
        System.setProperty(SYS_PROP_APP_VERSION, appVersionLong);//used by BootController.version()
        memo.append("\n\t- callerRootPackageName=").append(callerRootPackageName);
        String error = scanAnnotation_Unique(callerRootPackageName, memo);
        if (error != null) {
            System.out.println(error);
            System.exit(1);
        }
        scanAnnotation_ConfigImportResource("org.summerboot.jexpress", callerRootPackageName);
        scanAnnotation_Service(callerRootPackageName);
        scanImplementation_gRPC();

        return (T) this;
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

    protected void scanAnnotation_Version(Class callerClass) {
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
    protected String scanAnnotation_Unique(String rootPackageName, StringBuilder sb, String... displayByTags) {
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
                throw new RuntimeException("check unique failed on package " + rootPackageName + ".*", ex);
            }
        }
        return null;
    }

    protected void scanAnnotation_ConfigImportResource(String... rootPackageNames) {
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
            memo.append("\n\t- cfg.scaned=").append(jExpressConfigClass.getName()).append(", file=").append(configFileName);
        }
    }

    protected List<String> scanAnnotation_Service(String... rootPackageNames) {
        Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        for (String rootPackageName : rootPackageNames) {
            Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(Service.class, rootPackageName);
            classesAll.addAll(classes);
        }

        List<String> tags = new ArrayList();
        StringBuilder sb = new StringBuilder();
        for (Class serviceImplClass : classesAll) {
            Service serviceAnnotation = (Service) serviceImplClass.getAnnotation(Service.class);
            String named = serviceAnnotation.named().trim();
            String implTag = serviceAnnotation.implTag().trim();
            tags.add(implTag);
            String uniqueKey = implTag + named;
            Class[] bindingClasses = serviceAnnotation.binding();
            if (bindingClasses != null && bindingClasses.length > 0) {//developer specified 
                List<Class> interfaces = ReflectionUtil.getAllInterfaces(serviceImplClass, true);
                List<Class> superclasses = ReflectionUtil.getAllSuperClasses(serviceImplClass);
                interfaces.addAll(superclasses);
                for (Class bindingClass : bindingClasses) {
                    if (!interfaces.contains(bindingClass)) {
                        sb.append("\n\t").append(serviceImplClass).append(" specifies @").append(Service.class.getSimpleName()).append("(binding=").append(bindingClass.getSimpleName()).append(".class), but not in its Interfaces:").append(interfaces);
                        continue;
                    }
                    scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, new ServiceMetadata(serviceImplClass, named, implTag));
                }
            } else {//bindingClass not specified by developer, use its declaired interfaces by default
                List<Class> declaredInterfaces = ReflectionUtil.getAllInterfaces(serviceImplClass, false);
                if (declaredInterfaces.isEmpty()) {
                    List<Class> superInterfaces = ReflectionUtil.getAllInterfaces(serviceImplClass.getSuperclass(), true);
                    if (superInterfaces.isEmpty()) {
                        sb.append("\n\t").append(serviceImplClass).append(" does not implement any interfaces.");
                    } /*else if (superInterfaces.size() == 1) {
                        Class bindingClass = superInterfaces.get(0);
                        scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, serviceImplClass, named);
                    } */ else {
                        sb.append("\n\t").append(serviceImplClass).append(" needs to specify the binding interface @").append(Service.class.getSimpleName()).append("(binding=TheMissingInterface.class), which implemented by supper class: ").append(superInterfaces);
                    }
                    continue;
                }
                for (Class bindingClass : declaredInterfaces) {
                    scanAnnotation_Service_Add2BindingMap(bindingClass, uniqueKey, new ServiceMetadata(serviceImplClass, named, implTag));
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
        serviceImplTags.removeAll(Collections.singleton(Service.NO_TAG));
        availableImplTagOptions.addAll(serviceImplTags);
        return serviceImplTags;
    }

    protected void scanAnnotation_Service_Add2BindingMap(Class bindingClass, String uniqueKey, ServiceMetadata service) {
        memo.append("\n\t- scan.taggedservice.add to guiceModule.bind(").append(bindingClass.getName()).append(").to(").append(service).append("), uniqueKey=").append(uniqueKey);
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
        serviceImplList.add(service);
    }

    protected void scanAnnotation_Service_ValidateBindingMap(StringBuilder sb) {
        for (Class keyBindingClass : scanedServiceBindingMap.keySet()) {
            Map<String, List<ServiceMetadata>> taggeServicedMap = scanedServiceBindingMap.get(keyBindingClass);
            for (String keyImplTag : taggeServicedMap.keySet()) {
                List<ServiceMetadata> serviceImplList = taggeServicedMap.get(keyImplTag);
                int size = serviceImplList.size();
                if (size != 1) {
                    sb.append("\nIOC ").append(keyBindingClass).append(" required a single bean, but ").append(size).append(" were found with the same useImplTag(").append(keyImplTag).append("): ").append(serviceImplList);
                }
            }
        }
    }

    protected void scanImplementation_gRPC() {
        //ServerServiceDefinition
        Set<Class<? extends BindableService>> gRPC_ImplClasses = ReflectionUtil.getAllImplementationsByInterface(BindableService.class, callerRootPackageName);
        hasGRPCImpl = gRPC_ImplClasses != null && !gRPC_ImplClasses.isEmpty();
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
    }

    public static class ServiceMetadata {

        final Class serviceImplClass;
        final String named;
        final String implTag;

        public ServiceMetadata(Class serviceImplClass, String named, String implTag) {
            this.serviceImplClass = serviceImplClass;
            this.named = named;
            this.implTag = implTag;
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

    }
}
