/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.integration;

import com.google.inject.Injector;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.annotation.Service;
import org.summerboot.jexpress.annotation.health.HealthCheck;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ServiceError;
import org.summerboot.jexpress.api.health.HealthChecker;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.lifecycle.app.AppLifecycleListener;
import org.summerboot.jexpress.infra.netty.config.NioConfig;
import org.summerboot.jexpress.util.concurrent.Timeout;
import org.summerboot.jexpress.util.lang.BeanUtil;
import org.summerboot.jexpress.util.runtime.ApplicationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class HealthMonitor {

    protected static final Logger log = LogManager.getLogger(HealthMonitor.class.getName());

    /*
     * api variables
     */
    protected static volatile AppLifecycleListener appLifecycleListener;
    protected static final ExecutorService tpe = Executors.newSingleThreadExecutor();
    protected static final LinkedBlockingQueue<HealthChecker> HEALTH_CHECKER_QUEUE = new LinkedBlockingQueue<>();
    protected static final Set<HealthChecker> REGISTERED_HEALTH_CHECKERS = new HashSet<>();
    private static boolean keepRunning = false;
    private static volatile boolean started = false;

    /*
     * status variables
     */
    protected static volatile boolean isHealthCheckSuccess = true;
    protected static volatile boolean isServicePaused = false;
    protected static volatile ServiceError statusReasonHealthCheck;
    protected static volatile ServiceError statusReasonPaused;
    protected static volatile String statusReasonLastKnown;
    protected static final Map<String, Err> pauseReleaseCodes = new ConcurrentHashMap<>();
    protected static final Map<String, List<Err>> failedHealthChecks = new ConcurrentHashMap<>();

    public static void setAppLifecycleListener(AppLifecycleListener listener) {
        appLifecycleListener = listener;
    }

    private static final String ANNOTATION = HealthChecker.class.getSimpleName();

    public static void registerDefaultHealthInspectors(Map<String, Object> annotatedHealthCheckers, StringBuilder memo) {
        REGISTERED_HEALTH_CHECKERS.clear();
        if (annotatedHealthCheckers == null || annotatedHealthCheckers.isEmpty()) {
            memo.append(BootConstants.BR).append("\t- @" + ANNOTATION + " registered: none");
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean error = false;
        for (Map.Entry<String, Object> entry : annotatedHealthCheckers.entrySet()) {
            String className = entry.getKey();
            Object annotatedHealthChecker = entry.getValue();
            if (annotatedHealthChecker instanceof HealthChecker) {
                REGISTERED_HEALTH_CHECKERS.add((HealthChecker) annotatedHealthChecker);
                memo.append(BootConstants.BR).append("\t- @Inspector registered: ").append(className).append("=").append(annotatedHealthChecker.getClass().getName());
            } else {
                error = true;
                sb.append(BootConstants.BR).append("\tCoding Error: class ").append(annotatedHealthChecker.getClass().getName()).append(" has annotation @").append(HealthCheck.class.getSimpleName()).append(", should implement ").append(HealthChecker.class.getName());
            }
        }
        if (error) {
            log.fatal(BootConstants.BR + sb + BootConstants.BR);
            ApplicationUtil.RTO(BootErrorCode.RTO_CODE_ERROR_HM, null, null);
        }
    }

    /**
     * use default inspectors
     */
    public static int inspect() {
        REGISTERED_HEALTH_CHECKERS.forEach(HEALTH_CHECKER_QUEUE::offer);
        return REGISTERED_HEALTH_CHECKERS.size();
    }

    /**
     * @param healthCheckers use specified inspectors, if null or empty, use default inspectors
     */
    public static void inspect(HealthChecker... healthCheckers) {
        if (healthCheckers == null || healthCheckers.length == 0) {// use specified inspectors
            inspect();
            return;
        }
        for (HealthChecker healthChecker : healthCheckers) {// use specified inspectors
            if (healthChecker == null) {
                continue;
            }
            HEALTH_CHECKER_QUEUE.add(healthChecker);
        }
    }


    private static SummerApplication.AppContext appContext;

    public static String start(SummerApplication.AppContext context, boolean returnRsult, Injector guiceInjector) {
        appContext = context;
        if (keepRunning) {
            return "HealthMonitor is already running";
        }
        StringBuilder memo = new StringBuilder();
        boolean hasUnregistered = false;
        // 1. remove unused (via -use <implTag>) inspectors with @Service annotation
        Iterator<HealthChecker> iterator = REGISTERED_HEALTH_CHECKERS.iterator();
        while (iterator.hasNext()) {
            HealthChecker healthChecker = iterator.next();
            Service serviceAnnotation = healthChecker.getClass().getAnnotation(Service.class);
            if (serviceAnnotation != null) {
                Class c = healthChecker.getClass();
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
                    memo.append(BootConstants.BR).append("\t- @Inspector unused due to CLI argument -" + BootConstants.CLI_USE_ALTERNATIVE + " <alternativeNames>: ").append(c.getName());
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

    private static final Runnable AsyncTask = () -> {
        int inspectionIntervalSeconds = NioConfig.cfg.getHealthInspectionIntervalSeconds();
        long timeoutMs = BackOffice.agent.getProcessTimeoutMilliseconds();
        String timeoutDesc = BackOffice.agent.getProcessTimeoutAlertMessage();
        final Set<HealthChecker> triggeredHealthCheckers = new TreeSet<>();
        AtomicLong retry = new AtomicLong(0);
        do {
            triggeredHealthCheckers.clear();
            Boolean allHealthCheckPassed = null;
            try {
                // take all health checkers from the queue, remove duplicated
                do {
                    HealthChecker triggeredHealthChecker = HEALTH_CHECKER_QUEUE.take();// block/wait here for health checkers
                    triggeredHealthCheckers.add(triggeredHealthChecker);
                } while (!HEALTH_CHECKER_QUEUE.isEmpty());
                // for each checker do health check
                for (HealthChecker triggeredHealthChecker : triggeredHealthCheckers) {
                    HealthCheck healthCheckAnnotation = triggeredHealthChecker.getClass().getAnnotation(HealthCheck.class);
                    final String triggeredHealthCheckerName;
                    if (healthCheckAnnotation != null && StringUtils.isNoneBlank(healthCheckAnnotation.name())) {
                        triggeredHealthCheckerName = healthCheckAnnotation.name();
                    } else {
                        triggeredHealthCheckerName = triggeredHealthChecker.getClass().getSimpleName();
                    }

                    try (var a = Timeout.watch(triggeredHealthCheckerName + ".ping()", timeoutMs).withDesc(timeoutDesc)) {
                        List<Err> errs = triggeredHealthChecker.ping();
                        boolean currentInspectionPassed = errs == null || errs.isEmpty();
                        if (!currentInspectionPassed) {
                            HEALTH_CHECKER_QUEUE.offer(triggeredHealthChecker); // put failed health check back into the queue
                        }
                        HealthChecker.InspectionType inspectionType = triggeredHealthChecker.inspectionType();
                        switch (inspectionType) {
                            case PauseCheck -> {
                                String lockCode = triggeredHealthChecker.pauseLockCode();
                                if (currentInspectionPassed) {
                                    //failedHealthChecks.remove(triggeredHealthCheckerName);
                                    pauseService(false, lockCode, triggeredHealthCheckerName, "health check success");
                                } else {
                                    //failedHealthChecks.put(triggeredHealthCheckerName, errs);
                                    pauseService(true, lockCode, triggeredHealthCheckerName, "health check failed with errs: " + BeanUtil.toJson(errs));
                                }
                            }
                            case HealthCheck -> {
                                if (currentInspectionPassed) {
                                    if (allHealthCheckPassed == null) {
                                        allHealthCheckPassed = true;
                                    } else {
                                        allHealthCheckPassed &= true;
                                    }
                                    failedHealthChecks.remove(triggeredHealthCheckerName);
                                } else {
                                    allHealthCheckPassed = false;
                                    failedHealthChecks.put(triggeredHealthCheckerName, errs);
                                }


                            }
                        }
                    } catch (Throwable ex) {
                        HEALTH_CHECKER_QUEUE.offer(triggeredHealthChecker);
                        log.error("Health check error: " + triggeredHealthCheckerName, ex);
                    }
                }
                // all done, summarize the results
                if (allHealthCheckPassed != null) {
                    setHealthCheckPassed(allHealthCheckPassed);

                    long retryValue = retry.get();// not being set yet
                    if (allHealthCheckPassed) {
                        retry.set(0);
                    } else {
                        retry.incrementAndGet();
                    }
                    if (appLifecycleListener != null && started) {
                        try {
                            appLifecycleListener.onHealthCheckFinished(appContext, isHealthCheckSuccess, isServicePaused, retryValue, inspectionIntervalSeconds);
                        } catch (Throwable ex) {
                            log.error("appLifecycleListener.onHealthInspectionFailed() error", ex);
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

    public static void pauseService(boolean pauseService, String lockCode, String triggeredHealthCheckerName, String reason) {
        boolean serviceStatusChanged = isServicePaused ^ pauseService;
        // check lock
        if (lockCode == null) {
            lockCode = "";
        }

        final String detailedReason;
        if (pauseService) {
            isServicePaused = true;// pause immediately once any lock code added, to make sure no new request will be processed before the service is paused
            Err err = new Err(BootErrorCode.SERVICE_PAUSED, triggeredHealthCheckerName, "Service paused due to " + reason + ", lockCode: " + lockCode, null);
            pauseReleaseCodes.put(lockCode, err);
            detailedReason = BootConstants.APP_ID + " Service paused by health checker (" + triggeredHealthCheckerName + "), lock code: " + lockCode;
            statusReasonPaused = new ServiceError(BootConstants.APP_ID + "-HealthMonitor");
            statusReasonPaused.addError(err);
        } else {
            pauseReleaseCodes.remove(lockCode);
            int size = pauseReleaseCodes.size();
            if (size > 0) {// keep paused by other reasons with different passwords
                isServicePaused = true;
                detailedReason = BootConstants.APP_ID + " Service remain paused by other " + size + " lock code(s), although just released by health checker (" + triggeredHealthCheckerName + "), lock code: " + lockCode;
                statusReasonPaused = new ServiceError(BootConstants.APP_ID + "-HealthMonitor");
                pauseReleaseCodes.values().forEach(err -> {
                    statusReasonPaused.addError(err);
                });
            } else {
                isServicePaused = false;
                detailedReason = BootConstants.APP_ID + " Service resumed by health checker (" + triggeredHealthCheckerName + "), lock code: " + lockCode + ", all lock codes released";
                statusReasonPaused = null;
            }
        }
        updateServiceStatus(serviceStatusChanged, detailedReason);
    }

    protected static void setHealthCheckPassed(boolean newStatus) {
        boolean serviceStatusChanged = isHealthCheckSuccess ^ newStatus;
        isHealthCheckSuccess = newStatus;
        if (newStatus) {
            failedHealthChecks.clear();
            updateServiceStatus(serviceStatusChanged, "Health check passed");
            return;
        }

        //failedHealthChecks.forEach((k, v) -> log.warn("Health check failed: " + k + ", errors: " + v));
        List<String> affectedServices = getAffectedServices();
        statusReasonHealthCheck = new ServiceError(BootConstants.APP_ID + "-HealthMonitor");
        statusReasonHealthCheck.adAdditionalField("affectedServices", affectedServices);
        failedHealthChecks.forEach((healthCheckerName, errors) -> {
            errors.forEach((error) -> {
                error.setErrorTag(healthCheckerName);
            });
            statusReasonHealthCheck.addErrors(errors);
        });
        updateServiceStatus(serviceStatusChanged, statusReasonHealthCheck.toJson());
    }

    protected static void updateServiceStatus(boolean serviceStatusChanged, String reason) {
        statusReasonLastKnown = reason;
        if (!serviceStatusChanged || !started) {
            return;
        }
        log.warn(buildMessage());// always warn for status changed
        if (appLifecycleListener != null) {
            try {
                appLifecycleListener.onApplicationStatusUpdated(appContext, isHealthCheckSuccess, isServicePaused, serviceStatusChanged, reason);
            } catch (Throwable ex) {
                log.error("appLifecycleListener.onApplicationStatusUpdated() error", ex);
            }
        }
    }

    public static String buildMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(BootConstants.BR)
                .append("Health Check: ").append(isHealthCheckSuccess ? "passed" : "failed: ").append(BootConstants.BR);
        if (!isHealthCheckSuccess) {
            sb.append("\t cause: ").append(statusReasonLastKnown).append(BootConstants.BR);
        }
        sb.append("Service Status: ").append(isServicePaused ? "paused" : "running").append(BootConstants.BR);
        if (isServicePaused) {
            sb.append("\t cause: ").append(statusReasonPaused == null ? "" : statusReasonPaused.toJson()).append(BootConstants.BR);
        }
        return sb.toString();
    }

    public static boolean isServicePaused() {
        return isServicePaused;
    }

    public static ServiceError getStatusReasonPaused() {
        return statusReasonPaused;
    }

    public static boolean isHealthCheckSuccess() {
        return isHealthCheckSuccess;
    }

    public static ServiceError getStatusReasonHealthCheck() {
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
        /**
         * Require all discovered checks, failing if none are explicitly bound.
         */
        REQUIRE_ALL,

        /**
         * Allow the application to start healthy even with no checks registered.
         */
        REQUIRE_NONE
    }


    public static boolean isRequiredHealthChecksFailed(String[] requiredHealthChecks, EmptyHealthCheckPolicy emptyHealthCheckPolicyo, final Set<String> failedHealthChecks) {
        Set<String> set = null;
        if (requiredHealthChecks != null && requiredHealthChecks.length > 0) {
            set = new HashSet<>(Math.max((int) (requiredHealthChecks.length / 0.75f) + 1, 16));
            Collections.addAll(set, requiredHealthChecks);
        }
        return isRequiredHealthChecksFailed(set, emptyHealthCheckPolicyo, failedHealthChecks);
    }

    public static boolean isRequiredHealthChecksFailed(Set<String> requiredHealthChecks, EmptyHealthCheckPolicy emptyHealthCheckPolicyo) {
        return isRequiredHealthChecksFailed(requiredHealthChecks, emptyHealthCheckPolicyo, null);
    }

    public static boolean isRequiredHealthChecksFailed(Set<String> requiredHealthChecks, EmptyHealthCheckPolicy emptyHealthCheckPolicy, final Set<String> failedHealthChecks) {
        if (failedHealthChecks != null) {
            failedHealthChecks.clear();
        }
        if (requiredHealthChecks == null || requiredHealthChecks.isEmpty()) {
            switch (emptyHealthCheckPolicy) {
                case REQUIRE_ALL -> {
                    // if criticalHealthChecks is empty (default), that means requrie ALL HealthChecks, so return true if healthCheckFailedList is NOT empty
                    if (failedHealthChecks == null) {
                        return !HealthMonitor.failedHealthChecks.isEmpty();
                    } else {
                        failedHealthChecks.addAll(failedHealthChecks);
                    }
                }
                case REQUIRE_NONE -> {
                    // if criticalHealthChecks is empty (default), that means no required HealthChecks, so return false
                    return false;
                }
            }
        } else {
            // if criticalHealthChecks is NOT empty (user specified), that means critical on only given HealthChecks, so return true if healthCheckFailedList contains any of the criticalHealthChecks
            for (String criticalHealthCheck : requiredHealthChecks) {
                if (failedHealthChecks.contains(criticalHealthCheck)) {
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

    protected static Map<String, Set<String>> affectedServices = new ConcurrentHashMap<>();

    private static final String ALL = EmptyHealthCheckPolicy.REQUIRE_ALL.name();

    public static void registerAffectedServices(HttpMethod httpMethod, String declaredUri, Set<String> healthChecks, EmptyHealthCheckPolicy emptyHealthCheckPolicy) {
        String registeredUri = httpMethod + " " + declaredUri;

        if (healthChecks == null || healthChecks.isEmpty()) {
            switch (emptyHealthCheckPolicy) {
                case REQUIRE_ALL -> {
                    affectedServices.computeIfAbsent(ALL, k -> new HashSet<>()).add(registeredUri);
                    return;
                }
                case REQUIRE_NONE -> {
                    return;
                }
            }
        }
        for (String healthCheck : healthChecks) {
            if (StringUtils.isEmpty(healthCheck)) {
                continue;
            }
            affectedServices.computeIfAbsent(healthCheck, k -> new HashSet<>()).add(registeredUri);
        }
    }

    private static final List<String> EMPTY_LIST = Collections.emptyList();

    public static List<String> getAffectedServices() {
        if (failedHealthChecks.isEmpty()) {
            return EMPTY_LIST;
        }
        List<String> currentAffectedServices = new ArrayList<>();
        Set<String> all = affectedServices.get(ALL);
        if (all != null) {
            currentAffectedServices.addAll(all);
        }
        for (String failedHealthCheck : failedHealthChecks.keySet()) {
            currentAffectedServices.addAll(affectedServices.get(failedHealthCheck));
        }
        // remove duplicated and sort by alphabetical order for better readability
        return currentAffectedServices.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }
}
