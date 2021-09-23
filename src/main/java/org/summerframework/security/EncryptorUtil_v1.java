package org.summerframework.security;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encrypts and decrypts a file using CipherStreams and a 256-bit
 * Rijndael key. The key is then encrypted using a 1024-bit RSA key, which is
 * password-encrypted.
 *
 *
 * 1. UnlimitedJCEPolicyJDK7.zip - you must download the unrestricted policy
 * files for the Sun JCE. The policy files can be found at the same place as the
 * JDK download -
 * http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html
 *
 * 2. bcprov-jdk15on-152.jar and bcprov-ext-jdk15on-152.jar -
 * https://www.bouncycastle.org/documentation.html
 *
 * 3. unzip and copy to copy to jdk\jre\lib\security
 *
 * http://docs.oracle.com/javase/6/docs/technotes/guides/security/SunProviders.html
 */
@Deprecated
public class EncryptorUtil_v1 {

    protected static final Logger log = LogManager.getLogger(EncryptorUtil_v1.class);
    private static final String PBE_Algorithm = "PBEWithMD5AndDES";
    private static final String KeyPairGenerator_Algorithm = "RSA";
    private static final String RSA_Cipher_Algorithm = "RSA/ECB/PKCS1Padding";
    private static final String Password_Based_Encryption_Algorithm = "PBEWithSHA1AndDESede";
    private static final String Symmetric_Key_Algorithm = "Rijndael";//"AES";
    private static final String Symmetric_Cipher_Algorithm = "AES/CBC/PKCS5Padding";
    private static final int SALT_SIZE = 16;
    public static final String ENCODING = StandardCharsets.UTF_8.name();//ISO-8859-1";
    public static final DateTimeFormatter CODE4TODAY_VSDF = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
    public static final DateTimeFormatter CODE4CURRENT_HOUR_VSDF = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH").withZone(ZoneId.of("UTC"));
    public static final DateTimeFormatter CODE4CURRENT_MINUTE_VSDF = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").withZone(ZoneId.of("UTC"));

//    public static final SimpleDateFormat CODE4TODAY_VSDF = new SimpleDateFormat("yyyy-MM-dd");
//    public static final SimpleDateFormat CODE4CURRENT_HOUR_VSDF = new SimpleDateFormat("yyyy-MM-dd_HH");
//    public static final SimpleDateFormat CODE4CURRENT_MINUTE_VSDF = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Number of times the password will be hashed with MD5 when transforming it
     * into a TripleDES key.
     */
    private static final int ITERATIONS = 1024;
    //private static final Console console = System.console();

