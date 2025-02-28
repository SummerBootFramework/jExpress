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
package org.summerboot.jexpress.boot.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;
import org.summerboot.jexpress.nio.server.AbortPolicyWithReport;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.util.concurrent.EmptyBlockingQueue;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.summerboot.jexpress.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public abstract class BootConfig implements JExpressConfig {

    protected static final Map<Class, JExpressConfig> cache = new HashMap();

    protected static String BR = System.lineSeparator();

    protected static final String DESC_KMF = "Path to key store file. Use SSL/TLS when keystore is provided, otherwise use plain socket";
    protected static final String DESC_TMF = "Path to trust store file. Auth the remote peer certificate when a truststore is provided, otherwise blindly trust all remote peer certificate";
    public static final String DESC_PLAINPWD = "plain text inside DEC() will be automatically encrypted by app root password when the application starts or is running";
    protected static final String FILENAME_KEYSTORE = "keystore.p12";
    protected static final String FILENAME_SRC_TRUSTSTORE = "truststore.p12";

    public static <T extends JExpressConfig> T instance(Class<T> implclass) {
        JExpressConfig instance = cache.get(implclass);
        if (instance != null) {
            return (T) instance;
        }
        try {
            Constructor<T> cons = implclass.getDeclaredConstructor();
            cons.setAccessible(true);
            T ret = (T) cons.newInstance();
            //cache.put(subclass, ret); - done by ret.Constructor --> registerSingleton()
            //return ret;// - discard the new instance, return the cached singleton
            return (T) cache.get(implclass);
        } catch (Throwable ex) {
            throw new RuntimeException("Failed to instance " + implclass, ex);
        }
    }

    @JsonIgnore
    protected Logger logger;

    protected boolean generateTemplate = false;

    protected final Properties props = new Properties();

    public Properties getProperties() {
        return props;
    }

    protected BootConfig() {
        registerSingleton();
    }

    protected void registerSingleton() {
        Class key = this.getClass();
        if (cache.containsKey(key)) {
            //throw new FindException("No a singleton: " + key.getName());
            return;
        }
        cache.put(key, this);
    }

    @Override
    public JExpressConfig temp() {
        BootConfig ret = null;
        Class c = this.getClass();
        try {
            Constructor<? extends BootConfig> cons = c.getDeclaredConstructor();
            cons.setAccessible(true);
            ret = (BootConfig) cons.newInstance();
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                 IllegalArgumentException | InvocationTargetException ex) {
            if (logger == null) {
                logger = LogManager.getLogger(getClass());
            }
            logger.warn("failed to create temp " + c.getName(), ex);
        }
        return ret;
    }

    protected File cfgFile;
    protected String configName = getClass().getSimpleName();

    //public BootConfig(){}
//    public BootConfig(String configName) {
//        this.configName = configName;
//        if (StringUtils.isBlank(configName)) {
//            this.configName = getClass().getSimpleName();
//        }
//    }
    @Override
    public String name() {
        return configName;
    }

    @Override
    public File getCfgFile() {
        return cfgFile;
    }

    @Override
    public String info() {
        try {
            return BeanUtil.toJson(this, true, false);
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
            return ex.getMessage();
        }
    }

    protected void createIfNotExist(String srcFileName, String destFileName) {
        if (cfgFile == null || !generateTemplate) {
            return;
        }
        String location = cfgFile.getParentFile().getAbsolutePath();
        ClassLoader classLoader = this.getClass().getClassLoader();
        ApplicationUtil.createIfNotExist(location, classLoader, srcFileName, destFileName);
    }

    protected void preLoad(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
    }

    /**
     * Load config settings with @Config, supported Java types:
     * <pre>{@code
     * 1. T, K: enum, String, boolean/Boolean, byte/Byte, char/short/Short, int/Integer,
     * long/Long, float/Float, double/Double, BigDecimal, URI, URL, Path, File
     * 2. <T>[] array
     * 3. Immutable Set<T>
     * 4. Immutable List<T>
     * 5. Immutable Map<T, K>
     * 6. KeyManagerFactory
     * 7. TrustManagerFactory
     * }</pre>
     *
     * @param cfgFile
     * @param isReal
     * @throws FileNotFoundException
     * @throws IOException
     */
    @Override
    public void load(File cfgFile, boolean isReal) throws IOException {
        String configFolder = cfgFile.getParent();
        this.cfgFile = cfgFile.getAbsoluteFile();
        if (configName == null) {
            configName = cfgFile.getName();
        }
        //props = new Properties();
        props.clear();
        try (InputStream is = new FileInputStream(cfgFile); InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);) {
            props.load(isr);
        }
        ConfigUtil helper = new ConfigUtil(this.cfgFile.getAbsolutePath());
        try {
            preLoad(cfgFile, isReal, helper, props);
        } catch (Throwable ex) {
            ex.printStackTrace();
            helper.addError("failed to preLoad configs:" + ex);
        }

        Class c = this.getClass();
        List<Field> fields = ReflectionUtil.getDeclaredAndSuperClassesFields(c);
        boolean autoDecrypt = true;
        for (Field field : fields) {
            try {
                this.loadField(field, configFolder, helper, props, autoDecrypt);
            } catch (NullPointerException ex) {
                ex.printStackTrace();
            } catch (Throwable ex) {
                String key = field.getAnnotation(Config.class).key();
                Class expectedType = field.getType();
                helper.addError("invalid \"" + key + "\" - " + expectedType + ", error=" + ex);
            }
        }

        // 3. more
        try {
            loadCustomizedConfigs(cfgFile, isReal, helper, props);
        } catch (Throwable ex) {
            ex.printStackTrace();
            helper.addError("failed to init customized configs:" + ex);
        } finally {
            if (!isReal) {
                shutdown();
            }
        }

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        if (isReal && logger == null) {
            logger = LogManager.getLogger(getClass());
        }
    }

    protected abstract void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception;

    protected void loadField(Field field, String configFolder, ConfigUtil helper, Properties props, boolean autoDecrypt) throws IllegalAccessException {
        Config cfgAnnotation = field.getAnnotation(Config.class);
        if (cfgAnnotation == null) {
            return;
        }
        final String annotationKey = cfgAnnotation.key();
        String valueInCfgFile = props.getProperty(annotationKey);
        field.setAccessible(true);
        if (StringUtils.isBlank(valueInCfgFile)) {
            if (cfgAnnotation.required()) {// 1. no cfg value, but required
                helper.addError("missing \"" + annotationKey + "\"");
                return;
            }
            boolean isSpecifiedInCfgFile = props.containsKey(annotationKey);
            if (isSpecifiedInCfgFile) {// 2. empty cfg value as null 
                Object nullValue = ReflectionUtil.toStandardJavaType(null, false, field.getType(), false, false, null);
                field.set(this, nullValue);
                return;
            } else {
                String annotationDefaultValue = cfgAnnotation.defaultValue();
                boolean hasDefaultValue = StringUtils.isNotBlank(annotationDefaultValue);
                if (hasDefaultValue) {// 3. no cfg item, use annotationDefaultValue
                    valueInCfgFile = annotationDefaultValue;
                } else {// 4. no cfg item, no annotationDefaultValue, use class field default value
                    return;
                }
            }
        }

        // 5. override default field value with cfg value
        Config.Validate validate = cfgAnnotation.validate();
        boolean isEncrypted = validate.equals(Config.Validate.Encrypted);
        if (isEncrypted) {
            if (valueInCfgFile.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && valueInCfgFile.endsWith(")")) {
                try {
                    valueInCfgFile = SecurityUtil.decrypt(valueInCfgFile, true);
                } catch (GeneralSecurityException ex) {
                    throw new IllegalArgumentException("Failed to decrypt", ex);
                }
            } else {
                helper.addError("invalid \"" + annotationKey + "\" - require encrypted format, missing warpper: ENC(encrypted value)");
                return;
            }
        }
        boolean isEmailRecipients = validate.equals(Config.Validate.EmailRecipients);

        Class fieldClass = field.getType();
        if (fieldClass.equals(KeyManagerFactory.class)) {
            String key_storeFile = annotationKey;
            String key_storePwd = cfgAnnotation.StorePwdKey();
            String key_keyAlias = cfgAnnotation.AliasKey();
            String key_keyPwd = cfgAnnotation.AliasPwdKey();
            KeyManagerFactory kmf = helper.getAsKeyManagerFactory(props, configFolder,
                    key_storeFile, key_storePwd, key_keyAlias, key_keyPwd);
            field.set(this, kmf);
        } else if (fieldClass.equals(TrustManagerFactory.class)) {
            String key_storeFile = annotationKey;
            String key_storePwd = cfgAnnotation.StorePwdKey();
            TrustManagerFactory tmf = helper.getAsTrustManagerFactory(props, configFolder,
                    key_storeFile, key_storePwd);
            field.set(this, tmf);
        } else {
            if (valueInCfgFile != null && (fieldClass.equals(File.class) || fieldClass.equals(Path.class))) {
                File file = new File(valueInCfgFile);
                if (!file.isAbsolute()) {
                    valueInCfgFile = configFolder + File.separator + valueInCfgFile;
                }
            }
            String collectionDelimiter = cfgAnnotation.collectionDelimiter();
            ReflectionUtil.loadField(this, field, valueInCfgFile, autoDecrypt, isEmailRecipients, collectionDelimiter);
        }
    }

    protected String updateFilePath(File domainDir, String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return fileName;
        }
        if (fileName.startsWith(File.separator)) {
            return new File(fileName).getAbsolutePath();
        } else {
            return new File(domainDir.getAbsolutePath() + File.separator + fileName).getAbsolutePath();
        }
    }

    public void updateConfigFile(Map<String, String> updatedCfgs) throws IOException {
        if (updatedCfgs == null || updatedCfgs.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        LineIterator iterator = FileUtils.lineIterator(new File(cfgFile.getAbsolutePath()), "UTf-8");
        while (iterator.hasNext()) {
            String line = iterator.nextLine().trim();
            if (!line.startsWith("#")) {
                int i = line.indexOf("=");
                if (i > 0) {
                    String key = line.substring(0, i).trim();
                    if (updatedCfgs.containsKey(key)) {
                        line = key + "=" + updatedCfgs.get(key);
                    }
                }
            }
            sb.append(line).append(BR);
        }

        try (FileOutputStream output = new FileOutputStream(cfgFile); FileChannel foc = output.getChannel();) {
            foc.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    public static String generateTemplate(Class configClass) {
        Object objectInstance = null;
        if (JExpressConfig.class.isAssignableFrom(configClass)) {
            objectInstance = instance(configClass);
        }
        if (objectInstance == null) {
            try {
                Constructor cons = configClass.getDeclaredConstructor();
                cons.setAccessible(true);
                objectInstance = cons.newInstance();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }

        String namespace = "";
        ImportResource ir = (ImportResource) configClass.getAnnotation(ImportResource.class);
        if (ir != null) {
            namespace = ir.namespace();
        }
        if (!namespace.isBlank()) {
            namespace += ".";
        }

        List<Field> configItems = ReflectionUtil.getDeclaredAndSuperClassesFields(configClass, true);
        boolean hasConfig = false;
        StringBuilder sb = new StringBuilder();
        for (Field field : configItems) {
            // desc
            ConfigHeader header = field.getAnnotation(ConfigHeader.class);
            if (header != null) {
                List<String> list = parse(header);
                int maxSize = 0;
                for (String s : list) {
                    maxSize = Math.max(maxSize, getLength(s));
                }
                maxSize += 2;

                //1. top line ######################
                sb.append("\n\n");
                hasConfig = true;
                for (int i = 0; i < maxSize; i++) {
                    sb.append("#");
                }
                //2. desc
                sb.append("\n");
                for (String s : list) {
                    sb.append(s);
                    int size = maxSize - s.length() - 1;
                    for (int i = 0; i < size; i++) {
                        sb.append(" ");
                    }
                    sb.append("#").append("\n");
                }

                //3. bottom line ######################
                for (int i = 0; i < maxSize; i++) {
                    sb.append("#");
                }
                sb.append("\n");

                if (objectInstance != null) {
                    String callbackFunc = header.callbackMethodName4Dump();
                    if (StringUtils.isNotBlank(callbackFunc)) {
                        Class[] cArg = {StringBuilder.class};//new Class[1];
                        try {
                            Method cbMethod = ReflectionUtil.getMethod(configClass, callbackFunc, cArg);//configClass.getDeclaredMethod(callbackFunc, cArg);
                            if (cbMethod != null) {
                                cbMethod.setAccessible(true);
                                cbMethod.invoke(objectInstance, sb);
                            } else {
                                sb.append("NoSuchMethodException: ").append(callbackFunc).append("\n");
                            }
                        } catch (IllegalAccessException | IllegalArgumentException | /*NoSuchMethodException |*/
                                 SecurityException | InvocationTargetException ex) {
                            sb.append(ex).append("\n");
                        }
                    }
                }
            }

            //config
            Config cfg = field.getAnnotation(Config.class);
            if (cfg != null) {
                boolean isEncrypted = cfg.validate().equals(Config.Validate.Encrypted);
                String cm = cfg.desc();
                if (StringUtils.isNotBlank(cm)) {
                    List<String> memoList = new ArrayList();
                    lineBreak(cm, null, memoList);
                    for (String s : memoList) {
                        hasConfig = true;
                        sb.append("#").append(s).append("\n");
                    }
                }
                boolean isRequired = cfg.required();
                boolean hasDefaultValue = false, hasPredefinedValue = false;
                String dv = cfg.predefinedValue();
                if (!StringUtils.isBlank(dv)) {
                    hasPredefinedValue = true;
                } else {
                    dv = cfg.defaultValue();
                    if (StringUtils.isBlank(dv) && objectInstance != null) {
                        try {
                            field.setAccessible(true);
                            Object dfv = field.get(objectInstance);
                            if (dfv != null) {
                                dv = dfv.toString();//String.valueOf(dfv);
                            }
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                        }
                    }
                    if (StringUtils.isNotBlank(dv)) {
                        hasDefaultValue = true;
                    }
                }
                if (StringUtils.isNotBlank(dv)) {
                    dv = dv.replace("\n", "\\n");// use \n\ as the ending of the line in properties file
                    dv = dv.replace(BR, "\\n");
                }

                boolean dumpDefault = true;
                if (objectInstance != null) {
                    String callbackFunc = cfg.callbackMethodName4Dump();
                    if (StringUtils.isNotBlank(callbackFunc)) {
                        Class[] cArg = {StringBuilder.class};//new Class[1];
                        try {
                            Method cbMethod = ReflectionUtil.getMethod(configClass, callbackFunc, cArg);//configClass.getDeclaredMethod(callbackFunc, cArg);
                            if (cbMethod != null) {
                                cbMethod.setAccessible(true);
                                cbMethod.invoke(objectInstance, sb);
                                dumpDefault = false;
                            } else {
                                sb.append("NoSuchMethodException: ").append(callbackFunc).append("\n");
                            }
                        } catch (IllegalAccessException | IllegalArgumentException | /*NoSuchMethodException |*/
                                 SecurityException | InvocationTargetException ex) {
                            sb.append(ex).append("\n");
                        }
                    }
                }
                if (dumpDefault) {
                    if (!hasPredefinedValue && !isRequired || hasDefaultValue) {
                        sb.append("#");
                    }
                    String key = namespace + cfg.key();
                    sb.append(key).append("=");
                    if (isEncrypted) {
                        sb.append("DEC(");
                    }
                    if (hasDefaultValue || hasPredefinedValue) {
                        sb.append(dv);
                    } else if (isEncrypted) {
                        sb.append(DESC_PLAINPWD);
                    }
                    if (isEncrypted) {
                        sb.append(")");
                    }
                    sb.append("\n");

                    int i = 0;
                    String[] keys = {cfg.StorePwdKey(), cfg.AliasKey(), cfg.AliasPwdKey()};
                    for (String skey : keys) {
                        if (StringUtils.isNotBlank(skey)) {
                            if (!isRequired) {
                                sb.append("#");
                            }
                            sb.append(skey).append("=");
                            if (i == 0 || i == 2) {
                                sb.append("DEC(").append(DESC_PLAINPWD).append(")");
                            }
                            sb.append("\n");
                        }
                        i++;
                    }
                }
                if (StringUtils.isNotBlank(cm)) {
                    sb.append("\n");
                }
            }
        }

        return hasConfig ? sb.substring(2) : sb.toString();
    }

    protected static List<String> parse(ConfigHeader memo) {
        List<String> ret = new ArrayList<>();
        lineBreak(memo.title(), null, ret);
        lineBreak(memo.desc(), null, ret);
        lineBreak(memo.format(), "Format> ", ret);
        lineBreak(memo.example(), "Example> ", ret);

        return ret;
    }

    protected static String[] lineBreak(String s, String prefix, List<String> list) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        String[] ret = s.trim().split("\\r?\\n");
        if (ret != null) {
            for (String r : ret) {
                if (r != null) {
                    if (StringUtils.isBlank(prefix)) {
                        r = r.trim();
                    } else {
                        r = prefix + r.trim();
                    }
                    list.add("# " + r);
                }
            }
        }
        return ret;
    }

    protected static int getLength(String s) {
        return StringUtils.isBlank(s) ? 0 : s.trim().length();
    }

    protected static final int CPU_CORE = Runtime.getRuntime().availableProcessors();

    public enum ThreadingMode {
        VirtualThread, CPU, IO, Mixed
    }

    public static ThreadPoolExecutor buildThreadPoolExecutor(String tpeName) {
        return buildThreadPoolExecutor(null, tpeName, ThreadingMode.VirtualThread, 0, 0, Integer.MAX_VALUE, 60, null, false, true, false);
    }

    public static ThreadPoolExecutor buildThreadPoolExecutor(String tpeName, ThreadingMode threadingMode, int core, int max, int queue, long keepAliveSec) {
        return buildThreadPoolExecutor(null, tpeName, threadingMode, core, max, queue, keepAliveSec, null, false, false, false);
    }

    public static ThreadPoolExecutor buildThreadPoolExecutor(ThreadPoolExecutor tpe, String tpeName, ThreadingMode threadingMode,
                                                             int core, int max, int queue, long keepAliveSec, RejectedExecutionHandler rejectedExecutionHandler,
                                                             boolean prestartAllCoreThreads, boolean allowCoreThreadTimeOut, boolean isSingleton) {
        boolean useVirtualThread = false;
        switch (threadingMode) {
            case VirtualThread -> { // Java 21+ only
                useVirtualThread = true;
                if (core < 1) {
                    core = Integer.MAX_VALUE;
                    allowCoreThreadTimeOut = true;
                }
                if (max < 1) {
                    max = Integer.MAX_VALUE;
                }
                if (max < core) {
                    //helper.addError("BizExecutor.MaxSize should not less than BizExecutor.CoreSize");
                    max = core;
                }
            }
            case Mixed/*, VirtualThread*/ -> {// manual config is required when it is mixed
                if (core < 1) {
                    core = CPU_CORE * 2 + 1;
                }
                if (max < 1) {
                    max = CPU_CORE * 2 + 1;
                }
                if (max < core) {
                    //helper.addError("BizExecutor.MaxSize should not less than BizExecutor.CoreSize");
                    max = core;
                }
            }
            case CPU -> {// use CPU core + 1 when application is CPU bound
                core = CPU_CORE + 1;
                max = CPU_CORE + 1;
            }
            case IO -> {// use CPU core x 2 + 1 when application is I/O bound
                core = CPU_CORE * 2 + 1;
                max = CPU_CORE * 2 + 1;
            }
        }

        boolean isQueueChanged = false;
        if (tpe != null && !isSingleton) {
            int currentQueue = tpe.getQueue().size() + tpe.getQueue().remainingCapacity();
            isQueueChanged = currentQueue != queue;
        }

        if (tpe == null || isQueueChanged) {
            //backup old
            ThreadPoolExecutor old = tpe;
            //create new
            ThreadFactory factory = NamedDefaultThreadFactory.build(tpeName, useVirtualThread);
            BlockingQueue<Runnable> workQueue = queue > 0 ? new LinkedBlockingQueue<>(queue) : new EmptyBlockingQueue();
            if (rejectedExecutionHandler == null) {
                rejectedExecutionHandler = new AbortPolicyWithReport(tpeName);
            }
            tpe = new ThreadPoolExecutor(core, max, keepAliveSec, TimeUnit.SECONDS, workQueue,
                    factory, rejectedExecutionHandler);//.DiscardOldestPolicy()
            // then shotdown old tpe
            if (old != null) {
                old.shutdown();
            }
        }

        // Update tpe: From JDK 9 the implementation of ThreadPoolExecutor's setCorePoolSize was changed and contains an additional constraints comparing the maxPoolSize and corePoolSize values.
        // If maxPoolSize is not changed but corePoolSize is given as a higher number of default maxPoolSize it throws IllegalArgumentException.
        if (max >= tpe.getCorePoolSize()) {
            tpe.setMaximumPoolSize(max);
            tpe.setCorePoolSize(core);
        } else {
            tpe.setCorePoolSize(core);
            tpe.setMaximumPoolSize(max);
        }

        tpe.setKeepAliveTime(keepAliveSec, TimeUnit.SECONDS);
        if (prestartAllCoreThreads) {
            tpe.prestartAllCoreThreads();
        }
        tpe.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return tpe;
    }
}
