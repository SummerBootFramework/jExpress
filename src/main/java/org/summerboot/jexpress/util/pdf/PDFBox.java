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
import org.apache.pdfbox.rendering.RenderDestination;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PDFBox {

    public static final float POINTS_PER_MM = 75f;
    protected static final Map<String, PDFont> FONTS = new HashMap();

    public static PDFont getFont(String name) {
        return FONTS.get(name);
    }

    protected static File[] fontFiles = null;

    protected static Map<File, String> fonts = null;

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

    protected static final AccessPermission DEFAULT_AP = buildDefaultAccessPermission();
    protected static final int DEFAULT_KEY_LENGTH = 256;

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

    public static class LayoutInfo {

        protected final int pageCount;
        protected final int pageWidth;
        protected final int pageHeight;

        public LayoutInfo(int pageCount, int pageWidth, int pageHeight) {
            this.pageCount = pageCount;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        public int getPageCount() {
            return pageCount;
        }

        public int getPageWidth() {
            return pageWidth;
        }

        public int getPageHeight() {
            return pageHeight;
        }

    }

    /**
     * <pre>{@code
     * <html>
     * <head>
     * <style>
     *  @page {
     *      margin: 0px;
     *      size: ${pageWidth}mm 1mm;
     *  }
     * </style>
     * </head>
     * </html>
     *
     * if (isSinglePage) {
     *      PDFBox.LayoutInfo layoutInfo = PDFBox.layoutThenGetInfo(html, baseDir);
     *      if (layoutInfo.getPageCount() > 1) {
     *          float pageHeightMillimeters = layoutInfo.getPageHeight() * layoutInfo.getPageCount() / PDFBox.POINTS_PER_MM + 5;//add extral space
     *          html = html.replaceFirst("1mm;", pageHeightMillimeters + "mm;");
     *      }
     * }
     * }</pre>
     *
     * @param html
     * @param baseDir
     * @return
     * @throws IOException
     */
    public static LayoutInfo layoutThenGetInfo(String html, File baseDir) throws IOException {
        LayoutInfo ret;
        PdfRendererBuilder builderTemp = new PdfRendererBuilder();
        useFonts(builderTemp, null);
        builderTemp.withHtmlContent(html, buildBaseDocumentUri1(baseDir));
        builderTemp.useFastMode();
        try (PdfBoxRenderer renderer = builderTemp.buildPdfRenderer(); PDDocument doc = renderer.getPdfDocument();) {//need to close doc if use box
            renderer.layout();
            // The root box is <html>, the first child is <body>, then <div>.
            Box box = renderer.getRootBox();//1mm=76; 2mm=151;
            List<PageBox> pageList = box.getLayer().getPages();//1mm=215p; 2mm=110p;
            ret = new LayoutInfo(pageList.size(), box.getWidth(), box.getHeight());
        }
        return ret;
    }

    public static byte[] html2PDF(String html, File baseDir, ProtectionPolicy protectionPolicy, PDDocumentInformation info, float pdfVersion) throws IOException {
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

    protected static String buildBaseDocumentUri1(File baseDirectory) throws IOException {
        try {
            //return new File(baseDirectory, htmlFileName).toURI().toURL().toExternalForm();
            return baseDirectory.toURI().toURL().toExternalForm();
        } catch (MalformedURLException ex) {
            throw new IOException("Invalid baseDirectory=" + baseDirectory, ex);
        }
    }

    /**
     * @param pdfData
     * @param dpi
     * @param formatName  a {@code String} containing the informal name of a
     *                    format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @param destination
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(byte[] pdfData, float dpi, String formatName, RenderDestination destination) throws IOException {
        return pdf2Images(pdfData, dpi, ImageType.RGB, formatName, destination);
    }

    /**
     * @param pdfData
     * @param dpi
     * @param imageType
     * @param formatName  a {@code String} containing the informal name of a
     *                    format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @param destination
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(byte[] pdfData, float dpi, ImageType imageType, String formatName, RenderDestination destination) throws IOException {
        List<BufferedImage> images = pdf2Images(pdfData, dpi, imageType, destination);
        List<byte[]> imageDatas = images2Bytes(images, formatName);
        return imageDatas;

    }

    /**
     * @param pdfFile
     * @param dpi
     * @param imageType
     * @param formatName  a {@code String} containing the informal name of a
     *                    format (<i>e.g.</i>, "jpeg", "png" or "tiff".
     * @param destination
     * @return
     * @throws IOException
     */
    public static List<byte[]> pdf2Images(File pdfFile, float dpi, ImageType imageType, String formatName, RenderDestination destination) throws IOException {
        List<BufferedImage> images = pdf2Images(pdfFile, dpi, imageType, destination);
        List<byte[]> imageDatas = images2Bytes(images, formatName);
        return imageDatas;
    }

    public static List<BufferedImage> pdf2Images(byte[] pdfData, float dpi, ImageType imageType, RenderDestination destination) throws IOException {
        //1: Loading an Existing PDF Document
        try (PDDocument document = PDDocument.load(pdfData);) {
            return pdf2Images(document, dpi, imageType, destination);
        }
    }

    public static List<BufferedImage> pdf2Images(File pdfFile, float dpi, ImageType imageType, RenderDestination destination) throws IOException {
        //1: Loading an Existing PDF Document
        try (PDDocument document = PDDocument.load(pdfFile);) {
            return pdf2Images(document, dpi, imageType, destination);
        }
    }

    /**
     * @param document    make sure the caller will close the document
     * @param dpi         300
     * @param imageType
     * @param destination
     * @return
     * @throws IOException
     */
    public static List<BufferedImage> pdf2Images(PDDocument document, float dpi, ImageType imageType, RenderDestination destination) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        int totalPages = document.getNumberOfPages();
        List<BufferedImage> images = new ArrayList();
        for (int currentPage = 0; currentPage < totalPages; currentPage++) {
            BufferedImage image = renderer.renderImage(currentPage, dpi / 72f, imageType, destination);
            images.add(image);
        }
        return images;
    }

    /**
     * @param images
     * @param formatName a {@code String} containing the informal name of a
     *                   format (<i>e.g.</i>, "jpeg", "png" or "tiff".
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
