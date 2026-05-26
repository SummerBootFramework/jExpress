/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.integration.pdf;

import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.WriterProperties;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PDFBuilderConfig {
    public enum Agnet {
        iText, PDFBox
    }

    public static final PDFBuilderConfig buildProtectedConfig() {
        return new PDFBuilderConfig();
    }

    public static final PDFBuilderConfig buildUnprotectedConfig() {
        PDFBuilderConfig cfg = new PDFBuilderConfig();
        cfg.setProtectionSpec(ProtectionSpec.UNPROTECTED);
        return cfg;
    }

    private PDDocumentInformation docInfo;
    private Agnet agnet = Agnet.PDFBox;
    private float pdfVersion = 2.0f; // PDFBox
    private PdfVersion version = PdfVersion.PDF_2_0; // iText
    private boolean isFullCompressionMode = true; // iText
    private ProtectionSpec protectionSpec = ProtectionSpec.UNPROTECTED;
    private String ownerPwd = null;
    private String userPwd = null;
    private int encryptionKeyLength = ProtectionSpec.EncryptionKeyLength; // PDFBox
    private int encryptionAlgorithm = ProtectionSpec.EncryptionAlgorithm; // iText

    public PDFBuilderConfig() {
        this(null);
    }

    public PDFBuilderConfig(PDDocumentInformation docInfo) {
        if (docInfo == null) {
            this.docInfo = new PDDocumentInformation();
            this.docInfo.setProducer("jExpress");
        } else {
            this.docInfo = docInfo;
        }
    }

    public StandardProtectionPolicy buildProtectionPolicy() {
        return protectionSpec.buildProtectionPolicy(ownerPwd, userPwd, encryptionKeyLength);
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
        // Allowed discrete PDF versions
        float[] allowed = new float[]{1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 2.0f};
        if (Float.isNaN(pdfVersion)) {
            // ignore invalid input
            return;
        }

        // Find the allowed version closest to the requested value
        float closest = allowed[0];
        float minDiff = Math.abs(pdfVersion - closest);
        for (int i = 1; i < allowed.length; i++) {
            float d = Math.abs(pdfVersion - allowed[i]);
            if (d < minDiff) {
                minDiff = d;
                closest = allowed[i];
            }
        }

        this.pdfVersion = closest;
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
