package org.summerboot.jexpress.boot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.util.TimeUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * RequestTracker is a utility class to track the last request timestamp and loose transaction ID.
 * It can be used to monitor idle states and trigger events when a request has not been received within a specified threshold.
 * This class is thread-safe and can be used in a multi-threaded environment.
 */
public class BootRequestTracker {

    private final AtomicLong lastTimestamp = new AtomicLong(0);
    private final AtomicReference<String> lastTransactionId = new AtomicReference<>();
    private final String name;

    public BootRequestTracker(String name) {
        this.name = name;
        lastTimestamp.set(System.currentTimeMillis());
        lastTransactionId.set(name);
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


    protected static final Logger log = LogManager.getLogger(BootRequestTracker.class.getName());

    private static final Map<BootRequestTracker, Boolean> statusMap = new ConcurrentHashMap<>();

    public static interface IdleEventListener {
        void onIdle(BootRequestTracker requestTracker);
    }

    public static void start(final BootRequestTracker requestTracker, final IdleEventListener idleEventListener, long threshold, TimeUnit timeUnit) {
        if (requestTracker == null) {
            throw new IllegalArgumentException("Request tracker cannot be null");
        }
        Thread vThread = Thread.startVirtualThread(() -> {
            log.info("BootRequestTracker.start: " + requestTracker.getName());
            do {
                try {
                    long ttlMillis = requestTracker.getTTLMillis(threshold, timeUnit);
                    if (ttlMillis >= 0) {
                        Thread.sleep(ttlMillis);
                        continue;
                    }
                    log.info("BootRequestTracker.onIdle: " + requestTracker.getName() + ", lastTxId=" + requestTracker.getLastTransactionId() + ", lastTS=" + TimeUtil.toOffsetDateTime(requestTracker.getLastTimestamp(), null));
                    idleEventListener.onIdle(requestTracker);
                    requestTracker.update(requestTracker.getName());
                } catch (InterruptedException ex) {
                    log.error("BootRequestTracker.interrupted: " + requestTracker.getName(), ex);
                } catch (Throwable ex) {
                    log.error("BootRequestTracker.exception: " + requestTracker.getName(), ex);
                }
            } while (statusMap.getOrDefault(requestTracker, true));
            log.info("BootRequestTracker.shutdown: " + requestTracker.getName());
        });
    }

    public static void stop(BootRequestTracker requestTracker) {
        if (requestTracker == null) {
            return;
        }
        statusMap.put(requestTracker, false);
    }

    public static void stop() {
        for (BootRequestTracker tracker : statusMap.keySet()) {
            stop(tracker);
        }
        statusMap.clear();
    }
}