    /**
     * Creates a 1024 bit RSA key and stores it to the file system as two files.
     *
     * @param publicKeyFile
     * @param privateKeyFile
     * @param pwd
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static void generateKeyPair(File publicKeyFile, File privateKeyFile, char[] pwd) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        // Create an RSA key    
        log.debug("Generating an RSA keypair...");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyPairGenerator_Algorithm);
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.genKeyPair();

        if (log.isDebugEnabled()) {
            log.debug("write the public key out to " + publicKeyFile.getAbsolutePath());
        }
        // Get the encoded form of the public key so we can use it again in the future. This is X.509 by default.
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        // Write the encoded public key out to the filesystem
        FileOutputStream fos = new FileOutputStream(publicKeyFile);
        fos.write(publicKeyBytes);
        fos.close();

        if (log.isDebugEnabled()) {
            log.debug("write the password encrypted private key out to " + privateKeyFile.getAbsolutePath());
        }
        // Now we need to do the same thing with the private key, but we need to password encrypt it as well.
        // Get the encoded form. This is PKCS#8 by default.
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        // Here we actually encrypt the private key
        byte[] encryptedPrivateKeyBytes = passwordBasedEncrypt(pwd, privateKeyBytes);
        // Write the encrypted private key out to the filesystem
        fos = new FileOutputStream(privateKeyFile);
        fos.write(encryptedPrivateKeyBytes);
        fos.close();
        log.debug("done");
    }

    

    public static PrivateKey getPrivateKey(String filename) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Files.readAllBytes(Paths.get(filename));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    /**
     *
     * @param pkcs8File
     * @param privateKeyPwd
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static PrivateKey loadPrivateKey(File pkcs8File, char[] privateKeyPwd) throws FileNotFoundException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        // Load the private key bytes
        byte[] keyBytes = Files.readAllBytes(Paths.get(pkcs8File.getAbsolutePath()));

        keyBytes = passwordBasedDecrypt(privateKeyPwd, keyBytes);

        // Turn the encoded key into a real RSA private key.
        // Private keys are encoded in PKCS#8.
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KeyPairGenerator_Algorithm);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }

    @Deprecated
    public static PrivateKey loadPrivateKey(File pemFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        if (log.isDebugEnabled()) {
            log.debug("loading private key " + pemFile.getAbsolutePath());
        }

        // Load the private key bytes
        byte[] keyBytes = Files.readAllBytes(pemFile.toPath());
        String privateKeyPEM = new String(keyBytes);
        int begin = privateKeyPEM.indexOf("-----");
        privateKeyPEM = privateKeyPEM.substring(begin);
        privateKeyPEM = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                //.replaceAll(System.lineSeparator(), "");
                .replaceAll("\\s+", "");
        //byte[] encoded = java.util.Base64.getDecoder().decode(privateKeyContent);
        //byte[] header = Hex.decode("30 81bf 020100 301006072a8648ce3d020106052b81040022 0481a7");
        keyBytes = Base64.getDecoder().decode(privateKeyPEM);

        // Turn the encoded key into a real RSA private key.
        // Private keys are encoded in PKCS#8.
        KeyFactory keyFactory = KeyFactory.getInstance(KeyPairGenerator_Algorithm);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }

    

    /**
     *
     * @param publicKey
     * @param plainData
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    @Deprecated
    public static byte[] encryptData_Asymmetric(Key publicKey, byte[] plainData) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
        log.debug("");
        Cipher rsaCipher = Cipher.getInstance(RSA_Cipher_Algorithm);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        // Encrypt the Rijndael key with the RSA cipher
        // and write it to the beginning of the file.
        byte[] encryptedBytes = rsaCipher.doFinal(plainData);
        return encryptedBytes;
    }

    /**
     *
     * @param privateKey
     * @param encryptedBytes
     * @return
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    @Deprecated
    public static byte[] decryptData_Asymmetric(Key privateKey, byte[] encryptedBytes) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        log.debug("");
        // Create a cipher using that key to initialize it
        Cipher rsaCipher = Cipher.getInstance(RSA_Cipher_Algorithm);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = rsaCipher.doFinal(encryptedBytes);
        return decryptedBytes;
    }

    /**
     * Utility method to encrypt a byte array with a given password. Salt will
     * be the first 8 bytes of the byte array returned.
     *
     * @param password
     * @param plaintext
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws IOException
     */
    public static byte[] passwordBasedEncrypt(char[] password, byte[] plaintext) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, IOException {
        log.debug("Create the salt");
        byte[] salt = new byte[8];
        Random random = new Random();
        random.nextBytes(salt);

        log.debug("Create a Password_Based_Encryption (PBE) key and cipher");
        PBEKeySpec keySpec = new PBEKeySpec(password);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(Password_Based_Encryption_Algorithm);
        SecretKey key = keyFactory.generateSecret(keySpec);
        PBEParameterSpec paramSpec = new PBEParameterSpec(salt, ITERATIONS);
        Cipher cipher = Cipher.getInstance(Password_Based_Encryption_Algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);

        log.debug("Encrypt the array");
        byte[] ciphertext = cipher.doFinal(plaintext);

        log.debug("Write out the salt, then the ciphertext and return it");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(salt);
        baos.write(ciphertext);
        return baos.toByteArray();
    }

