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
package com.basemetas.fileview.convert.common;

import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件转换通用工具类 - 统一处理环境检测、路径验证、目录操作等
 * 
 * 主要功能：
 * 1. 环境检测（操作系统、架构、Java版本等）
 * 2. 文件路径验证、规范化、编码处理
 * 3. 目录创建和权限验证
 * 4. 文件存在性检查和智能查找
 * 5. 参数验证和错误处理
 * 6. 路径构建和安全检查
 * 7. 文件扩展名提取和验证
 * 
 * 设计目标：
 * - 消除Strategy类间的重复代码
 * - 提供统一的错误处理和日志记录
 * - 支持跨平台兼容性
 * - 增强文件操作的安全性
 * 
 * @author 夫子
 */
@Component
public class ValidateAndNormalized {

    private static final Logger logger = LoggerFactory.getLogger(ValidateAndNormalized.class);

    // 注入 FileTypeMapper 用于动态格式验证
    @Autowired
    private FileTypeMapper fileTypeMapper;

    // 环境信息缓存
    private static volatile EnvironmentInfo environmentInfo;
    private static final Object envLock = new Object();
    /**
     * 环境信息封装类
     */
    public static class EnvironmentInfo {
        private final boolean isWindows;
        private final boolean isLinux;
        private final boolean isMac;
        private final boolean is64Bit;
        private final String osName;
        private final String osVersion;
        private final String javaVersion;
        private final String userDir;
        private final String fileSeparator;

        public EnvironmentInfo() {
            this.osName = System.getProperty("os.name", "unknown").toLowerCase();
            this.osVersion = System.getProperty("os.version", "unknown");
            this.javaVersion = System.getProperty("java.version", "unknown");
            this.userDir = System.getProperty("user.dir", ".");
            this.fileSeparator = File.separator;

            this.isWindows = osName.contains("windows");
            this.isLinux = osName.contains("linux");
            this.isMac = osName.contains("mac");

            String arch = System.getProperty("os.arch", "unknown").toLowerCase();
            this.is64Bit = arch.contains("64");
        }

        // Getters
        public boolean isWindows() {
            return isWindows;
        }

        public boolean isLinux() {
            return isLinux;
        }

        public boolean isMac() {
            return isMac;
        }

        public boolean is64Bit() {
            return is64Bit;
        }

        public String getOsName() {
            return osName;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public String getUserDir() {
            return userDir;
        }

        public String getFileSeparator() {
            return fileSeparator;
        }

        @Override
        public String toString() {
            return String.format("EnvironmentInfo{os=%s %s, java=%s, arch=%s, dir=%s}",
                    osName, osVersion, javaVersion, is64Bit ? "64-bit" : "32-bit", userDir);
        }
    }

    /**
     * 验证结果封装类
     */
    public static class ValidationResult {
        private final boolean success;
        private final String message;
        private final String correctedPath;
        private final Map<String, Object> metadata;

        public ValidationResult(boolean success, String message) {
            this(success, message, null, new HashMap<>());
        }

        public ValidationResult(boolean success, String message, String correctedPath) {
            this(success, message, correctedPath, new HashMap<>());
        }

        public ValidationResult(boolean success, String message, String correctedPath, Map<String, Object> metadata) {
            this.success = success;
            this.message = message;
            this.correctedPath = correctedPath;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getCorrectedPath() {
            return correctedPath;
        }

        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }

        // 便捷方法
        public void addMetadata(String key, Object value) {
            this.metadata.put(key, value);
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{success=%s, message='%s', path='%s'}",
                    success, message, correctedPath);
        }
    }

    // ======================== 环境检测方法 ========================

    /**
     * 获取环境信息（线程安全的懒加载）
     */
    public EnvironmentInfo getEnvironmentInfo() {
        if (environmentInfo == null) {
            synchronized (envLock) {
                if (environmentInfo == null) {
                    // 使用临时变量完成所有初始化，确保对象完全构造后再赋值给 volatile 字段
                    EnvironmentInfo tempInfo = new EnvironmentInfo();
                    logger.info("环境信息初始化完成: {}", tempInfo);
                    
                    // 所有初始化完成后，再赋值给 volatile 字段（happens-before 保证）
                    environmentInfo = tempInfo;
                }
            }
        }
        return environmentInfo;
    }

