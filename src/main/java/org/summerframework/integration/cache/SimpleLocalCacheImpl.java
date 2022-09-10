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
package org.summerframework.integration.cache;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class SimpleLocalCacheImpl implements SimpleLocalCache {

    @Override
    public void put(Object key, Object value, Integer ttlSec) {
        debouncingData.put(key, new CacheEntity(value, ttlSec));
    }

    @Override
    public <T> T get(Object key) {
        if (key == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        clean(now);
        CacheEntity e = debouncingData.get(key);
        if (e == null) {
            return null;
        }
        //System.out.println("ttl left=" + (e.getTtlSec() - now));
        if (e.getTtlMillis() < now) {
            return null;
        }
        return (T) e.getValue();
    }

    @Override
    public <T> T delete(Object key) {
        T ret = get(key);
        debouncingData.remove(key);
        return ret;
    }

    public static class CacheEntity {

        private final Object value;
        private final long ttlMillis;

        public CacheEntity(Object value, Integer ttlSec) {
            this.value = value;
            this.ttlMillis = ttlSec == null || ttlSec < 0
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + ttlSec * 1000;
        }

        public Object getValue() {
            return value;
        }

        public long getTtlMillis() {
            return ttlMillis;
        }
    }

    protected final Map<Object, CacheEntity> debouncingData = new ConcurrentHashMap<>();

    protected void clean(long now) {
        debouncingData.keySet().forEach(key -> {
            CacheEntity ce = debouncingData.get(key);
            if (ce == null || ce.getTtlMillis() < now) {
                debouncingData.remove(key);
            }
        });
    }
}
