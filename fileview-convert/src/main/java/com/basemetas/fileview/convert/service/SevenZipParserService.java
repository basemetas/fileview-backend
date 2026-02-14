/*
 * Copyright 2025 BaseMetas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basemetas.fileview.convert.service;

import com.basemetas.fileview.convert.strategy.model.ArchiveInfo;
import com.basemetas.fileview.convert.strategy.model.ArchiveEntryInfo;
import com.basemetas.fileview.convert.utils.ArchiveUtils;
import com.basemetas.fileview.convert.utils.DateTimeUtils;
import com.basemetas.fileview.convert.utils.EnvironmentUtils;
import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SevenZip 压缩包解析服务
 * 
 * 提供四种解析策略:
 * 1. parse7zArchiveNativeIsolated - 子进程隔离的 native 解析(避免 JVM 崩溃)
 * 2. parse7zArchiveNative - 进程内 native 库解析(最准确,但有崩溃风险)
 * 3. parse7zArchiveFallback - 使用外部 7z 命令解析(最稳定,不加载 native 库)
 * 4. parse7zArchive - 智能选择策略(根据环境和模式自动选择)
 * 
 * @author 夫子
 */
@Component
public class SevenZipParserService {

    private static final Logger logger = LoggerFactory.getLogger(SevenZipParserService.class);

    @Autowired
    private ArchiveUtils archiveUtils;

    // 外部 7z 命令路径
    private static final String EXTERNAL_7Z_CMD = "7z";
    // 外部命令超时时间(秒)
    private static final long EXTERNAL_LIST_TIMEOUT_SEC = 30;

    // native 调用全局锁,减少并发调用导致的崩溃
    private static final Object NATIVE_LOCK = new Object();

    /**
     * 解析模式
     */
    public enum Mode {
        /** 自动选择(默认) */
        AUTO,
        /** 安全模式:始终隔离 native 调用 */
        SAFE_MODE_ALWAYS_ISOLATE,
        /** 仅使用外部命令 */
        EXTERNAL_ONLY
    }

    /**
     * 智能解析 7z 文件(默认 AUTO 模式)
     */
    public ArchiveInfo parse7zArchive(File archiveFile, String password) throws Exception {
        return parse7zArchive(archiveFile, password, Mode.AUTO);
    }

    /**
     * 智能解析 7z 文件(指定模式)
     * - AUTO: 智能选择,WSL2 环境优先隔离 native
     * - SAFE_MODE_ALWAYS_ISOLATE: 始终使用子进程隔离
     * - EXTERNAL_ONLY: 仅使用外部 7z 命令
     */
    public ArchiveInfo parse7zArchive(File archiveFile, String password, Mode mode) throws Exception {
        // 基本检查
        if (!archiveFile.exists()) {
            throw new FileNotFoundException("Archive not found: " + archiveFile.getAbsolutePath());
        }
        if (archiveFile.length() == 0) {
            throw new RuntimeException("7z文件为空或损坏");
        }

        boolean runningInWsl = EnvironmentUtils.isWslEnvironment();
        boolean externalAvailable = EnvironmentUtils.isExternal7zAvailable();

        logger.debug("7z解析环境检测 - WSL: {}, 外部7z可用: {}, 模式: {}", runningInWsl, externalAvailable, mode);

        // EXTERNAL_ONLY 模式
        if (mode == Mode.EXTERNAL_ONLY) {
            if (!externalAvailable) {
                throw new RuntimeException("外部 7z 命令不可用");
            }
            return parse7zArchiveFallback(archiveFile, password);
        }

        // 是否优先隔离
        boolean preferIsolation = mode == Mode.SAFE_MODE_ALWAYS_ISOLATE || runningInWsl;

        // 优先使用隔离的 native 解析(子进程,避免 JVM 崩溃)
        if (preferIsolation || mode == Mode.AUTO) {
            try {
                logger.debug("使用隔离子进程进行 native 解析");
                return parse7zArchiveNativeIsolated(archiveFile, password);
            } catch (Throwable t) {
                logger.warn("隔离 native worker 失败: {}", t.toString());
                if (externalAvailable) {
                    logger.info("Fallback 到外部 7z 解析器");
                    return parse7zArchiveFallback(archiveFile, password);
                }
                throw new RuntimeException("隔离 native 解析失败且外部 7z 不可用", t);
            }
        }

        // 进程内 native 解析(兜底)
        try {
            return parse7zArchiveNative(archiveFile, password);
        } catch (Throwable t) {
            logger.warn("进程内 native 解析失败: {}", t.toString());
            if (externalAvailable) {
                return parse7zArchiveFallback(archiveFile, password);
            }
            throw new RuntimeException("Native 解析失败且外部命令不可用", t);
        }
    }

