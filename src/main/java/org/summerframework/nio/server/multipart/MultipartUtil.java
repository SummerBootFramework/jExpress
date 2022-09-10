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
package org.summerframework.nio.server.multipart;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.util.internal.StringUtil;

/**
 *
 * @author
 * https://github.com/netty/netty/blob/4.1/codec-http/src/main/java/io/netty/handler/codec/http/multipart/HttpPostRequestDecoder.java
 */
public class MultipartUtil {

    /**
     * Check if the given request is a multipart request
     *
     * @param request
     * @return True if the request is a Multipart request
     */
    public static boolean isMultipart(HttpRequest request) {
        String mimeType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (mimeType != null && mimeType.startsWith(HttpHeaderValues.MULTIPART_FORM_DATA.toString())) {
            return getMultipartDataBoundary(mimeType) != null;
        }
        return false;
    }

    /**
     * Check from the request ContentType if this request is a Multipart
     * request.
     *
     * @param contentType
     * @return an array of String if multipartDataBoundary exists with the
     * multipartDataBoundary as first element, charset if any as second (missing
     * if not set), else null
     */
    protected static String[] getMultipartDataBoundary(String contentType) {
        // Check if Post using "multipart/form-data; boundary=--89421926422648 [; charset=xxx]"
        String[] headerContentType = splitHeaderContentType(contentType);
        final String multiPartHeader = HttpHeaderValues.MULTIPART_FORM_DATA.toString();
        if (headerContentType[0].regionMatches(true, 0, multiPartHeader, 0, multiPartHeader.length())) {
            int mrank;
            int crank;
            final String boundaryHeader = HttpHeaderValues.BOUNDARY.toString();
            if (headerContentType[1].regionMatches(true, 0, boundaryHeader, 0, boundaryHeader.length())) {
                mrank = 1;
                crank = 2;
            } else if (headerContentType[2].regionMatches(true, 0, boundaryHeader, 0, boundaryHeader.length())) {
                mrank = 2;
                crank = 1;
            } else {
                return null;
            }
            String boundary = StringUtil.substringAfter(headerContentType[mrank], '=');
            if (boundary == null) {
                throw new HttpPostRequestDecoder.ErrorDataDecoderException("Needs a boundary value");
            }
            if (boundary.charAt(0) == '"') {
                String bound = boundary.trim();
                int index = bound.length() - 1;
                if (bound.charAt(index) == '"') {
                    boundary = bound.substring(1, index);
                }
            }
            final String charsetHeader = HttpHeaderValues.CHARSET.toString();
            if (headerContentType[crank].regionMatches(true, 0, charsetHeader, 0, charsetHeader.length())) {
                String charset = StringUtil.substringAfter(headerContentType[crank], '=');
                if (charset != null) {
                    return new String[]{"--" + boundary, charset};
                }
            }
            return new String[]{"--" + boundary};
        }
        return null;
    }

    /**
     * Split the very first line (Content-Type value) in 3 Strings
     *
     * @return the array of 3 Strings
     */
    private static String[] splitHeaderContentType(String sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;
        aStart = HttpPostBodyUtil.findNonWhitespace(sb, 0);
        aEnd = sb.indexOf(';');
        if (aEnd == -1) {
            return new String[]{sb, "", ""};
        }
        bStart = HttpPostBodyUtil.findNonWhitespace(sb, aEnd + 1);
        if (sb.charAt(aEnd - 1) == ' ') {
            aEnd--;
        }
        bEnd = sb.indexOf(';', bStart);
        if (bEnd == -1) {
            bEnd = HttpPostBodyUtil.findEndOfString(sb);
            return new String[]{sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), ""};
        }
        cStart = HttpPostBodyUtil.findNonWhitespace(sb, bEnd + 1);
        if (sb.charAt(bEnd - 1) == ' ') {
            bEnd--;
        }
        cEnd = HttpPostBodyUtil.findEndOfString(sb);
        return new String[]{sb.substring(aStart, aEnd), sb.substring(bStart, bEnd), sb.substring(cStart, cEnd)};
    }
}
