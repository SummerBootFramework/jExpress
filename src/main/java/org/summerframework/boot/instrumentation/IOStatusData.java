/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.boot.instrumentation;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class IOStatusData {

    final public long hps;
    final public long tps;
    final public long totalHit;
    final public long pingHit;
    final public long bizHit;
    final public long totalChannel;
    final public long totalChannelActive;
    final public long task;
    final public long completed;
    final public long queue;
    final public long active;
    final public long pool;
    final public long core;
    final public long max;
    final public long largest;

    public IOStatusData(long hps, long tps, long totalHit, long pingHit, long bizHit, long totalChannel, long totalChannelActive, long task, long completed, long queue, long active, long pool, long core, long max, long largest) {
        this.hps = hps;
        this.tps = tps;
        this.totalHit = totalHit;
        this.pingHit = pingHit;
        this.bizHit = bizHit;
        this.totalChannel = totalChannel;
        this.totalChannelActive = totalChannelActive;
        this.task = task;
        this.completed = completed;
        this.queue = queue;
        this.active = active;
        this.pool = pool;
        this.core = core;
        this.max = max;
        this.largest = largest;
    }

    @Override
    public String toString() {
        return "IOStatusData{" + "hps=" + hps + ", tps=" + tps + ", totalHit=" + totalHit + ", pingHit=" + pingHit + ", bizHit=" + bizHit + ", totalChannel=" + totalChannel + ", totalChannelActive=" + totalChannelActive + ", task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest + '}';
    }

}
