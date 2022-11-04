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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Deprecated
public class EmailBuilder {

    private static final SMTPConfig CFG = new SMTPConfig();

    public static void config(File cfgFile) throws IOException, GeneralSecurityException {

    }

    public static String configInfo() {
        return CFG.toString();
    }

    public static class EmailAttachment implements Serializable {

        private final String type;
        private final byte[] dataStream;
        private final String fileName;
        private String cid;

        /**
         *
         * @param type - the file extention, ie. "pdf", image
         * @param dataStream
         * @param fileName
         * @param cid
         */
        public EmailAttachment(String type, byte[] dataStream, String fileName, String cid) {
            this.type = type;
            this.dataStream = dataStream;
            this.fileName = fileName;
            this.cid = cid;
        }

        public String getType() {
            return type;
        }

        public byte[] getDataStream() {
            return dataStream;
        }

        public String getFileName() {
            return fileName;
        }

        public String getCid() {
            return cid;
        }

        public void setCid(String cid) {
            this.cid = cid;
        }
    }

    public enum Format {
        html, text
    }
    private String from;
    private String[] toList;
    private String[] ccList;
    private String[] bccList;
    private String subject;
    private String body;
    private Format format;
    private List<EmailAttachment> attachments;

    public EmailBuilder() {
    }

    public EmailBuilder(String from, String[] to, String[] cc, String[] bcc, String subject, String body, Format format) {
        this.from = from;
        this.toList = to;
        this.ccList = cc;
        this.bccList = bcc;
        this.subject = subject;// + " at " + (new Date());
        this.body = body;
        this.format = format;
    }

    public void addAttachment(EmailAttachment att) {
        if (attachments == null) {
            attachments = new ArrayList();
        }
        attachments.add(att);
    }

    public void removeAttachments() {
        attachments.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\t from=").append(from)
                .append("\n\t to=").append(Arrays.toString(toList))
                .append("\n\t cc=").append(Arrays.toString(ccList))
                .append("\n\t bcc=").append(Arrays.toString(bccList))
                .append("\n\t subject=").append(subject)
                .append("\n\t format=").append(format)
                .append("\n\t body=").append(body);
        return sb.toString();
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String title, String email) {
        if (title == null) {
            this.from = email;
        } else {
            this.from = title + " <" + email + ">";
        }
    }

    public void setFrom(String email) {
        this.from = email;
    }

    public String[] getToList() {
        return toList;
    }

    public void setToList(String... toList) {
        this.toList = toList;
    }

    public String[] getCcList() {
        return ccList;
    }

    public void setCcList(String... ccList) {
        this.ccList = ccList;
    }

    public String[] getBccList() {
        return bccList;
    }

    public void setBccList(String... bccList) {
        this.bccList = bccList;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;// + " at " + (new Date());
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public List<EmailAttachment> getAttachments() {
        return attachments;
    }

    public MimeMessage buildMimeMessage() throws MessagingException {
        return buildMimeMessage(CFG.getMailSession());
    }

    public MimeMessage buildMimeMessage(Session emailSession) throws MessagingException {
        EmailBuilder email = this;
        if (emailSession == null || email == null) {
            return null;
        }
        MimeMessage message = new MimeMessage(emailSession);
        if (StringUtils.isBlank(email.getFrom())) {
            email.setFrom(emailSession.getProperty("mail.smtp.userName"), emailSession.getProperty("mail.smtp.user"));
        }
        message.setFrom(new InternetAddress(email.getFrom()));
        for (String to : email.getToList()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        }
        if (email.getCcList() != null) {
            for (String cc : email.getCcList()) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
            }
        }
        if (email.getBccList() != null) {
            for (String bcc : email.getBccList()) {
                message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc));
            }
        }
        message.setSentDate(new Date());
        message.setSubject(email.getSubject());

        //construct the mime multi part
        MimeMultipart mimeMultipartList = new MimeMultipart();
        switch (email.getFormat()) {
            case text:
                MimeBodyPart textBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(textBodyPart);
                textBodyPart.setText(email.getBody());
                textBodyPart.setHeader("Content-Transfer-Encoding", "quoted-printable");
                textBodyPart.setHeader("Content-Type", "text/plain; charset=utf-8");
                break;
            case html:
                MimeBodyPart htmlBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(htmlBodyPart);
                htmlBodyPart.setContent(email.getBody(), "text/html; charset=utf-8");
                break;
        }

        //construct the attachement body part
        if (email.getAttachments() != null) {
            for (EmailBuilder.EmailAttachment attachement : email.getAttachments()) {
                MimeBodyPart attachmentBodyPart = new MimeBodyPart();
                mimeMultipartList.addBodyPart(attachmentBodyPart);
                DataSource ds = new ByteArrayDataSource(attachement.getDataStream(), "application/" + attachement.getType());
                attachmentBodyPart.setDataHandler(new DataHandler(ds));
                attachmentBodyPart.setFileName(attachement.getFileName());
                if (attachement.getCid() != null) {
                    // HTML: <img src="cid:my_img_cid">"
                    // JSP:  <img src="cid:<%=attachement.getCid()%>">"
                    attachmentBodyPart.addHeader("Content-ID", "<" + attachement.getCid() + ">");
                }
            }
        }

        //message.setContent(textContent, "text/html; charset=utf-8");
        message.setContent(mimeMultipartList);
        return message;
    }

}
