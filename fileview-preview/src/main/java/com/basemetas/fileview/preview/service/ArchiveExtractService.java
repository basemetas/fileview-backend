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
package com.basemetas.fileview.preview.service;

import com.basemetas.fileview.preview.config.FileTypeMapper;
import com.basemetas.fileview.preview.config.FileCategory;
import com.basemetas.fileview.preview.model.archive.ArchivePathInfo;
import com.basemetas.fileview.preview.model.archive.ExtractResult;
import com.basemetas.fileview.preview.model.archive.ExtractionDecision;
import com.basemetas.fileview.preview.utils.FileUtils;
import com.basemetas.fileview.preview.utils.EnvironmentUtils;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import net.lingala.zip4j.ZipFile;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import com.basemetas.fileview.preview.service.password.PasswordOpenCallback;
import com.basemetas.fileview.preview.service.password.PasswordUnlockService;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

@Service
public class ArchiveExtractService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExtractService.class);

    // native 调用全局锁,减少并发调用导致的崩溃（参考转换模块）
    private static final Object NATIVE_LOCK = new Object();

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private PasswordUnlockService passwordUnlockService;

    @Autowired
    private FileUtils fileUtils;

    @Value("${fileview.archive.buffer-size:8192}")
    private int bufferSize;

    @Value("${fileview.archive.max-file-size:104857600}")
    private long maxFileSize;

    /**
     * 从完整路径中解压文件（支持密码）
     * 支持格式：/path/to/archive.zip/internal/file.txt 或
     * /path/to/archive.zip!/nested/archive.rar!/file.txt
     * 
     * @param fullPath 完整路径
     * @param password 密码（可选）
     * @return ExtractResult
     */
    public ExtractResult extractFileByFullPath(String fullPath, String password) {
        logger.info("开始解析完整路径: {}, 带密码: {}", fullPath, password != null);
        try {
            // 直接使用原有的解析方法，移除嵌套路径处理逻辑
            ArchivePathInfo pathInfo = parseArchivePath(fullPath);
            logger.info("解析完成 - 压缩文件路径: {}, 内部文件路径: {}", pathInfo.getArchivePath(), pathInfo.getInternalPath());

            // 调用现有的解压方法（传递密码）
            return extractFileFromArchive(pathInfo.getArchivePath(), pathInfo.getInternalPath(), null, password);
        } catch (IllegalArgumentException e) {
            logger.error("路径解析失败: {}", e.getMessage());
            return ExtractResult.failure("路径解析失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("解压文件时发生异常", e);
            return ExtractResult.failure("解压文件异常: " + e.getMessage());
        }
    }

    /**
     * 解压压缩包内的文件（支持从 Redis 获取密码）
     * 该方法会优先使用请求中的密码，如果没有则从 Redis 获取
     * 
     * @param archivePath     压缩包路径（包含内部文件路径）
     * @param fileId          文件ID
     * @param clientId        客户端ID
     * @param requestPassword 请求中的密码（可为null）
     * @return 解压后的文件路径
     */
    public String extractArchiveFileWithPassword(String archivePath, String fileId, String clientId,
            String requestPassword) {
        // 🔑 从完整路径中解析出压缩包文件路径，用于生成正确的 fileId
        String archiveFileId = fileId;
        String internalFilePath = null;
        try {
            ArchivePathInfo pathInfo = parseArchivePath(archivePath);
            // 使用压缩包文件的路径生成 fileId，而不是完整路径
            archiveFileId = fileUtils.generateFileIdFromFileUrl(pathInfo.getArchivePath());
            internalFilePath = pathInfo.getInternalPath();
            logger.debug("📝 使用压缩包文件生成 FileId: {} - ArchivePath: {}", archiveFileId, pathInfo.getArchivePath());
        } catch (Exception e) {
            logger.warn("⚠️ 无法解析压缩包路径，使用原始 fileId: {}", fileId, e);
        }

        // ⚠️ 关键检查：判断内部文件是否为不支持的格式
        if (internalFilePath != null) {
            String fileExtension = fileUtils.getFileExtention(internalFilePath);

            // 1. 检查是否为可执行文件
            if (isExecutableFile(internalFilePath)) {
                logger.warn("⚠️ 检测到可执行文件，拒绝解压 - File: {}", internalFilePath);
                throw new UnsupportedFileFormatException(
                        "不支持预览可执行文件(.exe/.dll/.so等),以防止系统崩溃",
                        "UNSUPPORTED_EXECUTABLE_FILE");
            }

            // 2. 检查是否为系统支持的文件格式
            if (fileExtension != null && !fileExtension.isEmpty()) {
                FileCategory category = fileTypeMapper.getFileCategory(fileExtension);
                if (category == null) {
                    logger.warn("⚠️ 检测到不支持的文件格式，拒绝解压 - File: {}, Extension: {}", internalFilePath, fileExtension);
                    throw new UnsupportedFileFormatException(
                            "不支持的文件格式: ." + fileExtension,
                            "UNSUPPORTED_FILE_FORMAT");
                }
                logger.debug("✅ 文件格式检查通过 - File: {}, Extension: {}, Category: {}",
                        internalFilePath, fileExtension, category.getDescription());
            }
        }

        // 🔑 优先从请求中获取密码，如果没有则从 Redis 获取
        String password = requestPassword;
        if ((password == null || password.trim().isEmpty()) && clientId != null && !clientId.trim().isEmpty()) {
            password = passwordUnlockService.getPassword(archiveFileId, clientId);
            if (password != null) {
                logger.info("🔓 从 Redis 获取到压缩包密码 - ArchiveFileId: {}, ClientId: {}", archiveFileId, clientId);
            }
        }

        // 使用密码解压文件
        ExtractResult extractResult = extractFileByFullPath(archivePath, password);

        // 检查解压结果并抛出异常
        if (!extractResult.isSuccess()) {
            if (extractResult.isRequiresPassword()) {
                String errorCode = extractResult.getErrorCode();
                if ("WRONG_PASSWORD".equals(errorCode)) {
                    logger.warn("❌ 压缩包密码错误 - ArchiveFileId: {}", archiveFileId);
                    throw new ArchivePasswordException("压缩包密码错误", "WRONG_PASSWORD");
                } else {
                    logger.warn("🔒 压缩包需要密码 - ArchiveFileId: {}", archiveFileId);
                    throw new ArchivePasswordException("压缩包需要密码", "PASSWORD_REQUIRED");
                }
            } else {
                logger.error("❌ 解压失败 - ArchiveFileId: {}, Error: {}", archiveFileId, extractResult.getErrorMessage());
                throw new ArchiveExtractionException("解压失败: " + extractResult.getErrorMessage());
            }
        }

        String extractedPath = extractResult.getAbsolutePath();
        logger.info("✅ 解压成功 - ExtractedPath: {}", extractedPath);
        return extractedPath;
    }

    /**
     * 解析完整路径，分离压缩文件路径和内部文件路径
     * 
     * @param fullPath 完整路径（格式：压缩文件路径+压缩包内文件路径）
     * @return ArchivePathInfo对象，包含压缩文件路径和内部文件路径
     * @throws IllegalArgumentException 当路径格式无效时抛出
     */
    public ArchivePathInfo parseArchivePath(String fullPath) throws IllegalArgumentException {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("完整路径不能为空");
        }
        // 获取压缩文件扩展名
        String[] archiveExtensions = getArchiveExtensions();
        // 查找最后一个支持的压缩文件扩展名的位置
        String lowerPath = fullPath.toLowerCase();
        int archiveEndIndex = -1;
        // 优先匹配较长的扩展名（如.tar.gz）
        for (String ext : new String[] { ".tar.gz", ".tar.bz2" }) {
            int index = lowerPath.indexOf(ext);
            if (index >= 0) {
                archiveEndIndex = index + ext.length();
                break;
            }
        }
        // 如果没找到复合扩展名，查找单个扩展名
        if (archiveEndIndex == -1) {
            for (String ext : archiveExtensions) {
                int index = lowerPath.indexOf(ext);
                if (index >= 0) {
                    archiveEndIndex = index + ext.length();
                    break;
                }
            }
        }
        if (archiveEndIndex == -1) {
            throw new IllegalArgumentException("路径中未找到有效的压缩文件扩展名");
        }
        // 分离压缩文件路径和内部文件路径
        String archivePath = fullPath.substring(0, archiveEndIndex);
        String internalPath = fullPath.substring(archiveEndIndex);

        // 移除开头的路径分隔符
        if (internalPath.startsWith("/") || internalPath.startsWith("\\")) {
            internalPath = internalPath.substring(1);
        }
        // 验证压缩文件路径是否存在
        File archiveFile = new File(archivePath);
        if (!archiveFile.exists()) {
            throw new IllegalArgumentException("压缩文件不存在: " + archivePath);
        }

        // 验证内部路径是否为空
        if (internalPath.isEmpty()) {
            throw new IllegalArgumentException("内部文件路径不能为空");
        }

        // 进行安全验证，防止路径遍历攻击
        validatePathSecurity(internalPath);

        return new ArchivePathInfo(archivePath, internalPath);
    }

    /**
     * 验证路径安全性，防止路径遍历攻击
     * 
     * @param path 待验证的路径
     * @throws IllegalArgumentException 当路径不安全时抛出
     */
    public void validatePathSecurity(String path) throws IllegalArgumentException {
        // 检查是否包含路径遍历字符
        if (path.contains("..")) {
            throw new IllegalArgumentException("路径包含非法字符 '..'");
        }
        // 检查是否以路径分隔符开头
        if (path.startsWith("/") || path.startsWith("\\")) {
            throw new IllegalArgumentException("路径不能以 '/' 或 '\\' 开头");
        }
        // 规范化路径并检查是否仍然安全
        try {
            Path normalizedPath = Paths.get(path).normalize();
            String normalizedStr = normalizedPath.toString().replace('\\', '/');
            // 检查规范化后的路径是否仍包含非法字符
            if (normalizedStr.contains("../") || normalizedStr.contains("..\\")) {
                throw new IllegalArgumentException("路径包含非法的相对路径引用");
            }
            // 检查规范化后的路径是否以父目录引用开始
            if (normalizedStr.startsWith("..")) {
                throw new IllegalArgumentException("路径指向父目录，可能存在安全风险");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("路径格式无效: " + e.getMessage());
        }
    }

    /**
     * 检查是否为macOS可执行文件
     */
    private boolean isExecutableFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        // Windows可执行文件
        String[] windowsExecs = { ".exe", ".dll", ".sys", ".bat", ".cmd", ".msi", ".scr", ".com" };
        for (String ext : windowsExecs) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        // Linux/Unix可执行文件
        String[] unixExecs = { ".so", ".sh", ".bin", ".run" };
        for (String ext : unixExecs) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        // macOS可执行文件
        String[] macExecs = { ".dylib", ".app" };
        for (String ext : macExecs) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查是否应该跳过macOS压缩包的元数据文件
     * macOS在创建ZIP压缩包时会自动添加元数据文件，这些文件不是真正的文档，无法被转换引擎处理
     * 
     * @param entryName 压缩包内文件路径
     * @return 如果应该跳过返回true
     */
    private boolean shouldSkipMacOSMetadataFile(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        
        // 1. 跳过 __MACOSX 目录下的所有文件
        if (entryName.contains("__MACOSX")) {
            return true;
        }
        
        // 2. 跳过以 ._ 开头的隐藏文件（资源分支文件）
        String fileName = entryName;
        int lastSlash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = entryName.substring(lastSlash + 1);
        }
        if (fileName.startsWith("._")) {
            return true;
        }
        
        // 3. 跳过其他macOS特殊文件
        if (".DS_Store".equals(fileName)) {
            return true;
        }
        
        return false;
    }

    /**
     * 从压缩文件中解压指定文件（完整版，支持密码）
     * 
     * @param archiveFilePath 压缩文件的完整路径
     * @param targetFilePath  要解压的文件在压缩包中的相对路径
     * @param customTempDir   自定义临时目录（可选）
     * @param password        压缩包密码（可选）
     * @return 解压结果，包含成功状态和解压后的文件路径
     */
    public ExtractResult extractFileFromArchive(String archiveFilePath, String targetFilePath, String customTempDir,
            String password) {
        logger.info("开始解压文件 - 压缩包: {}, 目标文件: {}, 带密码: {}", archiveFilePath, targetFilePath, password != null);

        try {
            // 验证输入参数
            if (archiveFilePath == null || archiveFilePath.trim().isEmpty()) {
                return ExtractResult.failure("压缩文件路径不能为空");
            }
            if (targetFilePath == null || targetFilePath.trim().isEmpty()) {
                return ExtractResult.failure("目标文件路径不能为空");
            }

            // 🔑 关键安全检查：过滤可执行文件和macOS元数据文件
            if (isExecutableFile(targetFilePath)) {
                logger.warn("⚠️ 检测到可执行文件，拒绝解压 - File: {}", targetFilePath);
                return ExtractResult.failure("不支持预览可执行文件（.exe/.dll/.so等），以防止系统崩溃");
            }
            
            if (shouldSkipMacOSMetadataFile(targetFilePath)) {
                logger.warn("⚠️ 检测到macOS元数据文件，拒绝解压 - File: {}", targetFilePath);
                return ExtractResult.failure("不支持预览macOS系统元数据文件（__MACOSX/._*）");
            }

            // 进行安全验证，防止路径遍历攻击
            validatePathSecurity(targetFilePath);

            File archiveFile = new File(archiveFilePath);
            if (!archiveFile.exists() || !archiveFile.isFile()) {
                return ExtractResult.failure("压缩文件不存在或不是有效文件: " + archiveFilePath);
            }

            // 创建临时目录
            Path tempDir = fileUtils.createTempDirectory(archiveFile);

            // 根据文件类型选择解压方法
            String fileType = fileUtils.detectArchiveType(archiveFile);
            ExtractResult result;

            switch (fileType.toLowerCase()) {
                case "zip":
                case "jar":
                case "war":
                case "ear":
                    result = extractFromZipArchive(archiveFile, targetFilePath, tempDir, password);
                    break;
                case "tar":
                    result = extractFromTarArchive(archiveFile, targetFilePath, tempDir, false);
                    break;
                case "tar.gz":
                case "tgz":
                    result = extractFromTarArchive(archiveFile, targetFilePath, tempDir, true);
                    break;
                case "rar":
                    result = extractFromRarArchive(archiveFile, targetFilePath, tempDir, password);
                    break;
                case "7z":
                    result = extractFrom7zArchive(archiveFile, targetFilePath, tempDir, password);
                    break;
                default:
                    return ExtractResult.failure("不支持的压缩文件格式: " + fileType);
            }

            if (result.isSuccess()) {
                logger.info("文件解压成功 - 源文件: {}, 解压后路径: {}", targetFilePath, result.getExtractedFilePath());
            } else {
                logger.error("文件解压失败 - 原因: {}", result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            logger.error("解压文件时发生异常", e);
            return ExtractResult.failure("解压文件异常: " + e.getMessage());
        }
    }

    /**
     * 从ZIP类型的压缩文件中解压指定文件（支持密码）
     */
    private ExtractResult extractFromZipArchive(File archiveFile, String targetFilePath, Path tempDir,
            String password) {
        // 🔧 尝试多种编码解析ZIP文件，解决中文乱码问题
        String[] encodings = { "GBK", "UTF-8", "GB2312", "ISO-8859-1" };

        for (String encoding : encodings) {
            try {
                try (ZipFile zip4jFile = new ZipFile(archiveFile)) {
                    // 设置字符集
                    zip4jFile.setCharset(java.nio.charset.Charset.forName(encoding));

                    // 检查是否加密
                    if (zip4jFile.isEncrypted()) {
                        if (password == null || password.trim().isEmpty()) {
                            logger.warn("🔒 ZIP文件已加密，需要密码 - File: {}", archiveFile.getName());
                            return ExtractResult.passwordRequired("zip", "ZIP文件已加密，需要密码");
                        }

                        // 设置密码
                        zip4jFile.setPassword(password.toCharArray());
                        logger.debug("🔓 使用密码解密 ZIP 文件 - File: {}", archiveFile.getName());
                    }

                    // 查找目标文件
                    net.lingala.zip4j.model.FileHeader fileHeader = zip4jFile.getFileHeader(targetFilePath);
                    if (fileHeader == null) {
                        // 尝试替换路径分隔符
                        String normalizedPath = targetFilePath.replace('\\', '/');
                        fileHeader = zip4jFile.getFileHeader(normalizedPath);
                    }

                    // 如果找到了文件，进行解压
                    if (fileHeader != null) {
                        logger.info("✅ 成功使用编码 {} 找到文件: {}", encoding, fileHeader.getFileName());

                        if (fileHeader.isDirectory()) {
                            return ExtractResult.failure("目标路径是目录，不是文件: " + targetFilePath);
                        }

                        // 检查文件大小
                        if (fileHeader.getUncompressedSize() > maxFileSize) {
                            return ExtractResult.failure("文件太大，超过限制: " + fileHeader.getUncompressedSize() + " bytes");
                        }

                        // 懒解压：只提取目标文件
                        zip4jFile.extractFile(fileHeader, tempDir.toString());

                        // 构建返回路径
                        Path extractedFile = tempDir.resolve(fileHeader.getFileName()).normalize();

                        // 安全检查
                        if (!extractedFile.startsWith(tempDir)) {
                            throw new IOException("不安全的文件路径: " + fileHeader.getFileName());
                        }

                        if (!Files.exists(extractedFile)) {
                            return ExtractResult.failure("解压失败，文件不存在: " + extractedFile);
                        }

                        String relativePath = fileUtils.getRelativePath(extractedFile);
                        logger.info("✅ ZIP文件提取成功 - File: {}, Size: {} bytes",
                                fileHeader.getFileName(), fileHeader.getUncompressedSize());

                        return ExtractResult.success(relativePath, extractedFile.toString());
                    }
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                // 当前编码找不到文件，尝试下一个编码
                logger.debug("使用编码 {} 未找到文件，尝试下一个编码 - 目标文件: {}", encoding, targetFilePath);

            } catch (net.lingala.zip4j.exception.ZipException e) {
                String errorMsg = e.getMessage();

                // 判断是否为密码错误
                if (errorMsg != null && (errorMsg.toLowerCase().contains("wrong password") ||
                        errorMsg.toLowerCase().contains("incorrect password"))) {
                    logger.warn("❌ ZIP密码错误 - File: {}", archiveFile.getName());
                    return ExtractResult.wrongPassword("zip", "ZIP密码错误，请重试");
                }

                // 其他异常，尝试下一个编码
                logger.debug("使用编码 {} 解压ZIP失败: {}", encoding, e.getMessage());
                if (encoding.equals("ISO-8859-1")) {
                    // 最后一个编码也失败了
                    logger.error("💥 ZIP文件解压异常", e);
                    return ExtractResult.failure("ZIP文件解压失败: " + errorMsg);
                }

            } catch (IOException e) {
                logger.debug("使用编码 {} 解压ZIP IO异常: {}", encoding, e.getMessage());
                if (encoding.equals("ISO-8859-1")) {
                    // 最后一个编码也失败了
                    logger.error("💥 ZIP文件解压 IO异常", e);
                    return ExtractResult.failure("ZIP文件解压失败: " + e.getMessage());
                }
            }
        }

        // 所有编码都失败，返回找不到文件
        logger.warn("⚠️ 尝试所有编码后仍未找到文件: {}", targetFilePath);
        return ExtractResult.failure("在压缩包中未找到指定文件: " + targetFilePath);
    }

    /**
     * 从TAR类型的压缩文件中解压指定文件
     */
    private ExtractResult extractFromTarArchive(File archiveFile, String targetFilePath, Path tempDir,
            boolean isGzipped) {
        try (FileInputStream fis = new FileInputStream(archiveFile);
                InputStream inputStream = isGzipped ? new GzipCompressorInputStream(fis) : fis;
                TarArchiveInputStream tarInput = new TarArchiveInputStream(inputStream)) {

            ArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                String entryName = entry.getName();

                // 检查是否是目标文件
                if (entryName.equals(targetFilePath) || entryName.equals(targetFilePath.replace('\\', '/'))) {
                    if (entry.isDirectory()) {
                        return ExtractResult.failure("目标路径是目录，不是文件: " + targetFilePath);
                    }

                    // 检查文件大小
                    if (entry.getSize() > maxFileSize) {
                        return ExtractResult.failure("文件太大，超过限制: " + entry.getSize() + " bytes");
                    }

                    // 解压文件
                    Path extractedFile = extractTarEntry(tarInput, entry, tempDir);
                    String relativePath = fileUtils.getRelativePath(extractedFile);

                    return ExtractResult.success(relativePath, extractedFile.toString());
                }
            }

            return ExtractResult.failure("在压缩包中未找到指定文件: " + targetFilePath);

        } catch (Exception e) {
            logger.error("解压TAR文件失败", e);
            return ExtractResult.failure("解压TAR文件失败: " + e.getMessage());
        }
    }

    /**
     * 从rar类型的压缩文件中解压指定文件（支持密码）
     */
    private ExtractResult extractFromRarArchive(File archiveFile, String targetFilePath, Path tempDir,
            String password) {
        // RAR 加密支持：优先使用 SevenZipJBinding（支持密码）
        if (password != null && !password.trim().isEmpty()) {
            logger.debug("🔓 使用 SevenZipJBinding 处理加密 RAR - File: {}", archiveFile.getName());
            return extractFromRarWith7z(archiveFile, targetFilePath, tempDir, password);
        }

        // 无密码时，先尝试 junrar
        try (Archive archive = new Archive(archiveFile)) {
            for (FileHeader fileHeader : archive.getFileHeaders()) {
                String entryName = fileHeader.getFileName();

                // 检查是否是目标文件
                if (entryName.equals(targetFilePath) || entryName.equals(targetFilePath.replace('\\', '/'))) {
                    if (fileHeader.isDirectory()) {
                        return ExtractResult.failure("目标路径是目录，不是文件: " + targetFilePath);
                    }

                    // 检查文件大小
                    if (fileHeader.getUnpSize() > maxFileSize) {
                        return ExtractResult.failure("文件太大，超过限制: " + fileHeader.getUnpSize() + " bytes");
                    }

                    // 解压文件
                    Path extractedFile = extractRarEntry(archive, fileHeader, tempDir);
                    String relativePath = fileUtils.getRelativePath(extractedFile);

                    return ExtractResult.success(relativePath, extractedFile.toString());
                }
            }

            return ExtractResult.failure("在压缩包中未找到指定文件: " + targetFilePath);

        } catch (Exception e) {
            logger.debug("junrar 失败，尝试 SevenZipJBinding: {}", e.getMessage());
            // junrar 失败，尝试 7z（可能是加密 RAR）
            return extractFromRarWith7z(archiveFile, targetFilePath, tempDir, null);
        }
    }

    /**
     * 使用 SevenZipJBinding 处理 RAR 文件（支持密码）
     */
    private ExtractResult extractFromRarWith7z(File archiveFile, String targetFilePath, Path tempDir, String password) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r")) {

            // 创建密码回调
            IArchiveOpenCallback openCallback = password != null ? new PasswordOpenCallback(password) : null;

            // 🔑 关键修复：使用三参数重载，显式指定归档格式，防止WSL2环境下SIGSEGV崩溃
            IInArchive inArchive = SevenZip.openInArchive(
                    ArchiveFormat.RAR,  // 显式指定 RAR 格式
                    new RandomAccessFileInStream(randomAccessFile),
                    openCallback);

            try {
                int numberOfItems = inArchive.getNumberOfItems();

                for (int i = 0; i < numberOfItems; i++) {
                    String fileName = (String) inArchive.getProperty(i, PropID.PATH);
                    Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                    Boolean isEncrypted = (Boolean) inArchive.getProperty(i, PropID.ENCRYPTED);

                    // 检查是否加密
                    if (Boolean.TRUE.equals(isEncrypted) && password == null) {
                        logger.warn("🔒 RAR文件已加密，需要密码 - File: {}", archiveFile.getName());
                        return ExtractResult.passwordRequired("rar", "RAR文件已加密，需要密码");
                    }

                    // 检查是否是目标文件
                    if (fileName != null && (fileName.equals(targetFilePath)
                            || fileName.equals(targetFilePath.replace('\\', '/')))) {
                        if (Boolean.TRUE.equals(isFolder)) {
                            return ExtractResult.failure("目标路径是目录，不是文件: " + targetFilePath);
                        }

                        // 检查文件大小
                        Long size = safeLongCast(inArchive.getProperty(i, PropID.SIZE));
                        if (size != null && size > maxFileSize) {
                            return ExtractResult.failure("文件太大，超过限制: " + size + " bytes");
                        }

                        // 解压文件
                        Path extractedFile = extract7zEntry(inArchive, i, fileName, tempDir);
                        String relativePath = fileUtils.getRelativePath(extractedFile);

                        logger.info("✅ RAR文件提取成功 - File: {}", fileName);
                        return ExtractResult.success(relativePath, extractedFile.toString());
                    }
                }

                return ExtractResult.failure("在压缩包中未找到指定文件: " + targetFilePath);

            } finally {
                inArchive.close();
            }

        } catch (SevenZipException e) {
            String errorMsg = e.getMessage();

            // 判断是否为密码相关错误
            if (errorMsg != null && (errorMsg.toLowerCase().contains("password") ||
                    errorMsg.toLowerCase().contains("encrypted") ||
                    errorMsg.toLowerCase().contains("wrong password"))) {
                if (password == null) {
                    logger.warn("🔒 RAR文件已加密，需要密码 - File: {}", archiveFile.getName());
                    return ExtractResult.passwordRequired("rar", "RAR文件已加密，需要密码");
                } else {
                    logger.warn("❌ RAR密码错误 - File: {}", archiveFile.getName());
                    return ExtractResult.wrongPassword("rar", "RAR密码错误，请重试");
                }
            }

            logger.error("💥 RAR文件解压异常", e);
            return ExtractResult.unsupportedEncryption("rar", "RAR文件解压失败，可能不支持该加密算法: " + errorMsg);

        } catch (Exception e) {
            logger.error("💥 RAR文件解压失败", e);
            return ExtractResult.failure("RAR文件解压失败: " + e.getMessage());
        }
    }

    /**
     * 从7z类型的压缩文件中解压指定文件（支持密码）
     * 参考转换模块 SevenZipParserService 的智能解析策略
     * - WSL2 环境：仅使用外部 7z 命令（避免 native 崩溃导致服务中断）
     * - 非 WSL2：使用 native 库（加全局锁保护），失败时 fallback 到外部命令
     */
    private ExtractResult extractFrom7zArchive(File archiveFile, String targetFilePath, Path tempDir, String password) {
        logger.info("🔍 开始解压7z文件 - ArchiveFile: {}, TargetFilePath: {}, TempDir: {}, HasPassword: {}", 
            archiveFile.getAbsolutePath(), targetFilePath, tempDir, password != null);
        
        // Step 1: 验证7z文件基本格式
        try {
            logger.debug("📋 开始验证7z文件格式 - File: {}", archiveFile.getName());
            if (!validate7zFileFormat(archiveFile)) {
                logger.error("❌ 7z文件格式验证失败 - File: {}", archiveFile.getName());
                return ExtractResult.failure("7z文件格式验证失败");
            }
            logger.debug("✅ 7z文件格式验证成功 - File: {}", archiveFile.getName());
        } catch (Exception e) {
            logger.error("💥 7z文件格式验证异常 - File: {}", archiveFile.getName(), e);
            return ExtractResult.failure("7z文件格式验证异常: " + e.getMessage());
        }
        
        // Step 2: 检测运行环境
        boolean runningInWsl = EnvironmentUtils.isWslEnvironment();
        boolean external7zAvailable = EnvironmentUtils.isExternal7zAvailable();
        logger.info("🔍 环境检测 - WSL2: {}, 外部7z命令可用: {}", runningInWsl, external7zAvailable);
        
        // Step 3: WSL2环境或不支持 native 的平台（如 ARM64），仅使用外部 7z 命令
        boolean nativeSupported = EnvironmentUtils.isNativeSevenZipSupported();
        if (runningInWsl || !nativeSupported) {
            if (!external7zAvailable) {
                logger.error("❌ WSL2环境或不支持 native 的平台下外部 7z 命令不可用，无法安全提取 - File: {}", archiveFile.getName());
                return ExtractResult.failure(
                    "WSL2环境或当前平台下需要p7zip-full支持，请在Docker镜像中安装：apt-get install p7zip-full");
            }
            if (runningInWsl) {
                logger.info("🔄 WSL2环境检测到，仅使用外部 7z 命令提取（避免 native 崩溃）- File: {}", archiveFile.getName());
            } else {
                logger.info("🔄 当前平台不支持 SevenZipJBinding native 库，直接使用外部 7z 命令提取 - File: {}", archiveFile.getName());
            }
            return tryExtractWith7zCommand(archiveFile, targetFilePath, tempDir, password);
        }
        
        // Step 4: 非WSL2环境，优先使用native方式（加全局锁保护）
        logger.debug("➡️ 非WSL2环境，调用native方式提取7z文件 - File: {}", archiveFile.getName());
        ExtractResult result = extract7zFileWithNative(archiveFile, targetFilePath, tempDir, password);
        
        // Step 5: Native失败且外部命令可用，尝试fallback
        if (!result.isSuccess() && external7zAvailable) {
            logger.info("🔄 Native提取失败，fallback到外部7z命令 - File: {}", archiveFile.getName());
            return tryExtractWith7zCommand(archiveFile, targetFilePath, tempDir, password);
        }
        
        return result;
    }
    
    /**
     * 验证 7z 文件格式
     */
    private boolean validate7zFileFormat(File archiveFile) throws Exception {
        logger.debug("🔍 开始验证7z文件格式 - File: {}, Size: {} bytes", archiveFile.getName(), archiveFile.length());
        
        if (archiveFile.length() == 0) {
            logger.warn("❌ 7z文件为空 - File: {}", archiveFile.getName());
            return false;
        }
        
        try (RandomAccessFile rafCheck = new RandomAccessFile(archiveFile, "r")) {
            if (rafCheck.length() < 32) {
                logger.warn("❌ 7z文件过小，至少需要32字节 - File: {}, ActualSize: {}", archiveFile.getName(), rafCheck.length());
                return false;
            }
            
            byte[] header = new byte[32];
            rafCheck.readFully(header);
            logger.debug("📝 读取7z文件头 - File: {}, HeaderBytes: [{}, {}, {}, {}, {}, {}]", 
                archiveFile.getName(), 
                String.format("0x%02X", header[0]), 
                String.format("0x%02X", header[1]), 
                String.format("0x%02X", header[2]),
                String.format("0x%02X", header[3]),
                String.format("0x%02X", header[4]),
                String.format("0x%02X", header[5]));
            
            // 验证魔数
            if (!(header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte)0xBC && 
                  header[3] == (byte)0xAF && header[4] == 0x27 && header[5] == 0x1C)) {
                logger.warn("❌ 7z文件魔数不匹配 - File: {}, Expected: [0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C]", archiveFile.getName());
                return false;
            }
            
            // 验证版本号
            int majorVersion = header[6] & 0xFF;
            int minorVersion = header[7] & 0xFF;
            logger.debug("📌 7z文件版本信息 - File: {}, Version: {}.{}", archiveFile.getName(), majorVersion, minorVersion);
            
            if (majorVersion != 0 || minorVersion > 20) {
                logger.warn("❌ 7z文件版本异常 - File: {}, Version: {}.{}, Expected: 0.x (x<=20)", 
                    archiveFile.getName(), majorVersion, minorVersion);
                return false;
            }
            
            logger.debug("✅ 7z文件格式验证通过 - File: {}, Version: {}.{}", 
                archiveFile.getName(), majorVersion, minorVersion);
            return true;
        }
    }
    
    /**
     * 使用 native 方式提取 7z 文件
     * 增强错误处理，捕获 native 层崩溃
     * 参考转换模块：使用全局锁保护 native 调用
     */
    private ExtractResult extract7zFileWithNative(File archiveFile, String targetFilePath, Path tempDir, String password) {
        logger.info("🔧 使用Native方式提取7z文件 - File: {}, Target: {}", archiveFile.getName(), targetFilePath);
        
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(archiveFile, "r")) {

            // 创建密码回调
            IArchiveOpenCallback openCallback = (password != null && !password.isEmpty()) 
                ? new PasswordOpenCallback(password) 
                : null;

            logger.debug("🔓 创建密码回调 - File: {}, HasPassword: {}", archiveFile.getName(), password != null);
            logger.info("📂 正在使用SevenZip.openInArchive打开7z文件... - File: {}, ArchiveFormat: SEVEN_ZIP", archiveFile.getName());
            
            // 🔑 关键修复：使用三参数重载，显式指定归档格式，防止WSL2环境下SIGSEGV崩溃
            IInArchive inArchive;
            try {
                // 🔒 参考转换模块：使用全局锁串行化 native 调用
                synchronized (NATIVE_LOCK) {
                    inArchive = SevenZip.openInArchive(
                            ArchiveFormat.SEVEN_ZIP,
                            new RandomAccessFileInStream(randomAccessFile),
                            openCallback);
                }
                logger.info("✅ SevenZip.openInArchive 调用成功 - File: {}", archiveFile.getName());
            } catch (Throwable nativeError) {
                // ⚠️ 捕获native层错误（包括SIGSEGV等致命错误的Java包装异常）
                logger.error("❌ SevenZipJBinding native库解析失败 - File: {}, ErrorType: {}, ErrorMessage: {}", 
                    archiveFile.getName(), nativeError.getClass().getName(), nativeError.getMessage(), nativeError);
                
                // 🔧 尝试使用外部 7z 命令作为 fallback（如果可用）
                logger.info("🔄 尝试fallback到外部7z命令 - File: {}", archiveFile.getName());
                return tryExtractWith7zCommand(archiveFile, targetFilePath, tempDir, password);
            }

            try {
                int numberOfItems = inArchive.getNumberOfItems();
                logger.info("📊 7z文件包含条目数 - File: {}, TotalItems: {}", archiveFile.getName(), numberOfItems);

                for (int i = 0; i < numberOfItems; i++) {
                    String fileName = (String) inArchive.getProperty(i, PropID.PATH);
                    logger.debug("📝 扫描7z条目[{}/{}] - OriginalFileName: {}", i + 1, numberOfItems, fileName);
                    
                    // 🔑 编码检测：处理中文文件名乱码
                    if (fileName != null && fileUtils.isMessyCode(fileName)) {
                        try {
                            String originalFileName = fileName;
                            fileName = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), "GBK");
                            logger.info("🔧 检测到乱码并修复文件名 - Original: {}, Fixed: {}", originalFileName, fileName);
                        } catch (Exception e) {
                            logger.warn("⚠️ 文件名编码转换失败 - FileName: {}", fileName, e);
                        }
                    }
                    
                    Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                    Boolean isEncrypted = (Boolean) inArchive.getProperty(i, PropID.ENCRYPTED);
                    logger.debug("📝 条目属性[{}/{}] - FileName: {}, IsFolder: {}, IsEncrypted: {}", 
                        i + 1, numberOfItems, fileName, isFolder, isEncrypted);

                    // 检查是否加密
                    if (Boolean.TRUE.equals(isEncrypted) && password == null) {
                        logger.warn("🔒 7z文件已加密，需要密码 - File: {}, EncryptedItem: {}", archiveFile.getName(), fileName);
                        return ExtractResult.passwordRequired("7z", "7z文件已加密，需要密码");
                    }

                    // 检查是否是目标文件
                    if (fileName != null && (fileName.equals(targetFilePath)
                            || fileName.equals(targetFilePath.replace('\\', '/')))) {
                        logger.info("✅ 找到目标文件 - TargetFilePath: {}, MatchedFileName: {}, Index: {}", targetFilePath, fileName, i);
                        
                        if (Boolean.TRUE.equals(isFolder)) {
                            logger.warn("❌ 目标路径是目录而非文件 - TargetFilePath: {}", targetFilePath);
                            return ExtractResult.failure("目标路径是目录，不是文件: " + targetFilePath);
                        }

                        // 检查文件大小
                        Long size = safeLongCast(inArchive.getProperty(i, PropID.SIZE));
                        logger.debug("📊 目标文件大小 - FileName: {}, Size: {} bytes, MaxAllowed: {} bytes", fileName, size, maxFileSize);
                        if (size != null && size > maxFileSize) {
                            logger.warn("❌ 文件太大，超过限制 - FileName: {}, Size: {}, MaxAllowed: {}", fileName, size, maxFileSize);
                            return ExtractResult.failure("文件太大，超过限制: " + size + " bytes");
                        }

                        // ⚠️ 关键修复：检查exe等可执行文件，防止native崩溃
                        if (isExecutableFile(fileName)) {
                            logger.warn("⚠️ 检测到可执行文件，跳过解压以防止native崩溃 - FileName: {}", fileName);
                            return ExtractResult.failure("不支持预览可执行文件（.exe/.dll/.so等），以防止系统崩溃");
                        }

                        // 解压文件
                        logger.info("🔧 开始解压目标文件 - FileName: {}, TempDir: {}", fileName, tempDir);
                        Path extractedFile = extract7zEntry(inArchive, i, fileName, tempDir);
                        String relativePath = fileUtils.getRelativePath(extractedFile);

                        logger.info("✅ 7z文件提取成功 - FileName: {}, ExtractedPath: {}, RelativePath: {}", 
                            fileName, extractedFile.toAbsolutePath(), relativePath);
                        return ExtractResult.success(relativePath, extractedFile.toString());
                    }
                }

                logger.warn("❌ 在压缩包中未找到指定文件 - TargetFilePath: {}, TotalItemsScanned: {}", targetFilePath, numberOfItems);
                return ExtractResult.failure("在压缩包中未找到指定文件: " + targetFilePath);

            } finally {
                logger.debug("🔒 关闭7z归档 - File: {}", archiveFile.getName());
                inArchive.close();
            }

        } catch (SevenZipException e) {
            String errorMsg = e.getMessage();
            logger.error("💥 7z文件解压异常(SevenZipException) - File: {}, ErrorMessage: {}", archiveFile.getName(), errorMsg, e);

            // 判断是否为密码相关错误
            if (errorMsg != null && (errorMsg.toLowerCase().contains("password") ||
                    errorMsg.toLowerCase().contains("encrypted") ||
                    errorMsg.toLowerCase().contains("wrong password"))) {
                if (password == null) {
                    logger.warn("🔒 7z文件已加密，需要密码 - File: {}", archiveFile.getName());
                    return ExtractResult.passwordRequired("7z", "7z文件已加密，需要密码");
                } else {
                    logger.warn("❌ 7z密码错误 - File: {}", archiveFile.getName());
                    return ExtractResult.wrongPassword("7z", "7z密码错误，请重试");
                }
            }

            logger.error("💥 7z文件解压异常 - File: {}", archiveFile.getName(), e);
            // 🔧 尝试外部 7z 命令 fallback
            logger.info("🔄 SevenZipException后尝试fallback到外部7z命令 - File: {}", archiveFile.getName());
            return tryExtractWith7zCommand(archiveFile, targetFilePath, tempDir, password);

        } catch (Exception e) {
            logger.error("💥 7z文件解压失败(通用异常) - File: {}, ErrorType: {}, ErrorMessage: {}", 
                archiveFile.getName(), e.getClass().getName(), e.getMessage(), e);
            return ExtractResult.failure("7z文件解压失败: " + e.getMessage());
        }
    }
    
    /**
     * 使用外部 7z 命令提取文件（fallback 方案）
     * 当 native 库失败时使用
     */
    private ExtractResult tryExtractWith7zCommand(File archiveFile, String targetFilePath, 
                                                    Path tempDir, String password) {
        logger.info("🔧 尝试使用外部7z命令提取文件 - File: {}, Target: {}", archiveFile.getName(), targetFilePath);
        
        // 检查外部 7z 命令是否可用
        if (!EnvironmentUtils.isExternal7zAvailable()) {
            logger.warn("❌ 外部7z命令不可用，无法fallback - File: {}", archiveFile.getName());
            return ExtractResult.failure(
                "该7z文件包含SevenZipJBinding库无法安全处理的特殊结构，" +
                "且外部7z命令不可用。建议使用其他解压工具。");
        }
        
        try {
            // 构建提取命令: 7z e -o<outdir> -p<password> archive.7z targetFile
            List<String> cmd = new ArrayList<>();
            cmd.add("7z");
            cmd.add("e");  // extract
            cmd.add("-o" + tempDir.toString());
            cmd.add("-y");  // yes to all prompts
            if (password != null && !password.isEmpty()) {
                cmd.add("-p" + password);
                logger.debug("🔓 使用密码执行外部7z命令 - File: {}", archiveFile.getName());
            }
            cmd.add(archiveFile.getAbsolutePath());
            cmd.add(targetFilePath);
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            
            logger.info("💻 执行外部7z命令: {}", String.join(" ", cmd));
            Process p = pb.start();
            
            boolean finished = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                logger.error("❌ 外部7z命令执行超时 - File: {}", archiveFile.getName());
                return ExtractResult.failure("外部 7z 命令超时");
            }
            
            int exitCode = p.exitValue();
            logger.debug("📊 外部7z命令执行完成 - ExitCode: {}, File: {}", exitCode, archiveFile.getName());
            
            if (exitCode != 0) {
                // 读取错误输出
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    logger.warn("❌ 外部7z命令执行失败 - File: {}, ExitCode: {}, Output: {}", 
                        archiveFile.getName(), exitCode, output.toString());
                }
                return ExtractResult.failure("外部 7z 命令执行失败，退出码: " + exitCode);
            }
            
            // 检查提取的文件是否存在
            Path extractedFile = tempDir.resolve(new File(targetFilePath).getName());
            logger.debug("🔍 检查提取的文件 - ExtractedFilePath: {}, Exists: {}", 
                extractedFile, Files.exists(extractedFile));
            
            if (!Files.exists(extractedFile)) {
                logger.error("❌ 外部7z命令执行成功，但未找到提取的文件 - ExpectedPath: {}", extractedFile);
                return ExtractResult.failure("外部 7z 命令执行成功，但未找到提取的文件");
            }
            
            String relativePath = fileUtils.getRelativePath(extractedFile);
            logger.info("✅ 使用外部7z命令提取成功 - File: {}, ExtractedPath: {}, RelativePath: {}", 
                targetFilePath, extractedFile, relativePath);
            return ExtractResult.success(relativePath, extractedFile.toString());
            
        } catch (Exception e) {
            logger.error("💥 外部7z命令执行异常 - File: {}, ErrorType: {}, ErrorMessage: {}", 
                archiveFile.getName(), e.getClass().getName(), e.getMessage(), e);
            return ExtractResult.failure("外部 7z 命令执行异常: " + e.getMessage());
        }
    }
    
    /**
     * 解压TAR条目到指定目录
     */
    private Path extractTarEntry(TarArchiveInputStream tarInput, ArchiveEntry entry, Path tempDir) throws IOException {
        String entryName = entry.getName();
        Path targetPath = tempDir.resolve(entryName).normalize();

        // 安全检查：防止路径遍历攻击
        if (!targetPath.startsWith(tempDir)) {
            throw new IOException("不安全的文件路径: " + entryName);
        }

        // 检查文件是否已经存在
        if (Files.exists(targetPath)) {
            long existingSize = Files.size(targetPath);
            long archiveSize = entry.getSize();

            // 如果文件存在且大小一致，则跳过解压
            if (existingSize == archiveSize) {
                logger.debug("文件已存在且大小一致，跳过解压: {}", targetPath);
                return targetPath;
            } else {
                logger.info("文件已存在但大小不一致，重新解压: {} (现有: {}, 压缩包: {})",
                        targetPath, existingSize, archiveSize);
            }
        }

        // 创建父目录（如果目录已存在，Files.createDirectories会自动跳过）
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.debug("创建目录: {}", parentDir);
        }

        // 写入文件内容
        try (OutputStream output = Files.newOutputStream(targetPath);
                BufferedOutputStream bufferedOutput = new BufferedOutputStream(output, bufferSize)) {

            IOUtils.copy(tarInput, bufferedOutput);
        }

        return targetPath;
    }

    /**
     * 解压RAR条目到指定目录
     */
    private Path extractRarEntry(Archive archive, FileHeader fileHeader, Path tempDir) throws Exception {
        String entryName = fileHeader.getFileName();
        Path targetPath = tempDir.resolve(entryName).normalize();

        // 安全检查：防止路径遍历攻击
        if (!targetPath.startsWith(tempDir)) {
            throw new IOException("不安全的文件路径: " + entryName);
        }

        // 检查文件是否已经存在
        if (Files.exists(targetPath)) {
            long existingSize = Files.size(targetPath);
            long archiveSize = fileHeader.getUnpSize();

            // 如果文件存在且大小一致，则跳过解压
            if (existingSize == archiveSize) {
                logger.debug("文件已存在且大小一致，跳过解压: {}", targetPath);
                return targetPath;
            } else {
                logger.info("文件已存在但大小不一致，重新解压: {} (现有: {}, 压缩包: {})",
                        targetPath, existingSize, archiveSize);
            }
        }

        // 创建父目录（如果目录已存在，Files.createDirectories会自动跳过）
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.debug("创建目录: {}", parentDir);
        }

        // 写入文件内容
        try (OutputStream output = Files.newOutputStream(targetPath);
                BufferedOutputStream bufferedOutput = new BufferedOutputStream(output, bufferSize)) {

            archive.extractFile(fileHeader, bufferedOutput);
        }

        return targetPath;
    }

    /**
     * 解压7z条目到指定目录
     */
    private Path extract7zEntry(IInArchive inArchive, int index, String fileName, Path tempDir) throws Exception {
        logger.debug("🔧 开始解压7z条目 - Index: {}, FileName: {}, TempDir: {}", index, fileName, tempDir);
        
        Path targetPath = tempDir.resolve(fileName).normalize();
        logger.debug("📋 目标路径 - TargetPath: {}", targetPath.toAbsolutePath());

        // 安全检查：防止路径遍历攻击
        if (!targetPath.startsWith(tempDir)) {
            logger.error("❌ 不安全的文件路径 - FileName: {}, TargetPath: {}, TempDir: {}", fileName, targetPath, tempDir);
            throw new IOException("不安全的文件路径: " + fileName);
        }

        // 检查文件是否已经存在
        Long size = safeLongCast(inArchive.getProperty(index, PropID.SIZE));
        if (Files.exists(targetPath) && size != null) {
            long existingSize = Files.size(targetPath);

            // 如果文件存在且大小一致，则跳过解压
            if (existingSize == size) {
                logger.debug("✅ 文件已存在且大小一致，跳过解压 - TargetPath: {}, Size: {} bytes", targetPath, size);
                return targetPath;
            } else {
                logger.info("⚠️ 文件已存在但大小不一致，重新解压 - TargetPath: {}, ExistingSize: {} bytes, ArchiveSize: {} bytes",
                        targetPath, existingSize, size);
            }
        }

        // 创建父目录（如果目录已存在，Files.createDirectories会自动跳过）
        Path parentDir = targetPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
            logger.debug("📁 创建父目录 - ParentDir: {}", parentDir);
        }

        // 写入文件内容
        logger.debug("💾 开始写入文件内容 - TargetPath: {}, ExpectedSize: {} bytes", targetPath, size);
        try (OutputStream output = Files.newOutputStream(targetPath);
                BufferedOutputStream bufferedOutput = new BufferedOutputStream(output, bufferSize)) {

            inArchive.extractSlow(index, new ISequentialOutStream() {
                private long bytesWritten = 0;
                
                @Override
                public int write(byte[] data) throws SevenZipException {
                    try {
                        bufferedOutput.write(data);
                        bytesWritten += data.length;
                        return data.length;
                    } catch (IOException e) {
                        logger.error("❌ 写入文件失败 - TargetPath: {}, BytesWritten: {} bytes, Error: {}", 
                            targetPath, bytesWritten, e.getMessage());
                        throw new SevenZipException("写入文件失败: " + e.getMessage());
                    }
                }
            });
        }

        long finalSize = Files.size(targetPath);
        logger.info("✅ 7z条目解压完成 - FileName: {}, TargetPath: {}, FinalSize: {} bytes", 
            fileName, targetPath, finalSize);
        return targetPath;
    }

    /**
     * 安全地将Object转换为Long类型
     * 处理Integer和Long之间的类型转换问题
     */
    private Long safeLongCast(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            logger.warn("Unexpected value type for long conversion: {} ({})",
                    value.getClass().getSimpleName(), value);
            return null;
        }
    }

    /**
     * 支持的压缩文件扩展名（使用FileTypeMapper中的数据）
     */
    private String[] getArchiveExtensions() {
        if (fileTypeMapper != null) {
            return fileTypeMapper.getArchiveExtensionsWithDotPrefix(com.basemetas.fileview.preview.config.FileCategory.ARCHIVE);
        }
        // 如果无法获取FileTypeMapper中的扩展名，则使用默认的
        return new String[] { ".zip", ".rar", ".7z", ".tar", ".tar.gz", ".tgz", ".jar", ".war", ".ear" };
    }

    /**
     * 综合判断是否需要解压文件
     * 
     * @param fullPath 完整路径
     * @return 解压决策结果对象
     */
    public ExtractionDecision shouldExtract(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            return new ExtractionDecision(false, "路径为空");
        }

        // 检查文件是否已经存在
        File file = new File(fullPath);
        if (file.exists() && file.isFile()) {
            return new ExtractionDecision(false, "文件已存在于文件系统中");
        }

        // 使用parseArchivePath方法来判断是否是有效的压缩包路径格式
        try {
            ArchivePathInfo pathInfo = parseArchivePath(fullPath);
            // 如果能成功解析，说明这是一个有效的压缩包路径格式
            // 进一步检查压缩文件是否存在
            File archiveFile = new File(pathInfo.getArchivePath());
            if (archiveFile.exists() && archiveFile.isFile()) {
                return new ExtractionDecision(true, "有效的压缩包路径格式");
            }
        } catch (IllegalArgumentException e) {
            // 解析失败，说明不是有效的压缩包路径格式
        }

        // 普通文件路径，但文件不存在
        return new ExtractionDecision(false, "普通文件路径但文件不存在");
    }

        
    /**
     * 压缩包密码异常
     */
    public static class ArchivePasswordException extends RuntimeException {
        private final String errorCode;

        public ArchivePasswordException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * 压缩包解压异常
     */
    public static class ArchiveExtractionException extends RuntimeException {
        public ArchiveExtractionException(String message) {
            super(message);
        }
    }

    /**
     * 不支持的文件格式异常
     */
    public static class UnsupportedFileFormatException extends RuntimeException {
        private final String errorCode;

        public UnsupportedFileFormatException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}