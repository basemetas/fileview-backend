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
package com.basemetas.fileview.preview.utils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.config.FileFormatConfig;

@Component
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

    @Autowired
    private EncodingUtils encodingUtils;

    @Autowired
    private FileFormatConfig fileFormatConfig;

    // 从配置文件读取解压临时目录
    @Value("${fileview.preview.storage.uncompress-dir:../fileTemp/source/uncompress}")
    private String baseTempDir;
    /**
     * 为本地上传文件生成唯一fileId
     * 
     * 生成策略：
     * 1. 如果已有fileId且不是临时ID，直接使用
     * 2. 如果没有fileId，基于文件路径+文件名生成MD5（不包含时间戳！）
     * 3. 格式：upload_{md5_hash}
     * 
     * 注意：不使用时间戳，保证相同文件生成相同fieldId，以便命中缓存
     * 
     * @param originalFileId 原始的fileId（可能为空）
     * @param fileName       文件名
     * @param filePath       文件路径
     * @return 生成的唯一fileId
     */
    public String generateFileIdForLocalUpload(String originalFileId, String fileName, String filePath) {
        try {
            // 1. 如果已有fileId且不是临时ID，直接使用
            if (originalFileId != null && !originalFileId.trim().isEmpty() && !originalFileId.startsWith("temp_")) {
                return originalFileId.trim();
            }

            // 2. 生成稳定的唯一ID：基于文件路径 + 文件名（不包含时间戳）
            // 这样相同文件多次上传会生成相同的fileId，能命中缓存
            String uniqueString = String.format("%s_%s",
                    filePath != null ? filePath : "",
                    fileName);

            // 3. 计算MD5哈希
            String hash = encodingUtils.calculateMD5(uniqueString);

            // 4. 返回格式：upload_{hash}
            String generatedId = "upload_" + hash;

            logger.debug("🔑 生成稳定的fileId - Input: {}, Hash: {}, FileId: {}",
                    uniqueString, hash, generatedId);

            return generatedId;

        } catch (Exception e) {
            logger.error("❌ 生成fileId失败，使用时戳作为降级方案", e);
            // 降级处理：使用时戳 + 随机数（只在异常时）
            return "upload_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        }
    }

    /**
     * 路径标准化处理
     * 
     * 处理Windows路径到Linux/WSL路径的转换：
     * - Windows: D:/path/to/file 或 D:\\path\\to\\file
     * - WSL: /mnt/d/path/to/file
     * - Linux: /path/to/file (不变)
     * 
     * @param path 原始路径
     * @return 标准化后的路径
     */
    public String normalizeFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        String normalizedPath = path.trim();

        // 检测是否为Windows路径（以盘符开头，C:, D:等）
        if (normalizedPath.matches("^[A-Za-z]:[/\\\\].*")) {
            // 提取盘符
            char driveLetter = Character.toLowerCase(normalizedPath.charAt(0));

            // 移除 "D:" 部分
            String pathWithoutDrive = normalizedPath.substring(2);

            // 替换反斜杠为正斜杠
            pathWithoutDrive = pathWithoutDrive.replace('\\', '/');

            // 转换为WSL路径：/mnt/d/...
            normalizedPath = "/mnt/" + driveLetter + pathWithoutDrive;

            logger.debug("🔄 Windows路径转换为WSL路径 - Drive: {}, Result: {}", driveLetter, normalizedPath);
        } else {
            // 已经是Linux路径，只需统一分隔符
            normalizedPath = normalizedPath.replace('\\', '/');
        }

        return normalizedPath;
    }

    /**
     * 根据文件路径或者URL生成稳定的fileId
     * 基于根文件路径或URL的MD5哈希值，确保相同URL生成相同fileId，命中缓存
     * 
     * @param networkFileUrl 网络文件URL
     * @return 生成的fileId，格式为 "download_" + MD5哈希
     */
    public String generateFileIdFromFileUrl(String networkFileUrl) {
        try {
            if (networkFileUrl == null || networkFileUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("网络文件URL不能为空");
            }
            // 计算URL的MD5哈希
            String hash = encodingUtils.calculateMD5(networkFileUrl.trim());
            // 返回格式：download_{hash}
            String generatedId = "preview_" + hash;
            return generatedId;

        } catch (Exception e) {
            logger.error("❌ 生成网络下载fileId失败，使用时间戳作为降级方案", e);
            // 降级处理：使用时间戳 + 随机数（只在异常时）
            return "download_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        }
    }

    /**
     * 将文件复制到目标路径
     * 
     * @param sourceFile    源文件
     * @param targetDirPath 目标目录路径
     * @param fileName      文件名
     * @param fileId        文件ID（用于错误日志）
     * @return 目标文件的完整路径
     */
    public String copyFileToTargetPath(File sourceFile, String targetDirPath, String fileName, String fileId) {
        try {
            // 确保目标目录存在
            File targetDir = new File(targetDirPath);
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    logger.warn("⚠️ 创建目录失败: {}", targetDirPath);
                }
            }

            // 构建目标文件路径
            String normalizedTargetDir = targetDirPath.endsWith("/") ? targetDirPath : targetDirPath + "/";
            String targetFilePath = normalizedTargetDir + fileName;
            File targetFile = new File(targetFilePath);

            // 如果目标文件已存在，先删除
            if (targetFile.exists()) {
                logger.info("ℹ️ 目标文件已存在，将被覆盖: {}", targetFilePath);
                boolean deleted = targetFile.delete();
                if (!deleted) {
                    logger.warn("⚠️ 删除旧文件失败: {}", targetFilePath);
                }
            }

            // 复制文件
            java.nio.file.Files.copy(
                    sourceFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            logger.info("✅ 文件复制成功 - From: {}, To: {}", sourceFile.getAbsolutePath(), targetFilePath);

            return targetFilePath;

        } catch (Exception e) {
            logger.error("❌ 文件复制失败 - FileId: {}, Source: {}, Target: {}",
                    fileId, sourceFile.getAbsolutePath(), targetDirPath, e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "文件复制失败: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }

    /**
     * 处理文件路径逻辑
     * 如果srcRelativePath是目录，则拼接fileName
     * 如果srcRelativePath是完整文件路径，则直接使用
     */
    public String processFilePath(String srcRelativePath, String fileName) {
        try {
            if (srcRelativePath == null || srcRelativePath.trim().isEmpty()) {
                logger.warn("ℹ️ 源文件路径为空");
                return null;
            }
            File path = new File(srcRelativePath);
            // 判断是目录还是文件
            if (path.isDirectory()) {
                // 是目录，需要拼接fileName
                if (fileName == null || fileName.trim().isEmpty()) {
                    logger.warn("ℹ️ srcRelativePath是目录但fileName为空: {}", srcRelativePath);
                    return null;
                }
                String normalizedPath = srcRelativePath.endsWith("/") ? srcRelativePath : srcRelativePath + "/";
                return normalizedPath + fileName.trim();

            } else if (path.isFile()) {
                // 是文件，直接使用
                return srcRelativePath;

            } else {
                // 文件不存在，尝试按文件名拼接的方式处理
                if (srcRelativePath.endsWith("/") || (!srcRelativePath.contains(".") && fileName != null)) {
                    // 可能是目录路径
                    if (fileName == null || fileName.trim().isEmpty()) {
                        logger.warn("ℹ️ 无法确定文件路径类型，fileName为空: {}", srcRelativePath);
                        return null;
                    }
                    String normalizedPath = srcRelativePath.endsWith("/") ? srcRelativePath : srcRelativePath + "/";
                    return normalizedPath + fileName.trim();
                } else {
                    // 可能是文件路径
                    return srcRelativePath;
                }
            }

        } catch (Exception e) {
            logger.error("❌ 处理文件路径失败 - srcRelativePath: {}, fileName: {}", srcRelativePath, fileName, e);
            return null;
        }
    }

    /**
     * 获取文件类型（扩展名）
     */
    public String getFileExtention(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 处理目标文件名逻辑
     * 如果targetFileName为空，则从fullName提取（去掉扩展名）
     */
    public String processTargetFileName(String targetFileName, String fileName) {
        try {
            // 如果targetFileName不为空，直接使用
            if (targetFileName != null && !targetFileName.trim().isEmpty()) {
                return targetFileName.trim();
            }

            // 如果targetFileName为空，从fullName提取
            if (fileName == null || fileName.trim().isEmpty()) {
                logger.warn("ℹ️ targetFileName和fullName都为空，使用默认名称");
                return "document"; // 默认名称
            }

            String processedFileName = fileName.trim();

            // 判断是否包含扩展名
            if (processedFileName.contains(".")) {
                // 去掉扩展名
                int lastDotIndex = processedFileName.lastIndexOf(".");
                return processedFileName.substring(0, lastDotIndex);
            } else {
                // 没有扩展名，直接使用
                return processedFileName;
            }

        } catch (Exception e) {
            logger.error("❌ 处理目标文件名失败 - targetFileName: {}, fullName: {}", targetFileName, fileName, e);
            return "document"; // 错误时返回默认名称
        }
    }

    /**
     * 从文件路径中提取不带扩展名的文件名
     * 
     * @param filePath 文件路径
     * @return 不带扩展名的文件名，如果无法提取则返回 "document"
     */
    public String getFullFileNamefromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "document";
        }
        // 提取文件名（包含扩展名）
        String fileName = new File(filePath).getName();
        // 如果没有扩展名，返回完整文件名
        return fileName;
    }

    /**
     * 从文件路径中提取不带扩展名的文件名
     * 
     * @param filePath 文件路径
     * @return 不带扩展名的文件名，如果无法提取则返回 "document"
     */
    public String getFileNamefromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "document";
        }

        // 提取文件名（包含扩展名）
        String fileName = new File(filePath).getName();

        // 查找最后一个点的位置
        int lastDotIndex = fileName.lastIndexOf('.');

        // 如果有点且不在开头或结尾，则提取不带扩展名的部分
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(0, lastDotIndex);
        }

        // 如果没有扩展名，返回完整文件名
        return fileName;
    }

    /**
     * 构建完整的目标文件路径
     * 格式：目录路径 + 文件名 + 扩展名
     * 
     * @param targetPath     目标目录路径
     * @param targetFileName 目标文件名
     * @param targetFormat   目标格式（扩展名）
     * @return 完整的目标文件路径
     */
    public String buildFullTargetPath(String targetPath, String targetFileName, String targetFormat) {
        if (targetPath == null || targetFileName == null) {
            return targetPath; // 返回原始路径作为备用
        }

        // 确保目录路径以斜杠结尾
        String normalizedPath = targetPath.endsWith("/") ? targetPath : targetPath + "/";

        // 拼接完整路径：目录 + 文件名 + 扩展名
        String fullTargetPath;
        if (targetFormat != null && !targetFormat.trim().isEmpty()) {
            // 检查文件名是否已经包含扩展名
            String lowerFileName = targetFileName.toLowerCase();
            String lowerFormat = targetFormat.toLowerCase();
            if (lowerFileName.endsWith("." + lowerFormat)) {
                // 已经包含扩展名，直接使用
                fullTargetPath = normalizedPath + targetFileName;
            } else {
                // 没有扩展名，添加上
                fullTargetPath = normalizedPath + targetFileName + "." + targetFormat;
            }
        } else {
            // 如果没有指定目标格式，直接使用文件名
            fullTargetPath = normalizedPath + targetFileName;
        }

        return fullTargetPath;
    }

    /**
     * 检查文件路径是否为系统级目录（Linux环境安全检查）
     * 
     * @param filePath 文件路径
     * @return 如果是系统级目录返回true，否则返回false
     */
    public boolean isSystemDirectory(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        String normalizedPath = filePath.trim();
        
        // 将Windows路径转换为Linux路径（如D:/path 转为 /mnt/d/path）
        normalizedPath = normalizeFilePath(normalizedPath);
        
        // 只在Linux/Unix环境下进行系统目录检查
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("linux") && !osName.contains("unix")) {
            // Windows环境不需要这个检查
            return false;
        }
        
        // 禁止访问的系统级目录列表
        String[] systemDirectories = {
            "/bin",           // 系统基本命令
            "/sbin",          // 系统管理命令
            "/boot",          // 启动文件
            "/dev",           // 设备文件
            "/etc",           // 系统配置文件
            "/lib",           // 系统库文件
            "/lib64",         // 64位系统库
            "/proc",          // 进程信息
            "/sys",           // 系统信息
            "/root",          // root用户主目录
            "/usr/bin",       // 用户命令
            "/usr/sbin",      // 用户管理命令
            "/usr/lib",       // 用户库文件
            "/usr/lib64",     // 64位用户库
            "/var/log",       // 系统日志
            "/var/run",       // 运行时数据
            "/run"            // 运行时数据（新版系统）
        };
        
        // 检查路径是否以系统目录开头
        for (String sysDir : systemDirectories) {
            if (normalizedPath.equals(sysDir) || normalizedPath.startsWith(sysDir + "/")) {
                logger.warn("🚫 安全拒绝：尝试访问系统目录 - Path: {}", normalizedPath);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 安全路径检查 - 防止路径遍历攻击和系统目录访问
     * 
     * @param filePath 文件路径
     * @return 如果路径安全返回true，否则返回false
     */
    public boolean isSecurePath(String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                logger.warn("⚠️ 文件路径为空");
                return false;
            }
            
            java.nio.file.Path path = java.nio.file.Paths.get(filePath).normalize();
            String normalizedPath = path.toString();

            // 禁止包含相对路径符号（防止路径遍历攻击）
            if (normalizedPath.contains("..") || normalizedPath.contains("./")) {
                logger.warn("🚫 检测到路径遍历尝试 - Path: {}", filePath);
                return false;
            }

            // 使用系统目录检查（涵盖/etc, /bin, /proc 等）
            if (isSystemDirectory(normalizedPath)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warn("⚠️ 路径检查异常: {}", filePath, e);
            return false;
        }
    }

       /**
     * 创建临时目录
     */
    public Path createTempDirectory(File archiveFile) throws IOException {
        // 确保基础目录存在
        Path baseDir = Paths.get(baseTempDir);
        
        // 检查基础目录是否存在，不存在则创建
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
            logger.debug("创建基础临时目录: {}", baseDir);
        }
        
        // 验证基础目录安全性
        validateBaseDirectorySecurity(baseDir);
        
        // 获取压缩文件名（不包含扩展名）
        String archiveFileName = archiveFile.getName();
        int lastDotIndex = archiveFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            archiveFileName = archiveFileName.substring(0, lastDotIndex);
        }
        
        // 清理文件名中的特殊字符，防止路径问题
        archiveFileName = archiveFileName.replaceAll("[^\\w\\u4e00-\\u9fff\\-_]", "_");
        
        // 直接使用压缩文件名作为解压目录，这样文件路径中就包含了压缩文件名
        Path tempDir = baseDir.resolve(archiveFileName);
        
        // 检查临时目录是否存在，不存在则创建
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
            logger.debug("创建临时解压目录: {}", tempDir);
        } else {
            logger.debug("临时解压目录已存在: {}", tempDir);
        }
        
        return tempDir;
    }

    
    /**
     * 验证基础目录安全性
     * 
     * @param baseDir 基础目录路径
     * @throws IOException 当目录不安全时抛出
     */
    public void validateBaseDirectorySecurity(Path baseDir) throws IOException {
        // 获取系统临时目录
        Path systemTempDir = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        
        // 获取用户主目录
        Path userHomeDir = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        
        // 获取当前工作目录
        Path currentWorkDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        
        // 规范化基础目录路径
        Path normalizedBaseDir = baseDir.toAbsolutePath().normalize();
        
        // 检查是否在系统临时目录、用户主目录或当前工作目录下
        boolean isInSystemTemp = normalizedBaseDir.startsWith(systemTempDir);
        boolean isInUserHome = normalizedBaseDir.startsWith(userHomeDir);
        boolean isInCurrentWorkDir = normalizedBaseDir.startsWith(currentWorkDir);
        
        // 如果不在这些安全目录下，需要额外验证
        if (!isInSystemTemp && !isInUserHome && !isInCurrentWorkDir) {
            // 检查目录是否存在且可写
            if (!Files.exists(normalizedBaseDir)) {
                // 尝试创建目录
                try {
                    Files.createDirectories(normalizedBaseDir);
                } catch (IOException e) {
                    throw new IOException("无法创建解压基础目录: " + normalizedBaseDir + "，请检查权限或配置", e);
                }
            }
            
            // 检查目录是否可写
            if (!Files.isWritable(normalizedBaseDir)) {
                throw new IOException("解压基础目录不可写: " + normalizedBaseDir);
            }
        }
        
        logger.debug("基础目录安全性验证通过: {}", normalizedBaseDir);
    }
    
    /**
     * 检测压缩文件类型
     */
    public String detectArchiveType(File archiveFile) {
        return extractFileExtension(archiveFile.getName());
    }

    
    /**
     * 检测文件格式（支持压缩包、Office、PDF）
     */
    public String detectFileFormat(String fileName) {
        return extractFileExtension(fileName);
    }
    
    /**
     * 提取文件扩展名（支持复合扩展名如 .tar.gz）
     * 
     * @param fileName 文件名
     * @return 扩展名（小写，不包含点），未匹配返回 "unknown"
     */
    private String extractFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }     
        String lowerName = fileName.toLowerCase();      
        // 优先匹配复合扩展名（按从长到短）
        if (lowerName.endsWith(".tar.gz")) {
            return "tar.gz";
        }
        if (lowerName.endsWith(".tar.bz2")) {
            return "tar.bz2";
        }
        
        // 处理 .tgz 特殊情况（映射为 tar.gz）
        if (lowerName.endsWith(".tgz")) {
            return "tar.gz";
        }
        
        // 提取最后一个点之后的扩展名
        int lastDot = lowerName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < lowerName.length() - 1) {
            return lowerName.substring(lastDot + 1);
        }
        
        return "unknown";
    }

    
    /**
     * 获取相对于项目根目录的路径
     */
    public String getRelativePath(Path absolutePath) {
        try {
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            Path relativePath = currentDir.relativize(absolutePath);
            return relativePath.toString().replace('\\', '/');
        } catch (Exception e) {
            logger.warn("无法计算相对路径，返回绝对路径: {}", e.getMessage());
            return absolutePath.toString().replace('\\', '/');
        }
    }
    
    /**
     * 判断是否为Office文档格式
     * 使用 FileFormatConfig 统一管理
     */
    public boolean isOfficeDocument(String fileFormat) {
        return fileFormatConfig.isOfficeDocument(fileFormat);
    }

     /**
     * 编码文件名以支持中文等特殊字符
     * 使用RFC 5987标准：filename*=UTF-8''encoded-filename
     * 
     * @param fileName 原始文件名
     * @return URL编码后的文件名
     */
    public String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20"); // 空格用%20而不是+
        } catch (Exception e) {
            logger.warn("⚠️ 文件名编码失败: {}", fileName, e);
            // 降级处理：移除非ASCII字符
            return fileName.replaceAll("[^\\x00-\\x7F]", "_");
        }
    }
    
    /**
     * 检测字符串是否为乱码（包含Box Drawing字符）
     * 用于检测压缩包中文文件名的编码问题
     */
    public boolean isMessyCode(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 检测Box Drawing字符（Unicode范围：U+2500-U+257F）
        for (char c : str.toCharArray()) {
            if (c >= '\u2500' && c <= '\u257F') {
                return true;
            }
        }
        return false;
    }

}
