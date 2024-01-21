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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.util.BeanUtil;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HealthMonitor {

    private static final Logger log = LogManager.getLogger(HealthMonitor.class.getName());

    protected static NioConfig nioCfg = NioConfig.cfg;

    public static final String PROMPT = "\tSelf Inspection Result: ";

    private static PostOffice postOffice;

    public static void setPostOffice(PostOffice po) {
        postOffice = po;
    }

    private static void startHealthInspectionSingleton(int inspectionIntervalSeconds, HealthInspector healthInspector) {
        if (healthInspector == null || inspectionIntervalSeconds < 1) {
            log.debug(() -> "HealthInspection Skipped: healthInspector=" + healthInspector + ", inspectionIntervalSeconds=" + inspectionIntervalSeconds);
            return;
        }
        long i = HealthInspector.healthInspectorCounter.incrementAndGet();
        if (i > 1) {
            log.debug(() -> "Duplicated HealthInspection Rejected: total=" + i);
            return;
        }
        final long timeoutMs = BackOffice.agent.getProcessTimeoutMilliseconds();
        final String timeoutDesc = BackOffice.agent.getProcessTimeoutAlertMessage();
        Runnable asyncTask = () -> {
            HealthInspector.healthInspectorCounter.incrementAndGet();
            boolean inspectionFailed;
            try {
                do {
                    StringBuilder sb = new StringBuilder();
                    sb.append(BootConstant.BR).append(PROMPT);
                    List<Err> errors = null;
                    try (var a = Timeout.watch(healthInspector.getClass().getName() + ".ping()", timeoutMs).withDesc(timeoutDesc)) {
                        errors = healthInspector.ping();
                    } catch (Throwable ex) {
                    }

                    inspectionFailed = errors != null && !errors.isEmpty();
                    if (inspectionFailed) {
                        String inspectionReport;
                        try {
                            inspectionReport = BeanUtil.toJson(errors, true, true);
                        } catch (Throwable ex) {
                            inspectionReport = "total " + ex;
                        }
                        sb.append(inspectionReport);
                        sb.append(BootConstant.BR).append(", will inspect again in ").append(inspectionIntervalSeconds).append(" seconds");
                        log.warn(sb);
                        if (postOffice != null) {
                            postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Health Inspection Failed", sb.toString(), null, true);
                        }
                        try {
                            TimeUnit.SECONDS.sleep(inspectionIntervalSeconds);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        sb.append("passed");
                        setHealthStatus(true, sb.toString(), null);
                    }
                } while (inspectionFailed);
            } finally {
                HealthInspector.healthInspectorCounter.set(0);
            }
        };
        if (i <= 1) {
            try {
                BackOffice.execute(asyncTask);
            } catch (RejectedExecutionException ex2) {
                log.debug(() -> "Duplicated HealthInspection Rejected: " + ex2);
            }
        } else {
            log.debug("HealthInspection Skipped");
        }
    }

    private static boolean healthOk = true, paused = false;
    private static String statusReason;
    //private static HttpResponseStatus status = HttpResponseStatus.OK;
    private static boolean serviceAvaliable = true;

    public static void setHealthStatus(boolean newStatus, String reason, HealthInspector healthInspector) {
        setHealthStatus(newStatus, reason, healthInspector, nioCfg.getHealthInspectionIntervalSeconds());
    }

    public static void setHealthStatus(boolean newStatus, String reason, HealthInspector healthInspector, int healthInspectionIntervalSeconds) {
        boolean serviceStatusChanged = healthOk != newStatus;
        healthOk = newStatus;
        updateServiceStatus(serviceStatusChanged, reason);
        if (!healthOk && healthInspector != null) {
            startHealthInspectionSingleton(healthInspectionIntervalSeconds, healthInspector);
        }
    }

    public static void setPauseStatus(boolean newStatus, String reason) {
        boolean serviceStatusChanged = paused != newStatus;
        paused = newStatus;
        updateServiceStatus(serviceStatusChanged, reason);
    }

    private static void updateServiceStatus(boolean serviceStatusChanged, String reason) {
        statusReason = reason;
//        status = paused
//                ? HttpResponseStatus.SERVICE_UNAVAILABLE
//                : (serviceOk ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
//        if (serviceStatusChanged) {
//            log.log(serviceOk ? Level.WARN : Level.FATAL, "\n\t server status changed: paused=" + paused + ", OK=" + serviceOk + ", status=" + status + "\n\t reason: " + reason);
//        }
        serviceAvaliable = healthOk && !paused;
        if (serviceStatusChanged) {
            String content = "\n\t server status changed: paused=" + paused + ", OK=" + healthOk + ", serviceAvaliable=" + serviceAvaliable + "\n\t reason: " + reason;
            log.log(healthOk ? Level.WARN : Level.FATAL, content);
            if (postOffice != null) {
                postOffice.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Service Status Changed", content, null, false);
            }
        }
    }

    public static boolean isServicePaused() {
        return paused;
    }

    public static boolean isServiceStatusOk() {
        return healthOk;
    }

    //    public static HttpResponseStatus getServiceStatus() {
//        return status;
//    }
    public static boolean isServiceAvaliable() {
        return serviceAvaliable;
    }

    public static String getServiceStatusReason() {
        return statusReason;
    }
}
