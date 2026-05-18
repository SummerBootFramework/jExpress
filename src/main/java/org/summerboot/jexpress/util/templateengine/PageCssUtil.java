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
package org.summerboot.jexpress.util.templateengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class PageCssUtil {
    /**
     * Returns updated HTML:
     * 1) If @page size exists, replaces only height token.
     * 2) Otherwise appends a new @page block under {@code <head>}.
     */
    public static String setHeight(String html, String newHeight) {
        if (html == null) {
            html = "";
        }
        if (newHeight == null || newHeight.trim().isEmpty()) {
            throw new IllegalArgumentException("newHeight must not be blank");
        }

        String h = newHeight.trim();

        // Parse as HTML so we can safely target <style> under <head>.
        Document doc = Jsoup.parse(html, "", Parser.htmlParser());

        // Keep output close to input (avoid pretty reformat)
        doc.outputSettings().prettyPrint(false);
        // Emit self-closing void tags (e.g. <meta .../>) instead of HTML5 style.
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

        // Matches: @page { ... size: <width> <height>; ... }
        Pattern twoTokenSize = Pattern.compile(
                "(?is)(@page\\s*\\{[^}]*?\\bsize\\s*:\\s*)([^;\\s]+)(\\s+)([^;\\s]+)(\\s*;)"
        );

        // Matches: @page { ... size: <single>; ... } e.g. A4/letter
        Pattern oneTokenSize = Pattern.compile(
                "(?is)(@page\\s*\\{[^}]*?\\bsize\\s*:\\s*)([^;\\s]+)(\\s*;)"
        );

        boolean changed = false;

        for (Element style : doc.select("style")) {
            String css = style.data();
            if (css == null || css.isEmpty()) {
                css = style.html();
            }

            Matcher m2 = twoTokenSize.matcher(css);
            if (m2.find()) {
                // keep width token, replace height token
                String updated = m2.replaceFirst(
                        Matcher.quoteReplacement(m2.group(1) + m2.group(2) + " " + h + m2.group(5))
                );
                style.text(updated);
                changed = true;
                break;
            }

            Matcher m1 = oneTokenSize.matcher(css);
            if (m1.find()) {
                // convert single token to two-token size
                String updated = m1.replaceFirst(
                        Matcher.quoteReplacement(m1.group(1) + m1.group(2) + " " + h + m1.group(3))
                );
                style.text(updated);
                changed = true;
                break;
            }
        }

        if (!changed) {
            Element head = doc.head();
            if (head == null) {
                head = doc.prependElement("head");
            }
            head.appendElement("style")
                    //.appendText("@page {\n  margin: 0px;\n  size: auto " + h + ";\n}");
                    .appendText("@page {\n  size: auto " + h + ";\n}");
        }

        String out = doc.outerHtml();
        // Jsoup emits a space before '/>' in XML syntax; normalize for expected output.
        return out.replaceAll("(?i)<meta([^>]*?)\\s+/>", "<meta$1/>");
    }
}
