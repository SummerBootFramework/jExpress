package org.summerframework.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;

/**
 * http://geobytes.com/get-city-details-api/
 */
public class GeoIpUtil {

    public static final String request = "http://getcitydetails.geobytes.com/GetCityDetails?fqcn=";

    public static String getRequest(String ip) {
        return request + ip;
    }

    public static void main(String[] args) {
        String[] ip = getMyIP();
        System.out.println(ip[0]);
        System.out.println(ip[1]);
        System.out.println(ip[2]);
    }

    public static final String CHECKIP_URL = "http://checkip.amazonaws.com";
    private static final String DEFAULT_IP = "";

    public static String getExternalIp() {
        String ip = DEFAULT_IP;
        try {
            URL whatismyip = new URL(CHECKIP_URL);
            try ( BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));) {
                ip = in.readLine();
            }
        } catch (Throwable ex) {
        }
        return ip;
    }

    public static String[] getMyIP() {
        String[] ret = {"", "", ""};
        StringBuilder sb0 = new StringBuilder();
        StringBuilder sb1 = new StringBuilder();
        try {
            sb0.append(getExternalIp()).append(" - ");
            ret[0] = InetAddress.getLocalHost().getHostAddress();
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.isAnyLocalAddress()) {
                        sb0.append(i.getHostAddress()).append(", ");
                        sb1.append("LocalAddress: ").append(i.toString()).append("<br>");
                    }
                    if (i.isLinkLocalAddress()) {
                        sb1.append("LinkLocalAddress: ").append(i.toString()).append("<br>");
                    }
                    if (i.isLoopbackAddress()) {
                        sb1.append("LoopbackAddress: ").append(i.toString()).append("<br>");
                    }
                    if (i.isSiteLocalAddress()) {
                        sb0.append(i.getHostAddress()).append(", ");
                        sb1.append("SiteLocalAddress: ").append(i.toString()).append("<br>");
                    }
                    if (i.isMCGlobal()) {
                        sb1.append("MCGlobal: ").append(i.toString()).append("<br>");
                    }
                    if (i.isMCLinkLocal()) {
                        sb1.append("MCLinkLocal: ").append(i.toString()).append("<br>");
                    }
                    if (i.isMCNodeLocal()) {
                        sb1.append("MCNodeLocal: ").append(i.toString()).append("<br>");
                    }
                    if (i.isMCOrgLocal()) {
                        sb1.append("MCOrgLocal: ").append(i.toString()).append("<br>");
                    }
                    if (i.isMCSiteLocal()) {
                        sb1.append("MCSiteLocal: ").append(i.toString()).append("<br>");
                    }
                }
            }
        } catch (Throwable ex) {
            sb1.append(ex);
        }
        String systemInfo = "<br>Pid#" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
                + "<br>user.name=" + System.getProperty("user.name")
                + "<br>user=" + System.getProperty("user.name")
                + "<br>home=" + System.getProperty("user.home")
                + "<br>dir=" + System.getProperty("user.dir")
                + "<br>os=" + System.getProperty("os.arch")
                + " " + System.getProperty("os.name")
                + " " + System.getProperty("os.version")
                + "<br>Java=" + System.getProperty("java.vendor")
                + " " + System.getProperty("java.version");
        ret[0] = sb0.toString();
        ret[1] = sb1.toString();
        ret[2] = systemInfo;
        return ret;
    }
}
