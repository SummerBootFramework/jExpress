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

import com.google.inject.Singleton;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.nio.server.domain.Err;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class BootPostOfficeImpl implements PostOffice {

    protected String appVersion = BootConstant.VERSION;

    @Override
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    protected Logger log = LogManager.getLogger(getClass());

    /**
     * Update alert title
     *
     * @param title
     * @return
     */
    protected String updateAlertTitle(String title) {
        return "Alert@" + SummerApplication.HOST + " " + appVersion + "[" + BootConstant.APP_ID + "] - " + title + " [" + OffsetDateTime.now() + "]";
    }

    /**
     * Update alert content
     *
     * @param content
     * @param cause
     * @return
     */
    protected String updateAlertContent(String content, Throwable cause) {
        if (cause == null) {
            return content;
        } else {
            return content + "\n\n" + ExceptionUtils.getStackTrace(cause);
        }
    }

    @Override
    public void sendAlertAsync(Collection<String> to, final String title, final String content, final Throwable cause, boolean debouncing) {
        sendAlert(to, title, content, cause, debouncing, true);
    }

    @Override
    public void sendAlertSync(Collection<String> to, final String title, final String content, final Throwable cause, boolean debouncing) {
        sendAlert(to, title, content, cause, debouncing, false);
    }

    /**
     * The implementation of both sendAlertAsync and sendAlertSync
     *
     * @param to
     * @param title
     * @param content
     * @param cause
     * @param debouncing
     * @param async
     */
    @Override
    public void sendAlert(Collection<String> to, final String title, final String content, final Throwable cause, boolean debouncing, boolean async) {
        if (debouncing) {
            String key = title;
            Throwable rootCause = ExceptionUtils.getRootCause(cause);
            if (rootCause == null) {
                rootCause = cause;
            }
            if (rootCause != null) {
                key = key + rootCause.getClass().getName();
            }
            if (debounced(key, SMTPClientConfig.cfg.getEmailAlertDebouncingIntervalMinutes())) {
                return;
            }
        }
        sendEmail(to, updateAlertTitle(title), updateAlertContent(content, cause), false, async);
    }

    @Override
    public boolean sendEmailAsync(Collection<String> to, String title, String content, boolean isHTMLFormat) {
        return this.sendEmail(to, title, content, isHTMLFormat, true);
    }

    @Override
    public boolean sendEmailSync(Collection<String> to, String title, String content, boolean isHTMLFormat) {
        return this.sendEmail(to, title, content, isHTMLFormat, false);
    }

    @Override
    public boolean sendEmail(Collection<String> to, String title, String content, boolean isHTMLFormat, boolean async) {
        Email email = Email.compose(title, content, isHTMLFormat ? Email.Format.html : Email.Format.text).to(to);
        if (to == null || to.isEmpty()) {
            log.warn(() -> "unknown recipient: " + email);
            return false;
        }

        boolean success = false;
        try {
            if (async) {
                Runnable postman = () -> {
                    try {
                        email.send(SMTPClientConfig.cfg.getMailSession());
                    } catch (Throwable ex) {
                        log.fatal("Failed to send email: " + ExceptionUtils.getRootCause(ex).toString());
                    }
                };
                BackOffice.execute(postman);
            } else {
                email.send(SMTPClientConfig.cfg.getMailSession());
            }
            success = true;
        } catch (Throwable ex) {
            log.fatal("Failed to send email: " + ExceptionUtils.getRootCause(ex).toString());
        }
        return success;
    }

    protected final Map<String, Long> debouncingData = new ConcurrentHashMap<>();

    protected boolean debounced(String key, int ttlMinute) {
        long now = System.currentTimeMillis();
        long ttl = now + ttlMinute * 60000;
        clean(now);
        Long existingTTL = debouncingData.putIfAbsent(key, ttl);
//        if (existingTTL == null) {
//            return false;
//        }
//        if (existingTTL < now) {
//            debouncingData.put(key, ttl);
//            return false;
//        }
//        return true;
        return existingTTL != null;
    }

    protected void clean(long now) {
        debouncingData.keySet().forEach(key -> {
            Long existingTTL = debouncingData.get(key);
            if (existingTTL == null || existingTTL < now) {
                debouncingData.remove(key);
            }
        });
    }

    @Override
    public List<Err> ping(String... emails) {
        Set<String> r = Set.of(emails);
        Err e = null;
        boolean success = sendEmailSync(r, "[Ping] " + appVersion, "just to test if you can receive this email.", false);
        if (!success) {
            e = new Err(BootErrorCode.ACCESS_ERROR_SMTP, null, "Mail Access Error - failed to send test email to app support", null, null);
        }
        List<Err> errors = null;
        if (e != null) {
            errors = new ArrayList<>();
            errors.add(e);
        }
        return errors;
    }

}
