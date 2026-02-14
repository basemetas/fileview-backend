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
package com.basemetas.fileview.preview.service.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import com.basemetas.fileview.preview.utils.EncodingUtils;
import com.basemetas.fileview.preview.utils.HttpUtils;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 智能下载服务
 * 支持ETag和Last-Modified缓存机制，避免重复下载
 * 
 * @author 夫子
 */
@Service
public class SmartDownloadService {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartDownloadService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private FileDownloadService fileDownloadService;

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private EncodingUtils  encodingUtils;
    
    @Value("${fileview.network.download.smart-enabled:true}")
    private boolean smartDownloadEnabled;
    
    /**
     * 智能下载文件（支持ETag和Last-Modified）
     * 
     * @param fileUrl 文件URL
     * @param targetPath 目标路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param timeout 超时时间
     * @return 下载后的本地文件路径
     */
    public String smartDownload(String fileUrl, String targetPath, String username, 
                               String password, int timeout) throws Exception {
        // 1. 检查缓存的ETag和Last-Modified
        String urlHash = encodingUtils.calculateMD5(fileUrl);
        String etagKey = CacheKeyManager.buildEtagKey(urlHash);
        String lastModifiedKey = CacheKeyManager.buildLastModifiedKey(urlHash);
        
        String cachedEtag = (String) redisTemplate.opsForValue().get(etagKey);
        String cachedLastModified = (String) redisTemplate.opsForValue().get(lastModifiedKey);
        
        // ⭐ 优化：如果没有缓存元数据，直接下载跳过 HEAD
        if (cachedEtag == null && cachedLastModified == null) {
            logger.debug("无缓存元数据，跳过 HEAD 直接下载 - URL: {}", httpUtils.maskSensitiveUrl(fileUrl));
            String downloadedFilePath = fileDownloadService.downloadFile(
                fileUrl, targetPath, username, password, timeout);
            // 下载后会由 FileDownloadService 自动保存 ETag/Last-Modified
            return downloadedFilePath;
        }
        
        // 2. 发送HEAD请求检查文件是否已修改
        CacheInfo cacheInfo = checkFileModification(fileUrl, username, password, 
                                                   cachedEtag, cachedLastModified);
        
        // 3. 检查文件是否已修改
        boolean isModified = isFileModified(cachedEtag, cacheInfo.etag, 
                                          cachedLastModified, cacheInfo.lastModified);
        
        if (!isModified) {
            // 文件未修改，检查本地文件是否存在
            String localFilePath = getLocalFilePath(fileUrl, targetPath);
            if (localFilePath != null && new File(localFilePath).exists()) {
                logger.info("文件未修改，使用本地缓存 - URL: {}", httpUtils.maskSensitiveUrl(fileUrl));
                return localFilePath;
            }
        }
        
        // 4. 文件已修改或本地文件不存在，执行下载
        String downloadedFilePath = fileDownloadService.downloadFile(
            fileUrl, targetPath, username, password, timeout);
        
        // 5. 更新缓存信息
        if (cacheInfo.etag != null) {
            redisTemplate.opsForValue().set(etagKey, cacheInfo.etag, 7, TimeUnit.DAYS);
        }
        if (cacheInfo.lastModified != null) {
            redisTemplate.opsForValue().set(lastModifiedKey, cacheInfo.lastModified, 7, TimeUnit.DAYS);
        }
        
        return downloadedFilePath;
    }
    
    /**
     * 智能下载文件（带偏好参数）
     * 
     * @param fileUrl 文件URL
     * @param targetPath 目标路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param timeout 超时时间
     * @param useSmartDownload 调用方是否希望使用智能下载
     * @return 下载后的本地文件路径
     */
    public String smartDownload(String fileUrl, String targetPath, String username,
                                String password, int timeout, boolean useSmartDownload) throws Exception {
        return smartDownload(fileUrl, targetPath, username, password, timeout, useSmartDownload, null);
    }
    
    /**
     * 智能下载文件（带偏好参数和自定义文件名）
     * 
     * @param fileUrl 文件URL
     * @param targetPath 目标路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param timeout 超时时间
     * @param useSmartDownload 调用方是否希望使用智能下载
     * @param fileName 自定义文件名（可选，非空时将直接使用该名称，不复用旧文件）
     * @return 下载后的本地文件路径
     */
    public String smartDownload(String fileUrl, String targetPath, String username,
                                String password, int timeout, boolean useSmartDownload, String fileName) throws Exception {
        // 仅当调用方偏好为true 且 配置开关启用 且 未指定自定义文件名 时，才走智能下载
        if (useSmartDownload && smartDownloadEnabled && (fileName == null || fileName.trim().isEmpty())) {
            return smartDownload(fileUrl, targetPath, username, password, timeout);
        }
        // 否则直接下载（跳过ETag/Last-Modified检查），并在底层使用自定义文件名（如果有）
        return fileDownloadService.downloadFile(fileUrl, targetPath, username, password, timeout, fileName);
    }
    
    /**
     * 检查文件修改信息
     */
    private CacheInfo checkFileModification(String fileUrl, String username, String password,
                                          String cachedEtag, String cachedLastModified) throws Exception {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // 设置认证信息
            if (username != null && password != null) {
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }
            
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(10000);    // 10秒读取超时
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String currentEtag = connection.getHeaderField("ETag");
                String currentLastModified = connection.getHeaderField("Last-Modified");
                
                logger.debug("HTTP HEAD响应 - ETag: {}, Last-Modified: {}", currentEtag, currentLastModified);
                
                return new CacheInfo(currentEtag, currentLastModified);
            }
        } catch (Exception e) {
            logger.warn("检查文件修改信息时发生异常 - URL: {}", httpUtils.maskSensitiveUrl(fileUrl), e);
        }
        
        // 默认返回空值
        return new CacheInfo(null, null);
    }
    
    /**
     * 检查文件是否已修改
     */
    private boolean isFileModified(String cachedEtag, String currentEtag, 
                                  String cachedLastModified, String currentLastModified) {
        // 如果没有缓存信息，认为文件已修改
        if (cachedEtag == null && cachedLastModified == null) {
            return true;
        }
        
        // 检查ETag
        if (cachedEtag != null && currentEtag != null) {
            return !cachedEtag.equals(currentEtag);
        }
        
        // 检查Last-Modified
        if (cachedLastModified != null && currentLastModified != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                Date cachedDate = sdf.parse(cachedLastModified);
                Date currentDate = sdf.parse(currentLastModified);
                return currentDate.after(cachedDate);
            } catch (ParseException e) {
                logger.warn("解析Last-Modified时间失败", e);
            }
        }
        
        // 默认认为文件已修改
        return true;
    }
    
    /**
     * 获取本地文件路径
     */
    private String getLocalFilePath(String fileUrl, String targetPath) {
        try {
            String fileName = httpUtils.extractFileNameFromUrl(fileUrl);
            String localFilePath = Paths.get(targetPath, fileName).toString();
            
            // 检查文件是否存在
            if (new File(localFilePath).exists()) {
                return localFilePath;
            }
        } catch (Exception e) {
            logger.warn("获取本地文件路径失败 - URL: {}", httpUtils.maskSensitiveUrl(fileUrl), e);
        }
        return null;
    }
          
    
    /**
     * 缓存信息封装类
     */
    private static class CacheInfo {
        final String etag;
        final String lastModified;
        
        CacheInfo(String etag, String lastModified) {
            this.etag = etag;
            this.lastModified = lastModified;
        }
    }
}