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

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.PageBox;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.ProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PDFBox {

    /**
     * user space units per inch
     */
    private static final float POINTS_PER_INCH = 72;

    /**
     * user space units per millimeter
     */
    private static final float POINTS_PER_MM = 75.5875f;

    private static final Map<String, PDFont> FONTS = new HashMap();

    public static PDFont getFont(String name) {
        return FONTS.get(name);
    }

    private static File[] fontFiles = null;

    private static Map<File, String> fonts = null;

    public static int loadFonts(File fontCacheDir, File fontDir) throws IOException {
        if (fontCacheDir != null) {
            //java -Dpdfbox.fontcache=/tmp
            fontCacheDir.mkdirs();
            System.setProperty("pdfbox.fontcache", fontCacheDir.getAbsolutePath());
        }
        if (fontDir == null) {
            return 0;
        }
        if (!fontDir.isDirectory()) {
            throw new IOException("Not a directory: " + fontDir);
        }
        fontFiles = fontDir.listFiles((File dir1, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".ttc");// || lower.endsWith(".otf");
        });
        if (fontFiles != null && fontFiles.length > 0) {
            fonts = new HashMap();
            for (File file : fontFiles) {
                String fileName = file.getName();
                String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
                fonts.put(file.getAbsoluteFile(), fontFamily);
            }
        }

//        if (fontFiles == null || fontFiles.length < 1) {
//            throw new IOException("No font files found: " + fontDir);
//        }
        return fontFiles == null ? 0 : fontFiles.length;
    }

    public static File[] getFontFiles() {
        return fontFiles;
    }

    /**
     *
     * @return {@code <font file, fontFamily>}
     */
    public static Map<File, String> getFonts() {
        return Map.copyOf(fonts);
    }

    public static int useFonts(PdfRendererBuilder builder, PDDocument doc) throws IOException {
        if (fonts == null || fonts.isEmpty()) {
            //throw new IOException("No font loaded: call PDFBoxUtil.loadFonts(File fontDir) first");
            return 0;
        }
        for (File fontFile : fonts.keySet()) {
            //String fileName = fontFile.getName();
            //String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
            String fontFamily = fonts.get(fontFile);
            if (builder != null) {
                builder.useFont(fontFile, fontFamily);
            }
            if (doc != null) {
                String fileName = fontFile.getName();
                if (!fileName.endsWith(".otf")) {
                    PDFont font = PDType0Font.load(doc, fontFile);
                    //PDFont font =        PDType0Font.load(doc, new File(PDFBoxConfig.CFG.getFontDir() + "\\SimHei.ttf"));
                    FONTS.put(fontFamily, font);
                }
            }
        }
        return fontFiles.length;
    }

    private static final AccessPermission DEFAULT_AP = buildDefaultAccessPermission();
    private static final int DEFAULT_KEY_LENGTH = 256;

    public static AccessPermission buildDefaultAccessPermission() {
        AccessPermission ap = new AccessPermission();
        ap.setCanAssembleDocument(false);
        ap.setCanExtractContent(false);
        ap.setCanExtractForAccessibility(true);
        ap.setCanFillInForm(true);
        ap.setCanModify(false);
        ap.setCanModifyAnnotations(true);
        ap.setCanPrint(true);
        ap.setCanPrintDegraded(true);
        ap.setReadOnly();
        return ap;
    }

    public static StandardProtectionPolicy buildStandardProtectionPolicy(String userPwd, String ownerPwd) {
        return buildStandardProtectionPolicy(userPwd, ownerPwd, DEFAULT_AP, DEFAULT_KEY_LENGTH);
    }

    public static StandardProtectionPolicy buildStandardProtectionPolicy(String userPwd, String ownerPwd, AccessPermission ap, int keyLenth) {
        if (StringUtils.isBlank(ownerPwd)) {
            ownerPwd = RandomStringUtils.randomAlphanumeric(10);
        }
        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
        spp.setEncryptionKeyLength(keyLenth);
        return spp;
    }

    public static byte[] html2PDF(String html, File baseDir, ProtectionPolicy protectionPolicy, PDDocumentInformation info, float pdfVersion,
            float pageWidth, float pageHeight, BaseRendererBuilder.PageSizeUnits units) throws IOException {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        useFonts(builder, null);
        builder.withHtmlContent(html, buildBaseDocumentUri1(baseDir));
        if (info != null) {
            builder.withProducer(info.getProducer());
        }
        builder.useFastMode();
        if (units != null) {
            builder.useDefaultPageSize(pageWidth, pageHeight, units);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            builder.toStream(baos);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer(); PDDocument doc = renderer.getPdfDocument();) {
                //security
                if (protectionPolicy == null) {
                    protectionPolicy = buildStandardProtectionPolicy(null, null);
                }
                doc.protect(protectionPolicy);
                doc.setVersion(pdfVersion);
//                //info
//                if (info != null) {
//                    doc.setDocumentInformation(info);
//                }
                //build PDF
                renderer.layout();//com.openhtmltopdf.load INFO:: Loading font(ArialUnicodeMS) from PDFont supplier now.
                renderer.createPDF();//com.openhtmltopdf.general INFO:: Using fast-mode renderer. Prepare to fly.
            }

            return baos.toByteArray();
        }
    }

    public static byte[] html2PDF(String html, File baseDir, ProtectionPolicy protectionPolicy, PDDocumentInformation info, float pdfVersion) throws IOException {
        float pageWidth, pageHeight;
        BaseRendererBuilder.PageSizeUnits units = BaseRendererBuilder.PageSizeUnits.MM;
        PdfRendererBuilder builderTemp = new PdfRendererBuilder();
        useFonts(builderTemp, null);
        builderTemp.withHtmlContent(html, buildBaseDocumentUri1(baseDir));
        if (info != null) {
            builderTemp.withProducer(info.getProducer());
        }
        builderTemp.useFastMode();
        try (PdfBoxRenderer renderer = builderTemp.buildPdfRenderer(); PDDocument doc = renderer.getPdfDocument();) {//need to close doc if use box
            renderer.layout();
            // The root box is <html>, the first child is <body>, then <div>.
            Box box = renderer.getRootBox();
            pageWidth = box.getWidth();
            pageHeight = box.getHeight();
            List<PageBox> pageList = box.getLayer().getPages();
            int pageCount = pageList.size();
            if (pageCount > 1) {
                pageHeight = pageHeight * pageCount;
            }
            pageWidth = pageWidth / POINTS_PER_MM;
            pageHeight = pageHeight / POINTS_PER_MM;
            html = html.replaceFirst("1mm;", pageHeight+"mm;");
        }

        PdfRendererBuilder builder = new PdfRendererBuilder();
        useFonts(builder, null);
        builder.withHtmlContent(html, buildBaseDocumentUri1(baseDir));
        if (info != null) {
            builder.withProducer(info.getProducer());
        }
        builder.useFastMode();

        //builder.useDefaultPageSize(pageWidth, pageHeight, units);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            builder.toStream(baos);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer(); PDDocument doc = renderer.getPdfDocument();) {
                //security
                if (protectionPolicy == null) {
                    protectionPolicy = buildStandardProtectionPolicy(null, null);
                }
                doc.protect(protectionPolicy);
                doc.setVersion(pdfVersion);
//                //info
//                if (info != null) {
//                    doc.setDocumentInformation(info);
//                }
                //build PDF
                renderer.layout();//com.openhtmltopdf.load INFO:: Loading font(ArialUnicodeMS) from PDFont supplier now.
                renderer.createPDF();//com.openhtmltopdf.general INFO:: Using fast-mode renderer. Prepare to fly.
            }

            return baos.toByteArray();
        }
    }

    private static String buildBaseDocumentUri1(File baseDirectory) throws IOException {
        try {
            //return new File(baseDirectory, htmlFileName).toURI().toURL().toExternalForm();
            return baseDirectory.toURI().toURL().toExternalForm();
        } catch (MalformedURLException ex) {
            throw new IOException("Invalid baseDirectory=" + baseDirectory, ex);
        }
    }

    /**
     *
     * @param pdfData
     * @param dpi
     * @param formatName a {@code String} containing the informal name of a
     * format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(byte[] pdfData, float dpi, String formatName) throws IOException {
        return pdf2Images(pdfData, dpi, ImageType.RGB, formatName);
    }

    /**
     *
     * @param pdfData
     * @param dpi
     * @param imageType
     * @param formatName a {@code String} containing the informal name of a
     * format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(byte[] pdfData, float dpi, ImageType imageType, String formatName) throws IOException {
        List<BufferedImage> images = pdf2Images(pdfData, dpi, imageType);
        List<byte[]> imageDatas = images2Bytes(images, formatName);
        return imageDatas;

    }

    /**
     *
     * @param pdfFile
     * @param dpi
     * @param imageType
     * @param formatName a {@code String} containing the informal name of a
     * format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(File pdfFile, float dpi, ImageType imageType, String formatName) throws IOException {
        List<BufferedImage> images = pdf2Images(pdfFile, dpi, imageType);
        List<byte[]> imageDatas = images2Bytes(images, formatName);
        return imageDatas;
    }

    public static List<BufferedImage> pdf2Images(byte[] pdfData, float dpi, ImageType imageType) throws IOException {
        //1: Loading an Existing PDF Document
        try (PDDocument document = PDDocument.load(pdfData);) {
            return pdf2Images(document, dpi, imageType);
        }
    }

    public static List<BufferedImage> pdf2Images(File pdfFile, float dpi, ImageType imageType) throws IOException {
        //1: Loading an Existing PDF Document
        try (PDDocument document = PDDocument.load(pdfFile);) {
            return pdf2Images(document, dpi, imageType);
        }
    }

    /**
     *
     * @param document make sure the caller will close the document
     * @param dpi 300
     * @param imageType
     * @return
     * @throws IOException
     */
    public static List<BufferedImage> pdf2Images(PDDocument document, float dpi, ImageType imageType) throws IOException {
        //1: Loading an Existing PDF Document
        //try(PDDocument document = PDDocument.load(pdfData);)
        //2: Instantiating the PDFRenderer Class
        PDFRenderer renderer = new PDFRenderer(document);
        //3: Rendering Image from the PDF Document
        //BufferedImage image = renderer.renderImage(0);
        //4: save to file
        //ImageIO.write(image, "JPEG", new File("C:/PdfBox_Examples/myimage.jpg"));

        int totalPages = document.getNumberOfPages();
        List<BufferedImage> images = new ArrayList();
        for (int currentPage = 0; currentPage < totalPages; currentPage++) {
            BufferedImage image = renderer.renderImageWithDPI(currentPage, dpi, imageType);
            images.add(image);
        }
        return images;
    }

    /**
     *
     * @param images
     * @param formatName a {@code String} containing the informal name of a
     * format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @return
     * @throws IOException
     */
    public static List<byte[]> images2Bytes(List<BufferedImage> images, String formatName) throws IOException {
        List<byte[]> imageDataList = new ArrayList(images.size());
        for (BufferedImage image : images) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
                ImageIO.write(image, formatName, baos);
                byte[] imageData = baos.toByteArray();
                imageDataList.add(imageData);
            }
        }
        return imageDataList;
    }

    public static interface Writer<T> {

        void write(PDDocument doc, T dto) throws IOException;
    }

    public static byte[] writePDF(Writer writer, Object dto, float pdfVersion) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PDDocument doc = new PDDocument()) {
            useFonts(null, doc);
            doc.protect(buildStandardProtectionPolicy(null, null));
            doc.setVersion(pdfVersion);
            writer.write(doc, dto);
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
