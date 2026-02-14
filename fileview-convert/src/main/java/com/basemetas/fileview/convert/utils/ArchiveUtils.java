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

import com.basemetas.fileview.convert.strategy.model.ArchiveInfo;
import com.basemetas.fileview.convert.strategy.model.ArchiveTreeNode;
import com.itextpdf.io.exceptions.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.basemetas.fileview.convert.strategy.model.ArchiveEntryInfo;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 压缩包工具类
 * 
 * 集中管理压缩包解析中的通用方法：
 * - 创建档案信息
 * - 计算统计信息
 * - 文件类型判断
 * - macOS 元数据文件过滤
 * - 工具方法
 * 
 * @author 夫子
 */
@Component
public class ArchiveUtils {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveUtils.class);

    @Autowired
    private FileUtils fileUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 档案信息创建 ====================

    /**
     * 创建基本的档案信息
     * 
     * @param archiveFile 压缩文件
     * @return 档案信息
     */
    public ArchiveInfo createArchiveInfo(File archiveFile) {
        ArchiveInfo info = new ArchiveInfo();
        info.setFileName(archiveFile.getName());
        info.setFilePath(archiveFile.getAbsolutePath());
        info.setFileSize(archiveFile.length());
        info.setLastModified(DateTimeUtils.toUTCDate(new Date(archiveFile.lastModified())));
        info.setArchiveFormat(fileUtils.detectArchiveFormat(archiveFile.getAbsolutePath()));
        return info;
    }

    /**
     * 创建基本的档案信息（用于 SevenZipParserService）
     * 
     * @param archiveFile 压缩文件
     * @param format      档案格式（如 "7Z"）
     * @return 档案信息
     */
    public static ArchiveInfo createArchiveInfo(File archiveFile, String format) {
        ArchiveInfo info = new ArchiveInfo();
        info.setFileName(archiveFile.getName());
        info.setFilePath(archiveFile.getAbsolutePath());
        info.setFileSize(archiveFile.length());
        info.setLastModified(DateTimeUtils.toUTCDate(new Date(archiveFile.lastModified())));
        info.setArchiveFormat(format);
        return info;
    }

    /**
     * 从 Apache Commons ArchiveEntry 创建条目信息
     * 
     * @param entry Apache Commons 的 ArchiveEntry
     * @return 档案条目信息
     */
    public ArchiveEntryInfo createArchiveEntryInfo(ArchiveEntry entry) {
        ArchiveEntryInfo entryInfo = new ArchiveEntryInfo();
        entryInfo.setName(entry.getName());

        long size = entry.getSize();
        if (size < 0) {
            size = 0;
        }
        entryInfo.setSize(size);

        entryInfo.setDirectory(entry.isDirectory());
        entryInfo.setLastModified(
                entry.getLastModifiedDate() != null ? DateTimeUtils.toUTCDate(entry.getLastModifiedDate()) : null);

        if (!entry.isDirectory()) {
            entryInfo.setFileType(fileUtils.determineFileType(entry.getName()));
        }

        return entryInfo;
    }

    // ==================== 统计信息计算 ====================

    /**
     * 计算统计信息
     * 
     * @param info 档案信息
     */
    public static void calculateStatistics(ArchiveInfo info) {
        if (info.getEntries() == null) {
            return;
        }

        int fileCount = 0;
        int directoryCount = 0;
        long totalUncompressedSize = 0;
        long totalCompressedSize = 0;

        for (ArchiveEntryInfo entry : info.getEntries()) {
            if (entry.isDirectory()) {
                directoryCount++;
            } else {
                fileCount++;
                totalUncompressedSize += entry.getSize();
                totalCompressedSize += entry.getCompressedSize();
            }
        }

        info.setTotalEntries(info.getEntries().size());
        info.setFileCount(fileCount);
        info.setDirectoryCount(directoryCount);

        if (totalUncompressedSize > 0 && totalCompressedSize > 0) {
            info.setCompressionRatio((double) totalCompressedSize / totalUncompressedSize);
        }
    }

    // ==================== 文件类型判断 ====================

    /**
     * 确定文件类型（简化版，用于不依赖 FileUtils 的场景）
     * 
     * @param fileName 文件名
     * @return 文件类型描述
     */
    public static String determineFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "Unknown";
        }

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "Unknown";
        }

        String extension = fileName.substring(lastDot + 1).toLowerCase();

        // 简单的文件类型映射
        switch (extension) {
            case "txt":
                return "Text";
            case "pdf":
                return "PDF";
            case "doc":
            case "docx":
                return "Word";
            case "xls":
            case "xlsx":
                return "Excel";
            case "ppt":
            case "pptx":
                return "PowerPoint";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
                return "Image";
            case "zip":
            case "rar":
            case "7z":
                return "Archive";
            default:
                return extension.toUpperCase();
        }
    }

    // ==================== macOS 元数据文件过滤 ====================

    /**
     * 是否跳过 macOS 元数据文件
     * 
     * @param fileName 文件名
     * @return true 如果是 macOS 元数据文件
     */
    public static boolean shouldSkipMacOSMetadataFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.contains("__MACOSX") ||
                fileName.contains(".DS_Store") ||
                fileName.startsWith("._");
    }

    // ==================== 工具方法 ====================

    /**
     * 安全地将 Object 转换为 Long
     * 
     * @param value 待转换的值
     * @return Long 值，转换失败返回 null
     */
    public static Long safeLongCast(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 检测字符串是否为乱码
     * 
     * @param str 待检测字符串
     * @return true 如果是乱码
     */
    public static boolean isMessyCode(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            // 检测方框字符（Unicode Box Drawing 范围）
            if (c >= '\u2500' && c <= '\u257F') {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析外部命令返回的日期字符串
     * 格式: 2023-01-15 10:30:45
     * 
     * @param dateStr 日期字符串
     * @return Date 对象，解析失败返回 null
     */
    public static Date parseDateFromExternal(String dateStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return DateTimeUtils.toUTCDate(sdf.parse(dateStr));
        } catch (Exception e) {
            logger.debug("解析日期字符串失败: {}", dateStr);
            return null;
        }
    }

    /**
     * 将解析结果写入JSON文件（支持嵌套压缩文件信息）
     * 只输出树形结构
     * @throws java.io.IOException 
     * @throws FileNotFoundException 
     */
    public void writeArchiveInfoToJson(ArchiveInfo archiveInfo, String jsonPath) throws IOException, FileNotFoundException, java.io.IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();

        // 基本信息
        rootNode.put("fileName", archiveInfo.getFileName());
        rootNode.put("filePath", archiveInfo.getFilePath());
        rootNode.put("fileSize", archiveInfo.getFileSize());
        rootNode.put("archiveFormat", archiveInfo.getArchiveFormat());
        // 🔑 关键改进：使用UTC格式的时间
        rootNode.put("lastModified", DateTimeUtils.toUTCString(archiveInfo.getLastModified()));
        rootNode.put("totalEntries", archiveInfo.getTotalEntries());
        rootNode.put("fileCount", archiveInfo.getFileCount());
        rootNode.put("directoryCount", archiveInfo.getDirectoryCount());

        if (archiveInfo.getCompressionRatio() > 0) {
            rootNode.put("compressionRatio", archiveInfo.getCompressionRatio());
        }

        // 统计信息
        ObjectNode statistics = objectMapper.createObjectNode();
        Map<String, Integer> fileTypeCount = new HashMap<>();

        for (ArchiveEntryInfo entry : archiveInfo.getEntries()) {
            if (!entry.isDirectory()) {
                String fileType = entry.getFileType();
                // 🔑 防止null值作为key
                if (fileType == null || fileType.isEmpty()) {
                    fileType = "unknown";
                }
                fileTypeCount.put(fileType, fileTypeCount.getOrDefault(fileType, 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> typeCount : fileTypeCount.entrySet()) {
            statistics.put(typeCount.getKey(), typeCount.getValue());
        }
        rootNode.set("fileTypeStatistics", statistics);

        // 文件列表 - 只输出树形结构
        List<ArchiveTreeNode> treeNodes = buildTreeStructure(archiveInfo.getEntries());
        ArrayNode treeArray = objectMapper.createArrayNode();
        for (ArchiveTreeNode node : treeNodes) {
            treeArray.add(createTreeNodeObject(node));
        }
        rootNode.set("entries", treeArray);

        // 写入JSON文件，使用UTF-8编码确保中文字符正确显示
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(jsonPath), StandardCharsets.UTF_8)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, rootNode);
        }

        logger.info("JSON file written successfully: {}", jsonPath);
    }

    /**
     * 将平铺的条目列表转换为树形结构
     * 
     * @param entries 平铺的条目列表
     * @return 树形结构的根节点列表
     */
    private List<ArchiveTreeNode> buildTreeStructure(List<ArchiveEntryInfo> entries) {
        // 创建根节点列表
        List<ArchiveTreeNode> rootNodes = new ArrayList<>();

        // 创建路径到节点的映射，用于快速查找父节点
        Map<String, ArchiveTreeNode> pathToNodeMap = new HashMap<>();

        // 首先创建所有节点
        for (ArchiveEntryInfo entry : entries) {
            ArchiveTreeNode node = new ArchiveTreeNode(entry);
            // 使用规范化的路径作为key（去除尾部斜杠）
            String normalizedPath = fileUtils.normalizePath(entry.getName());
            pathToNodeMap.put(normalizedPath, node);
        }

        // 然后建立父子关系
        for (ArchiveEntryInfo entry : entries) {
            String normalizedPath = fileUtils.normalizePath(entry.getName());
            ArchiveTreeNode node = pathToNodeMap.get(normalizedPath);

            // 查找父目录路径
            String parentPath = fileUtils.getParentPath(normalizedPath);

            if (parentPath == null || parentPath.isEmpty()) {
                // 没有父目录，这是根节点
                rootNodes.add(node);
            } else {
                // 有父目录，添加到父节点的子节点列表中
                ArchiveTreeNode parentNode = pathToNodeMap.get(parentPath);
                if (parentNode != null) {
                    parentNode.addChild(node);
                } else {
                    // 父节点不存在，可能是根节点
                    rootNodes.add(node);
                }
            }
        }

        return rootNodes;
    }

    /**
     * 将树形节点转换为JSON节点
     * 
     * @param node 树形节点
     * @return JSON节点
     */
    private ObjectNode createTreeNodeObject(ArchiveTreeNode node) {
        ObjectNode nodeObject = objectMapper.createObjectNode();

        // 基本属性（防止null值导致序列化失败）
        if (node.getName() != null) {
            nodeObject.put("name", node.getName());
        } else {
            nodeObject.put("name", "");
        }

        if (node.getFullPath() != null) {
            nodeObject.put("fullPath", node.getFullPath());
        } else {
            nodeObject.put("fullPath", "");
        }

        nodeObject.put("isDirectory", node.isDirectory());
        nodeObject.put("size", node.getSize());

        if (!node.isDirectory()) {
            // 文件类型字段可能为null，需要判空
            if (node.getFileType() != null && !node.getFileType().isEmpty()) {
                nodeObject.put("fileType", node.getFileType());
            }

            if (node.getCompressedSize() > 0) {
                nodeObject.put("compressedSize", node.getCompressedSize());
            }
            if (node.getMethod() != null && !node.getMethod().isEmpty()) {
                nodeObject.put("compressionMethod", node.getMethod());
            }
            if (node.getCrc() != 0) {
                nodeObject.put("crc", node.getCrc());
            }
        }

        // 🔑 关键改进：使用UTC格式的时间
        if (node.getLastModified() != null) {
            nodeObject.put("lastModified", DateTimeUtils.toUTCString(node.getLastModified()));
        }

        if (node.getContentPreview() != null && !node.getContentPreview().isEmpty()) {
            nodeObject.put("contentPreview", node.getContentPreview());
        }

        // 递归添加子节点（仅对目录）
        if (node.isDirectory() && node.getChildren() != null && !node.getChildren().isEmpty()) {
            ArrayNode childrenArray = objectMapper.createArrayNode();
            for (ArchiveTreeNode child : node.getChildren()) {
                childrenArray.add(createTreeNodeObject(child));
            }
            nodeObject.set("children", childrenArray);
        }

        return nodeObject;
    }
}
