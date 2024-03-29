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
package org.summerboot.jexpress.integration.ldap;

import org.summerboot.jexpress.security.SSLUtil;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class LdapSSLConnectionFactory2 extends SocketFactory {

    protected static final AtomicReference<LdapSSLConnectionFactory2> defaultFactory = new AtomicReference<>();

    protected final SSLSocketFactory sf;

    public static String TLS_PROTOCOL = "TLSv1.3";
    protected static KeyManager[] KMS;
    protected static TrustManager[] TMS;

    public static void init(KeyManagerFactory kmf, TrustManagerFactory tmf, String protocol) {
        KMS = kmf == null ? null : kmf.getKeyManagers();
        TMS = tmf == null ? null : tmf.getTrustManagers();
        if (protocol != null) {
            TLS_PROTOCOL = protocol;
        }
    }

    public LdapSSLConnectionFactory2() {
        try {
            SSLContext sslCtx = SSLUtil.buildSSLContext(KMS, TMS, TLS_PROTOCOL);
            sf = sslCtx.getSocketFactory();
        } catch (IOException | GeneralSecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static SocketFactory getDefault() {
        final LdapSSLConnectionFactory2 value = defaultFactory.get();
        if (value == null) {
            defaultFactory.compareAndSet(null, new LdapSSLConnectionFactory2());
            return defaultFactory.get();
        }
        return value;
    }

    @Override
    public Socket createSocket(final String host, final int port) throws IOException {
        return sf.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return sf.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(final InetAddress host, int port) throws IOException {
        return sf.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return sf.createSocket(address, port, localAddress, localPort);
    }
}
