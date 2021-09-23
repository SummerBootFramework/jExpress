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
package org.summerframework.boot;

import org.summerframework.boot.config.BootConfigChangeListenerImpl;
import org.summerframework.boot.config.ConfigChangeListener;
import org.summerframework.boot.instrumentation.BootHealthInspectorImpl;
import org.summerframework.boot.instrumentation.HTTPClientStatusListener;
import org.summerframework.boot.instrumentation.HealthInspector;
import org.summerframework.boot.instrumentation.NIOStatusListener;
import org.summerframework.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerframework.boot.instrumentation.jmx.InstrumentationMgrImpl;
import org.summerframework.boot.instrumentation.jmx.ServerStatus;
import org.summerframework.boot.instrumentation.jmx.ServerStatusMBean;
import org.summerframework.integration.smtp.BootPostOfficeImpl;
import org.summerframework.integration.smtp.PostOffice;
import org.summerframework.nio.server.BootHttpRequestHandler;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.summerframework.nio.server.BootHttpPingHandler;
import org.summerframework.nio.server.annotation.Controller;
import org.summerframework.util.ReflectionUtil;
import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import io.netty.channel.ChannelHandler;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
class BootGuiceModule extends AbstractModule {

    private final Object caller;
    private final Class callerClass;
    private final boolean startNIO;

    public BootGuiceModule(Object caller, Class callerClass, boolean startNIO) {
        this.caller = caller;
        this.callerClass = callerClass == null ? caller.getClass() : callerClass;
        this.startNIO = startNIO;
    }

    @Override
    public void configure() {
        //1. Instrumentation - JMX
        bind(NIOStatusListener.class).to(ServerStatus.class);
        bind(HTTPClientStatusListener.class).to(ServerStatus.class);
        bind(ServerStatusMBean.class).to(ServerStatus.class);
        bind(InstrumentationMgr.class).to(InstrumentationMgrImpl.class);

        //2. Non-Functinal services
        bind(HealthInspector.class).to(BootHealthInspectorImpl.class);
        bind(ConfigChangeListener.class).to(BootConfigChangeListenerImpl.class);
        bind(PostOffice.class).to(BootPostOfficeImpl.class);

        //3. NIO Controllers
        if (startNIO) {
            bind(ChannelHandler.class)
                    .annotatedWith(Names.named(BootHttpPingHandler.class.getName()))
                    .to(BootHttpPingHandler.class);
            bind(ChannelHandler.class)
                    .annotatedWith(Names.named(BootHttpRequestHandler.class.getName()))
                    .to(BootHttpRequestHandler.class);
            String packageName = callerClass.getPackageName();
            packageName = packageName.substring(0, packageName.indexOf("."));
            bindControllers(binder(), packageName);
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
