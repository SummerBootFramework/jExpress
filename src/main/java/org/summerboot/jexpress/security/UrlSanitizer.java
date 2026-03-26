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
package org.summerboot.jexpress.security;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.utils.StringUtils;
import org.owasp.encoder.Encode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class UrlSanitizer {
    public record UrlSanitized(String cleanPath, String cleanQuery, String cleanedURL, boolean isPathTraversal) {

    }

    private static final UrlSanitized EMPTY_URL = new UrlSanitized(null, null, null, false);

    /**
     * Clean URL from both XSS and path traversal attacks
     * Example: /../../../../windows/win.ini?q=<script>alert(1)</script>
     * Result: windows/win.ini?q=&lt;script&gt;alert(1)&lt;/script&gt;
     */
    public static UrlSanitized cleanUrl(String url) {
        if (url == null || url.isEmpty()) {
            return EMPTY_URL;
        }

        // 1. Split URL into path and query
        int queryStart = url.indexOf('?');
        String path = queryStart >= 0 ? url.substring(0, queryStart) : url;
        String queryString = queryStart >= 0 ? url.substring(queryStart + 1) : "";

        // 2. Clean path: Remove path traversal using Apache Commons IO
        String cleanPath = cleanPathTraversal(path);

        // 3. Clean query string: Sanitize XSS using OWASP Encoder
        String cleanQuery = cleanQueryString(queryString);

        // 4. Reconstruct URL
        String cleanedURL = cleanQuery.isEmpty() ? cleanPath : cleanPath + "?" + cleanQuery;

        return new UrlSanitized(cleanPath, cleanQuery, cleanedURL, StringUtils.isBlank(cleanPath));
    }

    /**
     * Remove path traversal using Apache Commons IO FilenameUtils
     */
    private static String cleanPathTraversal(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // URL decode multiple times (handle %2e%2e, %2f, etc.)
        String decoded = path;
        for (int i = 0; i < 5; i++) {
            try {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (Exception e) {
                break;
            }
        }

        // Remove Windows drive letters (C:, D:, etc.)
        decoded = decoded.replaceAll("^[a-zA-Z]:[/\\\\]", "");

        // Use Apache Commons IO to normalize and remove .. and .
        String normalized = FilenameUtils.normalize(decoded, true);

        // If normalization fails (invalid path), return empty
        if (normalized == null) {
            return "";
        }

        return normalized;
        // Remove leading slashes
        //return normalized.replaceAll("^/+", "");
    }

    /**
     * Sanitize query string from XSS using OWASP Encoder
     */
    private static String cleanQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }

        // Parse query parameters
        String[] params = queryString.split("&");
        StringBuilder result = new StringBuilder();

        for (String param : params) {
            if (param.isEmpty()) continue;

            String[] pair = param.split("=", 2);
            String key = pair[0];
            String value = pair.length > 1 ? pair[1] : "";

            // Encode both key and value for HTML context
            String encodedKey = Encode.forHtml(key);
            String encodedValue = Encode.forHtml(value);

            if (result.length() > 0) {
                result.append("&");
            }
            result.append(encodedKey);
            if (!value.isEmpty()) {
                result.append("=").append(encodedValue);
            }
        }

        return result.toString();
    }

    /**
     * Alternative: More aggressive XSS cleaning (removes scripts entirely)
     */
    public static UrlSanitized cleanUrlAggressive(String url) {
        if (url == null || url.isEmpty()) {
            return EMPTY_URL;
        }

        int queryStart = url.indexOf('?');
        String path = queryStart >= 0 ? url.substring(0, queryStart) : url;
        String queryString = queryStart >= 0 ? url.substring(queryStart + 1) : "";

        // Clean path
        String cleanPath = cleanPathTraversal(path);

        // Aggressively remove XSS patterns from query string
        String cleanQuery = removeXssPatterns(queryString);

        String cleanedURL = cleanQuery.isEmpty() ? cleanPath : cleanPath + "?" + cleanQuery;

        return new UrlSanitized(cleanPath, cleanQuery, cleanedURL, StringUtils.isBlank(cleanPath));
    }

    private static String removeXssPatterns(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Remove script tags and content
        String cleaned = input.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "");
        cleaned = cleaned.replaceAll("(?i)<object[^>]*>.*?</object>", "");
        cleaned = cleaned.replaceAll("(?i)<embed[^>]*>", "");

        // Remove javascript: protocol
        cleaned = cleaned.replaceAll("(?i)javascript:", "");

        // Remove event handlers
        cleaned = cleaned.replaceAll("(?i)on\\w+\\s*=", "");

        return cleaned;
    }
}