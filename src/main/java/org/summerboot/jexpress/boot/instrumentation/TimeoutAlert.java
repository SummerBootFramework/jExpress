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

import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.Backoffice;
import org.summerboot.jexpress.boot.BootConstant;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class TimeoutAlert implements AutoCloseable {

    protected static Logger log = LogManager.getLogger(TimeoutAlert.class);

    private final String processName;
    private final long timeoutMilliseconds;
    private String message;
    private boolean isFinishedOnTime = false;

    public TimeoutAlert(String processName) {
        this(processName, Backoffice.cfg.getProcessTimeoutMilliseconds(), null);
    }

    public TimeoutAlert(String processName, long timeoutMilliseconds) {
        this(processName, timeoutMilliseconds, null);
    }

    public TimeoutAlert(String processName, long timeoutMilliseconds, String message) {
        this.processName = processName;
        this.timeoutMilliseconds = timeoutMilliseconds;
        this.message = message;
        startTheTimer();
    }

    private void startTheTimer() {
        Runnable runnableTask = () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(timeoutMilliseconds);
                if (!isFinishedOnTime) {
                    if (message == null) {
                        message = Backoffice.cfg.getProcessTimeoutAlertMessage();
                    }
                    log.warn(BootConstant.BR + "\t*** Warning: " + processName + " timeout in " + timeoutMilliseconds + "ms ***" + BootConstant.BR + "\t" + message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Backoffice.execute(runnableTask);
    }

    @Override
    public void close() throws Exception {
        isFinishedOnTime = true;
    }

}
