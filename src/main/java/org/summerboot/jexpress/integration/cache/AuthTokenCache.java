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
package org.summerboot.jexpress.integration.cache;

import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.nio.server.domain.Err;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface AuthTokenCache extends HealthInspector {

    @Override
    default List<Err> ping(Object... params) {
        Err e = null;
        try {
            String key = "jwt123";
            blacklist(key, "uid123", 1000);
            boolean isOnBlacklist = isBlacklist(key);
            if (!isOnBlacklist) {
                e = new Err(BootErrorCode.ACCESS_ERROR_CACHE, null, "Cache Data Error - failed to read", null, null);
            }
            TimeUnit.MILLISECONDS.sleep(1500);
            isOnBlacklist = isBlacklist(key);
            if (isOnBlacklist) {
                e = new Err(BootErrorCode.ACCESS_ERROR_CACHE, null, "Cache Access Error - failed to expire", null, null);
            }
        } catch (Throwable ex) {
            e = new Err(BootErrorCode.ACCESS_ERROR_CACHE, null, "Cache Access Error - " + ex.toString(), ex, null);
        }
        List<Err> errors = null;
        if (e != null) {
            errors = new ArrayList<>();
            errors.add(e);
        }
        return errors;
    }

    void blacklist(String key, String value, long ttlMilliseconds);

    boolean isBlacklist(String key);
}
