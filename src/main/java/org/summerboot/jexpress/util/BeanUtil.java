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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Iterator;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BeanUtil {

    private static boolean isToJsonIgnoreNull = true;
    private static boolean isToJsonPretty = false;
    private static boolean isFromJsonFailOnUnknownProperties = true;

    public static ObjectMapper JacksonMapper = new ObjectMapper();//JsonMapper.builder().init(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    public static ObjectMapper JacksonMapperIgnoreNull = new ObjectMapper()
            .setSerializationInclusion(Include.NON_NULL)
            .setSerializationInclusion(Include.NON_EMPTY);
    public static XmlMapper XMLMapper = new XmlMapper();

    public static void update(ObjectMapper objectMapper) {
        objectMapper.registerModules(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, isFromJsonFailOnUnknownProperties);
    }

    public static void init(boolean fromJsonFailOnUnknownProperties, boolean fromJsonCaseInsensitive, boolean toJsonPretty, boolean toJsonIgnoreNull) {
        isFromJsonFailOnUnknownProperties = fromJsonFailOnUnknownProperties;
        isToJsonPretty = toJsonPretty;
        isToJsonIgnoreNull = toJsonIgnoreNull;
        if (fromJsonCaseInsensitive) {
            JacksonMapper = JsonMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
            XMLMapper = XmlMapper.builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true).build();
        }
        update(JacksonMapper);
        update(JacksonMapperIgnoreNull);
        update(XMLMapper);
    }

    static {
        init(true, false, false, true);
    }

    /**
     * Serialization, convert to JSON string, not pretty and ignore null/empty
     *
     * @param <T>
     * @param obj
     * @return
     * @throws JsonProcessingException
     */
    public static <T extends Object> String toJson(T obj) throws JsonProcessingException {
        return toJson(obj, isToJsonPretty, isToJsonIgnoreNull);
    }

    /**
     * Serialization, convert to JSON string
     *
     * @param <T>
     * @param obj
     * @param pretty
     * @param ignoreNull
     * @return
     * @throws JsonProcessingException
     */
    public static <T extends Object> String toJson(T obj, boolean pretty, boolean ignoreNull) throws JsonProcessingException {
        if (obj == null) {
            return "";
        }

        if (pretty) {
            if (ignoreNull) {
                return JacksonMapperIgnoreNull.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            } else {
                return JacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            }
        } else {
            if (ignoreNull) {
                return JacksonMapperIgnoreNull.writeValueAsString(obj);
            } else {
                return JacksonMapper.writeValueAsString(obj);
            }
        }
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <T>
     * @param c
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static <T extends Object> T fromJson(Class<T> c, String json) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JacksonMapper.readValue(json, c);
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <T>
     * @param javaType
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static <T extends Object> T fromJson(JavaType javaType, String json) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JacksonMapper.readValue(json, javaType);
    }

    public static <T extends Object> T fromJson(TypeReference<T> javaType, String json) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JacksonMapper.readValue(json, javaType);
    }

    /**
     * Deserialization , convert JSON string to object T
     *
     * @param <R>
     * @param collectionClass
     * @param genericClass
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static <R extends Object> R fromJson(Class<R> collectionClass, Class<?> genericClass, String json) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        if (genericClass == null) {
            return fromJson(collectionClass, json);
        }
        JavaType javaType = JacksonMapper.getTypeFactory().constructParametricType(collectionClass, genericClass);
        return JacksonMapper.readValue(json, javaType);
    }

    public static <T extends Object> T fromXML(Class<T> targetClass, String xml) throws JsonProcessingException {
        return (T) XMLMapper.readValue(xml, targetClass);
    }

    public static String toXML(Object obj) throws JsonProcessingException {
        return XMLMapper.writeValueAsString(obj);
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
}
