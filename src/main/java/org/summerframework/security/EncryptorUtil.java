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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class EncryptorUtil {

    private static final String AES_KEY_ALGO = "AES";
    private static final String DSA_KEY_ALGO = "DSA";
    private static final String RSA_KEY_ALGO = "RSA";
    private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding";
    private static final String RSA_Cipher_Algorithm = "RSA/ECB/PKCS1Padding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int AES_KEY_BIT = 256;

    static {
        try {
            Security.addProvider(new BouncyCastleProvider());
            System.setProperty("hostName", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ex) {
            ex.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    /**
     *
     * @param filename
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public static byte[] md5(File filename) throws NoSuchAlgorithmException, IOException {
        MessageDigest complete = MessageDigest.getInstance("MD5");
        try (InputStream fis = new FileInputStream(filename);) {
            byte[] buffer = new byte[1024];
            int numRead;
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    complete.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
        }
        return complete.digest();
    }

    /**
     *
     * @param text
     * @return
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    public static byte[] md5(String text) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return md5(text.getBytes(StandardCharsets.UTF_8), "MD5");
    }

    /**
     *
     * @param data
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static byte[] md5(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        //md.reset();
        md.update(data);
        byte[] digest = md.digest();
        return digest;
    }

    /**
     *
     * @param data
     * @param algorithm MD5 (32-bit), SHA, SHA-256 (64-bit), SHA-384, SHA-512
     * (128-bit)
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static byte[] md5(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        //md.reset();
        md.update(data);
        byte[] digest = md.digest();
        return digest;
    }

    public static String md5ToString(byte[] md5) {
        StringBuilder sb = new StringBuilder();
        if (md5 != null) {
            for (byte b : md5) {
                sb.append(String.format("%02X", b));
            }
        }
        return sb.toString();
    }

    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_KEY_ALGO);
        keyGen.init(AES_KEY_BIT, SecureRandom.getInstanceStrong());
        return keyGen.generateKey();
    }

    public static SecretKey loadSymmetricKey(String symmetricKeyFile, String symmetricKeyAlgorithm) throws IOException {
        byte[] symmetricKeyBytes = Files.readAllBytes(Paths.get(symmetricKeyFile));
        return loadSymmetricKey(symmetricKeyBytes, symmetricKeyAlgorithm);
    }

    public static SecretKey loadSymmetricKey(byte[] symmetricKeyBytes, String symmetricKeyAlgorithm) throws IOException {
        //int keyBytes = symmetricKeyBytes.length;
        //System.out.println("keySize=" + keySize);
        if (symmetricKeyAlgorithm == null) {
            symmetricKeyAlgorithm = AES_KEY_ALGO;
        }
        SecretKey symmetricKey = new SecretKeySpec(symmetricKeyBytes, symmetricKeyAlgorithm);
        return symmetricKey;
    }

    public static byte[] generateInitializationVector(int ivBytes) {
        byte[] nonce = new byte[ivBytes];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    public static Cipher buildCypher_GCM(boolean encrypt, SecretKey symmetricKey, byte[] iv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        if (encrypt) {
            cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        } else {
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        }
        return cipher;
    }

    public static byte[] encrypt(SecretKey symmetricKey, byte[] iv, byte[] plainData) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = buildCypher_GCM(true, symmetricKey, iv);
        byte[] encryptedData = cipher.doFinal(plainData);
        return encryptedData;
    }

    public static byte[] decrypt(SecretKey symmetricKey, byte[] iv, byte[] encryptedData) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = buildCypher_GCM(false, symmetricKey, iv);
        byte[] plainData = cipher.doFinal(encryptedData);
        return plainData;
    }

    public enum KeyFileType {

        X509, Certificate, PKCS12, JKS, PKCS8
    }

    public static KeyPair generateKeyPair_RSA4096() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return generateKeyPair("RSA", 4096);
    }

    /**
     * <code>openssl genrsa -out keypair.pem 2048</code>
     * <code>openssl rsa -in keypair.pem -pubout -out public.pem -outform PEM</code>
     * <code>openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in keypair.pem -out pkcs8.key</code>
     *
     * @param keyfactoryAlgorithm - RSA(2048), EC(571)
     * @param size
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    public static KeyPair generateKeyPair(String keyfactoryAlgorithm, int size) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (keyfactoryAlgorithm == null) {
            keyfactoryAlgorithm = "RSA";
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyfactoryAlgorithm);
        kpg.initialize(size);
        return kpg.generateKeyPair();
    }

    public static void secureMem(char[] pwd) {
        if (pwd == null) {
            return;
        }
        for (int i = 0; i < pwd.length; i++) {
            pwd[i] = 0;
        }
    }

    /**
     *
     * @param fileType
     * @param keystoreFile
     * @param keyStorePwd
     * @param alias
     * @param privateKeyPwd
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    public static KeyPair loadKeyPair(KeyFileType fileType, File keystoreFile, char[] keyStorePwd, String alias, char[] privateKeyPwd) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
        String keyStoreType;
        switch (fileType) {
            case PKCS12:
                keyStoreType = "PKCS12";
                break;
            case JKS:
                keyStoreType = KeyStore.getDefaultType();
                break;
            default:
                throw new NoSuchAlgorithmException(fileType.name());
        }
        FileInputStream is = new FileInputStream(keystoreFile);
        KeyStore keystore = KeyStore.getInstance(keyStoreType);
        keystore.load(is, keyStorePwd);
        secureMem(keyStorePwd);

        // Get private key
        PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, privateKeyPwd);
        secureMem(keyStorePwd);
        if (privateKey == null) {
            throw new KeyStoreException("private key of alias(" + alias + ") not found");
        }
        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);
        if (cert == null) {
            throw new KeyStoreException("certificate of alias(" + alias + ") not found");
        }
        // Get public key
        PublicKey publicKey = cert.getPublicKey();
        // Return a key pair
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        return keyPair;
    }

    // to be continued
//    public static KeyPair createKeyPair(KeyFileType fileType, File keystoreFile, char[] keyStorePwd, String alias, char[] privateKeyPwd,
//            String newAlias, String newPwd) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException, InvalidKeySpecException {
//        String keyStoreType;
//        switch (fileType) {
//            case PKCS12:
//                keyStoreType = "PKCS12";
//                break;
//            case JKS:
//                keyStoreType = KeyStore.getDefaultType();
//                break;
//            default:
//                throw new NoSuchAlgorithmException(fileType.name());
//        }
//        FileInputStream is = new FileInputStream(keystoreFile);
//        KeyStore keystore = KeyStore.getInstance(keyStoreType);
//        keystore.load(is, keyStorePwd);
//        secureMem(keyStorePwd);
//        
//        if(keystore.containsAlias(newAlias)) {
//            throw new KeyStoreException("alias " + newAlias+" already exists");
//        }        
//        KeyPair newKeyPair = generateKeyPair(null, 2048);
//        keystore.
//        
//        return newKeyPair;
//    }
    public static PublicKey loadPublicKey(KeyFileType fileType, File publicKeyFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
        return loadPublicKey(fileType, publicKeyFile, RSA_KEY_ALGO);
    }

    /**
     *
     * @param fileType
     * @param publicKeyFile
     * @param algorithm
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws CertificateException
     */
    public static PublicKey loadPublicKey(KeyFileType fileType, File publicKeyFile, String algorithm) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
        PublicKey publicKey;
        switch (fileType) {
            case X509:
            case PKCS12:
            case JKS:
            case PKCS8:
                // Load the public key bytes
//                byte[] keyBytes = Files.readAllBytes(Paths.get(publicKeyFile.getAbsolutePath()));
//                String keyPEM = new String(keyBytes, Charset.defaultCharset());
//                int begin = keyPEM.indexOf("-----");
//                keyPEM = keyPEM.substring(begin);
//                keyPEM = keyPEM
//                        .replace("-----BEGIN PUBLIC KEY-----", "")
//                        .replaceAll(System.lineSeparator(), "")
//                        .replace("-----END PUBLIC KEY-----", "")
//                        //                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
//                        //                .replace("-----END RSA PRIVATE KEY-----", "")
//                        //                .replace("-----BEGIN EC PRIVATE KEY-----", "")
//                        //                .replace("-----END EC PRIVATE KEY-----", "")
//                        //.replaceAll(System.lineSeparator(), "");
//                        .replaceAll("\\s+", "");
                byte[] encoded = loadPermKeyFileContent(publicKeyFile);
                // Turn the encoded key into a real RSA public key.
                // Public keys are encoded in X.509.
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
                KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                publicKey = keyFactory.generatePublic(keySpec);
                break;
            case Certificate:
                FileInputStream fin = new FileInputStream(publicKeyFile);
                CertificateFactory f = CertificateFactory.getInstance("X.509");
                X509Certificate certificate = (X509Certificate) f.generateCertificate(fin);
                publicKey = certificate.getPublicKey();
                break;
            default:
                throw new NoSuchAlgorithmException(fileType.name());
        }
        return publicKey;
    }

    public static PrivateKey loadPrivateKey(File pemFile) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        return loadPrivateKey(pemFile, RSA_KEY_ALGO);
    }

    public static PrivateKey loadPrivateKey(File pemFile, String algorithm) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        byte[] pkcs8Data = loadPermKeyFileContent(pemFile);

        // Turn the encoded key into a real RSA private key.
        // Private keys are encoded in PKCS#8.
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Data);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        return privateKey;
    }

    public static PrivateKey loadPrivateKey(File pemFile, char... password) throws IOException, OperatorCreationException, GeneralSecurityException {
        if (password == null || password.length < 1) {
            return loadPrivateKey(pemFile);
        }
//        Provider[] ps = Security.getProviders();
//        for (Provider p : ps) {
//            System.out.println("Provider.before=" + p);
//        }
//        ps = Security.getProviders();
//        for (Provider p : ps) {
//            System.out.println("Provider.after=" + p);
//        }

//        Provider provider = Security.getProvider(providerName);
//        if (provider == null) {
//            System.out.println(providerName + " provider not installed");
//            return null;
//        }
//        System.out.println("Provider Name :" + provider.getName());
//        System.out.println("Provider Version :" + provider.getVersion());
//        System.out.println("Provider Info:" + provider.getInfo());
        byte[] pkcs8Data = loadPermKeyFileContent(pemFile);

        ASN1Sequence derseq = ASN1Sequence.getInstance(pkcs8Data);
        PKCS8EncryptedPrivateKeyInfo encobj = new PKCS8EncryptedPrivateKeyInfo(EncryptedPrivateKeyInfo.getInstance(derseq));
        // decrypt and convert key
        JceOpenSSLPKCS8DecryptorProviderBuilder jce = new JceOpenSSLPKCS8DecryptorProviderBuilder();
//        String providerName = "BC";//BouncyCastle
//        jce.setProvider(providerName);
        InputDecryptorProvider decryptionProv = jce.build(password);
        PrivateKeyInfo privateKeyInfo;
        try {
            privateKeyInfo = encobj.decryptPrivateKeyInfo(decryptionProv);
        } catch (PKCSException ex) {
            throw new GeneralSecurityException("Invalid private key password", ex);
        } finally {
            for (int i = 0; i < password.length; i++) {
                password[i] = 0;
            }
        }
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
        return privateKey;
    }

    public static byte[] loadPermKeyFileContent(File pemFile) throws InvalidKeySpecException, IOException {
        // Load the private key bytes
        byte[] keyBytes = Files.readAllBytes(Paths.get(pemFile.getAbsolutePath()));
        String keyPEM = new String(keyBytes, Charset.defaultCharset());
        int begin = keyPEM.indexOf("-----");
        if (begin < 0) {
            throw new InvalidKeySpecException("missing key header");
        }
        keyPEM = keyPEM.substring(begin);
        keyPEM = keyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----END ENCRYPTED PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                //.replaceAll(System.lineSeparator(), "");
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        //byte[] encoded = java.util.Base64.getDecoder().decode(privateKeyContent);
        //byte[] header = Hex.decode("30 81bf 020100 301006072a8648ce3d020106052b81040022 0481a7");
        byte[] pkcs8Data = Base64.getDecoder().decode(keyPEM);
        return pkcs8Data;
    }

    /**
     *
     * @param cipherMode Cipher.ENCRYPT_MODE(1) or Cipher.DECRYPT_MODE(2)
     * @param asymmetricKey
     * @param in
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    private static byte[] asymmetric(int cipherMode, Key asymmetricKey, byte[] in) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        Cipher rsaCipher = Cipher.getInstance(RSA_Cipher_Algorithm);
        rsaCipher.init(cipherMode, asymmetricKey);
        // Encrypt the Rijndael key with the RSA cipher
        // and write it to the beginning of the file.
        byte[] out = rsaCipher.doFinal(in);
        return out;
    }

    public static byte[] encrypt(Key asymmetricKey, byte[] in) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        return asymmetric(Cipher.ENCRYPT_MODE, asymmetricKey, in);
    }

    public static byte[] decrypt(Key asymmetricKey, byte[] in) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        return asymmetric(Cipher.DECRYPT_MODE, asymmetricKey, in);
    }

    /**
     * encrypt large file
     *
     * @param asymmetricKey symmetric encryption will be used if null
     * @param symmetricKey encrypt with random session key if null
     * @param plainDataFileName
     * @param encryptedFileName
     * @param digitalSignatureKey - to sign the digital signature if not null
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static void encrypt(Key asymmetricKey, SecretKey symmetricKey, String plainDataFileName, String encryptedFileName, Key digitalSignatureKey) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        //0. metadata        
        String metaInfo = System.currentTimeMillis() + ", " + System.getProperty("hostName");
        byte[] metadata = metaInfo.getBytes(StandardCharsets.UTF_8);
        if (digitalSignatureKey != null) {
            metadata = encrypt(digitalSignatureKey, metadata);
        }
        boolean randomSymmetricKey = symmetricKey == null;
        //1. create cipher wtiht this key
        if (randomSymmetricKey) {
            symmetricKey = generateAESKey();
        }
        byte[] iv = generateInitializationVector(IV_LENGTH_BYTE);
        Cipher cipher = buildCypher_GCM(true, symmetricKey, iv);

        //2. asymmetric encrypt file header
        byte[] asymmetricEncryptedMD5 = null, asymmetricEncryptedIV = iv, asymmetricEncryptedSessionKey = null, asymmetricEncryptedSymmetricKeyAlgorithm = null;
        if (asymmetricKey != null) {
            byte[] md5 = md5(new File(plainDataFileName));
            asymmetricEncryptedMD5 = encrypt(asymmetricKey, md5);
            asymmetricEncryptedIV = encrypt(asymmetricKey, iv);
            if (randomSymmetricKey) {
                asymmetricEncryptedSymmetricKeyAlgorithm = symmetricKey.getAlgorithm().getBytes(StandardCharsets.ISO_8859_1);
                asymmetricEncryptedSymmetricKeyAlgorithm = encrypt(asymmetricKey, asymmetricEncryptedSymmetricKeyAlgorithm);
                asymmetricEncryptedSessionKey = encrypt(asymmetricKey, symmetricKey.getEncoded());
            }
        }

        //3. streaming
        try (InputStream plainDataInputStream = new FileInputStream(plainDataFileName); FileOutputStream fos = new FileOutputStream(encryptedFileName); DataOutputStream output = new DataOutputStream(fos); CipherOutputStream cos = new CipherOutputStream(output, cipher);) {
            //3.0 write metadata
            output.writeInt(metadata.length);
            output.write(metadata);
            //3.1 write asymmetric encrypted md5.length and md5 data
            if (asymmetricEncryptedMD5 != null) {
                output.writeInt(asymmetricEncryptedMD5.length);
                output.write(asymmetricEncryptedMD5);
            }
            //3.2 write asymmetric encrypted randome iv.length and iv data
            output.writeInt(asymmetricEncryptedIV.length);
            output.write(asymmetricEncryptedIV);
            //3.3 write asymmetric encrypted randome session key
            if (asymmetricEncryptedSymmetricKeyAlgorithm != null) {
                output.writeInt(asymmetricEncryptedSymmetricKeyAlgorithm.length);
                output.write(asymmetricEncryptedSymmetricKeyAlgorithm);
            }
            if (asymmetricEncryptedSessionKey != null) {
                output.writeInt(asymmetricEncryptedSessionKey.length);
                output.write(asymmetricEncryptedSessionKey);
            }
            //3.4 encrypte
            int byteRead;
            final int BUFF_SIZE = 102400;
            final byte[] buffer = new byte[BUFF_SIZE];
            while ((byteRead = plainDataInputStream.read(buffer)) != -1) {
                cos.write(buffer, 0, byteRead);
            }
        }
    }

    /**
     * encrypt data in RAM
     *
     * @param asymmetricKey symmetric encryption will be used if null
     * @param symmetricKey encrypt with random session key if null
     * @param plainData
     * @param digitalSignatureKey - to sign the digital signature if not null
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static byte[] encrypt(Key asymmetricKey, SecretKey symmetricKey, byte[] plainData, Key digitalSignatureKey) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        //0. metadata        
        String metaInfo = LocalDateTime.now() + ", " + System.getProperty("hostName");
        byte[] metadata = metaInfo.getBytes(StandardCharsets.UTF_8);
        if (digitalSignatureKey != null) {
            metadata = encrypt(digitalSignatureKey, metadata);
        }
        //1. create cipher wtiht this key
        boolean randomSymmetricKey = symmetricKey == null;
        if (randomSymmetricKey) {
            symmetricKey = generateAESKey();
        }
        byte[] iv = generateInitializationVector(IV_LENGTH_BYTE);
        Cipher cipher = buildCypher_GCM(true, symmetricKey, iv);

        //2. asymmetric encrypt file header
        byte[] asymmetricEncryptedMD5 = null, asymmetricEncryptedIV = iv, asymmetricEncryptedSessionKey = null, asymmetricEncryptedSymmetricKeyAlgorithm = null;
        if (asymmetricKey != null) {
            byte[] md5 = md5(plainData);
            asymmetricEncryptedMD5 = encrypt(asymmetricKey, md5);
            asymmetricEncryptedIV = encrypt(asymmetricKey, iv);
            if (randomSymmetricKey) {
                asymmetricEncryptedSymmetricKeyAlgorithm = symmetricKey.getAlgorithm().getBytes(StandardCharsets.ISO_8859_1);
                asymmetricEncryptedSymmetricKeyAlgorithm = encrypt(asymmetricKey, asymmetricEncryptedSymmetricKeyAlgorithm);
                asymmetricEncryptedSessionKey = encrypt(asymmetricKey, symmetricKey.getEncoded());
            }
        }

        //3. streaming
        byte[] ret;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bos);) {
            //3.0 write metadata
            output.writeInt(metadata.length);
            output.write(metadata);
            //3.1 write asymmetric encrypted md5.length and md5 data
            if (asymmetricEncryptedMD5 != null) {
                output.writeInt(asymmetricEncryptedMD5.length);
                output.write(asymmetricEncryptedMD5);
            }
            //3.2 write asymmetric encrypted randome iv.length and iv data
            output.writeInt(asymmetricEncryptedIV.length);
            output.write(asymmetricEncryptedIV);
            //3.3 write asymmetric encrypted randome session key
            if (asymmetricEncryptedSymmetricKeyAlgorithm != null) {
                output.writeInt(asymmetricEncryptedSymmetricKeyAlgorithm.length);
                output.write(asymmetricEncryptedSymmetricKeyAlgorithm);
            }
            if (asymmetricEncryptedSessionKey != null) {
                output.writeInt(asymmetricEncryptedSessionKey.length);
                output.write(asymmetricEncryptedSessionKey);
            }
            //3.4 encrypte
            byte[] encryptedData = cipher.doFinal(plainData);
            output.write(encryptedData);
            ret = bos.toByteArray();
        }
        return ret;
    }

    public static class EncryptionMeta {

        private String info;
        private String md5;

        public String getInfo() {
            return info;
        }

        public void setInfo(String info) {
            this.info = info;
        }

        public String getMd5() {
            return md5;
        }

        public void setMd5(String md5) {
            this.md5 = md5;
        }

        @Override
        public String toString() {
            return info + " (md5: " + md5 + ")";
        }
    }

    /**
     * decrypt large file
     *
     * @param asymmetricKey symmetric decryption if null
     * @param symmetricKey decrypt with asymmetric encrypted random session key
     * if null
     * @param encryptedFileName
     * @param plainDataFileName
     * @param digitalSignatureKey - to verify the digital signature if not null
     * @param meta
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws javax.crypto.IllegalBlockSizeException
     * @throws javax.crypto.BadPaddingException
     */
    public static void decrypt(Key asymmetricKey, SecretKey symmetricKey, String encryptedFileName, String plainDataFileName, Key digitalSignatureKey, @Nullable EncryptionMeta meta) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(encryptedFileName));) {
            //0. metadata
            byte[] metadata = new byte[dis.readInt()];
            dis.readFully(metadata);
            if (digitalSignatureKey != null) {
                try {
                    metadata = decrypt(digitalSignatureKey, metadata);
                    if (meta != null) {
                        meta.setInfo(new String(metadata, StandardCharsets.UTF_8));
                    }
                } catch (Throwable ex) {
                    throw new ProviderException("Digital Signature verification failed", ex);
                }
//                String metaInfo = new String(metadata, StandardCharsets.UTF_8);
//                System.out.println(metaInfo);
            }
            //1. read md5
            byte[] decryptedMD5 = null;
            if (asymmetricKey != null) {
                byte[] encryptedMD5 = new byte[dis.readInt()];
                dis.readFully(encryptedMD5);
                decryptedMD5 = decrypt(asymmetricKey, encryptedMD5);
            }
            //2. read iv
            byte[] asymmetricEncryptedIV = new byte[dis.readInt()];
            dis.readFully(asymmetricEncryptedIV);
            byte[] iv;
            if (asymmetricKey == null) {
                iv = asymmetricEncryptedIV;
            } else {
                //3. read session key
                iv = decrypt(asymmetricKey, asymmetricEncryptedIV);
                if (symmetricKey == null) {//decrypt with asymmetric encrypted random session
                    byte[] asymmetricEncryptedSymmetricKeyAlgorithm = new byte[dis.readInt()];
                    dis.readFully(asymmetricEncryptedSymmetricKeyAlgorithm);
                    byte[] symmetricKeyAlgorithm = decrypt(asymmetricKey, asymmetricEncryptedSymmetricKeyAlgorithm);
                    String keyAlgorithm = new String(symmetricKeyAlgorithm, StandardCharsets.ISO_8859_1);
                    byte[] asymmetricEncryptedSessionKey = new byte[dis.readInt()];
                    dis.readFully(asymmetricEncryptedSessionKey);
                    byte[] sessionKey = decrypt(asymmetricKey, asymmetricEncryptedSessionKey);
                    symmetricKey = loadSymmetricKey(sessionKey, keyAlgorithm);
                }
            }
            Cipher cipher = buildCypher_GCM(false, symmetricKey, iv);
            //4. decrypt streaming
            try (CipherInputStream cis = new CipherInputStream(dis, cipher); FileOutputStream fos = new FileOutputStream(plainDataFileName);) {
                final int BUFF_SIZE = 102400;
                final byte[] buffer = new byte[BUFF_SIZE];
                int byteRead;
                // Read through the file, decrypting each byte.
                while ((byteRead = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteRead);
                }
            }
            byte[] md5 = md5(new File(plainDataFileName));
            if (meta != null) {
                meta.setMd5(md5ToString(md5));
            }
            if (decryptedMD5 != null) {
                boolean isMatch = md5 != null && decryptedMD5.length == md5.length;
                if (isMatch) {
                    for (int i = 0; i < md5.length; i++) {
                        if (md5[i] != decryptedMD5[i]) {
                            isMatch = false;
                            break;
                        }
                    }
                }
                if (!isMatch) {
                    throw new IOException("MD5 verification failed");
                }
            }
        }
    }

    /**
     * decrypt data in RAM
     *
     * @param asymmetricKey symmetric decryption if null
     * @param symmetricKey decrypt with asymmetric encrypted random session key
     * if null
     * @param encryptedData
     * @param digitalSignatureKey - to verify the digital signature if not null
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws javax.crypto.IllegalBlockSizeException
     * @throws javax.crypto.BadPaddingException
     */
    public static byte[] decrypt(Key asymmetricKey, SecretKey symmetricKey, byte[] encryptedData, Key digitalSignatureKey, @Nullable EncryptionMeta meta) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        byte[] ret;
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(encryptedData));) {
            //0. metadata
            byte[] metadata = new byte[dis.readInt()];
            dis.readFully(metadata);
            if (digitalSignatureKey != null) {
                try {
                    metadata = decrypt(digitalSignatureKey, metadata);
                    if (meta != null) {
                        meta.setInfo(new String(metadata, StandardCharsets.UTF_8));
                    }
                } catch (Throwable ex) {
                    throw new ProviderException("Digital Signature verification failed", ex);
                }
//                String metaInfo = new String(metadata, StandardCharsets.UTF_8);
//                System.out.println(metaInfo);
            }
            //1. read md5
            byte[] decryptedMD5 = null;
            if (asymmetricKey != null) {
                byte[] encryptedMD5 = new byte[dis.readInt()];
                dis.readFully(encryptedMD5);
                decryptedMD5 = decrypt(asymmetricKey, encryptedMD5);
            }
            //2. read iv
            byte[] asymmetricEncryptedIV = new byte[dis.readInt()];
            dis.readFully(asymmetricEncryptedIV);
            byte[] iv;
            if (asymmetricKey == null) {
                iv = asymmetricEncryptedIV;
            } else {
                //3. read session key
                iv = decrypt(asymmetricKey, asymmetricEncryptedIV);
                if (symmetricKey == null) {//decrypt with asymmetric encrypted random session
                    byte[] asymmetricEncryptedSymmetricKeyAlgorithm = new byte[dis.readInt()];
                    dis.readFully(asymmetricEncryptedSymmetricKeyAlgorithm);
                    byte[] symmetricKeyAlgorithm = decrypt(asymmetricKey, asymmetricEncryptedSymmetricKeyAlgorithm);
                    String keyAlgorithm = new String(symmetricKeyAlgorithm, StandardCharsets.ISO_8859_1);
                    byte[] asymmetricEncryptedSessionKey = new byte[dis.readInt()];
                    dis.readFully(asymmetricEncryptedSessionKey);
                    byte[] sessionKey = decrypt(asymmetricKey, asymmetricEncryptedSessionKey);
                    symmetricKey = loadSymmetricKey(sessionKey, keyAlgorithm);
                }
            }
            Cipher cipher = buildCypher_GCM(false, symmetricKey, iv);
            //4. decrypt streaming
            try (CipherInputStream cis = new CipherInputStream(dis, cipher); ByteArrayOutputStream bos = new ByteArrayOutputStream();) {
                final int BUFF_SIZE = 102400;
                final byte[] buffer = new byte[BUFF_SIZE];
                int byteRead;
                // Read through the file, decrypting each byte.
                while ((byteRead = cis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteRead);
                }
                ret = bos.toByteArray();
            }

            byte[] md5 = md5(ret);
            if (meta != null) {
                meta.setMd5(md5ToString(md5));
            }
            if (decryptedMD5 != null) {

                boolean isMatch = md5 != null && decryptedMD5.length == md5.length;
                if (isMatch) {
                    for (int i = 0; i < md5.length; i++) {
                        if (md5[i] != decryptedMD5[i]) {
                            isMatch = false;
                            break;
                        }
                    }
                }
                if (!isMatch) {
                    throw new IOException("MD5 verification failed");
                }
            }
        }
        return ret;
    }

    public static byte[] decrypt(SecretKey symmetricKey, byte[] encryptedLibraryBytes) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(encryptedLibraryBytes));) {
            //2. read iv
            byte[] iv = new byte[IV_LENGTH_BYTE];
            dis.readFully(iv);
            Cipher cipher = buildCypher_GCM(false, symmetricKey, iv);
            //4. decrypt streaming
            try (CipherInputStream cis = new CipherInputStream(dis, cipher); ByteArrayOutputStream bos = new ByteArrayOutputStream();) {
                final int BUFF_SIZE = 102400;
                final byte[] buffer = new byte[BUFF_SIZE];
                int byteRead;
                // Read through the file, decrypting each byte.
                while ((byteRead = cis.read(buffer)) != -1) {
                    bos.write(buffer, 0, byteRead);
                }
                return bos.toByteArray();
            }
        }
    }

    @Deprecated
    public static void encrypt(SecretKey symmetricKey, String plainDataFileName, String encryptedFileName) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        //1. create cipher wtiht this key
        byte[] iv = generateInitializationVector(IV_LENGTH_BYTE);
        Cipher cipher = buildCypher_GCM(true, symmetricKey, iv);

        //3. streaming
        try (InputStream plainDataInputStream = new FileInputStream(plainDataFileName); FileOutputStream fos = new FileOutputStream(encryptedFileName); DataOutputStream output = new DataOutputStream(fos); CipherOutputStream cos = new CipherOutputStream(output, cipher);) {
            output.write(iv);
            //3.4 encrypte
            int byteRead;
            final int BUFF_SIZE = 102400;
            final byte[] buffer = new byte[BUFF_SIZE];
            while ((byteRead = plainDataInputStream.read(buffer)) != -1) {
                cos.write(buffer, 0, byteRead);
            }
        }
    }
}
