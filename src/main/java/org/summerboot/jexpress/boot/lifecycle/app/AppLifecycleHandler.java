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
package org.summerboot.jexpress.boot.lifecycle.app;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.api.mail.PostOffice;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.config.JExpressConfig;
import org.summerboot.jexpress.integration.HealthMonitor;
import org.summerboot.jexpress.integration.mail.config.SmtpClientConfig;

import java.io.File;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class AppLifecycleHandler implements AppLifecycleListener {

    protected static final Logger log = LogManager.getLogger(AppLifecycleHandler.class.getName());


    /*@Override
    public void initCLI(Options options) {
        log.debug("");
    }

    @Override
    public void initAppBeforeIoC(File configDir) {
        log.debug(configDir);*
    }

    @Override
    public void initAppAfterIoC(File configDir, Injector guiceInjector) {
        log.debug(configDir);
    }*/

    @Inject
    protected PostOffice postOffice;

    @Override
    public void beforeApplicationStart(SummerApplication.AppContext context) throws Exception {
        log.debug("");
    }

    @Override
    public void onApplicationStart(SummerApplication.AppContext context, String appVersion, String fullConfigInfo) throws Exception {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Started", fullConfigInfo, null, false);
        }
    }

    @Override
    public void onApplicationStop(SummerApplication.AppContext context, String appVersion) {
        log.warn(appVersion);
        if (postOffice != null) {
            postOffice.sendAlertSync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Shutdown", "pid#" + BootConstants.PID, null, false);
        }
    }

    /**
     * called when application paused or resumed by configuration/pause file or
     * BottController's ${ioc-root}/status?pause=true|false
     *
     * @param healthOk
     * @param paused
     * @param serviceStatusChanged
     * @param reason
     */
    @Override
    public void onApplicationStatusUpdated(SummerApplication.AppContext context, boolean healthOk, boolean paused, boolean serviceStatusChanged, String reason) throws Exception {
        if (serviceStatusChanged) {
            boolean serviceAvaliable = healthOk && !paused;
            String content = HealthMonitor.buildMessage();
            if (postOffice != null) {
                postOffice.sendAlertAsync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Service Status Changed", content, null, false);
            }
        }
    }

    @Override
    public void onHealthInspectionFailed(SummerApplication.AppContext context, boolean healthOk, boolean paused, long retryIndex, int nextInspectionIntervalSeconds) throws Exception {
        if (postOffice != null) {
            String content = HealthMonitor.buildMessage();
            postOffice.sendAlertAsync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Health check Failed", content, null, true);
        }
    }


    @Override
    public void onConfigChangeBefore(File configFile, JExpressConfig cfg) {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Config Changed - before", cfg.info(), null, false);
        }
    }

    @Override
    public void onConfigChangedAfter(File configFile, JExpressConfig cfg, Throwable ex) {
        if (postOffice != null) {
            postOffice.sendAlertAsync(SmtpClientConfig.cfg.getEmailToAppSupport(), "Config Changed - after", cfg.info(), ex, false);
        }
    }

    @Override
    public void onIdle(IdleEventMonitor idleEventMonitor) throws Exception {
    }
}
