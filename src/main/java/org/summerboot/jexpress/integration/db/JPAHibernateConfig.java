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
package org.summerboot.jexpress.integration.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.util.BeanUtil;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.ReflectionUtil;
import org.summerboot.jexpress.boot.config.JExpressConfig;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class JPAHibernateConfig implements JExpressConfig {

    private static volatile Logger log = null;

    @JsonIgnore
    private volatile SessionFactory sessionFactory;

    private File cfgFile;
    private final Properties props = new Properties();
    private final Map<String, Object> settings = new HashMap<>();
    private final List<Class<?>> entityClasses = new ArrayList();

    protected JPAHibernateConfig() {
    }

    /**
     * used by temp()
     * @param temp 
     */
    private JPAHibernateConfig(Object temp) {
    }

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
    public JExpressConfig temp() {
        JExpressConfig ret = null;
        Class c = this.getClass();
        try {
            Constructor<JExpressConfig> cons = c.getDeclaredConstructor(Object.class);
            cons.setAccessible(true);
            Object param = null;
            ret = (JExpressConfig) cons.newInstance(param);
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            if (log == null) {
                log = LogManager.getLogger(getClass());
            }
            log.warn("failed to create temp " + c.getName(), ex);

        }
        return ret;
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
        //scan @Entity
        //settings.put(Environment.LOADED_CLASSES, entityClasses);
        String callerRootPackageName = System.getProperty(SummerApplication.SYS_PROP_APP_PACKAGE_NAME);//SummerApplication.getCallerRootPackageName();
        String csvPackageNames = props.getProperty(Environment.LOADED_CLASSES, "");
        scanAnnotation_Entity(callerRootPackageName + "," + csvPackageNames, packages);

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

    private void scanAnnotation_Entity(String csvPackageNames, String... packages) {
        log.debug("_rootPackageNames={}", csvPackageNames);
        String[] rootPackageNames = FormatterUtil.parseCsv(csvPackageNames);
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
