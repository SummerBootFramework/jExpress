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
package org.summerboot.jexpress.security;

import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class SSLUtil {

    public static String DEFAULT_PROTOCOL = "TLSv1.3";
    // Create all-trusting host name verifier

    /**
     * To ignore the host name verification
     */
    public static final HostnameVerifier IGNORE_HOST_NAME_VERIFIER = (String hostname, SSLSession session) -> true;//(hostname, session) -> true

    enum Caller {
        client, server
    }

    protected static final X509Certificate[] TRUSTED_CERTIFICATE = new X509Certificate[0];
    public static final TrustManager[] TRUST_ALL_CERTIFICATES = new TrustManager[]{
            new X509TrustManager() {

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return TRUSTED_CERTIFICATE;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    //checkTrusted(Caller.client, certs, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    //checkTrusted(Caller.server, certs, authType);
                }

//            protected void checkTrusted(Caller caller, X509Certificate[] certs, String authType) {
//                System.out.println(caller + ".authType=" + authType);
//                for (X509Certificate cer : certs) {
//                    System.out.println(caller + ".cer=" + cer.getSubjectDN());
//                }
//            }
            }
    };
    //public static final TrustManager[] TRUST_ALL_CERTIFICATES = null;

    /*public static void disableSslVerification(KeyManager[] kms) throws NoSuchAlgorithmException, KeyManagementException {
        // 1. ignore the host name verification
        HttpsURLConnection.setDefaultHostnameVerifier(IGNORE_HOST_NAME_VERIFIER);

        //2. trust all certificates
        // Create a trust manager that does not validate certificate chains
        //导入客户端证书
//        KeyStore ks = KeyStore.getInstance("pkcs12");
//        FileInputStream instream = new FileInputStream(new File(PATH));
//        ks.load(instream, psw.toCharArray());
//        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//        kmf.init(ks, psw.toCharArray());
//        KeyManager[] kms = kmf.getKeyManagers();
        //String TLS_VERSION = "TLSv1.3";// "SSL";
        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance(DEFAULT_PROTOCOL);
        sc.init(kms, TRUST_ALL_CERTIFICATES, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }*/
    //    public static KeyManager[] buildKeyManagers(String keyStorePath, String keyStorePwdStr) throws GeneralSecurityException, IOException {
