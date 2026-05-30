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
package org.summerboot.jexpress.common.util;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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


    // 1. Thread-safe heavy objects initialized as static constants (Best practice for performance)
    private static final Tika TIKA = new Tika();
    private static final MimeTypes TIKA_REGISTRY = MimeTypes.getDefaultMimeTypes();

    /**
     * Complete metadata wrapper to hold structural results safely.
     */
    public static class FileTypeInfo {
        private final String mimeType;   // e.g., "image/png" or "application/x-rar-compressed; version=4"
        private final String group;      // e.g., "image", "video", "application"
        private final String extension;  // e.g., "png", "rar" (Cleaned without the leading dot)

        public FileTypeInfo(String mimeType, String group, String extension) {
            this.mimeType = mimeType != null ? mimeType : "";
            this.group = group != null ? group : "";
            this.extension = extension != null ? extension : "";
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getGroup() {
            return group;
        }

        public String getExtension() {
            return extension;
        }

        @Override
        public String toString() {
            return String.format("FileTypeInfo{mimeType='%s', group='%s', extension='%s'}", mimeType, group, extension);
        }
    }

    /**
     * Detects file information from a byte array.
     * Ideal for working with small files, uploaded chunk buffers, or Base64 byte arrays.
     */
    public static FileTypeInfo detectMimeType(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            return new FileTypeInfo("", "", "");
        }
        try {
            // Tika.detect(byte[]) is completely thread-safe
            String rawMimeType = TIKA.detect(fileData);
            return parseMimeTypeDetails(rawMimeType);
        } catch (Exception e) {
            // Replace with your project's logger if available, e.g., log.error("Failed to detect file type from bytes", e);
            return new FileTypeInfo("", "", "");
        }
    }

    /**
     * Detects file information from an InputStream.
     * RECOMMENDED for large files (e.g., Spring MultipartFile.getInputStream()) to avoid loading full bytes into memory.
     */
    public static FileTypeInfo detectMimeType(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return new FileTypeInfo("", "", "");
        }

        // Tika automatically reads the stream's prefix magic numbers and resets/marks the stream safely
        String rawMimeType = TIKA.detect(inputStream);
        return parseMimeTypeDetails(rawMimeType);
    }

    /**
     * Dual-layered detection combining File Name + Byte Stream.
     * Provides the absolute highest accuracy for Zip-based formats (e.g., distinguishing between .docx, .xlsx, and .jar).
     */
    public static FileTypeInfo detectTypeWithHint(InputStream inputStream, String originalFilename) {
        try {
            // Tika uses the original filename as a fallback hint to resolve magic-number collisions
            String rawMimeType = TIKA.detect(inputStream, originalFilename);
            return parseMimeTypeDetails(rawMimeType);
        } catch (Exception e) {
            return new FileTypeInfo("", "", "");
        }
    }

    /**
     * Internal structural parser that isolates parameters, prevents NullPointerException,
     * and maps MIME types to their short extensions.
     */
    private static FileTypeInfo parseMimeTypeDetails(String rawMimeType) {
        if (rawMimeType == null || rawMimeType.trim().isEmpty()) {
            return new FileTypeInfo("", "", "");
        }

        try {
            // Defensive cleaning: Isolates basic type from parameter extensions (e.g., text/html; charset=utf-8 -> text/html)
            MediaType mediaType = MediaType.parse(rawMimeType);
            String baseMimeType = mediaType.getBaseType().toString();
            String group = mediaType.getType(); // Extracts main group (e.g., "audio", "video")

            // Lookup the matching type instance in the global registry
            MimeType mimeType = TIKA_REGISTRY.getRegisteredMimeType(baseMimeType);
            String extension = "";

            if (mimeType != null) {
                extension = mimeType.getExtension(); // Returns extension containing the leading dot (e.g., ".png")
                if (extension != null && extension.startsWith(".")) {
                    extension = extension.substring(1); // Strip the leading dot -> "png"
                }
            } else {
                // Secondary fallback: If the database is missing a rare format, extract the subtype as a safe guess
                if (baseMimeType.contains("/")) {
                    extension = baseMimeType.substring(baseMimeType.indexOf("/") + 1)
                            .replace("x-", "")
                            .replaceAll("\\+.*", ""); // Cleans up things like 'svg+xml' -> 'svg'
                }
            }

            return new FileTypeInfo(rawMimeType, group, extension);

        } catch (Exception e) {
            return new FileTypeInfo(rawMimeType, "", "");
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

    /**
     * Encodes the source ByteBuf directly to Base64.
     * Use this alongside your stream operations.
     */
    public static String toBase64(ByteBuf srcBuffer) {
        if (srcBuffer == null || srcBuffer.readableBytes() == 0) {
            return "";
        }

        ByteBuf encodedBuffer = null;
        try {
            // Netty's native encoder reads between srcBuffer.readerIndex() and writerIndex()
            encodedBuffer = Base64.encode(srcBuffer, false);
            return encodedBuffer.toString(CharsetUtil.UTF_8);
        } finally {
            if (encodedBuffer != null) {
                encodedBuffer.release(); // Always release the temporary encoded buffer
            }
        }
    }
}
