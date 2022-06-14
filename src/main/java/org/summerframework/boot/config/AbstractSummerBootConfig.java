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
package org.summerframework.boot.config;

import static org.summerframework.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;
import org.summerframework.security.SecurityUtil;
import org.summerframework.util.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.StringUtils;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;
import org.summerframework.util.ReflectionUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.utils.ExceptionUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public abstract class AbstractSummerBootConfig implements SummerBootConfig {

    @JsonIgnore
    protected volatile Logger log = null;
    protected File cfgFile;
    protected String configName = getClass().getSimpleName();

    //public AbstractSummerBootConfig(){}
//    public AbstractSummerBootConfig(String configName) {
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
    public SummerBootConfig temp() {
        SummerBootConfig ret = null;
        Class c = this.getClass();
        Constructor[] css = c.getDeclaredConstructors();
        for (Constructor<SummerBootConfig> cs : css) {
            cs.setAccessible(true);
            int pc = cs.getParameterCount();
            if (pc == 0) {
                try {
                    ret = cs.newInstance();
                    return ret;
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            } else {
                Object[] params = new Object[pc];
//                for (int i = 0; i < pc; i++) {
//                    params[i] = null;
//                }
                try {
                    ret = cs.newInstance(params);
                    return ret;
                } catch (Throwable ex) {
                }
            }
        }
        return ret;
    }

    @Override
    public String info() {
        try {
            return BeanUtil.toJson(this, true, false);
        } catch (JsonProcessingException ex) {
            return ex.toString();
        }
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
        if (log == null) {
            log = LogManager.getLogger(getClass());
        }
        String configFolder = cfgFile.getParent();
        this.cfgFile = cfgFile.getAbsoluteFile();
        if (configName == null) {
            configName = cfgFile.getName();
        }
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(cfgFile);
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);) {
            props.load(isr);
        }
        ConfigUtil helper = new ConfigUtil(this.cfgFile.getAbsolutePath());
        Class c = this.getClass();
        Field[] fields = c.getDeclaredFields();
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
            helper.addError("failed to init customized configs:" + ExceptionUtils.getStackTrace(ex));
        }

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    protected abstract void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception;

    protected void loadField(Field field, String configFolder, ConfigUtil helper, Properties props, boolean autoDecrypt) throws IllegalAccessException {
        Config configSettings = field.getAnnotation(Config.class);
        if (configSettings == null) {
            return;
        }
        final String key = configSettings.key();
        String value = props.getProperty(key);
        field.setAccessible(true);
        if (StringUtils.isBlank(value)) {
            String defaultValue = configSettings.defaultValue();
            boolean hasDefaultValue = StringUtils.isNotBlank(defaultValue);
            if (hasDefaultValue) {
                value = defaultValue;
            } else {
                if (configSettings.required()) {
                    helper.addError("missing \"" + key + "\"");
                } else {
                    Object nullValue = ReflectionUtil.toStandardJavaType(null, field.getType(), false, false, null);
                    field.set(this, nullValue);
                }
                return;
            }
        }
        Config.Validate validate = configSettings.validate();
        boolean isEncrypted = validate.equals(Config.Validate.Encrypted);
        if (isEncrypted) {
            if (value.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
                try {
                    value = SecurityUtil.decrypt(value, true);
                } catch (GeneralSecurityException ex) {
                    throw new IllegalArgumentException("Failed to decrypt", ex);
                }
            } else {
                helper.addError("invalid \"" + key + "\" - require encrypted format, missing warpper: ENC(encrypted value)");
                return;
            }
        }
        boolean isEmailRecipients = validate.equals(Config.Validate.EmailRecipients);

        Class fieldClass = field.getType();
        if (fieldClass.equals(KeyManagerFactory.class)) {
            String key_storeFile = key;
            String key_storePwd = configSettings.StorePwdKey();
            String key_keyAlias = configSettings.AliasKey();
            String key_keyPwd = configSettings.AliasPwdKey();
            KeyManagerFactory kmf = helper.getAsKeyManagerFactory(props, configFolder,
                    key_storeFile, key_storePwd, key_keyAlias, key_keyPwd);
            field.set(this, kmf);
        } else if (fieldClass.equals(TrustManagerFactory.class)) {
            String key_storeFile = key;
            String key_storePwd = configSettings.StorePwdKey();
            TrustManagerFactory tmf = helper.getAsTrustManagerFactory(props, configFolder,
                    key_storeFile, key_storePwd);
            field.set(this, tmf);
        } else {
            if (value != null && (fieldClass.equals(File.class) || fieldClass.equals(Path.class))) {
                File file = new File(value);
                if (!file.isAbsolute()) {
                    value = configFolder + File.separator + value;
                }
            }
            ReflectionUtil.loadField(this, field, value, autoDecrypt, isEmailRecipients);
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
            sb.append(line).append(System.lineSeparator());
        }

        try (FileOutputStream output = new FileOutputStream(cfgFile);
                FileChannel foc = output.getChannel();) {
            foc.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    public static String generateTemplate(Class c) {
        StringBuilder sb = new StringBuilder();

        Field[] fields = c.getDeclaredFields();
        for (Field field : fields) {
            // desc
            Memo memo = field.getAnnotation(Memo.class);
            if (memo != null) {
                List<String> list = parse(memo);
                int maxSize = 0;
                for (String s : list) {
                    maxSize = Math.max(maxSize, getLength(s));
                }
                maxSize += 2;

                //1. top line ######################
                sb.append("\n\n");
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
                        sb.append("#").append(s).append("\n");
                    }
                }
                boolean isRequired = cfg.required();
                boolean hasDefaultValue = false;
                String dv = cfg.defaultValue();
                if (StringUtils.isNotBlank(dv)) {
                    hasDefaultValue = true;
                }
                if (!isRequired || hasDefaultValue) {
                    sb.append("#");
                }
                String key = cfg.key();
                sb.append(key).append("=");
                if (isEncrypted) {
                    sb.append("DEC(");
                }
                if (hasDefaultValue) {
                    sb.append(dv);
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
                            sb.append("DEC()");
                        }
                        sb.append("\n");
                    }
                    i++;
                }
                if (StringUtils.isNotBlank(cm)) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    private static List<String> parse(Memo memo) {
        List<String> ret = new ArrayList<>();
        lineBreak(memo.title(), null, ret);
        lineBreak(memo.desc(), null, ret);
        lineBreak(memo.format(), "Format: ", ret);
        lineBreak(memo.example(), "Example: ", ret);

        return ret;
    }

    private static String[] lineBreak(String s, String prefix, List<String> list) {
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

    private static int getLength(String s) {
        return StringUtils.isBlank(s) ? 0 : s.trim().length();
    }
}
