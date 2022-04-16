package org.summerframework.nio.grpc;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class Counter {

    protected final AtomicLong ping = new AtomicLong(0);
    protected final AtomicLong biz = new AtomicLong(0);
    protected final AtomicLong hit = new AtomicLong(0);
    protected final AtomicLong processed = new AtomicLong(0);

    public long getPing() {
        return ping.get();
    }

    public long incrementPing() {
        return ping.incrementAndGet();
    }

    public long getBiz() {
        return biz.get();
    }

    public long incrementBiz() {
        return biz.incrementAndGet();
    }

    public long getHit() {
        return hit.get();
    }

    public long incrementHit() {
        return hit.incrementAndGet();
    }

    public long getHitAndReset() {
        return hit.getAndSet(0);
    }

    public long getProcessed() {
        return processed.get();
    }

    public long incrementProcessed() {
        return processed.incrementAndGet();
    }

    public long getProcessedAndReset() {
        return processed.getAndSet(0);
    }

}
