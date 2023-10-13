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
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ApplicationUtil {

    public static final String[] EMPTY_ARGS = {};

    /**
     * Sun property pointing the main class and its arguments. Might not be
     * defined on non Hotspot VM implementations.
     */
    protected static final String JAVA_COMMAND_SUN = "sun.java.command";

    public static String[] getApplicationArgs() {
        String commandLine = System.getProperty(JAVA_COMMAND_SUN);
        if (commandLine != null) {
            return commandLine.split(" ");
        }
        return EMPTY_ARGS;
    }

    /**
     * java -XshowSettings:properties -version
     *
     * @param cmd
     * @return true if JMX is required
     */
    public static boolean scanJVM_StartCommand(final StringBuilder cmd) {
        boolean jmxRequired = false;
        String OS = System.getProperty("os.name").toLowerCase();
        boolean isWindows = OS.contains("win");
        // JVM home path
        String java = System.getProperty("java.home") + "/bin/java";
        // JVM arguments
        List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        StringBuffer vmArgsOneLine = new StringBuffer();
        for (String arg : vmArguments) {
            // ignore the agent argument to avoid the conflict of the address of old application and the new one
            if (!arg.contains("-agentlib")) {
                vmArgsOneLine.append(arg);
                vmArgsOneLine.append(" ");
            }
            if (arg.contains("com.sun.management.jmxremote.port")) {
                jmxRequired = true;
            }
        }
        String vender = System.getProperty("java.vendor") + " " + System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version");

        cmd.append("\nJVM = ").append(vender).append("\n");
        if (isWindows) {
            cmd.append("\"").append(java).append("\" ").append(vmArgsOneLine);
        } else {
            cmd.append(java).append(" ").append(vmArgsOneLine);
        }
        cmd.append("");

        String[] mainCommand = getApplicationArgs();
        String mainClassName = mainCommand.length > 0 ? mainCommand[0] : "(unknown Main class)";
        // application main
        if (mainClassName.endsWith(".jar")) {
            cmd.append("-jar ").append(new File(mainClassName).getAbsolutePath());
        } else {
            cmd.append("-cp \"").append(System.getProperty("java.class.path")).append("\" ").append(mainClassName);
        }
        // application arguments
        for (int i = 1; i < mainCommand.length; i++) {
            cmd.append(" ");
            cmd.append(mainCommand[i]);
        }
        return jmxRequired;
    }

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
            return InetAddress.getLocalHost().getHostName();
            //System.setProperty(key, InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ex) {
            ex.printStackTrace(System.err);
            if (exitWhenFail) {
                System.exit(-1);
            }
        }
        return null;
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

    public static Set<Class<?>> loadClassFromJarFile(File jarFile, boolean failOnUndefinedClasses) throws IOException {
        URL url = jarFile.getAbsoluteFile().toURI().toURL();
        URL[] urls = {url};
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();//Thread.currentThread().getContextClassLoader()
        URLClassLoader urlClassLoader = new URLClassLoader(urls, appClassLoader);
        Set<Class<?>> classes = new HashSet<>();
        StringBuilder sb = new StringBuilder(jarFile.getAbsolutePath());
        boolean onError = false;
        Set<String> classNames = getClassNamesFromJarFile(jarFile);
        for (String className : classNames) {
            try {
                Class loadedClass = urlClassLoader.loadClass(className);
                classes.add(loadedClass);
            } catch (ClassNotFoundException | NoClassDefFoundError ex) {
                onError = true;
                sb.append("\n\t").append(ex.toString());
            }
        }

        //Java 17 only if (!sb.isEmpty() && failOnUndefinedClasses) {
        if (onError && failOnUndefinedClasses) {
            throw new NoClassDefFoundError(sb.toString());
        }
        return classes;
    }

    public static final String RESOURCE_PATH = "org/summerboot/jexpress/template/";

    public static Path createIfNotExist(String location, ClassLoader classLoader, String srcFileName, String destFileName) {
        Path targetFile = Paths.get(location, destFileName).toAbsolutePath();
        if (Files.exists(targetFile)) {
            return targetFile;
        }
        try (InputStream ioStream = classLoader.getResourceAsStream(RESOURCE_PATH + srcFileName);) {
            final byte[] bytes = IOUtils.toByteArray(ioStream);
            Files.write(targetFile, bytes);
        } catch (Throwable ex) {
            System.out.println(ex + "\n\tCould generate from " + srcFileName + " to " + targetFile);
            ex.printStackTrace();
            System.exit(1);
        }
        return targetFile;
    }
}
