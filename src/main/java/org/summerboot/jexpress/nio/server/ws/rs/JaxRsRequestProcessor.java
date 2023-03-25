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

import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.BeanUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.annotation.security.DeclareRoles;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.nio.server.RequestProcessor;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JaxRsRequestProcessor implements RequestProcessor {

    //basic info
    private final Object javaInstance;
    private final Method javaMethod;
    private final String declaredPath;
    private final Set<String> rolesAllowed;
    private final boolean roleBased;
    private final boolean permitAll;
    private final List<String> consumes;
    private final List<String> produces;
    private final String produce_ExplicitType;
    private final String produce_DefaultType;

    //param info    
    private final List<JaxRsRequestParameter> parameterList;
    private final boolean usingMatrixParam;
    private final boolean usingPathParam;
    private final Map<String, MetaPathParam> pathParamMap;
    private final List<MetaMatrixParam> metaMatrixParamList;
    private final Pattern regexPattern;
    private final int parameterSize;
    public static final List<String> SupportedProducesWithReturnType = Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_PATCH_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN, MediaType.TEXT_HTML);

    public JaxRsRequestProcessor(final Object javaInstance, final Method javaMethod, final HttpMethod httpMethod, final String path, final Set<String> declareRoles) {
        //1. Basic info
        this.javaInstance = javaInstance;
        this.javaMethod = javaMethod;
        Class controllerClass = javaInstance.getClass();
        String info = controllerClass.getName() + "." + javaMethod.getName();
        DeclareRoles drs = (DeclareRoles) controllerClass.getAnnotation(DeclareRoles.class);
        if (drs != null) {
            declareRoles.addAll(Arrays.asList(drs.value()));
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
        Parameter[] params = javaMethod.getParameters();
        List<JaxRsRequestParameter> parameterListTemp = new ArrayList<>();
        List<MetaMatrixParam> metaMatrixParamListTemp = new ArrayList<>();
        if (params != null && params.length > 0) {
            for (Parameter param : params) {
                JaxRsRequestParameter srp = new JaxRsRequestParameter(info, httpMethod, consumes, param);
                parameterListTemp.add(srp);
                if (srp.getType().equals(JaxRsRequestParameter.ParamType.MatrixParam)) {
                    metaMatrixParamListTemp.add(new MetaMatrixParam(srp.getKey()));
                }
            }
        }
        parameterList = List.copyOf(parameterListTemp);
        parameterSize = parameterList.size();
        usingMatrixParam = !metaMatrixParamListTemp.isEmpty();
        metaMatrixParamList = usingMatrixParam ? List.copyOf(metaMatrixParamListTemp) : null;

        //6. Build path regex pattern - Method level preprocess - path parameter
        String pathParamRegex = "(\\/.*)";
        String pathParamRegex_OptionalInURL = "(\\/.*)?";
        String matrixParamRegx = "(;.+=.*)*";
        Map<String, MetaPathParam> pathParamMapTemp = new HashMap<>();
        String[] pathMembers = FormatterUtil.parseURL(path);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pathMembers.length; i++) {
            String pathMember = pathMembers[i];
            if (StringUtils.isBlank(pathMember)) {
                continue;
            }
            if (pathMember.startsWith("{") && pathMember.endsWith("}")) {
                String pathParamName = pathMember.substring(1, pathMember.length() - 1);
                String[] regexPathParamNames = pathParamName.split(":");
                MetaPathParam meta = new MetaPathParam(i, regexPathParamNames.length > 1 ? regexPathParamNames[1] : null);
                pathParamMapTemp.put(regexPathParamNames[0], meta);
                if (i < pathMembers.length - 1) {
                    sb.append(pathParamRegex);
                } else {
                    sb.append(pathParamRegex_OptionalInURL);
                }
            } else if (usingMatrixParam) {
                sb.append("\\/").append(pathMember);
            } else {
                sb.append("/").append(pathMember);
            }
            if (usingMatrixParam) {
                sb.append(matrixParamRegx);
            }
        }
        this.usingPathParam = !pathParamMapTemp.isEmpty();
        this.pathParamMap = usingPathParam ? Map.copyOf(pathParamMapTemp) : null;
        this.declaredPath = (usingPathParam || usingMatrixParam) ? sb.toString() : path;
        this.regexPattern = (usingPathParam || usingMatrixParam) ? Pattern.compile(this.declaredPath) : null;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return javaMethod.getAnnotation(annotationClass);
    }

    @Override
    public String getDeclaredPath() {
        return declaredPath;
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
            return declaredPath.equals(path);
        }
    }

    @Override
    public boolean authorizationCheck(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context, int badRequestErrorCode) throws Throwable {
        //1. authentication is done, now we do authorizationCheck
        if (roleBased) {
            boolean isAuthorized = false;
            Caller caller = context.caller();
            if (caller == null) {
                context.status(HttpResponseStatus.UNAUTHORIZED)
                        .error(new Err(BootErrorCode.AUTH_INVALID_USER, null, "Authentication Required - Unkown caller", null));
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
                        .error(new Err(BootErrorCode.AUTH_NO_PERMISSION, null, "Authorization Failed - Caller is not in role: " + rolesAllowed, null));
                return false;
            }
        }
        return true;
    }

    @Override
    public void process(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context, int badRequestErrorCode) throws Throwable {
        //2. invoke
        Object ret;
        if (parameterSize > 0) {
            ServiceRequest request = buildServiceRequest(channelHandlerCtx, httpHeaders, httpRequestPath, queryParams, httpPostRequestBody);
            Object[] paramValues = new Object[parameterSize];
            for (int i = 0; i < parameterSize; i++) {
                paramValues[i] = parameterList.get(i).value(badRequestErrorCode, request, context);
            }
            if (context.error() != null) {
                return;
            }
            try {
                context.timestampPOI(BootPOI.BIZ_BEGIN);
                ret = javaMethod.invoke(javaInstance, paramValues);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            } finally {
                context.timestampPOI(BootPOI.BIZ_END);
            }
        } else {
            try {
                context.timestampPOI(BootPOI.BIZ_BEGIN);
                ret = javaMethod.invoke(javaInstance);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            } finally {
                context.timestampPOI(BootPOI.BIZ_END);
            }
        }
        //3. process return object
        if (ret != null) {
            if (ret instanceof File) {
                context.file((File) ret);
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
                    if (responseContentType == null) {// finally client not match
                        responseContentType = MediaType.APPLICATION_JSON;
                    }
                }
                //2. set content and contentType
                if (ret instanceof String) {
                    context.txt((String) ret);
                } else {
                    switch (responseContentType) {
                        case MediaType.APPLICATION_JSON:
                            context.txt(BeanUtil.toJson(ret));
                            break;
                        case MediaType.APPLICATION_XML:
                        case MediaType.TEXT_XML:
                            context.txt(BeanUtil.toXML(ret));
                            break;
                        case MediaType.TEXT_HTML:
                        case MediaType.TEXT_PLAIN:
                            context.txt(ret.toString());
                            break;
                    }
                }
                //3. update content type
                if (context.contentType() == null) {
                    context.contentType(responseContentType);
                }
            }
        }
    }

    public boolean isUsingMatrixPara() {
        return usingMatrixParam;
    }

    public boolean isUsingPathParam() {
        return usingPathParam;
    }

    public ServiceRequest buildServiceRequest(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody) {
        ServiceRequest req = new ServiceRequest(channelHandlerCtx, httpHeaders, httpRequestPath, queryParams, httpPostRequestBody);
        if (usingPathParam) {
            String[] pathList = FormatterUtil.parseURL(httpRequestPath);
            pathParamMap.keySet().forEach(pathParamName -> {
                MetaPathParam meta = pathParamMap.get(pathParamName);
                int i = meta.getParamOrderIndex();
                if (i >= 0 && i < pathList.length) {
                    String value = pathList[i];
                    int k = value.indexOf(";");
                    if (k > 0) {
                        value = value.substring(0, k);
                    }
                    if (meta.matches(value)) {
                        req.addPathParam(pathParamName, value);
                    }
                }
            });

        }
        if (usingMatrixParam) {
            metaMatrixParamList.forEach(matrixParamMeta -> {
                String key = matrixParamMeta.getKey();
                String value = matrixParamMeta.value(httpRequestPath);
                req.addMatrixParam(key, value);
            });
        }
        return req;
    }
}
