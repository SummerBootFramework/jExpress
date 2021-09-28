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
package org.summerframework.util.pdf.itext;

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.font.FontProvider;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
final class ITextConfig {

    private String fontDir;// = "resources/font";
    private String baseUri;// = "resources/html";
    private FontProvider fontProvider;
    private final WriterProperties writerProperties = new WriterProperties();

    public ITextConfig(String baseDir, String fontDir) throws IOException {
        this(baseDir, fontDir, PdfVersion.PDF_1_7);
    }

    public ITextConfig(String baseDir, String fontDir, PdfVersion version) throws IOException {
        this.baseUri = baseDir;
        this.fontDir = fontDir;
        if (version != null) {
            writerProperties.setPdfVersion(version);
        }
        setSecurity(null, null);
        setFullCompressionMode(true);
        this.fontProvider = ITextUtil.loadFonts(new File(fontDir));
    }

    public String getFontDir() {
        return fontDir;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public FontProvider getFontProvider() {
        return fontProvider;
    }

    public WriterProperties getWriterProperties() {
        return writerProperties;
    }

    public void setSecurity(String userPwd, String ownerPwd) {
        writerProperties.setStandardEncryption(
                userPwd == null ? null : userPwd.getBytes(),
                ownerPwd == null ? null : ownerPwd.getBytes(),
                EncryptionConstants.ALLOW_PRINTING
                //| EncryptionConstants.ALLOW_COPY
                //| EncryptionConstants.ALLOW_MODIFY_CONTENTS
                | EncryptionConstants.ALLOW_FILL_IN
                //| EncryptionConstants.ALLOW_ASSEMBLY
                | EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS
                | EncryptionConstants.ALLOW_SCREENREADERS,
                EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA)
                .setInitialDocumentId(new PdfString("TEST"));
    }

    public void setFullCompressionMode(boolean mode) {
        writerProperties.setFullCompressionMode(mode);
    }

}
