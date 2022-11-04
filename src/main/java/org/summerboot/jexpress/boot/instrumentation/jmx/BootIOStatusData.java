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
package org.summerboot.jexpress.boot.instrumentation.jmx;

import org.summerboot.jexpress.boot.instrumentation.IOStatusData;
import org.summerboot.jexpress.util.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 *
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
        } catch (JsonProcessingException ex) {
            return ex.toString();
        }
    }
}
