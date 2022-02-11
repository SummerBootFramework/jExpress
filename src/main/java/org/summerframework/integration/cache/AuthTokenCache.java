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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.instrumentation.HealthInspector;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public interface AuthTokenCache extends HealthInspector {

    @Override
    default List<org.summerframework.nio.server.domain.Error> ping(Object... params) {
        org.summerframework.nio.server.domain.Error e = null;
        try {
            String key = "jwt123";
            putOnBlacklist(key, "uid123", 1000);
            boolean isOnBlacklist = isOnBlacklist(key);
            if (!isOnBlacklist) {
                e = new org.summerframework.nio.server.domain.Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Data Error", "failed to read", null);
            }
            TimeUnit.MILLISECONDS.sleep(1500);
            isOnBlacklist = isOnBlacklist(key);
            if (isOnBlacklist) {
                e = new org.summerframework.nio.server.domain.Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Access Error", "failed to expire", null);
            }
        } catch (Throwable ex) {
            e = new org.summerframework.nio.server.domain.Error(BootErrorCode.ACCESS_ERROR_CACHE, "Cache Access Error", ex.toString(), ex);
        }
        List<org.summerframework.nio.server.domain.Error> errors = null;
        if (e != null) {
            errors = new ArrayList<>();
            errors.add(e);
        }
        return errors;
    }

    void putOnBlacklist(String key, String value, long expireInSeconds);

    boolean isOnBlacklist(String key);
}
