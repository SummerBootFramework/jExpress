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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.integration.cache.domain.FlashSale;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.server.AbortPolicyWithReport;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.SetParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 1. download redis-cell from https://github.com/brandur/redis-cell/releases 2.
 * unzip 3. redis-cli>module load /path/to/libredis_cell.so
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class BootCache_RedisImple implements AuthTokenCache, BootCache {

    /*
      to avoid: 
    current thread step1: if(redis.get(key)==pwd) 
    other thread step1,2: expired/unlock then lock with new pwd 
    current thread step2: redis.del(key) - this will delete other's lock, which will cause a bug
     */
    protected static final String LUA_SCRIPT_UNLOCK = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    protected static final String LUA_SCRIPT_THROTTLE = "local key = KEYS[1]\n"
            + "local initBurst = tonumber(ARGV[1])\n"
            + "local maxBurstPerPeriod = tonumber(ARGV[2])\n"
            + "local period = tonumber(ARGV[3])\n"
            + "local quota = ARGV[4]\n"
            + "return redis.call('CL.THROTTLE', key, initBurst, maxBurstPerPeriod, period, quota)";
    protected static final String LUA_SCRIPT_FLASHSALE = "local order = tonumber(ARGV[1])\n"
            + "if not order or order < 1 then return 0 end\n"
            + "local vals = redis.call(\"HMGET\", KEYS[1], \"" + FlashSale.Status + "\", \"" + FlashSale.Total + "\", \"" + FlashSale.Limit + "\", \"" + FlashSale.Booked + "\")\n"
            + "local status = tonumber(vals[1])\n"
            + "if not status or status < 1 then return 0 end\n"//flash sale finished 
            + "local total = tonumber(vals[2])\n"
            + "local limit = tonumber(vals[3])\n"
            + "local booked = tonumber(vals[4])\n"
            + "if not total or not limit or not booked then return 0 end\n"
            //            + "if booked >= total then\n"
            //            + "	redis.call(\"HSET\", KEYS[1], \"" + FlashSale.Status + "\", 0)\n"
            //            + "	return 0\n"
            //            + "end\n"
            + "if order <= limit and booked + order <= total then\n"
            + "    redis.call(\"HINCRBY\", KEYS[1], \"" + FlashSale.Booked + "\", order)\n"
            + "    return order\n"
            + "end\n"
            + "return 0";//hmset myitem1 Status 0 Total 10 Booked 0
    protected static final String REDIS_SUCCESS = "OK";
    protected static final Long RELEASE_SUCCESS = 1L;

    protected static final Logger log = LogManager.getLogger(BootCache_RedisImple.class.getName());

    protected static final ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1), new NamedDefaultThreadFactory("Redis"), new AbortPolicyWithReport("Cahce.BackofficeExecutor"));

    protected static final RuntimeException REDIS_MASTER_NULL_EX = new RuntimeException("Redis master is null");

    protected static RedisConfig redisCfg = RedisConfig.cfg;

    protected class Holder<T> {

        protected T value;

        public Holder(T value) {
            this.value = value;
        }

        public void value(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }
    }

    protected void execute(JedisCall caller) {
        executeEx(caller, 0, 6);
    }

    protected void execute(boolean retry, JedisCall caller) {
        if (retry) {
            executeEx(caller, 0, 6);
        } else {
            executeEx(caller, 0, 0);
        }
    }

    protected void executeEx(JedisCall caller, int currentRetry, int maxRetry) {
        boolean success = false;
        try (Jedis jedis = redisCfg.getMaster();) {
            if (jedis == null) {
                //System.out.println("e 1 retry " + currentRetry);
                onRedisDown(REDIS_MASTER_NULL_EX);
                if (currentRetry >= maxRetry) {
                    //System.out.println("e 2 retry " + currentRetry);
                    //throw REDIS_MASTER_NULL_EX;
                    return;
                }
            } else {
                caller.call(jedis);
                success = true;
            }
        } catch (JedisConnectionException ex) {
            if (currentRetry == 2) {//retry on same connection once
                //System.out.println("e 3 retry " + currentRetry);
                onRedisDown(ex);
            }
            if (currentRetry >= maxRetry) {
                //System.out.println("e 4 retry " + currentRetry);
                throw ex;
            }
        } catch (JedisDataException ex) {
            //System.out.println("e 5 retry " + currentRetry);
            if (currentRetry >= maxRetry) {
                //System.out.println("e 6 retry " + currentRetry);
                throw ex;
            }
        }
        if (!success) {
            //System.out.println("e 7 retry " + currentRetry);
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executeEx(caller, ++currentRetry, maxRetry);
        }
    }

    protected void onRedisDown(Throwable ex) {
        Runnable asyncTask = () -> {
            long lastAlertTs = 0;
            String newNode;
            do {
                Thread.currentThread().setPriority(10);
                newNode = redisCfg.autoFailover(ex);
                long elapsedMinutes = (System.currentTimeMillis() - lastAlertTs) / 60000;
                if (elapsedMinutes >= redisCfg.getSendAlertIntervalMinutes()) {
                    onNoticeRedisDown(redisCfg.info(), ex);
                    lastAlertTs = System.currentTimeMillis();
                }
                if (newNode == null) {
                    try {
                        TimeUnit.MINUTES.sleep(redisCfg.getReconnectRetryIntervalMinutes());
                    } catch (InterruptedException ex1) {
                        Thread.currentThread().interrupt();
                        log.warn("Redis.autoFailover failed to sleep", ex1);
                    }
                }
            } while (newNode == null);
            onNoticeAutoFailover(redisCfg.info(), newNode);
        };
        if (tpe.getActiveCount() < 1) {
            try {
                tpe.execute(asyncTask);
            } catch (RejectedExecutionException ex2) {
                log.debug("Rejected");
            }
        } else {
            log.debug("Skipped");
        }
    }

    @Inject
    protected PostOffice po;

    protected void onNoticeRedisDown(String info, Throwable ex) {
        if (po != null) {
            po.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Redis is Down", info, ex, false);
        }
    }

    protected void onNoticeAutoFailover(String info, String newNode) {
        if (po != null) {
            po.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "Redis Auto Failover to " + newNode, info, null, false);
        }
    }

    /**
     * this is a Distributed non-blocking version of lock() method; it attempts
     * to acquire the lock immediately, return true if locking succeeds
     *
     * @param lockName                        the name of the tryLock
     * @param unlockPassword                  unlockPassword is to be used for unlock. To protect
     *                                        a tryLock from being unlocked by anyone, a tryLock cannot be released
     *                                        when unlockPassword not match
     * @param ttlToExpireIncaseUnableToUnlock expire time of tryLock in
     *                                        case unable to unlock (e.g. exception/error before executing unlock)
     * @return the result of get tryLock
     */
    @Override
    public boolean tryLock(String lockName, String unlockPassword, long ttlToExpireIncaseUnableToUnlock, TimeUnit timeUnit) {
        final Holder<Boolean> holder = new Holder<>(false);
        execute(true, jedis -> {
            SetParams p = new SetParams().nx().px(timeUnit.toMillis(ttlToExpireIncaseUnableToUnlock));
            String result = jedis.set(lockName, unlockPassword, p);

            boolean isLocked = REDIS_SUCCESS.equalsIgnoreCase(result);
            holder.value(isLocked);
        });
        return holder.value();
    }

    /**
     * unlocks the Distributed Lock instance
     *
     * @param lockName       the name of the tryLock
     * @param unlockPassword to ensure only the owner is able to unlock, success
     *                       only when this value equals the unlockPassword specified by tryLock
     * @return the result of get release
     */
    @Override
    public boolean unlock(String lockName, String unlockPassword) {
        final Holder<Boolean> holder = new Holder<>(false);
        execute(true, jedis -> {
            Object result = jedis.eval(LUA_SCRIPT_UNLOCK, Collections.singletonList(lockName), Collections.singletonList(unlockPassword));
            boolean isReleased = RELEASE_SUCCESS.equals(result);
            holder.value(isReleased);
        });
        return holder.value();
    }

    @Override
    public void blacklist(String key, String value, long ttlMilliseconds) {
        if (key == null) {
            return;
        }
        execute(true, jedis -> {
            if (ttlMilliseconds > 0) {
                jedis.psetex(key, ttlMilliseconds, value == null ? "?" : value);
            }
        });
    }

    @Override
    public boolean isBlacklist(String key) {
        if (key == null) {
            return false;
        }
        final Holder<Boolean> holder = new Holder<>(false);
        execute(true, jedis -> {
            boolean exists = jedis.exists(key);
            holder.value(exists);
        });
        return holder.value();
    }

    @Override
    public boolean flashsaleInventoryInit(String itemId, long totalAmount, long limit) {
        final Holder<Boolean> holder = new Holder<>(false);
        execute(true, jedis -> {
            Map<String, String> item = new HashMap<>();
            item.put(FlashSale.Status, "0");
            item.put(FlashSale.Total, String.valueOf(totalAmount));
            item.put(FlashSale.Limit, String.valueOf(limit));
            item.put(FlashSale.Booked, "0");
            String result = jedis.hmset(itemId, item);
            boolean success = REDIS_SUCCESS.equalsIgnoreCase(result);
            holder.value(success);
        });
        return holder.value();
    }

    @Override
    public FlashSale flashsaleInventoryReport(String itemId) {
        FlashSale ret = new FlashSale();
        execute(true, jedis -> {
            List<String> result = jedis.hmget(itemId, FlashSale.Status, FlashSale.Total, FlashSale.Limit, FlashSale.Booked);
            ret.setStatus(Integer.parseInt(result.get(0)));
            ret.setTotal(Long.parseLong(result.get(1)));
            ret.setLimit(Long.parseLong(result.get(2)));
            ret.setBooked(Long.parseLong(result.get(3)));
        });
        return ret;
    }

    @Override
    public void flashsaleEnable(String itemId, boolean isEnabled) {
        execute(true, jedis -> {
            jedis.hset(itemId, FlashSale.Status, isEnabled ? "1" : "0");
        });
    }

    @Override
    public long flashsaleAcquireQuota(String itemId, long requestAmount) {
        if (requestAmount < 1) {
            return -1;
        }
        final Holder<Long> holder = new Holder<>(0L);
        execute(true, jedis -> {
            Object result = jedis.eval(LUA_SCRIPT_FLASHSALE,
                    Collections.singletonList(itemId),
                    Collections.singletonList(String.valueOf(requestAmount)));
            holder.value((Long) result);
        });
        return holder.value();
    }

    @Override
    public long flashsaleRevokeQuota(String itemId, long requestAmount) {
        if (requestAmount < 1) {
            return -1;
        }
        final Holder<Long> holder = new Holder<>(0L);
        execute(true, jedis -> {
            Long result = jedis.hincrBy(itemId, FlashSale.Booked, 0 - requestAmount);
            holder.value(result);
        });
        return holder.value();
    }

    /**
     * Require Redis 4.0+ and Redis-Cell
     *
     * @param key
     * @param initBurst
     * @param maxBurstPerPeriod
     * @param period            seconds
     * @param requestQuota
     * @return The number of seconds until the user should retry, and always -1
     * if the action was allowed
     */
    public long rateLimiterGetWaitTime(String key, int initBurst, int maxBurstPerPeriod, int period, int requestQuota) {
        final Holder<Integer> holder = new Holder<>(-1);
        execute(true, jedis -> {
            List<String> argvs = new ArrayList<>();
            argvs.add(String.valueOf(initBurst));
            argvs.add(String.valueOf(maxBurstPerPeriod));
            argvs.add(String.valueOf(period));
            argvs.add(String.valueOf(requestQuota));
            Object result = jedis.eval(LUA_SCRIPT_THROTTLE,
                    Collections.singletonList(key),
                    argvs);
            List<Integer> quotaResult = (List<Integer>) result;
            int retryAfterSeconds = quotaResult.get(3);
            holder.value(retryAfterSeconds);
        });
        return holder.value();
    }

    /**
     * Only good for: low rate, check if a user input wrong password more than X
     * times within N minutes.
     *
     * <p>
     * Not good for: high rate
     * <p>
     * 1. Implemented via ZSet, will keep a record/access, not memory friendly
     * for high volumn access.
     * <p>
     * 2. Not atomic, not accurite with high concurrent access
     * <p>
     * 3. result is based on current burst ratet, it won't return true if the
     * burst rate keeps high, no matter how long it lasts
     *
     * @param key
     * @param periodSecond
     * @return the burst rate of the current period window
     */
    @Deprecated
    public long rateLimiterGetSlidingWindowRate(String key, int periodSecond) {
        long nowTs = System.currentTimeMillis();
        final Holder<Long> holder = new Holder<>(0L);
        execute(true, jedis -> {
            Response<Long> count;
            try (Pipeline pipeline = jedis.pipelined();) {
                //pipeline.multi();
                pipeline.zadd(key, nowTs, String.valueOf(nowTs));
                pipeline.zremrangeByScore(key, 0, nowTs - periodSecond * 1000);
                count = pipeline.zcard(key);
                pipeline.expire(key, periodSecond + 1);
                //pipeline.exec();
                pipeline.syncAndReturnAll();
            }
            if (count != null && count.get() != null) {
                long c = count.get();
                //boolean gotQuota = (c + requestQuota - 1) <= maxBurstPerPeriod;
                holder.value(c);
            }
        });
        return holder.value();
    }
}
