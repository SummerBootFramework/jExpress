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

import org.summerboot.jexpress.boot.annotation.Controller;
import io.netty.handler.codec.http.HttpMethod;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import org.summerboot.jexpress.boot.annotation.Ping;
import org.summerboot.jexpress.nio.server.NioServerContext;
import org.summerboot.jexpress.security.auth.AuthConfig;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JaxRsRequestProcessorManager {

    public static void registerControllers(@Controller Map<String, Object> controllers, StringBuilder memo) {
        if (controllers == null || controllers.isEmpty()) {
            return;
        }
        final Set<String> declareRoles = new HashSet();
        final AuthConfig authCfg = AuthConfig.instance(AuthConfig.class);
        Map<HttpMethod, Map<String, RequestProcessor>> stringMap = new HashMap<>();
        Map<HttpMethod, Map<String, RequestProcessor>> regexMap = new HashMap<>();
        Set<String> errors = new HashSet<>();
        String loadBalancingEndpoint = null;
        int pingCount = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Conflict of @Ping annotaion, should be only one @Ping in a @Controller class @GET method, but found multiple:");
        for (String name : controllers.keySet()) {
            Object javaInstance = controllers.get(name);
            Class controllerClass = javaInstance.getClass();
//            Annotation[] as = controllerClass.getAnnotations();
//            Annotation[] das = controllerClass.getDeclaredAnnotations();
            String rootPath = null;
            Path rp = (Path) controllerClass.getAnnotation(Path.class);
            if (rp != null) {
                rootPath = rp.value().trim();
            }
            //for each HTTPMethod-JavaMethod generate a processor
            Method[] methods = controllerClass.getDeclaredMethods();
            for (Method javaMethod : methods) {
                //@Path
                Path ap = javaMethod.getAnnotation(Path.class);
                if (ap == null) {
                    continue;
                }
                final String path;
                if (StringUtils.isBlank(rootPath)) {
                    path = ap.value().trim();
                } else {
                    path = rootPath + ap.value().trim();
                }
                if (path == null) {
                    continue;
                }
                //@HttpMethods
                final Set<HttpMethod> httpMethods = new HashSet<>();
                GET amg = javaMethod.getAnnotation(GET.class);
                if (amg != null) {
                    httpMethods.add(HttpMethod.GET);
                    Ping p = javaMethod.getAnnotation(Ping.class);
                    if (p != null) {
                        pingCount++;
                        loadBalancingEndpoint = path;
                        sb.append("\n\t").append(controllerClass.getName()).append(".").append(javaMethod.getName()).append("()");
                        memo.append("\n\t- ").append("* GET").append(" ").append(path).append(" (").append(javaInstance).append(".").append(javaMethod.getName()).append(" )");
                        continue;
                    }
                }
                POST amp = javaMethod.getAnnotation(POST.class);
                if (amp != null) {
                    httpMethods.add(HttpMethod.POST);
                }
                PUT ampt = javaMethod.getAnnotation(PUT.class);
                if (ampt != null) {
                    httpMethods.add(HttpMethod.PUT);
                }
                DELETE amd = javaMethod.getAnnotation(DELETE.class);
                if (amd != null) {
                    httpMethods.add(HttpMethod.DELETE);
                }
                OPTIONS amo = javaMethod.getAnnotation(OPTIONS.class);
                if (amo != null) {
                    httpMethods.add(HttpMethod.OPTIONS);
                }
                PATCH ampc = javaMethod.getAnnotation(PATCH.class);
                if (ampc != null) {
                    httpMethods.add(HttpMethod.PATCH);
                }
                HEAD amh = javaMethod.getAnnotation(HEAD.class);
                if (amh != null) {
                    httpMethods.add(HttpMethod.HEAD);
                }
                if (httpMethods.isEmpty()) {
                    continue;
                }
                for (HttpMethod httpMethod : httpMethods) {
                    JaxRsRequestProcessor processor;
                    try {
                        processor = new JaxRsRequestProcessor(javaInstance, javaMethod, httpMethod, path, declareRoles);
                        memo.append("\n\t- ").append(httpMethod).append(" ").append(path).append(" (").append(javaInstance).append(".").append(javaMethod.getName()).append(" )");
                    } catch (Throwable ex) {
                        errors.add("failed to create processor for " + controllerClass.getName() + "." + javaMethod.getName() + "\n\t" + ex);
                        continue;
                    }
                    Map<HttpMethod, Map<String, RequestProcessor>> rootMap;
                    Map<String, RequestProcessor> subMap;
                    if (processor.isUsingPathParam() || processor.isUsingMatrixPara()) {
                        rootMap = regexMap;
                    } else {
                        rootMap = stringMap;
                    }
                    subMap = rootMap.get(httpMethod);
                    if (subMap == null) {
                        subMap = new HashMap<>();
                        rootMap.put(httpMethod, subMap);
                    }
                    String key = processor.getDeclaredPath();
                    if (subMap.containsKey(key)) {
                        errors.add("request already exists: " + httpMethod + " '" + path + "' in " + controllerClass.getName() + "." + javaMethod.getName());
                        continue;
                    }
                    subMap.put(key, processor);
                }
            }
        }
        if (pingCount > 1) {
            errors.add(sb.toString());
        }
        if (!errors.isEmpty()) {
            System.err.println("Invalid Java methods: \n" + errors);
            System.exit(1);
        }
        if (loadBalancingEndpoint != null) {
            NioServerContext.setLoadBalancingEndpoint(loadBalancingEndpoint);
        }
        authCfg.addDeclareRoles(declareRoles);
        memo.append("\n\t- * LoadBalancingEndpoint=").append(loadBalancingEndpoint);
        memo.append("\n\t- * DeclareRoles=").append(declareRoles);
        processorMapString = stringMap;
        processorMapRegex = regexMap;
    }

    private static Map<HttpMethod, Map<String, RequestProcessor>> processorMapString;
    private static Map<HttpMethod, Map<String, RequestProcessor>> processorMapRegex;

    public static RequestProcessor getRequestProcessor(final HttpMethod httptMethod, final String httpRequestPath) {
        if (processorMapString == null) {
            return null;
        }
        RequestProcessor processor = null;
        Map<String, RequestProcessor> subMap = processorMapString.get(httptMethod);
        if (subMap != null) {
            processor = subMap.get(httpRequestPath);
        }
        if (processor == null) {
            subMap = processorMapRegex.get(httptMethod);
            if (subMap != null) {
                // find action with URI path templates like @Path("/tenant/{tenantNAme}/user/{username}")
                for (RequestProcessor p : subMap.values()) {
                    if (p.matches(httpRequestPath)) {
                        processor = p;
                        break;
                    }
                }
            }
        }
        return processor;
    }

    //@SuppressWarnings("unchecked")
    private static <T> T create(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), clazz.getInterfaces(), new InvocationHandler() {
            @Override

            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                boolean flag = method.isAnnotationPresent(GET.class);
                return null;
            }
        });
    }
}