    /**
     * Utility method to decrypt a byte array with a given password. Salt will
     * be the first 8 bytes in the array passed in.
     *
     * @param password
     * @param ciphertext
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static byte[] passwordBasedDecrypt(char[] password, byte[] ciphertext) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        if (password == null || password.length < 1) {
            return ciphertext;
        }
        log.debug("Read in the salt");
        byte[] salt = new byte[8];
        ByteArrayInputStream bais = new ByteArrayInputStream(ciphertext);
        bais.read(salt, 0, 8);

        log.debug("The remaining bytes are the actual ciphertext");
        byte[] remainingCiphertext = new byte[ciphertext.length - 8];
        bais.read(remainingCiphertext, 0, ciphertext.length - 8);

        log.debug("Create a PBE cipher to decrypt the byte array");
        PBEKeySpec keySpec = new PBEKeySpec(password);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(Password_Based_Encryption_Algorithm);
        SecretKey key = keyFactory.generateSecret(keySpec);
        PBEParameterSpec paramSpec = new PBEParameterSpec(salt, ITERATIONS);
        Cipher cipher = Cipher.getInstance(Password_Based_Encryption_Algorithm);

        log.debug("Perform the actual decryption");
        cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
        return cipher.doFinal(remainingCiphertext);
    }

    /**
     *
     * @param plainDataFile
     * @param plainData
     * @param signDigitalSignatureKey
     * @param sessionkeyEncrypttionKey
     * @param encryptedFile
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     * @throws FileNotFoundException
     * @throws CertificateException
     */
    @Deprecated
    public static void fileEncrypt(File plainDataFile, byte[] plainData, Key signDigitalSignatureKey, Key sessionkeyEncrypttionKey, File encryptedFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, FileNotFoundException, CertificateException {
        if (log.isDebugEnabled()) {
            log.debug("Open up " + encryptedFile.getAbsolutePath() + " for the output of the encryption");
        }
        DataOutputStream output = new DataOutputStream(new FileOutputStream(encryptedFile));

        log.debug("Calculating MD5 for plain data file and write it to the beginning of the file");
        byte[] md5 = plainDataFile == null ? md5(plainData, "MD5") : md5(plainDataFile);
        byte[] encryptedMD5 = encryptData_Asymmetric(signDigitalSignatureKey, md5);
        output.writeInt(encryptedMD5.length);
        output.write(encryptedMD5);

        log.debug("Generating a new 256 bit Rijndael session key to encrypt the file itself");
        KeyGenerator symmetricKeyGenerator = KeyGenerator.getInstance(Symmetric_Key_Algorithm);
        symmetricKeyGenerator.init(256);
        SecretKey symmetricSessionKey = symmetricKeyGenerator.generateKey();

        log.debug("Create a cipher using that key to initialize it");
        byte[] encodedSessionKeyBytes = encryptData_Asymmetric(sessionkeyEncrypttionKey, symmetricSessionKey.getEncoded());
        output.writeInt(encodedSessionKeyBytes.length);
        output.write(encodedSessionKeyBytes);

        log.debug("Generating an Initialization Vector for the symmetric cipher in CBC mode");
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[SALT_SIZE];
        random.nextBytes(iv);
        log.debug("Write the IV out to the file");
        output.write(iv);
        IvParameterSpec spec = new IvParameterSpec(iv);
        log.debug("Create the cipher for encrypting the file itself.");
        Cipher symmetricCipher = Cipher.getInstance(Symmetric_Cipher_Algorithm);
        symmetricCipher.init(Cipher.ENCRYPT_MODE, symmetricSessionKey, spec);

        log.debug("Encrypting the file..." + symmetricCipher.getAlgorithm());
        CipherOutputStream cos = new CipherOutputStream(output, symmetricCipher);

        InputStream plainDataInputStream;
        long plianDatalengthInByte;
        if (plainDataFile != null) {
            // Get the number of bytes in the file
            plianDatalengthInByte = plainDataFile.length();
            plainDataInputStream = new FileInputStream(plainDataFile);
        } else {
            plainDataInputStream = new ByteArrayInputStream(plainData);
            plianDatalengthInByte = plainData.length;
        }

        int progress = 0;
        long totalProcessed = 0;
        final int BUFF_SIZE = 100000;
        final byte[] buffer = new byte[BUFF_SIZE];
        int byteRead;
        while ((byteRead = plainDataInputStream.read(buffer)) != -1) {
            cos.write(buffer, 0, byteRead);
            totalProcessed += byteRead;
            int temp = (int) (100 * totalProcessed / plianDatalengthInByte);
            if (progress != temp) {
                progress = temp;
                if (log.isInfoEnabled()) {
                    log.info(progress + "%(" + totalProcessed + "/" + plianDatalengthInByte + ")");
                }
            }
        }
        plainDataInputStream.close();
        cos.close();
    }

    /**
     *
     * @param encryptedFile
     * @param expectedMD5OfEncryptedFile
     * @param verifyDigitalSignatureKey
     * @param sessionkeyDecrypttionKey
     * @param decryptedFile
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     */
    @Deprecated
    public static byte[] fileDecrypt(File encryptedFile, String expectedMD5OfEncryptedFile, Key verifyDigitalSignatureKey, Key sessionkeyDecrypttionKey, File decryptedFile) throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        if (expectedMD5OfEncryptedFile != null) {
            log.debug("Calculating MD5 of encrypted file...");
            byte[] md5 = md5(encryptedFile);
            String caculatedMD5 = digetsToHexString(md5);
            if (log.isDebugEnabled()) {
                log.debug("MD5(expected) =" + expectedMD5OfEncryptedFile);
                log.debug("MD5(caculated)=" + caculatedMD5);
            }
            boolean isSameMD5 = expectedMD5OfEncryptedFile.equals(caculatedMD5);
            if (!isSameMD5) {
                throw new IOException("MD5 not match, \n\t expcted=" + caculatedMD5 + "\n\t caculatedMD5=" + caculatedMD5);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Decrypting " + encryptedFile.getAbsolutePath());
            log.debug("Read in the encrypted bytes of the session key");
        }
        DataInputStream dis = new DataInputStream(new FileInputStream(encryptedFile));
        byte[] encryptedMD5 = new byte[dis.readInt()];
        dis.readFully(encryptedMD5);
        byte[] md5Bytes = decryptData_Asymmetric(verifyDigitalSignatureKey, encryptedMD5);

        byte[] encryptedSessionKeyBytes = new byte[dis.readInt()];
        dis.readFully(encryptedSessionKeyBytes);

        log.debug("Decrypt the session key bytes");
        byte[] symmetricSessionKeyBytes = decryptData_Asymmetric(sessionkeyDecrypttionKey, encryptedSessionKeyBytes);
        log.debug("Transform the key bytes into an actual key.");
        SecretKey symmetricSessionKey = new SecretKeySpec(symmetricSessionKeyBytes, Symmetric_Key_Algorithm);

        log.debug("Read in the Initialization Vector from the file.");
        byte[] iv = new byte[SALT_SIZE];
        dis.read(iv);
        IvParameterSpec spec = new IvParameterSpec(iv);

        //Cipher cipher = Cipher.getInstance("Rijndael/CBC/PKCS5Padding");
        Cipher cipher = Cipher.getInstance(Symmetric_Cipher_Algorithm);
        cipher.init(Cipher.DECRYPT_MODE, symmetricSessionKey, spec);
        CipherInputStream cis = new CipherInputStream(dis, cipher);

        log.debug("Decrypting the file...");
        byte[] ret = null;
        // Get the number of bytes in the file
        long fileLlengthInByte = encryptedFile.length();
        //String decryptedFilename = encryptedFilename + DECRYPTED_FILENAME_SUFFIX;
        FileOutputStream fos = null;
        ByteArrayOutputStream bos = null;
        if (decryptedFile != null) {
            fos = new FileOutputStream(decryptedFile);
        } else {
            bos = new ByteArrayOutputStream();
        }
        int progress = 0;
        long totalProcessed = 0;
        final int BUFF_SIZE = 500;
        final byte[] buffer = new byte[BUFF_SIZE];
        int byteRead;
        // Read through the file, decrypting each byte.
        while ((byteRead = cis.read(buffer)) != -1) {
            if (fos != null) {
                fos.write(buffer, 0, byteRead);
            }
            if (bos != null) {
                bos.write(buffer, 0, byteRead);
            }
            totalProcessed += byteRead;
            int temp = (int) (100 * totalProcessed / fileLlengthInByte);
            if (progress != temp) {
                progress = temp;
                if (log.isInfoEnabled()) {
                    log.info(progress + "%(" + totalProcessed + "/" + fileLlengthInByte + ")");
                }
            }
        }
        if (bos != null) {
            ret = bos.toByteArray();
        }
        cis.close();
        if (fos != null) {
            fos.close();
        }
        if (bos != null) {
            bos.close();
        }

        log.debug("Calculating MD5 of plain data file...");
        byte[] md5 = decryptedFile != null ? md5(decryptedFile) : md5(ret, "MD5");
        String caculatedDecryptedD5 = digetsToHexString(md5);
        String md5OfPlainDatafile = digetsToHexString(md5Bytes);
        if (log.isDebugEnabled()) {
            log.debug("MD5(delivered)=" + md5OfPlainDatafile);
            log.debug("MD5(caculated)=" + caculatedDecryptedD5);
        }
        boolean isSameMD5 = md5OfPlainDatafile.equals(caculatedDecryptedD5);
        if (!isSameMD5) {
            throw new IOException("Caculated decrypted MD5 value does not equal delivered plain data MD5");
        }
        return ret;
    }

    /**
     *
     * @param filename
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    @Deprecated
    public static byte[] md5(File filename) throws NoSuchAlgorithmException, IOException {
        if (log.isDebugEnabled()) {
            log.debug(filename.getAbsolutePath());
        }
        InputStream fis = new FileInputStream(filename);

        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return complete.digest();
    }

    /**
     *
     * @param text
     * @return
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    @Deprecated
    public static byte[] md5(String text) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return md5(text.getBytes(ENCODING), "MD5");
    }

    /**
     *
     * @param data
     * @param algorithm MD5 (32-bit), SHA, SHA-256 (64-bit), SHA-384, SHA-512
     * (128-bit)
     * @return
     * @throws NoSuchAlgorithmException
     */
    @Deprecated
    public static byte[] md5(byte[] data, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        //md.reset();
        md.update(data);
        byte[] digest = md.digest();
        return digest;
    }

    public static byte[] ssha(byte[] data) throws NoSuchAlgorithmException {
        byte[] salt = randSalt();
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.reset();
        md.update(data);
        md.update(salt);
        byte[] digest = md.digest();
        return concatenate(digest, randSalt());
    }

    public static byte[] randSalt() {
        int saltLen = 4; //8
        byte[] b = new byte[saltLen];
        for (int i = 0; i < saltLen; i++) {
            byte bt = (byte) (((Math.random()) * 256) - 128);
            //System.out.println(bt);
            b[i] = bt;
        }
        return b;
    }

    private static byte[] concatenate(byte[] l, byte[] r) {
        byte[] b = new byte[l.length + r.length];
        System.arraycopy(l, 0, b, 0, l.length);
        System.arraycopy(r, 0, b, l.length, r.length);
        return b;
    }

    public static String encryptPassword(String plainPassword) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte[] digest = md5(plainPassword.getBytes(StandardCharsets.UTF_8), "SHA-512");
        String encryptedPassword = digetsToHexString(digest);
        return encryptedPassword;
    }

