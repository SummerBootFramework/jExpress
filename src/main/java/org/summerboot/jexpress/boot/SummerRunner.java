/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, appVersionLong 2.0 (the
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
package org.summerboot.jexpress.boot;

import com.google.inject.Injector;
import org.apache.commons.cli.CommandLine;
import org.summerboot.jexpress.integration.smtp.PostOffice;

import java.io.File;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface SummerRunner {

    record RunnerContext(String appVersion,
                         CommandLine cli,
                         File configDir,
                         Injector guiceInjector,
                         PostOffice postOffice) {
    }


        /*public RunnerContext(String appVersion, CommandLine cli, File configDir, Injector guiceInjector, PostOffice postOffice) {
            this.appVersion = appVersion;
            this.cli = cli;
            this.configDir = configDir;
            this.guiceInjector = guiceInjector;
            this.postOffice = postOffice;
        }

        public CommandLine getCli() {
            return cli;
        }

        public File getConfigDir() {
            return configDir;
        }

        public Injector getGuiceInjector() {
            return guiceInjector;
        }

        public PostOffice getPostOffice() {
            return postOffice;
        }*/

    //}

    /**
     * @param context
     * @throws Exception
     */
    void run(RunnerContext context) throws Exception;
}
