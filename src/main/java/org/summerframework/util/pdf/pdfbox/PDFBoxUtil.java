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

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class PDFBoxUtil {

    private static final Map<String, PDFont> FONTS = new HashMap();

    public static PDFont getFont(String name) {
        return FONTS.get(name);
    }

    static void setFonts(PdfRendererBuilder builder, PDDocument doc, File dir) throws IOException {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((File dir1, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".ttc");// || lower.endsWith(".otf");
        });

        for (File file : files) {
            String fileName = file.getName();
            String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
            if (builder != null) {
                builder.useFont(file, fontFamily);
            }
            if (doc != null) {
                if (!fileName.endsWith(".otf")) {
                    PDFont font = PDType0Font.load(doc, file.getAbsoluteFile());
                    //PDFont font =        PDType0Font.load(doc, new File(PDFBoxConfig.CFG.getFontDir() + "\\SimHei.ttf"));
                    FONTS.put(fontFamily, font);
                }
            }
        }
    }

    private static final AccessPermission DEFAULT_AP = buildDefaultAccessPermission();
    private static final int DEFAULT_KEY_LENGTH = 256;

    private static AccessPermission buildDefaultAccessPermission() {
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

    static StandardProtectionPolicy buildStandardProtectionPolicy(String userPwd, String ownerPwd) {
        return buildStandardProtectionPolicy(userPwd, ownerPwd, DEFAULT_AP, DEFAULT_KEY_LENGTH);
    }

    static StandardProtectionPolicy buildStandardProtectionPolicy(String userPwd, String ownerPwd, AccessPermission ap, int keyLenth) {
        if (StringUtils.isBlank(ownerPwd)) {
            ownerPwd = RandomStringUtils.randomAlphanumeric(10);
        }
        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
        spp.setEncryptionKeyLength(keyLenth);
        return spp;
    }
}