//        char[] keyStorePwd = StringUtils.isBlank(keyStorePwdStr) ? null : SecurityUtil.decrypt(v).toCharArray();
//        return buildKeyManagers(keyStorePath, char[] keyStorePwd);
//    }
    public static KeyManagerFactory buildKeyManagerFactory(String keyStorePath, char[] keyStorePwd, String keyAlias, char[] keyPwd) throws GeneralSecurityException, IOException {
        if (StringUtils.isBlank(keyStorePath)) {
            return null;
        }
        KeyManagerFactory kmf = null;
        try (InputStream keystoreIn = new FileInputStream(keyStorePath.trim());) {
            KeyStore originalKeyStore = EncryptorUtil.getKeystoreInstance();
            originalKeyStore.load(keystoreIn, keyStorePwd);
            // If keyAlias is not specified, use the first alias in the keystore
            if (StringUtils.isNotBlank(keyAlias)) {
                // Check if the specified key alias exists in the keystore if keyAlias is specified
                if (!originalKeyStore.containsAlias(keyAlias)) {
                    throw new IllegalArgumentException("Key alias '" + keyAlias + "' not found in keystore: " + keyStorePath);
                }
                // check if the alias is a private key entry
                PrivateKey targetPrivateKey = null;
                Certificate[] targetCertChain = null;
                if (originalKeyStore.isKeyEntry(keyAlias)) {
                    targetPrivateKey = (PrivateKey) originalKeyStore.getKey(keyAlias, keyPwd);
                    targetCertChain = originalKeyStore.getCertificateChain(keyAlias);
                    if (targetPrivateKey == null || targetCertChain == null || targetCertChain.length == 0) {
                        throw new IllegalStateException("Failed to retrieve private key or certificate chain for alias: " + keyAlias + "' in keystore: " + keyStorePath);
                    }
                } else {
                    throw new IllegalArgumentException("Alias '" + keyAlias + "' is not a key entry in keystore: " + keyStorePath);
                }
                // Create a new KeyStore instance to hold the single entry
                KeyStore singleEntryKeyStore = EncryptorUtil.getKeystoreInstance();
                singleEntryKeyStore.load(null, null); // init a new empty keystore with no password
                // Set the target private key and certificate chain into the new keystore
                singleEntryKeyStore.setKeyEntry(
                        keyAlias, // Keep the original alias
                        targetPrivateKey,
                        keyPwd, // eep the original key password
                        targetCertChain
                );
                originalKeyStore = singleEntryKeyStore; // Use the new keystore with only the specified key entry
            }
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(originalKeyStore, keyStorePwd);
        } finally {
            // earse password from RAM
            if (keyStorePwd != null) {
                for (int i = 0; i < keyStorePwd.length; i++) {
                    keyStorePwd[i] = 0;
                }
            }
            if (keyPwd != null) {
                for (int i = 0; i < keyPwd.length; i++) {
                    keyPwd[i] = 0;
                }
            }
        }
        return kmf;
    }

    public static KeyManager[] buildKeyManagers(String keyStorePath, char[] keyStorePwd, String keyAlias, char[] keyPwd) throws GeneralSecurityException, IOException {
        if (StringUtils.isBlank(keyStorePath)) {
            return null;
        }
        KeyManagerFactory kmf = buildKeyManagerFactory(keyStorePath, keyStorePwd, keyAlias, keyPwd);

        return kmf == null ? null : kmf.getKeyManagers();
    }

    public static TrustManagerFactory buildTrustManagerFactory(String trustStorePath, char[] trustStorePwd) throws GeneralSecurityException, IOException {
        if (StringUtils.isBlank(trustStorePath)) {
            return null;
        }
        TrustManagerFactory tf = null;
        try (InputStream truststoreIn = new FileInputStream(trustStorePath);) {
            KeyStore tks = EncryptorUtil.getKeystoreInstance();
            tks.load(truststoreIn, trustStorePwd);
            tf = TrustManagerFactory.getInstance("SunX509");
            tf.init(tks);
            //trustManagers = tf.getTrustManagers();
        } finally {
            // earse password from RAM
            if (trustStorePwd != null) {
                for (int i = 0; i < trustStorePwd.length; i++) {
                    trustStorePwd[i] = 0;
                }
            }
        }

        return tf;
    }

    public static TrustManager[] buildTrustManagers(String trustStorePath, char[] trustStorePwd) throws GeneralSecurityException, IOException {
        TrustManagerFactory tmf = buildTrustManagerFactory(trustStorePath, trustStorePwd);
        return tmf == null ? null : tmf.getTrustManagers();
    }

    public static SSLContext buildSSLContext(String keyStorePath, char[] keyStorePwd, String keyAlias, char[] keyPwd, String protocol, String trustStorePath, char[] trustStorePwd) throws GeneralSecurityException, IOException {
        KeyManager[] kms = buildKeyManagers(keyStorePath, keyStorePwd, keyAlias, keyPwd);
        TrustManager[] tms = buildTrustManagers(trustStorePath, trustStorePwd);
        return buildSSLContext(kms, tms, protocol);
    }

    public static SSLContext buildSSLContext(KeyManager[] kms, TrustManager[] tms, String protocol) throws GeneralSecurityException, IOException {
        if (kms == null) {
            return null;
        }
        //protocol = StringUtils.isBlank(protocol)?DEFAULT_PROTOCOL:protocol;
        // Initialize the SSLContext to work with TLS
        SSLContext ret = SSLContext.getInstance(protocol);
        // Initialize the SSLContext to work with key and trust managers.
        ret.init(kms, tms, SecureRandom.getInstanceStrong());
        return ret;
    }

}
