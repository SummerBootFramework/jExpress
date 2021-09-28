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
package org.summerframework.nio.server.ws.rs;

import org.summerframework.nio.server.domain.Error;
import org.summerframework.nio.server.domain.ServiceRequest;
import org.summerframework.nio.server.domain.ServiceResponse;
import org.summerframework.util.BeanValidationUtil;
import org.summerframework.util.JsonUtil;
import org.summerframework.util.ReflectionUtil;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
class JaxRsRequestParameter {

    public enum ParamType {
        Request, Response, Body_STRING, Body_JSON, Body_XML, Body_By_RquestType, PathParam, MatrixParam, QueryParam, FormParam, HeaderParam, CookieParam
    }

    private final Class targetClass;
    private final Type parameterizedType;
    private final Type[] argTypes;
    private final Class genericClassT;
    private final ParamType type;
    private final String key;
    private final String defaultValue;
    private final boolean isRequired;
    private final boolean requestBodyAllowed;
    private boolean autoBeanValidation = false;
    private boolean cookieParamObj = false;
    private final EnumConvert.To enumConvert;

    public JaxRsRequestParameter(String info, HttpMethod httpMethod, List<String> consumes, Parameter param) {
        String error = "\n\tparameter is not allowed in " + info + "(" + param + ")\n\t - ";
        requestBodyAllowed = httpMethod.equals(HttpMethod.POST)
                || httpMethod.equals(HttpMethod.PUT)
                || httpMethod.equals(HttpMethod.PATCH);

        parameterizedType = param.getParameterizedType();

        if (parameterizedType instanceof ParameterizedType) {
            ParameterizedType genericType = (ParameterizedType) parameterizedType;
            Type fieldRawType = genericType.getRawType();
            if (fieldRawType instanceof Class) {
                targetClass = (Class) fieldRawType;
            } else {
                targetClass = param.getType();
            }
            argTypes = genericType.getActualTypeArguments();
        } else {
            targetClass = param.getType();
            argTypes = null;
        }
        if (argTypes == null || argTypes.length < 1) {
            genericClassT = null;
        } else {
            genericClassT = (Class) argTypes[0];
        }

        DefaultValue dft = param.getAnnotation(DefaultValue.class);
        if (dft == null) {
            defaultValue = null;
        } else {
            String dv = dft.value();
            defaultValue = StringUtils.isBlank(dv) ? null : dv;
        }
        if (targetClass.equals(ServiceRequest.class)) {
            type = ParamType.Request;
            key = null;
        } else if (targetClass.equals(ServiceResponse.class)) {
            type = ParamType.Response;
            key = null;
        } else {
            PathParam pathParam = param.getAnnotation(PathParam.class);
            MatrixParam matrixParam = param.getAnnotation(MatrixParam.class);
            QueryParam queryParam = param.getAnnotation(QueryParam.class);
            FormParam formParam = param.getAnnotation(FormParam.class);
            HeaderParam headerParam = param.getAnnotation(HeaderParam.class);
            CookieParam cookieParam = param.getAnnotation(CookieParam.class);
            if (pathParam != null) {
                type = ParamType.PathParam;
                key = pathParam.value();
            } else if (matrixParam != null) {
                type = ParamType.MatrixParam;
                key = matrixParam.value();
            } else if (queryParam != null) {
                type = ParamType.QueryParam;
                key = queryParam.value();
            } else if (formParam != null) {
                type = ParamType.FormParam;
                key = formParam.value();
            } else if (headerParam != null) {
                type = ParamType.HeaderParam;
                key = headerParam.value();
            } else if (cookieParam != null) {
                if (targetClass.equals(String.class)) {
                    cookieParamObj = false;
                } else if (targetClass.equals(Cookie.class)) {
                    cookieParamObj = true;
                } else {
                    throw new UnsupportedOperationException(error + "CookieParam type either String or " + Cookie.class.getName());
                }
                type = ParamType.CookieParam;
                key = cookieParam.value();
            } else {
                key = null;
                if (requestBodyAllowed) {
                    if (targetClass.equals(String.class)) {
                        type = ParamType.Body_STRING;
                    } else {
                        Valid v = param.getAnnotation(Valid.class);
                        if (v != null) {
                            autoBeanValidation = true;
                        }
                        if (consumes == null) {//default
                            type = ParamType.Body_JSON;
                        } else {
                            if (consumes.size() > 1 || consumes.contains("*/*")) {
                                type = ParamType.Body_By_RquestType;
                            } else {
                                if (consumes.contains(MediaType.APPLICATION_JSON)) {
                                    type = ParamType.Body_JSON;
                                } else if (consumes.contains(MediaType.APPLICATION_XML) || consumes.contains(MediaType.TEXT_XML)) {
                                    type = ParamType.Body_XML;
                                } else {
                                    //non-String and not JSON
                                    throw new UnsupportedOperationException(error + "Unsupported @Consumes(" + consumes + ") for non-String parameter, currently supported values: " + JaxRsRequestProcessor.SupportedProducesWithReturnType);
                                }
                            }
                        }
                    }
                } else {
                    throw new UnsupportedOperationException(error + "converting request body to non-String parameter is only allowed for POST, PUT and PATCH");
                }
            }
        }
        isRequired = param.getAnnotation(NotNull.class) != null
                || param.getAnnotation(Nonnull.class) != null;
        EnumConvert cs = param.getAnnotation(EnumConvert.class);
        if (cs != null) {
            enumConvert = cs.value();
        } else {
            enumConvert = null;
        }
    }