    public static String encryptLDAPPasswordSSHA(String plainPassword) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte[] digest = ssha(plainPassword.getBytes(StandardCharsets.UTF_8));
        String encryptedPassword = digetsToBase64(digest);
        return "{SSHA}" + encryptedPassword;
    }

    public static String encryptLDAPPasswordSHA(String plainPassword) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte[] digest = md5(plainPassword.getBytes(StandardCharsets.UTF_8), "SHA");
        String encryptedPassword = digetsToBase64(digest);
        return "{SHA}" + encryptedPassword;
    }

    public static void main(String[] args) throws Exception {
//        String plainPassword = "meiyou";
//        byte[] digest = md5(plainPassword.getBytes("UTF-8"), "SHA");
//        String encryptedPassword = digetsToBase64(digest);
//        System.out.println(encryptedPassword);
//
//        digest = md5(plainPassword.getBytes("UTF-8"), "MD5");
//        encryptedPassword = digetsToBase64(digest);
//        System.out.println(encryptedPassword);

//        System.out.println(CODE4TODAY_VSDF.format(new Date()));
//        System.out.println(CODE4CURRENT_HOUR_VSDF.format(new Date()));
//        System.out.println(CODE4CURRENT_MINUTE_VSDF.format(new Date()));
//        System.out.println(CODE4TODAY_VSDF.format(LocalDate.now()));
//        System.out.println(CODE4CURRENT_HOUR_VSDF.format(LocalDateTime.now()));
//        System.out.println(CODE4CURRENT_MINUTE_VSDF.format(LocalDateTime.now()));
//        System.out.println(getTempCodeForToday());
//        System.out.println(getTempCodeForCurrentHour());
//        System.out.println(getTempCodeForCurrentMinute());
        System.out.println(base64Decode("eyJhbGciOiJIUzI1NiJ9.eyJpYXQiOjE1MTcwNjQ4NTUsImlzcyI6Ik9MRzEyMzQ1Njc4OTAiLCJqdGkiOiIyM2Q4MjMyMWQ0ODY0YTAwYjI1ODlkZDE5MGI2OTA4OSIsInN1YiI6InNidSIsImFhIjpbImExIiwiYTIiXSwiZXhwIjoxNTE3MDY0ODU2fQ.yKMQAEuLo-hNcB8xHxuGxNYoIhlonOp7aN7CeUhbUcI"));
    }

    public static String digetsToHexString(byte[] digest) {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < md5Digest.length; i++) {
//            sb.append(Integer.toString((md5Digest[i] & 0xff) + 0x100, 16).substring(1));
//        }
//        return sb.toString();
        BigInteger bigInt = new BigInteger(1, digest);
        return bigInt.toString(16);
    }

    public static String digetsToBase64(byte[] digest) {
        return new String(Base64.getEncoder().encode(digest));
    }

    /**
     *
     * @param plainText
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String base64Encode(String plainText) throws UnsupportedEncodingException {
        // Encode the string into bytes using utf-8
        byte[] utf8 = plainText.getBytes(StandardCharsets.UTF_8);
        // Encode bytes to base64 to get a string
        byte[] b64 = Base64.getEncoder().encode(utf8);
        return new String(b64);
    }

    /**
     *
     * @param base64Text
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String base64Decode(String base64Text) throws UnsupportedEncodingException {
        // Decode base64 to get bytes
        //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
        byte[] dec = Base64.getDecoder().decode(base64Text);
        // Decode using utf-8
        return new String(dec, StandardCharsets.UTF_8);
    }

    private static final Map<String, EncryptorUtil_v1> pool = new HashMap();

    /**
     * Constructor used to create this object. Responsible for setting and
     * initializing this object's encrypter and decrypter Chipher instances
     * given a Secret Key and algorithm.
     *
     * @param secretKey
     * @return EncryptorUtil_v1
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     */
    public static EncryptorUtil_v1 getInstance(SecretKey secretKey, String cipherAlgorithm) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        EncryptorUtil_v1 ret = pool.get(secretKey.toString() + cipherAlgorithm);
        if (ret == null) {
            ret = new EncryptorUtil_v1(secretKey, cipherAlgorithm);
            pool.put(secretKey.toString(), ret);
        }
        return ret;
    }

    /**
     * Constructor used to create this object. Responsible for setting and
     * initializing this object's encrypter and decrypter Chipher instances
     * given a Pass Phrase and algorithm.
     *
     * @param password
     * @return EncryptorUtil_v1
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     */
    public static EncryptorUtil_v1 getInstance(String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        EncryptorUtil_v1 ret = pool.get(password);
        if (ret == null) {
            ret = new EncryptorUtil_v1(password);
            pool.put(password, ret);
        }
        return ret;
    }

    private final Cipher ecipher;

    private final Cipher dcipher;

    /**
     * Constructor used to create this object. Responsible for setting and
     * initializing this object's encrypter and decrypter Chipher instances
     * given a Pass Phrase and algorithm.
     *
     * @param password
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     */
    private EncryptorUtil_v1(String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        // 8-bytes Salt
        byte[] salt = {(byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32, (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03};

        // Iteration count
        int iterationCount = 19;

        KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount);
        SecretKey key = SecretKeyFactory.getInstance(PBE_Algorithm).generateSecret(keySpec);

        ecipher = Cipher.getInstance(key.getAlgorithm());
        dcipher = Cipher.getInstance(key.getAlgorithm());

        // Prepare the parameters to the cipthers
        AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

        ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
        dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
    }

    /**
     * Constructor used to create this object. Responsible for setting and
     * initializing this object's encrypter and decrypter Chipher instances
     * given a Secret Key and algorithm.
     *
     * @param secretKey
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     */
    private EncryptorUtil_v1(SecretKey secretKey, String _cipherAlgorithm) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        String cipherAlgorithm = _cipherAlgorithm == null ? secretKey.getAlgorithm() : _cipherAlgorithm;

        ecipher = Cipher.getInstance(cipherAlgorithm);
        dcipher = Cipher.getInstance(cipherAlgorithm);
        ecipher.init(Cipher.ENCRYPT_MODE, secretKey);
        dcipher.init(Cipher.DECRYPT_MODE, secretKey);
    }

    public String getCipherAlgorithm() {
        return ecipher.getAlgorithm();
    }

    /**
     * Takes a single String as an argument and returns an Encrypted version of
     * that String.
     *
     * @param plainText
     * @return encrypted string in Base64 format
     * @throws UnsupportedEncodingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public String symmetricEncrypt(String plainText) throws UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
        // Encode the string into bytes using utf-8
        byte[] utf8 = plainText.getBytes(StandardCharsets.UTF_8);
        // Encrypt
        byte[] enc = ecipher.doFinal(utf8);
        // Encode bytes to base64 to get a string
        byte[] b64 = Base64.getEncoder().encode(enc);
        return new String(b64);
    }

    /**
     * Takes a encrypted String as an argument, decrypts and returns the
     * decrypted String.
     *
     * @param str Encrypted String to be decrypted
     * @return <code>String</code> Decrypted version of the provided String
     * @throws javax.crypto.BadPaddingException
     * @throws javax.crypto.IllegalBlockSizeException
     * @throws java.io.UnsupportedEncodingException
     */
    public String symmetricDecrypt(String str) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException {
        // Decode base64 to get bytes
        //byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);
        byte[] dec = Base64.getDecoder().decode(str);
        // Decrypt
        byte[] utf8 = dcipher.doFinal(dec);
        // Decode using utf-8
        return new String(utf8, StandardCharsets.UTF_8);
    }

    public static String getTempCodeForToday() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return getTempCode(CODE4TODAY_VSDF.format(LocalDate.now()));
    }

    public static String getTempCodeForCurrentHour() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return getTempCode(CODE4CURRENT_HOUR_VSDF.format(LocalDateTime.now()));
    }

    public static String getTempCodeForCurrentMinute() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return getTempCode(CODE4CURRENT_MINUTE_VSDF.format(LocalDateTime.now()));
    }

    public static String getTempCode(String sPlain) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        int size = sPlain.length();
        byte[] baInput = sPlain.getBytes("ISO-8859-1");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.reset();
        md5.update(baInput);
        byte[] baOutput = md5.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : baOutput) {
            // System.out.print("%02x", 0xFF&b);
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        String ret = hexString.toString();
        if (hexString.length() > size) {
            ret = hexString.substring(0, size);
        }
        return ret;
    }

    public static final String CIPHER_NAME_AES = "AES/ECB/PKCS5Padding";
    public static final String ALGORITHM_NAME_AESR = "Rijndael"; // keySizes 128, 192, 256
    public static final String ALGORITHM_NAME_AES = "AES"; // keySizes 128, 192, 256

    public static SecretKey keyGen(String algorithm, int keySize) throws NoSuchAlgorithmException {
        KeyGenerator keygen = KeyGenerator.getInstance(algorithm);
        keygen.init(keySize);
        return keygen.generateKey();
    }

    public static byte[] serializeKey(SecretKey key) {
        return key.getEncoded();
    }

    public static SecretKey deserializeKey(byte[] keyBytes, String algorithm) {
        return new SecretKeySpec(keyBytes, algorithm);
    }

    public static byte[] parseHexString(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String formatHexString(byte[] bytes) {
        //return new String(bytes, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] encryptLong(SecretKey key, long num) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        BigInteger bignum = BigInteger.valueOf(num);
        Cipher cipher = Cipher.getInstance(CIPHER_NAME_AES);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(bignum.toByteArray());
    }

    public static long decryptLong(SecretKey key, byte[] ct) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(CIPHER_NAME_AES);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] pt = cipher.doFinal(ct);
        BigInteger bignum = new BigInteger(pt);
        return bignum.longValue();
    }

}
