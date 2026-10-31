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


import com.google.protobuf.ByteString;
import com.openhtmltopdf.util.XRLog;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.RenderDestination;
import org.summerboot.jexpress.api.common.BootPoi;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.util.format.PageCssUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PdfBuilder {

    private static final Logger log = LogManager.getLogger(PdfBuilder.class.getName());

    public static PdfBuilder init(File templateDir, File fontDir) throws IOException {
        return init(templateDir, fontDir, null);
    }

    public static PdfBuilder init(File templateDir, File fontDir, File fontCacheDir) throws IOException {
        return init(templateDir, fontDir, fontCacheDir, new File("dump").getAbsoluteFile());
    }

    public static PdfBuilder init(File templateDir, File fontDir, File fontCacheDir, File dumpDir) throws IOException {
        PdfBuilder pdfBuilder = new PdfBuilder(templateDir, fontDir, fontCacheDir, dumpDir);
        return pdfBuilder;
    }

    public static interface SinglePageHeightCalculationListener {
        /**
         *
         * @param retry
         * @param pageCount
         * @param requesterTxId
         * @param htmlContent
         * @param agent
         * @param extraSpace
         * @param pageHeightMillimeters
         */
        void onHeightCalculationRetry(int retry, int pageCount, String requesterTxId, String htmlContent, PdfBuilderConfig.Agent agent, int extraSpace, float pageHeightMillimeters);
    }

    public final File dumpDir;
    private final File htmlTemplateDir;
    protected final PdfBoxAgent pdfBoxAgent;
    protected final ItextAgent agentIText;
    protected final List<SinglePageHeightCalculationListener> listeners = new ArrayList<>();
    //private static final WriterProperties WRITER_PROPS = IText.buildDefaultAccessPermission(null, null, true, PdfVersion.PDF_1_7);

    /**
     * Initializes the PDF builder with template and font directories.
     *
     * <p><strong>Note on Chinese Characters:</strong> To render PDFs containing Chinese characters,
     * add the following CSS rule to your templates:
     * <pre>{@code
     * * {
     *   font-family: "ArialUnicodeMS";
     * }
     * }</pre>
     *
     * <p>Cache directory can also be specified using:
     * {@code java -Dpdfbox.fontcache=<path to cache dir>}
     *
     * <p>Fonts can also be added using a font-face at-rule in the CSS, but this is NOT recommended for performance.
     *
     * @param htmlTemplateDir the directory containing HTML templates
     * @param fontDir         the directory containing font files (.ttf, .ttc)
     * @param fontCacheDir    the directory for PDFBox font cache (optional)
     * @param dumpDir         the directory for temp (optional)
     * @throws IOException if I/O errors occur during font loading
     */
    protected PdfBuilder(File htmlTemplateDir, File fontDir, File fontCacheDir, File dumpDir) throws IOException {
        XRLog.setLoggingEnabled(false);//XRLog.setLevel(XRLog.CSS_PARSE, Level.SEVERE);
        this.htmlTemplateDir = htmlTemplateDir;
        // cache dir can also be specified using java -Dpdfbox.fontcache=<path to cache dir>
        //Fonts can also be added using a font-face at-rule in the CSS, but NOT good for performance
        this.pdfBoxAgent = new PdfBoxAgent(fontDir, fontCacheDir);
        this.agentIText = new ItextAgent(fontDir);
        this.dumpDir = dumpDir;
    }

    public File getDumpDir() {
        return dumpDir;
    }

    public File getHtmlTemplateDir() {
        return htmlTemplateDir;
    }

    public void addListener(SinglePageHeightCalculationListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
    }

    public boolean removeListener(SinglePageHeightCalculationListener listener) {
        return listeners.remove(listener);
    }

    public boolean isDumpEnabled() {
        return BootConstants.isDebugMode() && dumpDir.exists();
    }

    /**
     * Build multipage PDF
     *
     * @param requesterTxId
     * @param htmlContent
     * @param cfg
     * @param context
     * @return
     * @throws IOException
     */
    public byte[] htmlToPdfMultiPage(String requesterTxId, String htmlContent, PdfBuilderConfig cfg, SessionContext context) throws IOException {
        return htmlToPdf(requesterTxId, htmlContent, cfg, false, 0, null, context);
    }

    /**
     * Build single page PDF
     *
     * @param requesterTxId
     * @param htmlContent
     * @param cfg
     * @param extraSpace
     * @param onetimeListener
     * @param context
     * @return
     * @throws IOException
     */
    public byte[] htmlToPdfSinglePage(String requesterTxId, String htmlContent, PdfBuilderConfig cfg, int extraSpace, SinglePageHeightCalculationListener onetimeListener, SessionContext context) throws IOException {
        return htmlToPdf(requesterTxId, htmlContent, cfg, true, extraSpace, onetimeListener, context);
    }

    protected byte[] htmlToPdf(String requesterTxId, String htmlContent, PdfBuilderConfig cfg, boolean isSinglePage, int extraSpace, SinglePageHeightCalculationListener onetimeListener, SessionContext context) throws IOException {
        context.poi(BootPoi.PDF_BEGIN);
        PdfBuilderConfig.Agent agent = cfg.getAgent();
        if (extraSpace < 1) {
            extraSpace = 5;
        }
        final String sessionName = requesterTxId + "[" + agent + "]" + (isSinglePage ? "SP" : "A4") + "+" + extraSpace + "mm";
        byte[] pdf = null;
        try {
            if (isSinglePage) {
                htmlContent = PageCssUtil.setHeight(htmlContent, "1mm");
                PdfBoxAgent.LayoutInfo layoutInfo = pdfBoxAgent.layoutThenGetInfo(htmlContent, htmlTemplateDir);
                context.poi(BootPoi.PDF_HC);

                int pageCount = layoutInfo.getPageCount();
                float pageHeightMillimeters = layoutInfo.getPageHeight() * pageCount / PdfBoxAgent.POINTS_PER_MM;
                String htmlTemplate = htmlContent;
                int iterationCount = 0;
                while (pageCount > 1 && iterationCount < cfg.getSinglePageHeightCalculationMaxIteration()) {
                    pageHeightMillimeters += extraSpace;//add extra space
                    htmlTemplate = PageCssUtil.setHeight(htmlContent, pageHeightMillimeters + "mm;");
                    layoutInfo = pdfBoxAgent.layoutThenGetInfo(htmlTemplate, htmlTemplateDir);
                    pageCount = layoutInfo.getPageCount();
                    iterationCount++;
                    context.poi(BootPoi.PDF_HV)
                            .memo("iterationCount=" + iterationCount + ",pages=" + pageCount + ", extraSpace=" + extraSpace + "mm, height=" + pageHeightMillimeters + "mm");
                }
                if (iterationCount > 1) {
                    context.level(Level.WARN)
                            .memo("template height oversize iterationCount = " + iterationCount)
                            .error(new Err(0, null, null, null, sessionName + " template height oversize iterationCount = " + iterationCount));
                    if (onetimeListener == null) {
                        for (SinglePageHeightCalculationListener addedListener : listeners) {
                            if (addedListener == null) {
                                continue;
                            }
                            addedListener.onHeightCalculationRetry(iterationCount, pageCount, requesterTxId, htmlContent, agent, extraSpace, pageHeightMillimeters);
                        }
                    } else {
                        onetimeListener.onHeightCalculationRetry(iterationCount, pageCount, requesterTxId, htmlContent, agent, extraSpace, pageHeightMillimeters);
                    }
//                    if (po != null) {
//                        po.sendAlertAsync(SmtpClientConfig.cfg.getEmailToDevelopment(), "HTML oversize @" + sessionName, "iterationCount=" + iterationCount, null, true);
//                    }
                }
                htmlContent = htmlTemplate;
            }
            if (isDumpEnabled()) {
                try {
                    Path htmlFile = Paths.get(new File(dumpDir, sessionName + ".html").getAbsolutePath());
                    Files.writeString(htmlFile, htmlContent);
                    context.poi(BootPoi.PDF_DH);
                } catch (Throwable ex) {
                    log.fatal(dumpDir, ex);
                }
            }

            //4. generate PDF from HTML
            switch (agent) {
                case PDFBox -> {
                    pdf = pdfBoxAgent.html2PDF(htmlContent, htmlTemplateDir, cfg.buildProtectionPolicy(), cfg.getDocInfo(), cfg.getPdfVersion());
                    context.poi(BootPoi.PDF_H2PPE);
                }
                case iText -> {
                    pdf = agentIText.html2PDF(htmlContent, htmlTemplateDir, cfg.buildWriterProperties());
                    context.poi(BootPoi.PDF_H2PIE);
                }
            }
            if (isDumpEnabled()) {
                try {
                    Path pdfFile = Paths.get(new File(dumpDir, sessionName + ".pdf").getAbsolutePath());
                    Files.write(pdfFile, pdf);
                    context.poi(BootPoi.PDF_DP);
                } catch (Throwable ex) {
                    log.fatal(dumpDir, ex);
                }
            }
        } finally {
            context.poi(BootPoi.PDF_END);
        }
        return pdf;
    }

    public List<byte[]> pdf2Images(String requesterTxId, byte[] pdf, String password, ImageType imageType, float imageDPI, String imageFormat, RenderDestination renderDestination, SessionContext context) throws IOException {
        context.poi(BootPoi.PDF2IMG_BEGIN);
        String sessionName = requesterTxId + "_" + imageType + "_" + renderDestination + "_" + imageDPI + "_" + imageFormat;
        List<byte[]> imagePages = PdfBoxAgent.pdf2Images(pdf, password, imageDPI, imageType, imageFormat, renderDestination);
        context.poi(BootPoi.PDF2IMG_END).memo("imagePages", "" + imagePages.size());
        if (isDumpEnabled()) {
            int page = 0, total = imagePages.size();
            for (byte[] imageData : imagePages) {
                page++;
                if (imageData == null) {
                    continue;
                }

                try {
                    //log.trace("tx#" + sessionName + "\n\t pdf=" + buildBase64(pdf));
                    Path imageFile = Paths.get(new File(dumpDir, sessionName + "_" + page + "of" + total + "." + imageFormat).getAbsolutePath());
                    Files.write(imageFile, imageData);
                    context.poi(BootPoi.PDF_DI);
                } catch (Throwable ex) {
                    log.fatal(dumpDir, ex);
                    context.memo(dumpDir.getAbsolutePath(), ex.toString());
                }
            }
        }
        return imagePages;
    }

    public static ByteString toProtobufData(byte[] byteData) {
        return ByteString.copyFrom(byteData);
    }

    public static List<ByteString> toProtobufData(List<byte[]> byteDataList) {
        List<ByteString> protobufData = new ArrayList<>();
        for (byte[] byteData : byteDataList) {
            protobufData.add(toProtobufData(byteData));
        }
        return protobufData;
    }

    public static long crc(byte[] byteData) {
        CRC32 crc32 = new CRC32();
        crc32.update(byteData);
        return crc32.getValue();
    }

    public static String base64Encode(byte[] byteData) {
        return Base64.getEncoder().encodeToString(byteData);
    }
}