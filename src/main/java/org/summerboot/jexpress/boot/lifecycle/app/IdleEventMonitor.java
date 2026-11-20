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
package org.summerboot.jexpress.boot.lifecycle.app;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.util.format.FormatterUtil;
import org.summerboot.jexpress.util.time.TimeUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * IdleEventMonitor is a utility class to track the last request timestamp and loose transaction ID.
 * It can be used to monitor idle states and trigger events when a request has not been received within a specified threshold.
 * This class is thread-safe and can be used in a multi-threaded environment.
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class IdleEventMonitor {

    protected static final Logger log = LogManager.getLogger(IdleEventMonitor.class);

    // Stores ONLY real business access records (never overwritten by internal timer resets)
    public record CallState(long wallClockMillis, String transactionId) {
    }

    private enum State {RUNNING, PAUSED}

    private static final Map<IdleEventMonitor, State> stateMap = new ConcurrentHashMap<>();
    private static final Map<IdleEventMonitor, Thread> threadMap = new ConcurrentHashMap<>();

    // Register a JVM shutdown hook to gracefully stop all active monitors on exit
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutting down, stopping all IdleEventMonitors...");
            stopAll();
        }, "idle-monitor-shutdown"));
    }

    // 1. Dedicated monotonic timer for idle countdown (updated by both onCall and internal resets)
    private final AtomicLong lastTimerNano = new AtomicLong(System.nanoTime());

    // 2. Dedicated record for real user calls (updated ONLY by onCall; null until first real request)
    private final AtomicReference<CallState> lastCallState = new AtomicReference<>(null);

    private final String name;
    private final AtomicLong requestSequence = new AtomicLong(0);

    public interface IdleEventListener {
        void onIdle(IdleEventMonitor idleEventMonitor) throws Exception;
    }

    public IdleEventMonitor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void onCall() {
        onCall(null);
    }

    /**
     * Records a real business request: updates both the actual access record and the idle timer.
     */
    public void onCall(String transactionId) {
        long seq = requestSequence.incrementAndGet();
        String txId = (transactionId == null ? "" : transactionId) + " @" + name + "#" + seq;
        long nowNano = System.nanoTime();
        long nowMillis = System.currentTimeMillis();

        // Update real access record first, then reset the countdown timer
        this.lastCallState.set(new CallState(nowMillis, txId));
        this.lastTimerNano.set(nowNano);
    }

    /**
     * Internal helper: resets only the countdown timer without touching real user access records.
     */
    private void resetTimer() {
        this.lastTimerNano.set(System.nanoTime());
    }

    public String getLastTransactionId() {
        CallState state = lastCallState.get();
        return state != null ? state.transactionId() : null;
    }

    public long getLastTimestamp() {
        CallState state = lastCallState.get();
        return state != null ? state.wallClockMillis() : 0L;
    }

    public long getWaitMillis(long thresholdMillis) {
        return getWaitMillis(thresholdMillis, lastTimerNano.get());
    }

    private long getWaitMillis(long thresholdMillis, long timerNanoSnapshot) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - timerNanoSnapshot);
        return thresholdMillis - elapsedMillis;
    }

    public abstract long getIdleIntervalMillis();


    private static final String DEFAULT_REPORT_TEMPLATE = """
            ==================================================
            %s
            Monitor Name     : %s
            Report Time      : %s
            Idle Threshold   : %s seconds
            Last Access Time : %s
            Last Access By   : %s
            ==================================================
            """;

    /**
     * Generates an idle event report using default title, default timezone, and default template.
     */
    public String report() {
        return report(null, TimeUtil.ZONE_ID_ONTARIO, DEFAULT_REPORT_TEMPLATE);
    }

    /**
     * Generates an idle event report with a custom title, using default timezone and default template.
     */
    public String report(String reportTitle) {
        return report(reportTitle, TimeUtil.ZONE_ID_ONTARIO, DEFAULT_REPORT_TEMPLATE);
    }

    /**
     * Generates an idle event report using default title, specified ZoneId, and default template.
     */
    public String report(ZoneId zoneId) {
        return report(null, zoneId, DEFAULT_REPORT_TEMPLATE);
    }

    /**
     * Generates an idle event report with a custom title and timezone ID string, using default template.
     */
    public String report(String reportTitle, String timeZone) {
        return report(reportTitle, TimeUtil.parseZoneId(timeZone), DEFAULT_REPORT_TEMPLATE);
    }

    /**
     * Generates an idle event report with a custom title and ZoneId, using default template.
     */
    public String report(String reportTitle, ZoneId zoneId) {
        return report(reportTitle, zoneId, DEFAULT_REPORT_TEMPLATE);
    }

    /**
     * Generates an idle event report with a custom title, timezone ID string, and custom template.
     */
    public String report(String reportTitle, String timeZone, String reportTemplate) {
        return report(reportTitle, TimeUtil.parseZoneId(timeZone), reportTemplate);
    }

    /**
     * Generates an idle event report using the specified title, ZoneId, and custom format template.
     *
     * @param reportTitle    Optional title prefix. Uses "[IDLE EVENT REPORT]" if null/blank,
     *                       otherwise "[<reportTitle> IDLE EVENT REPORT]".
     * @param zoneId         Target ZoneId. Falls back to America/Toronto if null.
     * @param reportTemplate Custom format string accepting 6 string arguments (%s):
     *                       (1) formattedTitle, (2) monitorName, (3) formattedReportTime,
     *                       (4) formattedIdleSeconds, (5) formattedLastAccessTime, (6) lastAccessBy.
     *                       Falls back to DEFAULT_REPORT_TEMPLATE if null/blank.
     */
    public String report(String reportTitle, ZoneId zoneId, String reportTemplate) {
        String formattedTitle = (reportTitle == null || reportTitle.isBlank())
                ? "[IDLE EVENT REPORT]"
                : "[" + reportTitle.trim() + " IDLE EVENT REPORT]";

        ZoneId targetZone = (zoneId != null) ? zoneId : TimeUtil.ZONE_ID_ONTARIO;
        String template = (reportTemplate != null && !reportTemplate.isBlank())
                ? reportTemplate
                : DEFAULT_REPORT_TEMPLATE;

        // Reuse the zoned formatter for both Report Time and Last Access Time
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(targetZone);
        String formattedReportTime = formatter.format(Instant.now());

        // Read the real user call record (never polluted by internal timer resets)
        CallState state = this.lastCallState.get();
        String formattedIdleSeconds = FormatterUtil.formatMillisAsSeconds(this.getIdleIntervalMillis());

        String formattedLastAccessTime;
        String lastAccessBy;

        if (state != null) {
            formattedLastAccessTime = formatter.format(Instant.ofEpochMilli(state.wallClockMillis()));
            lastAccessBy = state.transactionId();
        } else {
            formattedLastAccessTime = "N/A";
            lastAccessBy = "N/A (No requests since startup)";
        }

        return template.formatted(
                formattedTitle,
                this.name,
                formattedReportTime,
                formattedIdleSeconds,
                formattedLastAccessTime,
                lastAccessBy
        );
    }


    /**
     * Starts monitoring idle events for the given monitor instance using a virtual thread.
     */
    public static void start(final IdleEventMonitor monitor, final IdleEventListener listener) {
        Objects.requireNonNull(monitor, "IdleEventMonitor cannot be null");
        Objects.requireNonNull(listener, "IdleEventListener cannot be null");

        threadMap.computeIfAbsent(monitor, m -> {
            // Reset ONLY the countdown timer before starting; preserve any existing real CallState
            m.resetTimer();
            stateMap.put(m, State.RUNNING);

            return Thread.startVirtualThread(() -> {
                log.info("IdleEventMonitor.start: {}", m.getName());
                try {
                    while (!Thread.currentThread().isInterrupted() && stateMap.containsKey(m)) {
                        try {
                            // Park indefinitely without consuming CPU while paused
                            if (stateMap.get(m) == State.PAUSED) {
                                LockSupport.park();
                                continue;
                            }

                            long idleIntervalMillis = m.getIdleIntervalMillis();
                            if (idleIntervalMillis < 1) {
                                long reloadMillis = TimeUnit.SECONDS.toMillis(BackOffice.agent.getIdleConfig0ReloadIntervalSec());
                                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(reloadMillis));
                                continue;
                            }

                            long ttlMillis = m.getWaitMillis(idleIntervalMillis);
                            if (ttlMillis > 0) {
                                // Park for the remaining idle duration; can be woken immediately by pause/stop
                                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(ttlMillis));
                                continue;
                            }

                            // Capture an atomic snapshot of the countdown timer
                            long timerSnapshot = m.lastTimerNano.get();

                            // Double-check against the exact timer snapshot before firing
                            // Prevents false triggers if onCall() updated lastState right before this line.
                            if (m.getWaitMillis(idleIntervalMillis, timerSnapshot) > 0) {
                                continue;
                            }

                            CallState realCall = m.lastCallState.get();
                            log.info("IdleEventMonitor.onIdle: {}, lastTxId={}, lastTS={}",
                                    m.getName(),
                                    realCall != null ? realCall.transactionId() : "N/A",
                                    realCall != null ? TimeUtil.toOffsetDateTime(realCall.wallClockMillis(), null) : "N/A");

                            listener.onIdle(m);

                            // Reset ONLY the timer if no new call arrived during listener execution
                            m.lastTimerNano.compareAndSet(timerSnapshot, System.nanoTime());

                        } catch (Exception ex) {
                            CallState realCall = m.lastCallState.get();
                            log.error("IdleEventMonitor.exception: {}, lastTxId={}, lastTS={}",
                                    m.getName(),
                                    realCall != null ? realCall.transactionId() : "N/A",
                                    realCall != null ? TimeUtil.toOffsetDateTime(realCall.wallClockMillis(), null) : "N/A",
                                    ex);
                            // Reset ONLY the timer on exception
                            m.resetTimer();
                        }
                    }
                } finally {
                    // Remove only if the map still points to this exact thread instance
                    threadMap.remove(m, Thread.currentThread());
                    log.info("IdleEventMonitor.shutdown: {}", m.getName());
                }
            });
        });
    }

    /**
     * Pauses monitoring and parks the underlying virtual thread immediately.
     */
    public static void pause(IdleEventMonitor monitor) {
        if (monitor != null && stateMap.replace(monitor, State.RUNNING, State.PAUSED)) {
            log.info("IdleEventMonitor.paused: {}", monitor.getName());
            LockSupport.unpark(threadMap.get(monitor));
        }
    }

    /**
     * Resumes monitoring, resets the idle timer, and unparks the virtual thread immediately.
     *
     * @param monitor
     */
    public static void resume(IdleEventMonitor monitor) {
        if (monitor != null && stateMap.replace(monitor, State.PAUSED, State.RUNNING)) {
// Reset ONLY the countdown timer on resume; preserve real lastCallState
            monitor.resetTimer();
            log.info("IdleEventMonitor.resumed: {}", monitor.getName());
            LockSupport.unpark(threadMap.get(monitor));
        }
    }

    /**
     * Permanently stops and cleans up a single monitor instance.
     *
     * @param monitor
     */
    public static void stop(IdleEventMonitor monitor) {
        if (monitor == null) {
            return;
        }
        stateMap.remove(monitor);
        Thread thread = threadMap.remove(monitor);
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    /**
     * Stops all active monitor instances. Automatically triggered by the JVM shutdown hook.
     */
    public static void stopAll() {
        for (IdleEventMonitor monitor : new ArrayList<>(stateMap.keySet())) {
            stop(monitor);
        }
    }
}