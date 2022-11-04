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
package org.summerframework.boot;

import org.summerframework.util.ApplicationUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface BootConstant {

    //runtime info
    String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    String HOST = ApplicationUtil.getServerName(false);

    //version
    String VERSION = "Summer.Boot.v2.1.5rc3@" + HOST;

    //logging metadata
    String LOG4J2_KEY = "log4j.configurationFile";
    
    String CFG_AUTH = "cfg_auth.properties";
    String CFG_HTTP = "cfg_http.properties";
    String CFG_NIO = "cfg_nio.properties";
    String CFG_SMTP = "cfg_smtp.properties";
}
