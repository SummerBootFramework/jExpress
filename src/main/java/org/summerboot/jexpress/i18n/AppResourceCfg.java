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
package org.summerboot.jexpress.i18n;

import java.util.Locale;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class AppResourceCfg {

    protected final int i18nLabelIndex;
    protected final Locale locale, parent;
    protected final String languageTag;
    protected final String displayName;

    public AppResourceCfg(int i18nLabelIndex, Locale parent, Locale locale, String displayName) {
        this.i18nLabelIndex = i18nLabelIndex;
        this.parent = parent;
        this.locale = locale;
        this.languageTag = locale.toLanguageTag();
        this.displayName = displayName == null ? locale.getDisplayName(locale) : displayName;
    }

    public int getI18nLabelIndex() {
        return i18nLabelIndex;
    }

    public Locale getLocale() {
        return locale;
    }

    public Locale getParent() {
        return parent;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getDisplayName() {
        return displayName;
    }
}
