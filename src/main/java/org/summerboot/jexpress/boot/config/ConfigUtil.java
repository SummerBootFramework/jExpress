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

import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.security.SSLUtil;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ConfigUtil {

    public static Path cfgRoot(String domainFolderPrefix, String domainName, String configDirName) {
//        String f = StringUtils.isBlank(runtimeDomain)
//                ? runtimeRootDirName + File.separator + configDirName
//                : runtimeRootDirName + '_' + runtimeDomain + File.separator + configDirName;
//        return new File(f).getAbsoluteFile();
        String root = StringUtils.isBlank(domainName)
                ? domainFolderPrefix
                : domainFolderPrefix + '_' + domainName;
        Path p = Paths.get(root, configDirName);
        return p.toAbsolutePath();
    }

//    public static File getConfigFile(Path configFolder, String cfgFile) {
//        Path p = Paths.get(configFolder.toString(), cfgFile);
//        return p.toFile();
//    }
    private static ConfigChangeListener listener;

    public static void setConfigChangeListener(ConfigChangeListener l) {
        listener = l;
    }

    public static enum ConfigLoadMode {
        cli_encrypt(true, true), cli_decrypt(false, true), app_run(true, false);

        private final boolean encryptMode;
        private final boolean cliMode;

        private ConfigLoadMode(boolean encryptMode, boolean cliMode) {
            this.encryptMode = encryptMode;
            this.cliMode = cliMode;
        }

        public boolean isEncryptMode() {
            return encryptMode;
        }

        public boolean isCliMode() {
            return cliMode;
        }
    }

    public static int loadConfigs(ConfigLoadMode mode, Logger log, Locale defaultRB, Path configFolder, Map<String, JExpressConfig> configs, int CfgMonitorInterval, File cfgConfigDir) throws Exception {
        // 1. load configs
        int updated = 0;
        Map<File, Runnable> cfgUpdateTasks = new HashMap();
        for (String fileName : configs.keySet()) {
            File configFile = Paths.get(configFolder.toString(), fileName).toFile();
            updated += loadConfig(mode, log, defaultRB, configFile, configs.get(fileName), cfgUpdateTasks, cfgConfigDir);
        }
        // 2. monitor the change of config files
        if (!mode.isCliMode() && CfgMonitorInterval > 0) {
            ConfigurationMonitor.listener.start(configFolder.toFile(), CfgMonitorInterval, cfgUpdateTasks);
        }
        return updated;
    }

    public static void createConfigFile(Class<? extends JExpressConfig> c, File cfgConfigDir, String cfgName, boolean cliMode) throws IOException {
        String configContent = BootConfig.generateTemplate(c);
//        if (StringUtils.isBlank(configContent)) {
//            return;
//        }
        ImportResource ir = (ImportResource) c.getAnnotation(ImportResource.class);
        String fileName = ir == null ? cfgName : ir.value();
        if (cliMode) {
            fileName = fileName + ".sample";
        }

        File cfgFile = new File(cfgConfigDir, fileName).getAbsoluteFile();
        if (cliMode) {
            System.out.print("saveing " + c.getName() + " to " + cfgFile);
        }
        Files.writeString(cfgFile.toPath(), configContent);
        if (cliMode) {
            System.out.println(" done!");
        }
    }

    public static int loadConfig(ConfigLoadMode mode, Logger log, Locale defaultRB, File configFile, JExpressConfig cfg, Map<File, Runnable> cfgUpdateTasks, File cfgConfigDir) throws Exception {
        if (cfg == null) {
            log.warn("null instance for " + configFile);
            return 0;
        }
        if (!configFile.exists()) {
            if (cfgConfigDir == null || !cfgConfigDir.isDirectory() || !cfgConfigDir.canWrite()) {
                return 0;
            }
            createConfigFile(cfg.getClass(), cfgConfigDir, configFile.getName(), false);
        }
        //ConfigurationMonitor.listener.stop();
        int updated = updatePasswords(configFile, null, mode.isEncryptMode());
        if (mode.isCliMode()) {
            System.out.println(updated + " config items have been " + (mode.isEncryptMode() ? "encrypted" : "decrypted") + " in " + configFile.getAbsolutePath());
            return updated;
        }
        cfg.load(configFile, true);
        log.debug(() -> cfg.info());
        cfgUpdateTasks.put(configFile, () -> {
            Throwable cause = null;
            if (listener != null) {
                listener.onBefore(configFile, cfg);
            }
            log.warn(I18n.info.cfgChangedBefore.format(defaultRB, cfg.info()));
            try {
                //ConfigurationMonitor.listener.stop();
                updatePasswords(configFile, null, true);
                //ConfigurationMonitor.listener.start();
                JExpressConfig temp = cfg.temp();
                if (temp != null) {
                    temp.load(configFile, false);// try it first on a temp vo to avoid bad CFG file damage the current settings
                }
                cfg.load(configFile, true);
            } catch (Throwable ex) {
                cause = ex;
                log.error(configFile, ex);
            } finally {
                log.warn(I18n.info.cfgChangedAfter.format(defaultRB, cfg.info()));
                if (listener != null) {
                    listener.onAfter(configFile, cfg, cause);
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    cfg.shutdown();
                    System.out.println(Thread.currentThread().getName() + ": shutdown - done!");
                }, "ShutdownHook." + cfg.name())
        );
        return updated;
    }

    private StringBuilder sb = null;
    private final String cfgFile;

    public ConfigUtil(String cfgFile) {
        this.cfgFile = cfgFile;
    }

    public void addError(String e) {
        if (sb == null) {
            sb = new StringBuilder();
            sb.append("config file = ").append(cfgFile);
        }
        sb.append(System.lineSeparator() + "\t").append(e);
    }

    public String getError() {
        return sb == null ? null : sb.toString();
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public boolean getAsBoolean(Properties props, String key, Boolean defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return false;
            } else {
                return defaultValue;
            }
        }
        try {
            return Boolean.parseBoolean(v.trim());
        } catch (RuntimeException ex) {
            addError("invalid \"" + key + "\"");
            return false;
        }
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public int getAsInt(Properties props, String key, Integer defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return Integer.MIN_VALUE;
            } else {
                return defaultValue;
            }
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException ex) {
            addError("invalid \"" + key + "\"");
            return Integer.MIN_VALUE;
        }
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public long getAsLong(Properties props, String key, Long defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return Long.MIN_VALUE;
            } else {
                return defaultValue;
            }
        }
        try {
            return Long.parseLong(v.trim());
        } catch (RuntimeException ex) {
            addError("invalid \"" + key + "\"");
            return Long.MIN_VALUE;
        }
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public float getAsFloat(Properties props, String key, Float defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return Float.MIN_VALUE;
            } else {
                return defaultValue;
            }
        }
        try {
            return Float.parseFloat(v.trim());
        } catch (RuntimeException ex) {
            addError("invalid \"" + key + "\"");
            return Float.MIN_VALUE;
        }
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public double getAsDouble(Properties props, String key, Double defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return Double.MIN_VALUE;
            } else {
                return defaultValue;
            }
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (RuntimeException ex) {
            addError("invalid \"" + key + "\"");
            return Double.MIN_VALUE;
        }
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public String getAsString(Properties props, String key, String defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return null;
            } else {
                return defaultValue;
            }
        }
        return v.trim();
    }

    /**
     *
     * @param props
     * @param key
     * @param defaultValue the default value if value is not specified in props,
     * null means required and no default value
     * @return
     */
    public String[] getAsCSV(Properties props, String key, String defaultValue) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            if (defaultValue == null) {
                addError("missing \"" + key + "\"");
                return FormatterUtil.EMPTY_STR_ARRAY;
            } else {
                try {
                    return FormatterUtil.parseCsv(defaultValue);
                } catch (RuntimeException ex) {
                    addError("invalid default CSV  of \"" + key + "\"");
                }
            }
        }
        try {
            return FormatterUtil.parseCsv(v);
        } catch (RuntimeException ex) {
            addError("invalid CSV of \"" + key + "\"");
        }
        return FormatterUtil.EMPTY_STR_ARRAY;
    }

    public Long[] getAsRangeLong(Properties props, String key, Set<Long> filterCodeSet) {
        String[] a = getAsCSV(props, key, null);
        if (a.length < 1) {
            return null;
        }
        Long[] ret = null;
        if (a.length == 1) {
            String r = a[0];
            String[] ra = r.split("\\s*-\\s*");
            Long filterCodeRangeFrom = Long.valueOf(ra[0]);
            Long filterCodeRangeTo = ra.length == 1
                    ? filterCodeRangeFrom
                    : Long.valueOf(ra[1]);
            if (filterCodeRangeFrom > filterCodeRangeTo) {
                addError("\"" + key + "\" " + filterCodeRangeFrom + " should be less than \"" + filterCodeRangeTo + "\"");
            } else {
                Long[] range = {filterCodeRangeFrom, filterCodeRangeTo};
                ret = range;
            }
        } else {
            filterCodeSet.addAll(Arrays.asList(a).stream().map(Long::valueOf).collect(Collectors.toList()));
        }
        return ret;
    }

    public Double[] getAsRangeDouble(Properties props, String key, Set<Double> filterCodeSet) {
        String[] a = getAsCSV(props, key, null);
        if (a.length < 1) {
            return null;
        }
        Double[] ret = null;
        if (a.length == 1) {
            String r = a[0];
            String[] ra = r.split("\\s*-\\s*");
            Double filterCodeRangeFrom = Double.parseDouble(ra[0]);
            Double filterCodeRangeTo = ra.length == 1
                    ? filterCodeRangeFrom
                    : Double.parseDouble(ra[1]);
            if (filterCodeRangeFrom > filterCodeRangeTo) {
                addError("\"" + key + "\" " + filterCodeRangeFrom + " should be less than \"" + filterCodeRangeTo + "\"");
            } else {
                Double[] range = {filterCodeRangeFrom, filterCodeRangeTo};
                ret = range;
            }
        } else {
            filterCodeSet.addAll(Arrays.asList(a).stream().map(Double::valueOf).collect(Collectors.toList()));
        }
        return ret;
    }

    public static final String ENCRYPTED_WARPER_PREFIX = "ENC";
    public static final String DECRYPTED_WARPER_PREFIX = "DEC";

    public String getAsPassword(Properties props, String key) {
        String v = props.getProperty(key);
        if (StringUtils.isBlank(v)) {
            return null;
        }
        String pwd = null;
        try {
            String value = v.trim();
            if (value.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
                pwd = SecurityUtil.decrypt(value, true);
            } else {
                addError("invalid format, expected:  \"" + key + "\"=ENC(encrypted value)");
            }
        } catch (Throwable ex) {
            pwd = null;
            addError("invalid \"" + key + "\"");
        }
        return pwd;
    }

    public static int updatePasswords(File configFile, File destFile, boolean encrypt) throws IOException, GeneralSecurityException {
        if (!configFile.exists()) {
            return 0;
        }
        int updated = 0;
        //File newFile = new File("src/test/resources/config/cfg_all3.properties");
        StringBuilder sb = new StringBuilder();
        LineIterator iterator = FileUtils.lineIterator(configFile, "UTf-8");
        while (iterator.hasNext()) {
            String line = iterator.nextLine().trim();
            if (!line.startsWith("#")) {
                String updatedLine = FormatterUtil.updateProtectedLine(line, encrypt);
                if (updatedLine != null) {
                    line = updatedLine;
                    updated++;
                }
            }
            sb.append(line).append(System.lineSeparator());
        }
        if (updated > 0) {
            if (destFile == null) {
                destFile = configFile;
            }
            try (FileOutputStream output = new FileOutputStream(destFile); FileChannel foc = output.getChannel();) {
                foc.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
            }
        }
        return updated;
    }

    public Map<String, Integer> getAsBindingAddress(Properties props, String key) {
        try {
            String v = props.getProperty(key).trim();
            if (StringUtils.isBlank(v)) {
                addError("invalid \"" + key + "\"");
                return null;
            }
            return FormatterUtil.parseBindingAddresss(v);
        } catch (Throwable ex) {
            addError("invalid \"" + key + "\"");
            return null;
        }
    }

    public KeyManagerFactory getAsKeyManagerFactory(Properties props, String configFolder, String keyFile, String storePwd, String keyAlias, String keyPwd) {
        String keyFileValue = props.getProperty(keyFile);
        if (StringUtils.isBlank(keyFileValue)) {
            return null;
        }
        String sslKeyStorePath = getFile(configFolder, keyFileValue).getAbsolutePath();
        String alias = props.getProperty(keyAlias);
        KeyManagerFactory kmf = null;
        try {
            String pwd = getAsPassword(props, storePwd);
            char[] pwdStore = pwd == null ? null : pwd.toCharArray();

            pwd = getAsPassword(props, keyPwd);
            char[] pwdKey = StringUtils.isBlank(alias) || pwd == null ? null : pwd.toCharArray();

            kmf = SSLUtil.buildKeyManagerFactory(sslKeyStorePath, pwdStore, alias, pwdKey);
        } catch (Throwable ex) {
            addError("Failed to load \"" + sslKeyStorePath + "\") - " + ex.toString());
        }
        return kmf;
    }

    public TrustManagerFactory getAsTrustManagerFactory(Properties props, String configFolder, String keyFile, String storePwd) {
        String trustStorePath = props.getProperty(keyFile);
        if (StringUtils.isBlank(trustStorePath)) {
            return null;
        }

        String sslKeyStorePath = getFile(configFolder, trustStorePath).getAbsolutePath();
        TrustManagerFactory tmf = null;
        try {
            String pwd = getAsPassword(props, storePwd);
            char[] pwdStore = pwd == null ? null : pwd.toCharArray();
            tmf = SSLUtil.buildTrustManagerFactory(sslKeyStorePath, pwdStore);
        } catch (Throwable ex) {
            addError("Failed to load \"" + sslKeyStorePath + "\") - " + ex.toString());
        }
        return tmf;
    }

    public File getFile(String configFolder, String filepath) {
        File file = new File(filepath);
        if (!file.isAbsolute()) {
            file = new File(configFolder + File.separator + filepath);
        }
        return file.getAbsoluteFile();
    }
}
