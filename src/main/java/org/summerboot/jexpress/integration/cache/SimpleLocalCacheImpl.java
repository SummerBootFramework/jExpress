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

import com.google.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @param <K>
 * @param <V>
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class SimpleLocalCacheImpl<K, V> implements SimpleLocalCache<K, V> {

    protected final Map<Object, CacheEntity<V>> debouncingData = new ConcurrentHashMap<>();

    protected void clean(long now) {
        debouncingData.keySet().forEach(key -> {
            CacheEntity<V> ce = debouncingData.get(key);
            if (ce == null || ce.getTtlMillis() < now) {
                debouncingData.remove(key);
            }
        });
    }

    /**
     * @param key
     * @param value
     * @param ttlMilliseconds
     */
    @Override
    public void put(K key, V value, Long ttlMilliseconds) {
        debouncingData.put(key, new CacheEntity(value, ttlMilliseconds));
    }

    /**
     * @param key
     * @return
     */
    @Override
    public V get(K key) {
        if (key == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        clean(now);
        CacheEntity<V> e = debouncingData.get(key);
        if (e == null) {
            return null;
        }
        //System.out.println("ttl left=" + (e.getTtlSec() - now));
        if (e.getTtlMillis() < now) {
            return null;
        }
        return e.getValue();
    }

    /**
     * @param key
     * @return
     */
    @Override
    public V delete(K key) {
        V ret = get(key);
        debouncingData.remove(key);
        return ret;
    }

    /**
     * @param <V>
     */
    public static class CacheEntity<V> {

        private final V value;
        private final long ttlMillis;

        public CacheEntity(V value, Long ttlMilliseconds) {
            this.value = value;
            this.ttlMillis = ttlMilliseconds == null || ttlMilliseconds < 0
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + ttlMilliseconds;
        }

        public V getValue() {
            return value;
        }

        public long getTtlMillis() {
            return ttlMillis;
        }
    }

}