    public ParamType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public Object value(int badRequestErrorCode, ServiceRequest request, ServiceResponse response) throws JAXBException {
        ParamType currentType = type;
        if (currentType.equals(ParamType.Body_By_RquestType)) {
            String ct = request.getHttpHeaders().get(HttpHeaderNames.CONTENT_TYPE);
            if (ct == null) {
                currentType = ParamType.Body_JSON;//default
            } else {
                if (ct.contains(MediaType.APPLICATION_JSON)) {
                    currentType = ParamType.Body_JSON;
                } else if (ct.contains(MediaType.APPLICATION_XML) || ct.contains(MediaType.TEXT_XML)) {
                    currentType = ParamType.Body_XML;
                }
            }
        }
        switch (currentType) {
            case Request:
                return request;
            case Response:
                return response;
            case PathParam:
                String v = request.getPathParam(key);
                return parse(v, defaultValue, response, badRequestErrorCode);
            case MatrixParam:
                v = request.getMatrixParam(key);
                return parse(v, defaultValue, response, badRequestErrorCode);
            case QueryParam:
                v = request.getQueryParam(key);
                return parse(v, defaultValue, response, badRequestErrorCode);
            case FormParam:
                v = request.getFormParam(key);
                return parse(v, defaultValue, response, badRequestErrorCode);
            case HeaderParam:
                v = request.getHttpHeaders().get(key);
                return parse(v, defaultValue, response, badRequestErrorCode);
            case CookieParam:
                String value = request.getHttpHeaders().get(HttpHeaderNames.COOKIE);
                if (value == null) {
                    return null;
                }
                Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(value);
                for (Cookie cookie : cookies) {
                    if (cookie.name().equals(key)) {
                        if (cookieParamObj) {
                            return cookie;
                        } else {
                            v = cookie.value();
                            if (StringUtils.isBlank(v)) {
                                if (defaultValue != null) {
                                    v = defaultValue;
                                }
                            }
                            return v;
                        }
                    }
                }
            case Body_JSON:
                Object postDataObj;
                try {
                    if (genericClassT == null) {
                        postDataObj = JsonUtil.fromJson(targetClass, request.getHttpPostRequestBody());
                    } else {
                        postDataObj = JsonUtil.fromJson(targetClass, genericClassT, request.getHttpPostRequestBody());
                    }
                } catch (Throwable ex) {
                    // 1. convert to JSON
                    Error e = new Error(badRequestErrorCode, null, "Bad request: " + ex.toString(), null);
                    // 2. build JSON response with same app error code, and keep the default INFO log level.
                    response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    return null;
                }
                if (postDataObj == null) {
                    if (isRequired) {
                        Error e = new Error(badRequestErrorCode, null, "missing " + type, null);
                        response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    } else {
                        return null;
                    }
                } else if (autoBeanValidation) {
                    if (postDataObj instanceof Collection) {
                        Collection c = (Collection) postDataObj;
                        boolean hasError = false;
                        for (Object o : c) {
                            String validationError = BeanValidationUtil.getBeanValidationResult(o);
                            if (validationError != null) {
                                hasError = true;
                                Error e = new Error(badRequestErrorCode, null, validationError, null);
                                // 2. build JSON response with same app error code, and keep the default INFO log level.
                                response.error(e);
                            }
                        }
                        if (hasError) {
                            response.status(HttpResponseStatus.BAD_REQUEST);
                            return null;
                        }
                    } else {
                        String validationError = BeanValidationUtil.getBeanValidationResult(postDataObj);
                        if (validationError != null) {
                            Error e = new Error(badRequestErrorCode, null, validationError, null);
                            // 2. build JSON response with same app error code, and keep the default INFO log level.
                            response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                            return null;
                        }
                    }
                }
                return postDataObj;
            case Body_STRING:
                v = request.getHttpPostRequestBody();
                if (isRequired && StringUtils.isBlank(v)) {
                    Error e = new Error(badRequestErrorCode, null, "missing " + type, null);
                    response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                }
                return v;
            case Body_XML:
                v = request.getHttpPostRequestBody();
                try {
                    postDataObj = JsonUtil.fromXML(v, targetClass);
                } catch (Throwable ex) {
                    // 1. convert to JSON
                    Error e = new Error(badRequestErrorCode, null, "Bad request: " + ex.toString(), null);
                    // 2. build JSON response with same app error code, and keep the default INFO log level.
                    response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    return null;
                }
                if (postDataObj == null) {
                    if (isRequired) {
                        Error e = new Error(badRequestErrorCode, null, "missing " + type, null);
                        response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    } else {
                        return null;
                    }
                } else if (autoBeanValidation) {
                    String validationError = BeanValidationUtil.getBeanValidationResult(postDataObj);
                    if (validationError != null) {
                        Error e = new Error(badRequestErrorCode, null, validationError, null);
                        // 2. build JSON response with same app error code, and keep the default INFO log level.
                        response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                        return null;
                    }
                }
                return postDataObj;
            case Body_By_RquestType:
                v = request.getHttpPostRequestBody();
                postDataObj = parse(v, defaultValue, response, badRequestErrorCode);
                if (autoBeanValidation) {
                    String validationError = BeanValidationUtil.getBeanValidationResult(postDataObj);
                    if (validationError != null) {
                        Error e = new Error(badRequestErrorCode, null, validationError, null);
                        // 2. build JSON response with same app error code, and keep the default INFO log level.
                        response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                        return null;
                    }
                }
                return postDataObj;
        }
        return null;
    }

    private Object parse(String value, String defaultValue, ServiceResponse response, int badRequestErrorCode) {
        if (StringUtils.isBlank(value)) {
            if (defaultValue != null) {
                value = defaultValue;
            } else {
                if (isRequired) {
                    Error e = new Error(badRequestErrorCode, null, "missing " + type + "{" + key + "}=" + value, null);
                    response.status(HttpResponseStatus.BAD_REQUEST).error(e);
                }
                return ReflectionUtil.toStandardJavaType(null, targetClass, false, false, null);//primitive types devault value or null
            }
        }
        try {
            return ReflectionUtil.toJavaType(targetClass, parameterizedType, value, false, false, enumConvert);
        } catch (Throwable ex) {
            Error e = new Error(badRequestErrorCode, null, "invalid " + type + "{" + key + "}=" + value, ex);
            response.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return ReflectionUtil.toStandardJavaType(null, targetClass, false, false, null);//primitive types devault value or null
        }
    }
}
