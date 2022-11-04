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
package org.summerboot.jexpress.boot.cli;

import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.security.JwtUtil;
import org.summerboot.jexpress.security.SecurityUtil;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.summerboot.jexpress.security.EncryptorUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class CommandLineRunner {

    protected static final String USAGE = "?";
    protected static final String VERSION = "version";
    protected static final String ADMIN_PWD_FILE = "authfile";
    protected static final String ADMIN_PWD = "auth";
    protected static final String JWT = "jwt";
    protected static final String ENCRYPT = "encrypt";
    protected static final String DOMAIN = "domain";
    protected static final String ENV = "env";

    protected CommandLine cli;
    protected final HelpFormatter formatter = new HelpFormatter();
    protected final Options options = new Options();

    protected String version = BootConstant.VERSION;

    final protected CommandLine initCLIs_BootDefault(String[] args) {
        Option arg = Option.builder(USAGE)
                .desc("Usage/Help")
                .build();
        options.addOption(arg);

        arg = Option.builder(VERSION)
                .desc("check framework version")
                .build();
        options.addOption(arg);

        arg = Option.builder(ADMIN_PWD_FILE)
                .desc("Specify an application config password in a file instead of the default one."
                        + System.lineSeparator() + "Note: Unlike the -auth opton, this option protects the app config password from being exposed via ps command."
                        + System.lineSeparator() + "The file should contains a line with the format as: APP_ROOT_PASSWORD=<my app config password>")
                .hasArg().argName("file")
                .build();
        options.addOption(arg);

        arg = Option.builder(ADMIN_PWD)
                .desc("Specify an application config password instead of the default one."
                        + System.lineSeparator() + "Note: This option exposes the app config password via ps command")
                .hasArg().argName("password")
                .build();
        options.addOption(arg);

        arg = Option.builder(JWT)
                .desc("generate JWT root signing key with the specified algorithm <HS256, HS384, HS512>")
                .hasArg().argName("algorithm")
                .build();
        options.addOption(arg);

        arg = Option.builder(ENCRYPT)
                .desc("Encrypt the given password via application config password."
                        + System.lineSeparator() + System.lineSeparator() + "1. Manual Batch Encrypt mode - the commands below encrypt all values in the format of \"DEC(plain text)\" in the specified configuration env:"
                        + System.lineSeparator() + System.lineSeparator() + "\t -env <env name> -encrypt true -authfile <path to a file which contains config password>"
                        + System.lineSeparator() + System.lineSeparator() + "\t java -jar app.jar -env <env name>  -encrypt true -auth <my app config password>"
                        + System.lineSeparator() + System.lineSeparator() + "2. Manual Batch Decrypt mode - the command below decrypts all values in the format of \"ENC(encrypted text)\" in the specified configuration env:"
                        + System.lineSeparator() + System.lineSeparator() + "t java -jar app.jar  -env <env name>  -encrypt false -auth <my app config password>"
                        + System.lineSeparator() + System.lineSeparator() + "3. Manual Encrypt mode - In case you want to manually verify an encrypted sensitive data,  use the command below, compare the output with the encrypted value in the config file:"
                        + System.lineSeparator() + System.lineSeparator() + "\t  -encrypt <plain text> -auth <my app config password>")
                .hasArg()//.argName("true|false or plain text to be encrypted")
                .build();
        options.addOption(arg);

        try {
            CommandLineParser parser = new DefaultParser();
            cli = parser.parse(options, args);
        } catch (ParseException ex) {
            System.out.println(ex.getMessage());
            formatter.printHelp(version, options);
            System.exit(1);
        }
        return cli;
    }

    final protected void processCLIs_BootDefault() {
        //usage
        if (cli.hasOption(USAGE)) {
            formatter.printHelp(version, options);
            System.exit(0);
        }
        // version
        if (cli.hasOption(VERSION)) {
            System.out.println(version);
            System.exit(0);
        }
        // app config password
        if (cli.hasOption(ADMIN_PWD_FILE)) {
            String adminPwdFile = cli.getOptionValue(ADMIN_PWD_FILE);
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(adminPwdFile);) {
                props.load(is);
            } catch (Throwable ex) {
                throw new RuntimeException("failed to load " + adminPwdFile, ex);
            }
            String adminPwd = props.getProperty("APP_ROOT_PASSWORD");
            adminPwd = SecurityUtil.base64Decode(adminPwd);
            EncryptorUtil.init(adminPwd);
        } else if (cli.hasOption(ADMIN_PWD)) {// "else" = only one option, cannot both
            String adminPwd = cli.getOptionValue(ADMIN_PWD);
            EncryptorUtil.init(adminPwd);
        }
        // generate JWT root signing key
        if (cli.hasOption(JWT)) {
            String algorithm = cli.getOptionValue(JWT);
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.forName(algorithm);
            String jwt = JwtUtil.buildSigningKey(signatureAlgorithm);
            System.out.println(jwt);
            System.exit(0);
        }

        try {
            if (cli.hasOption(ENCRYPT) && !cli.hasOption(ENV)) {
                String plainPwd = cli.getOptionValue(ENCRYPT);
                String encryptedPwd = SecurityUtil.encrypt(plainPwd, false);
                System.out.println(encryptedPwd);
                System.exit(0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(-1);
        }
    }

}
