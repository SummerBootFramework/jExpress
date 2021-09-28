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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class AbortPolicyWithReport extends ThreadPoolExecutor.AbortPolicy {

    private static final Logger log = LogManager.getLogger(AbortPolicyWithReport.class.getName());

    private final String threadName;

    public AbortPolicyWithReport(String threadName) {
        this.threadName = threadName;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor tpe) {
        String msg = threadName + "[ Pool Size=" + tpe.getPoolSize() + " (queue=" + tpe.getQueue().size() + ", active=" + tpe.getActiveCount() + ", core=" + tpe.getCorePoolSize() + ", max=" + tpe.getMaximumPoolSize() + ", largest=" + tpe.getLargestPoolSize()
                + "), Task=" + tpe.getTaskCount() + " (completed: " + tpe.getCompletedTaskCount() + "), Executor status:(isShutdown:" + tpe.isShutdown() + ", isTerminated:" + tpe.isTerminated() + ", isTerminating:" + tpe.isTerminating() + ")]";

        log.warn(msg);
        throw new RejectedExecutionException(msg);
    }
}
