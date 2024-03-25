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
package org.summerboot.jexpress.nio.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class AbortPolicyWithReport extends ThreadPoolExecutor.AbortPolicy {

    protected static final Logger log = LogManager.getLogger(AbortPolicyWithReport.class.getName());

    protected final String threadName;

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
