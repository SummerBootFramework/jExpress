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
package org.summerboot.jexpress.util.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstants;

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
                log.trace("Task started: {} - {}", processName, System.currentTimeMillis());
                if (lock.tryLock(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                    lock.unlock();
                    log.trace("Task finished: {} - {}", processName, System.currentTimeMillis());
                    return;
                }
                String desc = message == null
                        ? ""
                        : BootConstants.BR + "\t" + message;
                log.warn(BootConstants.BR + BootConstants.BR + "\t*** Warning: " + processName + " has timed out over " + timeoutMilliseconds + " ms ***" + BootConstants.BR + desc + BootConstants.BR + BootConstants.BR);
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
