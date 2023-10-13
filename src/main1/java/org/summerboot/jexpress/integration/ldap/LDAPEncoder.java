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

/**
 *
 * @author https://wiki.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
 */
public class LDAPEncoder {

    public static String escapeDN(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
        if ((name.length() > 0) && ((name.charAt(0) == ' ') || (name.charAt(0) == '#'))) {
            sb.append('\\'); // add the leading backslash if needed
        }
        for (int i = 0; i < name.length(); i++) {
            char curChar = name.charAt(i);
            switch (curChar) {
                case '\\':
                    sb.append("\\\\");
                    break;
//                case ',':
//                    sb.append("\\,");
//                    break;
                case '&':
                    sb.append("\\&");
                    break;
                case '+':
                    sb.append("\\+");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '<':
                    sb.append("\\<");
                    break;
                case '>':
                    sb.append("\\>");
                    break;
                case ';':
                    sb.append("\\;");
                    break;
                default:
                    sb.append(curChar);
            }
        }
        if ((name.length() > 1) && (name.charAt(name.length() - 1) == ' ')) {
            sb.insert(sb.length() - 1, '\\'); // add the trailing backslash if needed
        }
        return sb.toString();
    }

    public static final String escapeLDAPSearchFilter(String filter) {
        StringBuilder sb = new StringBuilder(); // If using JDK >= 1.5 consider using StringBuilder
        for (int i = 0; i < filter.length(); i++) {
            char curChar = filter.charAt(i);
            switch (curChar) {
                case '\\':
                    sb.append("\\5c");
                    break;
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\u0000':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(curChar);
            }
        }
        return sb.toString();
    }
}
