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
package org.summerframework.boot.config;

import org.summerframework.integration.smtp.PostOffice;
import org.summerframework.integration.smtp.SMTPConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@Singleton
public class BootConfigChangeListenerImpl implements ConfigChangeListener {

    @Inject
    protected PostOffice po;

    @Override
    public void onBefore(File configFile, SummerBootConfig cfg) {
        po.sendAlertAsync(SMTPConfig.CFG.getEmailToAppSupport(), "Config Changed - before", cfg.info(), null, false);
    }

    @Override
    public void onAfter(File configFile, SummerBootConfig cfg, Throwable ex) {
        po.sendAlertAsync(SMTPConfig.CFG.getEmailToAppSupport(), "Config Changed - after", cfg.info(), ex, false);
    }

}
