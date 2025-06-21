package org.summerboot.jexpress.nio;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class LooseRequestTracker {
    private final AtomicLong lastTimestamp = new AtomicLong(0);
    private final AtomicReference<String> lastTransactionId = new AtomicReference<>();

    public static final LooseRequestTracker BootTracker = new LooseRequestTracker();

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
        return System.currentTimeMillis() - lastTimestamp.get() - thresholdMillis;
    }

    public boolean isTimeout(long threshold, TimeUnit timeUnit) {
        long thresholdMillis = timeUnit.toMillis(threshold);
        return isTimeout(thresholdMillis);
    }

    public boolean isTimeout(long thresholdMillis) {
        return System.currentTimeMillis() - lastTimestamp.get() > thresholdMillis;
    }
}
