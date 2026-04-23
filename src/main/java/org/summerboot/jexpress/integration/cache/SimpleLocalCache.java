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

/**
 * @param <K>
 * @param <V>
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface SimpleLocalCache<K, V> {

    void put(K key, V value, Long ttlMilliseconds);

    V get(K key);

    void putAndKeepEvicted(K key, V value, Long ttlMilliseconds);

    CacheEntity<V> getWithEvicted(K key);

    V delete(K key);

    class CacheEntity<V> {

        protected final V value;
        protected long expiredTs;
        protected boolean keepEvicted;
        protected boolean evicted;

        public CacheEntity(V value, Long ttlMilliseconds, boolean keepEvicted) {
            this.value = value;
            this.expiredTs = ttlMilliseconds == null || ttlMilliseconds < 0
                    ? Long.MAX_VALUE
                    : System.currentTimeMillis() + ttlMilliseconds;
            this.keepEvicted = keepEvicted;
            this.evicted = false;
        }

        public V getValue() {
            return value;
        }

        public long getExpiredTs() {
            return expiredTs;
        }

        public void setExpiredTs(long ttlMilliseconds) {
            this.expiredTs = System.currentTimeMillis() + ttlMilliseconds;
        }

        public boolean isExpiredWhen(long targetTs) {
            return expiredTs <= targetTs;
        }

        public boolean isKeepEvicted() {
            return keepEvicted;
        }

        public void setKeepEvicted(boolean keepEvicted) {
            this.keepEvicted = keepEvicted;
        }

        public boolean isEvicted() {
            return evicted;
        }

        public void setEvicted(boolean evicted) {
            this.evicted = evicted;
        }
    }
}
