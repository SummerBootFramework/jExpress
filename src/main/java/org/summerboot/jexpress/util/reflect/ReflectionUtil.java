/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.util.reflect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.summerboot.jexpress.annotation.config.Config;
import org.summerboot.jexpress.annotation.validation.EnumConvert;
import org.summerboot.jexpress.annotation.validation.UniqueIgnore;
import org.summerboot.jexpress.security.crypto.EncryptorUtil;
import org.summerboot.jexpress.util.time.TimeUtil;
import org.summerboot.jexpress.util.format.FormatterUtil;
import org.summerboot.jexpress.util.lang.BeanUtil;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static org.summerboot.jexpress.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ReflectionUtil {

    protected static final Set<Class<?>> PluginClasses = new HashSet();


    public static void setPluginClasses(Set<Class<?>> pluginClasses) {
        PluginClasses.clear();
        if (pluginClasses != null && !pluginClasses.isEmpty()) {
            PluginClasses.addAll(pluginClasses);
        }
    }

    /**
     * REF2610-1: support application without package specified.
     * Creates a Reflections instance for the given package name.
     * Passing {@code ""} (empty string) scans the default (no-package) classes across the full classpath.
     * Passing {@code null} or whitespace-only strings is not allowed and will throw IllegalArgumentException.
     *
     * @param rootPackageName the package name, or {@code ""} for the default package
     * @param ignoredPackages the package names to be ingored, can be null or empty; null/blank entries will be ignored
     * @return a configured {@link Reflections} instance
     */
    private static Reflections buildReflections(String rootPackageName, String... ignoredPackages) {
        if (rootPackageName == null || !rootPackageName.trim().isEmpty()) {
            // normal named package (non-blank)
            return new Reflections(rootPackageName);
        }
        // rootPackageName is "" — scan full classpath for default-package classes
        ConfigurationBuilder cb = new ConfigurationBuilder().setUrls(ClasspathHelper.forJavaClassPath());
        FilterBuilder filter = new FilterBuilder();
        boolean hasRule = false;
        if (StringUtils.isNotBlank(rootPackageName)) {
            filter.includePackage(rootPackageName);
            hasRule = true;
        }
        if (ignoredPackages != null) {
            for (String p : ignoredPackages) {
                if (StringUtils.isNotBlank(p)) {
                    filter.excludePackage(p);
                    hasRule = true;
                }
            }
        }
        if (hasRule) {
            cb.filterInputsBy(filter);
        }
        return new Reflections(cb);
    }

    public static <T extends Object> Set<Class<? extends T>> getAllImplementationsByInterface(Class<T> interfaceClass, Collection<String> rootPackageNames) {
        String[] sa = rootPackageNames.toArray(String[]::new);
        return getAllImplementationsByInterface(interfaceClass, sa);
    }

    /**
     * @param <T>
     * @param interfaceClass
     * @param rootPackageNames
     * @return
     */
    public static <T extends Object> Set<Class<? extends T>> getAllImplementationsByInterface(Class<T> interfaceClass, String... rootPackageNames) {
        Set<Class<? extends T>> classes = new HashSet();
        for (String rootPackageName : rootPackageNames) {
            if (rootPackageName == null || (!rootPackageName.isEmpty() && rootPackageName.isBlank())) {
                continue;// skip null and whitespace-only; allow "" to scan default (no-package) classes
            }
            Reflections reflections = buildReflections(rootPackageName);//REF2610-1
            Set<Class<? extends T>> cs = reflections.getSubTypesOf(interfaceClass);
            if (cs.isEmpty()) {
                continue;
            }
            classes.addAll(cs);
        }
        for (Class c : PluginClasses) {
            if (interfaceClass.isAssignableFrom(c)) {
                classes.add(c);
            }
        }
        return classes;
    }

    /**
     * @param annotation
     * @param rootPackageNames
     * @param honorInherited
     * @return
     */
    public static Set<Class<?>> getAllImplementationsByAnnotation(Class<? extends Annotation> annotation, boolean honorInherited, String... rootPackageNames) {
        Set<Class<?>> classes = new HashSet();
        for (String rootPackageName : rootPackageNames) {
            if (rootPackageName == null || (!rootPackageName.isEmpty() && rootPackageName.isBlank())) {
                continue;// skip null and whitespace-only; allow "" to scan default (no-package) classes
            }
            Reflections reflections = buildReflections(rootPackageName);//REF2610-1
            Set<Class<?>> cs = reflections.getTypesAnnotatedWith(annotation, honorInherited);
            if (cs.isEmpty()) {
                continue;
            }
            classes.addAll(cs);
        }
        classes.addAll(PluginClasses);
        Set<Class<?>> ret = new HashSet();// honorInherited not working as expected, that will cause classes could contain subclasses with no such annotation
        for (Class c : classes) {
            if (c.isAnnotationPresent(annotation)) {
                ret.add(c);
            }
        }
        return ret;
    }

    public static Set<Class<?>> getAllImplementationsByAnnotation(Class<? extends Annotation> annotation, boolean honorInherited, Collection<String> rootPackageNames) {
        String[] sa = rootPackageNames.toArray(String[]::new);
        return getAllImplementationsByAnnotation(annotation, honorInherited, sa);
    }

    /**
     * @param targetClass
     * @param includeSuperClasses
     * @return all interfaces of the targetClass
     */
    public static List<Class> getAllInterfaces(Class targetClass, boolean includeSuperClasses) {
        List<Class> ret = new ArrayList();
        while (targetClass != null) {
            Class[] ca = targetClass.getInterfaces();
            if (ca != null) {
                for (Class c : ca) {
                    if (c != null) {
                        ret.add(c);
                    }
                }
            }
            targetClass = includeSuperClasses ? targetClass.getSuperclass() : null;
        }
        return ret;
    }

    /**
     * @param targetClass
     * @return
     */
    public static List<Class> getAllSuperClasses(Class targetClass) {
        List<Class> ret = new ArrayList();
        Class parent = targetClass.getSuperclass();
        while (parent != null) {
            ret.add(parent);
            parent = parent.getSuperclass();
        }
        return ret;
    }

    /**
     * Load config settings with @Config, supported Java types:
     * <pre>{@code
     * 1. T, K: enum, String, boolean/Boolean, byte/Byte, char/short/Short, int/Integer,
     * long/Long, float/Float, double/Double, BigDecimal, URI, URL, Path, File
     * 2. <T>[] array
     *
     * 3. Immutable Set, Immutable SortedSet<T>
     *
     * 4. Immutable List<T>
     *
     * 5. Immutable Map<T, K>
     *
     * 6. KeyManagerFactory
     *
     * 7. TrustManagerFactory
     * }</pre>
     *
     * @param instance
     * @param field
     * @param value
     * @param autoDecrypt
     * @param isEmailRecipients
     * @param collectionDelimiter
     * @throws java.lang.IllegalAccessException
     */
    public static void loadField(Object instance, Field field, String value, final boolean autoDecrypt, final boolean isEmailRecipients, String collectionDelimiter) throws IllegalAccessException {
        Class targetClass = field.getType();
        Type genericType = field.getGenericType();
        field.setAccessible(true);
        Config cfgSettings = field.getAnnotation(Config.class);
        boolean trim = cfgSettings == null ? false : cfgSettings.trim();
        Object v = toJavaType(targetClass, genericType, value, trim, autoDecrypt, isEmailRecipients, null, collectionDelimiter);
        field.set(instance, v);
    }

    protected static final Type[] DEFAULT_ARG_TYPES = {String.class, String.class};

    public static Object toJavaType(Class targetClass, Type genericType, String value, final boolean trim, final boolean autoDecrypt,
                                    final boolean isEmailRecipients, EnumConvert.To enumConvert, String collectionDelimiter) throws IllegalAccessException {
        if (StringUtils.isBlank(value)) {
            Object nullValue = ReflectionUtil.toStandardJavaType(null, trim, targetClass, autoDecrypt, false, enumConvert);
            return nullValue;
        }

        if (trim) {
            value = value.trim();
        }
//        Class targetClass = field.getType();
//        Type genericType = field.getGenericType();
        Type[] argTypes = DEFAULT_ARG_TYPES;
        Class[] upperBoundClasses = {};
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type fieldRawType = parameterizedType.getRawType();
            if (fieldRawType instanceof Class) {
                targetClass = (Class) fieldRawType;
            }
            argTypes = parameterizedType.getActualTypeArguments();
            upperBoundClasses = new Class[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                Type upperBoundType = argTypes[i];
                if (upperBoundType instanceof WildcardType) {
                    //String classT = argTypes[0].getTypeName();
                    upperBoundClasses[i] = (Class) ((WildcardType) upperBoundType).getUpperBounds()[0];
                } else if (upperBoundType instanceof Class) {
                    upperBoundClasses[i] = (Class) upperBoundType;
                }
            }
        }

        if (targetClass.isArray()) {
            String[] valuesStr = FormatterUtil.parseDsv(value, collectionDelimiter);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = targetClass.getComponentType();
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], trim, classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return array;
        } else if (targetClass.equals(Set.class)) {
            String[] valuesStr = FormatterUtil.parseDsv(value, collectionDelimiter);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = upperBoundClasses[0];//(Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], trim, classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return Set.of((Object[]) array);// Collections.newSetFromMap(new ConcurrentHashMap<>())
        } else if (targetClass.equals(SortedSet.class)) {
            String[] valuesStr = FormatterUtil.parseDsv(value, collectionDelimiter);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = upperBoundClasses[0];//(Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], trim, classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return ImmutableSortedSet.copyOf(List.of((Object[]) array));
        } else if (targetClass.equals(List.class)) {
            String[] valuesStr = FormatterUtil.parseDsv(value, collectionDelimiter);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = upperBoundClasses[0];//(Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], trim, classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return List.of((Object[]) array);
        } else if (targetClass.equals(Map.class)) {
            Map<String, String> stringMap = FormatterUtil.parseMap(value);
            if (stringMap == null || stringMap.isEmpty()) {
                return null;
            }
            Class classT1 = upperBoundClasses[0];//(Class) argTypes[0];
            Class classT2 = upperBoundClasses[1];//(Class) argTypes[1];
            Map ret = new HashMap();
            for (var k : stringMap.keySet()) {
                String v = stringMap.get(k);
                Object keyT = toStandardJavaType(k, trim, classT1, autoDecrypt, isEmailRecipients, enumConvert);
                Object valueT = toStandardJavaType(v, trim, classT2, autoDecrypt, isEmailRecipients, enumConvert);
                ret.put(keyT, valueT);
            }
            return Map.copyOf(ret);
        } else if (targetClass.equals(Class.class)) {
            // CWE-470 warning: use with caution, validate the class name against a combination of white and black lists to ensure that only expeted classes are loaded
            try {
                Class ret = Class.forName(value);// CWE-470 False Positive - Util Feature: caller should define a white/black list
                if (upperBoundClasses[0] != null && !upperBoundClasses[0].isAssignableFrom(ret)) {
                    throw new IllegalArgumentException("invalid Class name: " + value + ", expected a type of " + upperBoundClasses[0].getName());
                }
                return ret;
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("invalid Class name: " + value, ex);
            }
        } else if (targetClass.equals(JsonNode.class)) {
            try {
                return BeanUtil.JSONMapper.readTree(value);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("invalid json data: " + value, ex);
            }
        } else {
            Object v = toStandardJavaType(value, trim, targetClass, autoDecrypt, isEmailRecipients, enumConvert);
            return v;
        }
    }

    /**
     * T: enum, String, boolean/Boolean, byte/Byte, short/Short, int/Integer,
     * long/Long, float/Float, double/Double, BigDecimal, URI, URL, Path, File
     *
     * @param value
     * @param targetClass
     * @param autoDecrypt       auto decrypt value in ENC() format if true
     * @param enumConvert
     * @param isEmailRecipients
     * @return
     */
    public static Object toStandardJavaType(String value, final boolean trim, final Class targetClass, final boolean autoDecrypt,
                                            final boolean isEmailRecipients, EnumConvert.To enumConvert) {
        if (StringUtils.isBlank(value)) {
            if (targetClass.equals(boolean.class)) {
                return false;
            } else if (targetClass.equals(char.class)) {
                return (char) 0;
            } else if (targetClass.equals(byte.class)
                    || targetClass.equals(short.class)
                    || targetClass.equals(int.class)
                    || targetClass.equals(long.class)
                    || targetClass.equals(float.class)
                    || targetClass.equals(double.class)) {
                return (byte) 0;
            } else if (targetClass.equals(TimeZone.class)) {
                return TimeZone.getDefault();
            } else {
                return null;
            }
        }
        if (trim) {
            value = value.trim();
        }
        if (autoDecrypt && value.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
            try {
                value = EncryptorUtil.decrypt(value, true);
            } catch (GeneralSecurityException ex) {
                throw new IllegalArgumentException("Failed to decrypt", ex);
            }
        }
        if (isEmailRecipients) {
            Matcher matcher = FormatterUtil.REGEX_EMAIL_PATTERN.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid email address format");
            }
        }

        if (targetClass.equals(String.class)) {
            return value;
        } else if (targetClass.equals(boolean.class) || targetClass.equals(Boolean.class)) {
            return Boolean.parseBoolean(value);
        } else if (targetClass.equals(byte.class) || targetClass.equals(Byte.class)) {
            return Byte.parseByte(value);
        } else if (targetClass.equals(char.class)) {
            return (char) Short.parseShort(value);
        } else if (targetClass.equals(short.class) || targetClass.equals(Short.class)) {
            return Short.parseShort(value);
        } else if (targetClass.equals(int.class) || targetClass.equals(Integer.class)) {
            return Integer.parseInt(value);
        } else if (targetClass.equals(long.class) || targetClass.equals(Long.class)) {
            return Long.parseLong(value);
        } else if (targetClass.equals(float.class) || targetClass.equals(Float.class)) {
            return Float.parseFloat(value);
        } else if (targetClass.equals(double.class) || targetClass.equals(Double.class)) {
            return Double.parseDouble(value);
        } else if (targetClass.equals(BigDecimal.class)) {
            return BigDecimal.valueOf(Double.parseDouble(value));
        } else if (targetClass.equals(URI.class)) {
            try {
                return new URI(value);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("invalid URI format", ex);
            }
        } else if (targetClass.equals(URL.class)) {
            try {
                return new URI(value).toURL();
            } catch (URISyntaxException | MalformedURLException ex) {
                throw new IllegalArgumentException("invalid URL format", ex);
            }
        } else if (targetClass.equals(Path.class)) {
            File f = new File(value);// CWE-73 False Positive - Util Feature: caller should validate the use against a combination of white and black lists to ensure that only expeted classes are loaded
            return f.getAbsoluteFile().toPath().normalize();
        } else if (targetClass.equals(File.class)) {
            File f = new File(value);// CWE-73 False Positive - Util Feature: caller should validate the use against a combination of white and black lists to ensure that only expeted classes are loaded
            Path path = f.getAbsoluteFile().toPath().normalize();
            return path.toFile();
        } else if (targetClass.isEnum()) {
            if (enumConvert != null) {
                switch (enumConvert) {
                    case UpperCase:
                        value = value.toUpperCase();
                        break;
                    case LowerCase:
                        value = value.toLowerCase();
                        break;
                }
            }
            return Enum.valueOf((Class<Enum>) targetClass, value);
        } else if (targetClass.equals(OffsetDateTime.class)) {
            return OffsetDateTime.parse(value, TimeUtil.ISO8601_ZONED_DATE_TIME);
        } else if (targetClass.equals(ZonedDateTime.class)) {
            return ZonedDateTime.parse(value, TimeUtil.ISO8601_ZONED_DATE_TIME);
        } else if (targetClass.equals(LocalDateTime.class)) {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } else if (targetClass.equals(LocalDate.class)) {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } else if (targetClass.equals(TimeZone.class)) {
            if (value.equalsIgnoreCase("system") || value.equalsIgnoreCase("default") || value.equalsIgnoreCase("SystemDefault")) {
                return TimeZone.getDefault();
            }
            return TimeZone.getTimeZone(value);
        } else if (targetClass.equals(InetSocketAddress.class) || targetClass.equals(SocketAddress.class)) {
            //String[] ap = value.trim().split(FormatterUtil.REGEX_BINDING_MAP);
            String[] ap = value.trim().split(":");
            return new InetSocketAddress(ap[0], Integer.parseInt(ap[1]));
        } else if (targetClass.equals(Charset.class)) {
            return Charset.forName(value);
        } else {
//            //1. try JSON
//            try {
//                return JsonUtil.fromJson(targetClass, value);
//            } catch (JsonProcessingException ex) {
//            }
//            //2. try XML
//            try {
//                return JsonUtil.fromXML(targetClass, value);
//            } catch (JsonProcessingException ex) {
//            }

            //1. There is a static method or a method named fromstring that accepts string parameters
            //(for example: Integer.valueOf(String) and java.util.UUID.fromString(String));            
            try {
                Method mtd = targetClass.getMethod("valueOf", String.class);
                return mtd.invoke(null, value);
            } catch (NoSuchMethodException | SecurityException ex) {
                //no static fromString(String)
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                //failed to call static fromString(String)
            }
            try {
                Method mtd = targetClass.getMethod("of", String.class);
                return mtd.invoke(null, value);
            } catch (NoSuchMethodException | SecurityException ex) {
                //no static fromString(String)
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                //failed to call static fromString(String)
            }
            try {
                Method mtd = targetClass.getMethod("fromString", String.class);
                return mtd.invoke(null, value);
            } catch (NoSuchMethodException | SecurityException ex) {
                //no static fromString(String)
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                //failed to call static fromString(String)
            }
            //2. There is a constructor that accepts a string parameter
            try {
                Constructor cst = targetClass.getConstructor(String.class);
                return cst.newInstance(value);
            } catch (NoSuchMethodException | SecurityException ex) {
                //no constructor with (String)
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                     InvocationTargetException ex) {
                //failed to crate instance
            }
            try {
                return BeanUtil.fromJson(targetClass, value);
            } catch (Exception ex) {
            }
        }
        return null;
    }

    public static void loadFields(Class targetClass, Class fieldClass, Map results, boolean includeClassName) throws IllegalArgumentException, IllegalAccessException {
        loadFields(targetClass, fieldClass, results, includeClassName, false);
    }

    public static void loadFields(Class targetClass, Class fieldClass, Map results, boolean includeClassName, boolean useUniqueIgnore) throws IllegalArgumentException, IllegalAccessException {
        Class parent = targetClass.getSuperclass();
        if (parent != null) {
            loadFields(parent, fieldClass, results, includeClassName, useUniqueIgnore);
        }
        Class[] intfs = targetClass.getInterfaces();
        if (intfs != null) {
            for (Class i : intfs) {
                loadFields(i, fieldClass, results, includeClassName, useUniqueIgnore);
            }
        }
        Field[] fields = targetClass.getDeclaredFields();
        for (Field field : fields) {
            //field.setAccessible(true);
            Class type = field.getType();
            if (!fieldClass.equals(type)) {
                continue;
            }
            if (useUniqueIgnore) {
                UniqueIgnore aUI = field.getAnnotation(UniqueIgnore.class);
                if (aUI != null) {
                    continue;
                }
            }
            String varName = includeClassName ? targetClass.getName() + "." + field.getName() : field.getName();
            Object varValue = field.get(null);
            results.put(varName, varValue);
        }
    }

    public static String loadFields(Class targetClass, Class fieldClass) throws IllegalArgumentException, IllegalAccessException, JsonProcessingException {
        Map<String, Integer> results = new HashMap();
        loadFields(targetClass, fieldClass, results, false);
        Map<Object, String> sorted = results
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
        return BeanUtil.toJson(sorted, true, false);
    }

    //protected static final String MY_PACKAGE_ROOT = "org.";
    public static String getRootPackageName(Class callerClass, int level) {
        String packageName = callerClass.getPackageName();
        if (level < 1) {
            return packageName;
        }
        String[] packageNames = FormatterUtil.parseDsv(packageName, "\\.");
        if (level >= packageNames.length) {
            return packageName;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(packageNames[i]);
        }
        return sb.toString();

//        if (packageName.startsWith(MY_PACKAGE_ROOT)) {
//            int offset = MY_PACKAGE_ROOT.length();
//            return packageName.substring(0, packageName.indexOf(".", offset));
//        }
//        return packageName.substring(0, packageName.indexOf("."));
    }

    public static List<Field> getDeclaredAndSuperClassesFields(Class targetClass) {
        List<Field> ret = new ArrayList();
        do {
            Field[] fields = targetClass.getDeclaredFields();
            ret.addAll(Arrays.asList(fields));
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null);
        return ret;
    }

    public static List<Field> getDeclaredAndSuperClassesFields(Class targetClass, boolean isParentFirst) {
        List<Field> ret = new ArrayList();
        /*do {
            Field[] fields = targetClass.getDeclaredFields();
            ret.addAll(Arrays.asList(fields));
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null);*/

        List<Class> classes = getAllSuperClasses(targetClass);
        classes.add(0, targetClass);
        if (isParentFirst) {
            Collections.reverse(classes);
        }

        for (Class c : classes) {
            Field[] fields = c.getDeclaredFields();
            ret.addAll(Arrays.asList(fields));
        }
        return ret;
    }

    public static List<Method> getDeclaredAndSuperClassesMethods(Class targetClass, boolean includOverriddenSuperMethod) {
        List<Method> ret = new ArrayList();
        do {
            Method[] methods = targetClass.getDeclaredMethods();
            if (includOverriddenSuperMethod) {
                ret.addAll(Arrays.asList(methods));
            } else {
                for (Method m : methods) {
                    if (ret.contains(m)) {
                        continue;
                    }
                    ret.add(m);
                }
            }
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null);
        return ret;
    }

    public static Method getMethod(Class targetClass, String methodName, Class[] cArg) {
        Method ret = null;
        do {
            try {
                ret = targetClass.getDeclaredMethod(methodName, cArg);
            } catch (NoSuchMethodException ex) {
                targetClass = targetClass.getSuperclass();
//                if (targetClass == null) {
//                    throw ex;
//                }
            }
        } while (ret == null && targetClass != null);
        return ret;
    }

    /**
     * Checks if the given Class object is an abstract class.
     *
     * @param clazz The Class object to be checked.
     * @return true if the class is abstract; otherwise, returns false.
     */
    public static boolean isAbstract(Class<?> clazz) {
        // 1. 获取类的修饰符的位掩码
        int modifiers = clazz.getModifiers();

        // 2. 使用 Modifier.isAbstract 静态方法检查 abstract 标志是否设置
        return Modifier.isAbstract(modifiers);
    }

    /**
     * Removes all Class objects from the set that are superclasses of any other member
     * within the set. It retains only the most concrete subclasses in the inheritance chain.
     *
     * @param classSet
     * @param <T>
     * @return
     */
    public static <T extends Object> Set<Class<? extends T>> retainSubclasses(Set<? extends Class<?>> classSet) {

        if (classSet == null || classSet.isEmpty()) {
            return new HashSet<>();
        }

        // 泛型通配符捕获。由于我们需要在循环中进行 isAssignableFrom 检查，
        // 且知道传入的元素是 Class<?> 的子类型，这里可以安全地进行转换。
        @SuppressWarnings("unchecked")
        Set<Class<? extends T>> cs = (Set<Class<? extends T>>) classSet;

        // 过滤逻辑：保留那些“不是集合中任何其他类的父类”的成员
        Set<Class<? extends T>> filteredSet = cs.stream()
                .filter(potentialSubclass ->
                        !isSuperclassOfAnyOther(potentialSubclass, cs)
                )
                .collect(Collectors.toSet());

        return filteredSet;
    }

    /**
     * Checks if the given class 'clazz' is a superclass or interface of any other class
     * in the provided set 'allClasses'.
     *
     * @param clazz
     * @param allClasses
     * @return
     */
    private static boolean isSuperclassOfAnyOther(Class<?> clazz, Set<? extends Class<?>> allClasses) {
        for (Class<?> otherClass : allClasses) {
            // 排除自身比较
            if (clazz.equals(otherClass)) {
                continue;
            }

            // isAssignableFrom() checks if clazz is a superclass/interface of otherClass
            // If true, it means 'clazz' is a less specific class and should be filtered out.
            if (clazz.isAssignableFrom(otherClass)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Core entry point: resolves the Nth generic type argument declared on a target
     * superclass/interface for a given instance.
     *
     * @param handler
     * @param index
     * @return
     */

    public static Class<?> getInboundType(Object handler, int index) {
        Class<?> c = handler.getClass();
        while (c != null && c != Object.class) {
            // 1. Check superclass
            Type gs = c.getGenericSuperclass();

            // 1. checks if gs is a ParameterizedType AND binds it to pt, then checks if the raw type of pt is a Class<?> AND binds it to rawClass
            // 2. checks if the raw type is a Class AND binds it to rawClass
            if (gs instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawClass) {
                Class<?> result = resolveGenericType(handler, rawClass, index);
                if (result != null) return result;
            }

            // 2. Check interfaces
            Type[] interfaces = c.getGenericInterfaces();
            for (Type iface : interfaces) {
                if (iface instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawClass) {
                    Class<?> result = resolveGenericType(handler, rawClass, index);
                    if (result != null) return result;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * 2nd level entry point: resolves the Nth generic type argument declared on a target
     * superclass/interface for a given instance.
     *
     * <p>Typical scenarios:</p>
     * <ol>
     *   <li><b>Netty async handler (class inheritance)</b><br>
     *   {@code Class<?> type = ReflectionUtil.resolveGenericType(myObj, SimpleChannelInboundHandler.class, 0);}<br>
     *   Result: {@code FullStompFrame.class} or {@code WebSocketFrame.class}.
     *   </li>
     *   <li><b>Custom generic interface implementation</b><br>
     *   {@code interface Repository<T, ID> {}}<br>
     *   {@code class UserRepository implements Repository<User, Long> {}}<br>
     *   {@code resolveGenericType(new UserRepository(), Repository.class, 0)} => {@code User.class}<br>
     *   {@code resolveGenericType(new UserRepository(), Repository.class, 1)} => {@code Long.class}
     *   </li>
     *   <li><b>Multi-level inheritance + anonymous subclass</b><br>
     *   {@code class BaseService<T> {}}<br>
     *   {@code class OrderService extends BaseService<Order> {}}<br>
     *   {@code OrderService sub = new OrderService() {}; }<br>
     *   {@code resolveGenericType(sub, BaseService.class, 0)} still resolves {@code Order.class}.
     *   </li>
     *   <li><b>Direct instantiation of generic classes (cannot resolve)</b><br>
     *   {@code ArrayList<String> list = new ArrayList<>();}<br>
     *   For this case, generic information exists only at compile time and is not retained
     *   on {@code list.getClass()} (runtime class is only {@code ArrayList.class}), so result is {@code null}.
     *   </li>
     *   <li><b>Nested generics (inner type is not extracted by current implementation)</b><br>
     *   If subclass is {@code class MyHandler extends Base<List<String>>}, this method only
     *   resolves outer {@code List.class}. To resolve inner {@code String.class},
     *   {@code ParameterizedType} needs deeper parsing in {@code extractType(...)}.
     *   </li>
     * </ol>
     *
     * @param instance    the object instance to analyze
     * @param targetClass the target superclass or interface class (for example,
     *                    {@code SimpleChannelInboundHandler.class})
     * @param index       generic argument index (0-based)
     * @return resolved generic {@link Class}, or {@code null} when not found
     */
    private static Class<?> resolveGenericType(Object instance, Class<?> targetClass, int index) {
        if (instance == null || targetClass == null) return null;
        return resolveGenericType(instance.getClass(), targetClass, index);
    }

    /**
     * 递归解析类及接口
     */
    private static Class<?> resolveGenericType(Class<?> clazz, Class<?> targetClass, int index) {
        if (clazz == null || clazz == Object.class) return null;

        // 1. 检查当前类的【直接父类】是否符合目标
        Type superclass = clazz.getGenericSuperclass();
        Class<?> result = extractType(superclass, targetClass, index);
        if (result != null) return result;

        // 2. 检查当前类【直接实现的所有接口】是否符合目标
        Type[] interfaces = clazz.getGenericInterfaces();
        for (Type iface : interfaces) {
            result = extractType(iface, targetClass, index);
            if (result != null) return result;
        }

        // 3. 递归：沿着继承树往上一层父类查找
        result = resolveGenericType(clazz.getSuperclass(), targetClass, index);
        if (result != null) return result;

        // 4. 递归：沿着接口继承树往上查找
        for (Class<?> ifaceClass : clazz.getInterfaces()) {
            result = resolveGenericType(ifaceClass, targetClass, index);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * 辅助方法：从 Type 中判断并提取泛型
     */
    private static Class<?> extractType(Type type, Class<?> targetClass, int index) {
        if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            // 判断当前参数化类型的原始类型（RawType）是否是目标类
            if (paramType.getRawType() == targetClass) {
                Type[] args = paramType.getActualTypeArguments();
                if (index >= 0 && index < args.length && args[index] instanceof Class) {
                    return (Class<?>) args[index];
                }
            }
        }
        return null;
    }
}
