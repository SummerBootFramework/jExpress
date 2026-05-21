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
package org.summerboot.jexpress.boot.instrumentation.jmx;

import org.summerboot.jexpress.boot.instrumentation.IOStatusData;
import org.summerboot.jexpress.util.BeanUtil;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BootIOStatusData extends IOStatusData {

    public final String name;
    public final String ts;

    public BootIOStatusData(String ts, String name, long hps, long tps, long totalHit, long pingHit, long bizHit, long totalChannel, long activeChannel, long task, long completed, long queue, long active, long pool, long core, long max, long largest) {
        super(hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
        this.ts = ts;
        this.name = name;
    }

    @Override
    public String toString() {
        //return "[" + ts + "] " + super.toString();
        try {
            return BeanUtil.toJson(this);
        } catch (RuntimeException ex) {
            return ex.toString();
        }
    }
}
