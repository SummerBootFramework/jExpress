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
package org.summerboot.jexpress.nio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.util.TimeUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * IdleEventMonitor is a utility class to track the last request timestamp and loose transaction ID.
 * It can be used to monitor idle states and trigger events when a request has not been received within a specified threshold.
 * This class is thread-safe and can be used in a multi-threaded environment.
 */
public abstract class IdleEventMonitor {

    private final AtomicLong lastTimestamp = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<String> lastTransactionId = new AtomicReference<>();
    private final String name;

    public IdleEventMonitor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void onCall(String lastTransactionId) {
        onCall(System.currentTimeMillis(), lastTransactionId);
    }

    public void onCall(long timestamp, String lastTransactionId) {
        this.lastTimestamp.set(timestamp);
        this.lastTransactionId.set(lastTransactionId);
    }

    public String getLastTransactionId() {
        return lastTransactionId.get();
    }

    public long getLastTimestamp() {
        return lastTimestamp.get();
    }

    public long getWaitMillis(long threshold, TimeUnit timeUnit) {
        return getWaitMillis(timeUnit.toMillis(threshold));
    }

    public long getWaitMillis(long thresholdMillis) {
        return thresholdMillis - (System.currentTimeMillis() - lastTimestamp.get());
    }

    public boolean isTimeout(long threshold, TimeUnit timeUnit) {
        return isTimeout(timeUnit.toMillis(threshold));
    }

    public boolean isTimeout(long thresholdMillis) {
        return System.currentTimeMillis() - lastTimestamp.get() > thresholdMillis;
    }


    protected static final Logger log = LogManager.getLogger(IdleEventMonitor.class.getName());

    private static final Map<IdleEventMonitor, Boolean> statusMap = new ConcurrentHashMap<>();

    public static interface IdleEventListener {
        void onIdle(IdleEventMonitor idleEventMonitor) throws Exception;
    }


    public abstract long getIdleIntervalMillis();

    public static void start(final IdleEventMonitor idleEventMonitor, final IdleEventListener idleEventListener) throws Exception {
        if (idleEventMonitor == null) {
            throw new IllegalArgumentException("Request tracker cannot be null");
        }
//        if (threshold < 1) {
//            log.warn("IdleEventMonitor ({}}) is disabled due to threshold = {}}", idleEventMonitor.getName(), threshold);
//            return;
//        }
        //idleEventListener.onIdle(idleEventMonitor);
        Thread.startVirtualThread(() -> {
            log.info("IdleEventMonitor.start: {}", idleEventMonitor.getName());
            do {
                try {
                    long idleIntervalMillis = idleEventMonitor.getIdleIntervalMillis();
                    if (idleIntervalMillis < 1) {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(BackOffice.agent.getIdleConfig0ReloadIntervalSec()));
                        continue;
                    }
                    long ttlMillis = idleEventMonitor.getWaitMillis(idleIntervalMillis);
                    if (ttlMillis >= 0) {
                        Thread.sleep(ttlMillis);
                        continue;
                    }
                    log.info("IdleEventMonitor.onIdle: {}, lastTxId={}, lastTS={}", idleEventMonitor.getName(), idleEventMonitor.getLastTransactionId(), TimeUtil.toOffsetDateTime(idleEventMonitor.getLastTimestamp(), null));
                    idleEventListener.onIdle(idleEventMonitor);
                    idleEventMonitor.onCall(idleEventMonitor.getName());
                } catch (InterruptedException ex) {
                    log.info("IdleEventMonitor.interrupted: {}, lastTxId={}, lastTS={}", idleEventMonitor.getName(), idleEventMonitor.getLastTransactionId(), TimeUtil.toOffsetDateTime(idleEventMonitor.getLastTimestamp(), null), ex);
                } catch (Throwable ex) {
                    log.info("IdleEventMonitor.exception: {}, lastTxId={}, lastTS={}", idleEventMonitor.getName(), idleEventMonitor.getLastTransactionId(), TimeUtil.toOffsetDateTime(idleEventMonitor.getLastTimestamp(), null), ex);
                }
            } while (statusMap.getOrDefault(idleEventMonitor, true));
            log.info("IdleEventMonitor.shutdown: {}", idleEventMonitor.getName());
        });
    }

    public static void stop(IdleEventMonitor idleEventMonitor) {
        if (idleEventMonitor == null) {
            return;
        }
        statusMap.put(idleEventMonitor, false);
    }

    public static void stop() {
        for (IdleEventMonitor tracker : statusMap.keySet()) {
            stop(tracker);
        }
        statusMap.clear();
    }
}
