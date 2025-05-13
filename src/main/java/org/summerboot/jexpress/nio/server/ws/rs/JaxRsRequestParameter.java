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
package org.summerboot.jexpress.nio.server.ws.rs;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.ReflectionUtil;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class JaxRsRequestParameter {

    public enum ParamType {
        Request, Response, Body_STRING, Body_JSON, Body_XML, Body_OnDemond_BylClientRquestType, PathParam, MatrixParam, QueryParam, FormParam, HeaderParam, CookieParam
    }

    protected final Class targetClass;
    protected final Type parameterizedType;
    protected final Type[] argTypes;
    protected final Class genericClassT;
    protected final ParamType type;
    protected final String key;
    protected final String defaultValue;
    protected final boolean isRequired;

    protected final Pattern pattern;

    //protected final boolean requestBodyAllowed;
    protected boolean autoBeanValidation = false;
    protected boolean cookieParamObj = false;
    protected final EnumConvert.To enumConvert;
    protected final String collectionDelimiter = null;// TODO

    public JaxRsRequestParameter(String info, HttpMethod httpMethod, List<String> consumes, Parameter param) {
        String error = "\n\tparameter is not allowed in " + info + "(" + param + ")\n\t - ";
        /*requestBodyAllowed = httpMethod.equals(HttpMethod.POST)
                || httpMethod.equals(HttpMethod.PUT)
                || httpMethod.equals(HttpMethod.PATCH);
        
        The RFC2616 referenced as "HTTP/1.1 spec" is now obsolete. In 2014 it was replaced by RFCs 7230-7237. 
        Quote "the message-body SHOULD be ignored when handling the request" has been deleted. 
        It's now just "Request message framing is independent of method semantics, even if the method doesn't define any use for a message body" 
        The 2nd quote "The GET method means retrieve whatever information ... is identified by the Request-URI" was deleted. 
         */

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

        pattern = param.getAnnotation(Pattern.class);
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
        } else if (targetClass.equals(SessionContext.class)) {
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
//                if (requestBodyAllowed) {
                if (targetClass.equals(String.class)) {
                    type = ParamType.Body_STRING;
                } else {
                    Valid v = param.getAnnotation(Valid.class);
                    if (v != null) {
                        autoBeanValidation = true;
                    }
                    if (consumes == null) {//default
                        type = ParamType.Body_OnDemond_BylClientRquestType;
                    } else {
                        if (consumes.size() > 1 || consumes.contains(MediaType.WILDCARD)) {
                            type = ParamType.Body_OnDemond_BylClientRquestType;
                        } else {
                            String serverConsumesOnlyOneTyoe = consumes.get(0).toLowerCase();
                            if (serverConsumesOnlyOneTyoe.contains("json")) {//if (consumes.contains(MediaType.APPLICATION_JSON) || consumes.contains(MediaType.APPLICATION_JSON_PATCH_JSON)) {
                                type = ParamType.Body_JSON;
                            } else if (serverConsumesOnlyOneTyoe.contains("xml")) {//} else if (consumes.contains(MediaType.APPLICATION_XML) || consumes.contains(MediaType.TEXT_XML)) {
                                type = ParamType.Body_XML;
                            } else {
                                //non-String, neither JSON, neither XML
                                //throw new UnsupportedOperationException(error + "Unsupported @Consumes(" + consumes + ") for non-String parameter, currently supported values: " + JaxRsRequestProcessor.SupportedProducesWithReturnType);
                                type = ParamType.Body_OnDemond_BylClientRquestType;
                            }
                        }
                    }
                }
//                } else {
//                    throw new UnsupportedOperationException(error + "converting request body to non-String parameter is only allowed for POST, PUT and PATCH");
//                }
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

    public Object value(ServiceRequest request, SessionContext context) /*throws JAXBException*/ {
        ParamType currentType = type;
        if (currentType.equals(ParamType.Body_OnDemond_BylClientRquestType)) {
            String ct = request.getHttpHeaders().get(HttpHeaderNames.CONTENT_TYPE);
            if (ct != null) {
                ct = ct.toLowerCase();
                if (ct.contains("json")) {
                    currentType = ParamType.Body_JSON;
                } else if (ct.contains("xml")) {
                    currentType = ParamType.Body_XML;
                }
            }
        }
        switch (currentType) {
            case Request:
                return request;
            case Response:
                return context;
            case PathParam:
                String v = request.getPathParam(key);
                return parse(v, defaultValue, context);
            case MatrixParam:
                v = request.getMatrixParam(key);
                return parse(v, defaultValue, context);
            case QueryParam:
                v = request.getQueryParam(key);
                return parse(v, defaultValue, context);
            case FormParam:
                v = request.getFormParam(key);
                return parse(v, defaultValue, context);
            case HeaderParam:
                v = request.getHttpHeaders().get(key);
                return parse(v, defaultValue, context);
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
                            //return v;
                            return parse(v, defaultValue, context);
                        }
                    }
                }
            case Body_JSON:
                Object postDataObj;
                try {
                    if (genericClassT == null) {
                        postDataObj = BeanUtil.fromJson(targetClass, request.getHttpPostRequestBody());
                    } else {
                        postDataObj = BeanUtil.fromJson(targetClass, genericClassT, request.getHttpPostRequestBody());
                    }
                } catch (Throwable ex) {
                    // 1. convert to JSON
                    Err e = new Err<>(BootErrorCode.BAD_REQUEST_UNKNOWN_JSON_REQUEST_BODY, null, null, ex, "Unknown request(JSON) body: " + ex.toString());
                    // 2. build JSON response with same app error code, and keep the default INFO log level.
                    context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    return null;
                }
                if (postDataObj == null) {
                    if (isRequired) {
                        Err e = new Err<>(BootErrorCode.BAD_REQUEST_MISSING_JSON_REQUEST_BODY, null, null, null, "Missing request(JSON) body: " + type);
                        context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    } else {
                        return null;
                    }
                } else if (autoBeanValidation) {
                    if (postDataObj instanceof Collection) {
                        Collection c = (Collection) postDataObj;
                        boolean hasError = false;
                        for (Object o : c) {
                            String validationError = BeanUtil.getBeanValidationResult(o);
                            if (validationError != null) {
                                hasError = true;
                                Err e = new Err<>(BootErrorCode.BAD_REQUEST_INVALID_JSON_REQUEST_BODY, null, null, null, "Invalid request(JSON) body: " + validationError);
                                // 2. build JSON response with same app error code, and keep the default INFO log level.
                                context.error(e);
                            }
                        }
                        if (hasError) {
                            context.status(HttpResponseStatus.BAD_REQUEST);
                            return null;
                        }
                    } else {
                        String validationError = BeanUtil.getBeanValidationResult(postDataObj);
                        if (validationError != null) {
                            Err e = new Err<>(BootErrorCode.BAD_REQUEST_INVALID_JSON_REQUEST_BODY, null, null, null, "Invalid request(JSON) body: " + validationError);
                            // 2. build JSON response with same app error code, and keep the default INFO log level.
                            context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                            return null;
                        }
                    }
                }
                return postDataObj;
            case Body_STRING:
                v = request.getHttpPostRequestBody();
                if (isRequired && StringUtils.isBlank(v)) {
                    Err e = new Err<>(BootErrorCode.BAD_REQUEST_MISSING_REQUEST_BODY, null, null, null, "Missing request body: " + type);
                    context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                }
                return v;
            case Body_XML:
                v = request.getHttpPostRequestBody();
                try {
                    postDataObj = BeanUtil.fromXML(targetClass, v);
                } catch (Throwable ex) {
                    // 1. convert to JSON
                    Err e = new Err<>(BootErrorCode.BAD_REQUEST_UNKNOWN_XML_REQUEST_BODY, null, null, ex, "Unknown request(XML) body: " + ex.toString());
                    // 2. build JSON response with same app error code, and keep the default INFO log level.
                    context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    return null;
                }
                if (postDataObj == null) {
                    if (isRequired) {
                        Err e = new Err<>(BootErrorCode.BAD_REQUEST_MISSING_XML_REQUEST_BODY, null, null, null, "Missing request(XML) body: " + type);
                        context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    } else {
                        return null;
                    }
                } else if (autoBeanValidation) {
                    String validationError = BeanUtil.getBeanValidationResult(postDataObj);
                    if (validationError != null) {
                        Err e = new Err<>(BootErrorCode.BAD_REQUEST_INVALID_XML_REQUEST_BODY, null, null, null, "Invalid request(XML) body: " + validationError);
                        // 2. build JSON response with same app error code, and keep the default INFO log level.
                        context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                        return null;
                    }
                }
                return postDataObj;
            case Body_OnDemond_BylClientRquestType:
                v = request.getHttpPostRequestBody();
                postDataObj = parse(v, defaultValue, context);
                if (autoBeanValidation) {
                    String validationError = BeanUtil.getBeanValidationResult(postDataObj);
                    if (validationError != null) {
                        Err e = new Err<>(BootErrorCode.BAD_REQUEST_INVALID_REQUEST_BODY, null, null, null, "Invalid request body: " + validationError);
                        // 2. build JSON response with same app error code, and keep the default INFO log level.
                        context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                        return null;
                    }
                }
                return postDataObj;
        }
        return null;
    }

    protected Object parse(String value, String defaultValue, SessionContext context) {
        if (StringUtils.isBlank(value)) {
            if (defaultValue != null) {
                value = defaultValue;
            } else {
                if (isRequired) {
                    Err e = new Err<>(BootErrorCode.BAD_REQUEST_MISSING_REQUIRED_FILED, null, null, null, "Missing Required Filed: " + type + "{" + key + "}=" + value);
                    context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                }
                return ReflectionUtil.toStandardJavaType(null, false, targetClass, false, false, null);//primitive types devault value or null
            }
        }
        String regex = pattern == null ? null : pattern.regexp();
        if (regex != null && !value.matches(regex)) {
            Err e = new Err<>(BootErrorCode.BAD_REQUEST_DATA, null, null, null, "Failed to parse data type: invalid " + type + "{" + key + "}=" + value + " by regex=" + regex);
            context.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return ReflectionUtil.toStandardJavaType(null, false, targetClass, false, false, null);//primitive types devault value or null
        }
        try {
            return ReflectionUtil.toJavaType(targetClass, parameterizedType, value, true, false, false, enumConvert, collectionDelimiter);
        } catch (Throwable ex) {
            Err e = new Err<>(BootErrorCode.BAD_REQUEST_DATA, null, null, ex, "Failed to parse data type: invalid " + type + "{" + key + "}=" + value);
            context.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return ReflectionUtil.toStandardJavaType(null, false, targetClass, false, false, null);//primitive types devault value or null
        }
    }
}
