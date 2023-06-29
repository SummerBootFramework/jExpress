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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class Email {

    //protected static AlertEmailConfig smtpCfg = AlertEmailConfig.instance(AlertEmailConfig.class);
    public static Email compose(String subject, String body, Format format) {
        return new Email(subject, body, format);
    }

    public static class Attachment implements Serializable {

        private final String type;
        private final byte[] dataStream;
        private final String fileName;
        private String cid;

        /**
         *
         * @param type - the file extention, ie. "pdf", image
         * @param dataStream
         * @param fileName
         * @param cid - {@code<img src="cid:logo">}
         */
        public Attachment(String type, byte[] dataStream, String fileName, String cid) {
            this.type = type;
            this.dataStream = dataStream;
            this.fileName = fileName;
            this.cid = cid;
        }

        public String type() {
            return type;
        }

        public byte[] getDataStream() {
            return dataStream;
        }

        public String fileName() {
            return fileName;
        }

        public String cid() {
            return cid;
        }

        public Attachment cid(String cid) {
            this.cid = cid;
            return this;
        }
    }

    public enum Format {
        html, text
    }
    private String from;
    private Set<String> toList;
    private Set<String> ccList;
    private Set<String> bccList;
    private String subject;
    private String body;
    private Format format;
    private List<Attachment> attachments;

    private Email(String subject, String body, Format format) {
        this.subject = subject;// + " at " + (new Date());
        this.body = body;
        this.format = format;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\t from=").append(from)
                .append("\n\t to=").append(toList)
                .append("\n\t cc=").append(ccList)
                .append("\n\t bcc=").append(bccList)
                .append("\n\t subject=").append(subject)
                .append("\n\t format=").append(format)
                .append("\n\t body=").append(body);
        return sb.toString();
    }

    public String from() {
        return from;
    }

    public Email from(String userDisplayName, String userEmailAddr) {
        if (userDisplayName == null) {
            this.from = userEmailAddr;
        } else {
            this.from = userDisplayName + " <" + userEmailAddr + ">";
        }
        return this;
    }

    public Email from(String email) {
        this.from = email;
        return this;
    }

    public Set<String> to() {
        return toList;
    }

    public Email to(Collection<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return this;
        }
        this.toList = new TreeSet<>(recipients);
        return this;
    }

    public Email to(String... recipients) {
        if (recipients != null && recipients.length > 0) {
            this.toList = Set.of(recipients);
        }
        return this;
    }

    public Set<String> cc() {
        return ccList;
    }

    public Email cc(Collection<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return this;
        }
        this.ccList = new TreeSet<>(recipients);
        return this;
    }

    public Email cc(String... recipients) {
        if (recipients != null && recipients.length > 0) {
            this.ccList = Set.of(recipients);
        }
        return this;
    }

    public Set<String> bcc() {
        return bccList;
    }

    public Email bcc(Collection<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return this;
        }
        this.bccList = new TreeSet<>(recipients);
        return this;
    }

    public Email bcc(String... recipients) {
        if (recipients != null && recipients.length > 0) {
            this.bccList = Set.of(recipients);
        }
        return this;
    }

    public String subject() {
        return subject;
    }

    public Email subject(String subject) {
        this.subject = subject == null ? "" : subject;// + " at " + (new Date());
        return this;
    }

    public String body() {
        return body;
    }

    public Email body(String body) {
        this.body = body;
        return this;
    }

    public Format format() {
        return format;
    }

    public Email format(Format format) {
        this.format = format;
        return this;
    }

    public List<Attachment> attachments() {
        return attachments;
    }

    public Email attachment(Attachment att) {
        if (attachments == null) {
            attachments = new ArrayList();
        }
        attachments.add(att);
        return this;
    }

    public Email clearAttachments() {
        attachments.clear();
        return this;
    }

    public void send(Session emailSession) throws MessagingException {
        MimeMessage msg = buildMimeMessage(emailSession);
        if (msg != null) {
            Transport.send(msg);
        }
    }

    public MimeMessage buildMimeMessage(Session emailSession) throws MessagingException {
        Email email = this;
        if (emailSession == null || email == null) {
            return null;
        }
        MimeMessage message = new MimeMessage(emailSession);
        if (StringUtils.isBlank(email.from())) {
            email.from(emailSession.getProperty(SMTPClientConfig.KEY_USER_DISPLAYNAME), emailSession.getProperty(SMTPClientConfig.KEY_USER_ACCOUNT));
        }
        message.setFrom(new InternetAddress(email.from()));
        if (email.to() != null) {
            for (String to : email.to()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
        }
        if (email.cc() != null) {
            for (String cc : email.cc()) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
            }
        }
        if (email.bcc() != null) {
            for (String bcc : email.bcc()) {
                message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
            }
        }
        message.setSentDate(new Date());
        message.setSubject(email.subject());

        //construct the mime multi part
        MimeMultipart mimeMultipartList = new MimeMultipart();
        switch (email.format()) {
            case text:
                MimeBodyPart textBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(textBodyPart);
                textBodyPart.setText(email.body());
                textBodyPart.setHeader("Content-Transfer-Encoding", "quoted-printable");
                textBodyPart.setHeader("Content-Type", "text/plain; charset=utf-8");
                break;
            case html:
                MimeBodyPart htmlBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(htmlBodyPart);
                htmlBodyPart.setContent(email.body(), "text/html; charset=utf-8");
                break;
        }

        //construct the attachement body part
        if (email.attachments() != null) {
            for (Email.Attachment attachement : email.attachments()) {
                MimeBodyPart attachmentBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(attachmentBodyPart);
                DataSource ds = new ByteArrayDataSource(attachement.getDataStream(), "application/" + attachement.type());
                attachmentBodyPart.setDataHandler(new DataHandler(ds));
                attachmentBodyPart.setFileName(attachement.fileName());
                if (attachement.cid() != null) {
                    // HTML: <img src="cid:my_img_cid">"
                    // JSP:  <img src="cid:<%=attachement.cid()%>">"
                    attachmentBodyPart.addHeader("Content-ID", "<" + attachement.cid() + ">");
                }
            }
        }

        //message.setContent(textContent, "text/html; charset=utf-8");
        message.setContent(mimeMultipartList);
        return message;
    }

    public void sendA() {
        String regionName = "AWS_SES_Region";
        String accessKy = "AWS_SES_AccessKey";
        String secretKey = "AWS_SES_SecretKey";
        /*
        <!-- https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk-ses -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-ses</artifactId>
    <version>1.11.816</version>
</dependency>
         */

        //AmazonSimpleEmailServiceClientBuilder builderSES = AWS_SES_Util.initAmazonSimpleEmailServiceClientBuilder(regionName, accessKy, secretKey);
    }

}
