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
package org.summerframework.boot.instrumentation.jmx;

import org.summerframework.nio.server.HttpConfig;
import org.summerframework.nio.server.NioServer;
import com.google.inject.Inject;
import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import com.google.inject.Singleton;
import org.summerframework.boot.instrumentation.NIOStatusListener;
import org.summerframework.boot.instrumentation.HTTPClientStatusListener;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@Singleton
public class InstrumentationMgrImpl implements InstrumentationMgr {

    private MBeanServer mBeanServer;
    private ObjectName mbeanName;

    @Inject
    private ServerStatusMBean mbean;

    @Inject
    private NIOStatusListener nioListener;

    @Inject
    private HTTPClientStatusListener httpclientListener;

    @Override
    public void start(String beanName) throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        NioServer.setStatusListener(nioListener);
        HttpConfig.CFG.setStatusListener(httpclientListener);

        mBeanServer = ManagementFactory.getPlatformMBeanServer();
        mbeanName = new ObjectName(beanName + ":name=Status");
        //ServerStatusMBean mbean = Main.injector.getInstance(ServerStatusMBean.class);
        mBeanServer.registerMBean(mbean, mbeanName);
        ObjectName poolName = new ObjectName("com.zaxxer.hikari:type=Pool (foo)");
        //HikariPoolMXBean poolProxy = JMX.newMXBeanProxy(mBeanServer, poolName, HikariPoolMXBean.class);
        //int idleConnections = poolProxy.getIdleConnections();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(Thread.currentThread().getName() + ": shutdown JMX");
            try {
                mBeanServer.unregisterMBean(mbeanName);
            } catch (InstanceNotFoundException | MBeanRegistrationException ex) {
                ex.printStackTrace(System.err);
            }
        }, "ShutdownHook.JMX")
        );
    }

}
