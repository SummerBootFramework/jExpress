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
package org.summerboot.jexpress.boot.ioc;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import io.grpc.ServerInterceptor;
import io.netty.channel.ChannelHandler;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Scheduler;
import org.summerboot.jexpress.annotation.Controller;
import org.summerboot.jexpress.annotation.Service;
import org.summerboot.jexpress.annotation.health.HealthCheck;
import org.summerboot.jexpress.api.auth.Authenticator;
import org.summerboot.jexpress.api.cache.AuthTokenCache;
import org.summerboot.jexpress.api.mail.PostOffice;
import org.summerboot.jexpress.api.rest.BootHttpRequestHandler;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.lifecycle.app.AppLifecycleHandler;
import org.summerboot.jexpress.boot.lifecycle.app.AppLifecycleListener;
import org.summerboot.jexpress.boot.lifecycle.http.HttpExceptionHandler;
import org.summerboot.jexpress.boot.lifecycle.http.HttpExceptionListener;
import org.summerboot.jexpress.boot.lifecycle.http.HttpLifecycleHandler;
import org.summerboot.jexpress.boot.lifecycle.http.HttpLifecycleListener;
import org.summerboot.jexpress.infra.metrics.HttpClientStatusListener;
import org.summerboot.jexpress.infra.metrics.NioStatusListener;
import org.summerboot.jexpress.infra.metrics.jmx.InstrumentationMgr;
import org.summerboot.jexpress.infra.metrics.jmx.InstrumentationMgrImpl;
import org.summerboot.jexpress.infra.metrics.jmx.ServerStatus;
import org.summerboot.jexpress.infra.metrics.jmx.ServerStatusMBean;
import org.summerboot.jexpress.infra.netty.BootHttpPingHandler;
import org.summerboot.jexpress.infra.netty.HttpNioChannelInitializer;
import org.summerboot.jexpress.infra.netty.NioChannelInitializer;
import org.summerboot.jexpress.integration.cache.local.AuthTokenCacheLocalImpl;
import org.summerboot.jexpress.integration.mail.BootPostOfficeImpl;
import org.summerboot.jexpress.integration.scheduling.quartz.GuiceSchedulerProvider;
import org.summerboot.jexpress.security.auth.LdapAuthenticator;
import org.summerboot.jexpress.util.reflect.ReflectionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootGuiceModule extends AbstractModule {

    protected final static String BIND_TO = " --> ";
    protected final static String INFO = BootConstants.BR + "\t- Ioc.default.binding: ";
    protected final Object caller;
    protected final Class callerClass;
    protected final String callerRootPackageName;
    protected final Set<String> userSpecifiedImplTags;
    protected final StringBuilder memo;

    public BootGuiceModule(Object caller, Class callerClass, Set<String> userSpecifiedImplTags, StringBuilder memo) {
        this.caller = caller;
        this.callerClass = callerClass == null ? caller.getClass() : callerClass;
        this.callerRootPackageName = "";//REF2610-1 removed: ReflectionUtil.getRootPackageName(this.callerClass, BootConstant.PACKAGE_LEVEL);
        this.userSpecifiedImplTags = userSpecifiedImplTags;
        this.memo = memo;
    }

    protected boolean isTagSpecifiedViaCLI(String implTag) {
        return userSpecifiedImplTags != null && userSpecifiedImplTags.contains(implTag);
    }

    @Override
    public void configure() {
        // 1. Instrumentation - JMX
        bind(NioStatusListener.class).to(ServerStatus.class);
        memo.append(INFO).append(NioStatusListener.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(HttpClientStatusListener.class).to(ServerStatus.class);
        memo.append(INFO).append(HttpClientStatusListener.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(ServerStatusMBean.class).to(ServerStatus.class);
        memo.append(INFO).append(ServerStatusMBean.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(InstrumentationMgr.class).to(InstrumentationMgrImpl.class);
        memo.append(INFO).append(InstrumentationMgr.class.getName()).append(BIND_TO).append(InstrumentationMgrImpl.class.getName());

        // 2. Non-Functinal services
//        bind(ConfigChangeListener.class).to(ConfigChangeListenerImpl.class);
//        memo.append(INFO).append(ConfigChangeListener.class.getName()).append(BIND_TO).append(ConfigChangeListenerImpl.class.getName());

        // 3. NIO Controllers
        bind(NioChannelInitializer.class).to(HttpNioChannelInitializer.class);

        bind(ChannelHandler.class).annotatedWith(Names.named(BootHttpPingHandler.class.getSimpleName())).to(BootHttpPingHandler.class);
        memo.append(INFO).append(ChannelHandler.class.getName()).append(BIND_TO).append(BootHttpPingHandler.class.getSimpleName()).append(", named=").append(BootHttpPingHandler.class.getSimpleName());

        // 4. @Services
        bind(AuthTokenCache.class).to(AuthTokenCacheLocalImpl.class);
        memo.append(INFO).append(AuthTokenCache.class.getName()).append(BIND_TO).append(AuthTokenCacheLocalImpl.class.getName());

        bind(Authenticator.class).to(LdapAuthenticator.class);
        memo.append(INFO).append(Authenticator.class.getName()).append(BIND_TO).append(LdapAuthenticator.class.getName());

        bind(ServerInterceptor.class).to(LdapAuthenticator.class);
        memo.append(INFO).append(ServerInterceptor.class.getName()).append(BIND_TO).append(LdapAuthenticator.class.getName());

        bind(HttpExceptionListener.class).to(HttpExceptionHandler.class);
        memo.append(INFO).append(HttpExceptionListener.class.getName()).append(BIND_TO).append(HttpExceptionHandler.class.getName());

        bind(HttpLifecycleListener.class).to(HttpLifecycleHandler.class);
        memo.append(INFO).append(HttpLifecycleListener.class.getName()).append(BIND_TO).append(HttpLifecycleHandler.class.getName());

        bind(AppLifecycleListener.class).to(AppLifecycleHandler.class);
        memo.append(INFO).append(AppLifecycleListener.class.getName()).append(BIND_TO).append(AppLifecycleHandler.class.getName());

        bind(PostOffice.class).to(BootPostOfficeImpl.class);
        memo.append(INFO).append(PostOffice.class.getName()).append(BIND_TO).append(BootPostOfficeImpl.class.getName());

        bind(ChannelHandler.class).annotatedWith(Names.named(BootHttpRequestHandler.class.getSimpleName())).to(BootHttpRequestHandler.class);
        memo.append(INFO).append(ChannelHandler.class.getName()).append(BIND_TO).append(BootHttpRequestHandler.class.getSimpleName()).append(", named=").append(BootHttpRequestHandler.class.getSimpleName());

        // 5. get instances
        scanAnnotation_BindInstance(binder(), Controller.class, callerRootPackageName);
        scanAnnotation_BindInstance(binder(), HealthCheck.class, callerRootPackageName);

        // 6. caller's Main class (App.Main)
        if (caller != null) {
            requestInjection(caller);//Although requestInjection is always considered a bad idea because it can easily set up a very fragile graph of implicit dependencies, we only use it here to bind the caller's Main class (App.Main)
            memo.append(INFO).append(caller);
        }
        // 7. supports org.quartz with Guice IoC
        bind(Scheduler.class).toProvider(GuiceSchedulerProvider.class).asEagerSingleton();
    }

    /**
     * This method will be called by
     * <pre>
     * Guice.createInjector(...) from SummerBigBang.genesis(...)
     * it will trigger SummerBigBang.onGuiceInjectorCreated_ControllersInjected(@Controller {@code Map<String, Object>} controllers)
     * </pre>
     *
     * @param binder
     * @param rootPackageNames
     * @param annotation       the class level annotation to mark this class as a HTTP
     *                         request api
     */
    protected void scanAnnotation_BindInstance(Binder binder, Class<? extends Annotation> annotation, String... rootPackageNames) {
        MapBinder<String, Object> mapbinder = MapBinder.newMapBinder(binder, String.class, Object.class, annotation);
        // binder.addBinding("NFC").to(NonFunctionalServiceController.class);
        // binder.addBinding("BIZ").to(BusinessServiceController.class);

        final Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        //for (String rootPackageName : rootPackageNames) {
        Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(annotation, false, rootPackageNames);
        //classesAll.addAll(classes);
        for (Class c : classes) {
            //<A extends Annotation>
            Annotation a = c.getAnnotation(annotation);
            int mod = c.getModifiers();
            if (Modifier.isAbstract(mod) || Modifier.isInterface(mod)) {
                continue;
            }
            String alternativeName = null;
            if (a instanceof Controller) {
                Controller ca = (Controller) a;
                alternativeName = ca.AlternativeName();
            } else if (a instanceof HealthCheck) {
                Service sa = (Service) c.getAnnotation(Service.class);
                if (sa != null) {
                    alternativeName = sa.AlternativeName();
                }
            }
            // no alternativeName = always use this api, with alternativeName = only use this api when -use <alternativeName> specified
            if (StringUtils.isNotBlank(alternativeName) && !isTagSpecifiedViaCLI(alternativeName)) {
                continue;
            }
            classesAll.add(c);
        }
        //}
        classesAll.forEach(c -> {
            mapbinder.addBinding(c.getName()).to(c);
        });
    }
}
