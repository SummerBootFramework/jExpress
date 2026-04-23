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
package org.summerboot.jexpress.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootConstant;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;
import tools.jackson.databind.ser.std.SimpleFilterProvider;
import tools.jackson.dataformat.xml.XmlMapper;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BeanUtil {

    protected static final FilterProvider ServiceErrorFilter = new SimpleFilterProvider()
            .addFilter(BootConstant.JSONFILTER_NAME_SERVICEERROR, SimpleBeanPropertyFilter.serializeAllExcept("ref"));
    protected static final FilterProvider EmptyFilter = new SimpleFilterProvider()
            .addFilter(BootConstant.JSONFILTER_NAME_SERVICEERROR, SimpleBeanPropertyFilter.serializeAll());


    protected static boolean isSerializationIgnoreNull;
    protected static boolean isSerializationPretty;

    public static JsonMapper JSONMapperIncludeNull;
    public static JsonMapper JSONMapper;
    public static XmlMapper XMLMapper;

    static {
        init(TimeZone.getDefault(), true, false, false, false, true, true);
    }

    public static void init(TimeZone timeZone, boolean deserializationFailOnUnknownProperties, boolean deserializationCaseInsensitive, boolean serializationPretty, boolean serializationIgnoreEmptyArray, boolean serializationIgnoreNull, boolean showRefInServiceError) {
        isSerializationPretty = serializationPretty;
        isSerializationIgnoreNull = serializationIgnoreNull;

        JSONMapperIncludeNull = buildJsonMapper(timeZone, deserializationFailOnUnknownProperties, deserializationCaseInsensitive, serializationIgnoreEmptyArray, false, showRefInServiceError).build();
        JSONMapper = buildJsonMapper(timeZone, deserializationFailOnUnknownProperties, deserializationCaseInsensitive, serializationIgnoreEmptyArray, true, showRefInServiceError).build();
        XMLMapper = buildXmlMapper(timeZone, deserializationFailOnUnknownProperties, deserializationCaseInsensitive, serializationIgnoreEmptyArray, true, showRefInServiceError).build();
    }

    public static JsonMapper.Builder buildJsonMapper(TimeZone timeZone, boolean deserializationFailOnUnknownProperties,
                                                     boolean deserializationCaseInsensitive, boolean ignoreEmptyArray, boolean ignoreNull, boolean showRefInServiceError) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .defaultTimeZone(timeZone)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST);

        if (deserializationFailOnUnknownProperties) {
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        } else {
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        if (deserializationCaseInsensitive) {
            builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        } else {
            builder.disable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        }

        if (ignoreEmptyArray) {
            builder.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        } else {
            builder.enable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        }

        if (ignoreNull) {
            builder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL));
        } else {
            builder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS));
        }

        FilterProvider filterProvider = showRefInServiceError ? EmptyFilter : ServiceErrorFilter;
        builder.filterProvider(filterProvider);

        return builder;
    }

    public static XmlMapper.Builder buildXmlMapper(TimeZone timeZone, boolean deserializationFailOnUnknownProperties,
                                                   boolean deserializationCaseInsensitive, boolean ignoreEmptyArray, boolean ignoreNull, boolean showRefInServiceError) {
        XmlMapper.Builder builder = XmlMapper.builder()
                .defaultTimeZone(timeZone)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST);

        if (deserializationFailOnUnknownProperties) {
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        } else {
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        if (deserializationCaseInsensitive) {
            builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        }

        if (ignoreEmptyArray) {
            builder.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        } else {
            builder.enable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        }

        if (ignoreNull) {
            builder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL));
        } else {
            builder.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS));
        }

        FilterProvider filterProvider = showRefInServiceError ? EmptyFilter : ServiceErrorFilter;
        builder.filterProvider(filterProvider);

        return builder;
    }

    /**
     * Serialization, convert to JSON string, not pretty and ignore null/empty
     *
     * @param <T>
     * @param obj
     * @return
     */
    public static <T extends Object> String toJson(T obj) {
        return toJson(obj, isSerializationPretty, isSerializationIgnoreNull);
    }

    public static <T extends Object> String toJson(T obj, boolean pretty) {
        return toJson(obj, pretty, isSerializationIgnoreNull);
    }

    /**
     * Serialization, convert to JSON string
     *
     * @param <T>
     * @param obj
     * @param pretty
     * @param ignoreNull
     * @return
     */
    public static <T extends Object> String toJson(T obj, boolean pretty, boolean ignoreNull) {
        if (obj == null) {
            return "";
        }
        final ObjectMapper objectMapper = ignoreNull ? JSONMapper : JSONMapperIncludeNull;
        if (pretty) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } else {
            return objectMapper.writeValueAsString(obj);
        }
    }

    public static String toXML(Object obj) {
        return toXML(obj, isSerializationPretty);
    }

    public static <T extends Object> String toXML(T obj, boolean pretty) {
        if (obj == null) {
            return "";
        }
        if (pretty) {
            return XMLMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } else {
            return XMLMapper.writeValueAsString(obj);
        }
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <T>
     * @param json
     * @param c
     * @return
     */
    public static <T extends Object> T fromJson(String json, Class<T> c) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSONMapperIncludeNull.readValue(json, c);
    }

    public static <T extends Object> T fromJson(Class<T> c, String json) {
        return fromJson(json, c);
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <T>
     * @param json
     * @param javaType
     * @return
     */
    public static <T extends Object> T fromJson(String json, JavaType javaType) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSONMapperIncludeNull.readValue(json, javaType);
    }

    public static <T extends Object> T fromJson(String json, TypeReference<T> javaType) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSONMapperIncludeNull.readValue(json, javaType);
    }

    public static <T extends Object> T fromJson(String json, Class keyClass, Class valueClass) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        JavaType javaType = buildMapType(keyClass, valueClass);
        return JSONMapperIncludeNull.readValue(json, javaType);
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <R>
     * @param json
     * @param collectionClass
     * @param genericClasses
     * @return
     */
    public static <R extends Object> R fromJson(String json, Class<R> collectionClass, Class<?>... genericClasses) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        if (genericClasses == null) {
            return fromJson(json, collectionClass);
        }
        JavaType javaType = JSONMapperIncludeNull.getTypeFactory().constructParametricType(collectionClass, genericClasses);
        return JSONMapperIncludeNull.readValue(json, javaType);
    }

    public static JavaType buildJavaType(Class collectionClass, Class... genericClasses) {
        return JSONMapperIncludeNull.getTypeFactory().constructParametricType(collectionClass, genericClasses);
    }

    public static JavaType buildMapType(Class keyClass, Class valueClass) {
        return JSONMapperIncludeNull.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
    }

    public static <T extends Object> T fromXML(String xml, Class<T> targetClass) {
        return (T) XMLMapper.readValue(xml, targetClass);
    }

    public static final ValidatorFactory ValidatorFactory = Validation.buildDefaultValidatorFactory();

    public static String getBeanValidationResult(Object bean) {
        if (bean == null) {
            return "missing data";
        }
        Set<ConstraintViolation<Object>> violations = ValidatorFactory.getValidator().validate(bean);
        if (violations.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(bean.getClass().getSimpleName()).append(" Validation Failed: ");
        Iterator<ConstraintViolation<Object>> violationsIter = violations.iterator();
        while (violationsIter.hasNext()) {
            ConstraintViolation<Object> constViolation = violationsIter.next();
            sb.append(constViolation.getPropertyPath()).append("=").append(constViolation.getInvalidValue())
                    .append(" - ").append(constViolation.getMessage()).append("; ");

        }
        return sb.toString();
    }

//    public static String getSchema(Class<?> beanClass) {
//        JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(JacksonMapper);
//        JsonSchema schema = schemaGen.generateSchema(beanClass);
//        String schemaString = JacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
//        System.out.println(schemaString);
//    }

    /**
     * Good for all type of arrays, including primitive array
     *
     * @param <T>
     * @param array1
     * @param array2
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T arrayMergeAndRemoveDuplicated(T array1, T array2) {
        if (array1 == null) {
            return (T) arrayRemoveDuplicated(array2);
        }
        if (array2 == null) {
            return (T) arrayRemoveDuplicated(array1);
        }
        if (!array1.getClass().isArray() || !array2.getClass().isArray()) {
            throw new IllegalArgumentException("Both parameters must be array");
        }

        Class<?> arrayType1 = array1.getClass().getComponentType();
        Class<?> arrayType2 = array2.getClass().getComponentType();
        if (!arrayType1.equals(arrayType2)) {
            throw new IllegalArgumentException("Two arrays have different types: " + arrayType1 + " and " + arrayType2);
        }// now the afterward typecasting below will be safe 

        int len1 = Array.getLength(array1);
        if (len1 == 0) {
            return (T) arrayRemoveDuplicated(array2);
        }
        int len2 = Array.getLength(array2);
        if (len2 == 0) {
            return (T) arrayRemoveDuplicated(array1);
        }
        // create the merged array to be returned
        T result = (T) Array.newInstance(arrayType1, len1 + len2);
        // copy to merged array
        System.arraycopy(array1, 0, result, 0, len1);
        System.arraycopy(array2, 0, result, len1, len2);

        //return (T) arrayRemoveDuplicated((Object[]) result);
        return (T) arrayRemoveDuplicated(result);
    }

    /**
     * Good for all type of arrays, including primitive array
     *
     * @param <T>
     * @param array
     * @return
     */
    public static <T> T arrayRemoveDuplicated(T array) {
        if (array == null) {
            return null;
        }
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("Parameter must be array");
        }
        int len = Array.getLength(array);
        if (len < 2) {
            return array;
        }
        Class<?> arrayType = array.getClass().getComponentType();
        T noDuplicates = (T) Array.newInstance(arrayType, len);
        int uniqueCount = 0;

        for (int i = 0; i < len; i++) {
            Object element = Array.get(array, i);
            if (!isArrayElementPresent(noDuplicates, element)) {
                Array.set(noDuplicates, uniqueCount, element);
                uniqueCount++;
            }
        }
        T truncatedArray = (T) Array.newInstance(arrayType, uniqueCount);
        System.arraycopy(noDuplicates, 0, truncatedArray, 0, uniqueCount);
        return truncatedArray;
    }

    /**
     * Good for all type of arrays, including primitive array
     *
     * @param <T>
     * @param array
     * @param element
     * @return
     */
    public static <T> boolean isArrayElementPresent(T array, T element) {
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            Object e1 = Array.get(array, i);
            if (Objects.equals(e1, element)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T[] arrayRemoveDuplicated(T[] array) {
        if (array == null || array.length < 2) {
            return array;
        }
        Class<?> arrayType = array.getClass().getComponentType();
        T[] noDuplicates = (T[]) Array.newInstance(arrayType, array.length);
        int uniqueCount = 0;

        for (T element : array) {
            if (!isArrayElementPresent(noDuplicates, element)) {
                noDuplicates[uniqueCount] = element;
                uniqueCount++;
            }
        }
        T[] truncatedArray = (T[]) Array.newInstance(arrayType, uniqueCount);
        System.arraycopy(noDuplicates, 0, truncatedArray, 0, uniqueCount);
        return truncatedArray;
    }

    public static <T> boolean isArrayElementPresent(T[] array, T element) {
        for (T el : array) {
            //if (el == element) {
            if (Objects.equals(el, element)) {
                return true;
            }
        }
        return false;
    }
}
