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

import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.config.JExpressConfig;

import java.io.File;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface AppLifecycleListener extends /* do NOT SummerInitializer (it will init this instance before app init),*/ IdleEventMonitor.IdleEventListener {
    void beforeApplicationStart(SummerApplication.AppContext context) throws Exception;

    void onApplicationStart(SummerApplication.AppContext context, String appVersion, String fullConfigInfo) throws Exception;

    void onApplicationStop(SummerApplication.AppContext context, String appVersion);

    /**
     * called when application paused or resumed by configuration/pause file or BottController's ${ioc-root}/status?pause=true|false
     *
     * @param healthOk             true if health is ok
     * @param paused               true if paused
     * @param serviceStatusChanged true if service status changed
     * @param reason               the reason
     */
    void onApplicationStatusUpdated(SummerApplication.AppContext context, boolean healthOk, boolean paused, boolean serviceStatusChanged, String reason) throws Exception;

    void onHealthInspectionFailed(SummerApplication.AppContext context, boolean healthOk, boolean paused, long retryIndex, int nextInspectionIntervalSeconds) throws Exception;

    void onConfigChangeBefore(File configFile, JExpressConfig cfg);

    void onConfigChangedAfter(File configFile, JExpressConfig cfg, Throwable ex);
}
