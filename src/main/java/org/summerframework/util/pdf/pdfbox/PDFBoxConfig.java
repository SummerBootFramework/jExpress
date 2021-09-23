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
package org.summerframework.util.pdf.pdfbox;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
class PDFBoxConfig {

    private final File fontDir;// = "resources/font";
    private final String baseUri;// = "resources/html";
    private final float pdfVersion;// = 1.7f;

    public PDFBoxConfig(String baseDir, String fontDir) throws IOException {
        this(baseDir, fontDir, 1.7f);
    }

    public PDFBoxConfig(String baseDir, String fontDir, float pdfVersion) throws IOException {
        this.baseUri = buildBaseDocumentUri1(new File(baseDir).getAbsoluteFile());
        this.fontDir = new File(fontDir).getAbsoluteFile();
        this.pdfVersion = pdfVersion;
    }

    private String buildBaseDocumentUri1(File baseDirectory) throws IOException {
        try {
            //return new File(baseDirectory, htmlFileName).toURI().toURL().toExternalForm();
            return baseDirectory.toURI().toURL().toExternalForm();
        } catch (MalformedURLException ex) {
            throw new IOException("Invalid baseDirectory=" + baseDirectory, ex);
        }
    }

    public File getFontDir() {
        return fontDir;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public float getPdfVersion() {
        return pdfVersion;
    }

}
