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


import com.google.protobuf.ByteString;
import com.openhtmltopdf.util.XRLog;
import freemarker.template.Template;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.RenderDestination;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.util.templateengine.PageCssUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PDFBuilder {

    private static final Logger log = LogManager.getLogger(PDFBuilder.class.getName());

    public static PDFBuilder init(File templateDir, File fontDir) throws IOException {
        return init(templateDir, fontDir, null);
    }

    public static PDFBuilder init(File templateDir, File fontDir, File fontCacheDir) throws IOException {
        return init(templateDir, fontDir, fontCacheDir, new File("dump").getAbsoluteFile());
    }

    public static PDFBuilder init(File templateDir, File fontDir, File fontCacheDir, File dumpDir) throws IOException {
        PDFBuilder pdfBuilder = new PDFBuilder(templateDir, fontDir, fontCacheDir, dumpDir);
        return pdfBuilder;
    }

    public final File dumpDir;

    public boolean isDumpEnabled() {
        return true || BootConstant.isDebugMode() && dumpDir.exists();
    }

    private File htmlTemplateDir;

    //private static final WriterProperties WRITER_PROPS = IText.buildDefaultAccessPermission(null, null, true, PdfVersion.PDF_1_7);

    private final Map<String, Template> FreeMarkerTemplates = new HashMap<>();

    protected Agent_PDFBox agentPDFBox;
    protected Agent_IText agentIText;


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
    protected PDFBuilder(File htmlTemplateDir, File fontDir, File fontCacheDir, File dumpDir) throws IOException {
        XRLog.setLoggingEnabled(false);//XRLog.setLevel(XRLog.CSS_PARSE, Level.SEVERE);
        this.htmlTemplateDir = htmlTemplateDir;
        // cache dir can also be specified using java -Dpdfbox.fontcache=<path to cache dir>
        //Fonts can also be added using a font-face at-rule in the CSS, but NOT good for performance
        this.agentPDFBox = new Agent_PDFBox(fontDir, fontCacheDir);
        this.agentIText = new Agent_IText(fontDir);
        this.dumpDir = dumpDir;
    }

    public byte[] html2PDF(String requesterTxId, String htmlContent, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        return html2PDF(requesterTxId, htmlContent, false, cfg, po, context);
    }

    public byte[] html2PDF(String requesterTxId, String htmlContent, boolean isSinglePage, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        return html2PDF(requesterTxId, htmlContent, isSinglePage, 5, cfg, po, context);
    }

    public byte[] html2PDF(String requesterTxId, String htmlContent, boolean isSinglePage, int extraSpace, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        context.poi(BootPOI.PDF_BEGIN);
        PDFBuilderConfig.Agnet agnet = cfg.getAgnet();
        if (extraSpace < 1) {
            extraSpace = 5;
        }
        final String sessionName = requesterTxId + "_" + agnet + "_" + isSinglePage + "_" + extraSpace;
        byte[] pdf = null;
        try {
            if (isSinglePage) {
                htmlContent = PageCssUtil.setHeight(htmlContent, "1mm");
                Agent_PDFBox.LayoutInfo layoutInfo = agentPDFBox.layoutThenGetInfo(htmlContent, htmlTemplateDir);
                context.poi(BootPOI.PDF_HC);

                int pageCount = layoutInfo.getPageCount();
                float pageHeightMillimeters = layoutInfo.getPageHeight() * pageCount / Agent_PDFBox.POINTS_PER_MM;
                String htmlTemplate = htmlContent;
                int retry = 0;
                while (pageCount > 1 && retry < 2) {
                    pageHeightMillimeters += extraSpace;//add extra space
                    htmlTemplate = PageCssUtil.setHeight(htmlContent, pageHeightMillimeters + "mm;");
                    layoutInfo = agentPDFBox.layoutThenGetInfo(htmlTemplate, htmlTemplateDir);
                    context.poi(BootPOI.PDF_HV);
                    pageCount = layoutInfo.getPageCount();
                    retry++;
                }
                if (retry > 1) {
                    context.level(Level.WARN).memo("template height oversize retry = " + retry).error(new Err(0, null, null, null, sessionName + " template height oversize retry = " + retry));
                    //log.fatal("template height oversize (retry={}) for TxId={}", retry, sessionName);
                    if (po != null) {
                        po.sendAlertAsync(SMTPClientConfig.cfg.getEmailToAppSupport(), "HTML oversize @" + sessionName, "retry=" + retry, null, true);
                    }
                }
                htmlContent = htmlTemplate;
            }
            if (isDumpEnabled()) {
                try {
                    Path htmlFile = Paths.get(new File(dumpDir, sessionName + ".html").getAbsolutePath());
                    Files.writeString(htmlFile, htmlContent);
                    context.poi(BootPOI.PDF_DH);
                } catch (Throwable ex) {
                    log.fatal(dumpDir, ex);
                }
            }

            //4. generate PDF from HTML
            switch (agnet) {
                case PDFBox -> {
                    pdf = agentPDFBox.html2PDF(htmlContent, htmlTemplateDir, cfg.buildProtectionPolicy(), cfg.getDocInfo(), cfg.getPdfVersion());
                    context.poi(BootPOI.PDF_H2PPE);
                }
                case iText -> {
                    pdf = agentIText.html2PDF(htmlContent, htmlTemplateDir, cfg.buildWriterProperties());
                    context.poi(BootPOI.PDF_H2PIE);
                }
            }
            if (isDumpEnabled()) {
                try {
                    Path pdfFile = Paths.get(new File(dumpDir, sessionName + ".pdf").getAbsolutePath());
                    Files.write(pdfFile, pdf);
                    context.poi(BootPOI.PDF_DP);
                } catch (Throwable ex) {
                    log.fatal(dumpDir, ex);
                }
            }
        } finally {
            context.poi(BootPOI.PDF_END);
        }
        return pdf;
    }


    public List<byte[]> pdf2Images(String requesterTxId, byte[] pdf, String password, ImageType imageType, float imageDPI, String imageFormat, RenderDestination renderDestination, SessionContext context) throws IOException {
        context.poi(BootPOI.PDF2IMG_BEGIN);
        String sessionName = requesterTxId + "_" + imageType + "_" + renderDestination + "_" + imageDPI + "_" + imageFormat;
        List<byte[]> imagePages = Agent_PDFBox.pdf2Images(pdf, password, imageDPI, imageType, imageFormat, renderDestination);
        context.poi(BootPOI.PDF2IMG_END).memo("imagePages", "" + imagePages.size());
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
                    context.poi(BootPOI.PDF_DI);
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