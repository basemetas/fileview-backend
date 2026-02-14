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
package com.basemetas.fileview.convert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * 临时文件清理服务
 * 
 * 定期清理 LibreOffice 转换过程中产生的临时实例目录
 * 
 * @author 夫子
 */
@Service
public class TempFileCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(TempFileCleanupService.class);
    
    @Value("${libreoffice.temp.dir:}")
    private String tempDir;
    
    /**
     * 临时文件最大保留时间（毫秒）
     * 默认 1 小时，超过此时间的 instance 目录将被清理
     */
    @Value("${libreoffice.temp.cleanup.max-age-hours:1}")
    private int maxAgeHours;
    
    /**
     * 定时清理任务
     * 每小时执行一次
     */
    @Scheduled(cron = "${libreoffice.temp.cleanup.cron:0 0 * * * ?}")
    public void cleanupOldTempInstances() {
        if (tempDir == null || tempDir.trim().isEmpty()) {
            logger.debug("未配置 LibreOffice 临时目录，跳过清理");
            return;
        }
        
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists() || !tempDirFile.isDirectory()) {
            logger.debug("LibreOffice 临时目录不存在，跳过清理: {}", tempDir);
            return;
        }
        
        logger.info("🧹 开始清理 LibreOffice 临时实例目录: {}", tempDir);
        
        int deletedCount = 0;
        int failedCount = 0;
        long maxAgeMillis = maxAgeHours * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();
        
        try {
            File[] files = tempDirFile.listFiles();
            if (files == null || files.length == 0) {
                logger.info("临时目录为空，无需清理");
                return;
            }
            
            for (File file : files) {
                // 只清理 instance_ 开头的目录
                if (file.isDirectory() && file.getName().startsWith("instance_")) {
                    long fileAge = currentTime - file.lastModified();
                    
                    if (fileAge > maxAgeMillis) {
                        try {
                            logger.debug("清理过期的临时实例目录: {} (年龄: {} 小时)", 
                                       file.getName(), fileAge / (60 * 60 * 1000));
                            deleteDirectory(file);
                            deletedCount++;
                        } catch (Exception e) {
                            logger.warn("清理临时实例目录失败: {} - {}", file.getName(), e.getMessage());
                            failedCount++;
                        }
                    }
                }
            }
            
            logger.info("✅ LibreOffice 临时目录清理完成 - 已删除: {}, 失败: {}", deletedCount, failedCount);
            
        } catch (Exception e) {
            logger.error("清理 LibreOffice 临时目录时发生异常", e);
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        
        if (!directory.delete()) {
            throw new IOException("无法删除文件或目录: " + directory.getAbsolutePath());
        }
    }
}

