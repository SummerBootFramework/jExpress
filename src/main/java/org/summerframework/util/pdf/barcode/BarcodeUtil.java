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
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.oned.CodaBarWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.Code93Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.ITFWriter;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.oned.UPCEWriter;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.summerframework.util.FormatterUtil;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
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
    public static final Map<EncodeHintType, ?> DEFAULT_HINTS = Map.of(EncodeHintType.CHARACTER_SET, "utf-8", EncodeHintType.MARGIN, 0);

//    public static String toPDF417(String contents, int widthPixels, int heightPixels) throws IOException {
//        return toPDF417(contents, widthPixels, heightPixels, ARGB_BLACK, ARGB_TRANSPARENT);
//    }
    public static String generateBase64Image(String barcodeText, BarcodeFormat bf, int widthPixels, int heightPixels, int onColor, int offColor) throws IOException {
        return generateBase64Image(barcodeText, bf, widthPixels, heightPixels, onColor, offColor, DEFAULT_HINTS);
    }

    /**
     * {@code Useage in HTML <img src="data:image/png;base64,${barcode image string}" alt="barcode" />}.
     *
     * @param barcodeText
     * @param format
     * @param widthPixels
     * @param heightPixels
     * @param onColor ARGB
     * @param offColor ARGB
     * @param cfg
     * @return
     * @throws IOException
     */
    public static String generateBase64Image(String barcodeText, BarcodeFormat format, int widthPixels, int heightPixels, int onColor, int offColor, Map<EncodeHintType, ?> cfg) throws IOException {
        Writer writer;
        switch (format) {
            case AZTEC:
                writer = new AztecWriter();
                break;
            case CODABAR:
                writer = new CodaBarWriter();
                break;
            case CODE_128:
                writer = new Code128Writer();
                break;
            case CODE_39:
                writer = new Code39Writer();
                break;
            case CODE_93:
                writer = new Code93Writer();
                break;
            case DATA_MATRIX:
                writer = new DataMatrixWriter();
                break;
            case EAN_13:
                writer = new EAN13Writer();
                break;
            case EAN_8:
                writer = new EAN8Writer();
                break;
            case ITF:
                writer = new ITFWriter();
                break;
            case PDF_417:
                writer = new PDF417Writer();
                break;
            case QR_CODE:
                writer = new QRCodeWriter();
                break;
            case UPC_A:
                writer = new UPCAWriter();
                break;
            case UPC_E:
                writer = new UPCEWriter();
                break;
            case RSS_14:
            case MAXICODE:
            case UPC_EAN_EXTENSION:
            default:
                throw new IllegalArgumentException("No encoder available for format " + format);

        }
        BitMatrix bitMatrix = generateBarcode(barcodeText, writer, format, widthPixels, heightPixels, cfg);
        byte[] data = toByteArray(bitMatrix, "png", onColor, offColor);
        return FormatterUtil.encodeMimeBase64(data);
    }

    public static BitMatrix generateBarcode(String barcodeText, Writer writer, BarcodeFormat bf, int widthPixels, int heightPixels, Map<EncodeHintType, ?> cfg) throws IOException {
        try {
            return writer.encode(barcodeText, bf, widthPixels, heightPixels, cfg);
        } catch (WriterException ex) {
            throw new IOException(ex);
        }
    }

//    public static String encodeMimeBase64(byte[] contentBytes) {
//        return Base64.getMimeEncoder().encodeToString(contentBytes);
//    }
//    public static byte[] toByteArray(BitMatrix matrix, String format) throws IOException {
//        return toByteArray(matrix, format, ARGB_BLACK, ARGB_TRANSPARENT);
//    }
    /**
     *
     * @param matrix
     * @param format png
     * @param onColor ARGB
     * @param offColor ARGB
     * @return
     * @throws IOException
     */
    public static byte[] toByteArray(BitMatrix matrix, String format, int onColor, int offColor) throws IOException {
        byte[] bytes;
        MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            MatrixToImageWriter.writeToStream(matrix, format, baos, config);
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
