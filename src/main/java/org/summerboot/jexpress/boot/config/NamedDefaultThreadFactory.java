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
        return useVirtualThread
                ? Thread.ofVirtual().name(namePrefix, 0).factory() // Java 21+ only
                : new NamedDefaultThreadFactory(namePrefix);
    }
}
