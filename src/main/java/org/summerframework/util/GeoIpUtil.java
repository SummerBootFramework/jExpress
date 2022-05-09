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
package org.summerframework.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class GeoIpUtil {

    public static final String CHECKIP_URL_AWS = "http://checkip.amazonaws.com";

    public static String getExternalIp() throws IOException {
        return getExternalIp(CHECKIP_URL_AWS);
    }

    public static String getExternalIp(String url) throws IOException {
        URL whatismyip = new URL(url);
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
}
