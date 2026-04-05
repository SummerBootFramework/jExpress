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
package org.summerboot.jexpress.boot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.security.EncryptorUtil;

import java.io.File;
import java.util.Properties;

class MasterPassword extends BootConfig {
    public static void main(String[] args) {
        String t = generateTemplate(MasterPassword.class);
        System.out.println(t);
    }

    public static final MasterPassword cfg = new MasterPassword();

    protected MasterPassword() {
    }

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) throws Exception {
        masterPassword = EncryptorUtil.base64Decode(base64EncodedAdminPwd);
    }

    @Override
    public void shutdown() {
    }

    @ConfigHeader(title = "To protect configuration via Bse64 encoded Master Password in a file:",
            desc = """ 
                    
                      1. Copy this file to a secure location and change the password, e.g. /etc/security/master.password
                      2. Make it only accessible by OS admin but not application admin nor other users
                      3. To start the app: java -jar <app>.jar -authfile <path to this file> [-dmain <domain>]
                    
                    """,
            format = "Base64 Encoded",
            example = "java -jar jExpressApp.jar -authfile /etc/security/master.password")
    @Config(key = "APP_ROOT_PASSWORD", predefinedValue = "Y2hhbmdlaXQ=", required = true)
    @JsonIgnore
    private volatile String base64EncodedAdminPwd;

    @JsonIgnore
    private volatile String masterPassword;

    String getMasterPassword() {
        return masterPassword;
    }
}
