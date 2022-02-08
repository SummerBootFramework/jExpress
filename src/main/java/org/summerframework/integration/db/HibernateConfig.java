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
package org.summerframework.integration.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.persistence.EntityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.summerframework.boot.config.SummerBootConfig;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class HibernateConfig implements SummerBootConfig {

    private static volatile Logger log = null;

    public static final HibernateConfig CFG = new HibernateConfig();
    @JsonIgnore
    private volatile SessionFactory sessionFactory;

    private String cfgFile;
    private final Map<String, Object> settings = new HashMap<>();

    @Override
    public String getCfgFile() {
        return cfgFile;
    }

    @Override
    public String name() {
        return "DB Config";
    }

    @Override
    public String info() {
        try {
            return JsonUtil.toJson(this, true, false);
        } catch (JsonProcessingException ex) {
            return ex.toString();
        }
    }

    @Override
    public SummerBootConfig temp() {
        return new HibernateConfig();
    }

    @Override
    public void load(File cfgFile, boolean isReal) throws IOException, GeneralSecurityException {
        if (log == null) {
            log = LogManager.getLogger(getClass());
        }
        this.cfgFile = cfgFile.getAbsolutePath();
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(cfgFile);) {
            props.load(is);
        }

        settings.clear();
        ConfigUtil helper = new ConfigUtil(this.cfgFile);

        Set<Object> keys = props.keySet();
        keys.forEach((key) -> {
            String name = key.toString();
            //if (name.startsWith("hibernate.")) {
            settings.put(name, props.getProperty(name));
            //}
        });

        //Environment.PASS = "hibernate.connection.password"
        settings.put(Environment.PASS, helper.getAsPassword(props, Environment.PASS));
        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

//        //build EMF
//        EntityManagerFactory emf = new EntityManagerFactoryBuilderImpl(
//                new PersistenceUnitInfoDescriptor(null), settings)
//                .build();
        //build SessionFactory
        SessionFactory old = sessionFactory;
        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
        registryBuilder.applySettings(settings);
        StandardServiceRegistry registry = registryBuilder.build();
        MetadataSources sources = new MetadataSources(registry);
        Metadata metadata = sources.getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();

        if (old != null) {
            log.warn("close current db connection due to config changed");
            try {
                old.close();
            } catch (Throwable ex) {
                log.warn("failed to close current db connection", ex);
            }
        }
        if (settings.get(Environment.PASS) != null) {
            settings.put(Environment.PASS, "****");// protect password from being logged
        }
    }

    @Override
    public void shutdown() {
        System.out.println(Thread.currentThread().getName() + ": shutdown DB.SessionFactory");
        if (sessionFactory != null) {
            try {
                sessionFactory.close();
            } catch (Throwable ex) {
                ex.printStackTrace(System.err);
            }
        }
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public EntityManager em() {
        return sessionFactory.createEntityManager();
    }

}
