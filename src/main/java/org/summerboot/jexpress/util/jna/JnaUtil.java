/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.summerboot.jexpress.util.jna;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */

public class JnaUtil {
    /**
     * System property to specify the path where JNA should look for 3rd party native libraries.
     */
    public static final String SYS_LIB_PATH = "jna.library.path"; //System.setProperty("jna.library.path", "/path/to/mylib.so"); // -Djna.library.path=/path/to/mylib.so
    public static final String SYS_LIB_TEMP_DIR = "jna.tmpdir"; // System.setProperty("jna.tmpdir", "/path/to/temp"); // -Djna.tmpdir=/path/to/temp

    /**
     * System property to specify the path where JNA should look for its own native library (libjnidispatch.so).
     * This is useful if you want to
     * <p> 1. use a specific version of JNA or if you want to avoid conflicts with other versions of JNA that may be present on the system.
     * <p> 2. need to disable JNA's automatic extraction and unpacking of native libraries from JAR files or resources.
     * <p> 3. want to load a so file  from memory-backed file (memfd_create) instead of a physical file on disk, which can be useful for security or performance reasons.
     */
    public static final String SYS_LIB_NOUNPACK = "jna.nounpack"; // System.setProperty("jna.nounpack", "true"); // -Djna.nounpack=true
    public static final String SYS_BOOT_LIB_PATH = "jna.boot.library.path"; // System.setProperty("jna.boot.library.path", "/usr/lib64/jna"); // -Djna.boot.library.path=<path to libjnidispatch.so, which is inside jna-5.19.1.jar>

    public interface LibC extends Library {
        LibC INSTANCE = load(); // Load the platform's C standard library

        static LibC load() {
            return load(null);
        }

        static LibC load(String libName) {
            if (libName == null) {
                libName = Platform.isWindows() ? "msvcrt" : Platform.C_LIBRARY_NAME;//msvcrt.dll, libc.so
            }
            LibC INSTANCE = Native.load((libName), LibC.class);
            return INSTANCE;
        }

        void printf(String format, Object... args);

        int write(int fd, ByteBuffer buf, int count);

        NativeLong write(int fd, Pointer buffer, NativeLong count);

        int close(int fd);

        int memfd_create(String name, int flags);
    }


    private static final int MFD_CLOEXEC = 0x0001;   // Automatically close on exec to prevent descriptor leaks to child processes

    /**
     * Loads a Linux shared library from a byte array using a memory-backed file.
     *
     * <p>The bytes must contain a complete ELF shared-library image compatible
     * with the current process. This method does not decrypt the input.
     *
     * <p>To disable JNA's automatic extraction of native libraries from JAR files
     * or resources, set these JVM options before JNA initializes:
     * <pre>{@code
     * -Djna.nounpack=true
     * -Djna.boot.library.path=/path/to/libjnidispatch.so (inside jna-5.19.1.jar)
     * }</pre>
     *
     * <p>The {@code libjnidispatch.so} must match the application's JNA version, operating system, and architecture.
     *
     * @param <T>         the JNA library interface type
     * @param libClass    the JNA library interface class
     * @param libFileData the complete ELF shared-library bytes
     * @param clearData   whether to overwrite the input array with zeros in the
     *                    cleanup block, on both success and failure
     * @return the loaded library proxy
     * @throws UnsupportedOperationException if the operating system is not Linux
     * @throws RuntimeException              if creating or writing the memory-backed file fails
     * @throws UnsatisfiedLinkError          if a required native function or library
     *                                       cannot be loaded
     */
    public static <T extends Library> T loadLibrary(Class<T> libClass, byte[] libFileData, boolean clearData) {
        int fd = -1;
        try {
            if (!Platform.isLinux()) {
                throw new UnsupportedOperationException("Operation is only supported on Linux. Current OS: " + Platform.getOSType());
            }

            // 1. Create an anonymous memory-backed file with a descriptive name.
            fd = LibC.INSTANCE.memfd_create("secure_mem_lib", MFD_CLOEXEC);
            if (fd < 0) {
                throw new RuntimeException("Failed to create memfd, Linux kernel version might be older than 3.17. Error code: " + fd);
            }

            // 2. Write the decrypted bytes to the memory-backed file descriptor.
            //ByteBuffer buffer = ByteBuffer.wrap(libFileData);
            try (Memory nativeBuffer = new Memory(libFileData.length);) {
                nativeBuffer.write(0, libFileData, 0, libFileData.length);
                NativeLong written = LibC.INSTANCE.write(fd, nativeBuffer, new NativeLong(libFileData.length));
                if (written.longValue() != libFileData.length) {
                    throw new RuntimeException("Failed to write the complete data to the memory file");
                }
            }

            // 3. Build the virtual path to the memory-backed file; /proc/self/fd/ exposes the current process's file descriptors.
            String procFdPath = "/proc/self/fd/" + fd;

            // 4. Let JNA call dlopen through this virtual path to dynamically link the memory-backed library.
            return Native.load(procFdPath, libClass);
        } finally {
            // Clear the input array to reduce exposure in JVM memory dumps.
            if (clearData) {
                Arrays.fill(libFileData, (byte) 0);
            }
            // After JNA loads the library, dlopen retains its mappings, allowing the original file descriptor to be closed.
            LibC.INSTANCE.close(fd);
        }
    }
}