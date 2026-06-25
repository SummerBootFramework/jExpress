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
package org.summerboot.jexpress.infra.grpc.server;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
class GrpcServiceCounter {

    protected final AtomicLong ping = new AtomicLong(0);
    protected final AtomicLong biz = new AtomicLong(0);
    protected final LongAdder hit = new LongAdder();
    protected final LongAdder processed = new LongAdder();
    protected final LongAdder cancelled = new LongAdder();

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
        return hit.sum();
    }

    public void incrementHit() {
        hit.increment();
    }

    public long getHitAndReset() {
        return hit.sumThenReset();
    }

    public long getProcessed() {
        return processed.sum();
    }

    public void incrementProcessed() {
        processed.increment();
    }

    public long getProcessedAndReset() {
        return processed.sumThenReset();
    }

    public long getCancelled() {
        return cancelled.sum();
    }

    public void incrementCancelled() {
        cancelled.increment();
    }

}
