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
import org.summerboot.jexpress.boot.annotation.Inspector;
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
        StringBuilder sb = new StringBuilder();
        boolean error = false;
        for (Map.Entry<String, Object> entry : defaultHealthInspectors.entrySet()) {
            String name = entry.getKey();
            Object healthInspector = entry.getValue();
            if (healthInspector instanceof HealthInspector) {
                registeredHealthInspectors.add((HealthInspector) healthInspector);
                memo.append(BootConstant.BR).append("\t- @DefaultHealthInspector registered: ").append(name).append("=").append(healthInspector.getClass().getName());
            } else {
                error = true;
                sb.append(BootConstant.BR).append("\tCoding Error: class ").append(healthInspector.getClass().getName()).append(" has annotation @").append(Inspector.class.getSimpleName()).append(", should implement ").append(HealthInspector.class.getName());
            }
        }
        if (error) {
            log.fatal(BootConstant.BR + sb + BootConstant.BR);
            System.exit(2);
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
                Boolean healthCheckAllPassed = null;
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
                                    String lockCode = healthInspector.pauseLockCode();
                                    String reason;
                                    if (currentInspectionPassed) {
                                        reason = name + " success";
                                        setPauseStatus(false, reason, lockCode, null);
                                    } else {
                                        try {
                                            reason = BeanUtil.toJson(errs, true, true);
                                        } catch (Throwable ex) {
                                            reason = name + " failed " + ex;
                                        }
                                        setPauseStatus(true, reason, lockCode, null);
                                    }
                                }
                                case HealthCheck -> {
                                    if (currentInspectionPassed) {
                                        if (healthCheckAllPassed == null) {
                                            healthCheckAllPassed = true;
                                        } else {
                                            healthCheckAllPassed &= true;
                                        }
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
                    if (healthCheckAllPassed != null) {
                        String inspectionReport;
                        if (healthCheckAllPassed) {
                            inspectionReport = "Current all health inspectors passed";
                        } else {
                            try {
                                inspectionReport = BeanUtil.toJson(healthCheckFailedReport, true, true);
                            } catch (Throwable ex) {
                                inspectionReport = " toJson failed " + ex;
                            }
                            long retryIndex = HealthInspector.retryIndex.get();// not being set yet
                            if (appLifecycleListener != null) {
                                appLifecycleListener.onHealthInspectionFailed(isHealthCheckSuccess, isServicePaused, retryIndex, inspectionIntervalSeconds);
                            }
                        }
                        setHealthStatus(healthCheckAllPassed, inspectionReport, null);
                    }
                    log.warn(buildMessage());
                    // wait
                    if (!isServiceAvaliable()) {
                        log.warn("will check again in " + inspectionIntervalSeconds + " seconds");
                    }
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
        if (healthInspectors == null) {// no inspectors
            return;
        }
        if (healthInspectors.length == 0) {// use default inspectors
            inspect();
            return;
        }
        for (HealthInspector healthInspector : healthInspectors) {// use specified inspectors
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

    protected static volatile boolean isHealthCheckSuccess = true;
    protected static volatile boolean isServicePaused = false;
    protected static volatile boolean isServiceStatusChanged = false;
    protected static volatile String statusReasonHealthCheck;
    protected static volatile String statusReasonPaused;
    protected static volatile String statusReasonLastKnown;
    protected static volatile Set<String> pauseReleaseCodes = new HashSet<>();

    public static void setHealthStatus(boolean newStatus, String reason, HealthInspector... healthInspectors) {
        boolean serviceStatusChanged = isHealthCheckSuccess ^ newStatus;
        isHealthCheckSuccess = newStatus;
        statusReasonHealthCheck = reason;
        updateServiceStatus(serviceStatusChanged, reason);

        if (!isHealthCheckSuccess) {
            inspect(healthInspectors);
        }
    }

    public static void setPauseStatus(boolean pauseService, String reason, String lockCode, HealthInspector... healthInspectors) {
        // check lock
        if (lockCode == null) {
            lockCode = "";
        }
        if (pauseService) {
            pauseReleaseCodes.add(lockCode);
        } else {
            pauseReleaseCodes.remove(lockCode);
            int size = pauseReleaseCodes.size();
            if (size > 0) {// keep paused by other reasons with different passwords
                pauseService = true;
                reason += ", still paused by other " + size + " reason(s) with different password(s)";
            }
        }
        boolean serviceStatusChanged = isServicePaused ^ pauseService;
        isServicePaused = pauseService;
        statusReasonPaused = reason;
        updateServiceStatus(serviceStatusChanged, reason);

        if (isServicePaused) {
            inspect(healthInspectors);
        }
    }

    protected static void updateServiceStatus(boolean serviceStatusChanged, String reason) {
        if (!serviceStatusChanged) {
            return;
        }
        statusReasonLastKnown = reason;
        log.warn(buildMessage());
        if (appLifecycleListener != null) {
            appLifecycleListener.onApplicationStatusUpdated(isHealthCheckSuccess, isServicePaused, serviceStatusChanged, reason);
        }
    }

    public static String buildMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(BootConstant.BR)
                .append("\t Self Inspection Result: ").append(isHealthCheckSuccess ? "passed" : "failed").append(BootConstant.BR);
        if (!isHealthCheckSuccess) {
            sb.append("\t\t cause: ").append(statusReasonHealthCheck).append(BootConstant.BR);
        }
        sb.append("\t Service Status: ").append(isServicePaused ? "paused" : "running").append(BootConstant.BR)
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
