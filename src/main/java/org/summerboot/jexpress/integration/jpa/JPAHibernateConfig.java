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
package org.summerboot.jexpress.integration.jpa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Environment;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class JPAHibernateConfig extends JPAConfig {

    public static void main(String[] args) {
        class a extends JPAHibernateConfig {
        }
        String t = generateTemplate(a.class);
        System.out.println(t);
    }

    @ConfigHeader(title = "1. Hibernate properties for Database Connection",
            callbackMethodName4Dump = "generateTemplate_JPAConnection")
    protected final String dummyfield4annotion1 = null;

    protected void generateTemplate_JPAConnection(StringBuilder sb) {
        sb.append(Environment.JAKARTA_JDBC_URL + "=\n");
        sb.append(Environment.JAKARTA_JDBC_USER + "=\n");
        sb.append(Environment.JAKARTA_JDBC_PASSWORD + "=DEC(" + DESC_PLAINPWD + ")\n");
        sb.append(Environment.JAKARTA_JDBC_DRIVER + "=\n");
        sb.append("#" + Environment.DIALECT + "=\n");
        sb.append(Environment.SHOW_SQL + "=false\n");
        sb.append(Environment.HBM2DDL_AUTO + "=validate\n");
        sb.append("#hibernate.proc.param_null_passing=true\n");
        sb.append("#" + Environment.LOADED_CLASSES + "=\n");
    }

    @ConfigHeader(title = "2. Connection Pool",
            callbackMethodName4Dump = "generateTemplate_ConnectionPool")
    protected final String dummyfield4annotion2 = null;

    protected void generateTemplate_ConnectionPool(StringBuilder sb) {
        sb.append("# Maximum waiting time for a connection from the pool\n");
        sb.append("hibernate.hikari.connectionTimeout=20000\n");
        sb.append("# Minimum number of ideal connections in the pool\n");
        sb.append("hibernate.hikari.minimumIdle=10\n");
        sb.append("# Maximum number of actual connection in the pool\n");
        sb.append("hibernate.hikari.maximumPoolSize=20\n");
        sb.append("# Maximum time that a connection is allowed to sit ideal in the pool\n");
        sb.append("hibernate.hikari.idleTimeout=300000\n");
        sb.append("hibernate.hikari.registerMbeans=true\n");
    }

    //protected static volatile Logger log = null;
    @JsonIgnore
    protected volatile SessionFactory sessionFactory;

    protected JPAHibernateConfig() {
    }

    @Override
    protected void buildEntityManagerFactory() {
        try {
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
                logger.warn("close current db connection due to config changed");
                try {
                    old.close();
                } catch (Throwable ex) {
                    logger.warn("failed to close current db connection", ex);
                }
            }
        } finally {
            if (settings.get(Environment.JAKARTA_JDBC_PASSWORD) != null) {
                settings.put(Environment.JAKARTA_JDBC_PASSWORD, "****");// protect password from being logged
            }
            if (settings.get(Environment.PASS) != null) {
                settings.put(Environment.PASS, "****");// protect password from being logged
            }
        }
    }

    @Override
    public void shutdown() {
        if (sessionFactory != null) {
            System.out.println(Thread.currentThread().getName() + ": shutdown DB.SessionFactory");
            try {
                sessionFactory.close();
                sessionFactory = null;
            } catch (Throwable ex) {
                ex.printStackTrace(System.err);
            }
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public EntityManager em() {
        return sessionFactory.createEntityManager();
    }

}
