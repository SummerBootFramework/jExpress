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

import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.integration.cache.domain.FlashSale;

import java.util.UUID;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface BootCache {

    /**
     * this is a Distributed non-blocking version of lock() method; it attempts
     * to acquire the lock immediately, return true if locking succeeds
     *
     * @param lockName                                 the name of the tryLock
     * @param unlockPassword                           unlockPassword is to be used for unlock. To protect
     *                                                 a tryLock from being unlocked by anyone, a tryLock cannot be released
     *                                                 when unlockPassword not match
     * @param millisecondsToExpireIncaseUnableToUnlock expire time of tryLock in
     *                                                 case unable to unlock (e.g. exception/error before executing unlock)
     * @return the result of get tryLock
     */
    boolean tryLock(String lockName, String unlockPassword, long millisecondsToExpireIncaseUnableToUnlock);

    /**
     * unlocks the Distributed Lock instance
     *
     * @param lockName       the name of the tryLock
     * @param unlockPassword to ensure only the owner is able to unlock, success
     *                       only when this value equals the unlockPassword specified by tryLock
     * @return the result of get release
     */
    boolean unlock(String lockName, String unlockPassword);

    default String generateUnlockPassword() {
        return UUID.randomUUID().toString() + "_" + BootConstant.PID + "_" + Thread.currentThread().getName();
    }

    /**
     * @param key
     * @param unlockPassword
     * @param ttlMinute
     * @return true when debounced
     */
    default boolean debounced(String key, String unlockPassword, int ttlMinute) {
        return !tryLock(key, unlockPassword, ttlMinute * 60000);
    }

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
     * @param limit       max orderAmount per order
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
