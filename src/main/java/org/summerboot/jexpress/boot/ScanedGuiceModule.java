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
import com.google.inject.name.Names;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.summerboot.jexpress.boot.annotation.Service;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ScanedGuiceModule extends AbstractModule {

    /*
     * Annotation scan results as ScanedGuiceModule input
     * Format: bindingClass <--> {key=(ImplTag+named) <--> [@Service impl list]}
     */
    private final Map<Class, Map<String, List<SummerSingularity.ServiceMetadata>>> scanedServiceBindingMap;
    private final Set<String> userSpecifiedImplTags;
    private final StringBuilder memo;

    public ScanedGuiceModule(Map<Class, Map<String, List<SummerSingularity.ServiceMetadata>>> scanedServiceBindingMap, Set<String> userSpecifiedImplTags, StringBuilder memo) {
        this.scanedServiceBindingMap = scanedServiceBindingMap;
        this.userSpecifiedImplTags = userSpecifiedImplTags;
        this.memo = memo;
    }

    protected boolean isCliUseImplTag(String implTag) {
        return userSpecifiedImplTags.contains(implTag);
    }

    @Override
    public void configure() {
        String ARROW = " --> ";
        for (Class interfaceClass : scanedServiceBindingMap.keySet()) {
            Map<String, List<SummerSingularity.ServiceMetadata>> taggeServicedMap = scanedServiceBindingMap.get(interfaceClass);
            SummerSingularity.ServiceMetadata defaultImpl = null;
            SummerSingularity.ServiceMetadata tagMatchImpl = null;
            SummerSingularity.ServiceMetadata bindingImpl;
            boolean notNamed = false;
            Map<String, SummerSingularity.ServiceMetadata> namedServiceImpls = new HashMap();
            for (String uniqueKey : taggeServicedMap.keySet()) {
                SummerSingularity.ServiceMetadata serviceImpl = taggeServicedMap.get(uniqueKey).get(0);//validated by SummerSingularity.scanAnnotation_Service_ValidateBindingMap() for error msg
                if (serviceImpl == null) {
                    continue;
                }
                String implTag = serviceImpl.getImplTag();
                boolean isCliUseImplTag = isCliUseImplTag(implTag);
                memo.append("\n\t- Ioc.taggedservice.check: ").append(interfaceClass.getName()).append(", implTag=").append(uniqueKey).append(ARROW).append(serviceImpl).append(", isCliUseImplTag=").append(isCliUseImplTag);

                String named = serviceImpl.getNamed();
                notNamed = Service.NOT_NAMED.equals(named);
                if (notNamed) {//non-named: one(interface) <--> one(Impl)
                    if (Service.NOT_TAGGED.equals(implTag)) {
                        defaultImpl = serviceImpl;
                    }
                    if (isCliUseImplTag) {
                        tagMatchImpl = serviceImpl;
                    }
                } else {//named: one(interface) <--> many(Impl)
                    //SummerSingularity.ServiceMetadata namedDefaultImpl = null;
                    //SummerSingularity.ServiceMetadata namedTagMatchImpl = null;
                    if (Service.NOT_TAGGED.equals(implTag)) {
                        //namedDefaultImpl = serviceImpl;
                        if (!namedServiceImpls.containsKey(named)) {
                            namedServiceImpls.put(named, serviceImpl);//set only when there is no existing (as default)
                        }
                    }
                    if (isCliUseImplTag) {
                        //namedTagMatchImpl = serviceImpl;
                        namedServiceImpls.put(named, serviceImpl);//override default, favor -use <impleTag> over default
                    }
                    /*//favor -use <impleTag> over default
                    SummerSingularity.ServiceMetadata namedBindingImpl = namedTagMatchImpl != null ? namedTagMatchImpl : namedDefaultImpl;
                    if (namedBindingImpl != null) {
                        Class implClass = namedBindingImpl.getServiceImplClass();
                        bind(interfaceClass).annotatedWith(Names.named(named)).to(implClass);
                        memo.append("\n\t- Ioc.taggedservice.override: ").append(interfaceClass).append(" bind to ").append(implClass).append(", named=").append(named);
                    }
                    //continue;*/
                }
            }
            if (notNamed) {
                // non-named: one(interface) <--> one(Impl)
                //favor -use <impleTag> over default
                bindingImpl = tagMatchImpl != null ? tagMatchImpl : defaultImpl;
                if (bindingImpl == null) {
                    continue;
                }
                Class implClass = bindingImpl.getServiceImplClass();
                bind(interfaceClass).to(implClass);
                memo.append("\n\t- Ioc.taggedservice.override: ").append(interfaceClass).append(" bind to ").append(implClass);
            } else {
                for (String named : namedServiceImpls.keySet()) {
                    bindingImpl = namedServiceImpls.get(named);
                    Class implClass = bindingImpl.getServiceImplClass();
                    bind(interfaceClass).annotatedWith(Names.named(named)).to(implClass);
                    memo.append("\n\t- Ioc.taggedservice.override: ").append(interfaceClass).append(" bind to ").append(implClass).append(", named=").append(named);
                }
            }
        }
    }

}
