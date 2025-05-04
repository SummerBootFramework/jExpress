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

import com.veracode.annotation.CRLFCleanser;
import com.veracode.annotation.FilePathCleanser;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class SecurityUtil {

    public static final HostnameVerifier DO_NOT_VERIFY_REMOTE_IP = (String hostname, SSLSession session) -> true;
    public static final HostnameVerifier hostnameVerifier = (String hostname, SSLSession session) -> {
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        return hv.verify("hostname", session);
    };

    public static final String[] CIPHER_SUITES = {"TLS_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384", "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_anon_WITH_AES_256_CBC_SHA", "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_NULL_SHA", "TLS_ECDH_RSA_WITH_NULL_SHA", "TLS_ECDH_anon_WITH_NULL_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_NULL_SHA,TLS_ECDHE_RSA_WITH_NULL_SHA"};

    public static final Pattern PATTERN_UNPRINTABLE = Pattern.compile("\\p{C}");
    public static final Pattern PATTERN_UNPRINTABLE_CRLFTAB = Pattern.compile("\\p{C}&&[^\\r\\n\\t]");


    /**
     * Removes all unprintable characters from a string and replaces with
     * substitute (i.e. a space).
     *
     * @param input
     * @param substitute
     * @return the stripped value
     */
    public static String stripControls(String input, String substitute) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }
        return PATTERN_UNPRINTABLE.matcher(input).replaceAll(substitute);//str.replaceAll("\\p{C}", " ");
    }

    public static final Pattern Pattern_HasUppercase = Pattern.compile("[A-Z]");
    public static final Pattern Pattern_HasLowercase = Pattern.compile("[a-z]");
    public static final Pattern Pattern_HasNumber = Pattern.compile("\\d");
    public static final Pattern Pattern_HasSpecialChar = Pattern.compile("[^a-zA-Z0-9 ]");

    public static boolean validatePassword(String pwd, int length) {
        if (StringUtils.isBlank(pwd)) {
            return false;
        }
        if (pwd.length() < length) {
            return false;
        }
        if (!Pattern_HasUppercase.matcher(pwd).find()) {
            return false;
        }
        if (!Pattern_HasLowercase.matcher(pwd).find()) {
            return false;
        }
        if (!Pattern_HasNumber.matcher(pwd).find()) {
            return false;
        }
        if (!Pattern_HasSpecialChar.matcher(pwd).find()) {
            return false;
        }
        return true;
    }


    public static String randomAlphanumeric(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int randomIndex = EncryptorUtil.RANDOM.nextInt(33, 126);
            sb.append((char) (randomIndex));
        }
        return sb.toString();
    }

    @CRLFCleanser
    public static String sanitizeCRLF(String plainText) {
        if (StringUtils.isEmpty(plainText)) {
            return plainText;
        }
        return StringEscapeUtils.escapeJava(plainText);
    }

    @FilePathCleanser
    public static String sanitizeFilePath(String plainText) {
        if (StringUtils.isEmpty(plainText)) {
            return plainText;
        }
        String str = StringUtils.replaceAll(plainText, "\\.\\./", "/");
        return (String) str.chars().mapToObj((i) -> (char) i).map((c) -> Character.isWhitespace(c) ? '_' : c).filter((c) -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '/' || c == '\\' || c == '@' || c == '.').map(String::valueOf).collect(Collectors.joining());
    }

    @FilePathCleanser
    public static String sanitizeFilePath(File file) {
        return sanitizeFilePath(file.getAbsolutePath());
    }

    @FilePathCleanser
    public static boolean sanitizePath(String path) {
        return !path.contains(File.separator + '.')
                && !path.contains('.' + File.separator);
    }

    @FilePathCleanser
    public static boolean precheckFile(File file, ServiceContext context) {
        String filePath = file.getAbsolutePath();
        String realPath;
        try {
            realPath = file.getAbsoluteFile().toPath().normalize().toString();
        } catch (Throwable ex) {
            Err e = new Err(BootErrorCode.NIO_REQUEST_BAD_DOWNLOAD, null, null, ex, "Invalid file path: " + filePath);
            context.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return false;
        }

        if (!file.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, null, null, "File not exists: " + filePath);
            context.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!sanitizePath(filePath) || !filePath.equals(realPath)
                || file.isDirectory() || !file.isFile()
                || file.isHidden() || !file.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, null, null, "Malicious file request: " + filePath);
            context.status(HttpResponseStatus.FORBIDDEN).error(e);
            return false;
        }
        return true;
    }


    public static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    public static boolean sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                return false;
            }
        }
        uri = uri.replace('/', File.separatorChar);
        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        return !(uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator)
                || uri.charAt(0) == '.'
                || uri.charAt(uri.length() - 1) == '.'
                || INSECURE_URI.matcher(uri).matches());
    }

    @Deprecated
    public static String sanitizeDocRootUri(String uri, String docroot) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error(e);
            }
        }
        uri = uri.replace('/', File.separatorChar);
        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator)
                || uri.charAt(0) == '.'
                || uri.charAt(uri.length() - 1) == '.'
                || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        if (!uri.startsWith(docroot)) {
            return null;
        }
        return System.getProperty("user.dir") + uri;
    }


    /**
     * Distinguished Name Escaping - The exhaustive list is the following: \ # + < > , ; " = and leading or trailing spaces.
     *
     * @param dnName
     * @return
     */
    public static final String escapeDN(String dnName) {
        if (dnName == null) {
            return dnName;
        }
        StringBuilder sb = new StringBuilder();

        if ((dnName.length() > 0) && ((dnName.charAt(0) == ' ') || (dnName.charAt(0) == '#'))) {
            sb.append('\\'); // add the leading backslash if needed
        }
        for (int i = 0; i < dnName.length(); i++) {
            char curChar = dnName.charAt(i);
            switch (curChar) {
                case '\\':// Backslash character
                case '#':// Pound sign (hash sign)
                case '+':// Plus sign
                case '<':// Less than symbol
                case '>':// Greater than symbol
                case ',':// comma
                case ';':// Semicolon
                case '"':// Double quote (quotation mark)
                case '=':// Equal sign
                    break;
                default:
                    sb.append(curChar);
            }
        }
        if ((dnName.length() > 1) && (dnName.charAt(dnName.length() - 1) == ' ')) {
            sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
        }

        return sb.toString().trim();// Leading or trailing spaces
    }

    public static final String escapeLDAPSearchFilter(String filter) {
        if (filter == null) {
            return filter;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filter.length(); i++) {
            char curChar = filter.charAt(i);
            switch (curChar) {
                case '\\':
                case '*':
                case '(':
                case ')':
                case '\u0000':
                    break;
                default:
                    sb.append(curChar);
            }
        }
        return sb.toString().trim();
    }

}
