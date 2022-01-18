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
package org.summerframework.util.pdf.barcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.pdf417.PDF417Writer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class BarcodeUtil {

    //    public static void main(String[] args) throws IOException {
//        BitMatrix bitMatrix = buildPDF417("123451234567812", 480, 200);
//        byte[] data = toByteArray(bitMatrix, "png");
//        java.io.File f = new java.io.File("run/pdf417.png").getAbsoluteFile();
//        System.out.println("save to " + f);
//        Files.write(f.toPath(), data);
//    }
    public static final int ARGB_BLACK = 0xff000000;
    public static final int ARGB_WHITE = 0xfffffae7;//0xfffffae7;
    public static final int ARGB_TRANSPARENT = 0x00ffffff;//0xfffffae7;

//    public static String toPDF417(String contents, int width, int height) throws IOException {
//        return toPDF417(contents, width, height, ARGB_BLACK, ARGB_TRANSPARENT);
//    }
    public static String toPDF417(String contents, int width, int height, int onColor, int offColor) throws IOException {
        BitMatrix bitMatrix = buildPDF417(contents, width, height);
        byte[] data = toByteArray(bitMatrix, "png", onColor, offColor);
        return encodeMimeBase64(data);
    }

    public static BitMatrix buildPDF417(String contents, int width, int height) throws IOException {
        Writer writer = new PDF417Writer();
        try {
            return writer.encode(contents, BarcodeFormat.PDF_417, width, height, HINTS);
        } catch (WriterException ex) {
            throw new IOException(ex);
        }
    }
    
    public static String encodeMimeBase64(byte[] contentBytes) {
        return Base64.getMimeEncoder().encodeToString(contentBytes);
    }

    private static final Map<EncodeHintType, Object> HINTS = Map.of(EncodeHintType.CHARACTER_SET, "utf-8",
            EncodeHintType.MARGIN, 0);

//    public static byte[] toByteArray(BitMatrix matrix, String format) throws IOException {
//        return toByteArray(matrix, format, ARGB_BLACK, ARGB_TRANSPARENT);
//    }
    public static byte[] toByteArray(BitMatrix matrix, String format, int onColor, int offColor) throws IOException {
        byte[] bytes;
        MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            MatrixToImageWriter.writeToStream(matrix, format, baos, config);
            bytes = baos.toByteArray();
        }
        return bytes;
    }

    public static BufferedImage toBufferedImage(BitMatrix matrix) {
//        int width = matrix.getWidth();
//        int height = matrix.getHeight();
//        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
//        for (int x = 0; x < width; x++) {
//            for (int y = 0; y < height; y++) {
//                image.setRGB(x, y, matrix.get(x, y) == true ? ARGB_BLACK : ARGB_WHITE);
//            }
//        }
//        return image;
        return MatrixToImageWriter.toBufferedImage(matrix);
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
