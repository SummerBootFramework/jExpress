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
package org.summerboot.jexpress.boot.instrumentation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class Timeout implements AutoCloseable {

    protected static Logger log = LogManager.getLogger(Timeout.class);

    protected final String processName;
    protected final long timeoutMilliseconds;
    protected String message;
    protected Runnable task;

    protected final ReentrantLock lock = new ReentrantLock();

    public static Timeout watch(String processName, long timeoutMilliseconds) {
        return new Timeout(processName, timeoutMilliseconds, null, null);
    }

    protected Timeout(String processName, long timeoutMilliseconds, String message, Runnable task) {
        this.processName = processName;
        this.timeoutMilliseconds = timeoutMilliseconds;
        this.message = message;
        this.task = task;
        startTheTimer();
    }

    public Timeout withDesc(String desc) {
        this.message = desc;
        return this;
    }

    public Timeout withTask(Runnable task) {
        this.task = task;
        return this;
    }

    protected void startTheTimer() {
        lock.lock();
        Runnable runnableTask = () -> {
            try {
                log.info("Task started: {} - {}", processName, System.currentTimeMillis());
                if (lock.tryLock(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                    lock.unlock();
                    log.info("Task finished: {} - {}", processName, System.currentTimeMillis());
                    return;
                }
                String desc = message == null
                        ? ""
                        : BootConstant.BR + "\t" + message;
                log.warn(BootConstant.BR + BootConstant.BR + "\t*** Warning: " + processName + " has timed out over " + timeoutMilliseconds + " ms ***" + BootConstant.BR + desc + BootConstant.BR + BootConstant.BR);
                if (task != null) {
                    BackOffice.execute(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        BackOffice.execute(runnableTask);
    }

    @Override
    public void close() throws Exception {
        lock.unlock();
    }
}
