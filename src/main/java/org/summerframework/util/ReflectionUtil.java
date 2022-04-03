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
package org.summerframework.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import static org.summerframework.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;
import org.summerframework.security.SecurityUtil;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Matcher;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.summerframework.nio.server.ws.rs.EnumConvert;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class ReflectionUtil {

    public static <T extends Object> Set<Class<? extends T>> getAllImplementationsByInterface(Class<T> interfaceClass, String rootPackageName) {
        Reflections reflections = new Reflections(rootPackageName);
        Set<Class<? extends T>> classes = reflections.getSubTypesOf(interfaceClass);
        return classes;
    }

    public static Set<Class<?>> getAllImplementationsByAnnotation(Class<? extends Annotation> annotation, String rootPackageName) {
        Reflections reflections = new Reflections(rootPackageName);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(annotation);
        return classes;
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
     *
     * @param instance
     * @param field
     * @param value
     * @param autoDecrypt
     * @param isEmailRecipients
     * @throws java.lang.IllegalAccessException
     */
    public static void loadField(Object instance, Field field, String value, final boolean autoDecrypt, final boolean isEmailRecipients) throws IllegalAccessException {
        Class targetClass = field.getType();
        Type genericType = field.getGenericType();
        field.setAccessible(true);
        field.set(instance, toJavaType(targetClass, genericType, value, autoDecrypt, isEmailRecipients, null));
    }

    private static final Type[] DEFAULT_ARG_TYPES = {String.class, String.class};

    public static Object toJavaType(Class targetClass, Type genericType, String value, final boolean autoDecrypt,
            final boolean isEmailRecipients, EnumConvert.To enumConvert) throws IllegalAccessException {
        if (StringUtils.isBlank(value)) {
            Object nullValue = ReflectionUtil.toStandardJavaType(null, targetClass, autoDecrypt, false, enumConvert);
            return nullValue;
        }
        value = value.trim();
//        Class targetClass = field.getType();
//        Type genericType = field.getGenericType();
        Type[] argTypes = DEFAULT_ARG_TYPES;
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type fieldRawType = parameterizedType.getRawType();
            if (fieldRawType instanceof Class) {
                targetClass = (Class) fieldRawType;
            }
            argTypes = parameterizedType.getActualTypeArguments();
        }

        if (targetClass.isArray()) {
            String[] valuesStr = FormatterUtil.parseCsv(value);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = targetClass.getComponentType();
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return array;
        } else if (targetClass.equals(Set.class)) {
            String[] valuesStr = FormatterUtil.parseCsv(value);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = (Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return Set.of((Object[]) array);
        } else if (targetClass.equals(SortedSet.class)) {
            String[] valuesStr = FormatterUtil.parseCsv(value);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = (Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return ImmutableSortedSet.copyOf(List.of((Object[]) array));
        } else if (targetClass.equals(List.class)) {
            String[] valuesStr = FormatterUtil.parseCsv(value);
            if (valuesStr == null || valuesStr.length < 1) {
                return null;
            }
            Class classT = (Class) argTypes[0];
            Object array = Array.newInstance(classT, valuesStr.length);
            for (int i = 0; i < valuesStr.length; i++) {
                Array.set(array, i, toStandardJavaType(valuesStr[i], classT, autoDecrypt, isEmailRecipients, enumConvert));
            }
            return List.of((Object[]) array);
        } else if (targetClass.equals(Map.class)) {
            Map<String, String> stringMap = FormatterUtil.parseMap(value);
            if (stringMap == null || stringMap.isEmpty()) {
                return null;
            }
            Class classT1 = (Class) argTypes[0];
            Class classT2 = (Class) argTypes[1];
            Map ret = new HashMap();
            for (var k : stringMap.keySet()) {
                String v = stringMap.get(k);
                Object keyT = toStandardJavaType(k, classT1, autoDecrypt, isEmailRecipients, enumConvert);
                Object valueT = toStandardJavaType(v, classT2, autoDecrypt, isEmailRecipients, enumConvert);
                ret.put(keyT, valueT);
            }
            return Map.copyOf(ret);
        } else {
            Object v = toStandardJavaType(value, targetClass, autoDecrypt, isEmailRecipients, enumConvert);
            return v;
        }
    }

    /**
     * T: enum, String, boolean/Boolean, byte/Byte, short/Short, int/Integer,
     * long/Long, float/Float, double/Double, BigDecimal, URI, URL, Path, File
     *
     * @param value
     * @param targetClass
     * @param autoDecrypt auto decrypt value in ENC() format if true
     * @param enumConvert
     * @param isEmailRecipients
     * @return
     */
    public static Object toStandardJavaType(String value, final Class targetClass, final boolean autoDecrypt,
            final boolean isEmailRecipients, EnumConvert.To enumConvert) {
        if (value == null) {
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
            } else {
                return null;
            }
        }
        value = value.trim();
        if (autoDecrypt && value.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
            try {
                value = SecurityUtil.decrypt(value, true);
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
                return new URL(value);
            } catch (MalformedURLException ex) {
                throw new IllegalArgumentException("invalid URL format", ex);
            }
        } else if (targetClass.equals(Path.class)) {
            File f = new File(value);
            return f.getAbsoluteFile().toPath().normalize();
        } else if (targetClass.equals(File.class)) {
            File f = new File(value);
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
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                //failed to crate instance
            }
        }
        return null;
    }

    public static void loadFields(Class targetClass, Class fieldClass, Map results, boolean includeClassName) throws IllegalArgumentException, IllegalAccessException {
        Class parent = targetClass.getSuperclass();
        if (parent != null) {
            loadFields(parent, fieldClass, results, includeClassName);
        }
        Class[] intfs = targetClass.getInterfaces();
        if (intfs != null) {
            for (Class i : intfs) {
                loadFields(i, fieldClass, results, includeClassName);
            }
        }
        Field[] fields = targetClass.getDeclaredFields();
        for (Field field : fields) {
            //field.setAccessible(true);
            Class type = field.getType();
            if (!fieldClass.equals(type)) {
                continue;
            }
            String varName = includeClassName ? targetClass.getName() + "." + field.getName() : field.getName();
            Object varValue = field.get(null);
            results.put(varName, varValue);
        }
    }
}
