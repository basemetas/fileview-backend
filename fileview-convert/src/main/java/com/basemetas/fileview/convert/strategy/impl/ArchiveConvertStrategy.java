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
package com.basemetas.fileview.convert.strategy.impl;

import com.basemetas.fileview.convert.annotation.ConvertStrategy;
import com.basemetas.fileview.convert.common.ValidateAndNormalized;
import com.basemetas.fileview.convert.strategy.model.ArchiveEntryInfo;
import com.basemetas.fileview.convert.strategy.model.ArchiveInfo;
import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import com.basemetas.fileview.convert.utils.ArchiveUtils;
import com.basemetas.fileview.convert.utils.DateTimeUtils; // 修改导入
import com.basemetas.fileview.convert.utils.FileUtils;
import com.basemetas.fileview.convert.service.SevenZipParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import net.lingala.zip4j.ZipFile;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

/**
 * 压缩文件转换策略实现类
 * 
 * 支持多种压缩格式：
 * - ZIP/JAR/WAR/EAR
 * - TAR/TAR.GZ/TGZ
 * - RAR
 * - 7Z
 * 
 * 特性：
 * - 解析压缩文件结构
 * - 提取文件列表和元数据
 * - 转换为JSON格式便于前端展示
 * - 支持大文件处理优化
 * - 智能编码检测（解决中文乱码问题）
 * - 支持递归读取嵌套压缩文件结构
 * 
 * @author 夫子
 */
