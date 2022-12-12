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
package org.summerboot.jexpress.boot.instrumentation.jmx;

import org.summerboot.jexpress.nio.server.NioServer;
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
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class InstrumentationMgrImpl implements InstrumentationMgr {

    private MBeanServer mBeanServer;
    private ObjectName mbeanName;

    @Inject
    private ServerStatusMBean mbean;

    @Inject
    private NIOStatusListener nioListener;

    //protected static HttpClientConfig httpCfg = HttpClientConfig.instance(HttpClientConfig.class);
    @Override
    public void start(String beanName) throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        NioServer.setStatusListener(nioListener);
        //httpCfg.setStatusListener(httpclientListener);

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
        try {
            mBeanServer.unregisterMBean(mbeanName);
        } catch (InstanceNotFoundException | MBeanRegistrationException ex) {
        }
    }

}
