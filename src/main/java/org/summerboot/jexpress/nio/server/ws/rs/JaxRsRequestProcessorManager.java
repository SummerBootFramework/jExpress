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

import io.netty.handler.codec.http.HttpMethod;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Ping;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.util.ReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JaxRsRequestProcessorManager {

    private static class ProcessorMeta {

        final String key;
        final String url;
        final Class cThis;
        final Class cSuper;
        final Method m;
        final Object instance;
        final String info;

        public ProcessorMeta(String key, String url, Method m, Object instance) {
            this.key = key;
            this.url = url;
            this.cThis = instance.getClass();
            this.cSuper = m.getDeclaringClass();
            this.m = m;
            this.instance = instance;
            if (cThis.equals(cSuper)) {
                this.info = cThis.getName() + "." + m.getName() + "()";
            } else {
                this.info = cThis.getName() + " extneds " + cSuper.getName() + "." + m.getName() + "()";
            }
        }

    }

    private static final Map<String, List<ProcessorMeta>> registeredProcessors = new HashMap();

    private static void registerProcessor(String key, String path, Method method, Object instance) {
        List<ProcessorMeta> processors = registeredProcessors.get(key);
        if (processors == null) {
            processors = new ArrayList();
            registeredProcessors.put(key, processors);
        }
        processors.add(new ProcessorMeta(key, path, method, instance));
    }

    public static final String KEY_PING = Ping.class.getName();

    private static void checkDuplicated(StringBuilder errors, StringBuilder memo) {
        List<ProcessorMeta> pingProcessors = registeredProcessors.remove(KEY_PING);
        if (pingProcessors != null) {
            for (ProcessorMeta pingProcessor : pingProcessors) {
                BackOffice.agent.addLoadBalancingPingEndpoint(pingProcessor.url.toString());
                memo.append("\n\t- ").append("* GET").append(" ").append(pingProcessor.url).append(" (").append(pingProcessor.info).append(" )");
            }
            /*
            //only allow either one default @Ping without overridden, or one overridden @Ping
            ProcessorMeta targetPingProcessor = null;
            int pintImplCount = pingProcessors.size();
            if (pintImplCount == 1) {//it is ok if either one default @Ping or one overridden @Ping
                targetPingProcessor = pingProcessors.get(0);
            } else if (pintImplCount > 1) {
                //List<ProcessorMeta> defaultPing = new ArrayList();
                List<ProcessorMeta> overridenPing = new ArrayList();
                for (ProcessorMeta pingProcessor : pingProcessors) {
                    if (pingProcessor.m.getDeclaringClass().equals(PingController.class)) {
                        //defaultPing.add(pingProcessor);
                        targetPingProcessor = pingProcessor;
                    } else {
                        overridenPing.add(pingProcessor);
                    }
                }
                if (overridenPing.size() == 1) {// it is only ok with one overridden @Ping if more than one @Ping
                    targetPingProcessor = overridenPing.get(0);
                } else {
                    errors.append("\n\n! Only one URI in @Controller classes is allowed to be annotated with both @GET and @").append(Ping.class.getSimpleName()).append(", found duplicated:");
                    for (ProcessorMeta p : overridenPing) {
                        errors.append("\n\t").append("@ ").append(p.info);
                    }
                }
            }
            if (targetPingProcessor != null) {
                memo.append("\n\t- ").append("* GET").append(" ").append(targetPingProcessor.url).append(" (").append(targetPingProcessor.info).append(" )");
            }*/
        }

        for (String key : registeredProcessors.keySet()) {
            List<ProcessorMeta> processors = registeredProcessors.get(key);
            if (processors == null || processors.size() < 2) {
                continue;
            }
            errors.append("\n\n! Duplicated URI ").append(key);
            for (ProcessorMeta p : processors) {
                errors.append("\n\t").append("@ ").append(p.info);
            }
        }
        //return pingURL;
    }

    public static void registerControllers(@Controller Map<String, Object> controllers, StringBuilder memo) {
        if (controllers == null || controllers.isEmpty()) {
            return;
        }
        registeredProcessors.clear();
        final Set<String> declareRoles = new HashSet();
        Map<HttpMethod, Map<String, RequestProcessor>> stringMap = new HashMap<>();
        Map<HttpMethod, Map<String, RequestProcessor>> regexMap = new HashMap<>();
        StringBuilder errors = new StringBuilder();
        //int pingCount = 0;
        //StringBuilder sb = new StringBuilder();
        //sb.append("Conflict of @Ping annotaion, should be only one @Ping in a @Controller class @GET method, but found multiple:");
        for (String name : controllers.keySet()) {
            Object javaInstance = controllers.get(name);
            Class controllerClass = javaInstance.getClass();
//            Annotation[] as = controllerClass.getAnnotations();
//            Annotation[] das = controllerClass.getDeclaredAnnotations();
            String rootPath = null;
            Path pathRoot = (Path) controllerClass.getAnnotation(Path.class);
            if (pathRoot != null) {
                rootPath = pathRoot.value().trim();
            }
            //for each HTTPMethod-JavaMethod generate a processor
            //Method[] methods = controllerClass.getDeclaredMethods();
            List<Method> methods = ReflectionUtil.getDeclaredAndSuperClassesMethods(controllerClass, true);
            for (Method javaMethod : methods) {
                //@Path
                Path pathAnnotation = javaMethod.getAnnotation(Path.class);
                if (pathAnnotation == null) {
                    continue;
                }
                final String path;
                if (StringUtils.isBlank(rootPath)) {
                    path = pathAnnotation.value().trim();
                } else {
                    path = rootPath + pathAnnotation.value().trim();
                }
                if (path == null) {
                    continue;
                }
                //@HttpMethods
                final Set<HttpMethod> httpMethods = new HashSet<>();
                GET amg = javaMethod.getAnnotation(GET.class);
                if (amg != null) {
                    httpMethods.add(HttpMethod.GET);
                    Ping ping = javaMethod.getAnnotation(Ping.class);
                    if (ping != null) {
                        registerProcessor(KEY_PING, path, javaMethod, javaInstance);
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
                        memo.append("\n\t- ").append(httpMethod).append(" ").append(path).append(" (").append(javaMethod.getDeclaringClass().getName()).append(".").append(javaMethod.getName()).append(" )");
                    } catch (Throwable ex) {
                        errors.append("failed to create processor for ").append(controllerClass.getName()).append(".").append(javaMethod.getName()).append("\n\t").append(ex);
                        continue;
                    }
                    Map<HttpMethod, Map<String, RequestProcessor>> httpMethodMapRef;
                    Map<String, RequestProcessor> processorMapPerHttpMethod;
                    boolean isRegexMap = processor.hasPathParam() || processor.hasMatrixPara();
                    httpMethodMapRef = isRegexMap ? regexMap : stringMap;
                    processorMapPerHttpMethod = httpMethodMapRef.get(httpMethod);
                    if (processorMapPerHttpMethod == null) {
                        processorMapPerHttpMethod = isRegexMap
                                ? new TreeMap<>(Comparator.comparingInt(String::length).reversed().thenComparing(Function.identity()))
                                : new HashMap();
                        httpMethodMapRef.put(httpMethod, processorMapPerHttpMethod);
                    }
                    String key = processor.getDeclaredPath();
//                    if (subMap.containsKey(key)) {
//                        errors.add("request already exists: " + httpMethod + " '" + path + "' @ " + controllerClass.getName() + "." + javaMethod.getName() + "()");
//                        continue;
//                    }
                    registerProcessor(httpMethod + " " + key, path, javaMethod, javaInstance);
                    processorMapPerHttpMethod.put(key, processor);
                }
            }
        }
        checkDuplicated(errors, memo);
        //Java 17 if (!errors.isEmpty()) {
        String error = errors.toString();
        if (!error.isBlank()) {
            System.err.println("Invalid Java methods: \n" + errors);
            System.exit(1);
        }
//        final AuthConfig authCfg = AuthConfig.cfg;
//        authCfg.addDeclareRoles(declareRoles);
//        memo.append("\n\t- * LoadBalancingEndpoint=").append(loadBalancingEndpoint);
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