@ConvertStrategy(category = FileCategory.ARCHIVE, description = "压缩文件处理（ZIP/RAR/7Z）", priority = 100)
public class ArchiveConvertStrategy implements FileConvertStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveConvertStrategy.class);

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private ValidateAndNormalized validateAndNormalized;
    
    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private ArchiveUtils archiveUtils;

    @Autowired
    private SevenZipParserService sevenZipParserService;

 

    @PostConstruct
    public void init() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.ARCHIVE);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.ARCHIVE);

        logger.info("压缩文件转换策略初始化完成 - 类型: ARCHIVE, 支持源格式({}种): {}",
                sourceFormats.size(), sourceFormats);
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);
    }

    @Override
    public boolean convert(String filePath, String targetPath) {
        return convert(filePath, targetPath, "archive_content", "json");
    }

    @Override
    public boolean convert(String filePath, String targetPath, String targetFileName, String targetFormat) {
        return convertWithParams(filePath, targetPath, targetFileName, targetFormat, null);
    }

    @Override
    public boolean convertWithParams(String filePath, String targetPath, String targetFileName,
            String targetFormat, Map<String, Object> convertParams) {
        logger.info("开始压缩文件转换 - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}",
                filePath, targetPath, targetFileName, targetFormat);

        // 提取密码参数
        String password = null;
        String fileId = null;
        if (convertParams != null) {
            if (convertParams.containsKey("password")) {
                password = (String) convertParams.get("password");
                logger.info("🔑 检测到密码参数，将用于解析加密压缩包");
            }
            if (convertParams.containsKey("fileId")) {
                fileId = (String) convertParams.get("fileId");
                logger.info("🆔 检测到 fileId 参数: {}", fileId);
            }
        }

        try {
            // 1. 基本参数验证
            if (filePath == null || filePath.trim().isEmpty()) {
                logger.error("源文件路径不能为空");
                return false;
            }
            if (targetPath == null || targetPath.trim().isEmpty()) {
                logger.error("目标路径不能为空");
                return false;
            }
            if (targetFileName == null || targetFileName.trim().isEmpty()) {
                logger.error("目标文件名不能为空");
                return false;
            }

            // 2. 验证目标格式（压缩文件仅支持转换为JSON格式）
            if (!"json".equalsIgnoreCase(targetFormat)) {
                logger.error("压缩文件仅支持转换为JSON格式，当前目标格式: {}", targetFormat);
                return false;
            }

            // 3. 验证源文件存在性
            File sourceFile = new File(filePath);
            if (!sourceFile.exists()) {
                logger.error("源文件不存在: {}", filePath);
                return false;
            }
            if (!sourceFile.isFile()) {
                logger.error("源路径不是文件: {}", filePath);
                return false;
            }

            String correctedSourcePath = sourceFile.getAbsolutePath();

            // 4. 检测压缩文件格式
            String fileFormat = fileUtils.detectArchiveFormat(correctedSourcePath);
            if (!isFormatSupported(fileFormat)) {
                logger.error("不支持的压缩格式: {}", fileFormat);
                return false;
            }

            // 5. 构建目标文件完整路径
            String targetFilePath = validateAndNormalized.buildTargetFilePathEnhanced(targetPath, targetFileName,
                    targetFormat);

            // 6. 确保目标目录存在
            if (!validateAndNormalized.ensureTargetDirectory(targetFilePath)) {
                logger.error("无法创建或访问目标目录");
                return false;
            }

            // 7. 执行压缩文件转换（传递密码和 fileId）
            return convertArchiveToJson(correctedSourcePath, targetFilePath, password, fileId);

        } catch (Exception e) {
            logger.error("压缩文件转换异常 - 源文件: {}, 目标路径: {}", filePath, targetPath, e);
            return false;
        }
    }

    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.ARCHIVE, sourceFormat, targetFormat);
    }

    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.ARCHIVE);
    }

    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.ARCHIVE);
    }

    /**
     * 获取支持的压缩文件格式列表
     */
    public Set<String> getSupportedFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.ARCHIVE);
    }

    /**
     * 检查格式是否支持
     */
    private boolean isFormatSupported(String format) {
        if (format == null) {
            return false;
        }
        Set<String> supportedFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.ARCHIVE);
        return supportedFormats.contains(format.toLowerCase());
    }

    /**
     * 将压缩文件转换为JSON格式（带密码支持）
     * 优化逻辑：仅读取压缩包目录结构，不解压文件内容
     * 
     * @param archivePath 压缩文件路径
     * @param jsonPath    JSON输出路径
     * @param password    压缩包密码（可为null）
     * @param fileId      文件ID（用于创建解压目录，可为null）
     * @return 转换是否成功
     */
    private boolean convertArchiveToJson(String archivePath, String jsonPath, String password, String fileId) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Starting archive to JSON conversion: {} -> {}", archivePath, jsonPath);
            if (password != null && !password.isEmpty()) {
                logger.info("🔐 使用密码读取加密压缩包目录结构");
            }

            File archiveFile = new File(archivePath);
            String format = fileUtils.detectArchiveFormat(archivePath);

            // 如果没有提供 fileId，从文件名生成
            if (fileId == null || fileId.trim().isEmpty()) {
                String archiveName = archiveFile.getName();
                int dotIndex = archiveName.lastIndexOf('.');
                fileId = "archive_" + (dotIndex > 0 ? archiveName.substring(0, dotIndex) : archiveName);
                logger.info("🆔 未提供 fileId，从文件名生成: {}", fileId);
            }

            // 优化：仅读取压缩包目录结构，不解压内容
            ArchiveInfo archiveInfo = parseArchiveStructure(archivePath, format, password, fileId);
            if (archiveInfo == null) {
                logger.error("❌ 读取压缩包目录结构失败: {}", archivePath);
                return false;
            }

            long parseTime = System.currentTimeMillis() - startTime;
            logger.info("✅ 压缩包目录结构读取成功，耗时: {}ms", parseTime);

            // 转换为JSON并写入文件
            archiveUtils.writeArchiveInfoToJson(archiveInfo, jsonPath);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Archive conversion completed successfully - 格式: {}, 耗时: {}ms", format, duration);
            return true;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed to convert archive to JSON, 耗时: {}ms", duration, e);
            return false;
        }
    }

    /**
     * 解析压缩包目录结构（不解压内容）
     * 根据不同格式调用对应的解析方法
     * 
     * @param archivePath 压缩文件路径
     * @param format      压缩格式
     * @param password    密码（可为null）
     * @param fileId      文件ID（保留参数以保持接口兼容）
     * @return 压缩包信息
     */
    private ArchiveInfo parseArchiveStructure(String archivePath, String format, String password, String fileId) {
        try {
            ArchiveInfo archiveInfo;

            switch (format) {
                case "zip":
                case "jar":
                case "war":
                case "ear":
                    archiveInfo = parseZipArchive(archivePath, password);
                    break;
                case "tar":
                    archiveInfo = parseTarArchive(archivePath);
                    break;
                case "tar.gz":
                case "tgz":
                    archiveInfo = parseTarGzArchive(archivePath);
                    break;
                case "rar":
                    archiveInfo = parseRarArchive(archivePath, password);
                    break;
                case "7z":
                    archiveInfo = parse7zArchive(archivePath, password);
                    break;
                default:
                    logger.error("不支持的压缩格式: {}", format);
                    return null;
            }

            return archiveInfo;

        } catch (Exception e) {
            logger.error("解析压缩包目录结构失败: {}", archivePath, e);
            return null;
        }
    }

    /**
     * 解析ZIP格式文件（带密码支持）
     * 
     * @param archivePath ZIP文件路径
     * @param password    密码（可为null）
     * @return 压缩包信息
     * @throws IOException 解析失败
     */
    private ArchiveInfo parseZipArchive(String archivePath, String password) throws IOException {
        long startTime = System.currentTimeMillis();
        File archiveFile = new File(archivePath);
        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile);
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        // 先检测ZIP文件是否加密
        boolean isEncrypted = isZipFileEncrypted(archiveFile);

        // 如果文件加密，必须使用zip4j库解析（即使有密码，因为标准ZipInputStream不支持加密文件）
        if (isEncrypted) {
            try {
                logger.info("🔑 检测到加密ZIP文件，使用zip4j解析: {}", archivePath);
                entries = parseZipWithPassword(archiveFile, password);
                info.setEntries(entries);
                ArchiveUtils.calculateStatistics(info);
                logger.info("✅ 成功解析加密ZIP文件，共解析 {} 个条目，耗时 {}ms",
                        entries.size(), System.currentTimeMillis() - startTime);
                return info;
            } catch (Exception e) {
                logger.error("❌ 使用zip4j解析加密ZIP文件失败: {}", archivePath, e);
                if (password == null || password.isEmpty()) {
                    throw new IOException("ZIP文件已加密，但未提供密码", e);
                } else {
                    throw new IOException("无法使用提供的密码解析ZIP文件，可能密码错误或文件损坏", e);
                }
            }
        }

        // 未加密，使用标准方式解析
        // 优化：智能编码检测，优先尝试最可能的编码
        String[] encodings = fileUtils.detectOptimalEncodingOrder(archiveFile);

        for (String encoding : encodings) {
            try {
                logger.debug("尝试使用编码 {} 解析ZIP文件: {}", encoding, archivePath);
                entries = parseZipWithEncodingOptimized(archiveFile, encoding);
                logger.info("成功使用编码 {} 解析ZIP文件，共解析 {} 个条目，耗时 {}ms",
                        encoding, entries.size(), System.currentTimeMillis() - startTime);
                break;
            } catch (Exception e) {
                logger.debug("使用编码 {} 解析失败: {}", encoding, e.getMessage());
                if ("ISO-8859-1".equals(encoding)) {
                    // 如果所有编码都失败，抛出异常
                    logger.error("所有编码方式都无法解析ZIP文件: {}", archivePath);
                    throw new IOException("无法解析ZIP文件，可能文件损坏或包含不支持的字符编码", e);
                }
            }
        }

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    /**
     * 使用zip4j解析加密的ZIP文件
     * 支持智能字符编码检测，解决中文乱码问题
     */
    private List<ArchiveEntryInfo> parseZipWithPassword(File archiveFile, String password) throws Exception {
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        // 🔧 尝试多种编码解析ZIP文件，解决中文乱码问题
        String[] encodings = fileUtils.detectOptimalEncodingOrder(archiveFile);

        for (String encoding : encodings) {
            try {
                // 创建ZipFile并设置字符集
               ZipFile zipFile = new ZipFile(archiveFile);
                zipFile.setCharset(java.nio.charset.Charset.forName(encoding));

                if (zipFile.isEncrypted()) {
                    if (password == null || password.trim().isEmpty()) {
                        throw new IOException("ZIP文件已加密，但未提供密码");
                    }
                    zipFile.setPassword(password.toCharArray());
                }

                // 尝试获取文件头以验证编码是否正确
                List<net.lingala.zip4j.model.FileHeader> fileHeaders = zipFile.getFileHeaders();

                // 检查是否有乱码（简单检查：如果文件名包含乱码字符，尝试下一个编码）
                boolean hasGarbledText = false;
                for (net.lingala.zip4j.model.FileHeader fileHeader : fileHeaders) {
                    String fileName = fileHeader.getFileName();
                    // 检测乱码：包含特殊字符但不是正常的Unicode字符
                    if (fileName != null && fileUtils.isMessyCode(fileName)) {
                        hasGarbledText = true;
                        logger.debug("检测到乱码字符，尝试下一个编码 - 当前编码: {}, 文件名示例: {}", encoding, fileName);
                        break;
                    }
                }

                if (!hasGarbledText) {
                    // 编码正确，解析所有条目
                    logger.info("✅ 成功使用编码 {} 解析ZIP文件: {}", encoding, archiveFile.getName());
                    for (net.lingala.zip4j.model.FileHeader fileHeader : fileHeaders) {
                        ArchiveEntryInfo entryInfo = new ArchiveEntryInfo();
                        entryInfo.setName(fileHeader.getFileName());
                        entryInfo.setSize(fileHeader.getUncompressedSize());
                        entryInfo.setCompressedSize(fileHeader.getCompressedSize());
                        entryInfo.setDirectory(fileHeader.isDirectory());

                        // 设置修改时间
                        long lastModified = fileHeader.getLastModifiedTimeEpoch();
                        if (lastModified > 0) {
                            entryInfo.setLastModified(new java.util.Date(lastModified));
                        }

                        // 设置加密状态
                        entryInfo.setEncrypted(fileHeader.isEncrypted());

                        entries.add(entryInfo);
                    }
                    return entries;
                }

            } catch (Exception e) {
                logger.debug("使用编码 {} 解析ZIP失败: {}", encoding, e.getMessage());
                if ("ISO-8859-1".equals(encoding)) {
                    // 最后一个编码也失败了，抛出异常
                    throw new IOException("无法使用任何编码解析ZIP文件，可能文件损坏", e);
                }
            }
        }

        return entries;
    }

    /**
     * 检测ZIP文件是否加密
     * 使用zip4j库检测，避免标准ZipInputStream在遇到加密文件时抛出异常
     */
    private boolean isZipFileEncrypted(File archiveFile) {
        try {
            ZipFile zipFile = new ZipFile(archiveFile);
            return zipFile.isEncrypted();
        } catch (Exception e) {
            logger.warn("检测ZIP加密状态时出错: {}, 文件: {}", e.getMessage(), archiveFile.getAbsolutePath());
            // 如果检测失败，假设未加密，让后续流程处理
            return false;
        }
    }

    /**
     * 优化版本：使用指定编码解析ZIP文件
     */
    private List<ArchiveEntryInfo> parseZipWithEncodingOptimized(File archiveFile, String encoding) throws IOException {
        long startTime = System.currentTimeMillis();
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        // 优化：使用BufferedInputStream提高IO性能
        try (FileInputStream fis = new FileInputStream(archiveFile);
                BufferedInputStream bis = new BufferedInputStream(fis, 8192);
                ZipInputStream zis = new ZipInputStream(bis, java.nio.charset.Charset.forName(encoding))) {

            ZipEntry entry;
            int entryCount = 0;
            int calculatedSizeCount = 0; // 记录需要计算大小的条目数量
            long totalCalculateTime = 0; // 记录计算大小的总时间

            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                
                // 安全获取文件名，处理可能的编码问题
                String entryName = getEntryNameSafely(entry);
                
                // 🔑 关键过滤：跳过macOS压缩包的元数据文件
                if (fileUtils.shouldSkipMacOSMetadataFile(entryName)) {
                    logger.debug("⏭️ 跳过macOS元数据文件: {}", entryName);
                    continue;
                }
                
                ArchiveEntryInfo entryInfo = new ArchiveEntryInfo();
                entryInfo.setName(entryName);

                // 🔑 关键改进：尝试获取实际文件大小
                long size = entry.getSize();
                if (size < 0 && !entry.isDirectory()) {
                    // 如果大小为负数且不是目录，尝试通过读取内容来计算大小
                    long calcStartTime = System.currentTimeMillis();
                    size = calculateEntrySize(zis);
                    long calcDuration = System.currentTimeMillis() - calcStartTime;
                    totalCalculateTime += calcDuration;
                    calculatedSizeCount++;
                    logger.debug("ZIP条目大小计算 - 文件: {}, 大小: {} 字节, 耗时: {}ms", entryName, size, calcDuration);
                }
                entryInfo.setSize(size);

                long compressedSize = entry.getCompressedSize();
                if (compressedSize < 0) {
                    compressedSize = 0; // 如果压缩后大小为负数，设置为0
                }
                entryInfo.setCompressedSize(compressedSize);

                entryInfo.setDirectory(entry.isDirectory());
                entryInfo.setLastModified(entry.getLastModifiedTime() != null
                        ? DateTimeUtils.toUTCDate(new Date(entry.getLastModifiedTime().toMillis()))
                        : null);
                entryInfo.setCrc(entry.getCrc());
                entryInfo.setMethod(fileUtils.getCompressionMethod(entry.getMethod()));

                if (!entry.isDirectory()) {
                    entryInfo.setFileType(fileUtils.determineFileType(entryName));

                    // 优化：更严格的文件大小限制，避免处理大文件
                    if (fileUtils.isTextFile(entryName) && size > 0 && size <= 1024) {
                        try {
                            String content = readEntryContentOptimized(zis, (int) size);
                            if (!content.isEmpty()) {
                                entryInfo.setContentPreview(
                                        content.length() > 200 ? content.substring(0, 200) + "..." : content);
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to read content preview for {}: {}", entryName, e.getMessage());
                        }
                    }
                }

                entries.add(entryInfo);

                // 优化：每处理100个文件输出一次进度日志
                if (entryCount % 100 == 0) {
                    logger.debug("已处理 {} 个条目...", entryCount);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("ZIP文件解析完成 - 文件: {}, 条目数: {}, 需要计算大小的条目数: {}, 计算大小总耗时: {}ms, 总耗时: {}ms",
                    archiveFile.getName(), entryCount, calculatedSizeCount, totalCalculateTime, duration);
        }

        return entries;
    }

    /**
     * 通过读取条目内容来计算实际大小
     */
    private long calculateEntrySize(ZipInputStream zis) {
        long startTime = System.currentTimeMillis();
        try {
            long size = 0;
            byte[] buffer = new byte[8192];
            int bytesRead;

            // 读取条目内容并计算大小
            while ((bytesRead = zis.read(buffer)) != -1) {
                size += bytesRead;
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.debug("计算ZIP条目大小完成 - 大小: {} 字节, 耗时: {}ms", size, duration);

            return size;
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("计算条目大小时出错: {}, 耗时: {}ms", e.getMessage(), duration);
            return 0;
        }
    }

    /**
     * 解析TAR格式文件
     */
    private ArchiveInfo parseTarArchive(String archivePath) throws IOException {
        long startTime = System.currentTimeMillis();
        File archiveFile = new File(archivePath);
        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile);
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(archiveFile);
                TarArchiveInputStream tis = new TarArchiveInputStream(fis)) {

            ArchiveEntry entry;
            int entryCount = 0;
            int negativeSizeCount = 0; // 记录大小为负数的条目数量

            while ((entry = tis.getNextEntry()) != null) {
                entryCount++;
                
                // 🔑 关键过滤：跳过macOS元数据文件
                String entryName = entry.getName();
                if (fileUtils.shouldSkipMacOSMetadataFile(entryName)) {
                    logger.debug("⏭️ 跳过macOS元数据文件: {}", entryName);
                    continue;
                }
                
                ArchiveEntryInfo entryInfo = archiveUtils.createArchiveEntryInfo(entry);

                // 统计大小为负数的条目
                if (entryInfo.getSize() < 0) {
                    negativeSizeCount++;
                }

                entries.add(entryInfo);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("TAR文件解析完成 - 文件: {}, 条目数: {}, 大小为负数的条目数: {}, 耗时: {}ms",
                    archiveFile.getName(), entryCount, negativeSizeCount, duration);
        }

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    /**
     * 解析TAR.GZ格式文件
     */
    private ArchiveInfo parseTarGzArchive(String archivePath) throws IOException {
        long startTime = System.currentTimeMillis();
        File archiveFile = new File(archivePath);
        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile);
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(archiveFile);
                GzipCompressorInputStream gis = new GzipCompressorInputStream(fis);
                TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

            ArchiveEntry entry;
            int entryCount = 0;
            int negativeSizeCount = 0; // 记录大小为负数的条目数量

            while ((entry = tis.getNextEntry()) != null) {
                entryCount++;
                
                // 🔑 关键过滤：跳过macOS元数据文件
                String entryName = entry.getName();
                if (fileUtils.shouldSkipMacOSMetadataFile(entryName)) {
                    logger.debug("⏭️ 跳过macOS元数据文件: {}", entryName);
                    continue;
                }
                
                ArchiveEntryInfo entryInfo = archiveUtils.createArchiveEntryInfo(entry);

                // 统计大小为负数的条目
                if (entryInfo.getSize() < 0) {
                    negativeSizeCount++;
                }

                entries.add(entryInfo);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("TAR.GZ文件解析完成 - 文件: {}, 条目数: {}, 大小为负数的条目数: {}, 耗时: {}ms",
                    archiveFile.getName(), entryCount, negativeSizeCount, duration);
        }

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    /**
     * 解析RAR格式文件（带密码支持）
     * 
     * @param archivePath RAR文件路径
     * @param password    密码（可为null）
     * @return 压缩包信息
     * @throws Exception 解析失败
     */
    private ArchiveInfo parseRarArchive(String archivePath, String password) throws Exception {
        long startTime = System.currentTimeMillis();
        File archiveFile = new File(archivePath);
        ArchiveInfo info = archiveUtils.createArchiveInfo(archiveFile);
        List<ArchiveEntryInfo> entries = new ArrayList<>();

        Archive archive = null;
        try {
            if (password != null && !password.isEmpty()) {
                logger.info("🔑 使用密码解析RAR文件");
                archive = new Archive(archiveFile, password);
            } else {
                archive = new Archive(archiveFile);
            }

            int entryCount = 0;
            int negativeSizeCount = 0; // 记录大小为负数的条目数量

            for (FileHeader fileHeader : archive.getFileHeaders()) {
                entryCount++;
                
                // 🔑 关键过滤：跳过macOS元数据文件
                String fileName = fileHeader.getFileName();
                if (fileUtils.shouldSkipMacOSMetadataFile(fileName)) {
                    logger.debug("⏭️ 跳过macOS元数据文件: {}", fileName);
                    continue;
                }
                
                ArchiveEntryInfo entryInfo = new ArchiveEntryInfo();
                entryInfo.setName(fileName);

                // 🔑 关键改进：尝试获取实际文件大小
                long size = fileHeader.getUnpSize();
                // 对于RAR文件，我们信任库返回的大小信息
                entryInfo.setSize(size);

                // 统计大小为负数的条目
                if (size < 0) {
                    negativeSizeCount++;
                }

                long compressedSize = fileHeader.getPackSize();
                if (compressedSize < 0) {
                    compressedSize = 0; // 如果压缩后大小为负数，设置为0
                }
                entryInfo.setCompressedSize(compressedSize);

                entryInfo.setDirectory(fileHeader.isDirectory());
                // 🔑 关键改进：使用UTC格式的时间
                entryInfo.setLastModified(
                        fileHeader.getMTime() != null ? DateTimeUtils.toUTCDate(fileHeader.getMTime()) : null);
                entryInfo.setCrc(fileHeader.getFileCRC());
                entryInfo.setMethod("RAR");
                entryInfo.setEncrypted(fileHeader.isEncrypted());

                if (!fileHeader.isDirectory()) {
                    entryInfo.setFileType(fileUtils.determineFileType(fileHeader.getFileName()));
                }

                entries.add(entryInfo);
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("RAR文件解析完成 - 文件: {}, 条目数: {}, 大小为负数的条目数: {}, 耗时: {}ms",
                    archiveFile.getName(), entryCount, negativeSizeCount, duration);
        } finally {
            if (archive != null) {
                try {
                    archive.close();
                } catch (Exception e) {
                    logger.warn("关闭 RAR archive 失败", e);
                }
            }
        }

        info.setEntries(entries);
        ArchiveUtils.calculateStatistics(info);
        return info;
    }

    /**
     * 解析7Z格式文件（带密码支持）
     * 使用 SevenZipParserService 智能解析策略：
     * - WSL2 环境：优先使用隔离子进程的 native 解析
     * - 其他环境：优先使用隔离 native，失败后 fallback 到外部命令
     * - 支持安全模式强制隔离或仅使用外部命令
     * 
     * @param archivePath 7Z文件路径
     * @param password    密码（可为null）
     * @return 压缩包信息
     * @throws Exception 解析失败
     */
    private ArchiveInfo parse7zArchive(String archivePath, String password) throws Exception {
        logger.debug("准备解析7z文件 - Path: {}", archivePath);
        File archiveFile = new File(archivePath);
        
        // 使用智能解析工具（自动选择策略，默认 AUTO 模式）
        // 可选模式: AUTO / SAFE_MODE_ALWAYS_ISOLATE / EXTERNAL_ONLY
        return sevenZipParserService.parse7zArchive(
            archiveFile, 
            password,
            SevenZipParserService.Mode.AUTO
        );
    }

    /**
     * 安全获取ZIP条目名称，处理编码问题
     */
    private String getEntryNameSafely(ZipEntry entry) {
        try {
            String name = entry.getName();
            if (name != null) {
                return name;
            }
        } catch (Exception e) {
            logger.debug("获取ZIP条目名称失败: {}", e.getMessage());
        }

        // 如果获取名称失败，返回默认名称
        return "unknown_entry_" + System.currentTimeMillis();
    }

    /**
     * 优化版本：更高效地读取条目内容
     */
    private String readEntryContentOptimized(ZipInputStream zis, int size) throws IOException {
        if (size <= 0 || size > 1024) { // 优化：更严格的大小限制
            return "";
        }

        byte[] buffer = new byte[size];
        int totalRead = 0;
        int bytesRead;

        try {
            // 优化：增加超时控制，避免无限等待
            long startTime = System.currentTimeMillis();
            while (totalRead < size && (bytesRead = zis.read(buffer, totalRead, size - totalRead)) != -1) {
                totalRead += bytesRead;

                // 超时保护：如果读取超过500ms，立即停止
                if (System.currentTimeMillis() - startTime > 500) {
                    logger.debug("读取条目内容超时，已读取 {} 字节", totalRead);
                    break;
                }
            }

            // 优化：只尝试UTF-8和GBK两种主要编码
            String[] encodings = { "UTF-8", "GBK" };

            for (String encoding : encodings) {
                try {
                    String result = new String(buffer, 0, totalRead, encoding);
                    // 简单检查是否包含乱码
                    if (!result.contains("\uFFFD")) {
                        return result;
                    }
                } catch (Exception e) {
                    // 继续尝试下一种编码
                }
            }

            // 如果都失败，返回空字符串
            return "";

        } catch (Exception e) {
            logger.debug("读取ZIP条目内容失败: {}", e.getMessage());
            return "";
        }
    }

}