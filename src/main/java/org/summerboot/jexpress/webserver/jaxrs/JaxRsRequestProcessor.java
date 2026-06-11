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
package org.summerboot.jexpress.webserver.jaxrs;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.annotation.Controller;
import org.summerboot.jexpress.annotation.rest.Daemon;
import org.summerboot.jexpress.annotation.rest.Log;
import org.summerboot.jexpress.annotation.rest.ParamCollectionDelimiter;
import org.summerboot.jexpress.annotation.rest.RequiresHealthCheck;
import org.summerboot.jexpress.api.auth.Caller;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.BootPoi;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ProcessorSettings;
import org.summerboot.jexpress.api.common.RequestProcessor;
import org.summerboot.jexpress.api.common.ServiceRequest;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.integration.HealthMonitor;
import org.summerboot.jexpress.util.format.FormatterUtil;
import org.summerboot.jexpress.util.lang.BeanUtil;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JaxRsRequestProcessor implements RequestProcessor {

    //basic info
    protected final Object javaInstance;
    protected final Method javaMethod;
    protected final String declaredUri;
    protected final String processedDeclaredUri;
    protected final Set<String> rolesAllowed;
    protected final boolean roleBased;
    protected final boolean permitAll;
    protected final List<String> consumes;
    protected final List<String> produces;
    protected final String produce_ExplicitType;
    protected final String produce_DefaultType;
    protected final Log classLevelLogAnnotation;
    protected final boolean rejectWhenPaused;
    //protected final boolean rejectWhenHealthCheckFailed;
    protected final Set<String> requiredHealthChecks;
    protected final HealthMonitor.EmptyHealthCheckPolicy emptyHealthCheckPolicy;

    //param info    
    protected final List<JaxRsRequestParameter> parameterList;
    protected final boolean hasMatrixParam;
    protected final boolean hasPathParam;
    protected final Map<String, MetaPathParam> pathParamMap;
    protected final List<MetaMatrixParam> metaMatrixParamList;
    protected final Pattern regexPattern;
    protected final int parameterSize;
    public static final List<String> SupportedProducesWithReturnType = Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_PATCH_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN, MediaType.TEXT_HTML);
    protected final Boolean pretty;

    //logging info
    protected final ProcessorSettings processorSettings;
