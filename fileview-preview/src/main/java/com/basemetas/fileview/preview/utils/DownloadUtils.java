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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

/**
 * 下载工具类
 * 提供下载相关的通用工具方法
 */
@Component
public class DownloadUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(DownloadUtils.class);
    
    @Autowired
    private EncodingUtils encodingUtils;
    
    @Autowired
    private HttpUtils httpUtils;
    
    // 禁用 SmartDownload 的域名黑名单
    @Value("${fileview.download.smart.blacklist-domains:}")
    private String blacklistDomainsConfig;
    
    // 小文件大小阈值（字节），低于此值跳过 HEAD
    @Value("${fileview.download.smart.small-file-threshold:1048576}")
    private long smallFileThreshold; // 默认 1MB
    
    private Set<String> blacklistDomains;
    
    /**
     * 计算字符串MD5哈希值
     */
    public String calculateMD5(String input) {
        return encodingUtils.calculateMD5(input);
    }
    
    /**
     * 屏蔽URL中的敏感信息（密码）
     */
    public String maskSensitiveUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }
    
    /**
     * 智能判断是否使用智能下载模式
     * 
     * 决策逻辑（优化版）：
     * 1. 只有 HTTP/HTTPS 协议支持智能下载（ETag/Last-Modified）
     * 2. 黑名单域名（响应慢/不支持 HEAD）跳过 SmartDownload
     * 3. 小文件（< 1MB）跳过 HEAD，直接 GET 更快
     * 4. 动态 API 路径（包含 api/download/export 等）不适合缓存
     * 5. 静态资源 URL（包含 common/static/public 等路径）适合缓存
     * 6. 低频更新文档类型（PDF/Office文档等）适合缓存
     * 
     * @param fileUrl 文件URL
     * @return true 使用智能下载，false 使用直接下载
     */
    public boolean shouldUseSmartDownload(String fileUrl) {
        return shouldUseSmartDownload(fileUrl, -1);
    }
    
    /**
     * 智能判断是否使用智能下载模式（带文件大小参数）
     * 
     * @param fileUrl 文件URL
     * @param fileSize 文件大小（字节），-1 表示未知
     * @return true 使用智能下载，false 使用直接下载
     */
    public boolean shouldUseSmartDownload(String fileUrl, long fileSize) {
        // 延迟初始化黑名单
        if (blacklistDomains == null) {
            initBlacklistDomains();
        }
        try {
            String lowerUrl = fileUrl.toLowerCase();
            
            // 1. 只支持 HTTP/HTTPS 协议
            if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
                return false;
            }
            
            // 2. 检查域名黑名单（优先级高）
            if (isBlacklistedDomain(fileUrl)) {
                logger.debug("域名在黑名单中，跳过 SmartDownload - URL: {}", maskSensitiveUrl(fileUrl));
                return false;
            }
            
            // 3. 小文件跳过 HEAD（< 1MB）
            if (fileSize >= 0 && fileSize < smallFileThreshold) {
                logger.debug("文件过小({} bytes < {} bytes)，跳过 SmartDownload", fileSize, smallFileThreshold);
                return false;
            }
            
            // 4. 动态 API 路径不适合缓存
            if (lowerUrl.contains("/api/") || 
                lowerUrl.contains("/download?") || 
                lowerUrl.contains("/export?") || 
                lowerUrl.contains("/generate?") ||
                lowerUrl.contains("action=")) {
                return false;
            }
            
            // 5. 静态资源路径适合缓存
            if (lowerUrl.contains("/static/") || 
                lowerUrl.contains("/public/") || 
                lowerUrl.contains("/common/") ||
                lowerUrl.contains("/assets/") ||
                lowerUrl.contains("/resources/")) {
                return true;
            }
            
            // 6. 根据文件扩展名判断：低频更新文档适合缓存
            String fileName = httpUtils.extractSimpleFileNameFromUrl(lowerUrl);
            if (fileName != null) {
                // Office 文档
                if (fileName.endsWith(".pdf") || 
                    fileName.endsWith(".doc") || fileName.endsWith(".docx") ||
                    fileName.endsWith(".xls") || fileName.endsWith(".xlsx") ||
                    fileName.endsWith(".ppt") || fileName.endsWith(".pptx") ||
                    fileName.endsWith(".odt") || fileName.endsWith(".ods") ||
                    fileName.endsWith(".odp")) {
                    return true;
                }
                
                // 图片/视频文件
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                    fileName.endsWith(".bmp") || fileName.endsWith(".svg") ||
                    fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                    fileName.endsWith(".mov")) {
                    return true;
                }
                
                // CAD/设计文件
                if (fileName.endsWith(".dwg") || fileName.endsWith(".dxf")) {
                    return true;
                }
            }
            
            // 7. 默认使用直接下载（性能优先）
            return false;
            
        } catch (Exception e) {
            logger.warn("判断下载模式失败，使用直接下载 - URL: {}", 
                maskSensitiveUrl(fileUrl), e);
            return false;
        }
    }
    
    /**
     * 初始化黑名单域名集合
     */
    private synchronized void initBlacklistDomains() {
        if (blacklistDomains != null) {
            return;
        }
        blacklistDomains = new HashSet<>();
        if (blacklistDomainsConfig != null && !blacklistDomainsConfig.trim().isEmpty()) {
            String[] domains = blacklistDomainsConfig.split(",");
            for (String domain : domains) {
                String trimmed = domain.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    blacklistDomains.add(trimmed);
                }
            }
            logger.info("📋 SmartDownload 黑名单域名: {}", blacklistDomains);
        }
    }
    
    /**
     * 检查 URL 是否在黑名单中
     */
    private boolean isBlacklistedDomain(String fileUrl) {
        if (blacklistDomains == null || blacklistDomains.isEmpty()) {
            return false;
        }
        try {
            java.net.URL url = new java.net.URL(fileUrl);
            String host = url.getHost().toLowerCase();
            return blacklistDomains.contains(host);
        } catch (Exception e) {
            return false;
        }
    }
}