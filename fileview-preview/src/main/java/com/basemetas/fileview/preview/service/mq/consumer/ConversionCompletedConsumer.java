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
package com.basemetas.fileview.preview.service.mq.consumer;

import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.service.cache.CacheWriteService;
import com.basemetas.fileview.preview.service.storage.FileStorageService;
import com.basemetas.fileview.preview.service.url.PreviewUrlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.service.storage.impl.LocalFileStorageService;
import java.util.Map;

/**
 * 转换完成事件消费者（架构优化版）核心处理类
 * 只负责处理事件 Map，不直接绑定具体MQ实现。
 */
@Component
public class ConversionCompletedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ConversionCompletedConsumer.class);

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PreviewUrlService previewUrlService;

    @Autowired
    private CacheWriteService cacheWriteService;

    public void onMessage(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("type");
            String fileId = (String) event.get("fileId");

            // 参数验证
            if (eventType == null || eventType.trim().isEmpty()) {
                logger.warn("⚠️ 事件类型为空");
                throw FileViewException.of(
                        ErrorCode.INVALID_PARAMETER,
                        "事件类型不能为空");
            }

            if (fileId == null || fileId.trim().isEmpty()) {
                logger.warn("⚠️ 文件ID为空");
                throw FileViewException.of(
                        ErrorCode.MISSING_REQUIRED_PARAMETER,
                        "文件ID不能为空");
            }

            logger.info("📨 收到转换事件 - Type: {}, FileId: {}", eventType, fileId);

            if ("CONVERSION_COMPLETED".equals(eventType)) {
                handleConversionCompleted(event);
            } else if ("CONVERSION_FAILED".equals(eventType)) {
                handleConversionFailed(event);
            } else {
                logger.warn("⚠️ 未知的事件类型: {}", eventType);
                throw FileViewException.of(
                        ErrorCode.INVALID_PARAMETER,
                        "不支持的事件类型: " + eventType);
            }

        } catch (FileViewException e) {
            logger.error("❌ 处理转换事件失败 - 错误码: {}, 消息: {}",
                    e.getErrorCode().getCode(), e.getMessage());
            // Consumer层不抛出异常，避免消息重试
        } catch (Exception e) {
            logger.error("💥 处理转换事件异常", e);
            // 记录未知异常，但不抛出
        }
    }

    /**
     * 处理转换完成事件
     */
    private void handleConversionCompleted(Map<String, Object> event) {
        String fileId = (String) event.get("fileId");
        long startTime = System.currentTimeMillis(); // 记录开始时间

        try {
            // 根据统一的数据结构获取数据
            Map<String, Object> unifiedInfo = (Map<String, Object>) event.get("unifiedInfo");
            if (unifiedInfo == null) {
                logger.error("❌ 统一预览信息为空 - FileId: {}", fileId);
                throw FileViewException.of(
                        ErrorCode.CONVERSION_RESULT_NOT_FOUND,
                        "统一预览信息为空").withFileId(fileId);
            }

            // 获取转换后的文件路径
            String convertedFilePath = (String) unifiedInfo.get("convertedFilePath");
            String originalFileType = (String) unifiedInfo.get("originalFileType");
            String targetFormat = (String) unifiedInfo.get("targetFormat");
            logger.info("✅ 转换完成 - FileId: {}, ConvertedPath: {}, Format: {} -> {}",
                    fileId, convertedFilePath, originalFileType, targetFormat);

            // 使用存储服务生成预览URL（相对地址）
            String previewUrl = null;
            Long fileSize = null;

            if (convertedFilePath != null && !convertedFilePath.trim().isEmpty()) {
                try {
                    // 检查文件是否存在
                    if (fileStorageService.fileExists(convertedFilePath)) {
                        // 使用存储服务获取预览URL（相对路径）
                        previewUrl = fileStorageService.getFileUrl(convertedFilePath, fileId);
                        // 获取文件大小
                        fileSize = fileStorageService.getFileSize(convertedFilePath);               
                    } else {
                        logger.warn("⚠️ 转换后的文件不存在: {}", convertedFilePath);
                        // 尝试获取物理路径(可能需要路径映射)
                        String physicalPath = fileStorageService.getPhysicalPath(convertedFilePath);
                        if (physicalPath != null && !physicalPath.equals(convertedFilePath)) {
                            logger.info("🔄 尝试使用物理路径: {}", physicalPath);
                            if (fileStorageService.fileExists(physicalPath)) {
                                previewUrl = fileStorageService.getFileUrl(physicalPath, fileId);
                                fileSize = fileStorageService.getFileSize(physicalPath);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("❌ 存储服务操作失败: {}", e.getMessage(), e);
                    // 降级处理:使用传统方式
                    previewUrl = previewUrlService.generatePreviewUrl(fileId, convertedFilePath);
                }
            }

            // 如果存储服务未生成URL,使用降级方案
            if (previewUrl == null || previewUrl.trim().isEmpty()) {
                previewUrl = previewUrlService.generatePreviewUrl(fileId, convertedFilePath);
            }

            // 🔑 检查是否为多页文件
            Boolean isMultiPage = (Boolean) unifiedInfo.get("isMultiPage");
            Integer totalPages = (Integer) unifiedInfo.get("totalPages");
            String pagesDirectory = (String) unifiedInfo.get("pagesDirectory");

            if (isMultiPage != null && isMultiPage && totalPages != null && totalPages > 0) {
                // 处理多页文件
                cacheWriteService.handleMultiPageFile(fileId, targetFormat, pagesDirectory, totalPages,
                        unifiedInfo, startTime);
                return; // 多页文件单独处理，直接返回
            }

            // 🔑 关键修复:将生成的预览URL更新到Redis缓存
             cacheWriteService.updatePreviewUrlToCache(fileId, targetFormat, previewUrl, fileSize, unifiedInfo);

        } catch (FileViewException e) {
            logger.error("❌ 处理转换完成事件失败 - 错误码: {}, 消息: {}",
                    e.getErrorCode().getCode(), e.getMessage());
            // Consumer层不抛出异常，避免消息重试
        } catch (Exception e) {
            logger.error("💥 处理转换完成事件异常 - FileId: {}", fileId, e);
        }
    }

    /**
     * 处理转换失败事件
     */
    private void handleConversionFailed(Map<String, Object> event) {
        String fileId = (String) event.get("fileId");
        String error = (String) event.get("error");
        Object errorCode = event.get("errorCode");

        try {
            logger.error("❌ 转换失败 - FileId: {}, Error: {}, ErrorCode: {}", fileId, error, errorCode);
            // 记录转换失败信息，便于监控和追踪
            if (errorCode != null) {
                logger.info("📊 转换失败详情 - FileId: {}, 错误码: {}, 错误消息: {}",
                        fileId, errorCode, error);
            }
            // 🔑 关键修复：更新Redis缓存，将转换状态设置为失败
            String targetFormat = (String) event.get("targetFormat");
            String originalFileFormat = (String) event.get("originalFileFormat");
            if (targetFormat != null && !targetFormat.trim().isEmpty()) {
                 cacheWriteService.updateFailedStatusToCache(fileId, targetFormat, error, errorCode, originalFileFormat);
            } else {
                logger.warn("⚠️ 未能获取目标格式，无法更新缓存 - FileId: {}", fileId);
            }

            logger.info("ℹ️ 转换失败事件处理完成 - FileId: {}", fileId);

        } catch (Exception e) {
            logger.error("💥 处理转换失败事件异常 - FileId: {}", fileId, e);
        }
    }
}