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
package org.summerboot.jexpress.util.pdf;

import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.WriterProperties;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PDFRequirement {
    public enum Agnet {
        iText, PDFBox
    }

    public static final PDFRequirement DefaultRequirement = new PDFRequirement();


    private PDDocumentInformation docInfo;
    private Agnet agnet = Agnet.PDFBox;
    private float pdfVersion = 1.7f; // PDFBox
    private PdfVersion version = PdfVersion.PDF_1_7; // iText
    private boolean isFullCompressionMode = true; // iText
    private ProtectionSpec protectionSpec = ProtectionSpec.UNPROTECTED;
    private String ownerPwd = null;
    private String userPwd = null;
    private int encryptionKeyLength = ProtectionSpec.EncryptionKeyLength; // PDFBox
    private int encryptionAlgorithm = ProtectionSpec.EncryptionAlgorithm; // iText

    public PDFRequirement() {
        this(null);
    }

    public PDFRequirement(PDDocumentInformation docInfo) {
        if (docInfo == null) {
            this.docInfo = new PDDocumentInformation();
            this.docInfo.setProducer("jExpress");
        } else {
            this.docInfo = docInfo;
        }
    }

    public StandardProtectionPolicy buildProtectionPolicy() {
        return protectionSpec.buildProtectionPolicy(ownerPwd, ownerPwd, encryptionKeyLength);
    }

    public WriterProperties buildWriterProperties() {
        return protectionSpec.buildWriterProperties(ownerPwd, userPwd, encryptionAlgorithm, isFullCompressionMode, version);
    }

    public PDDocumentInformation getDocInfo() {
        return docInfo;
    }

    public void setDocInfo(PDDocumentInformation docInfo) {
        this.docInfo = docInfo;
    }

    public Agnet getAgnet() {
        return agnet;
    }

    public void setAgnet(Agnet agnet) {
        this.agnet = agnet;
    }

    public float getPdfVersion() {
        return pdfVersion;
    }

    public void setPdfVersion(float pdfVersion) {
        this.pdfVersion = pdfVersion;
    }

    public PdfVersion getVersion() {
        return version;
    }

    public void setVersion(PdfVersion version) {
        this.version = version;
    }

    public boolean isFullCompressionMode() {
        return isFullCompressionMode;
    }

    public void setFullCompressionMode(boolean fullCompressionMode) {
        isFullCompressionMode = fullCompressionMode;
    }

    public ProtectionSpec getProtectionSpec() {
        return protectionSpec;
    }

    public void setProtectionSpec(ProtectionSpec protectionSpec) {
        this.protectionSpec = protectionSpec;
    }

    public String getOwnerPwd() {
        return ownerPwd;
    }

    public void setOwnerPwd(String ownerPwd) {
        this.ownerPwd = ownerPwd;
    }

    public String getUserPwd() {
        return userPwd;
    }

    public void setUserPwd(String userPwd) {
        this.userPwd = userPwd;
    }

    public int getEncryptionKeyLength() {
        return encryptionKeyLength;
    }

    public void setEncryptionKeyLength(int encryptionKeyLength) {
        this.encryptionKeyLength = encryptionKeyLength;
    }

    public int getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(int encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }
}
