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
package org.summerframework.integration.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.util.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.Entity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.summerframework.boot.SummerApplication;
import org.summerframework.boot.config.SummerBootConfig;
import org.summerframework.util.FormatterUtil;
import org.summerframework.util.ReflectionUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JPAHibernateConfig implements SummerBootConfig {

    private static volatile Logger log = null;

    public static final JPAHibernateConfig CFG = new JPAHibernateConfig();
    @JsonIgnore
    private volatile SessionFactory sessionFactory;

    private File cfgFile;
    private final Properties props = new Properties();
    private final Map<String, Object> settings = new HashMap<>();
    private volatile List<Class<?>> entityClasses = new ArrayList();

    @Override
    public File getCfgFile() {
        return cfgFile;
    }

    @Override
    public String name() {
        return "DB Config";
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
    public SummerBootConfig temp() {
        return new JPAHibernateConfig();
    }

    @Override
    public void load(File cfgFile, boolean isReal) throws IOException, GeneralSecurityException {
        load(cfgFile);
    }

    /**
     *
     * @param cfgFile
     * @param packages in which contains the @Entity classes
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public void load(File cfgFile, String... packages) throws IOException, GeneralSecurityException {
        if (log == null) {
            log = LogManager.getLogger(getClass());
        }
        this.cfgFile = cfgFile.getAbsoluteFile();
        try (InputStream is = new FileInputStream(cfgFile);) {
            props.load(is);
        }

        settings.clear();
        ConfigUtil helper = new ConfigUtil(this.cfgFile.getAbsolutePath());

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
        String callerRootPackageName = SummerApplication.getCallerRootPackageName();
        String _rootPackageNames = callerRootPackageName + "," + props.getProperty(Environment.LOADED_CLASSES, "");//load JAP Entity classes from this root package names (CSV) for  O-R Mapping
        log.debug("_rootPackageNames={}", _rootPackageNames);
        String[] rootPackageNames = FormatterUtil.parseCsv(_rootPackageNames);
        List<String> rootPackageNameList = new ArrayList();
        rootPackageNameList.addAll(Arrays.asList(rootPackageNames));
        rootPackageNameList.addAll(Arrays.asList(packages));
        rootPackageNameList = rootPackageNameList.stream()
                .distinct()
                .collect(Collectors.toList());
        rootPackageNameList.removeAll(Collections.singleton(""));
        rootPackageNameList.removeAll(Collections.singleton(null));
        log.debug("rootPackageNameList:{}", rootPackageNameList);
        for (String rootPackageName : rootPackageNameList) {
            Set<Class<?>> tempEntityClasses = ReflectionUtil.getAllImplementationsByAnnotation(Entity.class, rootPackageName);
            entityClasses.addAll(tempEntityClasses);
            //settings.put(Environment.LOADED_CLASSES, entityClasses);
        }

        //build EMF
        //EntityManagerFactory emf = new EntityManagerFactoryBuilderImpl(new PersistenceUnitInfoDescriptor(null), settings).build();
        //build SessionFactory
        SessionFactory old = sessionFactory;
        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
        registryBuilder.applySettings(settings);
        StandardServiceRegistry registry = registryBuilder.build();
        MetadataSources sources = new MetadataSources(registry);
        entityClasses.forEach(sources::addAnnotatedClass);
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

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    public List<Class<?>> getEntityClasses() {
        return entityClasses;
    }

}