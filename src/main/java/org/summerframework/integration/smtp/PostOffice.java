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
package org.summerframework.integration.smtp;

import org.summerframework.boot.instrumentation.HealthInspector;
import java.util.Collection;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public interface PostOffice extends HealthInspector<String> {

    /**
     * send alert email in async mode
     *
     * @param to
     * @param title
     * @param content
     * @param cause
     * @param debouncing
     */
    void sendAlertAsync(Collection<String> to, String title, String content, final Throwable cause, boolean debouncing);

    /**
     * send alert email in sync mode
     *
     * @param to
     * @param title
     * @param content
     * @param cause
     * @param debouncing
     */
    void sendAlertSync(Collection<String> to, String title, String content, final Throwable cause, boolean debouncing);

    /**
     * send email in sync mode
     *
     * @param to
     * @param title
     * @param content
     * @param isHTMLFormat
     * @return true is success
     */
    boolean sendEmailSync(Collection<String> to, String title, String content, boolean isHTMLFormat);

    /**
     * send email in async mode
     *
     * @param to
     * @param title
     * @param content
     * @param isHTMLFormat
     * @return
     */
    boolean sendEmailAsync(Collection<String> to, String title, String content, boolean isHTMLFormat);
}
