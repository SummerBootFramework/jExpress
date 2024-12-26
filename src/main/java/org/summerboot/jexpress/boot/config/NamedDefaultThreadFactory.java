package org.summerboot.jexpress.boot.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedDefaultThreadFactory implements ThreadFactory {

    protected static final AtomicInteger poolNumber = new AtomicInteger(1);
    protected final ThreadGroup group;
    protected final AtomicInteger threadCounter = new AtomicInteger(1);
    protected final String namePrefix;

    private NamedDefaultThreadFactory(String tpeName) {
        group = Thread.currentThread().getThreadGroup();
        namePrefix = tpeName;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, namePrefix + threadCounter.getAndIncrement(), 0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }

    public static ThreadFactory build(String tpeName, boolean useVirtualThread) {
        String namePrefix = tpeName + "-"
                + poolNumber.getAndIncrement()
                + (useVirtualThread ? "-vt-" : "-pt-");
        return new NamedDefaultThreadFactory(namePrefix);
    }
}
