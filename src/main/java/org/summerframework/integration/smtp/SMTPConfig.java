/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.integration.smtp;

import org.summerframework.boot.config.AbstractSummerBootConfig;
import org.summerframework.boot.config.ConfigUtil;
import org.summerframework.boot.config.annotation.Config;
import org.summerframework.boot.config.annotation.Memo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

/**
 * Volatile Bean　Pattern
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class SMTPConfig extends AbstractSummerBootConfig {

    public static final SMTPConfig CFG = new SMTPConfig();

    public static void main(String[] args) {
        String t = generateTemplate(SMTPConfig.class);
        System.out.println(t);
    }

    @Override
    public void shutdown() {
    }

    @JsonIgnore
    private volatile Session mailSession;

    //1. SMTP Settings
    @Memo(title = "1. SMTP Settings")
    @JsonIgnore
    @Config(key = "mail.smtp.host")
    protected volatile String smtpHost = "smtp.gmail.com";

    @JsonIgnore
    @Config(key = "mail.smtp.port", required = false)
    protected volatile int smtpPort = 587;

    @JsonIgnore
    @Config(key = "mail.smtp.auth", required = false)
    protected volatile boolean smtpAuth = true;

    @JsonIgnore
    @Config(key = "mail.smtp.starttls.enable", required = false)
    protected volatile boolean smtpStarttls = true;

    @JsonIgnore
    @Config(key = "mail.smtp.userName", desc = "Display name")
    protected volatile String smtpUserDisplayName;

    @JsonIgnore
    @Config(key = "mail.smtp.user", desc = "Email account")
    protected volatile String smtpUser;

    @JsonIgnore
    @Config(key = "mail.smtp.pwd", validate = Config.Validate.Encrypted, required = false)
    protected volatile String smtpPassword;

    //2. Alert Recipients
    @Memo(title = "2. Alert Recipients",
            format = "CSV format",
            example = "johndoe@test.com, janedoe@test.com")
    @Config(key = "email.to.AppSupport", required = false)
    protected volatile Set<String> emailToAppSupport;

    @Config(key = "email.to.Development", required = false,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToDevelopment;

    @Config(key = "email.to.ReportViewer", required = false,
            desc = "use AppSupport if not provided")
    protected volatile Set<String> emailToReportViewer;

    @Config(key = "debouncing.emailalert_minute", defaultValue = "30",
            desc = "Alert message with the same title will not be sent out within this minutes")
    protected volatile int emailAlertDebouncingInterval;

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
