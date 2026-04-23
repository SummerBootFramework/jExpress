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

    protected void evict() {
        evict(System.currentTimeMillis());
    }

    protected void evict(long targetTime) {
        debouncingData.keySet().forEach(key -> {
            CacheEntity<V> ce = debouncingData.get(key);
            if (ce != null && ce.isExpiredWhen(targetTime)) {
                if (ce.isKeepEvicted()) {
                    ce.setEvicted(true);
                } else {
                    debouncingData.remove(key);
                }
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
        debouncingData.put(key, new CacheEntity<>(value, ttlMilliseconds, false));
    }

    @Override
    public void putAndKeepEvicted(K key, V value, Long ttlMilliseconds) {
        debouncingData.put(key, new CacheEntity<>(value, ttlMilliseconds, true));
    }

    /**
     * @param key
     * @return
     */
    @Override
    public V get(K key) {
        CacheEntity<V> ce = getWithEvicted(key);
        if (ce == null) {
            return null;
        }
        //System.out.println("ttl left=" + (e.getTtlSec() - now));
        if (ce.isExpiredWhen(System.currentTimeMillis())) {
            return null;
        }
        return ce.getValue();
    }

    @Override
    public CacheEntity<V> getWithEvicted(K key) {
        if (key == null) {
            return null;
        }
        evict();
        return debouncingData.get(key);
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
}
