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
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;

import java.util.Properties;

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

    protected void generateTemplate_JPAConnection(StringBuilder sb, Properties currentValues) {
        appendCurrentValue(Environment.JAKARTA_JDBC_URL, currentValues, "", sb);
        appendCurrentValue(Environment.JAKARTA_JDBC_USER, currentValues, "", sb);
        appendCurrentValue(Environment.JAKARTA_JDBC_PASSWORD, currentValues, "DEC(" + DESC_PLAINPWD + ")", sb);
        appendCurrentValue(Environment.JAKARTA_JDBC_DRIVER, currentValues, "", sb);

        //sb.append("#" + Environment.DIALECT + "=\n");
        appendCurrentValue(Environment.DIALECT, currentValues, "", sb, true);

        appendCurrentValue(Environment.SHOW_SQL, currentValues, "false", sb);
        appendCurrentValue(Environment.HBM2DDL_AUTO, currentValues, "validate", sb, true);
        appendCurrentValue("hibernate.proc.param_null_passing", currentValues, "true", sb, true);

        //sb.append("#" + Environment.LOADED_CLASSES + "=\n");
        appendCurrentValue(Environment.LOADED_CLASSES, currentValues, "", sb, true);
    }

    @ConfigHeader(title = "2. Connection Pool",
            callbackMethodName4Dump = "generateTemplate_ConnectionPool")
    protected final String dummyfield4annotion2 = null;

    protected void generateTemplate_ConnectionPool(StringBuilder sb, Properties currentValues) {
        sb.append("# Note: Maximum waiting time for a connection from the pool" + BootConstant.BR);
        appendCurrentValue("hibernate.hikari.connectionTimeout", currentValues, "20000", sb);

        sb.append("# Note: Minimum number of ideal connections in the pool" + BootConstant.BR);
        appendCurrentValue("hibernate.hikari.minimumIdle", currentValues, "10", sb);

        sb.append("# Note: Maximum number of actual connection in the pool" + BootConstant.BR);
        appendCurrentValue("hibernate.hikari.maximumPoolSize", currentValues, "20", sb);

        sb.append("# Note: Maximum time that a connection is allowed to sit ideal in the pool" + BootConstant.BR);
        appendCurrentValue("hibernate.hikari.idleTimeout", currentValues, "300000", sb);

        sb.append("# Note: enables Java Management Extensions (JMX) MBeans for the HikariCP connection pool, " +
                "allowing real-time monitoring of metrics like active/idle connections and pool usage. " +
                "It is essential for tracking pool health but must be enabled explicitly as it defaults to false" + BootConstant.BR);
        appendCurrentValue("hibernate.hikari.registerMbeans", currentValues, "true", sb);
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
            /*if (settings.get(Environment.PASS) != null) {
                settings.put(Environment.PASS, "****");// protect password from being logged
            }*/
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
