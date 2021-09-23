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
package org.summerframework.util.pdf.itext;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.font.FontSet;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
public class ITextUtil {

    private static final Map<String, PdfFont> FONTS = new HashMap<>();

    public static PdfFont getFont(String name) {
        return FONTS.get(name);
    }

    static FontProvider loadFonts(File dir) throws IOException {
        if (!dir.isDirectory()) {
            throw new IOException(dir.getAbsolutePath() + " is not a directory");
        }
        FontSet fontSet = new FontSet();
        fontSet.addDirectory(dir.getAbsolutePath(), true);
        FontProvider fontProvider = new FontProvider(fontSet);

        File[] files = dir.listFiles((File dir1, String name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".ttf") || lower.endsWith(".ttc") || lower.endsWith(".otf");
        });

        for (File file : files) {
            String fileName = file.getName();
            String fontFamily = fileName.substring(0, fileName.lastIndexOf("."));
            PdfFont font = PdfFontFactory.createFont(file.getAbsolutePath(), PdfEncodings.IDENTITY_H);
            FONTS.put(fontFamily, font);
        }
        return fontProvider;
    }
}
