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
package org.summerboot.jexpress.common.util.concurrent;

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
        return useVirtualThread
                ? Thread.ofVirtual().name(namePrefix, 0).factory() // Java 21+ only
                : new NamedDefaultThreadFactory(namePrefix);
    }
}
