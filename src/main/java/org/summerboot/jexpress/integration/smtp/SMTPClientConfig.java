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
package org.summerboot.jexpress.integration.smtp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Volatile Bean　Pattern
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
//@ImportResource(BootConstant.FILE_CFG_SMTP)
public class SMTPClientConfig extends BootConfig {

    public static void main(String[] args) {
        String t = generateTemplate(SMTPClientConfig.class);
        System.out.println(t);
    }

    public static final SMTPClientConfig cfg = new SMTPClientConfig();

    private SMTPClientConfig() {
    }

    @Override
    public void shutdown() {
    }

    @JsonIgnore
    private volatile Session mailSession;

    //1. SMTP Settings
    @ConfigHeader(title = "1. SMTP Settings")
    @JsonIgnore
    @Config(key = "mail.smtp.host", required = false, desc = "The SMTP server to connect to. i.e. Gmail server: smtp.gmail.com")
    protected volatile String smtpHost;

    @JsonIgnore
    @Config(key = "mail.smtp.port", defaultValue = "25",
            desc = "25: The original standard SMTP port\n"
                    + "587: The standard secure SMTP port")
    protected volatile int smtpPort = 25;

    @JsonIgnore
    @Config(key = "mail.smtp.auth", desc = "Whether to attempt to authenticate the user using the AUTH command")
    protected volatile Boolean smtpAuth;

    @JsonIgnore
    @Config(key = "mail.smtp.starttls.enable", desc = "To inform the email server that the email client wants to upgrade from an insecure connection to a secure one using TLS or SSL")
    protected volatile Boolean smtpStarttls;

    public static final String KEY_USER_ACCOUNT = "mail.smtp.user";
    @JsonIgnore
    @Config(key = KEY_USER_ACCOUNT, required = false, desc = "Sender email account, also as default username for SMTP when display name is not provided")
    protected volatile String smtpUser;

    public static final String KEY_USER_DISPLAYNAME = "mail.smtp.user.displayname";
    @JsonIgnore
    @Config(key = KEY_USER_DISPLAYNAME, desc = "Sender display name")
    protected volatile String smtpUserDisplayName;

    public static final String KEY_USER_PWD = "mail.smtp.user.passwrod";
    @JsonIgnore
    @Config(key = KEY_USER_PWD, validate = Config.Validate.Encrypted)
    protected volatile String smtpPassword;

    //2. Alert Recipients
    @ConfigHeader(title = "2. Alert Recipients",
            format = "CSV format",
            example = "johndoe@test.com, janedoe@test.com")
    public static final String KEY_MAILTO_APPSUPPORT = "email.to.AppSupport";
    @Config(key = KEY_MAILTO_APPSUPPORT, validate = Config.Validate.EmailRecipients,
            desc = "The default alert email recipients")
    protected volatile Set<String> emailToAppSupport;

    public static final String KEY_MAILTO_DEV = "email.to.Development";
    @Config(key = KEY_MAILTO_DEV, validate = Config.Validate.EmailRecipients,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToDevelopment;

    public static final String KEY_MAILTO_REPORT = "email.to.ReportViewer";
    @Config(key = "email.to.ReportViewer", validate = Config.Validate.EmailRecipients,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToReportViewer;

    public static final String KEY_DEBOUCING_INTERVAL = "debouncing.emailalert_minute";
    @Config(key = KEY_DEBOUCING_INTERVAL,
            desc = "Alert message with the same title will not be sent out within this minutes")
    protected volatile int emailAlertDebouncingIntervalMinutes = 30;

    //3. mail session for Json display only
    private Properties mailSessionProp;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isReal, ConfigUtil helper, Properties props) {
        //1. SMTP recipients
        if (emailToAppSupport == null) {
            emailToAppSupport = new HashSet<>();
        }
        if (emailToDevelopment == null) {
            emailToDevelopment = new HashSet<>(emailToAppSupport);
        }
        if (emailToDevelopment.isEmpty()) {
            emailToDevelopment.addAll(emailToAppSupport);
        }
        if (emailToReportViewer == null) {
            emailToReportViewer = new HashSet<>(emailToAppSupport);
        }
        if (emailToReportViewer.isEmpty()) {
            emailToReportViewer.addAll(emailToAppSupport);
        }

        // validate error
        String error = helper.getError();
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        props.remove(KEY_USER_PWD);
        props.remove(KEY_MAILTO_APPSUPPORT);
        props.remove(KEY_MAILTO_DEV);
        props.remove(KEY_MAILTO_REPORT);
        props.remove(KEY_DEBOUCING_INTERVAL);
        Object displayName = props.get(KEY_USER_DISPLAYNAME);
        if (displayName == null) {
            displayName = props.get("mail.smtp.userName");// for backward compatibility only, will be depreacated in next release
            if (displayName == null) {
                displayName = BackOffice.agent.getVersionShort();// use major Version
            }

            if (displayName != null) {
                props.put(KEY_USER_DISPLAYNAME, displayName);
            }
        }
        mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });
        mailSessionProp = mailSession.getProperties();
    }

    public Session getMailSession() {
        return mailSession;
    }

    public Set<String> getEmailToAppSupport() {
        return emailToAppSupport;
    }

    public Set<String> getEmailToDevelopment() {
        return emailToDevelopment;
    }

    public Set<String> getEmailToReportViewer() {
        return emailToReportViewer;
    }

    public int getEmailAlertDebouncingIntervalMinutes() {
        return emailAlertDebouncingIntervalMinutes;
    }
}
