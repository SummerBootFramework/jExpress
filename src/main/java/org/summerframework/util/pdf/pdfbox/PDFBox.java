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

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class PDFBox {

    public static PDFBox build(String baseDir, String fontDir) throws IOException {
        return new PDFBox(baseDir, fontDir);
    }

    private final PDFBoxConfig cfg;

    private PDFBox(String baseDir, String fontDir) throws IOException {
        cfg = new PDFBoxConfig(baseDir, fontDir);
    }

    public static interface HTMLConverter<T> {

        String toHTML(String templateFileName, T dto) throws IOException;
    }

    public byte[] html2PDF(String templateFileName, HTMLConverter converter, Object dto) throws IOException {
        return this.html2PDF(templateFileName, converter, dto, null);
    }

    public byte[] html2PDF(String templateFileName, HTMLConverter converter, Object dto, PDDocumentInformation info) throws IOException {
        String html = converter.toHTML(templateFileName, dto);

        PdfRendererBuilder builder = new PdfRendererBuilder();
        PDFBoxUtil.setFonts(builder, null, cfg.getFontDir());
        builder.withHtmlContent(html, cfg.getBaseUri());
        if (info != null) {
            builder.withProducer(info.getProducer());
        }
        builder.useFastMode();
        try ( ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            builder.toStream(baos);
            try ( PdfBoxRenderer renderer = builder.buildPdfRenderer();  PDDocument doc = renderer.getPdfDocument();) {
                //security
                doc.protect(PDFBoxUtil.buildStandardProtectionPolicy(null, null));
                doc.setVersion(cfg.getPdfVersion());
//                //info
//                if (info != null) {
//                    doc.setDocumentInformation(info);
//                }
                //build PDF
                renderer.layout();
                renderer.createPDF();
            }
            return baos.toByteArray();
        }
    }

    public static interface Writer<T> {

        void write(PDDocument doc, T dto) throws IOException;
    }

    public byte[] writePDF(Writer writer, Object dto) throws IOException {
        ByteArrayOutputStream out;
        try ( ByteArrayOutputStream baos = new ByteArrayOutputStream();  PDDocument doc = new PDDocument()) {
            PDFBoxUtil.setFonts(null, doc, cfg.getFontDir());
            doc.protect(PDFBoxUtil.buildStandardProtectionPolicy(null, null));
            doc.setVersion(cfg.getPdfVersion());
            out = baos;
            writer.write(doc, dto);
            doc.save(out);
        }
        return out.toByteArray();

    }
}
