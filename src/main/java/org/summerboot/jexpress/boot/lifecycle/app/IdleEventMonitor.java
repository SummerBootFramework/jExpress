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
import org.summerboot.jexpress.util.time.TimeUtil;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    public record CallState(long nanoTime, long wallClockMillis, String transactionId) {
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

    private final AtomicReference<CallState> lastState = new AtomicReference<>(
            new CallState(System.nanoTime(), System.currentTimeMillis(), null)
    );
    private final String name;

    public interface IdleEventListener {
        void onIdle(IdleEventMonitor idleEventMonitor) throws Exception;
    }

    public IdleEventMonitor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void onCall(String transactionId) {
        this.lastState.set(new CallState(System.nanoTime(), System.currentTimeMillis(), transactionId));
    }

    public String getLastTransactionId() {
        return lastState.get().transactionId();
    }

    public long getLastTimestamp() {
        return lastState.get().wallClockMillis();
    }

    public long getWaitMillis(long thresholdMillis) {
        return getWaitMillis(thresholdMillis, lastState.get());
    }

    private long getWaitMillis(long thresholdMillis, CallState state) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.nanoTime());
        return thresholdMillis - elapsedMillis;
    }

    public abstract long getIdleIntervalMillis();

    /**
     * Starts monitoring idle events for the given monitor instance using a virtual thread.
     */
    public static void start(final IdleEventMonitor monitor, final IdleEventListener listener) {
        Objects.requireNonNull(monitor, "IdleEventMonitor cannot be null");
        Objects.requireNonNull(listener, "IdleEventListener cannot be null");


        threadMap.computeIfAbsent(monitor, m -> {
            // Initialize state and reset timer BEFORE starting the virtual thread
            // so immediate calls to pause() or stopAll() right after start() take effect reliably.
            m.onCall(m.getName());
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

                            // Capture an atomic snapshot of the current state
                            CallState snapshot = m.lastState.get();

                            // Double-check against the exact snapshot before firing the event.
                            // Prevents false triggers if onCall() updated lastState right before this line.
                            if (m.getWaitMillis(idleIntervalMillis, snapshot) > 0) {
                                continue;
                            }

                            log.info("IdleEventMonitor.onIdle: {}, lastTxId={}, lastTS={}",
                                    m.getName(), snapshot.transactionId(), TimeUtil.toOffsetDateTime(snapshot.wallClockMillis(), null));

                            listener.onIdle(m);

                            // Reset the timer only if no new call arrived during listener execution
                            m.lastState.compareAndSet(snapshot, new CallState(System.nanoTime(), System.currentTimeMillis(), m.getName()));

                        } catch (Exception ex) {
                            log.error("IdleEventMonitor.exception: {}, lastTxId={}, lastTS={}",
                                    m.getName(), m.getLastTransactionId(), TimeUtil.toOffsetDateTime(m.getLastTimestamp(), null), ex);
                            // Reset the timer on exception to prevent tight-loop log flooding
                            m.onCall(m.getName());
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
     */
    public static void resume(IdleEventMonitor monitor) {
        if (monitor != null && stateMap.replace(monitor, State.PAUSED, State.RUNNING)) {
            monitor.onCall(monitor.getName());
            log.info("IdleEventMonitor.resumed: {}", monitor.getName());
            LockSupport.unpark(threadMap.get(monitor));
        }
    }

    /**
     * Permanently stops and cleans up a single monitor instance.
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