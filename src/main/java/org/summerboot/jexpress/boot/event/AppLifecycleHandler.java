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
package org.summerboot.jexpress.boot.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.SummerRunner;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;

import java.io.File;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class AppLifecycleHandler implements AppLifecycleListener {

    protected static final Logger log = LogManager.getLogger(AppLifecycleHandler.class.getName());

    @Inject
    protected PostOffice postOffice;

    @Override
    public void beforeApplicationStart(SummerRunner.RunnerContext context) {
    }

    @Override
    public void onApplicationStart(SummerRunner.RunnerContext context, String appVersion, String fullConfigInfo) {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Started", fullConfigInfo, null, false);
        }
    }

    @Override
    public void onApplicationStop(SummerRunner.RunnerContext context, String appVersion) {
        log.warn(appVersion);
        if (postOffice != null) {
            postOffice.sendAlertSync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Shutdown", "pid#" + BootConstant.PID, null, false);
        }
    }

    /**
     * called when application paused or resumed by configuration/pause file or
     * BottController's ${context-root}/status?pause=true|false
     *
     * @param healthOk
     * @param paused
     * @param serviceStatusChanged
     * @param reason
     */
    @Override
    public void onApplicationStatusUpdated(SummerRunner.RunnerContext context, boolean healthOk, boolean paused, boolean serviceStatusChanged, String reason) {
        if (serviceStatusChanged) {
            boolean serviceAvaliable = healthOk && !paused;
            String content = HealthMonitor.buildMessage();
            if (postOffice != null) {
                postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Service Status Changed", content, null, false);
            }
        }
    }

    @Override
    public void onHealthInspectionFailed(SummerRunner.RunnerContext context, boolean healthOk, boolean paused, long retryIndex, int nextInspectionIntervalSeconds) {
        if (postOffice != null) {
            String content = HealthMonitor.buildMessage();
            postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Health Inspection Failed", content, null, true);
        }
    }


    @Override
    public void onConfigChangeBefore(File configFile, JExpressConfig cfg) {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Config Changed - before", cfg.info(), null, false);
        }
    }

    @Override
    public void onConfigChangedAfter(File configFile, JExpressConfig cfg, Throwable ex) {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Config Changed - after", cfg.info(), ex, false);
        }
    }
}
