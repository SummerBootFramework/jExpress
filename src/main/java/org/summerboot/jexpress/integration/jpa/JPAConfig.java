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

import jakarta.persistence.EntityManager;
import org.apache.logging.log4j.LogManager;
import org.hibernate.cfg.Environment;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.ReflectionUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class JPAConfig extends BootConfig {
    protected final Map<String, Object> settings = new HashMap<>();
    protected final List<Class<?>> entityClasses = new ArrayList<>();

    public Map<String, Object> getSettings() {
        return settings;
    }

    public List<Class<?>> getEntityClasses() {
        return entityClasses;
    }

    @Override
    public void load(File cfgFile, boolean isReal) throws IOException {
        load(cfgFile);
    }

    /**
     * @param cfgFile
     * @param packages in which contains the @Entity classes
     * @throws IOException
     */
    public void load(File cfgFile, String... packages) throws IOException {
        if (logger == null) {
            logger = LogManager.getLogger(getClass());
        }
        this.cfgFile = cfgFile.getAbsoluteFile();

        try (InputStream is = new FileInputStream(cfgFile);) {
            props.load(is);
        }
        if (!cfgFile.canRead() || props.isEmpty()) {
            throw new IOException("Failed to load DB config file " + cfgFile);
        }

        ConfigUtil helper = new ConfigUtil(this.cfgFile.getAbsolutePath());
        Set<Object> keys = props.keySet();
        settings.clear();
        keys.forEach((key) -> {
            String name = key.toString();
            //if (name.startsWith("hibernate.")) {
            settings.put(name, props.getProperty(name));
            //}
        });

        if (props.get(Environment.JAKARTA_JDBC_PASSWORD) != null) {
            settings.put(Environment.JAKARTA_JDBC_PASSWORD, helper.getAsPassword(props, Environment.JAKARTA_JDBC_PASSWORD));
        } else if (props.get(Environment.PASS) != null) {
            settings.put(Environment.PASS, helper.getAsPassword(props, Environment.PASS));
        }

        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        //scan @Entity
        //settings.put(Environment.LOADED_CLASSES, entityClasses);
        Set<String> packageSet = new HashSet<>();
        packageSet.addAll(Set.of(packages));
        Set<String> configuredPackageSet = BackOffice.agent.getRootPackageNames();
        if (configuredPackageSet != null && !configuredPackageSet.isEmpty()) {
            packageSet.addAll(configuredPackageSet);
        }
        String csvPackageNames = props.getProperty(Environment.LOADED_CLASSES, "");
        scanAnnotation_Entity(csvPackageNames, packageSet);

        buildEntityManagerFactory();
    }

    protected void scanAnnotation_Entity(String csvPackageNames, Set<String> packageSet) {
        logger.debug("_rootPackageNames={}", csvPackageNames);
        String[] rootPackageNames = FormatterUtil.parseCsv(csvPackageNames);
        List<String> rootPackageNameList = new ArrayList<>();
        rootPackageNameList.addAll(Arrays.asList(rootPackageNames));
        rootPackageNameList.addAll(List.copyOf(packageSet));
        rootPackageNameList = rootPackageNameList.stream()
                .distinct()
                .collect(Collectors.toList());
        rootPackageNameList.removeAll(Collections.singleton(""));
        rootPackageNameList.removeAll(Collections.singleton(null));
        logger.debug("rootPackageNameList:{}", rootPackageNameList);
        entityClasses.clear();
        Set<Class<?>> tempEntityClasses = ReflectionUtil.getAllImplementationsByAnnotation(jakarta.persistence.Entity.class, false, rootPackageNameList);
        entityClasses.addAll(tempEntityClasses);
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
    }

    protected abstract void buildEntityManagerFactory();

    public abstract EntityManager em();
}
