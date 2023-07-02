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
package org.summerboot.jexpress.boot;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import io.grpc.ServerInterceptor;
import io.netty.channel.ChannelHandler;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigChangeListenerImpl;
import org.summerboot.jexpress.boot.instrumentation.BootHealthInspectorImpl;
import org.summerboot.jexpress.boot.instrumentation.HTTPClientStatusListener;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgrImpl;
import org.summerboot.jexpress.boot.instrumentation.jmx.ServerStatus;
import org.summerboot.jexpress.boot.instrumentation.jmx.ServerStatusMBean;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.integration.cache.AuthTokenCacheLocalImpl;
import org.summerboot.jexpress.integration.smtp.BootPostOfficeImpl;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.nio.server.BootHttpPingHandler;
import org.summerboot.jexpress.nio.server.BootHttpRequestHandler;
import org.summerboot.jexpress.nio.server.BootNioExceptionHandler;
import org.summerboot.jexpress.nio.server.BootNioLifecycleHandler;
import org.summerboot.jexpress.nio.server.HttpNioChannelInitializer;
import org.summerboot.jexpress.nio.server.NioChannelInitializer;
import org.summerboot.jexpress.nio.server.NioExceptionListener;
import org.summerboot.jexpress.nio.server.NioLifecycleListener;
import org.summerboot.jexpress.security.auth.Authenticator;
import org.summerboot.jexpress.security.auth.AuthenticatorMockImpl;
import org.summerboot.jexpress.util.ReflectionUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootGuiceModule extends AbstractModule {

    private final Object caller;
    private final Class callerClass;
    private final String callerRootPackageName;
    private final Set<String> userSpecifiedImplTags;
    private final StringBuilder memo;

    public BootGuiceModule(Object caller, Class callerClass, Set<String> userSpecifiedImplTags, StringBuilder memo) {
        this.caller = caller;
        this.callerClass = callerClass == null ? caller.getClass() : callerClass;
        this.callerRootPackageName = ReflectionUtil.getRootPackageName(this.callerClass);
        this.userSpecifiedImplTags = userSpecifiedImplTags;
        this.memo = memo;
    }

    protected boolean isCliUseImplTag(String implTag) {
        return userSpecifiedImplTags.contains(implTag);
    }

    private final static String BIND_TO = " --> ";
    private final static String INFO = "\n\t- Ioc.default.binding: ";

    @Override
    public void configure() {
        //1. Instrumentation - JMX
        bind(NIOStatusListener.class).to(ServerStatus.class);
        memo.append(INFO).append(NIOStatusListener.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(HTTPClientStatusListener.class).to(ServerStatus.class);
        memo.append(INFO).append(HTTPClientStatusListener.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(ServerStatusMBean.class).to(ServerStatus.class);
        memo.append(INFO).append(ServerStatusMBean.class.getName()).append(BIND_TO).append(ServerStatus.class.getName());

        bind(InstrumentationMgr.class).to(InstrumentationMgrImpl.class);
        memo.append(INFO).append(InstrumentationMgr.class.getName()).append(BIND_TO).append(InstrumentationMgrImpl.class.getName());

        //2. Non-Functinal services
        bind(ConfigChangeListener.class).to(ConfigChangeListenerImpl.class);
        memo.append(INFO).append(ConfigChangeListener.class.getName()).append(BIND_TO).append(ConfigChangeListenerImpl.class.getName());

        //3. NIO Controllers
        bind(NioChannelInitializer.class).to(HttpNioChannelInitializer.class);

        bind(ChannelHandler.class).annotatedWith(Names.named(BootHttpPingHandler.class.getSimpleName())).to(BootHttpPingHandler.class);
        memo.append(INFO).append(ChannelHandler.class.getName()).append(BIND_TO).append(BootHttpPingHandler.class.getSimpleName()).append(", named=").append(BootHttpPingHandler.class.getSimpleName());

        //4. @Services
        bind(HealthInspector.class).to(BootHealthInspectorImpl.class);
        memo.append(INFO).append(HealthInspector.class.getName()).append(BIND_TO).append(BootHealthInspectorImpl.class.getName());

        bind(AuthTokenCache.class).to(AuthTokenCacheLocalImpl.class);
        memo.append(INFO).append(AuthTokenCache.class.getName()).append(BIND_TO).append(AuthTokenCacheLocalImpl.class.getName());

        bind(Authenticator.class).to(AuthenticatorMockImpl.class);
        memo.append(INFO).append(Authenticator.class.getName()).append(BIND_TO).append(AuthenticatorMockImpl.class.getName());

        bind(ServerInterceptor.class).to(AuthenticatorMockImpl.class);
        memo.append(INFO).append(ServerInterceptor.class.getName()).append(BIND_TO).append(AuthenticatorMockImpl.class.getName());

        bind(NioExceptionListener.class).to(BootNioExceptionHandler.class);
        memo.append(INFO).append(NioExceptionListener.class.getName()).append(BIND_TO).append(BootNioExceptionHandler.class.getName());

        bind(NioLifecycleListener.class).to(BootNioLifecycleHandler.class);
        memo.append(INFO).append(NioLifecycleListener.class.getName()).append(BIND_TO).append(BootNioLifecycleHandler.class.getName());

        bind(PostOffice.class).to(BootPostOfficeImpl.class);
        memo.append(INFO).append(PostOffice.class.getName()).append(BIND_TO).append(BootPostOfficeImpl.class.getName());

        bind(ChannelHandler.class).annotatedWith(Names.named(BootHttpRequestHandler.class.getSimpleName())).to(BootHttpRequestHandler.class);
        memo.append(INFO).append(ChannelHandler.class.getName()).append(BIND_TO).append(BootHttpRequestHandler.class.getSimpleName()).append(", named=").append(BootHttpRequestHandler.class.getSimpleName());

        //5. Controllers
        scanAnnotation_BindInstance(binder(), Controller.class, callerRootPackageName);

        //6. caller's Main class (App.Main)
        if (caller != null) {
            requestInjection(caller);//Although requestInjection is always considered a bad idea because it can easily set up a very fragile graph of implicit dependencies, we only use it here to bind the caller's Main class (App.Main)
            memo.append(INFO).append(caller);
        }
    }

    /**
     * This method will be called by
     * <pre>
     * Guice.createInjector(...) from SummerBigBang.genesis(...) to trigger SummerBigBang.onGuiceInjectorCreated_ControllersInjected(@Controller {@code Map<String, Object>} controllers)
     * </pre>
     *
     * @param binder
     * @param rootPackageNames
     * @param annotation the class level annotation to mark this class as a HTTP
     * request controller
     */
    protected void scanAnnotation_BindInstance(Binder binder, Class<? extends Annotation> annotation, String... rootPackageNames) {
        MapBinder<String, Object> mapbinder = MapBinder.newMapBinder(binder, String.class, Object.class, annotation);
        // binder.addBinding("NFC").to(NonFunctionalServiceController.class);
        // binder.addBinding("BIZ").to(BusinessServiceController.class);

        final Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        for (String rootPackageName : rootPackageNames) {
            Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(annotation, rootPackageName, false);
            //classesAll.addAll(classes);
            for (Class c : classes) {
                Controller a = (Controller) c.getAnnotation(annotation);
                String implTag = a.implTag();
                if (StringUtils.isNotBlank(implTag) && !isCliUseImplTag(implTag)) {
                    continue;
                }

                int mod = c.getModifiers();
                if (Modifier.isAbstract(mod) || Modifier.isInterface(mod)) {
                    continue;
                }
                classesAll.add(c);
            }
        }
        classesAll.forEach(c -> {
            mapbinder.addBinding(c.getName()).to(c);
        });
    }
}
