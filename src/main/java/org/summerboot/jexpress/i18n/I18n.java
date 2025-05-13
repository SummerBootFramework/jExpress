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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface I18n {

    // i18n
    Locale en = Locale.ENGLISH;
    Locale fr_CA = Locale.CANADA_FRENCH;
    Locale zh_CN = Locale.SIMPLIFIED_CHINESE;

    List<AppResourceCfg> cfgs = new ArrayList<>();

    static void init(Class additionalI18nClass) {
        cfgs.add(new AppResourceCfg(0, null, en, "English"));
        cfgs.add(new AppResourceCfg(1, en, fr_CA, "Française"));
        cfgs.add(new AppResourceCfg(2, en, zh_CN, "中文简体"));
        AppResourceBundle.clear();
        AppResourceBundle.addLabels(cfgs, I18n.class);
        if (additionalI18nClass != null) {
            AppResourceBundle.addLabels(cfgs, additionalI18nClass);
        }
    }

    interface info {

        I18nLabel launchingLog = new I18nLabel(
                "loading log4j2 from %ARG1%",
                "chargement de log4j2 à partir de %ARG1%",
                "加载log4j2 %ARG1%");

        I18nLabel launching = new I18nLabel(
                "Launching application...",
                "Lancer l'application ...",
                "启动应用程序......",
                "啟動應用程序......");

        I18nLabel launched = new I18nLabel(
                "%ARG1% application launched (success), kill -9 or Ctrl+C to shutdown",
                "%ARG1% application lancée (succès), kill -9 ou Ctrl + C pour arrêter",
                "%ARG1% 应用程序启动（成功），请用 kill -9 或 Ctrl + C 关闭");

        I18nLabel unlaunched = new I18nLabel(
                "Appliclation failed to start",
                "L'application n'a pas pu démarrer",
                "应用程序无法启动");

        I18nLabel cfgChangedBefore = new I18nLabel(
                "Configuration changed, before - \n%ARG1%",
                "Configuration changée, avant - \n%ARG1%",
                "配置更改, 之前 - \n%ARG1%");
        I18nLabel cfgChangedAfter = new I18nLabel(
                "Configuration changed, after - \n%ARG1%",
                "Configuration changée, après - \n%ARG1%",
                "配置更改, 之后 - \n%ARG1%");
    }

    interface error {

        interface http500 {

            I18nLabel runtime = new I18nLabel("500", true,
                    "We are having trouble (%ARG1%) processing your request. Our supporting team is being notified.",
                    "Nous rencontrons des difficultés (%ARG1%) pour traiter votre demande. Notre équipe de support est notifiée.");
        }

        interface http400 {

            I18nLabel Authentication = new I18nLabel("401", true,
                    "Authentication failed, user name or password do not match our record.",
                    "L'authentification a échoué, le nom d'utilisateur ou le mot de passe ne correspond pas à notre enregistrement.");
            I18nLabel Authorization = new I18nLabel("403", true,
                    "Unauthorized for %ARG1%",
                    "Non autorisé pour %ARG1%");
        }

    }
}
