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

import com.google.inject.Singleton;
import org.summerboot.jexpress.security.authenticate.Caller;
import org.summerboot.jexpress.security.authenticate.User;
import org.summerboot.jexpress.util.BeanUtil;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 2.0
 */
@Singleton
public class AuthTokenCacheLocalImpl extends SimpleLocalCacheImpl<String, String> implements AuthTokenCache {

    @Override
    public void blacklist(String key, String value, long ttlMilliseconds) {
        put(key, value, ttlMilliseconds);
    }

    @Override
    public boolean isBlacklist(String key) {
        String v = get(key);
        return v != null;
    }

    private static final String OTT_KEY_PREFIX = "ws:token:";

    @Override
    public void oneTimeTokenPut(String key, Caller caller, long ttlMilliseconds) {
        put(OTT_KEY_PREFIX + key, BeanUtil.toJson(caller, false, true), ttlMilliseconds);
    }

    @Override
    public Caller oneTimeTokenVerifyAndDestroy(String key) {
        String json = get(OTT_KEY_PREFIX + key);
        if (json != null) {
            delete(OTT_KEY_PREFIX + key);
            return BeanUtil.fromJson(json, User.class);
        }
        return null;
    }
}