    /**
     * 检查是否为Windows环境
     */
    public boolean isWindows() {
        return getEnvironmentInfo().isWindows();
    }

    /**
     * 检查是否为Linux环境
     */
    public boolean isLinux() {
        return getEnvironmentInfo().isLinux();
    }

    /**
     * 检查是否为64位架构
     */
    public boolean is64BitArchitecture() {
        return getEnvironmentInfo().is64Bit();
    }

    /**
     * 获取当前工作目录
     */
    public String getCurrentWorkingDirectory() {
        return getEnvironmentInfo().getUserDir();
    }

    // ======================== 增强的参数验证方法 ========================

    /**
     * 验证转换参数（增强版）
     */
    public ValidationResult validateConversionParameters(String sourceFilePath, String targetPath,
            String targetFileName, String targetFormat) {
        logger.debug("开始验证转换参数: source={}, target={}, name={}, format={}",
                sourceFilePath, targetPath, targetFileName, targetFormat);

        // 1. 基本参数检查
        ValidationResult basicCheck = validateBasicParameters(sourceFilePath, targetPath, targetFileName, targetFormat);
        if (!basicCheck.isSuccess()) {
            return basicCheck;
        }

        // 2. 源文件验证
        ValidationResult sourceCheck = validateSourceFile(sourceFilePath);
        if (!sourceCheck.isSuccess()) {
            return sourceCheck;
        }

        // 3. 目标路径验证
        ValidationResult targetCheck = validateAndCreateTargetDirectory(targetPath);
        if (!targetCheck.isSuccess()) {
            return targetCheck;
        }

        // 4. 格式支持检查
        if (!isFormatSupportedEnhanced(targetFormat)) {
            return new ValidationResult(false, "不支持的目标格式: " + targetFormat);
        }

        // 返回成功结果，包含修正后的源文件路径
        ValidationResult result = new ValidationResult(true, "参数验证通过", sourceCheck.getCorrectedPath());
        result.addMetadata("targetDirectory", targetPath);
        result.addMetadata("targetFileName", targetFileName);
        result.addMetadata("targetFormat", targetFormat);

        logger.debug("参数验证成功: {}", result);
        return result;
    }

    /**
     * 基本参数检查
     */
    private ValidationResult validateBasicParameters(String sourceFilePath, String targetPath,
            String targetFileName, String targetFormat) {
        if (!StringUtils.hasText(sourceFilePath)) {
            return new ValidationResult(false, "源文件路径不能为空");
        }

        if (!StringUtils.hasText(targetPath)) {
            return new ValidationResult(false, "目标路径不能为空");
        }

        if (!StringUtils.hasText(targetFileName)) {
            return new ValidationResult(false, "目标文件名不能为空");
        }

        if (!StringUtils.hasText(targetFormat)) {
            return new ValidationResult(false, "目标格式不能为空");
        }

        return new ValidationResult(true, "基本参数检查通过");
    }

    /**
     * 验证输入参数（保持向后兼容）
     */
    public boolean validateInputs(String filePath, String targetPath, String targetFileName, String targetFormat) {
        if (!StringUtils.hasText(filePath) || !StringUtils.hasText(targetPath)) {
            logger.error("Source file path or target file path is empty");
            return false;
        }

        // 规范化源文件路径
        String normalizedFilePath = this.normalizePath(filePath);
        logger.info("Validating source file path: original={}, normalized={}", filePath, normalizedFilePath);

        File sourceFile = new File(normalizedFilePath);

        // 详细的文件存在性检查
        if (!sourceFile.exists()) {
            logger.error("Source file does not exist: {}", normalizedFilePath);
            logger.error("Absolute path: {}", sourceFile.getAbsolutePath());
            logger.error("Current working directory: {}", System.getProperty("user.dir"));

            // 尝试其他可能的路径解析方式
            this.tryAlternativePathResolution(filePath);
            return false;
        }

        // 检查文件是否可读
        if (!sourceFile.canRead()) {
            logger.error("Source file exists but cannot be read: {}", normalizedFilePath);
            logger.error("File permissions - readable: {}, writable: {}, executable: {}",
                    sourceFile.canRead(), sourceFile.canWrite(), sourceFile.canExecute());
            return false;
        }

        // 检查是否为文件（不是目录）
        if (!sourceFile.isFile()) {
            logger.error("Source path is not a file: {}", normalizedFilePath);
            return false;
        }

        logger.info("Source file validation successful: {} (size: {} bytes)",
                normalizedFilePath, sourceFile.length());

        if (!this.isFormatSupported(targetFormat)) {
            logger.error("Unsupported target format: {}", targetFormat);
            return false;
        }

        return true;
    }

