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
package org.summerframework.util;

import java.awt.image.BufferedImage;
import static org.summerframework.boot.config.ConfigUtil.DECRYPTED_WARPER_PREFIX;
import static org.summerframework.boot.config.ConfigUtil.ENCRYPTED_WARPER_PREFIX;
import org.summerframework.security.SecurityUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class FormatterUtil {

    public static final String[] EMPTY_STR_ARRAY = {};

    public static final String REGEX_CSV = "\\s*,\\s*";
    public static final String REGEX_URL = "\\s*/\\s*";
    public static final String REGEX_BINDING_MAP = "\\s*:\\s*";
    public static final String REGEX_EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    public static final Pattern REGEX_EMAIL_PATTERN = Pattern.compile(REGEX_EMAIL);

    public static String[] parseDsv(String csv, String delimiter) {
        return StringUtils.isBlank(csv) ? EMPTY_STR_ARRAY : csv.trim().split("\\s*" + delimiter + "\\s*");
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

    public static <T extends Object> String toCSV(Collection<T> a) {
        return a.stream().map(String::valueOf).collect(Collectors.joining(","));
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

    public static String scrapeJson(String target, String plain) {
        if (plain == null) {
            return null;
        }
        return plain.replaceAll("\"" + target + "\"\\s*:\\s*\"\\s*[0-9]*", "\"" + target + "\":\"********");
    }

    private static final Pattern REGEX_DEC_PATTERN = Pattern.compile(DECRYPTED_WARPER_PREFIX + "\\(([^)]+)\\)");
    private static final Pattern REGEX_ENC_PATTERN = Pattern.compile(ENCRYPTED_WARPER_PREFIX + "\\(([^)]+)\\)");

    public static String updateLine(String line, boolean encrypt) throws GeneralSecurityException {
        Matcher matcher = encrypt
                ? REGEX_DEC_PATTERN.matcher(line)
                : REGEX_ENC_PATTERN.matcher(line);
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (matches.isEmpty()) {
            return null;
        }
        for (String match : matches) {
            try {
                String converted;
                if (encrypt) {
                    converted = SecurityUtil.encrypt(match, true);
                    line = line.replace(DECRYPTED_WARPER_PREFIX + "(" + match + ")", ENCRYPTED_WARPER_PREFIX + "(" + converted + ")");
                } else {
                    converted = SecurityUtil.decrypt(match, true);
                    line = line.replace(ENCRYPTED_WARPER_PREFIX + "(" + match + ")", DECRYPTED_WARPER_PREFIX + "(" + converted + ")");
                }
            } catch (Throwable ex) {
                System.out.println(ex + " - " + match + ": " + line);
            }
        }
        return line;
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

    /**
     *
     * @param expectedStr "Fantasticèéçà Entwickeln Sie mit Vergnügen"
     * @param targetCharset "Windows-1252"
     * @return
     */
    @Deprecated
    private static byte[] getBytesFromUTF8(String expectedStr, String targetCharset) {

        // Trick: -Dfile.encoding=UTF-8 or code below:
        // This way you are going to trick JVM which would think that charset is not set
        // and make it to set it again to UTF-8, on runtime!
//            System.setProperty("file.encoding", "UTF-8");
//            Field charset = Charset.class.getDeclaredField("defaultCharset");
//            charset.setAccessible(true);
//            charset.set(null, null);
        byte[] ret;
//        try ( ByteArrayInputStream original = new ByteArrayInputStream(expectedStr.getBytes());  InputStreamReader contentReader = new InputStreamReader(original, CharsetUtil.UTF_8);  ByteArrayOutputStream converted = new ByteArrayOutputStream();  Writer writer = new OutputStreamWriter(converted, targetCharset)) {
//            int readCount;
//            char[] buffer = new char[4096];
//            while ((readCount = contentReader.read(buffer, 0, buffer.length)) != -1) {
//                writer.write(buffer, 0, readCount);
//            }
//            ret = converted.toByteArray();
//        } catch (Throwable ex) {
//            ex.printStackTrace();
//            try {
//                ret = expectedStr.getBytes(targetCharset);
//            } catch (Throwable ex2) {
//                ex2.printStackTrace();
//                ret = expectedStr.getBytes(StandardCharsets.UTF_8);
//            }
//        }

        int readCount;
        char[] buffer = new char[4096];
        try {
            ByteArrayInputStream original = new ByteArrayInputStream(expectedStr.getBytes());
            InputStreamReader contentReader = new InputStreamReader(original, "UTF-8");
            try (ByteArrayOutputStream converted = new ByteArrayOutputStream()) {
                try (Writer writer = new OutputStreamWriter(converted, "Windows-1252")) {
                    while ((readCount = contentReader.read(buffer, 0, buffer.length)) != -1) {
                        writer.write(buffer, 0, readCount);
                    }
                }
                ret = converted.toByteArray();
                //actualStr = new String(converted.toByteArray(), "Windows-1252");
            }
        } catch (Throwable ex) {
            try {
                ret = expectedStr.getBytes(targetCharset);
            } catch (Throwable ex2) {
                ex2.printStackTrace();
                ret = expectedStr.getBytes(StandardCharsets.UTF_8);
            }
        }
        return ret;
    }
//    public static void main(String[] args) throws UnsupportedEncodingException {
//        DateTimeFormatter DTF = DateTimeFormatter.ofPattern("EEEE, dd LLLL, yyyy HH:mm:ss");
//        System.out.println(DTF.format(LocalDateTime.now()));
//    }

    public static DateTimeFormatter UTC_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static ZoneId ZONE_ID_ONTARIO = ZoneId.of("America/Toronto");

    /**
     * Maps the UTC time to an ET format.
     *
     * @param utcTime UTC time to be formatted.
     * @param zoneId
     *
     * @return ET formatted time.
     *
     */
    public static String transformUTCDateTimeToLocalDateTime(String utcTime, ZoneId zoneId) {
        if (StringUtils.isBlank(utcTime)) {
            return null;
        }
        return ZonedDateTime.parse(utcTime, UTC_DATE_TIME_FORMATTER)
                .withZoneSameInstant(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    }

    public static LocalDateTime toLocalDateTime(long utcTs) {
        return toLocalDateTime(utcTs, ZoneId.systemDefault());
    }

    public static LocalDateTime toLocalDateTime(long utcTs, ZoneId zoneId) {
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        return Instant.ofEpochMilli(utcTs).atZone(zoneId).toLocalDateTime();
    }

    public static String encodeMimeBase64(byte[] contentBytes) {
        return Base64.getMimeEncoder().encodeToString(contentBytes);
    }

    /**
     *
     * @param bi
     * @param format - png
     * @return
     * @throws IOException
     */
    public static byte[] toByteArray(BufferedImage bi, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, format, baos);
        byte[] bytes = baos.toByteArray();
        return bytes;
    }
}
