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
package org.summerframework.security;

import org.summerframework.util.FormatterUtil;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
//import jcifs.util.Base64;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public final class SecurityUtil {

    public static final HostnameVerifier DO_NOT_VERIFY_REMOTE_IP = (String hostname, SSLSession session) -> true;
    public static final HostnameVerifier hostnameVerifier = (String hostname, SSLSession session) -> {
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        return hv.verify("hostname", session);
    };

    public static final String[] CIPHER_SUITES = {"TLS_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_256_CBC_SHA", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384", "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDH_anon_WITH_AES_256_CBC_SHA", "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_NULL_SHA", "TLS_ECDH_RSA_WITH_NULL_SHA", "TLS_ECDH_anon_WITH_NULL_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_NULL_SHA,TLS_ECDHE_RSA_WITH_NULL_SHA"};

    public static byte[] buildSecretKey(String password) {
        byte[] ret = null;
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");// 创建AES的Key生产者
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(password.getBytes());
            kgen.init(128, secureRandom);// 利用用户密码作为随机数初始化出
            // 128位的key生产者
            //加密没关系，SecureRandom是生成安全随机数序列，password.getBytes()是种子，只要种子相同，序列就一样，所以解密只要有password就行
            SecretKey secretKey = kgen.generateKey();// 根据用户密码，生成一个密钥
            ret = secretKey.getEncoded();// 返回基本编码格式的密钥，如果此密钥不支持编码，则返回null。
        } catch (NoSuchAlgorithmException ex) {

        }
        return ret;
    }

    public static Key[] parseKeyPair(String publicKeyContent, String privateKeyContent, String keyfactoryAlgorithm) throws NoSuchAlgorithmException, InvalidKeySpecException {
        //RSA
        Key[] ret = {null, null};
        KeyFactory kf = KeyFactory.getInstance(keyfactoryAlgorithm, new BouncyCastleProvider());
        if (publicKeyContent != null) {
            publicKeyContent = publicKeyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = java.util.Base64.getDecoder().decode(publicKeyContent);
            //byte[] encoded = Base64.decode(publicKeyContent);
            X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(encoded);
            Key pubKey = kf.generatePublic(keySpecX509);
            ret[0] = pubKey;
        }
        if (privateKeyContent != null) {
            privateKeyContent = privateKeyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            //byte[] header = Hex.decode("30 81bf 020100 301006072a8648ce3d020106052b81040022 0481a7");
            byte[] encoded = java.util.Base64.getDecoder().decode(privateKeyContent);
            //byte[] encoded = Base64.decode(privateKeyContent);
            //byte[] bytes = Arrays.concatenate(header, encoded);
            PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(encoded);
            Key privKey = kf.generatePrivate(keySpecPKCS8);
            ret[1] = privKey;

        }

        return ret;
    }

    

    public static Key SCERET_KEY = new SecretKeySpec(buildSecretKey("changeit"), "AES");

    /**
     *
     * @param plainData
     * @param warped true if the encrypted value is in a warper like
     * password=DEC(encrypted password)
     * @return
     * @throws GeneralSecurityException
     */
    public static String encrypt(String plainData, boolean warped) throws GeneralSecurityException {
        if (warped) {
            plainData = FormatterUtil.getInsideParenthesesValue(plainData);
        }
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, SCERET_KEY);
        byte[] utf8 = plainData.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedData = cipher.doFinal(utf8);
        //String result = Base64.encode(encryptedData);
        String result = Base64.getEncoder().encodeToString(encryptedData);
        for (int i = 0; i < utf8.length; i++) {
            utf8[i] = 0;
        }
        for (int i = 0; i < encryptedData.length; i++) {
            encryptedData[i] = 0;
        }
        return result;
    }

    /**
     * decrypt encrypted value with prefix to plain text char array
     *
     * @param encrypted
     * @param warped true if the encrypted value is in a warper like
     * password=ENC(encrypted password)
     * @return
     * @throws GeneralSecurityException
     */
    public static char[] decrypt2Char(String encrypted, boolean warped) throws GeneralSecurityException {
        String decrypted = decrypt(encrypted, warped);
        return decrypted == null ? null : decrypted.toCharArray();
    }

    /**
     * decrypt encrypted value with prefix to plain text
     *
     * @param encrypted
     * @param warped true if the encrypted value is in a warper like
     * password=ENC(encrypted password)
     * @return
     * @throws GeneralSecurityException
     */
    public static String decrypt(String encrypted, boolean warped) throws GeneralSecurityException {
        if (warped) {
            encrypted = FormatterUtil.getInsideParenthesesValue(encrypted);
        }
        byte[] utf8 = decryptEx(encrypted);
        if (utf8 == null) {
            return null;
        }
        String result = new String(utf8, StandardCharsets.UTF_8);
        for (int i = 0; i < utf8.length; i++) {
            utf8[i] = 0;
        }
        return result;
    }

    public static byte[] decryptEx(String encrypted) throws GeneralSecurityException {
        if (encrypted == null) {
            return null;
        }
        byte[] result;
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, SCERET_KEY);
        //byte[] decodedData = Base64.decode(encrypted);
        byte[] decodedData = Base64.getDecoder().decode(encrypted);
        result = cipher.doFinal(decodedData);
        for (int i = 0; i < decodedData.length; i++) {
            decodedData[i] = 0;
        }
        return result;
    }

//    public static String base64MimeDecode(String base64Text) throws UnsupportedEncodingException {
//        // Decode base64 to get bytes
//        //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
//        byte[] dec = java.util.Base64.getDecoder().decode(base64Text);
//        // Decode using utf-8
//        return new String(dec, StandardCharsets.UTF_8);
//    }
    public static String base64Decode(String base64Text) {
        // Decode base64 to get bytes
        //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
        //byte[] dec = Base64.decode(base64Text);
        byte[] dec = Base64.getDecoder().decode(base64Text);
        // Decode using utf-8
        return new String(dec, StandardCharsets.UTF_8);
    }

//    public static String base64MimeEncode(String plainText) throws UnsupportedEncodingException {
//        // Decode base64 to get bytes
//        //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
//        byte[] dec = java.util.Base64.getEncoder().encode(plainText.getBytes(StandardCharsets.UTF_8));
//        // Decode using utf-8
//        return new String(dec, StandardCharsets.UTF_8);
//    }
    public static String base64Encode(String plain) {
        //return Base64.encode(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public static final Pattern hasUppercase = Pattern.compile("[A-Z]");
    public static final Pattern hasLowercase = Pattern.compile("[a-z]");
    public static final Pattern hasNumber = Pattern.compile("\\d");
    public static final Pattern hasSpecialChar = Pattern.compile("[^a-zA-Z0-9 ]");

    public static boolean validatePassword(String pwd, int length) {
        if (StringUtils.isBlank(pwd)) {
            return false;
        }
        if (pwd.length() < length) {
            return false;
        }
        if (!hasUppercase.matcher(pwd).find()) {
            return false;
        }
        if (!hasLowercase.matcher(pwd).find()) {
            return false;
        }
        if (!hasNumber.matcher(pwd).find()) {
            return false;
        }
        if (!hasSpecialChar.matcher(pwd).find()) {
            return false;
        }
        return true;
    }

}
