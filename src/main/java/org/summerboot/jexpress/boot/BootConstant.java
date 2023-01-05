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

import org.summerboot.jexpress.util.ApplicationUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
interface BootConstant {

    //runtime info
    String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    String HOST = ApplicationUtil.getServerName(false);

    //version
    String VERSION = "SummerBoot.jExpress 2.2.3";

    //logging metadata
    String LOG4J2_KEY = "log4j.configurationFile";

    String CFG_AUTH = "cfg_auth.properties";
    String CFG_SMTP = "cfg_smtp.properties";
    String CFG_NIO = "cfg_nio.properties";
    String CFG_GRPC = "cfg_grpc.properties";

    /*
     * Pass by System.setProperty() instead of making them public static, any better idea?
     * ‘java.lang.System.getProperty()’ API underlyingly uses ‘java.util.Hashtable.get()’ API. 
     * Please be advised that ‘java.util.Hashtable.get()’ is a synchronized API. 
     * It means only one thread can invoke the ‘java.util.Hashtable.get()’ method at any given time. 
     */
    String SYS_PROP_APP_VERSION = "version";//used by BootController.version()
    String SYS_PROP_APP_PACKAGE_NAME = "appPackage";//used by both log4j2.xml ${sys:appPackage} and JPAHibernateConfig to scan @Entity
    String SYS_PROP_APP_NAME = "appappName";//used by log4j2.xml ${sys:appappName}
    String SYS_PROP_LOGGINGPATH = "logDir";//used by log4j2.xml ${sys:loggingPath}
    String SYS_PROP_PING_URI = "pingURI";//used by NioServer.bind() and BootHttpPingHandler. TODO: use injector
}
