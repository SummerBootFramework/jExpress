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

import org.summerframework.nio.server.domain.Error;
import org.summerframework.nio.server.domain.ServiceResponse;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Iterator;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class JsonUtil {

    protected static final ObjectMapper JacksonMapper = new ObjectMapper();
    protected static final ObjectMapper JacksonMapperIgnoreNull = new ObjectMapper()
            .setSerializationInclusion(Include.NON_NULL)
            .setSerializationInclusion(Include.NON_EMPTY);
    protected final static XmlMapper xmlMapper = new XmlMapper();

    static {
        JacksonMapper.registerModule(new JavaTimeModule());
        //JacksonMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        JacksonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        JacksonMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        JacksonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JacksonMapperIgnoreNull.registerModule(new JavaTimeModule());
        //JacksonMapperIgnoreNull.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        JacksonMapperIgnoreNull.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonMapperIgnoreNull.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        JacksonMapperIgnoreNull.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        JacksonMapperIgnoreNull.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Serialization, convert to JSON string, not pretty
     *
     * @param <T>
     * @param obj
     * @return
     * @throws JsonProcessingException
     */
    public static <T extends Object> String toJson(T obj) throws JsonProcessingException {
        return toJson(obj, false, false);
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
     * Deserialization, convert JSON string to object T
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

    public static final ValidatorFactory ValidatorFactory = Validation.buildDefaultValidatorFactory();

    public static boolean isValidBean(Object bean, int appErrorCode, final ServiceResponse response) {
        Set<ConstraintViolation<Object>> violations = ValidatorFactory.getValidator().validate(bean);
        if (violations.isEmpty()) {
            return true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(bean.getClass().getSimpleName()).append(" Validation Failed: ");
        Iterator<ConstraintViolation<Object>> violationsIter = violations.iterator();
        while (violationsIter.hasNext()) {
            ConstraintViolation<Object> constViolation = violationsIter.next();
            //String key = constViolation.getPropertyPath().toString();
            //String value = key + "=" + constViolation.getInvalidValue() + " - " + constViolation.getMessage();
            sb.append(constViolation.getPropertyPath()).append("=").append(constViolation.getInvalidValue())
                    .append(" - ").append(constViolation.getMessage()).append("; ");
            // error Message format. DO not use ":" in messages as it is a reserved JSON delimiter
            // Example for value: cardNumber=aaBBCC3232; The card Number is in incorrect format;
        }
        var e = new Error(appErrorCode, null, sb.toString(), null);
        response.status(HttpResponseStatus.BAD_REQUEST).error(e);

//        Map<String, String> errorMap = BeanValidationUtil.getErrorMessages(bean);
//        boolean isValid = errorMap.isEmpty();
//        if (!isValid) {
//            error.setErrorCode(AppErrorCode.BAD_REQUEST);
//            error.setErrorDesc(bean.getClass().getSimpleName() + " Validation Failed: "
//                    + errorMap.entrySet().stream().map(o -> o.getValue()).collect(Collectors.joining("; ")));
//            response.error(error).status(HttpResponseStatus.BAD_REQUEST);
//        }
        return false;
    }

    public static <T extends Object> T fromXML(String xml, Class<T> targetClass) throws JsonProcessingException {
        return (T) xmlMapper.readValue(xml, targetClass);
    }

    public static String toXML(Object obj) throws JsonProcessingException {
        return xmlMapper.writeValueAsString(obj);
    }
}
