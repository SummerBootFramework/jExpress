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

import com.google.inject.Injector;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.annotation.Inspector;
import org.summerboot.jexpress.boot.annotation.Service;
import org.summerboot.jexpress.boot.event.AppLifecycleListener;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

    /*
     * controller variables
     */
    protected static volatile AppLifecycleListener appLifecycleListener;
    protected static final ExecutorService tpe = Executors.newSingleThreadExecutor();
    protected static final LinkedBlockingQueue<HealthInspector> healthInspectorQueue = new LinkedBlockingQueue<>();
    protected static final Set<HealthInspector> registeredHealthInspectors = new HashSet<>();
    private static boolean keepRunning = false;
    private static volatile boolean started = false;

    /*
     * status variables
     */
    protected static volatile boolean isHealthCheckSuccess = true;
    protected static volatile boolean isServicePaused = false;
    protected static volatile String statusReasonHealthCheck;
    protected static volatile String statusReasonPaused;
    protected static volatile String statusReasonPausedForExternalCaller;
    protected static volatile String statusReasonLastKnown;
    protected static final Set<String> pauseReleaseCodes = new HashSet<>();
    protected static final Set<String> healthCheckFailedList = new HashSet<>();

    public static void setAppLifecycleListener(AppLifecycleListener listener) {
        appLifecycleListener = listener;
    }

    private static final String ANNOTATION = HealthInspector.class.getSimpleName();

    public static void registerDefaultHealthInspectors(Map<String, Object> defaultHealthInspectors, StringBuilder memo) {
        registeredHealthInspectors.clear();
        if (defaultHealthInspectors == null || defaultHealthInspectors.isEmpty()) {
            memo.append(BootConstant.BR).append("\t- @" + ANNOTATION + " registered: none");
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean error = false;
        for (Map.Entry<String, Object> entry : defaultHealthInspectors.entrySet()) {
            String name = entry.getKey();
            Object healthInspector = entry.getValue();
            if (healthInspector instanceof HealthInspector) {
                registeredHealthInspectors.add((HealthInspector) healthInspector);
                memo.append(BootConstant.BR).append("\t- @Inspector registered: ").append(name).append("=").append(healthInspector.getClass().getName());
            } else {
                error = true;
                sb.append(BootConstant.BR).append("\tCoding Error: class ").append(healthInspector.getClass().getName()).append(" has annotation @").append(Inspector.class.getSimpleName()).append(", should implement ").append(HealthInspector.class.getName());
            }
        }
        if (error) {
            log.fatal(BootConstant.BR + sb + BootConstant.BR);
            ApplicationUtil.RTO(BootErrorCode.RTO_CODE_ERROR_HM, null, null);
        }
    }

    /**
     * use default inspectors
     */
    public static int inspect() {
        registeredHealthInspectors.forEach(healthInspectorQueue::offer);
        return registeredHealthInspectors.size();
    }

    /**
     * @param healthInspectors use specified inspectors, if null or empty, use default inspectors
     */
    public static void inspect(HealthInspector... healthInspectors) {
        if (healthInspectors == null || healthInspectors.length == 0) {// use specified inspectors
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

    private static final Runnable AsyncTask = () -> {
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
                    String inspectorClassName = healthInspector.getClass().getName();
                    Inspector inspectorAnnotation = healthInspector.getClass().getAnnotation(Inspector.class);
                    final String inspectorName;
                    if (inspectorAnnotation != null && StringUtils.isNoneBlank(inspectorAnnotation.name())) {
                        inspectorName = inspectorAnnotation.name();
                    } else {
                        inspectorName = inspectorClassName;
                    }

                    try (var a = Timeout.watch(inspectorName + ".ping()", timeoutMs).withDesc(timeoutDesc)) {
                        HealthInspector.InspectionType inspectionType = healthInspector.inspectionType();
                        List<Err> errs = healthInspector.ping();
                        boolean currentInspectionPassed = errs == null || errs.isEmpty();
                        if (!currentInspectionPassed) {
                            healthInspectorQueue.offer(healthInspector);
                        }
                        switch (inspectionType) {
                            case PauseCheck -> {
                                String lockCode = healthInspector.pauseLockCode();
                                String reason;
                                if (currentInspectionPassed) {
                                    reason = inspectorName + " success";
                                    pauseService(false, lockCode, reason);
                                } else {
                                    try {
                                        reason = BeanUtil.toJson(errs, true, true);
                                    } catch (Throwable ex) {
                                        reason = inspectorName + " failed " + ex;
                                    }
                                    pauseService(true, lockCode, reason);
                                }
                            }
                            case HealthCheck -> {
                                if (currentInspectionPassed) {
                                    if (healthCheckAllPassed == null) {
                                        healthCheckAllPassed = true;
                                    } else {
                                        healthCheckAllPassed &= true;
                                    }
                                    healthCheckFailedList.remove(inspectorName);
                                } else {
                                    healthCheckAllPassed = false;
                                    healthCheckFailedReport.addErrors(errs);
                                    healthCheckFailedList.add(inspectorName);
                                        /*Level level = healthInspector.logLevel();
                                        if (level != null && log.isEnabled(level)) {
                                            healthCheckFailedReport.addErrors(errs);
                                        }*/
                                }
                            }
                        }
                    } catch (Throwable ex) {
                        healthInspectorQueue.offer(healthInspector);
                        log.error("HealthInspector error: " + inspectorName, ex);
                    }
                }
                if (healthCheckAllPassed != null) {
                    String inspectionReport;
                    if (healthCheckAllPassed) {
                        inspectionReport = "Current all health inspectors passed";
                        setHealthStatus(healthCheckAllPassed, inspectionReport);
                    } else {
                        try {
                            inspectionReport = BeanUtil.toJson(healthCheckFailedReport, true, true);
                        } catch (Throwable ex) {
                            inspectionReport = " toJson failed " + ex;
                        }
                        setHealthStatus(healthCheckAllPassed, inspectionReport);
                        long retryIndex = HealthInspector.retryIndex.get();// not being set yet
                        if (appLifecycleListener != null && started) {
                            appLifecycleListener.onHealthInspectionFailed(isHealthCheckSuccess, isServicePaused, retryIndex, inspectionIntervalSeconds);
                        }
                    }
                }
                started = true;

                // wait
                TimeUnit.SECONDS.sleep(inspectionIntervalSeconds);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.error("HealthMonitor interrupted", ex);
            }
        } while (keepRunning);
    };

    public static String start(boolean returnRsult, Injector guiceInjector) {
        if (keepRunning) {
            return "HealthMonitor is already running";
        }
        StringBuilder memo = new StringBuilder();
        boolean hasUnregistered = false;
        // 1. remove unused (via -use <implTag>) inspectors with @Service annotation
        Iterator<HealthInspector> iterator = registeredHealthInspectors.iterator();
        while (iterator.hasNext()) {
            HealthInspector healthInspector = iterator.next();
            Service serviceAnnotation = healthInspector.getClass().getAnnotation(Service.class);
            if (serviceAnnotation != null) {
                Class c = healthInspector.getClass();
                boolean usedByTag = false;
                Class[] bindingClasses = serviceAnnotation.binding();
                if (bindingClasses == null || bindingClasses.length < 1) {
                    bindingClasses = c.getInterfaces();
                }
                for (Class bindingClasse : bindingClasses) {
                    Object o = guiceInjector.getInstance(bindingClasse);
                    if (o.getClass().equals(c)) {
                        usedByTag = true;
                        break;
                    }
                }
                if (!usedByTag) {
                    hasUnregistered = true;
                    memo.append(BootConstant.BR).append("\t- @Inspector unused due to CLI argument -" + BootConstant.CLI_USE_ALTERNATIVE + " <alternativeNames>: ").append(c.getName());
                    iterator.remove();
                }
            }
        }
        if (hasUnregistered) {
            log.warn(memo);
        }

        // 2. start health monitor sync to return result
        String ret = null;
        if (returnRsult) {
            // start sync to get result
            int size = inspect();
            keepRunning = false;
            if (size > 0) {
                AsyncTask.run();
                ret = buildMessage();
            } else {
                ret = "No health inspectors registered";
            }
        }

        // 3. start async in background
        keepRunning = true;
        if (!isServiceAvailable()) {
            inspect();
        }
        tpe.execute(AsyncTask);

        // return sync result
        return ret;
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


    protected static void setHealthStatus(boolean newStatus, String reason) {
        boolean serviceStatusChanged = isHealthCheckSuccess ^ newStatus;
        isHealthCheckSuccess = newStatus;
        statusReasonHealthCheck = reason;
        updateServiceStatus(serviceStatusChanged, reason);
    }

    public static void pauseService(boolean pauseService, String lockCode, String reason) {
        boolean serviceStatusChanged = isServicePaused ^ pauseService;
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
                reason += ", still paused by other " + size + " reason(s) with different lock code(s)";
            }
        }
        //serviceStatusChanged = isServicePaused ^ pauseService;
        isServicePaused = pauseService;
        if (isServicePaused) {
            ServiceError se = new ServiceError(HealthMonitor.class.getSimpleName());
            Err error = new Err<>(BootErrorCode.SERVICE_PAUSED, null, lockCode, null);
            se.addError(error);
            statusReasonPausedForExternalCaller = se.toJson();
        }
        statusReasonPaused = reason;
        updateServiceStatus(serviceStatusChanged, reason);
    }


    protected static void updateServiceStatus(boolean serviceStatusChanged, String reason) {
        statusReasonLastKnown = reason;
        if (!serviceStatusChanged || !started) {
            return;
        }
        log.warn(buildMessage());// always warn for status changed
        if (appLifecycleListener != null) {
            appLifecycleListener.onApplicationStatusUpdated(isHealthCheckSuccess, isServicePaused, serviceStatusChanged, reason);
        }
    }

    public static String buildMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(BootConstant.BR)
                .append("Self Inspection Result: ").append(isHealthCheckSuccess ? "passed" : "failed: ").append(healthCheckFailedList).append(BootConstant.BR);
        if (!isHealthCheckSuccess) {
            sb.append("\t cause: ").append(statusReasonHealthCheck).append(BootConstant.BR);
        }
        sb.append("Service Status: ").append(isServicePaused ? "paused" : "running").append(BootConstant.BR)
                .append("\t cause: ").append(statusReasonPaused).append(BootConstant.BR);
        return sb.toString();
    }

    public static boolean isServicePaused() {
        return isServicePaused;
    }

    public static String getStatusReasonPaused() {
        return statusReasonPaused;
    }

    public static String getStatusReasonPausedForExternalCaller() {
        return statusReasonPausedForExternalCaller;
    }

    public static boolean isHealthCheckSuccess() {
        return isHealthCheckSuccess;
    }

    public static String getStatusReasonHealthCheck() {
        return statusReasonHealthCheck;
    }

    public static boolean isServiceAvailable() {
        return isHealthCheckSuccess && !isServicePaused;
    }

    public static String getServiceStatusReason() {
        return statusReasonLastKnown;
    }

    public static boolean isRequiredHealthChecksFailed(String[] requiredHealthChecks, EmptyHealthCheckPolicy mode) {
        return isRequiredHealthChecksFailed(requiredHealthChecks, mode, null);
    }

    public static Set<String> getFailedRequiredHealthChecks(String[] requiredHealthChecks, EmptyHealthCheckPolicy mode) {
        Set<String> ret = new HashSet<>();
        isRequiredHealthChecksFailed(requiredHealthChecks, mode, ret);
        return ret;
    }

    public enum EmptyHealthCheckPolicy {
        REQUIRE_ALL, REQUIRE_NONE
    }


    public static boolean isRequiredHealthChecksFailed(String[] requiredHealthChecks, EmptyHealthCheckPolicy mode, final Set<String> failedHealthChecks) {
        Set<String> set = new HashSet<>(Math.max((int) (requiredHealthChecks.length / 0.75f) + 1, 16));
        Collections.addAll(set, requiredHealthChecks);
        return isRequiredHealthChecksFailed(set, mode, failedHealthChecks);
    }

    public static boolean isRequiredHealthChecksFailed(Set<String> requiredHealthChecks, EmptyHealthCheckPolicy mode, final Set<String> failedHealthChecks) {
        if (failedHealthChecks != null) {
            failedHealthChecks.clear();
        }
        if (requiredHealthChecks == null || requiredHealthChecks.isEmpty()) {
            switch (mode) {
                case REQUIRE_ALL -> {
                    // if criticalHealthChecks is empty (default), that means requrie ALL HealthChecks, so return true if healthCheckFailedList is NOT empty
                    if (failedHealthChecks == null) {
                        return !healthCheckFailedList.isEmpty();
                    } else {
                        failedHealthChecks.addAll(healthCheckFailedList);
                    }
                }
                case REQUIRE_NONE -> {
                    // if criticalHealthChecks is empty (default), that means no requried HealthChecks, so return false
                    return false;
                }
            }
        } else {
            // if criticalHealthChecks is NOT empty (user specified), that means critical on only given HealthChecks, so return true if healthCheckFailedList contains any of the criticalHealthChecks
            for (String criticalHealthCheck : requiredHealthChecks) {
                if (healthCheckFailedList.contains(criticalHealthCheck)) {
                    if (failedHealthChecks == null) {
                        return true;
                    } else {
                        failedHealthChecks.add(criticalHealthCheck);
                    }
                }
            }
        }
        if (failedHealthChecks == null) {
            return false;
        } else {
            return !failedHealthChecks.isEmpty();
        }
    }
}
