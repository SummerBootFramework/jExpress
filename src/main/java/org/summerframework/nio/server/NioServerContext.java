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
package org.summerframework.nio.server;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class NioServerContext {//package access only, not a public class

    protected static final AtomicLong COUNTER_PING_HIT = new AtomicLong(0);
    protected static final AtomicLong COUNTER_BIZ_HIT = new AtomicLong(0);
    protected static final AtomicLong COUNTER_TOTAL_CHANNEL = new AtomicLong(0);
    protected static final AtomicLong COUNTER_ACTIVE_CHANNEL = new AtomicLong(0);
    protected static final AtomicLong COUNTER_HIT = new AtomicLong(0);
    protected static final AtomicLong COUNTER_SENT = new AtomicLong(0);

    protected static String webApiContextRoot = null;
    protected static String loadBalancerHealthCheckPath = null;

    public static String getWebApiContextRoot() {
        return webApiContextRoot;
    }

    public static void setWebApiContextRoot(String contextRoot) {
        if (webApiContextRoot != null) {
            throw new UnsupportedOperationException("Context Root has been set, and cannot be changed");
        }
        webApiContextRoot = contextRoot;
    }

    public static String getLoadBalancerHealthCheckPath() {
        return loadBalancerHealthCheckPath;
    }

    public static void setLoadBalancerHealthCheckPath(String pingPath) {
        if (loadBalancerHealthCheckPath != null) {
            throw new UnsupportedOperationException("Ping path has been set, and cannot be changed");
        }
        loadBalancerHealthCheckPath = pingPath;
    }

}
