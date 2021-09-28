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
package org.summerframework.integration.cache;

import org.summerframework.integration.cache.domain.FlashSale;
import org.summerframework.nio.server.domain.Error;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.summerframework.boot.BootConstant;
import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.instrumentation.HealthInspector;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public interface BootCache extends HealthInspector {

    @Override
    default List<Error> ping(Object... params) {
        Error e = null;
        try {
            String key = "jwt123";
            putOnBlacklist(key, "uid123", 1000);
            boolean isOnBlacklist = isOnBlacklist(key);
            if (!isOnBlacklist) {
                e = new Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Data Error", "failed to read", null);
            }
            TimeUnit.MILLISECONDS.sleep(1500);
            isOnBlacklist = isOnBlacklist(key);
            if (isOnBlacklist) {
                e = new Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Access Error", "failed to expire", null);
            }
        } catch (Throwable ex) {
            e = new Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Access Error", ex.toString(), ex);
        }
        List<Error> errors = null;
        if (e != null) {
            errors = new ArrayList<>();
            errors.add(e);
        }
        return errors;
    }

    /**
     * this is a Distributed non-blocking version of lock() method; it attempts
     * to acquire the lock immediately, return true if locking succeeds
     *
     * @param lockName the name of the tryLock
     * @param unlockPassword unlockPassword is to be used for unlock. To protect
     * a tryLock from being unlocked by anyone, a tryLock cannot be released
     * when unlockPassword not match
     * @param millisecondsToExpireIncaseUnableToUnlock expire time of tryLock in
     * case unable to unlock (e.g. exception/error before executing unlock)
     * @return the result of get tryLock
     *
     */
    boolean tryLock(String lockName, String unlockPassword, long millisecondsToExpireIncaseUnableToUnlock);

    /**
     * unlocks the Distributed Lock instance
     *
     * @param lockName the name of the tryLock
     * @param unlockPassword to ensure only the owner is able to unlock, success
     * only when this value equals the unlockPassword specified by tryLock
     * @return the result of get release
     *
     */
    boolean unlock(String lockName, String unlockPassword);

    default String generateUnlockPassword() {
        return UUID.randomUUID().toString() + "_" + BootConstant.PID + "_" + Thread.currentThread().getName();
    }

    /**
     *
     * @param key
     * @param unlockPassword
     * @param ttlMinute
     * @return true when debounced
     */
    default boolean debounced(String key, String unlockPassword, int ttlMinute) {
        return !tryLock(key, unlockPassword, ttlMinute * 60000);
    }

    void putOnBlacklist(String key, String value, long expireInSeconds);

    boolean isOnBlacklist(String key);

    /**
     * flash sale - enable
     *
     * @param itemId
     * @param isEnabled sale enabled if true, else disabled
     */
    default void flashsaleEnable(String itemId, boolean isEnabled) {
    }

    /**
     * flash sale - init inventory
     *
     * @param itemId
     * @param totalAmount total inventory
     * @param limit max orderAmount per order
     * @return true if success
     */
    default boolean flashsaleInventoryInit(String itemId, long totalAmount, long limit) {
        return false;
    }

    /**
     * flash sale - order competition before reducing inventory
     *
     * @param itemId
     * @param requestAmount
     * @return confirmed order amount
     */
    default long flashsaleAcquireQuota(String itemId, long requestAmount) {
        return -600;
    }

    /**
     * flash sale - revoke an order (undo order competition, normally happens
     * when failed to pay within N minutes)
     *
     * @param itemId
     * @param requestAmount
     * @return the updated inventory booked amount
     */
    default long flashsaleRevokeQuota(String itemId, long requestAmount) {
        return 0;
    }

    /**
     * flash sale - inventory report
     *
     * @param itemId
     * @return
     */
    default FlashSale flashsaleInventoryReport(String itemId) {
        return null;
    }
}
