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

import org.summerboot.jexpress.boot.config.ConfigUtil;
import static org.summerboot.jexpress.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.BeanUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import org.summerboot.jexpress.boot.config.JExpressConfig;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RedisConfig implements JExpressConfig {

    private static final String PK = "primary";

    private static volatile Logger log = null;
    private File cfgFile;
    private volatile List<JedisPool> jedisPools;
    private volatile JedisPool masterPool;
    private volatile List<String> nodes;
    private volatile int reconnectRetryIntervalMinutes;
    private volatile int sendAlertIntervalMinutes;

    public static final RedisConfig cfg = new RedisConfig();

    public RedisConfig() {
    }

    @Override
    public File getCfgFile() {
        return cfgFile;
    }

    @Override
    public String name() {
        return "Redis Config";
    }

    @Override
    public String info() {
        try {
            return BeanUtil.toJson(this, true, false);
        } catch (JsonProcessingException ex) {
            return ex.toString();
        }
    }

    @Override
    public JExpressConfig temp() {
        return new RedisConfig();
    }

    @Override
    public void load(File cfgFile, boolean isReal) throws Exception {
        if (log == null) {
            log = LogManager.getLogger(getClass());
        }
        this.cfgFile = cfgFile.getAbsoluteFile();
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(cfgFile);) {
            props.load(is);
        }
        if (jedisPools == null) {
            jedisPools = new ArrayList();
        } else {
            jedisPools.clear();
        }
        if (nodes == null) {
            nodes = new ArrayList();
        } else {
            nodes.clear();
        }
        ConfigUtil helper = new ConfigUtil(this.cfgFile.getAbsolutePath());
        reconnectRetryIntervalMinutes = helper.getAsInt(props, "redis.Reconnect.Retry.IntervalMinutes", 1);
        sendAlertIntervalMinutes = helper.getAsInt(props, "redis.SendAlert.IntervalMinutes", 10);

        masterPool = null;

        Set<String> _keys = props.keySet().stream().map(o -> o.toString()).collect(Collectors.toSet());
        List<String> keys = new ArrayList<>(_keys);
        Collections.sort(keys);
        keys.forEach((name) -> {
            if (name.startsWith("redis.node")) {
                String url = props.getProperty(name);// url: arbitrary_usrname:password@host:port
                String[] fields = url.split("\\:");
                //String user = fields[0];
                String[] f2 = fields[1].split("\\@");
                String encPwd = f2[0];
                String pwd = null;
                if (StringUtils.isNotBlank(encPwd)) {
                    try {
                        if (encPwd.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && encPwd.endsWith(")")) {
                            pwd = SecurityUtil.decrypt(encPwd, true);
                        }
                    } catch (GeneralSecurityException ex) {

                    }
                }
                String host = f2[1];
                int port = Integer.parseInt(fields[2]);
                JedisPool jp = new JedisPool(new JedisPoolConfig(),
                        host,
                        port,
                        Protocol.DEFAULT_TIMEOUT,
                        pwd);
                jedisPools.add(jp);
                nodes.add(host + ":" + port);
            }
        });
        String master = autoFailover(null);
        if (master != null) {
            nodes.add("current master=" + master);
        }
    }

    @Override

    public void shutdown() {
        jedisPools.forEach((p) -> {
            try {
                p.destroy();
            } catch (Throwable ex) {
            }
        });
    }

    public String autoFailover(Throwable cause) {
        if (cause != null) {
            log.error(cause);
        }
        long lastFailoveredMasterPoolTTL = 0;
        masterPool = null;
        String key = PK;
        JedisPool firstAvaliableMasterPool = null, failoveredMasterPool = null;
        for (JedisPool pool : jedisPools) {
            try (Jedis jedis = pool.getResource();) {
                jedis.del("arbitraryValue_just_check-writable");
                String anyValue = jedis.get(key);
                if (anyValue == null) {// not found
                    if (firstAvaliableMasterPool == null) {
                        firstAvaliableMasterPool = pool;
                    }
                } else {// found
                    Long currentTTL = jedis.ttl(key);
                    if (currentTTL == null) {
                        currentTTL = 0L;
                    } else if (currentTTL < 0) {
                        currentTTL = Long.MAX_VALUE / 1000;
                    }
                    if (currentTTL > lastFailoveredMasterPoolTTL) {
                        failoveredMasterPool = pool;
                        lastFailoveredMasterPoolTTL = currentTTL;
                    }
//                    if (failoveredMasterPool == null) {
//                        failoveredMasterPool = pool;
//                    }
                }
            } catch (JedisConnectionException | JedisDataException ex) {
                log.warn("find connection: " + ex);
            }
        }
        if (failoveredMasterPool != null) {
            masterPool = failoveredMasterPool;
        } else if (firstAvaliableMasterPool != null) {
            masterPool = firstAvaliableMasterPool;
        }

        String ret = null; // return null to indicate no available node
        if (masterPool != null) {
            // now switched to new master node
            try (Jedis jedis = masterPool.getResource();) {
                //ret = jedis.getClient().toString();
                ret = jedis.getConnection().toString();
                long ttl = (Long.MAX_VALUE - System.currentTimeMillis() - 1000) / 1000;
                jedis.psetex(key, ttl, ret);
                //jedis.set(key, ret);
            }
        }
        return ret;
    }

    @JsonIgnore
    public Jedis getMaster() {
        if (masterPool == null) {
            return null;
        }
        return masterPool.getResource();
    }

    public List<String> getNodes() {
        return nodes;
    }

    public int getReconnectRetryIntervalMinutes() {
        return reconnectRetryIntervalMinutes;
    }

    public int getSendAlertIntervalMinutes() {
        return sendAlertIntervalMinutes;
    }
}