    /**
     * 隔离子进程中运行 native 解析
     * 启动独立 JVM 进程运行 native worker,避免主进程崩溃
     */
    public ArchiveInfo parse7zArchiveNativeIsolated(File archiveFile, String password) throws Exception {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String mainClass = SevenZipParserService.class.getName();

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);
        cmd.add("--native-worker");
        cmd.add(archiveFile.getAbsolutePath());
        cmd.add(password == null ? "" : password);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        // logger.debug("启动隔离 native worker: {}",
        // String.join(" ", cmd.stream().map(s -> s.contains(" ") ? '"'+s+'"' :
        // s).collect(Collectors.toList())));
        Process p = pb.start();

        List<ArchiveEntryInfo> entries = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                // 每行格式: PATH\tIS_DIR\tSIZE\tPACKED_SIZE\tENCRYPTED\tMTIME
                String[] parts = line.split("\t", -1);
                if (parts.length < 6) {
                    continue;
                }
                String entryName = parts[0];

                // 跳过空路径或macOS元数据文件
                if (entryName.isEmpty() || ArchiveUtils.shouldSkipMacOSMetadataFile(entryName)) {
                    logger.debug("⏭️ 跳过条目: {}", entryName);
                    continue;
                }

                ArchiveEntryInfo e = new ArchiveEntryInfo();
                e.setName(entryName);
                e.setDirectory("1".equals(parts[1]));
                try {
                    e.setSize(Long.parseLong(parts[2]));
                } catch (Exception ex) {
                    e.setSize(0);
                }
                try {
                    e.setCompressedSize(Long.parseLong(parts[3]));
                } catch (Exception ex) {
                    e.setCompressedSize(0);
                }
                e.setEncrypted("1".equals(parts[4]));
                if (!parts[5].isEmpty()) {
                    try {
                        e.setLastModified(DateTimeUtils.toUTCDate(new Date(Long.parseLong(parts[5]))));
                    } catch (Exception ignored) {
                    }
                }
                if (!e.isDirectory()) {
                    e.setFileType(ArchiveUtils.determineFileType(e.getName()));
                }
                e.setMethod("7Z");
                entries.add(e);
            }

            boolean finished = p.waitFor(EXTERNAL_LIST_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("隔离 native worker 超时");
            }

            int exit = p.exitValue();
            if (exit != 0) {
                throw new RuntimeException("隔离 native worker 退出码: " + exit);
            }

            ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile, "7Z");
            info.setEntries(entries);
            ArchiveUtils.calculateStatistics(info);
            logger.info("隔离 native 解析完成 - 文件: {}, 条目数: {}", archiveFile.getName(), entries.size());
            return info;
        }
    }

    /**
     * Worker 主函数(在子进程中运行)
     * 调用: java -cp <cp> SevenZipParserService --native-worker <path> <password>
     */
    public static void main(String[] args) {
        if (args.length >= 1 && "--native-worker".equals(args[0])) {
            String path = args.length > 1 ? args[1] : null;
            String pw = args.length > 2 && !args[2].isEmpty() ? args[2] : null;
            int rc = 0;
            try {
                runNativeWorker(path, pw);
            } catch (Throwable t) {
                t.printStackTrace(System.err);
                rc = 2;
            }
            System.exit(rc);
        } else {
            System.err.println("该类可作为 native worker: --native-worker <path> <password>");
        }
    }

    /**
     * Worker 函数,在子 JVM 中执行
     */
    private static void runNativeWorker(String archivePath, String password) throws Exception {
        File archiveFile = new File(archivePath);
        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;
        try {
            randomAccessFile = new RandomAccessFile(archiveFile, "r");
            RandomAccessFileInStream rafStream = new RandomAccessFileInStream(randomAccessFile);
            IArchiveOpenCallback openCallback = (password != null && !password.isEmpty())
                    ? new PasswordArchiveOpenCallback(password)
                    : null;

            inArchive = SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, rafStream, openCallback);
            int n = inArchive.getNumberOfItems();

            for (int i = 0; i < n; i++) {
                String fileName = (String) inArchive.getProperty(i, PropID.PATH);

                // 跳过空路径或无效条目
                if (fileName == null || fileName.trim().isEmpty()) {
                    continue;
                }

                // 跳过macOS元数据文件
                if (ArchiveUtils.shouldSkipMacOSMetadataFile(fileName)) {
                    continue;
                }

                Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                Object sizeObj = inArchive.getProperty(i, PropID.SIZE);
                Object packedObj = inArchive.getProperty(i, PropID.PACKED_SIZE);
                Boolean encrypted = (Boolean) inArchive.getProperty(i, PropID.ENCRYPTED);
                Date mtime = (Date) inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME);

                long size = ArchiveUtils.safeLongCast(sizeObj) == null ? 0L : ArchiveUtils.safeLongCast(sizeObj);
                long packed = ArchiveUtils.safeLongCast(packedObj) == null ? 0L : ArchiveUtils.safeLongCast(packedObj);
                String nameOut = fileName.replace('\t', ' ');
                String isDirFlag = (isFolder != null && isFolder) ? "1" : "0";
                String encFlag = (encrypted != null && encrypted) ? "1" : "0";
                String mtimeMs = mtime != null ? String.valueOf(mtime.getTime()) : "";

                // 输出行
                System.out.println(nameOut + "\t" + isDirFlag + "\t" + size + "\t" +
                        packed + "\t" + encFlag + "\t" + mtimeMs);
            }
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (Exception ignored) {
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 使用 SevenZipJBinding native 库解析(进程内)
     * 使用全局锁串行化调用,减少并发问题
     */
    public ArchiveInfo parse7zArchiveNative(File archiveFile, String password) throws Exception {
        long startTime = System.currentTimeMillis();

        // 文件格式验证
        validateArchiveFile(archiveFile);

        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile, "7Z");
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        RandomAccessFile randomAccessFile = null;
        IInArchive inArchive = null;

        try {
            randomAccessFile = new RandomAccessFile(archiveFile, "r");
            RandomAccessFileInStream rafStream = new RandomAccessFileInStream(randomAccessFile);

            // 创建密码回调
            IArchiveOpenCallback openCallback = (password != null && !password.isEmpty())
                    ? new PasswordArchiveOpenCallback(password)
                    : null;

            if (password != null && !password.isEmpty()) {
                logger.info("🔑 使用密码解析7Z文件 (native)");
            }

            logger.debug("调用 SevenZip.openInArchive (native)...");
            try {
                // 🔑 使用全局锁串行化 native 调用
                synchronized (NATIVE_LOCK) {
                    inArchive = SevenZip.openInArchive(
                            ArchiveFormat.SEVEN_ZIP,
                            rafStream,
                            openCallback);
                }
                logger.debug("✅ Native SevenZip.openInArchive 成功");
            } catch (Throwable nativeError) {
                logger.error("❌ SevenZipJBinding native 解析失败 - File: {}, Error: {}",
                        archiveFile.getName(), nativeError.getMessage(), nativeError);
                throw nativeError;
            }

            int numberOfItems = inArchive.getNumberOfItems();
            int negativeSizeCount = 0;

            for (int i = 0; i < numberOfItems; i++) {
                String fileName = (String) inArchive.getProperty(i, PropID.PATH);

                // 编码检测:处理中文文件名乱码
                if (fileName != null && ArchiveUtils.isMessyCode(fileName)) {
                    try {
                        fileName = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), "GBK");
                        logger.debug("🔧 检测到乱码并修复文件名: {}", fileName);
                    } catch (Exception e) {
                        logger.warn("文件名编码转换失败: {}", fileName, e);
                    }
                }

                // 跳过 macOS 元数据文件
                if (ArchiveUtils.shouldSkipMacOSMetadataFile(fileName)) {
                    logger.debug("⏭️ 跳过macOS元数据文件: {}", fileName);
                    continue;
                }

                ArchiveEntryInfo entryInfo = new ArchiveEntryInfo();
                entryInfo.setName(fileName != null ? fileName : "");

                Long size = ArchiveUtils.safeLongCast(inArchive.getProperty(i, PropID.SIZE));
                Long packedSize = ArchiveUtils.safeLongCast(inArchive.getProperty(i, PropID.PACKED_SIZE));
                Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                Date lastModified = (Date) inArchive.getProperty(i, PropID.LAST_MODIFICATION_TIME);
                Long crc = ArchiveUtils.safeLongCast(inArchive.getProperty(i, PropID.CRC));
                Boolean encrypted = (Boolean) inArchive.getProperty(i, PropID.ENCRYPTED);

                entryInfo.setSize(size != null ? size : 0);
                if (size == null || size < 0) {
                    negativeSizeCount++;
                }
                entryInfo.setCompressedSize(packedSize != null && packedSize >= 0 ? packedSize : 0);
                entryInfo.setDirectory(isFolder != null ? isFolder : false);
                entryInfo.setLastModified(lastModified != null ? DateTimeUtils.toUTCDate(lastModified) : null);
                entryInfo.setCrc(crc != null ? crc : 0);
                entryInfo.setMethod("7Z");
                entryInfo.setEncrypted(encrypted != null ? encrypted : false);

                if (!entryInfo.isDirectory()) {
                    entryInfo.setFileType(ArchiveUtils.determineFileType(entryInfo.getName()));
                }

                entries.add(entryInfo);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("7Z native 解析完成 - 文件: {}, 条目数: {}, 负数/null大小: {}, 耗时: {}ms",
                    archiveFile.getName(), entries.size(), negativeSizeCount, duration);

        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (Exception e) {
                    logger.warn("关闭 inArchive 失败", e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (Exception e) {
                    logger.warn("关闭 randomAccessFile 失败", e);
                }
            }
        }

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    /**
     * 使用外部 7z 命令解析(p7zip)
     * 不加载 native 库,因此不会导致 JVM 因 SIGSEGV 崩溃
     * 运行 `7z l -slt -pPASSWORD archive` 并解析输出
     */
    public ArchiveInfo parse7zArchiveFallback(File archiveFile, String password) throws Exception {
        long startTime = System.currentTimeMillis();
        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile, "7Z");
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        List<String> cmd = new ArrayList<>();
        cmd.add(EXTERNAL_7Z_CMD);
        cmd.add("l"); // list
        cmd.add("-slt"); // show technical information
        if (password != null && !password.isEmpty()) {
            cmd.add("-p" + password);
        }
        cmd.add(archiveFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        logger.debug("运行外部 7z 命令: {}", String.join(" ", cmd));
        Process p = pb.start();

        boolean finished = p.waitFor(EXTERNAL_LIST_TIMEOUT_SEC, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("外部 7z 命令超时");
        }

        int exitCode = p.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("外部 7z 命令执行失败,退出码: " + exitCode);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            ArchiveEntryInfo current = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                // 条目分隔符
                if (line.startsWith("----------") || line.equals("-------------------")) {
                    if (current != null && !ArchiveUtils.shouldSkipMacOSMetadataFile(current.getName())) {
                        entries.add(current);
                    }
                    current = null;
                    continue;
                }

                // 解析 Key = Value 格式
                int eq = line.indexOf("=");
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                switch (key) {
                    case "Path":
                        if (current != null && !ArchiveUtils.shouldSkipMacOSMetadataFile(current.getName())) {
                            entries.add(current);
                        }
                        current = new ArchiveEntryInfo();
                        // 🔑 关键修复：过滤掉压缩包本身（绝对路径或与压缩包同名）
                        if (val.equals(archiveFile.getAbsolutePath()) ||
                                val.equals(archiveFile.getName())) {
                            logger.debug("⚠ 跳过压缩包本身: {}", val);
                            current = null; // 不创建节点
                        } else {
                            current.setName(val);
                        }
                        break;
                    case "Size":
                        if (current != null) {
                            try {
                                current.setSize(Long.parseLong(val));
                            } catch (NumberFormatException e) {
                                current.setSize(0);
                            }
                        }
                        break;
                    case "Packed Size":
                        if (current != null) {
                            try {
                                current.setCompressedSize(Long.parseLong(val));
                            } catch (NumberFormatException e) {
                                current.setCompressedSize(0);
                            }
                        }
                        break;
                    case "Modified":
                        if (current != null) {
                            try {
                                // 7z 时间格式: 2023-01-15 10:30:45
                                current.setLastModified(ArchiveUtils.parseDateFromExternal(val));
                            } catch (Exception e) {
                                logger.debug("解析时间失败: {}", val);
                            }
                        }
                        break;
                    case "Folder":
                        if (current != null) {
                            current.setDirectory("+".equals(val) || "1".equals(val));
                        }
                        break;
                    case "Attributes":
                        // 🔑 关键修复：通过Attributes属性识别目录（包含D标志）
                        if (current != null && val.contains("D")) {
                            current.setDirectory(true);
                            logger.debug("  ✓ 通过Attributes识别目录: {} ({})", current.getName(), val);
                        }
                        break;
                    case "CRC":
                        if (current != null && !val.isEmpty()) {
                            try {
                                current.setCrc(Long.parseLong(val, 16));
                            } catch (NumberFormatException e) {
                                current.setCrc(0);
                            }
                        }
                        break;
                    case "Encrypted":
                        if (current != null) {
                            current.setEncrypted("+".equals(val) || "1".equals(val));
                        }
                        break;
                    case "Method":
                        if (current != null) {
                            current.setMethod(val.isEmpty() ? "7Z" : val);
                        }
                        break;
                }
            }

            // 添加最后一个条目
            if (current != null && !ArchiveUtils.shouldSkipMacOSMetadataFile(current.getName())) {
                entries.add(current);
            }
        }

        // 填充文件类型
        for (ArchiveEntryInfo entry : entries) {
            if (!entry.isDirectory()) {
                entry.setFileType(ArchiveUtils.determineFileType(entry.getName()));
            }
            if (entry.getMethod() == null || entry.getMethod().isEmpty()) {
                entry.setMethod("7Z");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("7Z 外部命令解析完成 - 文件: {}, 条目数: {}, 耗时: {}ms",
                archiveFile.getName(), entries.size(), duration);

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证 7z 文件格式
     */
    private static void validateArchiveFile(File archiveFile) throws Exception {
        if (archiveFile.length() < 32) {
            throw new RuntimeException("7z文件过小,可能已损坏");
        }

        try (RandomAccessFile rafCheck = new RandomAccessFile(archiveFile, "r")) {
            byte[] header = new byte[32];
            rafCheck.readFully(header);

            // 验证魔数 (0x377ABCAF271C)
            if (!(header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte) 0xBC &&
                    header[3] == (byte) 0xAF && header[4] == 0x27 && header[5] == 0x1C)) {
                throw new RuntimeException("7z文件魔数不匹配,可能已损坏");
            }

            // 验证版本号
            int majorVersion = header[6] & 0xFF;
            int minorVersion = header[7] & 0xFF;

            if (majorVersion != 0) {
                throw new RuntimeException("7z文件版本不支持(Major Version应为0)");
            }
            if (minorVersion > 20) {
                throw new RuntimeException("7z文件Minor Version异常,可能已损坏");
            }

            logger.debug("7z文件格式验证通过 - File: {}, Version: {}.{}",
                    archiveFile.getName(), majorVersion, minorVersion);
        }
    }

    /**
     * 密码回调类,用于 7z 加密文件解压
     */
    private static class PasswordArchiveOpenCallback implements IArchiveOpenCallback,
            net.sf.sevenzipjbinding.ICryptoGetTextPassword {
        private final String password;

        public PasswordArchiveOpenCallback(String password) {
            this.password = password;
        }

        @Override
        public void setTotal(Long files, Long bytes) {
            // 不需要实现
        }

        @Override
        public void setCompleted(Long files, Long bytes) {
            // 不需要实现
        }

        @Override
        public String cryptoGetTextPassword() {
            return password;
        }
    }
}
