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
package org.summerboot.jexpress.integration.smtp;

import org.summerboot.jexpress.boot.instrumentation.HealthInspector;

import java.util.Collection;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface PostOffice extends HealthInspector<String> {

    /**
     * send email
     *
     * @param to
     * @param subject
     * @param content
     * @param isHTMLFormat
     * @param async
     * @return
     */
    boolean sendEmail(Collection<String> to, String subject, String content, boolean isHTMLFormat, boolean async);

    /**
     * send alert email
     *
     * @param to
     * @param subject
     * @param content
     * @param cause
     * @param debouncing
     * @param async
     */
    void sendAlert(Collection<String> to, final String subject, final String content, final Throwable cause, boolean debouncing, boolean async);

    /**
     * send alert email in async mode
     *
     * @param to
     * @param subject
     * @param content
     * @param cause
     * @param debouncing
     */
    void sendAlertAsync(Collection<String> to, String subject, String content, final Throwable cause, boolean debouncing);

    /**
     * send alert email in sync mode
     *
     * @param to
     * @param subject
     * @param content
     * @param cause
     * @param debouncing
     */
    void sendAlertSync(Collection<String> to, String subject, String content, final Throwable cause, boolean debouncing);

    /**
     * send email in sync mode
     *
     * @param to
     * @param subject
     * @param content
     * @param isHTMLFormat
     * @return true is success
     */
    boolean sendEmailSync(Collection<String> to, String subject, String content, boolean isHTMLFormat);

    /**
     * send email in async mode
     *
     * @param to
     * @param subject
     * @param content
     * @param isHTMLFormat
     * @return
     */
    boolean sendEmailAsync(Collection<String> to, String subject, String content, boolean isHTMLFormat);

    void setAppVersion(String appVersion);
}
