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
