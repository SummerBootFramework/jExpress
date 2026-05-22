/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.util;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class FileUtil {
    public static String toBase64(File srcFile, File base64File, int maxSizePerFile) throws IOException {
        byte[] bytes = Files.readAllBytes(srcFile.toPath());
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);

        // Clean previous part files so stale files do not affect future decode.
        deletePartFiles(base64File);

        if (maxSizePerFile <= 0 || base64.length() <= maxSizePerFile) {
            Files.write(base64File.toPath(), base64.getBytes(StandardCharsets.UTF_8));
            return base64;
        }

        int partIndex = 1;
        for (int start = 0; start < base64.length(); start += maxSizePerFile) {
            int end = Math.min(start + maxSizePerFile, base64.length());
            String chunk = base64.substring(start, end);
            File partFile = partFile(base64File, partIndex++);
            Files.write(partFile.toPath(), chunk.getBytes(StandardCharsets.UTF_8));
        }

        // Remove single-file output when chunked output is used.
        Files.deleteIfExists(base64File.toPath());
        return base64;

    }

    public static byte[] fromBase64(File base64File, File targetFile) throws IOException {
        String base64;
        if (partFile(base64File, 1).exists()) {
            StringBuilder sb = new StringBuilder();
            int partIndex = 1;
            while (true) {
                File partFile = partFile(base64File, partIndex++);
                if (!partFile.exists()) {
                    break;
                }
                sb.append(new String(Files.readAllBytes(partFile.toPath()), StandardCharsets.UTF_8).trim());
            }
            base64 = sb.toString();
        } else {
            base64 = new String(Files.readAllBytes(base64File.toPath()), StandardCharsets.UTF_8).trim();
        }

        byte[] bytes = java.util.Base64.getDecoder().decode(base64);
        Files.write(targetFile.toPath(), bytes);
        return bytes;
    }

    private static File partFile(File base64File, int partIndex) {
        return new File(base64File.getAbsolutePath() + "." + partIndex);
    }

    private static void deletePartFiles(File base64File) throws IOException {
        int partIndex = 1;
        while (true) {
            File partFile = partFile(base64File, partIndex++);
            if (!partFile.exists()) {
                break;
            }
            Files.delete(partFile.toPath());
        }
    }

    public static final Tika TIKA = new Tika();
    public static final MimeTypes TIKA_REGISTRY = MimeTypes.getDefaultMimeTypes();

    public static String[] getMIMEShortExtension(byte[] fileData) {
        try {
            // 1. 先通过字节码识别出长 MIME 类型（例如 "image/png" 或 "image/jpeg"）
            String mimeTypeStr = TIKA.detect(fileData);

            // 2. 从 Tika 注册表中找到该类型对应的 MimeType 对象
            MimeType mimeType = TIKA_REGISTRY.getRegisteredMimeType(mimeTypeStr);

            // 3. 获取短扩展名（注意：Tika 返回的带有 "."，例如 ".png" 或 ".jpg"）
            MediaType mediaType = mimeType.getType();
            String type = mediaType.getType();
            String ext = mimeType.getExtension();

            // 如果你想去掉前面的点，可以做个简单处理
            if (ext != null && ext.startsWith(".")) {
                ext = ext.substring(1); // 返回 "png" 或 "jpg"
            }
            String[] ret = {mimeTypeStr, type, ext};
            return ret;
        } catch (Exception e) {
            String[] ret = {"", "", ""};
            return ret;
        }
    }

    public static String formatFileSize(long sizeOfBytes) {
        double size = sizeOfBytes / 1024.0d; // Start from KB so sub-1KB values become 0.xxKB
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int unitIndex = 0;

        while (size >= 1024.0d && unitIndex < units.length - 1) {
            size /= 1024.0d;
            unitIndex++;
        }

        // Manual two-decimal formatting avoids String.format(Locale, ...) overhead.
        long scaled = Math.round(size * 100.0d);
        long integerPart = scaled / 100;
        int fractionalPart = (int) (scaled % 100);

        StringBuilder sb = new StringBuilder(16);
        sb.append(integerPart).append('.');
        if (fractionalPart < 10) {
            sb.append('0');
        }
        sb.append(fractionalPart).append(units[unitIndex]);
        return sb.toString();
    }
}
