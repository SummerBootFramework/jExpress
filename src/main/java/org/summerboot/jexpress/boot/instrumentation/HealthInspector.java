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
package org.summerboot.jexpress.boot.instrumentation;

import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.nio.server.domain.Err;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @param <T> parameter
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface HealthInspector<T extends Object> extends Comparable<Object> {

    AtomicLong retryIndex = new AtomicLong(0);

    List<Err> ping(T... param);

    default InspectionType inspectionType() {
        return InspectionType.HealthCheck;
    }

    /**
     * @return null to disable logging
     */
    default Level logLevel() {
        return Level.WARN;
    }

    default String pauseLockCode() {
        return this.getClass().getName();
    }

    enum InspectionType {
        HealthCheck, PauseCheck
    }

    //@Override Comparable
    default int compareTo(Object o) {
        return this.getClass().getName().compareTo(o.getClass().getName());
    }
}
