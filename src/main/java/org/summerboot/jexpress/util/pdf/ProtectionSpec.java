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

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.WriterProperties;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.BeanUtil;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public record ProtectionSpec(
        Boolean readOnly,
        Boolean canAssembleDocument, // allow page reordering/insertion/deletion/assembly operations
        Boolean canModify, // allow general document editing
        Boolean canModifyAnnotations, // allow comments/annotations/form markup edits
        Boolean canExtractContent, // allow copy/extract text/images
        Boolean canExtractForAccessibility, // allow extraction for screen readers/accessibility tools
        Boolean canFillInForm, // allow filling forms
        Boolean canPrintFaithful, // allow high-quality/full-fidelity printing
        Boolean canPrint // allow normal printing
) {

    public ProtectionSpec {
        canAssembleDocument = canAssembleDocument != null ? canAssembleDocument : false;
        canExtractContent = canExtractContent != null ? canExtractContent : false;
        canExtractForAccessibility = canExtractForAccessibility != null ? canExtractForAccessibility : true;
        canFillInForm = canFillInForm != null ? canFillInForm : true;
        canModify = canModify != null ? canModify : false;
        canModifyAnnotations = canModifyAnnotations != null ? canModifyAnnotations : true;
        canPrint = canPrint != null ? canPrint : true;
        canPrintFaithful = canPrintFaithful != null ? canPrintFaithful : true;
        readOnly = readOnly != null ? readOnly : true;
    }

    public static ProtectionSpec PROTECTED = new ProtectionSpec(true, false, false, false, false, false, true, true, true);
    public static ProtectionSpec UNPROTECTED = new ProtectionSpec(false, true, true, true, true, true, true, true, true);

    public static ProtectionSpec init(String json) {
        return BeanUtil.fromJson(json, ProtectionSpec.class);
    }

    public static final int EncryptionKeyLength = 256;
    public static final int EncryptionAlgorithm = EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;

    public static int getEncryptionKeyLength(int encryptionKeyLength) {
        if (encryptionKeyLength >= 192) {
            return EncryptionKeyLength;
        }
        if (encryptionKeyLength <= 48) {
            return 48;
        }
        return 128;
    }

    public static String generatePassword() {
        return generatePassword(10);
    }

    public static String generatePassword(int count) {
        return SecurityUtil.randomAlphanumeric(count);
    }

    public AccessPermission buildAccessPermission() {
        AccessPermission ap = new AccessPermission();
        ap.setCanAssembleDocument(canAssembleDocument);
        ap.setCanExtractContent(canExtractContent);
        ap.setCanExtractForAccessibility(canExtractForAccessibility);
        ap.setCanFillInForm(canFillInForm);
        ap.setCanModify(canModify);
        ap.setCanModifyAnnotations(canModifyAnnotations);
        ap.setCanPrint(canPrint);
        ap.setCanPrintFaithful(canPrintFaithful);
        if (readOnly) {
            ap.setReadOnly();
        }
        return ap;
    }

    public WriterProperties buildWriterProperties(String ownerPwd, String userPwd) {
        return buildWriterProperties(ownerPwd, userPwd, true, null);
    }

    public WriterProperties buildWriterProperties(String ownerPwd, String userPwd, boolean isFullCompressionMode, PdfVersion version) {
        return buildWriterProperties(ownerPwd, userPwd, EncryptionAlgorithm, isFullCompressionMode, version);
    }

    public WriterProperties buildWriterProperties(String ownerPwd, String userPwd, int encryptionAlgorithm, boolean isFullCompressionMode, PdfVersion version) {
        byte[] user = userPwd == null ? null : userPwd.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] owner = ownerPwd == null ? null : ownerPwd.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int permissions = 0;
        if (canPrint) {
            permissions |= EncryptionConstants.ALLOW_PRINTING;
        }
        if (canPrintFaithful) {
            permissions |= EncryptionConstants.ALLOW_DEGRADED_PRINTING;
        }
        if (canExtractContent) {
            permissions |= EncryptionConstants.ALLOW_COPY;
        }
        if (canExtractForAccessibility) {
            permissions |= EncryptionConstants.ALLOW_SCREENREADERS;
        }
        if (canModify) {
            permissions |= EncryptionConstants.ALLOW_MODIFY_CONTENTS;
        }
        if (canModifyAnnotations) {
            permissions |= EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS;
        }
        if (canFillInForm) {
            permissions |= EncryptionConstants.ALLOW_FILL_IN;
        }
        if (canAssembleDocument) {
            permissions |= EncryptionConstants.ALLOW_ASSEMBLY;
        }

        // AES-256 to align with your PDFBox default key length 256.
        WriterProperties writerProperties = new WriterProperties()
                .setStandardEncryption(
                        user,
                        owner,
                        permissions,
                        encryptionAlgorithm
                );
        writerProperties.setFullCompressionMode(isFullCompressionMode);
        if (version != null) {
            writerProperties.setPdfVersion(version);
        }
        return writerProperties;
    }

    public StandardProtectionPolicy buildProtectionPolicy(String ownerPassword, String userPassword, int encryptionKeyLength) {
        AccessPermission ap = buildAccessPermission();
        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPassword, userPassword, ap);
        spp.setEncryptionKeyLength(getEncryptionKeyLength(encryptionKeyLength));
        return spp;
    }

    public static StandardProtectionPolicy defaultProtectionPolicy() {
        AccessPermission ap = AccessPermission.getOwnerAccessPermission();
        String ownerPassword = null;//SecurityUtil.randomAlphanumeric(10);
        StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(ownerPassword, null, ap);
        protectionPolicy.setEncryptionKeyLength(256);
        return protectionPolicy;
    }
}