    /**
     * 规范化路径格式，处理Windows和Unix路径兼容性
     */
    public String normalizePath(String path) {
        if (path == null) {
            return null;
        }

        // 处理不同操作系统的路径分隔符
        String normalized = path;

        // 在Linux环境下，将Windows路径分隔符转换为Unix分隔符
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            normalized = path.replace("\\", "/");

            // 处理Windows驱动器盘符（如 D:/ 转换为 /mnt/d/ 或保持原样）
            if (normalized.matches("^[A-Za-z]:/.*")) {
                logger.info("Detected Windows drive path in Linux environment: {}", normalized);
                // 可以根据需要进行路径映射，这里保持原样
                // 在Docker容器中，Windows路径通常已经被挂载映射
            }
        }

        // 处理重复的路径分隔符
        normalized = normalized.replaceAll("/+", "/");

        // 解析相对路径为绝对路径
        try {
            Path normalizedPath = Paths.get(normalized).normalize();
            String result = normalizedPath.toString();
            logger.debug("Path normalization: {} -> {}", path, result);
            return result;
        } catch (Exception e) {
            logger.warn("Failed to normalize path {}, using original: {}", path, e.getMessage());
            return normalized;
        }
    }

    /**
     * 构建目标文件完整路径（保持向后兼容）
     */
    public String buildTargetFilePath(String targetPath, String targetFileName, String targetFormat) {
        return buildTargetFilePathEnhanced(targetPath, targetFileName, targetFormat);
    }

    /**
     * 尝试其他可能的路径解析方式
     */
    private void tryAlternativePathResolution(String originalPath) {
        logger.info("Trying alternative path resolution for: {}", originalPath);

        // 1. 尝试相对于当前工作目录
        String workingDir = System.getProperty("user.dir");
        File relativeToWorking = new File(workingDir, originalPath);
        logger.info("Trying relative to working directory: {}", relativeToWorking.getAbsolutePath());
        if (relativeToWorking.exists()) {
            logger.info("Found file relative to working directory!");
        }

        // 2. 尝试相对于应用程序根目录
        File relativeToRoot = new File(originalPath);
        logger.info("Trying as relative path: {}", relativeToRoot.getAbsolutePath());
        if (relativeToRoot.exists()) {
            logger.info("Found file as relative path!");
        }

        // 3. 如果路径包含反斜杠，尝试转换为正斜杠
        if (originalPath.contains("\\")) {
            String unixPath = originalPath.replace("\\", "/");
            File unixFile = new File(unixPath);
            logger.info("Trying with Unix path separators: {}", unixFile.getAbsolutePath());
            if (unixFile.exists()) {
                logger.info("Found file with Unix path separators!");
            }
        }

        // 4. 列出可能的目录内容
        try {
            File parentDir = new File(originalPath).getParentFile();
            if (parentDir != null && parentDir.exists()) {
                logger.info("Parent directory exists: {}", parentDir.getAbsolutePath());
                String[] files = parentDir.list();
                if (files != null) {
                    logger.info("Files in parent directory: {}", Arrays.toString(files));
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to list parent directory: {}", e.getMessage());
        }
    }

    // ======================== 源文件验证方法 ========================

    /**
     * 验证源文件（增强版）
     */
    public ValidationResult validateSourceFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return new ValidationResult(false, "源文件路径为空");
        }

        // 尝试多种路径解析方式
        List<String> pathsToTry = generatePathVariants(filePath);

        for (String pathVariant : pathsToTry) {
            File file = new File(pathVariant);

            if (file.exists() && file.isFile()) {
                // 进一步验证文件
                ValidationResult fileCheck = validateFileProperties(file, pathVariant);
                if (fileCheck.isSuccess()) {
                    logger.debug("源文件验证成功: {} -> {}", filePath, pathVariant);
                    return new ValidationResult(true, "源文件验证成功", pathVariant);
                }
            }
        }

        // 所有路径都失败了，返回详细的错误信息
        String errorMsg = String.format("无法找到或访问源文件: %s (尝试了%d种路径变体)", filePath, pathsToTry.size());
        logPathDebuggingInfo(filePath, pathsToTry);
        return new ValidationResult(false, errorMsg);
    }

    /**
     * 生成路径变体用于智能查找
     */
    private List<String> generatePathVariants(String originalPath) {
        List<String> variants = new ArrayList<>();

        // 1. 原始路径
        variants.add(originalPath);

        // 2. 规范化路径
        String normalized = normalizePath(originalPath);
        if (normalized != null && !normalized.equals(originalPath)) {
            variants.add(normalized);
        }

        // 3. URL解码路径（容错处理：解码失败时跳过，保留原始路径）
        try {
            String decoded = URLDecoder.decode(originalPath, StandardCharsets.UTF_8.name());
            if (!decoded.equals(originalPath)) {
                variants.add(decoded);

                // 解码后再规范化
                String decodedNormalized = normalizePath(decoded);
                if (decodedNormalized != null && !decodedNormalized.equals(decoded)) {
                    variants.add(decodedNormalized);
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.debug("URL解码失败: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // 非法转义字符（如 %=&），跳过解码，保留原始路径
            logger.debug("URL解码失败（非法转义字符）: {}", e.getMessage());
        }

        // 4. 处理空格和特殊字符
        if (originalPath.contains(" ")) {
            String fixedSpaces = smartFixSpacesInPath(originalPath);
            if (fixedSpaces != null && !fixedSpaces.equals(originalPath)) {
                variants.add(fixedSpaces);
            }
        }

        // 5. 相对路径处理
        if (!Paths.get(originalPath).isAbsolute()) {
            String workingDir = getCurrentWorkingDirectory();
            String absolutePath = Paths.get(workingDir, originalPath).normalize().toString();
            variants.add(absolutePath);
        }

        // 去重
        return new ArrayList<>(new LinkedHashSet<>(variants));
    }

    /**
     * 智能修复路径中的空格问题
     */
    private String smartFixSpacesInPath(String path) {
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();

            if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
                String fileName = file.getName();
                String[] possibleNames = {
                        fileName.replace(" ", "+"),
                        fileName.replace(" ", "%20"),
                        fileName.replace(" ", "_")
                };

                for (String possibleName : possibleNames) {
                    File possibleFile = new File(parentDir, possibleName);
                    if (possibleFile.exists()) {
                        return possibleFile.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("智能修复空格失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 验证文件属性
     */
    private ValidationResult validateFileProperties(File file, String path) {
        if (!file.canRead()) {
            return new ValidationResult(false, "文件不可读: " + path);
        }

        if (file.length() == 0) {
            logger.warn("警告: 文件大小为0字节: {}", path);
        }

        return new ValidationResult(true, "文件属性验证通过");
    }

    // ======================== 目录操作方法 ========================

    /**
     * 验证并创建目标目录（增强版）
     */
    public ValidationResult validateAndCreateTargetDirectory(String targetPath) {
        if (!StringUtils.hasText(targetPath)) {
            return new ValidationResult(false, "目标路径为空");
        }

        try {
            String normalizedPath = normalizePath(targetPath);
            File targetDir = new File(normalizedPath);

            // 如果目录不存在，尝试创建
            if (!targetDir.exists()) {
                logger.info("目标目录不存在，尝试创建: {}", normalizedPath);
                boolean created = targetDir.mkdirs();
                if (!created) {
                    return new ValidationResult(false, "无法创建目标目录: " + normalizedPath);
                }
                logger.info("成功创建目标目录: {}", normalizedPath);
            }

            // 验证目录属性
            if (!targetDir.isDirectory()) {
                return new ValidationResult(false, "目标路径不是目录: " + normalizedPath);
            }

            if (!targetDir.canWrite()) {
                return new ValidationResult(false, "目标目录不可写: " + normalizedPath);
            }

            ValidationResult result = new ValidationResult(true, "目标目录验证成功", normalizedPath);
            result.addMetadata("directory", targetDir.getAbsolutePath());
            result.addMetadata("writable", targetDir.canWrite());
            result.addMetadata("existed", targetDir.exists());

            return result;

        } catch (Exception e) {
            logger.error("目录操作失败: {}", targetPath, e);
            return new ValidationResult(false, "目录操作异常: " + e.getMessage());
        }
    }

    /**
     * 确保目标目录存在（保持向后兼容）
     */
    public boolean ensureTargetDirectory(String fullTargetPath) {
        try {
            File targetFile = new File(fullTargetPath);
            File parentDir = targetFile.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
                logger.debug("创建目标目录: {}", parentDir.getAbsolutePath());
            }

            return true;
        } catch (Exception e) {
            logger.error("检查目标目录失败", e);
            return false;
        }
    }

    // ======================== 路径构建方法 ========================

    /**
     * 构建完整的目标文件路径（增强版）
     */
    public String buildTargetFilePathEnhanced(String targetPath, String targetFileName, String targetFormat) {
        // 规范化目标路径
        String normalizedPath = normalizePath(targetPath);
        if (normalizedPath == null) {
            normalizedPath = targetPath;
        }

        // 确保路径以分隔符结尾
        if (!normalizedPath.endsWith(File.separator) && !normalizedPath.endsWith("/")) {
            normalizedPath += File.separator;
        }

        // 构建完整文件名
        String fullFileName = targetFileName;
        String formatLower = targetFormat.toLowerCase();

        if (!targetFileName.toLowerCase().endsWith("." + formatLower)) {
            fullFileName += "." + formatLower;
        }

        String result = normalizedPath + fullFileName;
        logger.debug("构建目标路径: {} + {} + {} -> {}", targetPath, targetFileName, targetFormat, result);

        return result;
    }

    // ======================== 文件扩展名处理 ========================

    /**
     * 验证文件名和路径中的文件名
     * 
     * 验证规则：
     * 1. filePath不能同时没有文件名
     * 2. 如果有值，必须要有文件后缀名
     * 
     * @param filePath 文件路径
     * @return 验证结果
     */
    public ValidationResult validateFileNameAndPath(String filePath,String targetFormat) {
        logger.debug("验证文件名和路径: filePath={}", filePath);
        // 检查filePath是否包含文件名（包含路径分隔符）
        boolean hasFileNameInFilePath = filePath != null && (filePath.contains("/") || filePath.contains("\\"));
        // 规则1 filePath不能没有文件名
        if (!hasFileNameInFilePath) {
            return new ValidationResult(false, "filePath不能同时没有文件名");
        }
         if(targetFormat.isEmpty()){
             return new ValidationResult(false, "目标文件格式不能为空");                
        }
        // 规则2：如果filePath有值，提取的文件名必须要有文件后缀名
        String fileNameFromPath = extractFileNameFromPath(filePath);
        // 如果路径中包含文件名（不是纯目录路径）
        if (!fileNameFromPath.isEmpty()) {
            if (!fileNameFromPath.contains(".")) {
                return new ValidationResult(false,
                        "filePath中的文件名必须包含文件后缀名，例如: /path/to/document.pdf");
            }
            // 检查后缀名是否合法
            String extension = extractFileExtension(fileNameFromPath);
            if (extension.isEmpty()) {
                return new ValidationResult(false, "filePath中的文件后缀名无效");
            }
            FileCategory category = fileTypeMapper.getFileCategory(extension);
            if (category == null) {
                return new ValidationResult(false, "不支持的文件格式");
            }
           
            if (!fileTypeMapper.isConversionSupported(category, extension, targetFormat)) {
                return new ValidationResult(false, "不支持此转换组合");
            }
        }
        logger.debug("文件名和路径验证通过");
        return new ValidationResult(true, "文件名和路径验证通过");
    }

    /**
     * 从文件路径中提取文件名
     * 
     * @param filePath 文件路径
     * @return 文件名，如果无法提取则返回空字符串
     */
    private String extractFileNameFromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }

        int lastSlash = Math.max(filePath.lastIndexOf("/"), filePath.lastIndexOf("\\"));
        if (lastSlash >= 0 && lastSlash < filePath.length() - 1) {
            return filePath.substring(lastSlash + 1);
        }

        // 如果没有路径分隔符，返回整个字符串
        return filePath;
    }

    /**
     * 提取文件扩展名（增强版）
     */
    public String extractFileExtension(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "";
        }

        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
            logger.debug("提取文件扩展名: {} -> {}", filePath, extension);
            return extension;
        }

        logger.debug("无法提取扩展名: {}", filePath);
        return "";
    }

    /**
     * 检查文件扩展名是否为指定类型
     */
    public boolean isFileOfType(String filePath, String expectedExtension) {
        if (!StringUtils.hasText(filePath) || !StringUtils.hasText(expectedExtension)) {
            return false;
        }

        String actualExtension = extractFileExtension(filePath);
        return expectedExtension.toLowerCase().equals(actualExtension);
    }

    // ======================== 格式支持检查 ========================

    /**
     * 检查格式是否支持（增强版 - 使用 FileTypeMapper 动态验证）
     * 
     * 此方法检查指定格式是否为任意文件类别的有效目标格式。
     * 使用 FileTypeMapper 的动态配置，而非硬编码常量。
     * 
     * @param format 目标格式（如 "png", "pdf", "json" 等）
     * @return true 表示支持，false 表示不支持
     */
    public boolean isFormatSupportedEnhanced(String format) {
        if (!StringUtils.hasText(format)) {
            return false;
        }

        String formatLower = format.toLowerCase().trim();

        // 使用 FileTypeMapper 动态检查所有文件类别的目标格式
        for (FileCategory category : FileCategory.values()) {
            Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(category);
            if (targetFormats != null && targetFormats.contains(formatLower)) {
                logger.debug("格式验证通过: {} 属于 {} 类别的目标格式", formatLower, category.getDescription());
                return true;
            }
        }

        logger.debug("格式验证失败: {} 不是任何文件类别的有效目标格式", formatLower);
        return false;
    }

    /**
     * 获取格式类别（使用 FileTypeMapper 动态查找）
     * 
     * @param format 文件格式
     * @return 格式类别描述
     */
    public String getFormatCategory(String format) {
        if (!StringUtils.hasText(format)) {
            return "unknown";
        }

        String formatLower = format.toLowerCase().trim();

        // 使用 FileTypeMapper 动态查找格式所属类别
        for (FileCategory category : FileCategory.values()) {
            Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(category);
            if (targetFormats != null && targetFormats.contains(formatLower)) {
                return category.name().toLowerCase();
            }
        }

        return "unknown";
    }

    // ======================== 调试和日志方法 ========================

    /**
     * 记录路径调试信息
     */
    private void logPathDebuggingInfo(String originalPath, List<String> pathsToTry) {
        logger.debug("路径查找失败调试信息:");
        logger.debug("  原始路径: {}", originalPath);
        logger.debug("  当前工作目录: {}", getCurrentWorkingDirectory());
        logger.debug("  尝试的路径变体({}):", pathsToTry.size());

        for (int i = 0; i < pathsToTry.size(); i++) {
            String path = pathsToTry.get(i);
            File file = new File(path);
            logger.debug("    {}: {} (exists: {}, isFile: {})",
                    i + 1, path, file.exists(), file.isFile());
        }
    }

    // ======================== 向后兼容方法 ========================

    /**
     * 检查目标格式是否支持（保持向后兼容）
     */
    private boolean isFormatSupported(String targetFormat) {
        return isFormatSupportedEnhanced(targetFormat);
    }

}
