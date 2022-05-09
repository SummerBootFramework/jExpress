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
package org.summerframework.integration.ldap;

import org.summerframework.security.SSLUtil;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class LdapSSLConnectionFactory extends SocketFactory {

    private static final AtomicReference<LdapSSLConnectionFactory> defaultFactory = new AtomicReference<>();

    private SSLSocketFactory sf;

    public static String TLS_PROTOCOL = "TLSv1.3";
    private static KeyManager[] KMS;
    private static TrustManager[] TMS;

    public static void init(KeyManager[] kms, TrustManager[] tms, String protocol) {
        KMS = kms;
        TMS = tms;
        if (protocol != null) {
            TLS_PROTOCOL = protocol;
        }
    }

    public LdapSSLConnectionFactory() {
        try {
            SSLContext sslCtx = SSLUtil.buildSSLContext(KMS, TMS, TLS_PROTOCOL);
            sf = sslCtx.getSocketFactory();
        } catch (IOException | GeneralSecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static SocketFactory getDefault() {
        final LdapSSLConnectionFactory value = defaultFactory.get();
        if (value == null) {
            defaultFactory.compareAndSet(null, new LdapSSLConnectionFactory());
            return defaultFactory.get();
        }
        return value;
    }

    @Override
    public Socket createSocket(final String s, final int i) throws IOException {
        return sf.createSocket(s, i);
    }

    @Override
    public Socket createSocket(final String s, final int i, final InetAddress inetAddress, final int i1) throws IOException {
        return sf.createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(final InetAddress inetAddress, final int i) throws IOException {
        return sf.createSocket(inetAddress, i);
    }

    @Override
    public Socket createSocket(final InetAddress inetAddress, final int i, final InetAddress inetAddress1, final int i1) throws IOException {
        return sf.createSocket(inetAddress, i, inetAddress1, i1);
    }
}
