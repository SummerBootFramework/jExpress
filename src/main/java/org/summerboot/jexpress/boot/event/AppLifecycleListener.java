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

import org.summerboot.jexpress.boot.config.JExpressConfig;

import java.io.File;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface AppLifecycleListener {
    void onApplicationStart(String appVersion, String fullConfigInfo);

    void onApplicationStop(String appVersion);

    /**
     * called when application paused or resumed by configuration/pause file or BottController's ${context-root}/status?pause=true|false
     *
     * @param healthOk             true if health is ok
     * @param paused               true if paused
     * @param serviceStatusChanged true if service status changed
     * @param reason               the reason
     */
    void onApplicationStatusUpdated(boolean healthOk, boolean paused, boolean serviceStatusChanged, String reason);

    void onHealthInspectionFailed(boolean healthOk, boolean paused, long retryIndex, int nextInspectionIntervalSeconds);

    void onConfigChangeBefore(File configFile, JExpressConfig cfg);

    void onConfigChangedAfter(File configFile, JExpressConfig cfg, Throwable ex);
}
