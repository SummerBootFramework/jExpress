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

import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.util.Properties;
import java.util.Set;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import java.util.HashSet;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;

/**
 * Volatile Bean　Pattern
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@ImportResource(SummerApplication.CFG_SMTP)
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
    @Config(key = "mail.smtp.host")
    protected volatile String smtpHost = "smtp.gmail.com";

    @JsonIgnore
    @Config(key = "mail.smtp.port",
            desc = "Port 25: The original standard SMTP port\n"
            + "Port 587: The standard secure SMTP port")
    protected volatile int smtpPort = 587;

    @JsonIgnore
    @Config(key = "mail.smtp.auth")
    protected volatile boolean smtpAuth = true;

    @JsonIgnore
    @Config(key = "mail.smtp.starttls.enable")
    protected volatile boolean smtpStarttls = true;

    @JsonIgnore
    @Config(key = "mail.smtp.userName", desc = "Display name")
    protected volatile String smtpUserDisplayName = "John Doe";

    @JsonIgnore
    @Config(key = "mail.smtp.user", desc = "Email account")
    protected volatile String smtpUser = "johndoe@???.com";

    @JsonIgnore
    @Config(key = "mail.smtp.pwd", validate = Config.Validate.Encrypted)
    protected volatile String smtpPassword;

    //2. Alert Recipients
    @ConfigHeader(title = "2. Alert Recipients",
            format = "CSV format",
            example = "johndoe@test.com, janedoe@test.com")
    @Config(key = "email.to.AppSupport", validate = Config.Validate.EmailRecipients,
            desc = "The default alert email recipients")
    protected volatile Set<String> emailToAppSupport;

    @Config(key = "email.to.Development", validate = Config.Validate.EmailRecipients,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToDevelopment;

    @Config(key = "email.to.ReportViewer", validate = Config.Validate.EmailRecipients,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToReportViewer;

    @Config(key = "debouncing.emailalert_minute",
            desc = "Alert message with the same title will not be sent out within this minutes")
    protected volatile int emailAlertDebouncingInterval = 30;

    //3. mail session for Json display only
    private Properties mailSessionProp;

//    @Override
//    public String toString() {
//        Properties p = (Properties) mailSession.getProperties().clone();
//        p.put("mail.smtp.pwd", "***");
//        p.put("cfgFile", cfgFile);
//        p.put("session", mailSession.toString());
//        try {
//            //return "{" + "getConfigFile=" + getConfigFile + ", bindingAddresses=" + bindingAddresses + ", keyManagerFactory=" + keyManagerFactory + ", trustManagerFactory=" + trustManagerFactory + ", sslProtocols=" + Arrays.toString(sslProtocols) + ", sslCipherSuites=" + Arrays.toString(sslCipherSuites) + ", readerIdleTime=" + readerIdleTime + ", writerIdleTime=" + writerIdleTime + ", soBacklog=" + soBacklog + ", soConnectionTimeout=" + soConnectionTimeout + ", sslHandshakeTimeout=" + sslHandshakeTimeout + ", soRcvBuf=" + soRcvBuf + ", soSndBuf=" + soSndBuf + ", maxContentLength=" + maxContentLength + ", tpsWarnThreshold=" + tpsWarnThreshold + ", nioEventLoopGroupAcceptorSize=" + nioEventLoopGroupAcceptorSize + ", nioEventLoopGroupWorkerSize=" + nioEventLoopGroupWorkerSize + ", bizExecutorCoreSize=" + bizExecutorCoreSize + ", bizExecutorMaxSize=" + bizExecutorMaxSize + ", bizExecutorQueueSize=" + bizExecutorQueueSize + ", sslProvider=" + sslProvider + ", httpRequestHandlerImpl=" + httpRequestHandlerImpl + ", tpe=" + tpe + '}';
//            return JsonUtil.toJson(p, true, false);
//        } catch (JsonProcessingException ex) {
//            return ex.toString();
//        }
//    }
//
//    @Override
//    public String info() {
//        return toString();
//    }
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

        mailSession = Session.getInstance(props,
                new jakarta.mail.Authenticator() {
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

    public int getEmailAlertDebouncingInterval() {
        return emailAlertDebouncingInterval;
    }
}
