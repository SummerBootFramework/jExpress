/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.util.io;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PropertiesFile extends Properties {
    private List<String> orderedKeys = new ArrayList<>();

    public List<String> keyList() {
        return orderedKeys;
    }

    public List<ImmutablePair<String, String>> load(File propertiesFile) throws IOException {
        try (InputStream is = new FileInputStream(propertiesFile);
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);) {
            super.load(isr);
        }
        List<ImmutablePair<String, String>> pairs = new ArrayList<>();
        for (String key : orderedKeys) {
            pairs.add(new ImmutablePair<>(key, super.getProperty(key)));
        }
        return pairs;
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        String keyStr = (key instanceof String) ? (String) key : key.toString();
        orderedKeys.add(keyStr);
        return super.put(key, value);
    }

    @Override
    public synchronized void clear() {
        orderedKeys.clear();
        super.clear();
    }
}
