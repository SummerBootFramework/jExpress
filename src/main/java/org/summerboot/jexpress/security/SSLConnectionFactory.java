package org.summerboot.jexpress.security;


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

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class SSLConnectionFactory extends SSLSocketFactory {
    protected final SSLSocketFactory sf;

    /**
     * Creates an SSLConnectionFactory with the specified KeyManagerFactory and TrustManagerFactory.
     *
     * @param kmf
     * @param tmf
     * @param protocol default TLSv1.3 if null
     */
    public SSLConnectionFactory(KeyManagerFactory kmf, TrustManagerFactory tmf, String protocol) throws GeneralSecurityException, IOException {
        this(kmf == null ? null : kmf.getKeyManagers(),
                tmf == null ? null : tmf.getTrustManagers(),
                protocol);
    }

    /**
     * Creates an SSLConnectionFactory with the specified KeyManagers and TrustManagers.
     *
     * @param kms
     * @param tms
     * @param protocol
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public SSLConnectionFactory(KeyManager[] kms, TrustManager[] tms, String protocol) throws GeneralSecurityException, IOException {
        SSLContext sslCtx = SSLUtil.buildSSLContext(kms, tms, protocol == null ? "TLSv1.3" : protocol);
        sf = sslCtx.getSocketFactory();
    }

    /**
     * Creates an SSLConnectionFactory with the specified SSLSocketFactory.
     *
     * @param sf
     */
    public SSLConnectionFactory(SSLSocketFactory sf) {
        this.sf = sf;
    }

    public SSLSocketFactory getSSLSocketFactory() {
        return sf;
    }

    @Override
    public Socket createSocket() throws IOException {
        return sf.createSocket();
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

    @Override
    public String[] getDefaultCipherSuites() {
        return sf.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return sf.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return sf.createSocket(s, host, port, autoClose);
    }
}
