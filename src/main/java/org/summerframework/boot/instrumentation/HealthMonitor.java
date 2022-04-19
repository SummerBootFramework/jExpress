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
package org.summerframework.boot.instrumentation;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerframework.nio.server.NioConfig;
import org.summerframework.util.BeanUtil;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class HealthMonitor {

    private static final Logger log = LogManager.getLogger(HealthMonitor.class.getName());

    private static final ThreadPoolExecutor POOL_HealthInspector;

    static {
        POOL_HealthInspector = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            POOL_HealthInspector.shutdown();
        }, "ShutdownHook.HealthInspector")
        );
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
        Runnable asyncTask = () -> {
            HealthInspector.healthInspectorCounter.incrementAndGet();
            boolean inspectionFailed;
            do {
                StringBuilder sb = new StringBuilder();
                sb.append(System.lineSeparator()).append("Self Inspection ");
                List<org.summerframework.nio.server.domain.Error> errors = healthInspector.ping();
                inspectionFailed = errors != null && !errors.isEmpty();
                if (inspectionFailed) {
                    String inspectionReport;
                    try {
                        inspectionReport = BeanUtil.toJson(errors, true, true);
                    } catch (Throwable ex) {
                        inspectionReport = "total " + ex;
                    }
                    sb.append("failed: ").append(inspectionReport);
                    sb.append(System.lineSeparator()).append(", will inspect again in ").append(inspectionIntervalSeconds).append(" seconds");
                    log.warn(sb);
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
            HealthInspector.healthInspectorCounter.set(0);
        };
        if (POOL_HealthInspector.getActiveCount() < 1) {
            try {
                POOL_HealthInspector.execute(asyncTask);
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
        setHealthStatus(newStatus, reason, healthInspector, NioConfig.CFG.getHealthInspectionIntervalSeconds());
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
            log.log(healthOk ? Level.WARN : Level.FATAL, "\n\t server status changed: paused=" + paused + ", OK=" + healthOk + ", serviceAvaliable=" + serviceAvaliable + "\n\t reason: " + reason);
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
