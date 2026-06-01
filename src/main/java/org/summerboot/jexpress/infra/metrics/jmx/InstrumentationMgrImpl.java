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
package org.summerboot.jexpress.infra.metrics.jmx;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class InstrumentationMgrImpl implements InstrumentationMgr {

    protected MBeanServer mBeanServer;
    protected ObjectName mbeanName;

    @Inject
    protected ServerStatusMBean mbean;

    @Override
    public void start(String beanName) throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        mbeanName = new ObjectName(beanName + ":name=Status");
        //ServerStatusMBean mbean = Main.injector.getInstance(ServerStatusMBean.class);
        mBeanServer.registerMBean(mbean, mbeanName);
        //ObjectName poolName = new ObjectName("com.zaxxer.hikari:type=Pool (foo)");
        //HikariPoolMXBean poolProxy = JMX.newMXBeanProxy(mBeanServer, poolName, HikariPoolMXBean.class);
        //int idleConnections = poolProxy.getIdleConnections();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println(Thread.currentThread().getName() + ": shutdown JMX");
                    shutdown();
                }, "ShutdownHook.JMX")
        );
    }

    @Override
    public void shutdown() {
        if (mBeanServer != null) {
            try {

                mBeanServer.unregisterMBean(mbeanName);
            } catch (InstanceNotFoundException | MBeanRegistrationException ex) {
            }
        }
    }

}
