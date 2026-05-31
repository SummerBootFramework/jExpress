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

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.font.FontSet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ItextPdfAgent {

    protected final Map<String, PdfFont> FONTS = new HashMap<>();

    public PdfFont getFont(String name) {
        return FONTS.get(name);
    }

    protected FontSet fontSet = null;

    public ItextPdfAgent(File fontDir) throws IOException {
        if (!fontDir.isDirectory()) {
            throw new IOException(fontDir.getAbsolutePath() + " is not a directory");
        }

        File[] files = fontDir.listFiles((File dir1, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".ttc") || lower.endsWith(".otf");
        });

        if (files == null || files.length < 1) {
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
            PdfFont font = PdfFontFactory.createFont(file.getAbsolutePath(), PdfEncodings.IDENTITY_H);
            FONTS.put(fontFamily, font);
        }
        fontSet = new FontSet();
        fontSet.addDirectory(fontDir.getAbsolutePath(), true);
    }

    public FontSet getFontSet() {
        return fontSet;
    }

    public static WriterProperties buildDefaultAccessPermission(String userPwd, String ownerPwd) {
        return buildDefaultAccessPermission(userPwd, ownerPwd, true, null);
    }

    public static WriterProperties buildDefaultAccessPermission(String userPwd, String ownerPwd, boolean isFullCompressionMode, PdfVersion version) {
        WriterProperties writerProperties = new WriterProperties();
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
        writerProperties.setFullCompressionMode(isFullCompressionMode);
        if (version != null) {
            writerProperties.setPdfVersion(version);
        }

        return writerProperties;
    }

    public byte[] html2PDF(String html, File baseDir, WriterProperties writerProperties) throws IOException {
        ConverterProperties prop = new ConverterProperties();
        if (fontSet != null) {
            prop.setFontProvider(new FontProvider(fontSet));
        }
        prop.setBaseUri(baseDir.getAbsolutePath());
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos, writerProperties);
             PdfDocument pdfDoc = new PdfDocument(writer);) {
            pdfDoc.setTagged();
            HtmlConverter.convertToPdf(html, pdfDoc, prop);
            return baos.toByteArray();
        }
    }

    public static interface Writer<T> {

        void write(PdfDocument pdfDoc, Document document, T dto);
    }

    public static byte[] writePDF(Writer writer, Object dto) throws IOException {
        WriterProperties wp = buildDefaultAccessPermission(null, null);
        return buildPDF(writer, dto, wp);
    }

    public static byte[] buildPDF(Writer writer, Object dto, WriterProperties writerProperties) throws IOException {
        //MemoryStream ms = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter pdfWriter = new PdfWriter(baos, writerProperties);
             PdfDocument pdfDoc = new PdfDocument(pdfWriter); Document document = new Document(pdfDoc)) {
            writer.write(pdfDoc, document, dto);
            return baos.toByteArray();
        }
    }

}
