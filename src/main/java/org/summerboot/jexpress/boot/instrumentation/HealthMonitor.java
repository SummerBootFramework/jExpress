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
import org.summerboot.jexpress.boot.event.AppLifecycleListener;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.util.BeanUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HealthMonitor {

    protected static final Logger log = LogManager.getLogger(HealthMonitor.class.getName());

    protected static volatile AppLifecycleListener appLifecycleListener;

    protected static volatile ExecutorService tpe = Executors.newSingleThreadExecutor();
    protected static volatile LinkedBlockingQueue<HealthInspector> healthInspectorQueue = new LinkedBlockingQueue<>();

    protected static volatile Set<HealthInspector> registeredHealthInspectors = new HashSet<>();

    public static void setAppLifecycleListener(AppLifecycleListener listener) {
        appLifecycleListener = listener;
    }

    public static void registerDefaultHealthInspectors(Map<String, Object> defaultHealthInspectors, StringBuilder memo) {
        registeredHealthInspectors.clear();
        if (defaultHealthInspectors == null || defaultHealthInspectors.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : defaultHealthInspectors.entrySet()) {
            String name = entry.getKey();
            Object healthInspector = entry.getValue();
            if (healthInspector instanceof HealthInspector) {
                registeredHealthInspectors.add((HealthInspector) healthInspector);
                memo.append(BootConstant.BR).append("\t- DefaultHealthInspector registered: ").append(name).append("=").append(healthInspector.getClass().getName());
            }
        }
    }

    private static boolean keepRunning = true;

    public static void start() {
        inspect();
        Runnable asyncTask = () -> {
            int inspectionIntervalSeconds = NioConfig.cfg.getHealthInspectionIntervalSeconds();
            long timeoutMs = BackOffice.agent.getProcessTimeoutMilliseconds();
            String timeoutDesc = BackOffice.agent.getProcessTimeoutAlertMessage();
            final Set<HealthInspector> batchInspectors = new TreeSet<>();
            do {
                ServiceError healthCheckFailedReport = new ServiceError(BootConstant.APP_ID + "-HealthMonitor");
                batchInspectors.clear();
                boolean healthCheckAllPassed = true;
                try {
                    // take all health inspectors from the queue, remove duplicated
                    do {
                        HealthInspector healthInspector = healthInspectorQueue.take();// block/wait here for health inspectors
                        batchInspectors.add(healthInspector);
                    } while (!healthInspectorQueue.isEmpty());
                    // inspect
                    for (HealthInspector healthInspector : batchInspectors) {
                        String name = healthInspector.getClass().getName();
                        try (var a = Timeout.watch(name + ".ping()", timeoutMs).withDesc(timeoutDesc)) {
                            HealthInspector.Type type = healthInspector.type();
                            List<Err> errs = healthInspector.ping();
                            boolean currentInspectionPassed = errs == null || errs.isEmpty();
                            if (!currentInspectionPassed) {
                                healthInspectorQueue.offer(healthInspector);
                            }
                            switch (type) {
                                case ServicePaused -> {
                                    String relasePausePassword = healthInspector.releasePausePassword();
                                    String reason;
                                    if (currentInspectionPassed) {
                                        reason = name + " success";
                                        setPauseStatus(false, reason, relasePausePassword);
                                    } else {
                                        try {
                                            reason = BeanUtil.toJson(errs, true, true);
                                        } catch (Throwable ex) {
                                            reason = name + " failed " + ex;
                                        }
                                        setPauseStatus(true, reason, relasePausePassword);
                                    }
                                }
                                case HealthCheck -> {
                                    if (currentInspectionPassed) {
                                        //sb.append(name).append(" passed");
                                    } else {
                                        healthCheckAllPassed = false;
                                        Level level = healthInspector.logLevel();
                                        if (level != null && log.isEnabled(level)) {
                                            healthCheckFailedReport.addErrors(errs);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ex) {
                            healthInspectorQueue.offer(healthInspector);
                            log.error("HealthInspector error: " + name, ex);
                        }
                    }
                    String inspectionReport;
                    if (healthCheckAllPassed) {
                        inspectionReport = "All health inspectors passed";
                    } else {
                        try {
                            inspectionReport = BeanUtil.toJson(healthCheckFailedReport, true, true);
                        } catch (Throwable ex) {
                            inspectionReport = " toJson failed " + ex;
                        }
                    }
                    setHealthStatus(healthCheckAllPassed, inspectionReport);
                    // wait
                    TimeUnit.SECONDS.sleep(inspectionIntervalSeconds);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    log.error("HealthMonitor interrupted", ex);
                }
            } while (keepRunning);
        };
        tpe.execute(asyncTask);
    }

    public static void inspect() {
        registeredHealthInspectors.forEach(healthInspectorQueue::offer);
    }

    public static void inspect(HealthInspector... healthInspectors) {
        if (healthInspectors == null || healthInspectors.length == 0) {
            return;
        }
        for (HealthInspector healthInspector : healthInspectors) {
            if (healthInspector == null) {
                continue;
            }
            healthInspectorQueue.add(healthInspector);
        }
    }

    public static void shutdown() {
        keepRunning = false;
        tpe.shutdown();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    HealthMonitor.shutdown();
                }, "HealthMonitor.shutdownHook")
        );
    }

    protected static volatile Boolean isHealthCheckSuccess = null;
    protected static volatile Boolean isServicePaused = null;
    protected static volatile String statusReasonHealthCheck;
    protected static volatile String statusReasonPaused;
    protected static volatile String statusReasonLastKnown;
    protected static volatile Set<String> relasePausePasswords = new HashSet<>();

    public static void setHealthStatus(boolean newStatus, String reason, HealthInspector... healthInspectors) {
        boolean serviceStatusChanged = isHealthCheckSuccess == null || isHealthCheckSuccess ^ newStatus;
        isHealthCheckSuccess = newStatus;
        statusReasonHealthCheck = reason;
        updateServiceStatus(serviceStatusChanged, reason);

        if (!isHealthCheckSuccess) {
            if (healthInspectors != null && healthInspectors.length > 0) {
                inspect(healthInspectors);
            } else {
                inspect();// use default health inspectors
            }
        }
    }

    public static void setPauseStatus(boolean pauseService, String reason, String relasePausePassword) {
        // check lock
        if (relasePausePassword == null) {
            relasePausePassword = "";
        }
        if (pauseService) {
            relasePausePasswords.add(relasePausePassword);
        } else {
            relasePausePasswords.remove(relasePausePassword);
            int size = relasePausePasswords.size();
            if (size > 0) {// keep paused by other reasons with different passwords
                pauseService = true;
                reason += ", still paused by other " + size + " reason(s) with different password(s)";
            }
        }
        boolean serviceStatusChanged = isServicePaused == null || isServicePaused ^ pauseService;
        isServicePaused = pauseService;
        statusReasonPaused = reason;
        updateServiceStatus(serviceStatusChanged, reason);
    }

    protected static void updateServiceStatus(boolean serviceStatusChanged, String reason) {
        statusReasonLastKnown = reason;
        log.warn(buildMessage());
        if (!serviceStatusChanged) {
            return;
        }
        if (appLifecycleListener != null) {
            appLifecycleListener.onApplicationStatusUpdated(isHealthCheckSuccess, isServicePaused, serviceStatusChanged, reason);
        }
    }

    public static String buildMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(BootConstant.BR)
                .append("\t Self Inspection Result: ").append(isHealthCheckSuccess != null && isHealthCheckSuccess ? "passed" : "failed").append(BootConstant.BR)
                .append("\t\t cause: ").append(statusReasonHealthCheck).append(BootConstant.BR)
                .append("\t Service Status: ").append(isServicePaused != null && isServicePaused ? "paused" : "running").append(BootConstant.BR)
                .append("\t\t cause: ").append(statusReasonPaused).append(BootConstant.BR);
        return sb.toString();
    }

    public static boolean isServicePaused() {
        return isServicePaused;
    }

    public static String getStatusReasonPaused() {
        return statusReasonPaused;
    }

    public static boolean isHealthCheckSuccess() {
        return isHealthCheckSuccess;
    }

    public static String getStatusReasonHealthCheck() {
        return statusReasonHealthCheck;
    }

    public static boolean isServiceAvaliable() {
        return isHealthCheckSuccess && !isServicePaused;
    }

    public static String getServiceStatusReason() {
        return statusReasonLastKnown;
    }
}
