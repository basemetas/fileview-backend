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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import com.basemetas.fileview.preview.utils.DownloadUtils;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 下载去重服务
 * 提供文件下载、去重检查、任务管理等完整功能
 * 基于文件内容哈希值避免重复下载相同内容的文件
 * 
 * @author 夫子
 */
@Service
public class DownloadDeduplicationService {
    private static final Logger logger = LoggerFactory.getLogger(DownloadDeduplicationService.class);
    
    @Autowired
    private SmartDownloadService smartDownloadService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private DownloadUtils downloadUtils;
    
    /**
     * 综合下载（整合去重、任务检查、智能下载）
     * 
     * @param fileUrl    文件URL
     * @param targetPath 目标路径
     * @param username   用户名（可选）
     * @param password   密码（可选）
     * @param timeout    超时时间
     * @return 下载结果
     * @throws Exception 下载过程中可能发生的异常
     */
    public DownloadResult downloadWithDeduplication(String fileUrl, String targetPath, String username,
            String password, int timeout) throws Exception {
        return downloadWithDeduplication(fileUrl, targetPath, username, password, timeout, true, null);
    }

    /**
     * 综合下载（整合去重、任务检查、智能下载）
     * 
     * @param fileUrl    文件URL
     * @param targetPath 目标路径
     * @param username   用户名（可选）
     * @param password   密码（可选）
     * @param timeout    超时时间
     * @param useSmartDownload 是否使用智能下载（检查ETag和Last-Modified）
     * @param fileName   自定义文件名（可选，非空时将直接使用该名称，不复用旧文件）
     * @return 下载结果
     * @throws Exception 下载过程中可能发生的异常
     */
    public DownloadResult downloadWithDeduplication(String fileUrl, String targetPath, String username,
            String password, int timeout, boolean useSmartDownload, String fileName) throws Exception {

        boolean hasCustomFileName = fileName != null && !fileName.trim().isEmpty();

        // 1. 检查是否已有相同URL的下载任务正在进行（仅在未指定自定义文件名时复用）
        if (!hasCustomFileName) {
            String existingTaskId = checkExistingDownloadTask(fileUrl);
            if (existingTaskId != null) {
                return DownloadResult.pending(existingTaskId);
            }
        }

        // 2. 基于内容哈希检查文件是否已存在（仅在未指定自定义文件名时复用）
        String existingFilePath = null;
        if (!hasCustomFileName) {
            existingFilePath = checkFileExistsByHash(fileUrl);
        }
        if (existingFilePath != null) {
            return DownloadResult.success(existingFilePath);
        }

        // 3. 使用智能下载或直接下载（统一由SmartDownloadService根据偏好与配置判断）
        String downloadedFilePath = smartDownloadService.smartDownload(
                fileUrl, targetPath, username, password, timeout, useSmartDownload, fileName);

        // 4. 记录文件哈希值
        recordFileHash(fileUrl, downloadedFilePath);

        return DownloadResult.success(downloadedFilePath);
    }

    /**
     * 检查是否已有相同URL的下载任务
     * 
     * @param fileUrl 文件URL
     * @return 正在进行的任务ID，如果没有则返回null
     */
    private String checkExistingDownloadTask(String fileUrl) {
        try {
            String urlHash = downloadUtils.calculateMD5(fileUrl);
            String taskKey = CacheKeyManager.buildDownloadTaskKey(urlHash);

            return (String) redisTemplate.opsForValue().get(taskKey);
        } catch (Exception e) {
            logger.warn("检查现有下载任务时发生异常 - URL: {}", downloadUtils.maskSensitiveUrl(fileUrl), e);
            return null;
        }
    }

    /**
     * 检查文件是否已存在（基于内容哈希）
     * 
     * @param fileUrl 文件URL
     * @return 已存在的文件路径，如果不存在则返回null
     */
    public String checkFileExistsByHash(String fileUrl) {
        try {
            // 生成文件URL的唯一标识
            String urlHash = downloadUtils.calculateMD5(fileUrl);
            String pathKey = CacheKeyManager.buildFilePathMappingKey(urlHash);
            
            // 检查Redis中是否已存在该文件的哈希值
            String fileHash = (String) redisTemplate.opsForValue().get(pathKey);
            if (fileHash != null) {
                String hashKey = CacheKeyManager.buildFileHashKey(fileHash);
                String existingFilePath = (String) redisTemplate.opsForValue().get(hashKey);
                if (existingFilePath != null && new File(existingFilePath).exists()) {
                    logger.info("文件已存在，避免重复下载 - URL: {}, Path: {}", downloadUtils.maskSensitiveUrl(fileUrl), existingFilePath);
                    return existingFilePath;
                }
            }
        } catch (Exception e) {
            logger.warn("检查文件去重时发生异常 - URL: {}", downloadUtils.maskSensitiveUrl(fileUrl), e);
        }
        return null;
    }
    
    /**
     * 记录文件哈希值
     * 
     * @param fileUrl 文件URL
     * @param filePath 文件路径
     */
    public void recordFileHash(String fileUrl, String filePath) {
        try {
            // 计算文件内容的MD5哈希值
            String fileHash = downloadUtils.calculateMD5(filePath);
            String urlHash = downloadUtils.calculateMD5(fileUrl);
            
            // 存储文件哈希值和路径的映射关系
            String hashKey = CacheKeyManager.buildFileHashKey(fileHash);
            String pathKey = CacheKeyManager.buildFilePathMappingKey(urlHash);
            
            // 设置过期时间（例如7天）
            redisTemplate.opsForValue().set(hashKey, filePath, 7, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(pathKey, fileHash, 7, TimeUnit.DAYS);
            
            logger.info("记录文件哈希值 - URL: {}, Hash: {}, Path: {}", downloadUtils.maskSensitiveUrl(fileUrl), fileHash, filePath);
        } catch (Exception e) {
            logger.warn("记录文件哈希值时发生异常 - URL: {}, Path: {}", downloadUtils.maskSensitiveUrl(fileUrl), filePath, e);
        }
    }
}