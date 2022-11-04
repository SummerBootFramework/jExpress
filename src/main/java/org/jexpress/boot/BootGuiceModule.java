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
package org.jexpress.boot;

import org.jexpress.boot.config.ConfigChangeListenerImpl;
import org.jexpress.boot.config.ConfigChangeListener;
import org.jexpress.boot.instrumentation.JExpressHealthInspectorImpl;
import org.jexpress.boot.instrumentation.HTTPClientStatusListener;
import org.jexpress.boot.instrumentation.HealthInspector;
import org.jexpress.boot.instrumentation.NIOStatusListener;
import org.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.jexpress.boot.instrumentation.jmx.InstrumentationMgrImpl;
import org.jexpress.boot.instrumentation.jmx.ServerStatus;
import org.jexpress.boot.instrumentation.jmx.ServerStatusMBean;
import org.jexpress.integration.smtp.JExpressPostOfficeImpl;
import org.jexpress.integration.smtp.PostOffice;
import org.jexpress.nio.server.BootHttpRequestHandler;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.jexpress.nio.server.BootHttpPingHandler;
import org.jexpress.nio.server.annotation.Controller;
import org.jexpress.util.ReflectionUtil;
import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import io.netty.channel.ChannelHandler;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class BootGuiceModule extends AbstractModule {

    private final Object caller;
    private final Class callerClass;
    private final boolean startNIO;
    private final String callerRootPackageName;

    public BootGuiceModule(Object caller, Class callerClass, boolean startNIO) {
        this.caller = caller;
        this.callerClass = callerClass == null ? caller.getClass() : callerClass;
        this.startNIO = startNIO;
        callerRootPackageName = ReflectionUtil.getRootPackageName(this.callerClass);
    }

    @Override
    public void configure() {
        //1. Instrumentation - JMX
        bind(NIOStatusListener.class).to(ServerStatus.class);
        bind(HTTPClientStatusListener.class).to(ServerStatus.class);
        bind(ServerStatusMBean.class).to(ServerStatus.class);
        bind(InstrumentationMgr.class).to(InstrumentationMgrImpl.class);

        //2. Non-Functinal services
        bind(HealthInspector.class).to(JExpressHealthInspectorImpl.class);
        bind(ConfigChangeListener.class).to(ConfigChangeListenerImpl.class);
        bind(PostOffice.class).to(JExpressPostOfficeImpl.class);

        //3. NIO Controllers
        if (startNIO) {
            bind(ChannelHandler.class)
                    .annotatedWith(Names.named(BootHttpPingHandler.class.getName()))
                    .to(BootHttpPingHandler.class);
            bind(ChannelHandler.class)
                    .annotatedWith(Names.named(BootHttpRequestHandler.class.getName()))
                    .to(BootHttpRequestHandler.class);
            bindControllers(binder(), callerRootPackageName);
        }

        //4. main
        if (caller != null) {
            requestInjection(caller);
        }
    }

    /**
     * we know that each controller classes has @Controller class level
     * annotation
     *
     * @param binder
     * @param rootPackageName
     */
    private void bindControllers(Binder binder, String... rootPackageNames) {
        bindControllers(binder, Controller.class, rootPackageNames);
    }

    /**
     * This method should be called within Google.Guice module, and will
     * automatically trigger Google.Guice to call initControllerActions(...)
     *
     * @param binder
     * @param rootPackageName
     * @param annotation the class level annotation to mark this class as a HTTP
     * request controller
     */
    private void bindControllers(Binder binder, Class<? extends Annotation> annotation, String... rootPackageNames) {
        MapBinder<String, Object> mapbinder = MapBinder.newMapBinder(binder, String.class, Object.class, annotation);
        // binder.addBinding("NFC").to(NonFunctionalServiceController.class);
        // binder.addBinding("BIZ").to(BusinessServiceController.class);

        Set<Class<?>> classesAll = new HashSet();//to remove duplicated
        for (String rootPackageName : rootPackageNames) {
            Set<Class<?>> classes = ReflectionUtil.getAllImplementationsByAnnotation(annotation, rootPackageName);
            classesAll.addAll(classes);
        }
        classesAll.forEach(c -> {
            mapbinder.addBinding(c.getName()).to(c);
        });
    }
}
