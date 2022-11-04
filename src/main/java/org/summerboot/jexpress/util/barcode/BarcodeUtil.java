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
package org.summerboot.jexpress.util.barcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.summerboot.jexpress.util.FormatterUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BarcodeUtil {

    //    public static void main(String[] args) throws IOException {
//        BitMatrix bitMatrix = generatePDF417("123451234567812", 480, 200);
//        byte[] data = toByteArray(bitMatrix, "png");
//        java.io.File f = new java.io.File("run/pdf417.png").getAbsoluteFile();
//        System.out.println("save to " + f);
//        Files.write(f.toPath(), data);
//    }
    public static final int ARGB_BLACK = 0xff000000;
    public static final int ARGB_WHITE = 0xfffffae7;//0xfffffae7;
    public static final int ARGB_TRANSPARENT = 0x00ffffff;//0xfffffae7;
    public static final Map<EncodeHintType, ?> DEFAULT_COFNIG = Map.of(
            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8,
            EncodeHintType.MARGIN, 0);

    /**
     * {@code Useage in HTML <img src="data:image/png;base64,${barcode image string}" alt="barcode" />}.
     *
     * @param barcodeText
     * @param barcodeFormat
     * @param widthPixels
     * @param heightPixels
     * @param cfg
     * @param imageFormat png, jpg, etc.
     * @param onColor ARGB
     * @param offColor ARGB
     * @return
     * @throws IOException
     */
    public static String generateBase64Image(String barcodeText, BarcodeFormat barcodeFormat, int widthPixels, int heightPixels, Map<EncodeHintType, ?> cfg, String imageFormat, int onColor, int offColor) throws IOException {
        BitMatrix bitMatrix = generateBarcode(barcodeText, barcodeFormat, widthPixels, heightPixels, cfg);
        byte[] data = toByteArray(bitMatrix, imageFormat, onColor, offColor);
        return FormatterUtil.base64MimeEncode(data);
    }

    public static BitMatrix generateBarcode(String barcodeText, BarcodeFormat format, int widthPixels, int heightPixels, Map<EncodeHintType, ?> cfg) throws IOException {
        Writer writer = new MultiFormatWriter();
        return generateBarcode(barcodeText, writer, format, widthPixels, heightPixels, cfg);
    }

    public static BitMatrix generateBarcode(String barcodeText, Writer writer, BarcodeFormat format, int widthPixels, int heightPixels, Map<EncodeHintType, ?> cfg) throws IOException {
        try {
            return writer.encode(barcodeText, format, widthPixels, heightPixels, cfg);
        } catch (WriterException ex) {
            throw new IOException(writer.toString() + "(" + format + ")", ex);
        }
    }

//    public static String base64MimeEncode(byte[] contentBytes) {
//        return Base64.getMimeEncoder().encodeToString(contentBytes);
//    }
//    public static byte[] toByteArray(BitMatrix matrix, String format) throws IOException {
//        return toByteArray(matrix, format, ARGB_BLACK, ARGB_TRANSPARENT);
//    }
    /**
     *
     * @param matrix
     * @param imageFormat png
     * @param onColor ARGB
     * @param offColor ARGB
     * @return
     * @throws IOException
     */
    public static byte[] toByteArray(BitMatrix matrix, String imageFormat, int onColor, int offColor) throws IOException {
        byte[] bytes;
        MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            MatrixToImageWriter.writeToStream(matrix, imageFormat, baos, config);
            bytes = baos.toByteArray();
        }
        return bytes;
    }

    public static BufferedImage toBufferedImage(BitMatrix matrix, int onColor, int offColor) {
//        int widthPixels = matrix.getWidth();
//        int heightPixels = matrix.getHeight();
//        BufferedImage image = new BufferedImage(widthPixels, heightPixels, BufferedImage.TYPE_INT_ARGB);
//        for (int x = 0; x < widthPixels; x++) {
//            for (int y = 0; y < heightPixels; y++) {
//                image.setRGB(x, y, matrix.get(x, y) == true ? ARGB_BLACK : ARGB_WHITE);
//            }
//        }
//        return image;
        MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
        return MatrixToImageWriter.toBufferedImage(matrix, config);
    }
}
