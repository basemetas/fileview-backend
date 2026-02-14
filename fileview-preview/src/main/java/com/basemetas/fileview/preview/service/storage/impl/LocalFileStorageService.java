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
package com.basemetas.fileview.preview.service.storage.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.basemetas.fileview.preview.service.storage.FileStorageService;
import com.basemetas.fileview.preview.service.url.BaseUrlProvider;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 本地文件存储服务实现
 * 
 * 适用场景：
 * - 单机部署环境
 * - 开发测试环境
 * - 文件存储在本地磁盘或挂载的网络磁盘(NAS/NFS)
 * 
 * @author 夫子
 */
@Service
@ConditionalOnProperty(name = "fileview.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    
    @Value("${fileview.storage.local.base-path:d:/myWorkSpace/fileview-backend/fileTemp}")
    private String basePath;
    
    @Value("${fileview.preview.url.base-url:http://127.0.0.1:8184/preview}")
    private String previewBaseUrl;
    
    @Value("${fileview.storage.local.path-mapping.enabled:false}")
    private boolean pathMappingEnabled;
    
    @Value("${fileview.storage.local.path-mapping.from:/var/app/fileview-backend}")
    private String pathMappingFrom;
    
    @Value("${fileview.storage.local.path-mapping.to:d:/myWorkSpace/fileview-backend}")
    private String pathMappingTo;
    
    @Autowired
    private BaseUrlProvider baseUrlProvider;
    
    @Override
    public String getFileUrl(String filePath) {
        return getFileUrl(filePath, null);
    }
    
    @Override
    public String getFileUrl(String filePath, String fileId) {
        try {
            // 获取物理路径
            String physicalPath = getPhysicalPath(filePath);
            
            // 生成相对路径 URL（不拼接 baseUrl）
            String encodedPath = URLEncoder.encode(physicalPath, StandardCharsets.UTF_8);
            String url = "/preview/api/file?filePath=" + encodedPath + "&t=" + System.currentTimeMillis();
            
            logger.debug("生成本地文件URL（相对路径）: {} -> {}", filePath, url);
            return url;
        } catch (Exception e) {
            logger.error("生成文件URL失败: {}", filePath, e);
            return null;
        }
    }
    
    @Override
    public boolean fileExists(String filePath) {
        try {
            String physicalPath = getPhysicalPath(filePath);
            File file = new File(physicalPath);
            boolean exists = file.exists() && file.isFile();
            
            if (!exists) {
                logger.debug("文件不存在: {} (物理路径: {})", filePath, physicalPath);
            }
            
            return exists;
        } catch (Exception e) {
            logger.warn("检查文件存在性失败: {}", filePath, e);
            return false;
        }
    }
    
    @Override
    public String getPhysicalPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        String normalizedPath = filePath;
        
        // 应用路径映射（如果启用）
        if (pathMappingEnabled) {
            // 检查是否已经是Windows路径格式（包含盘符）
            if (filePath.contains(":\\") || filePath.contains(":/")) {
                // 检查是否是复杂的错误映射路径（包含重复映射）
                if (filePath.contains(pathMappingFrom) || filePath.contains(pathMappingTo)) {
                    // 这种情况是错误的重复映射，我们需要提取正确的部分
                    // 例如: /var/app/fileview-backend/fileview-preview/d:/myWorkSpace/fileview-backend/fileTemp/preview/temp6.pdf
                    // 应该提取为: d:/myWorkSpace/fileview-backend/fileTemp/preview/temp6.pdf
                    
                    // 查找pathMappingTo的位置
                    int toIndex = filePath.indexOf(pathMappingTo);
                    if (toIndex >= 0) {
                        normalizedPath = filePath.substring(toIndex);
                        logger.debug("修复重复映射路径: {} -> {}", filePath, normalizedPath);
                    } else {
                        // 如果找不到，使用原始路径
                        normalizedPath = filePath;
                        logger.debug("无法修复重复映射路径，使用原始路径: {}", filePath);
                    }
                } else {
                    // 已经是Windows路径，直接使用
                    normalizedPath = filePath;
                    logger.debug("路径为Windows格式，直接使用: {}", filePath);
                }
            } 
            // 检查是否以映射前缀开始
            else if (filePath.startsWith(pathMappingFrom)) {
                // 执行路径映射
                String pathAfterFrom = filePath.substring(pathMappingFrom.length());
                normalizedPath = pathMappingTo + pathAfterFrom;
                normalizedPath = normalizedPath.replace("/", File.separator);
                logger.debug("路径映射: {} -> {}", filePath, normalizedPath);
            }
        }
        
        // 如果是绝对路径，直接使用
        File file = new File(normalizedPath);
        if (file.isAbsolute()) {
            return file.getAbsolutePath();
        }
        
        // 相对路径，基于basePath
        File baseDir = new File(basePath);
        File targetFile = new File(baseDir, normalizedPath);
        return targetFile.getAbsolutePath();
    }
    
    @Override
    public InputStream getFileStream(String filePath) throws IOException {
        String physicalPath = getPhysicalPath(filePath);
        File file = new File(physicalPath);
        
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + physicalPath);
        }
        
        return new FileInputStream(file);
    }
    
    @Override
    public String saveFile(String filePath, byte[] fileData) throws IOException {
        String physicalPath = getPhysicalPath(filePath);
        File file = new File(physicalPath);
        
        // 创建父目录
        File parentDir = file.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("无法创建目录: " + parentDir.getAbsolutePath());
        }
        
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(fileData);
        }
        
        logger.info("文件保存成功: {} (大小: {} bytes)", physicalPath, fileData.length);
        return getFileUrl(filePath);
    }
    
    @Override
    public boolean deleteFile(String filePath) {
        try {
            String physicalPath = getPhysicalPath(filePath);
            File file = new File(physicalPath);
            
            if (file.exists() && file.delete()) {
                logger.info("文件删除成功: {}", physicalPath);
                return true;
            } else {
                logger.warn("文件删除失败或不存在: {}", physicalPath);
                return false;
            }
        } catch (Exception e) {
            logger.error("删除文件异常: {}", filePath, e);
            return false;
        }
    }
    
    @Override
    public Long getFileSize(String filePath) {
        try {
            String physicalPath = getPhysicalPath(filePath);
            File file = new File(physicalPath);
            
            if (file.exists() && file.isFile()) {
                return file.length();
            }
        } catch (Exception e) {
            logger.warn("获取文件大小失败: {}", filePath, e);
        }
        return null;
    }
    
    @Override
    public String getStorageType() {
        return "local";
    }
}
