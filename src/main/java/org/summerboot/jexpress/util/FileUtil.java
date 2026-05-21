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

}
