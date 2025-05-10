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
package org.summerboot.jexpress.util;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.security.EncryptorUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.summerboot.jexpress.boot.config.ConfigUtil.DECRYPTED_WARPER_PREFIX;
import static org.summerboot.jexpress.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class FormatterUtil {

    protected static Logger log = null;// = LogManager.getLogger(FormatterUtil.class);

    public static final long INT_MASK = 0xFFFFFFFFL;//(long) Integer.MAX_VALUE - (long) Integer.MIN_VALUE;
    public static final int SHORT_MASK = 0xFFFF;
    public static final short BYTE_MASK = 0xFF;
    public static final short NIBBLE_MASK = 0x0F;

    public static final String[] EMPTY_STR_ARRAY = {};
    public static final String REGEX_CSV = "\\s*,\\s*";
    public static final String REGEX_PSV = "[\\|\\s]+";
    public static final String REGEX_URL = "\\s*/\\s*";
    public static final String REGEX_BINDING_MAP = "\\s*:\\s*";
    public static final String REGEX_EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    public static final Pattern REGEX_EMAIL_PATTERN = Pattern.compile(REGEX_EMAIL);


    public static String[] parseLines(String txt) {
        return StringUtils.isBlank(txt) ? EMPTY_STR_ARRAY : txt.split("\\r?\\n");
    }

    public static String[] parseDsv(String csv, String delimiter) {
        if (StringUtils.isBlank(delimiter) || ",".equals(delimiter)) {
            return parseCsv(csv);
        }
        return StringUtils.isBlank(csv) ? EMPTY_STR_ARRAY : csv.trim().split("\\s*" + delimiter + "\\s*");
    }

    public static String[] parsePsv(String csv) {
        return StringUtils.isBlank(csv) ? EMPTY_STR_ARRAY : csv.trim().split(REGEX_PSV);
    }

    public static String[] parseCsv(String csv) {
        return StringUtils.isBlank(csv) ? EMPTY_STR_ARRAY : csv.trim().split(REGEX_CSV);
    }

    public static String[] parseURL(String url) {
        return StringUtils.isBlank(url) ? EMPTY_STR_ARRAY : url.trim().split(REGEX_URL);
    }

    public static String[] parseURL(String url, boolean trim) {
        return StringUtils.isBlank(url)
                ? EMPTY_STR_ARRAY
                : trim ? url.trim().split(REGEX_URL) : url.split(REGEX_URL);
    }

    public static String parseUrlQueryParam(String url, Map<String, String> queryParam) {
        final QueryStringDecoder qd = new QueryStringDecoder(url, StandardCharsets.UTF_8, true);
        Map<String, List<String>> pms = qd.parameters();
        for (String key : pms.keySet()) {
            queryParam.put(key, pms.get(key).get(0));
        }
        String action = qd.path();
        return action;
    }

    @Deprecated
    public static String parseUrlQueryParamEx(String url, Map<String, String> queryParam) {
        String[] request = url.split("\\?", 2);
        String action = request[0];
        String query;
        if (request.length < 2) {
            if (!action.contains("=") && !action.contains("&")) {
                return action;
            }
            action = null;
            query = request[0];
        } else {
            query = request[1];
        }
        //String queryParamString = isUrlEncoded ? URLDecoder.decode(query, StandardCharsets.UTF_8) : query;
        parseFormParam(query, queryParam);
        return action;
    }

    /**
     * Break on #
     *
     * @param formBody
     * @param queryParam
     */
    public static void parseFormParam_Netty(String formBody, Map<String, String> queryParam) {
        QueryStringDecoder qd = new QueryStringDecoder(formBody, StandardCharsets.UTF_8, false);
        Map<String, List<String>> pms = qd.parameters();
        for (String key : pms.keySet()) {
            queryParam.put(key, pms.get(key).get(0));
        }
    }

    /**
     * Will not break on #
     *
     * @param formBody
     * @param queryParam
     */
    public static void parseFormParam(String formBody, Map<String, String> queryParam) {
        String[] pairs = formBody.split("&");
        for (String param : pairs) {
            String[] keyValuePair = param.split("=", 2);
            String key = URLDecoder.decode(keyValuePair[0], StandardCharsets.UTF_8);
            if (keyValuePair.length < 2) {
                queryParam.put(key, "");
            } else {
                String value = keyValuePair[1];
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
                queryParam.put(key, value);
            }
        }
    }


    public static <T extends Object> String toCSV(Collection<T> a) {
        return a.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    public static String[] getEnumNames(Class<? extends Enum<?>> e) {
        //return Arrays.stream(MyEnum.values()).map(MyEnum::name).toArray(String[]::new);
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }

    public static Pattern INSIDE_PARENTHESES_VALUE = Pattern.compile("\\(([^)]+)\\)");

    public static String getInsideParenthesesValue(String value) {
        String ret = value;
//        Matcher m = INSIDE_PARENTHESES_VALUE.matcher(value);
//        if (m.find()) {
//                ret = m.group(1);
//        }
//        return ret;
        int end = value.lastIndexOf(")");
        if (end >= 0) {
            ret = value.substring(value.indexOf("(") + 1, end);
        }
        return ret;
    }

    public static final String REGEX_FIRST_AND_LAST_B = "\\(.*\\)";

    public static final Pattern REGEX_DEC_PATTERN = Pattern.compile(DECRYPTED_WARPER_PREFIX + REGEX_FIRST_AND_LAST_B);

    public static final Pattern REGEX_ENC_PATTERN = Pattern.compile(ENCRYPTED_WARPER_PREFIX + REGEX_FIRST_AND_LAST_B);

    public static String updateProtectedLine(String line, boolean encrypt) throws GeneralSecurityException {
        Matcher matcher = encrypt
                ? REGEX_DEC_PATTERN.matcher(line)
                : REGEX_ENC_PATTERN.matcher(line);
        if (matcher.find()) {
            String match = matcher.group();
            String converted;
            if (encrypt) {
                converted = EncryptorUtil.encrypt(match, true);
                return line.replace(match, ENCRYPTED_WARPER_PREFIX + "(" + converted + ")");
            } else {
                converted = EncryptorUtil.decrypt(match, true);
                return line.replace(match, DECRYPTED_WARPER_PREFIX + "(" + converted + ")");
            }
        }
        return null;
    }

    @Deprecated
    public static String updateProtectedLine_(String line, boolean encrypt) throws GeneralSecurityException {
        String ret = null;
        int eq = line.indexOf("=");
        if (eq < 0) {
            return null;
        }
        String key = line.substring(0, eq).trim();
        String value = line.substring(++eq).trim();
        if (encrypt && value.startsWith(DECRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
            String encrypted = EncryptorUtil.encrypt(value, true);
            ret = key + "=" + ENCRYPTED_WARPER_PREFIX + "(" + encrypted + ")";
        } else if (!encrypt && value.startsWith(ENCRYPTED_WARPER_PREFIX + "(") && value.endsWith(")")) {
            String decrypted = EncryptorUtil.decrypt(value, true);
            ret = key + "=" + DECRYPTED_WARPER_PREFIX + "(" + decrypted + ")";
        }
        return ret;
    }

    public static String b2n(String s) {
        return StringUtils.isBlank(s) ? null : s.trim();
    }

    /*
     * BindAddresses = 192.168.1.10:8445, 127.0.0.1:8446, 0.0.0.0:8447
     */
    public static Map<String, Integer> parseBindingAddresss(String bindAddresses) {
        //int[] ports = Arrays.stream(portsStr).mapToInt(Integer::parseInt).toArray();
        Map<String, Integer> ret = new HashMap<>();
        String[] addrs = parseCsv(bindAddresses);
        for (String addr : addrs) {
            String[] ap = addr.trim().split(REGEX_BINDING_MAP);
            ret.put(ap[0], Integer.parseInt(ap[1]));
        }
        return ret;
    }

    public static Map<String, String> parseMap(String mapCVS) {
        //int[] ports = Arrays.stream(portsStr).mapToInt(Integer::parseInt).toArray();
        Map<String, String> ret = new HashMap<>();
        String[] mapKeyValues = parseCsv(mapCVS);
        for (String mapKeyValue : mapKeyValues) {
            String[] ap = mapKeyValue.trim().split(REGEX_BINDING_MAP);
            ret.put(ap[0], ap[1]);
        }
        return ret;
    }

    public static String convertTo(String value, String targetCharsetName) throws UnsupportedEncodingException {
        //String rawString = "Fantasticèéçà Entwickeln Sie mit Vergnügen";
        return new String(value.getBytes(targetCharsetName), targetCharsetName);
    }

    public static String base64MimeEncode(byte[] contentBytes) {
        return Base64.getMimeEncoder().encodeToString(contentBytes);
    }

    public static byte[] base64MimeDecode(String encodedMime) {
        return Base64.getMimeDecoder().decode(encodedMime);
    }

    public static String base64Encode(byte[] contentBytes) {
        return Base64.getEncoder().encodeToString(contentBytes);
    }

    public static byte[] base64Decode(String encodedMime) {
        return Base64.getDecoder().decode(encodedMime);
    }

    /**
     * @param bi
     * @param format - png
     * @return
     * @throws IOException ImageIO failed to access system cache dir
     */
    public static byte[] toByteArray(BufferedImage bi, String format) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            ImageIO.write(bi, format, baos);
            byte[] bytes = baos.toByteArray();
            return bytes;
        }
    }

    public static String toString(ByteBuffer buffer) {
        return toString(buffer, true, true, 10, "\t");
    }

    public static String toString(ByteBuffer buffer, boolean showStatus, boolean showHeaderfooter, int numberOfBytesPerLine) {
        return toString(buffer, showStatus, showHeaderfooter, numberOfBytesPerLine, "\t");
    }

    public static String toString(ByteBuffer buffer, boolean showStatus, boolean showHeaderfooter, int numberOfBytesPerLine, String delimiter) {
        return toString(buffer, showStatus, showHeaderfooter, numberOfBytesPerLine, delimiter, BootConstant.BR, "ByteBuffer Contents starts", "ByteBuffer Contents ends");
    }

    public static String toString(ByteBuffer buffer, boolean showStatus, boolean showHeaderfooter, int numberOfBytesPerLine, String delimiter, String br, String header, String footer) {
        if (buffer == null) {
            return "";
        }
        if (br == null) {
            br = BootConstant.BR;
        }
        StringBuilder sb = new StringBuilder();
        if (showStatus) {
            sb.append("ByteBuffer status:")
                    .append(" Order=").append(buffer.order())
                    .append(" Position=").append(buffer.position())
                    .append(" Limit=").append(buffer.limit())
                    .append(" Capacity=").append(buffer.capacity())
                    .append(" Remaining=").append(buffer.remaining())
                    .append(br);
        }
        if (showHeaderfooter) {
            buuldHeaderfooter(header, numberOfBytesPerLine, delimiter, sb, br);
        }
        boolean eol = false;
        if (numberOfBytesPerLine > 0) {
            byte[] array = buffer.array();
            for (int i = 0; i < buffer.limit(); i++) {
                eol = (i + 1) % numberOfBytesPerLine == 0;
                //String hexChars = String.format("0x%02X", array[i]);
                // replaced String.format("0x%02X", i) with better performance api, 100 times faster via byte operations: 10k loads performace: 317ms vs 2ms
                char[] hexChars = toString(array[i], true);
                sb.append(hexChars).append(eol ? br : delimiter);
            }
        }
        if (showHeaderfooter) {
            if (!eol) {
                sb.append("\n");
            }
            buuldHeaderfooter(footer, numberOfBytesPerLine, delimiter, sb, br);
        }
        return sb.toString();
    }

    public static final short HEX_STRING_SIZE = 4;
    public static final char[] HexArrayIndexTable = "0123456789ABCDEF".toCharArray();

    private static void buuldHeaderfooter(String title, int numberOfBytesPerLine, String delimiter, StringBuilder sb, String br) {
        int delimiterSize = delimiter.equals("\t") ? 4 : delimiter.length();
        int lineSize = HEX_STRING_SIZE * numberOfBytesPerLine + delimiterSize * (numberOfBytesPerLine - 1);
        int titleSize = title.length() + 2;
        int totalAsteriskSize = lineSize - titleSize;
        int asteriskSize = Math.max(2, totalAsteriskSize / 2 + totalAsteriskSize % 2);
        String asteriskLine = StringUtils.repeat("*", asteriskSize);
        sb.append(asteriskLine).append(" ").append(title).append(" ").append(asteriskLine).append(br);
    }

    /**
     * replaced String.format("0x%02X", i) with better performance api, 100 times faster via byte operations: 10k loads performace: 317ms vs 2ms
     *
     * @param v
     * @return
     */
    public static char[] toString(byte v, boolean append0x) {
        int b = v & 0xFF;
        char[] hexChars;
        if (append0x) {
            hexChars = new char[4];
            hexChars[0] = '0';
            hexChars[1] = 'x';
            hexChars[2] = HexArrayIndexTable[b >>> 4];
            hexChars[3] = HexArrayIndexTable[b & 0x0F];
        } else {
            hexChars = new char[2];
            hexChars[0] = HexArrayIndexTable[b >>> 4];
            hexChars[1] = HexArrayIndexTable[b & 0x0F];
        }
        return hexChars;
    }

    /**
     * For old Java before Java 17 HexFormat.of().parseHex(s)
     *
     * @param hexString must be an even-length string
     * @return
     */
    public static byte[] parseHex(String hexString) {
        String evenLengthHexString = hexString.replaceAll("0x", "").replaceAll("[^a-zA-Z0-9]", "");//remove "0x" and other delimiters, 0x5A   ,;/n/t|...   0x01 -> 5A01
        int len = evenLengthHexString.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Converted Hex string length=" + len + " and is not an even-length: \n\t arg: " + hexString + "\n\t hex: " + evenLengthHexString);
        }
        String hex = evenLengthHexString.replaceAll("[^a-fA-F0-9]", "");
        if (!evenLengthHexString.equals(hex)) {
            throw new IllegalArgumentException("Invalid Hex string \n\t arg: " + hexString + "\n\t hex: " + evenLengthHexString);
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(evenLengthHexString.charAt(i), 16) << 4)
                    + Character.digit(evenLengthHexString.charAt(i + 1), 16));
        }
        return data;
    }

    public static <T extends Object> Set<T> findDuplicates(List<T> listContainingDuplicates) {
        final Set<T> setToReturn = new HashSet<>();
        final Set<T> set1 = new HashSet<>();

        for (T yourInt : listContainingDuplicates) {
            if (!set1.add(yourInt)) {
                setToReturn.add(yourInt);
            }
        }
        return setToReturn;
    }

    public static String protectContentNumber(String plain, String keyword, String delimiter, String replaceWith) {
        if (StringUtils.isBlank(plain)) {
            return plain;
        }
        String regex = "(?i)" + keyword + "\\s*" + delimiter + "\\s*\"*[0-9]*";
        String replacement = keyword + delimiter + replaceWith;
        return plain.replaceAll(regex, replacement);
    }

    public static String protectContent(String plain, String keyword, String delimiter, String wrapper, String replaceWith) {
        if (StringUtils.isBlank(plain)) {
            return plain;
        }
        if (wrapper == null) {
            wrapper = "";
        }
        final String regex = "(?i)" + keyword + "\\s*" + delimiter + "\\s*" + wrapper + "(\\w*\\s*\\w*(-\\w*)*\\.*!*:*=*\\+*/*@*#*\\$*\\^*&*\\**\\(*\\)*)*" + wrapper;
        final String replacement = keyword + delimiter + replaceWith + wrapper;
        if (log == null) {
            log = LogManager.getLogger(FormatterUtil.class);
        }
        log.trace(() -> "replace " + plain + "\n\t regex=" + regex + "\n\t with=" + replacement);
        return plain.replaceAll(regex, replacement);
    }

    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public enum RegexType {
        jsonNumber, jsonString, jsonArray,
        jsonNumberSanitized, jsonStringSanitized, jsonArraySanitized,
        xml, FormParam,
    }

    public static Pattern getPattern(String dataFieldName, RegexType type) {
        // Cache and retrieve Pattern
        String key = type + "." + dataFieldName;
        Pattern pattern = patternCache.computeIfAbsent(key, k -> {
            String regex = switch (type) {
                case jsonNumber -> "(\"" + dataFieldName + "\"\\s*:\\s*)(\\d+)";
                case jsonNumberSanitized -> "(\"" + dataFieldName + "\\\\\"\\s*:\\s*)(\\d+)";
                case jsonString -> "(\"" + dataFieldName + "\"\\s*:\\s*\")[^\"]*(\")";//("key"\s*:\s*")[^"]*(")
                case jsonStringSanitized -> "(\"" + dataFieldName + "\\\\\"\\s*:\\s*\\\\\")[^\"]*(\\\\\")";//("key"\s*:\s*")[^"]*(")
                case jsonArray -> "(\"" + dataFieldName + "\"\\s*:\\s*\\[)[^\\[]*(\\])";
                case jsonArraySanitized -> "(\"" + dataFieldName + "\\\\\"\\s*:\\s*\\[)[^\\[]*(\\])";
                case xml -> String.format("(<%1$s>)(.*?)(</%1$s>)", Pattern.quote(dataFieldName));
                case FormParam -> "(?i)" + dataFieldName + "=[^&]*";
            };
            return Pattern.compile(regex, Pattern.DOTALL);
        });
        return pattern;
    }

    public static String replaceDataField(RegexType type, String txt, String key, String newValue) {
        if (StringUtils.isBlank(txt) || StringUtils.isBlank(key)) {
            return txt;
        }
        if (newValue == null) {
            newValue = "";
        }

        // Cache and retrieve Pattern
        Pattern pattern = getPattern(key, type);

        Matcher matcher = pattern.matcher(txt);
        if (matcher.find()) {
            switch (type) {
                case jsonNumber, jsonNumberSanitized -> {
                    return matcher.replaceAll("$1" + Matcher.quoteReplacement(newValue));
                }
                case xml -> {
                    return matcher.replaceAll("$1" + Matcher.quoteReplacement(newValue) + "$3");
                }
                case FormParam -> {
                    return matcher.replaceAll(key + "=" + newValue);
                }
                default -> {
                    return matcher.replaceAll("$1" + Matcher.quoteReplacement(newValue) + "$2");
                }
            }
        }

        return txt;
    }

    public static String replaceDataField(String json, String key, String newValue) {
        String ret = json;
        ret = replaceDataField(RegexType.jsonString, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonStringSanitized, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonNumber, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonNumberSanitized, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonArray, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonArraySanitized, ret, key, newValue);
        ret = replaceDataField(RegexType.xml, ret, key, newValue);
        ret = replaceDataField(RegexType.FormParam, ret, key, newValue);
        return ret;
    }

    public static String replaceJsonString(String json, String key, String newValue) {
        String ret = json;
        ret = replaceDataField(RegexType.jsonString, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonStringSanitized, ret, key, newValue);
        return ret;
    }

    public static String replaceJsonNumber(String json, String key, String newValue) {
        String ret = json;
        ret = replaceDataField(RegexType.jsonNumber, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonNumberSanitized, ret, key, newValue);
        return ret;
    }

    public static String replaceJsonArray(String json, String key, String newValue) {
        String ret = json;
        ret = replaceDataField(RegexType.jsonArray, ret, key, newValue);
        ret = replaceDataField(RegexType.jsonArraySanitized, ret, key, newValue);
        return ret;
    }

    public static String replaceXMLValue(String xml, String key, String newValue) {
        return replaceDataField(RegexType.xml, xml, key, newValue);
    }

    public static String preplaceFormParam(String formRequest, String key, String newValue) {
        return replaceDataField(RegexType.FormParam, formRequest, key, newValue);
    }

    public static String replaceJsonString_InString(String json, String key, String replaceWith) {
        final String regex = "(\"" + key + "\\\\\"\\s*:\\s*\\\\\")[^\"]*(\")";
        return json.replaceAll(regex, "$1" + replaceWith + "$2");
    }


    public static String replaceJsonNumber_InString(String json, String key, String replaceWith) {
        String regex = "(\"" + key + "\\\\\"\\s*:\\s*)(\\d+)";
        return json.replaceAll(regex, "$1" + replaceWith);
    }

    public static String[] splitByLength(String plain, int chunckSize) {
        return plain.split("(?<=\\G.{" + chunckSize + "})");
    }

    public static <T> T[] arrayCopy(T[] array1, T[] array2) {
        T[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public static <T> T[] arrayAdd(T[] array1, T newElement) {
        int size = array1.length;
        T[] result = Arrays.copyOf(array1, size + 1);
        result[size] = newElement;
        return result;
    }
}
