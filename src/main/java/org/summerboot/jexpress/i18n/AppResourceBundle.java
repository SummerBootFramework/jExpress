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
package org.summerboot.jexpress.i18n;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class AppResourceBundle extends ResourceBundle {

    /**
     * key - @see Locale.toLanguageTag
     */
    protected static final ConcurrentHashMap<String, AppResourceBundle> POOL = new ConcurrentHashMap<>();
    protected static AppResourceBundle defaultRB;

    public static synchronized void clear() {
        POOL.clear();
    }

    public static synchronized void addLabels(List<AppResourceCfg> cfgs, Class i18nClass) {
        // init each RB
        Map<Locale, AppResourceBundle> tempRbs = new HashMap<>();
        cfgs.stream().forEach((AppResourceCfg cfg1) -> {
            String key = cfg1.getLanguageTag();
            // load hardcoded in I18n class
            Map<String, String> translationsMap1 = new HashMap<>();
            I18nLabel.buildTranslationsMap(null, i18nClass, cfg1.getI18nLabelIndex(), translationsMap1);
            AppResourceBundle arb = POOL.get(key);
            if (arb == null) {
                arb = new AppResourceBundle(cfg1, translationsMap1);
                POOL.put(key, arb);
            } else {
                arb.translationsMap.putAll(translationsMap1);
            }
            if (cfg1.getParent() == null) {
                defaultRB = arb;
            }
            tempRbs.put(cfg1.getLocale(), arb);
        });
        // setup RB parent relationship
        cfgs.stream().filter((AppResourceCfg dto) -> (dto.getParent() != null)).forEach((dto) -> {
            AppResourceBundle p = tempRbs.get(dto.getParent());
            AppResourceBundle i = tempRbs.get(dto.getLocale());
            i.setParent(p);
        });
        // cleanup        
        tempRbs.clear();
    }

    public static void dumpToFile(String folder, String filename) {
        List<String> sorted = new ArrayList<>();
        Enumeration<String> enumKeys = defaultRB.getKeys();
        while (enumKeys.hasMoreElements()) {
            sorted.add(enumKeys.nextElement());
        }
        Collections.sort(sorted);

        POOL.forEach((languageTag, rb) -> {
            Path path = Paths.get(folder, languageTag + "_" + filename);
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                for (String key : sorted) {
                    writer.write(key + " = " + rb.getString(key) + "\n");
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    /**
     * @param languageTag - @see Locale.toLanguageTag
     * @param key
     * @param value
     */
    public static void update(String languageTag, String key, String value) {
        AppResourceBundle cachedRb = POOL.get(languageTag);
        if (cachedRb != null) {
            cachedRb.update(key, value);
        }
    }

    /**
     * @param languageTag - @see Locale.toLanguageTag
     * @return
     */
    public static AppResourceBundle getAppBundle(String languageTag) {
        if (languageTag == null) {
            return defaultRB;
        }
        AppResourceBundle ret = POOL.get(languageTag);
        if (ret == null) {
            ret = defaultRB;
        }
        return ret;
    }

    protected final ConcurrentMap<String, String> translationsMap;

    protected final AppResourceCfg cfg;

    protected AppResourceBundle(AppResourceCfg cfg, Map<String, String> translations) {
        this.cfg = cfg;
        this.translationsMap = new ConcurrentHashMap<>(translations.size());
        this.translationsMap.putAll(translations);
    }

    @Override
    protected Object handleGetObject(String key) {
        return translationsMap.get(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return new IteratorEnumeration<>(translationsMap.keySet().iterator());
    }

    @Override
    public String getBaseBundleName() {
        return cfg.getLanguageTag();
    }

    @Override
    public Locale getLocale() {
        return cfg.getLocale();
    }

    public ResourceBundle getParent() {
        return parent;
    }

    protected void update(String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            translationsMap.put(key, value);
        }
    }

    protected static class IteratorEnumeration<E> implements Enumeration<E> {

        protected final Iterator<E> iterator;

        public IteratorEnumeration(Iterator<E> iterator) {
            this.iterator = iterator;
        }

        @Override
        public E nextElement() {
            return iterator.next();
        }

        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

    }

}
