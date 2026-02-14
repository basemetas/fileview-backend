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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 下载任务管理服务
 */
@Service
public class DownloadTaskManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadTaskManager.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final long TASK_EXPIRE_TIME = 24 * 60 * 60; // 24小时过期
    
    /**
     * 创建下载任务
     */
    public DownloadTask createTask(FilePreviewRequest request) {
        DownloadTask task = new DownloadTask();
        task.setTaskId(request.getFileId()); // 使用fileId作为taskId
        task.setFileId(request.getFileId());
        task.setNetworkFileUrl(request.getNetworkFileUrl());
        task.setNetworkUsername(request.getNetworkUsername());
        task.setNetworkPassword(request.getNetworkPassword());
        task.setDownloadTargetPath(request.getDownloadTargetPath());
        task.setDownloadTimeout(request.getDownloadTimeout());
        task.setFileName(request.getFileName());
        task.setPassWord(request.getPassword()); // 🔑 设置压缩包密码
        task.setClientId(request.getClientId()); // 🔑 设置客户端标识
        
        // 存储到Redis
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + task.getFileId(); // 使用fileId作Redis键
        redisTemplate.opsForValue().set(key, task, TASK_EXPIRE_TIME, TimeUnit.SECONDS);
        
        logger.info("创建下载任务 - FileId: {}", task.getFileId());
        return task;
    }
    
    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String fileId, DownloadTaskStatus status) {
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + fileId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            DownloadTask task = convertToDownloadTask(obj);
            if (task != null) {
                task.setStatus(status);
                if (status == DownloadTaskStatus.DOWNLOADED || status == DownloadTaskStatus.FAILED) {
                    task.setFinishedTime(System.currentTimeMillis());
                }
                redisTemplate.opsForValue().set(key, task, TASK_EXPIRE_TIME, TimeUnit.SECONDS);
            }
        }
    }
    
    /**
     * 更新任务完成状态
     */
    public void updateTaskSuccess(String fileId, String localFilePath) {
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + fileId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            DownloadTask task = convertToDownloadTask(obj);
            if (task != null) {
                task.setStatus(DownloadTaskStatus.DOWNLOADED);
                task.setLocalFilePath(localFilePath);
                task.setFinishedTime(System.currentTimeMillis());
                task.setProgress(100.0);
                redisTemplate.opsForValue().set(key, task, TASK_EXPIRE_TIME, TimeUnit.SECONDS);
            }
        }
    }
    
    /**
     * 更新任务失败状态
     */
    public void updateTaskFailed(String fileId, String errorMessage) {
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + fileId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            DownloadTask task = convertToDownloadTask(obj);
            if (task != null) {
                task.setStatus(DownloadTaskStatus.FAILED);
                task.setErrorMessage(errorMessage);
                task.setFinishedTime(System.currentTimeMillis());
                redisTemplate.opsForValue().set(key, task, TASK_EXPIRE_TIME, TimeUnit.SECONDS);
            }
        }
    }
    
    /**
     * 更新任务
     */
    public void updateTask(DownloadTask task) {
        if (task == null || task.getFileId() == null) {
            logger.warn("⚠️ 任务或fileId为空，无法更新");
            return;
        }
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + task.getFileId();
        redisTemplate.opsForValue().set(key, task, TASK_EXPIRE_TIME, TimeUnit.SECONDS);
        logger.debug("✅ 更新下载任务 - FileId: {}", task.getFileId());
    }
    
    /**
     * 获取任务状态
     */
    public DownloadTask getTask(String fileId) {
        String key = CacheKeyManager.DOWNLOAD_TASK_PREFIX + fileId;
        Object obj = redisTemplate.opsForValue().get(key);
        return convertToDownloadTask(obj);
    }
    
    /**
     * 将Redis中的对象转换为DownloadTask对象
     */
    private DownloadTask convertToDownloadTask(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof DownloadTask) {
            return (DownloadTask) obj;
        }
        
        // 如果是从Redis中反序列化的对象，可能是一个Map
        if (obj instanceof Map) {
            try {
                // 使用Jackson将Map转换为DownloadTask对象
                return objectMapper.convertValue(obj, DownloadTask.class);
            } catch (Exception e) {
                logger.error("转换DownloadTask对象失败", e);
                return null;
            }
        }
        
        logger.warn("无法转换对象类型: {}", obj.getClass().getName());
        return null;
    }


}