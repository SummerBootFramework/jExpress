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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ApplicationUtil {

    public static Map<Object, Set<String>> checkDuplicateFields(Class errorCodeClass, Class fieldClass) throws IllegalArgumentException, IllegalAccessException {
        Map<Object, Set<String>> duplicates = new HashMap();
        Map<String, Object> errorCodes = new HashMap();
        ReflectionUtil.loadFields(errorCodeClass, fieldClass, errorCodes, true);

        Map<Object, String> temp = new HashMap();
        errorCodes.keySet().forEach((varName) -> {
            Object errorCode = errorCodes.get(varName);
            String duplicated = temp.put(errorCode, varName);
            if (duplicated != null) {
                Set<String> names = duplicates.get(errorCode);
                if (names == null) {
                    names = new HashSet();
                    duplicates.put(errorCode, names);
                }
                names.add(varName);
                names.add(duplicated);
            }
        });

        return duplicates;
    }

    public static String getServerName(boolean exitWhenFail) {
        try {
            System.setProperty("hostName", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ex) {
            System.setProperty("hostName", null);
            ex.printStackTrace(System.err);
            if (exitWhenFail) {
                System.exit(-1);
            }
        }
        return System.getProperty("hostName");
    }

    public static Set<String> getClassNamesFromJarFile(File jarFile) throws IOException {
        Set<String> classNames = new HashSet<>();
        JarFile jar = new JarFile(jarFile);
        Enumeration<JarEntry> jarEntries = jar.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            String entryName = jarEntry.getName();
            if (entryName.endsWith(".class")) {
                String className = entryName.replace(".class", "").replaceAll("/", ".");
                classNames.add(className);
            }
        }
        return classNames;
    }

    public static Set<Class> loadClassFromJarFile(File jarFile, boolean failOnUndefinedClasses) throws IOException {
        String jarURL = "file:/" + jarFile.getAbsolutePath();
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new URL(jarURL)}, Thread.currentThread().getContextClassLoader());
        Set<Class> classes = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        Set<String> classNames = getClassNamesFromJarFile(jarFile);
        for (String className : classNames) {
            try {
                Class loadedClass = urlClassLoader.loadClass(className);
                classes.add(loadedClass);
            } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                sb.append("\n\t").append(ex.toString());
            }
        }
        if (!sb.isEmpty() && failOnUndefinedClasses) {
            throw new NoClassDefFoundError(sb.toString());
        }
        return classes;
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        File jarFile = new File("D:\\projects\\Quickcat\\Quickcat-ejb\\dist\\Quickcat-ejb.jar");
        Set<Class> classSet = loadClassFromJarFile(jarFile, true);
        System.out.println(classSet.size());
    }
}
