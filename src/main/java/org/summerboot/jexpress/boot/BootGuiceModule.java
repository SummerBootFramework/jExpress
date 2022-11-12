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

import org.summerboot.jexpress.boot.config.ConfigChangeListenerImpl;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.instrumentation.BootHealthInspectorImpl;
import org.summerboot.jexpress.boot.instrumentation.HTTPClientStatusListener;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgrImpl;
import org.summerboot.jexpress.boot.instrumentation.jmx.ServerStatus;
import org.summerboot.jexpress.boot.instrumentation.jmx.ServerStatusMBean;
import org.summerboot.jexpress.integration.smtp.BootPostOfficeImpl;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.nio.server.BootHttpRequestHandler;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.summerboot.jexpress.nio.server.BootHttpPingHandler;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.util.ReflectionUtil;
import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;
import io.netty.channel.ChannelHandler;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootGuiceModule extends AbstractModule {
    
    private final Object caller;
    private final Class callerClass;
    private final String callerRootPackageName;
    
    private final Map<Class, Map<String, List<Class>>> componentBbindingMap;
    private final Set<String> userSpecifiedImplTags;
    private final StringBuilder memo;
    
    public BootGuiceModule(Object caller, Class callerClass, Map<Class, Map<String, List<Class>>> componentBbindingMap, Set<String> userSpecifiedImplTags, StringBuilder memo) {
        this.caller = caller;
        this.callerClass = callerClass == null ? caller.getClass() : callerClass;
        callerRootPackageName = ReflectionUtil.getRootPackageName(this.callerClass);
        this.componentBbindingMap = componentBbindingMap;
        this.userSpecifiedImplTags = userSpecifiedImplTags;
        this.memo = memo;
    }
    
    protected boolean isCliUseImplTag(String mockTag) {
        return userSpecifiedImplTags.contains(mockTag);
    }
    
    @Override
    public void configure() {
        //1. Instrumentation - JMX
        bind(NIOStatusListener.class).to(ServerStatus.class);
        bind(HTTPClientStatusListener.class).to(ServerStatus.class);
        bind(ServerStatusMBean.class).to(ServerStatus.class);
        bind(InstrumentationMgr.class).to(InstrumentationMgrImpl.class);

        //2. Non-Functinal services
        bind(ConfigChangeListener.class).to(ConfigChangeListenerImpl.class);

        //3. NIO Controllers
        //if (startNIO) {
        bind(ChannelHandler.class)
                .annotatedWith(Names.named(BootHttpPingHandler.class.getName()))
                .to(BootHttpPingHandler.class);
        //}

        //4. Components
        boolean useDefaultHealthInspector = true;
        boolean useDefaultPostOffice = true;
        boolean useDefaultHttpRequestHandler = true;
        for (Class bindingClass : componentBbindingMap.keySet()) {
            Class defaultClass = null;
            Class mockClass = null;
            Class implClass = null;
            Map<String, List<Class>> componentMap = componentBbindingMap.get(bindingClass);
            for (String implTag : componentMap.keySet()) {
                Class componenClass = componentMap.get(implTag).get(0);
                if (StringUtils.isBlank(implTag)) {
                    defaultClass = componenClass;
                }
                boolean isCliUseImplTag = isCliUseImplTag(implTag);
                if (isCliUseImplTag) {
                    mockClass = componenClass;
                }
                memo.append("\n\t- Ioc.load: ").append(bindingClass).append(", implTag=").append(implTag).append(", to=").append(componenClass).append(", isCliUseImplTag=").append(isCliUseImplTag);
            }
            if (mockClass != null) {
                implClass = mockClass;
            } else if (defaultClass != null) {
                implClass = defaultClass;
            }
            if (defaultClass != null) {
                if (bindingClass.equals(ChannelHandler.class)) {
                    useDefaultHttpRequestHandler = false;
                    bind(bindingClass).annotatedWith(Names.named(BootHttpRequestHandler.BINDING_NAME)).to(implClass);
                } else {
                    bind(bindingClass).to(implClass);
                    if (bindingClass.equals(HealthInspector.class)) {
                        useDefaultHealthInspector = false;
                    } else if (bindingClass.equals(PostOffice.class)) {
                        useDefaultPostOffice = false;
                    }
                }
            }
            memo.append("\n\t- Ioc.bind: ").append(bindingClass).append(" bind to ").append(implClass);
        }
        if (useDefaultHealthInspector) {
            bind(HealthInspector.class).to(BootHealthInspectorImpl.class);
        }
        if (useDefaultPostOffice) {
            bind(PostOffice.class).to(BootPostOfficeImpl.class);
        }
        if (useDefaultHttpRequestHandler) {
            //bind(ChannelHandler.class).to(BootHttpRequestHandler.class);
            bind(ChannelHandler.class).annotatedWith(Names.named(BootHttpRequestHandler.BINDING_NAME)).to(BootHttpRequestHandler.class);
        }

        //5. Controllers
        scanAnnotation_BindInstance(binder(), Controller.class, callerRootPackageName);// triger SummerApplication.autoScan4GuiceCallback2RegisterControllers(@Controller Map<String, Object> controllers)

        //6. caller's Main class (App.Main)
        if (caller != null) {
            requestInjection(caller);//Although requestInjection is always considered a bad idea because it can easily set up a very fragile graph of implicit dependencies, we only use it here to bind the caller's Main class (App.Main)
        }
    }

    /**
     * This method should be called within Google.Guice module, and will
     * automatically trigger Google.Guice to call initControllerActions(...)
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
