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
package org.summerframework.util.pdf;

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
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class IText {

//    public static IText build(String baseDir, String fontDir) throws IOException {
//        return new IText(baseDir, fontDir);
//    }
//
//    private final ITextConfig cfg;
//
//    private IText(String baseDir, String fontDir) throws IOException {
//        cfg = new ITextConfig(baseDir, fontDir);
//    }
    private static final Map<String, PdfFont> FONTS = new HashMap<>();

    public static PdfFont getFont(String name) {
        return FONTS.get(name);
    }

    private static FontProvider fontProvider = null;

    public static FontProvider loadFonts(File fontDir) throws IOException {
        if (!fontDir.isDirectory()) {
            throw new IOException(fontDir.getAbsolutePath() + " is not a directory");
        }

        File[] files = fontDir.listFiles((File dir1, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".ttc") || lower.endsWith(".otf");
        });

        if (files == null || files.length < 1) {
            return null;
        }
        for (File file : files) {
            String fileName = file.getName();
            String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
            PdfFont font = PdfFontFactory.createFont(file.getAbsolutePath(), PdfEncodings.IDENTITY_H);
            FONTS.put(fontFamily, font);
        }
        FontSet fontSet = new FontSet();
        fontSet.addDirectory(fontDir.getAbsolutePath(), true);
        fontProvider = new FontProvider(fontSet);
        return fontProvider;
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

    public static byte[] html2PDF(String html, File baseDir, WriterProperties writerProperties) throws IOException {
        ConverterProperties prop = new ConverterProperties();
        prop.setFontProvider(fontProvider);
        prop.setBaseUri(baseDir.getAbsolutePath());
        ByteArrayOutputStream out;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(baos, writerProperties);
                PdfDocument pdfDoc = new PdfDocument(writer);) {
            out = baos;
            pdfDoc.setTagged();
            HtmlConverter.convertToPdf(html, pdfDoc, prop);
        }
        return out.toByteArray();
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
        ByteArrayOutputStream out;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PdfWriter pdfWriter = new PdfWriter(baos, writerProperties);
                PdfDocument pdfDoc = new PdfDocument(pdfWriter); Document document = new Document(pdfDoc)) {
            out = baos;
            writer.write(pdfDoc, document, dto);
        }
        return out.toByteArray();
    }

//    public String loadTemplateFromResources(String filename) {
//        //URL resource = ITextBuilder.class.getClassLoader().getResource(filename);
//        //byte[] bytes = Files.readAllBytes(Paths.get(resource.toURI()));
//        File f = new File(filename).getAbsoluteFile();
//        byte[] bytes;
//        try {
//            bytes = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
//        } catch (IOException ex) {
//            throw new RuntimeException("Failed to load file: " + f, ex);
//        }
//        return new String(bytes, StandardCharsets.UTF_8);
//    }
    /*
    private byte[] sample1(Ticket lottoTicket) throws IOException {
        return ITextBuilder.html2PDF((ticket) -> {
            String html = ITextBuilder.loadTemplateFromResources("resources/html/649.html");//"resources/html/649.html"
            html = html.replaceAll("_title", String.valueOf(System.currentTimeMillis()));
            return html;
        }, lottoTicket, null);
    }

    private byte[] sample2() throws IOException {
        Ticket lottoTicket = new Ticket();
        Writer writer = (pdfDoc, document, ticket) -> {
            String line = "Hello! 中文 Welcome to iTextPdf byte【】[] option1";
            document.add(new Paragraph(line).setFont(PDFFontUtil.DEFAULT_FONT));
        };
        return ITextBuilder.writePDF(writer, lottoTicket, null);
    }

    private byte[] sample3() throws IOException {
        Ticket lottoTicket = new Ticket();
        return ITextBuilder.writePDF((pdfDoc, document, ticket) -> {
            String line = "Hello! 中文 Welcome to iTextPdf byte【】[] option2";
            document.add(new Paragraph(line).setFont(PDFFontUtil.DEFAULT_FONT));
        }, lottoTicket, null);
    }

    private byte[] sample4(Ticket lottoTicket) throws IOException {
        return ITextBuilder.writePDF((pdfDoc, document, ticket) -> {
            Document doc = document;
            String line = "Hello! 中文 Welcome to iTextPdf byte【】[]4";
            doc.add(new Paragraph(line).setFont(PDFFontUtil.DEFAULT_FONT));

            Div div = new Div();
            div.setBackgroundColor(ColorConstants.GREEN);
            div.setWidth(UnitValue.createPercentValue(100));
            div.setHeight(UnitValue.createPercentValue(100));
            div.setHorizontalAlignment(HorizontalAlignment.CENTER);
            Paragraph p1 = new Paragraph();
            p1.setBackgroundColor(ColorConstants.CYAN);
            p1.setHorizontalAlignment(HorizontalAlignment.CENTER);
            p1.setMaxWidth(UnitValue.createPercentValue(75));
            p1.setMarginTop(180f);
            p1.setCharacterSpacing(0.4f);
            Style large = new Style();
            large.setFontSize(22);

            large.setFontColor(ColorConstants.BLUE);
            p1.add(new Text("sss尊敬的 ").addStyle(large).setFont(PDFFontUtil.DEFAULT_FONT));

            Paragraph p2 = new Paragraph();

            div.add(p1);
            //div.add(p2);
            doc.add(div);
        }, lottoTicket, null);
    }
     */
}