//    protected final boolean logRequestHeader;
//    protected final boolean logRequestBody;
//    protected final boolean logResponseHeader;
//    protected final boolean logResponseBody;

    public JaxRsRequestProcessor(final Object javaInstance, final Method javaMethod, final HttpMethod httpMethod, final String declaredUri, final Set<String> declareRoles) {
        //1. Basic info
        this.javaInstance = javaInstance;
        this.javaMethod = javaMethod;
        this.declaredUri = declaredUri;
        Class controllerClass = javaInstance.getClass();
        String info = controllerClass.getName() + "." + javaMethod.getName();
        DeclareRoles drs = (DeclareRoles) controllerClass.getAnnotation(DeclareRoles.class);
        if (drs != null) {
            declareRoles.addAll(Arrays.asList(drs.value()));
        }
        Log classLeveLog = (Log) controllerClass.getAnnotation(Log.class);
        Log methodLevelLog = javaMethod.getAnnotation(Log.class);
        if (methodLevelLog != null) {
            pretty = methodLevelLog.pretty().value();
        } else if (classLeveLog != null) {
            pretty = classLeveLog.pretty().value();
        } else {
            pretty = null;
        }


        // Reject ASAP: pause
        Daemon classLevelDaemon = (Daemon) controllerClass.getAnnotation(Daemon.class);
        Daemon methodLevelDaemon = javaMethod.getAnnotation(Daemon.class);
        if (methodLevelDaemon != null) {
            rejectWhenPaused = !methodLevelDaemon.value();
        } else if (classLevelDaemon != null) {
            rejectWhenPaused = !classLevelDaemon.value();
        } else {
            rejectWhenPaused = true;
        }
        // Reject ASAP: HealthCheck
        RequiresHealthCheck classLevelRequiresHealthCheck = (RequiresHealthCheck) controllerClass.getAnnotation(RequiresHealthCheck.class);
        RequiresHealthCheck methodLevelRequiresHealthCheck = javaMethod.getAnnotation(RequiresHealthCheck.class);
        if (methodLevelRequiresHealthCheck != null) {
            requiredHealthChecks = toImmutableSet(info, methodLevelRequiresHealthCheck.value());
            emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_ALL;
        } else if (classLevelRequiresHealthCheck != null) {
            requiredHealthChecks = toImmutableSet(info, classLevelRequiresHealthCheck.value());
            emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_ALL;
        } else {
            requiredHealthChecks = null;
            emptyHealthCheckPolicy = HealthMonitor.EmptyHealthCheckPolicy.REQUIRE_NONE;
        }

        //2. Parse @RolesAllowed, @PermitAll and @DenyAll - Method level preprocess - Authoritarian - Role based 
        RolesAllowed rolesAllowedAnnotation = javaMethod.getAnnotation(RolesAllowed.class);
        PermitAll permitAllAnnotation = javaMethod.getAnnotation(PermitAll.class);
        DenyAll denyAllAnnotation = javaMethod.getAnnotation(DenyAll.class);
        if (permitAllAnnotation != null && denyAllAnnotation != null || permitAllAnnotation != null && rolesAllowedAnnotation != null || denyAllAnnotation != null && rolesAllowedAnnotation != null) {
            throw new UnsupportedOperationException("Only one security role is allowed: either @RolesAllowed, @PermitAll or @DenyAll @ " + info);
        }
        if (permitAllAnnotation != null) {
            roleBased = true;
            rolesAllowed = declareRoles;
            permitAll = true;
        } else if (denyAllAnnotation != null) {
            roleBased = true;
            rolesAllowed = Set.of();
            permitAll = false;
        } else {
            permitAll = false;
            if (rolesAllowedAnnotation == null) {
                rolesAllowedAnnotation = (RolesAllowed) controllerClass.getAnnotation(RolesAllowed.class);
            }
            if (rolesAllowedAnnotation != null) {
                roleBased = true;
                rolesAllowed = Set.of(rolesAllowedAnnotation.value());
                declareRoles.addAll(rolesAllowed);
//                if(!declareRoles.containsAll(rolesAllowed)) {
//                    throw new UnsupportedOperationException("@RolesAllowed value is not defined in @DeclareRoles @ " + info);
//                }
            } else {
                roleBased = false;
                rolesAllowed = null;
            }
        }

        //3. Parse @Consumes - The @Consumes annotation is used to specify the MIME media types of representations a resource can consume 
        //that were sent by the client.
        Consumes ac = javaMethod.getAnnotation(Consumes.class);
        if (ac == null) {
            ac = (Consumes) controllerClass.getAnnotation(Consumes.class);
        }
        List<String> temp = new ArrayList<>();
        if (ac != null) {
            String[] sa = ac.value();
            if (sa != null && sa.length > 0) {
                for (String s : sa) {
                    if (StringUtils.isNotBlank(s)) {
                        temp.add(s.trim());
                    }
                }
            }
        }
        if (temp.isEmpty()) {
            consumes = null;
        } else {
            consumes = List.copyOf(temp);
            temp.clear();
        }

        //4. Parse @Produces - The @Produces annotation is used to specify the MIME media types of representations a resource can produce 
        //and send back to the client: for example, "text/plain".
        Produces ap = javaMethod.getAnnotation(Produces.class);
        if (ap == null) {
            ap = (Produces) controllerClass.getAnnotation(Produces.class);
        }
        if (ap != null) {
            String[] sa = ap.value();
            if (sa != null && sa.length > 0) {
                for (String s : sa) {
                    if (StringUtils.isNotBlank(s)) {
                        temp.add(s.trim());
                    }
                }
            }
        }
        if (temp.isEmpty()) {
            produces = null;
            produce_ExplicitType = null;
            produce_DefaultType = null;
        } else {
            Class retType = javaMethod.getReturnType();
            if (retType != null && !retType.equals(String.class) && !retType.equals(File.class)) {
                List<String> filter = new ArrayList<>(temp);
                filter.removeAll(SupportedProducesWithReturnType);
                if (!filter.isEmpty()) {
                    throw new UnsupportedOperationException("\n\t@Produces(" + filter + ") is not supported with return type(" + retType + ") @ " + info + ", supported @Produces values with return type are: " + SupportedProducesWithReturnType);
                }
            }

            produces = List.copyOf(temp);
            produce_DefaultType = produces.get(0);
            if (produces.size() == 1) {
                produce_ExplicitType = produce_DefaultType;
            } else {
                produce_ExplicitType = null;
            }
            temp.clear();
        }

        //5. Parse Parameters
        final String collectionDelimiter = getCollectionDelimiter(javaMethod, controllerClass);
        Parameter[] params = javaMethod.getParameters();
        List<JaxRsRequestParameter> parameterListTemp = new ArrayList<>();
        List<MetaMatrixParam> metaMatrixParamListTemp = new ArrayList<>();
        if (params != null && params.length > 0) {
            for (Parameter param : params) {
                JaxRsRequestParameter srp = new JaxRsRequestParameter(info, httpMethod, consumes, param, collectionDelimiter);
                parameterListTemp.add(srp);
                if (srp.getType().equals(JaxRsRequestParameter.ParamType.MatrixParam)) {
                    metaMatrixParamListTemp.add(new MetaMatrixParam(srp.getKey()));
                }
            }
        }
        parameterList = List.copyOf(parameterListTemp);
        parameterSize = parameterList.size();
        hasMatrixParam = !metaMatrixParamListTemp.isEmpty();
        metaMatrixParamList = hasMatrixParam ? List.copyOf(metaMatrixParamListTemp) : null;

        //6. Build path regex pattern - Method level preprocess - path parameter
        String pathParamRegex = "(\\/.*)";
        String pathParamRegex_OptionalInURL = "(\\/.*)?";
        String matrixParamRegx = "(;.+=.*)*";
        Map<String, MetaPathParam> pathParamMapTemp = new HashMap<>();
        String[] pathMembers = FormatterUtil.parseURL(declaredUri);
        StringBuilder sb = new StringBuilder();
        int size = pathMembers.length;
        int last = size - 1;
        for (int i = 0; i < size; i++) {
            String pathMember = pathMembers[i];
            if (StringUtils.isBlank(pathMember)) {
                continue;
            }
            if (pathMember.startsWith("{") && pathMember.endsWith("}")) {
                boolean isLast = i == last;
                String pathParamName = pathMember.substring(1, pathMember.length() - 1);
                String[] regexPathParamNames = pathParamName.split(":");
                MetaPathParam meta = new MetaPathParam(i, regexPathParamNames.length > 1 ? regexPathParamNames[1] : null, isLast);
                pathParamMapTemp.put(regexPathParamNames[0], meta);
                if (i < pathMembers.length - 1) {
                    sb.append(pathParamRegex);
                } else {
                    sb.append(pathParamRegex_OptionalInURL);
                }
            } else if (hasMatrixParam) {
                sb.append("\\/").append(pathMember);
            } else {
                sb.append("/").append(pathMember);
            }
            if (hasMatrixParam) {
                sb.append(matrixParamRegx);
            }
        }
        this.hasPathParam = !pathParamMapTemp.isEmpty();
        this.pathParamMap = hasPathParam ? Map.copyOf(pathParamMapTemp) : null;
        this.processedDeclaredUri = (hasPathParam || hasMatrixParam) ? sb.toString() : declaredUri;
        this.regexPattern = (hasPathParam || hasMatrixParam) ? Pattern.compile(this.processedDeclaredUri) : null;

        //logging info
        classLevelLogAnnotation = (Log) controllerClass.getAnnotation(Log.class);
//        Class ctrlClass = controllerClass;
//        while (classLevelLogAnnotation == null && ctrlClass.getSuperclass() != null) {
//            ctrlClass = ctrlClass.getSuperclass();
//            classLevelLogAnnotation = (Log) ctrlClass.getAnnotation(Log.class);
//        }

        processorSettings = new ProcessorSettings();
        updateLogSettings(classLevelLogAnnotation);//init with class level settings
        Log methodLevelLogAnnotation = javaMethod.getAnnotation(Log.class);
        updateLogSettings(methodLevelLogAnnotation);//override root settings with method level settings
        if (processorSettings.getLogSettings() != null) {
            processorSettings.getLogSettings().removeDuplicates();
        }
        Controller controllerAnnotation = (Controller) controllerClass.getAnnotation(Controller.class);
        if (controllerAnnotation != null) {
            String responseHeaderRefName = controllerAnnotation.responseHeader_Reference();
            if (StringUtils.isBlank(responseHeaderRefName)) {
                responseHeaderRefName = BootConstants.RESPONSE_HEADER_KEY_REF;
            }
            processorSettings.setHttpServiceResponseHeaderName_Reference(responseHeaderRefName);

            String responseHeaderServerTsName = controllerAnnotation.responseHeader_ServerTs();
            if (StringUtils.isBlank(responseHeaderServerTsName)) {
                responseHeaderServerTsName = BootConstants.RESPONSE_HEADER_KEY_TS;
            }
            processorSettings.setHttpServiceResponseHeaderName_ServerTimestamp(responseHeaderServerTsName);
        }
    }

    protected static Set<String> toImmutableSet(String info, String[] array) {
        if (array == null) {
            return null;
        }
        if (array.length < 1) {
            return Set.of();
        }
        try {
            return Set.of(array);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(info + " @RequiresHealthCheck " + Arrays.toString(array) + " has " + ex.getMessage());
        }
    }

    private static String getCollectionDelimiter(Method javaMethod, Class controllerClass) {
        final String collectionDelimiter;
        ParamCollectionDelimiter methodLevelCollectionDelimiter = javaMethod.getAnnotation(ParamCollectionDelimiter.class);
        if (methodLevelCollectionDelimiter != null) {
            collectionDelimiter = methodLevelCollectionDelimiter.value();
        } else {
            ParamCollectionDelimiter classLeveCollectionDelimiter = (ParamCollectionDelimiter) controllerClass.getAnnotation(ParamCollectionDelimiter.class);
            if (classLeveCollectionDelimiter != null) {
                collectionDelimiter = classLeveCollectionDelimiter.value();
            } else {
                collectionDelimiter = ",";
            }
        }
        return collectionDelimiter;
    }

    protected void updateLogSettings(Log log) {
        if (log == null) {
            return;
        }
        ProcessorSettings.LogSettings logSettings = processorSettings.getLogSettings(true);
//        if (logSettings == null) {
//            logSettings = processorSettings.new LogSettings();
//            processorSettings.setLogSettings(logSettings);
//        }
        logSettings.setLogRequestHeader(log.requestHeader());
        logSettings.setLogRequestBody(log.requestBody());
        logSettings.setLogResponseHeader(log.responseHeader());
        logSettings.setLogResponseBody(log.responseBody());


        String[] protectDataFieldsFromLogging = log.maskDataFields();
        if (protectDataFieldsFromLogging != null && protectDataFieldsFromLogging.length > 0) {
            List<String> list = logSettings.getProtectDataFieldsFromLogging();
            if (list == null) {
                list = new ArrayList<>();
                logSettings.setProtectDataFieldsFromLogging(list);
            }
            list.addAll(Arrays.asList(protectDataFieldsFromLogging));
        }
    }

    @Override
    public ProcessorSettings getProcessorSettings() {
        return processorSettings;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return javaMethod.getAnnotation(annotationClass);
    }

    @Override
    public String getDeclaredUri() {
        return declaredUri;
    }

    @Override
    public String getProcessedDeclaredUri() {
        return processedDeclaredUri;
    }

    @Override
    public HealthMonitor.EmptyHealthCheckPolicy getEmptyHealthCheckPolicy() {
        return emptyHealthCheckPolicy;
    }

    @Override
    public Set<String> getRequiredHealthChecks() {
        return requiredHealthChecks;
    }

    @Override
    public boolean isRoleBased() {
        return roleBased;
    }

    @Override
    public boolean matches(String path) {
        if (regexPattern != null) {
            return regexPattern.matcher(path).matches();
        } else {
            return processedDeclaredUri.equals(path);
        }
    }

    @Override
    public boolean authorizationCheck(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context, int badRequestErrorCode) throws Throwable {
        //1. authentication is done, now we do authorizationCheck
        if (roleBased) {
            boolean isAuthorized = false;
            Caller caller = context.caller();
            if (caller == null) {
                context.error(Err.UNAUTHORIZED_401).status(HttpResponseStatus.UNAUTHORIZED);
                return false;
            }

            for (String role : rolesAllowed) {
                if (caller.isInRole(role)) {
                    isAuthorized = true;
                    break;
                }
            }

            if (!isAuthorized) {
                context.status(HttpResponseStatus.FORBIDDEN)
                        .error(new Err(BootErrorCode.AUTH_NO_PERMISSION, null, "Authorization Failed - Caller is not in role", null, "Authorization Failed - Caller is not in role: " + rolesAllowed));
                return false;
            }
        }
        return true;
    }

    @Override
    public Object process(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context) throws Throwable {
        //2. invoke
        Object ret;
        Object[] paramValues = new Object[parameterSize];
        if (parameterSize > 0) {
            ServiceRequest request = buildServiceRequest(channelHandlerCtx, httpHeaders, httpRequestPath, queryParams, httpPostRequestBody, context);
            for (int i = 0; i < parameterSize; i++) {
                paramValues[i] = parameterList.get(i).value(request, context);
            }
            if (context.error() != null) {
                return null;
            }
        }
        try {
            context.poi(BootPoi.BIZ_BEGIN);
            Set<String> failedHealthChecks = new HashSet<>();
            //if (!HealthMonitor.isHealthCheckSuccess() && (rejectWhenHealthCheckFailed || HealthMonitor.isRequiredHealthChecksFailed(requiredHealthChecks, failedHealthChecks))) {
            if (!HealthMonitor.isHealthCheckSuccess() && HealthMonitor.isRequiredHealthChecksFailed(requiredHealthChecks, emptyHealthCheckPolicy, failedHealthChecks)) {
                final String internalError = failedHealthChecks.toString();
                context.status(HttpResponseStatus.BAD_GATEWAY)
                        .error(new Err(BootErrorCode.SERVICE_HEALTH_CHECK_FAILED, null, "Service health check failed by HealthMonitor", null,
                                "Service health check failed by HealthMonitor: " + internalError));
                return null;
            }
            if (rejectWhenPaused && HealthMonitor.isServicePaused()) {
                context.status(HttpResponseStatus.SERVICE_UNAVAILABLE)
                        .error(new Err(BootErrorCode.SERVICE_PAUSED, null, "Service is temporarily paused by HealthMonitor", null, "Service is temporarily paused by HealthMonitor: " + HealthMonitor.getStatusReasonPaused()));
                return null;
            }

            ret = javaMethod.invoke(javaInstance, paramValues);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        } finally {
            context.poi(BootPoi.BIZ_END);
        }

        //3. process return object
        if (ret != null) {
            if (ret instanceof File) {
                context.response((File) ret);
            } else if (ret instanceof Path) {
                context.response((Path) ret);
            } else if (ret instanceof byte[]) {
                context.data((byte[]) ret);
            } else {
                //1. calculate responseContentType
                String responseContentType = produce_ExplicitType;
                if (responseContentType == null) {// server undefined or decide by client Accept header
                    String clientAcceptedContentType = context.clientAcceptContentType();
                    if (clientAcceptedContentType == null) {// decide by server side
                        responseContentType = produce_DefaultType;
                    } else {//client to match from server defined list         
                        clientAcceptedContentType = clientAcceptedContentType.toLowerCase();
                        if (produces != null) {
                            for (String produce : produces) {
                                if (clientAcceptedContentType.contains(produce)) {
                                    responseContentType = produce;
                                    break;
                                }
                            }
                            if (responseContentType == null) {// client not match
                                responseContentType = produce_DefaultType;
                            }
                        } else {
                            if (clientAcceptedContentType.contains("json")) {
                                responseContentType = MediaType.APPLICATION_JSON;
                            } else if (clientAcceptedContentType.contains("xml")) {
                                responseContentType = MediaType.APPLICATION_XML;
                            } else if (clientAcceptedContentType.contains("txt")) {
                                responseContentType = MediaType.TEXT_HTML;
                            } else {
                                responseContentType = MediaType.APPLICATION_JSON;
                            }
                        }
                    }
                }
                if (responseContentType == null) {// finally client not match
                    responseContentType = MediaType.APPLICATION_JSON;
                }
                //2. set content and contentType
                if (ret instanceof String) {
                    context.response((String) ret);
                } else {
                    switch (responseContentType) {
                        case MediaType.APPLICATION_JSON -> {
                            Boolean isPretty = isPretty(context.pretty(), pretty);
                            if (isPretty != null) {
                                context.response(BeanUtil.toJson(ret, isPretty));
                            } else {
                                context.response(BeanUtil.toJson(ret));
                            }
                        }
                        case MediaType.APPLICATION_XML, MediaType.TEXT_XML -> {
                            Boolean isPretty = isPretty(context.pretty(), pretty);
                            if (isPretty != null) {
                                context.response(BeanUtil.toXML(ret, isPretty));
                            } else {
                                context.response(BeanUtil.toXML(ret));
                            }
                        }
                        case MediaType.TEXT_HTML, MediaType.TEXT_PLAIN -> {
                            context.response(ret.toString());
                        }
                    }
                }
                //3. update content type
                if (context.contentType() == null) {
                    context.contentType(responseContentType);
                }
            }
        }
        return ret;
    }

    public boolean hasMatrixPara() {
        return hasMatrixParam;
    }

    public boolean hasPathParam() {
        return hasPathParam;
    }

    public ServiceRequest buildServiceRequest(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context) {
        ServiceRequest req = new ServiceRequest(channelHandlerCtx, httpHeaders, httpRequestPath, queryParams, httpPostRequestBody);
        if (hasPathParam) {
            String[] pathList = FormatterUtil.parseURL(httpRequestPath);
            int size = pathList.length;
            pathParamMap.keySet().forEach(pathParamName -> {
                MetaPathParam meta = pathParamMap.get(pathParamName);
                int i = meta.getParamOrderIndex();
                if (i >= 0 && i < size) {
                    String value = pathList[i];
//                    if (meta.isIsLast()) {
//                        StringBuilder sb = new StringBuilder();
//                        for (int k = i; k < size; k++) {
//                            sb.append(pathList[k]);
//                            if (k < (size - 1)) {
//                                sb.append("/");
//                            }
//                        }
//                        value = sb.toString();
//                    } else {
//                        value = pathList[i];
//                    }                    
                    if (hasMatrixParam) {
                        int k = value.indexOf(";");
                        int e = value.indexOf("=");
                        if (k > 0 && e > k) {
                            value = value.substring(0, k);
                        }
                    }
                    if (meta.matches(value)) {
                        req.addPathParam(pathParamName, value);
                    } else {
                        String pattern = meta.pathParamMetaPattern.pattern();
                        Err e = new Err(BootErrorCode.BAD_REQUEST_DATA, null,
                                "Value (" + value + ") does not match parameter (" + pathParamName + ")'s pattern (" + pattern + ") in declared URL: " + declaredUri, null);
                        context.status(HttpResponseStatus.BAD_REQUEST).error(e);
                    }
                }
            });
        }
        if (hasMatrixParam) {
            metaMatrixParamList.forEach(matrixParamMeta -> {
                String key = matrixParamMeta.getKey();
                String value = matrixParamMeta.value(httpRequestPath);
                req.addMatrixParam(key, value);
            });
        }
        return req;
    }

    /**
     *
     * @param pretty1
     * @param pretty2
     * @return pretty1 value override pretty2 value
     */
    private static Boolean isPretty(Boolean pretty1, Boolean pretty2) {
        if (pretty1 != null) {
            return pretty1;
        }
        return pretty2;
    }
}
