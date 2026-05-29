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
package org.summerboot.jexpress.integration;

import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.controller.Err;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @param <T> parameter
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface HealthChecker<T extends Object> extends Comparable<Object> {

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
        return this.getClass().getSimpleName();
    }

    enum InspectionType {
        HealthCheck, PauseCheck
    }

    //@Override Comparable
    default int compareTo(Object o) {
        return this.getClass().getName().compareTo(o.getClass().getName());
    }
}
