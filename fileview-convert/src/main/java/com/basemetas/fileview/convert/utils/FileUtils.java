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
package com.basemetas.fileview.convert.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.File;
import java.nio.file.Path;

@Component
public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
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
     * 为本地文件生成唯一fileId
     * 
     * 生成策略：
     * 1. 如果已有fileId且不是临时ID，直接使用
     * 2. 如果没有fileId，基于文件路径+文件名生成MD5（不包含时间戳！）
     * 3. 格式：upload_{md5_hash}
     * 
     * @param fileFileId 原始的fileId（可能为空）
     * @param fileName   文件名
     * @param filePath   文件路径
     * @return 生成的唯一fileId
     */
    public String generateFileId(String fileFileId, String fileName, String filePath) {
        try {
            // 1. 如果已有fileId且不是临时ID，直接使用
            if (fileFileId != null && !fileFileId.trim().isEmpty() && !fileFileId.startsWith("temp_")) {
                return fileFileId.trim();
            }

            // 2. 生成稳定的唯一ID：基于文件路径 + 文件名（不包含时间戳）
            // 这样相同文件多次上传会生成相同的fileId，能命中缓存
            String uniqueString = String.format("%s_%s",
                    filePath != null ? filePath : "",
                    fileName);

            // 3. 计算MD5哈希
            String hashCode = calculateMD5(uniqueString);
            String generatedId = "convert_" + hashCode;
            logger.debug("🔑 生成稳定的fileId - Input: {},  FileId: {}", uniqueString, generatedId);
            return generatedId;
        } catch (Exception e) {
            logger.error("❌ 生成fileId失败，使用时戳作为降级方案", e);
            // 降级处理：使用时戳 + 随机数（只在异常时）
            return System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        }
    }

    /**
     * 计算字符串的MD5哈希值
     */
    private String calculateMD5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 转为16进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            // 只取前16位
            return hexString.substring(0, 16);
        } catch (Exception e) {
            logger.error("❌ MD5计算失败", e);
            // 降级：使用hashCode
            return String.format("%08x", input.hashCode());
        }
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
    /**
     * 获取文件大小
     */
    public Long getFileSize(String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                return null;
            }
            File file = new File(filePath);
            return file.exists() ? file.length() : null;
        } catch (Exception e) {
            logger.warn("获取文件大小失败 - Path: {}", filePath, e);
            return null;
        }
    }
    
    /**
     * 检测字符串是否为乱码（包含Box Drawing字符）
     * 用于检测压缩包中文文件名的编码问题
     * 
     * 注：与containsGarbledCharacters功能相同，统一使用此方法
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
    
    /**
     * 检测压缩文件格式
     */
    public String detectArchiveFormat(String filePath) {
        String fileName = new File(filePath).getName().toLowerCase();

        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            return "tar.gz";
        } else if (fileName.endsWith(".tar")) {
            return "tar";
        } else if (fileName.endsWith(".zip")) {
            return "zip";
        } else if (fileName.endsWith(".jar")) {
            return "jar";
        } else if (fileName.endsWith(".war")) {
            return "war";
        } else if (fileName.endsWith(".ear")) {
            return "ear";
        } else if (fileName.endsWith(".rar")) {
            return "rar";
        } else if (fileName.endsWith(".7z")) {
            return "7z";
        }

        return "unknown";
    }
    
    /**
     * 检查是否包含中文字符
     */
    public boolean containsChineseCharacters(String text) {
        if (text == null) {
            return false;
        }
        return text.matches(".*[\u4e00-\u9fa5].*");
    }
    
    /**
     * 检查是否为中文系统环境
     */
    public boolean isChineseSystemEnvironment() {
        String userLanguage = System.getProperty("user.language", "");
        String userCountry = System.getProperty("user.country", "");
        return "zh".equals(userLanguage) || "CN".equals(userCountry);
    }
    
    /**
     * 智能编码检测：根据文件名和环境优化编码尝试顺序
     */
    public String[] detectOptimalEncodingOrder(File archiveFile) {
        String fileName = archiveFile.getName().toLowerCase();

        // 根据文件名含有中文或者系统环境优化顺序
        if (containsChineseCharacters(fileName) || isChineseSystemEnvironment()) {
            return new String[] { "GBK", "UTF-8", "GB2312", "ISO-8859-1" };
        } else {
            return new String[] { "UTF-8", "GBK", "GB2312", "ISO-8859-1" };
        }
    }
    
    /**
     * 检查是否应该跳过macOS压缩包的元数据文件
     * macOS在创建ZIP压缩包时会自动添加元数据文件，这些文件不是真正的文档，无法被转换引擎处理
     * 
     * @param entryName 压缩包内文件路径
     * @return 如果应该跳过返回true
     */
    public boolean shouldSkipMacOSMetadataFile(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        
        // 1. 跳过 __MACOSX 目录下的所有文件
        if (entryName.contains("__MACOSX")) {
            return true;
        }
        
        // 2. 跳过以 ._ 开头的隐藏文件（资源分支文件）
        String fileName = entryName;
        int lastSlash = entryName.lastIndexOf('/');
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
     * 判断是否为文本文件
     */
    public boolean isTextFile(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return java.util.Arrays.asList("txt", "java", "xml", "html", "css", "js", "json", "yml", "yaml",
                "properties", "md", "sql", "sh", "bat", "cmd", "log", "conf", "cfg").contains(extension);
    }
    
    /**
     * 确定文件类型
     */
    public String determineFileType(String fileName) {
        if (fileName == null) {
            return "unknown";
        }

        String extension = getFileExtension(fileName);
        if (extension.isEmpty()) {
            return "unknown";
        }

        switch (extension.toLowerCase()) {
            case "java":
                return "Java Source";
            case "class":
                return "Java Class";
            case "xml":
                return "XML";
            case "html":
            case "htm":
                return "HTML";
            case "css":
                return "CSS";
            case "js":
                return "JavaScript";
            case "json":
                return "JSON";
            case "properties":
                return "Properties";
            case "yml":
            case "yaml":
                return "YAML";
            case "txt":
                return "Text";
            case "md":
                return "Markdown";
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "bmp":
                return "Image";
            case "pdf":
                return "PDF";
            case "jar":
                return "JAR Archive";
            case "zip":
                return "ZIP Archive";
            case "war":
                return "WAR Archive";
            case "ear":
                return "EAR Archive";
            case "tar":
                return "TAR Archive";
            case "gz":
                return "GZIP Archive";
            case "rar":
                return "RAR Archive";
            case "7z":
                return "7Z Archive";
            default:
                return extension.toUpperCase() + " File";
        }
    }

     /**
     * 规范化路径：去除尾部斜杠，统一使用正斜杠
     */
    public String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // 统一使用正斜杠
        path = path.replace('\\', '/');

        // 去除尾部斜杠
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * 获取父目录路径
     * 
     * @param path 完整路径（已经规范化）
     * @return 父目录路径（不带尾部斜杠）
     */
    public String getParentPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // 查找最后一个/的位置
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex <= 0) {
            // 没有父目录或在根目录下
            return null;
        }

        // 返回父目录路径（不带尾部斜杠）
        return path.substring(0, lastSlashIndex);
    }
    
    /**
     * 获取文件扩展名
     */
    public String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 获取压缩方法名称
     */
    public String getCompressionMethod(int method) {
        switch (method) {
            case java.util.zip.ZipEntry.STORED:
                return "STORED";
            case java.util.zip.ZipEntry.DEFLATED:
                return "DEFLATED";
            default:
                return "UNKNOWN(" + method + ")";
        }
    }
    
    /**
     * 检查路径是否安全（防止路径穿越攻击）
     * 
     * @param baseDir 基础目录
     * @param entryPath 条目路径
     * @return true 如果路径安全
     */
    public boolean isPathSafe(Path baseDir, String entryPath) {
        try {
            // 规范化路径
            Path resolvedPath = baseDir.resolve(entryPath).normalize();
            
            // 检查解析后的路径是否在基础目录之内
            return resolvedPath.startsWith(baseDir.normalize());
        } catch (Exception e) {
            logger.warn("路径安全检查失败: {}", entryPath, e);
            return false;
        }
    }
}
