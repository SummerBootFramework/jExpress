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
import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class JnaUtil {
    public static final String SYS_LIB_PATH = "jna.library.path";
    public static final String SYS_LIB_TEMP_DIR = "jna.tmpdir";

    public interface CLibrary extends Library {
        CLibrary INSTANCE = load(); // Load the Linux C standard library

        static CLibrary load() {
            return load(null);
        }

        static CLibrary load(String libName) {
            if (libName == null) {
                libName = Platform.isWindows() ? "msvcrt" : "c";//msvcrt.dll, libc.so
            }
            CLibrary INSTANCE = (CLibrary) Native.load((libName), CLibrary.class);
            return INSTANCE;
        }

        void printf(String format, Object... args);

        /**
         * 基础的 close 和 write 函数
         */
        int write(int fd, ByteBuffer buf, int count);

        int close(int fd);

        /**
         * 针对 memfd_create 系统调用的显式映射 (最安全，推荐)
         * C 签名: long syscall(SYS_memfd_create, const char *name, unsigned int flags)
         * 在 64 位 Linux 中，返回值和系统调用号建议都使用 long (对应 C 的 long/size_t)
         */
        long syscall(long number, String name, int flags);

        /**
         * 通用的变长参数系统调用映射
         * 注意：在实际传入参数时，所有的指针、地址或大于 32 位的整数，必须显式强转为 long！
         */
        long syscall(long number, Object... args);
    }

    // Linux x86_64 架构下的系统调用号 (System Call Numbers)
    private static final int SYS_memfd_create = 319; // x86_64 特有，如果是其他架构需更换
    private static final int MFD_CLOEXEC = 0x0001;   // 执行 exec 时自动关闭，防止泄漏给子进程

    /**
     * 从解密后的内存 byte[] 直接加载 .so 动态库
     */
    public static <T extends Library> T loadFromMemory(byte[] soFileData, Class<T> libClass) {
        if (!Platform.isLinux()) {
            throw new UnsupportedOperationException("该无盘加载方案仅支持 Linux 环境！");
        }
        // 禁用 JNA 自动从 Jar 或资源中提取和解压本地库的功能
        //System.setProperty("jna.nounpack", "true"); // -Djna.nounpack=true
        //System.setProperty("jna.boot.library.path", "/usr/lib64/jna"); // -Djna.boot.library.path=/usr/lib64/jna

        // 1. 在内存中创建一个匿名文件，赋予一个伪装的文件名（在无盘审计中只对内存可见）
        // syscall(SYS_memfd_create, "name", flags)
        long lfd = CLibrary.INSTANCE.syscall(SYS_memfd_create, "secure_mem_lib", MFD_CLOEXEC);
        int fd = Math.toIntExact(lfd); // 将 long 转为 int，确保在 32 位系统上也能正确使用
        if (fd < 0) {
            throw new RuntimeException("memfd_create 失败，可能 Linux 内核版本低于 3.17");
        }

        try {
            // 2. 将解密后的明文 byte[] 写入到这个内存文件描述符中
            ByteBuffer buffer = ByteBuffer.wrap(soFileData);
            int written = CLibrary.INSTANCE.write(fd, buffer, soFileData.length);
            // 🌟 纵深防御 A：立刻擦除 Java 层明文内存，防止 JVM 内存被 Dump 提取
            Arrays.fill(soFileData, (byte) 0);
            if (written != soFileData.length) {
                throw new RuntimeException("写入内存文件不完整");
            }

            // 3. 构造该内存文件在当前进程中的虚拟映射路径
            // 每一个 Linux 进程都可以在 /proc/self/fd/ 下看到自己的文件描述符
            String procFdPath = "/proc/self/fd/" + fd;

            // 4. 让 JNA 通过这个虚拟路径进行 dlopen 加载
            // 此时系统内核会直接读取 RAM 中的数据，完成动态链接，物理磁盘无任何 I/O
            return Native.load(procFdPath, libClass);

        } finally {
            // JNA 加载完成后，句柄已被内核 dlopen 持有，可以安全关闭 Java 层的 fd
            CLibrary.INSTANCE.close(fd);
        }
    }
}
