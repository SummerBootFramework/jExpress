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
package org.summerboot.jexpress.integration.cache;

import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.controller.Err;
import org.summerboot.jexpress.integration.HealthChecker;
import org.summerboot.jexpress.security.authenticate.Caller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface AuthTokenCache extends HealthChecker {

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

    /**
     * store it in redis with key "ws:ticket:" + oneTimeTicket, value = caller (or json string),
     *
     * @param key
     * @param caller
     * @param ttlMilliseconds
     */
    void oneTimeTokenPut(String key, Caller caller, long ttlMilliseconds);

    /**
     * call redis.getdel("ws:token:" + oneTimeToken)
     *
     * @param key
     * @return
     */
    Caller oneTimeTokenVerifyAndDestroy(String key);
}
