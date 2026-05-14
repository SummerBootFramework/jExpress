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


    public static final File dumpDir = new File("dump").getAbsoluteFile();

    public static boolean isDumpEnabled() {
        return BootConstant.isDebugMode() && dumpDir.exists();
    }

    private static File TEMPLATE_DIR;

    //private static final WriterProperties WRITER_PROPS = IText.buildDefaultAccessPermission(null, null, true, PdfVersion.PDF_1_7);

    private static final Map<String, Template> FreeMarkerTemplates = new HashMap<>();

    public static void init(File fontDir, File fontCacheDir, File templateDir) throws IOException {
        TEMPLATE_DIR = templateDir;

        // cache dir can also be specified using java -Dpdfbox.fontcache=<path to cache dir>
        //Fonts can also be added using a font-face at-rule in the CSS, but NOT good for performance
        XRLog.setLoggingEnabled(false);//XRLog.setLevel(XRLog.CSS_PARSE, Level.SEVERE);

        /*
        Rendering a pdf that contains Chinese characters: add the following into css
            * {
              font-family: "ArialUnicodeMS";
            }
         */
        Agent_PDFBox.loadFonts(fontCacheDir, fontDir);
        Agent_IText.loadFonts(fontDir);
    }

    public static byte[] html2PDF(String txId, String htmlContent, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        return html2PDF(txId, htmlContent, false, cfg, po, context);
    }

    public static byte[] html2PDF(String txId, String htmlContent, boolean isSinglePage, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        return html2PDF(txId, htmlContent, isSinglePage, 5, cfg, po, context);
    }

    public static byte[] html2PDF(String txId, String htmlContent, boolean isSinglePage, int extraSpace, PDFBuilderConfig cfg, PostOffice po, SessionContext context) throws IOException {
        context.poi(BootPOI.PDF_BEGIN);
        PDFBuilderConfig.Agnet agnet = cfg.getAgnet();
        if (extraSpace < 1) {
            extraSpace = 5;
        }
        final String sessionName = txId + "_" + agnet + "_" + isSinglePage + "_" + extraSpace;
        byte[] pdf = null;
        try {
            if (isSinglePage) {
                htmlContent = PageCssUtil.setHeight(htmlContent, "1mm");
                Agent_PDFBox.LayoutInfo layoutInfo = Agent_PDFBox.layoutThenGetInfo(htmlContent, TEMPLATE_DIR);
                context.poi(BootPOI.PDF_HC);

                int pageCount = layoutInfo.getPageCount();
                float pageHeightMillimeters = layoutInfo.getPageHeight() * pageCount / Agent_PDFBox.POINTS_PER_MM;
                String htmlTemplate = htmlContent;
                int retry = 0;
                while (pageCount > 1 && retry < 2) {
                    pageHeightMillimeters += extraSpace;//add extra space
                    htmlTemplate = PageCssUtil.setHeight(htmlContent, pageHeightMillimeters + "mm;");
                    layoutInfo = Agent_PDFBox.layoutThenGetInfo(htmlTemplate, TEMPLATE_DIR);
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
                    pdf = Agent_PDFBox.html2PDF(htmlContent, TEMPLATE_DIR, cfg.buildProtectionPolicy(), cfg.getDocInfo(), cfg.getPdfVersion());
                    context.poi(BootPOI.PDF_H2PPE);
                }
                case iText -> {
                    pdf = Agent_IText.html2PDF(htmlContent, TEMPLATE_DIR, cfg.buildWriterProperties());
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


    public static List<byte[]> pdf2Images(String txId, byte[] pdf, String password, ImageType imageType, float imageDPI, String imageFormat, RenderDestination renderDestination, SessionContext context) throws IOException {
        context.poi(BootPOI.PDF2IMG_BEGIN);
        String sessionName = txId + "_" + imageType + "_" + renderDestination + "_" + imageDPI + "_" + imageFormat;
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