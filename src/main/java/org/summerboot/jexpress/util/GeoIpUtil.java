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
package org.summerboot.jexpress.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GeoIpUtil {

    public static final String CHECKIP_URL_AWS = "http://checkip.amazonaws.com";

    public static String getExternalIp() throws IOException, URISyntaxException {
        return getExternalIp(CHECKIP_URL_AWS);
    }

    public static String getExternalIp(String url) throws IOException, URISyntaxException {
        URL whatismyip = new URI(url).toURL();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));) {
            return in.readLine();
        }
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

    public static void showAddress(String host, int port) {
        InetSocketAddress address = new InetSocketAddress(host, port);
        String info = showAddress(address);
        System.out.println(info);
    }

    public static String showAddress(InetSocketAddress address) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n isUnresolved=").append(address.isUnresolved());
        sb.append("\n port=").append(address.getPort());
        sb.append("\n toString=").append(address.toString());
        sb.append("\n getHostString=").append(address.getHostString());
        sb.append("\n getHostName=").append(address.getHostName());
        sb.append("\n getAddress=").append(address.getAddress());
        sb.append("\n getHostAddress=").append(address.getAddress().getHostAddress());
        sb.append("\n getHostName=").append(address.getAddress().getHostName());
        sb.append("\n getCanonicalHostName=").append(address.getAddress().getCanonicalHostName());
        return sb.toString();
    }

    public static enum CallerAddressFilterOption {
        String, HostString, HostName, AddressString, HostAddress, AddrHostName, CanonicalHostName
    }

    /**
     * Simple filter for caller address
     *
     * @param callerAddr
     * @param whiteList
     * @param blackList
     * @param option
     * @return null if OK, otherwise return the reason
     */
    public static String callerAddressFilter(SocketAddress callerAddr, Set<String> whiteList, Set<String> blackList, String regexPrefix, CallerAddressFilterOption option) {
        if (callerAddr == null) {
            return "caller address is null";
        }
        String host;
        if (callerAddr instanceof InetSocketAddress) {
            InetSocketAddress address = (InetSocketAddress) callerAddr;
            if (address.isUnresolved()) {
                return "caller address (" + address + ") is unresolved";
            }
            switch (option) {
                case String -> host = address.toString();
                case HostString -> host = address.getHostString();
                case HostName -> host = address.getHostName();
                case AddressString -> host = address.getAddress().toString();
                case HostAddress -> host = address.getAddress().getHostAddress();
                case AddrHostName -> host = address.getAddress().getHostName();
                case CanonicalHostName -> host = address.getAddress().getCanonicalHostName();
                default -> host = address.getHostName();
            }
        } else {
            host = callerAddr.toString();
        }
        return callerAddressFilter(host, whiteList, blackList, regexPrefix);
    }

    /**
     * Simple filter for caller address
     *
     * @param host
     * @param whiteList
     * @param blackList
     * @return null if OK, otherwise return the reason
     */
    public static String callerAddressFilter(String host, Set<String> whiteList, Set<String> blackList, String regexPrefix) {
        if (whiteList != null && !whiteList.isEmpty()) {
            if (!whiteList.contains(host)) {
                // check regex
                if (regexPrefix != null) {
                    for (String whiteRegex : whiteList) {
                        if (whiteRegex.startsWith(regexPrefix)) {
                            if (matches(host, whiteRegex, regexPrefix)) {
                                return null;
                            }
                        }
                    }
                }
                return "caller address (" + host + ") is not in white list";
            }
        }
        if (blackList != null && !blackList.isEmpty()) {
            if (blackList.contains(host)) {
                return "caller address (" + host + ") is in black list";
            } else if (regexPrefix != null) {
                for (String blackRegex : blackList) {// check regex
                    if (blackRegex.startsWith(regexPrefix)) {
                        if (matches(host, blackRegex, regexPrefix)) {
                            return "caller address (" + host + ") matches black list: " + blackRegex;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    public static boolean matches(String input, String regex, String regexPrefix) {
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        Pattern p = REGEX_CACHE.get(regex);
        if (p == null) {
            if (regexPrefix != null && regex.startsWith(regexPrefix)) {
                p = Pattern.compile(regex.substring(regexPrefix.length()));
            } else {
                p = Pattern.compile(regex);
            }
            REGEX_CACHE.put(regex, p);
        }
        Matcher m = p.matcher(input);
        //return m.matches();  This is a Java's misnamed method, it tries and matches ALL the input.
        return m.find();// If you want to see if the regex matches an input text, use the .find() method of the matcher
    }
}
