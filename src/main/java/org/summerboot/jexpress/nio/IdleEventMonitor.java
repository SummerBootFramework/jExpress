package org.summerboot.jexpress.nio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
public class IdleEventMonitor {

    private final AtomicLong lastTimestamp = new AtomicLong(0);
    private final AtomicReference<String> lastTransactionId = new AtomicReference<>();
    private final String name;

    public IdleEventMonitor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void update(String transactionId) {
        lastTimestamp.set(System.currentTimeMillis());
        lastTransactionId.set(transactionId);
    }

    public void update(long timestamp, String transactionId) {
        lastTimestamp.set(timestamp);
        lastTransactionId.set(transactionId);
    }

    public String getLastTransactionId() {
        return lastTransactionId.get();
    }

    public long getLastTimestamp() {
        return lastTimestamp.get();
    }

    public long getTTLMillis(long threshold, TimeUnit timeUnit) {
        long thresholdMillis = timeUnit.toMillis(threshold);
        return getTTLMillis(thresholdMillis);
    }

    public long getTTLMillis(long thresholdMillis) {
        return thresholdMillis - (System.currentTimeMillis() - lastTimestamp.get());
    }

    public boolean isTimeout(long threshold, TimeUnit timeUnit) {
        long thresholdMillis = timeUnit.toMillis(threshold);
        return isTimeout(thresholdMillis);
    }

    public boolean isTimeout(long thresholdMillis) {
        return System.currentTimeMillis() - lastTimestamp.get() > thresholdMillis;
    }


    protected static final Logger log = LogManager.getLogger(IdleEventMonitor.class.getName());

    private static final Map<IdleEventMonitor, Boolean> statusMap = new ConcurrentHashMap<>();

    public static interface IdleEventListener {
        void onIdle(IdleEventMonitor idleEventMonitor) throws Exception;
    }

    public static void start(final IdleEventMonitor idleEventMonitor, final IdleEventListener idleEventListener, long threshold, TimeUnit timeUnit) throws Exception {
        if (idleEventMonitor == null) {
            throw new IllegalArgumentException("Request tracker cannot be null");
        }
        idleEventListener.onIdle(idleEventMonitor);
        Thread vThread = Thread.startVirtualThread(() -> {
            log.info("IdleEventMonitor.start: " + idleEventMonitor.getName());
            do {
                try {
                    long ttlMillis = idleEventMonitor.getTTLMillis(threshold, timeUnit);
                    if (ttlMillis >= 0) {
                        Thread.sleep(ttlMillis);
                        continue;
                    }
                    log.info("IdleEventMonitor.onIdle: " + idleEventMonitor.getName() + ", lastTxId=" + idleEventMonitor.getLastTransactionId() + ", lastTS=" + TimeUtil.toOffsetDateTime(idleEventMonitor.getLastTimestamp(), null));
                    idleEventListener.onIdle(idleEventMonitor);
                    idleEventMonitor.update(idleEventMonitor.getName());
                } catch (InterruptedException ex) {
                    log.error("IdleEventMonitor.interrupted: " + idleEventMonitor.getName(), ex);
                } catch (Throwable ex) {
                    log.error("IdleEventMonitor.exception: " + idleEventMonitor.getName(), ex);
                }
            } while (statusMap.getOrDefault(idleEventMonitor, true));
            log.info("IdleEventMonitor.shutdown: " + idleEventMonitor.getName());
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
