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

import org.summerframework.nio.server.domain.Error;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerframework.boot.BootConstant;
import org.summerframework.boot.BootErrorCode;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@Singleton
public class BootPostOfficeImpl implements PostOffice {

    private static final ExecutorService POST_OFFICE;

    static {
        POST_OFFICE = new ThreadPoolExecutor(2, 2,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            POST_OFFICE.shutdown();
        }, "ShutdownHook.PostOffice")
        );
    }

    protected Logger log = LogManager.getLogger(getClass().getName());

    /**
     * Update alert title
     *
     * @param title
     * @return
     */
    protected String updateAlertTitle(String title) {
        return "[ALERT] " + title;
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
    protected void sendAlert(Collection<String> to, final String title, final String content, final Throwable cause, boolean debouncing, boolean async) {
        if (to == null || to.isEmpty()) {
            //log.warn(() -> "unknown recipient: " + title + "\n" + _content);
            return;
        }
        Runnable postman = () -> {
            if (debouncing) {
                String key = title;
                Throwable rootCause = ExceptionUtils.getRootCause(cause);
                if (rootCause != null) {
                    key = key + rootCause.getClass().getName();
                }
                if (debounced(key, SMTPConfig.CFG.getEmailAlertDebouncingInterval())) {
                    return;
                }
            }
            Email email = Email.compose(updateAlertTitle(title), updateAlertContent(content, cause), Email.Format.text).to(to);
            try {
                email.send();
            } catch (Throwable ex) {
                log.fatal("Failed to send email: " + ExceptionUtils.getRootCause(ex).toString());
            }
        };
        if (async) {
            POST_OFFICE.execute(postman);
        } else {
            postman.run();
        }
    }

    @Override
    public boolean sendEmailAsync(Collection<String> to, String title, String content, boolean isHTMLFormat) {
        return this.sendEmail(to, title, content, isHTMLFormat, true);
    }

    @Override
    public boolean sendEmailSync(Collection<String> to, String title, String content, boolean isHTMLFormat) {
        return this.sendEmail(to, title, content, isHTMLFormat, false);
    }

    protected boolean sendEmail(Collection<String> to, String title, String content, boolean isHTMLFormat, boolean async) {
        boolean success = false;
        Email email = Email.compose(title, content, isHTMLFormat ? Email.Format.html : Email.Format.text).to(to);
        if (to != null && !to.isEmpty()) {
            try {
                if (async) {
                    Runnable postman = () -> {
                        try {
                            email.send();
                        } catch (Throwable ex) {
                            log.fatal("Failed to send email: " + ExceptionUtils.getRootCause(ex).toString());
                        }
                    };
                    POST_OFFICE.execute(postman);
                } else {
                    email.send();
                }
                success = true;
            } catch (Throwable ex) {
                log.fatal("Failed to send email: " + ExceptionUtils.getRootCause(ex).toString());
            }
        } else {
            log.error(() -> "unknown recipient: " + email);
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
    public List<Error> ping(String... emails) {
        Set<String> r = Set.of(emails);
        Error e = null;
        //String[] emails = AppConfig.CFG.getEmailToAppSupport();
        boolean success = sendEmailSync(r, "[Ping] " + BootConstant.VERSION, "just to test if you can receive this email.", false);
        if (!success) {
            e = new Error(BootErrorCode.ACCESS_ERROR_SMTP, "Mail Access Error", "failed to send test email to app support", null);
        }
        List<Error> errors = null;
        if (e != null) {
            errors = new ArrayList<>();
            errors.add(e);
        }
        return errors;
    }

}
