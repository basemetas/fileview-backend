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
package com.basemetas.fileview.convert.service.mq.consumer;

import com.basemetas.fileview.convert.service.mq.event.EventChannel;
import com.basemetas.fileview.convert.service.mq.producer.EventPublisher;
import com.basemetas.fileview.convert.service.mq.event.FileEvent;
import com.basemetas.fileview.convert.strategy.FileConvertContext;
import com.basemetas.fileview.convert.utils.FileUtils;
import com.basemetas.fileview.convert.service.cache.ConvertResultCacheStrategy;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.model.ConvertResultInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件事件消费者核心处理类
 * 只负责处理 FileEvent，不直接绑定具体MQ实现。
 */
@Component
public class FileEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(FileEventConsumer.class);

    @Autowired
    private FileConvertContext fileConvertContext;

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private ConvertResultCacheStrategy convertResultCacheStrategy;

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${convert.consumer.message-expire-time:300000}")
    private long messageExpireTime;
    
    @Value("${convert.consumer.conversion-timeout:120000}")
    private long conversionTimeout;
    
    // 新增：转换线程池配置参数（带默认值，避免未配置时启动失败）
    @Value("${convert.consumer.conversion-core-pool-size:4}")
    private int conversionCorePoolSize;

    @Value("${convert.consumer.conversion-max-pool-size:8}")
    private int conversionMaxPoolSize;

    @Value("${convert.consumer.conversion-queue-capacity:200}")
    private int conversionQueueCapacity;
    
    // Redis 消息去重缓存键前缀
    private static final String MESSAGE_DEDUP_KEY_PREFIX = "convert:dedup:";

    // 有界线程池用于转换任务（避免线程无限增长）
    private ExecutorService conversionExecutor;

    /**
     * 初始化转换线程池
     * 使用有界线程池替代原来的无界CachedThreadPool，避免高并发时线程数爆炸
     */
    @PostConstruct
    private void initConversionExecutor() {
        // 防御性校正：避免配置错误导致异常
        if (conversionCorePoolSize <= 0) {
            conversionCorePoolSize = 2;
            logger.warn("⚠️ conversionCorePoolSize 配置无效，已重置为默认值: 2");
        }
        if (conversionMaxPoolSize < conversionCorePoolSize) {
            conversionMaxPoolSize = conversionCorePoolSize;
            logger.warn("⚠️ conversionMaxPoolSize 小于 corePoolSize，已调整为: {}", conversionMaxPoolSize);
        }
        if (conversionQueueCapacity <= 0) {
            conversionQueueCapacity = 100;
            logger.warn("⚠️ conversionQueueCapacity 配置无效，已重置为默认值: 100");
        }

        this.conversionExecutor = new ThreadPoolExecutor(
                conversionCorePoolSize,                   // 核心线程数
                conversionMaxPoolSize,                   // 最大线程数
                60L,                                     // 非核心线程空闲存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(conversionQueueCapacity), // 有界队列
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "conversion-worker-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                // 拒绝策略：由调用线程直接执行，避免静默丢任务
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("✅ 初始化转换线程池 - corePoolSize: {}, maxPoolSize: {}, queueCapacity: {}",
                conversionCorePoolSize, conversionMaxPoolSize, conversionQueueCapacity);
    }

    public void onMessage(FileEvent fileEvent) {
        long receiveTime = System.currentTimeMillis();
        logger.info("📨 [MQ接收] 收到转换事件 - FileId: {}, EventType: {}, TargetFormat: {}, SourceFormat: {}, ReceiveTime: {}", 
                   fileEvent != null ? fileEvent.getFileId() : "null",
                   fileEvent != null ? fileEvent.getEventType() : "null",
                   fileEvent != null ? fileEvent.getTargetFormat() : "null",
                   fileEvent != null ? fileEvent.getSourceFormat() : "null",
                   receiveTime);
        
        try {
            // 验证事件数据
            if (fileEvent == null) {
                logger.warn("❌ [MQ消费] 无效事件，跳过处理");
                return;
            }
            // 消息去重检查
            if (isDuplicateMessage(fileEvent)) {
                logger.warn("🔄 [MQ消费] 检测到重复消息，跳过处理 - FileId: {}, TargetFormat: {}", 
                           fileEvent.getFileId(), fileEvent.getTargetFormat());
                return;
            }
            
            logger.info("🔄 [MQ消费] 开始文件转换 - FileId: {}, FileName: {}, SourceFormat: {} -> TargetFormat: {}", 
                       fileEvent.getFileId(), fileEvent.getTargetFileName(), 
                       fileEvent.getSourceFormat(), fileEvent.getTargetFormat());
            
            // 执行转换
            convertFile(fileEvent);
        } catch (Exception e) {
            logger.error("💥 [MQ消费异常] 处理文件事件失败 - FileId: {}, EventType: {}, Error: {}", 
                        fileEvent != null ? fileEvent.getFileId() : "null",
                        fileEvent != null ? fileEvent.getEventType() : "null",
                        e.getMessage(), e);
            // 添加异常处理，确保发送转换失败事件
            try {
                // 创建一个转换结果信息对象用于发送失败事件
                ConvertResultInfo errorResultInfo = new ConvertResultInfo(
                    fileEvent.getFileId(),
                    fileEvent.getFilePath(),
                    fileEvent.getFileType()
                );
                errorResultInfo.markConversionFailed("处理文件事件时发生异常: " + e.getMessage(), 0);
                publishConversionFailureEvent(fileEvent, errorResultInfo);
            } catch (Exception ex) {
                logger.error("❌ 发送转换失败事件失败: {}", fileEvent.getFileId(), ex);
            }
        }
    }

    /**
     * 检查是否为重复消息（使用 Redis 实现分布式去重）
     * 使用 Redis 的 SETNX + 过期时间实现原子性去重
     */
    private boolean isDuplicateMessage(FileEvent fileEvent) {
        try {
            String messageKey = MESSAGE_DEDUP_KEY_PREFIX + generateMessageKey(fileEvent);
            
            // 使用 Redis SETNX 命令，只有当 key 不存在时才设置成功
            // 如果返回 true，说明是第一次处理该消息
            Boolean isFirstTime = redisTemplate.opsForValue().setIfAbsent(
                messageKey, 
                System.currentTimeMillis(), 
                messageExpireTime, 
                TimeUnit.MILLISECONDS
            );
            
            // 如果 isFirstTime 为 true，说明不是重复消息
            // 如果 isFirstTime 为 false 或 null，说明是重复消息
            boolean isDuplicate = (isFirstTime == null || !isFirstTime);
            
            if (isDuplicate) {
                logger.debug("检测到重复消息 - Key: {}", messageKey);
            } else {
                logger.debug("首次处理消息 - Key: {}, 过期时间: {}ms", messageKey, messageExpireTime);
            }
            
            return isDuplicate;
            
        } catch (Exception e) {
            // Redis 异常时记录日志，但不阻塞消息处理
            // 默认允许处理（容错设计）
            logger.warn("消息去重检查异常，允许处理 - FileId: {}", fileEvent.getFileId(), e);
            return false;
        }
    }

    /**
     * 生成消息唯一标识
     * 使用文件 ID 和目标格式作为主要标识
     */
    private String generateMessageKey(FileEvent fileEvent) {
        // 使用 fileId + targetFormat 作为唯一标识
        // 这与缓存结果的 key 保持一致
        return String.format("%s:%s",
                fileEvent.getFileId(),
                fileEvent.getTargetFormat());
    }

    /**
     * 清除去重键，允许重新处理
     * 在转换失败时调用，以便用户可以重试
     */
    private void clearDuplicateMessageKey(FileEvent fileEvent) {
        try {
            String messageKey = MESSAGE_DEDUP_KEY_PREFIX + generateMessageKey(fileEvent);
            Boolean deleted = redisTemplate.delete(messageKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("✅ 清除去重键成功，允许重试 - Key: {}", messageKey);
            } else {
                logger.debug("⚠️ 去重键不存在或已过期 - Key: {}", messageKey);
            }
        } catch (Exception e) {
            logger.warn("清除去重键异常 - FileId: {}", fileEvent.getFileId(), e);
        }
    }

    /**
     * 执行文件转换逻辑（带超时控制和完整缓存）
     */
    private void convertFile(FileEvent fileEvent) {
        long startTime = System.currentTimeMillis();
        logger.info("⚙️ [转换开始] FileId: {}, SourceFormat: {}, TargetFormat: {}, FilePath: {}", 
                   fileEvent.getFileId(), fileEvent.getSourceFormat(), 
                   fileEvent.getTargetFormat(), fileEvent.getFilePath());

        // 创建转换结果信息对象
        ConvertResultInfo convertResultInfo = new ConvertResultInfo(
                fileEvent.getFileId(),
                fileEvent.getFilePath(),
                fileEvent.getFileType());
        convertResultInfo.setOriginalFileFormat(fileEvent.getSourceFormat());
        convertResultInfo.setOriginalFileName(fileEvent.getFileName());
        // convertResultInfo.setOriginalFilePath(fileEvent.getFilePath());
        convertResultInfo.setTargetFormat(fileEvent.getTargetFormat());
        convertResultInfo.setOriginalFileSize(fileUtils.getFileSize(fileEvent.getFilePath()));
        // 🔑 关键修复：从事件中读取 encrypted 字段
        if (fileEvent.getEncrypted() != null) {
            convertResultInfo.setEncrypted(fileEvent.getEncrypted());
            logger.info("🔒 设置 encrypted 字段 - FileId: {}, Encrypted: {}", fileEvent.getFileId(), fileEvent.getEncrypted());
        }
        // 先缓存转换中状态
        convertResultCacheStrategy.cacheConvertResult(
                fileEvent.getFileId(),
                fileEvent.getTargetFormat(),
                convertResultInfo);

        try {
            String filePath = fileEvent.getFilePath();
            String fileType = fileEvent.getFileType();
            if (fileType == null || fileType.trim().isEmpty()) {
                String sourceFormat = fileTypeMapper.extractExtension(filePath);
                // 【性能优化2】提前计算fileType（避免Consumer重复计算）
                fileType = fileTypeMapper.getStrategyType(sourceFormat);

            }
            // 【性能优化】使用预生成的fullTargetPath，避免重复拼接
            String fullTargetPath = fileEvent.getFullTargetPath();
            String targetPath = fileEvent.getTargetPath();
            String targetFileName = fileEvent.getTargetFileName();
            String targetFormat = fileEvent.getTargetFormat();

            logger.info("🔧 Converting file: {} -> {}", filePath, fullTargetPath != null ? fullTargetPath : targetPath);
            logger.info("📁 File type: {}, Target format: {}", fileType, targetFormat);

            // 声明final变量供lambda表达式使用
            final String finalFileType = fileType;
            final String password = fileEvent.getPassWord(); // 获取密码用于加密文件

            // 使用统一的转换上下文处理所有文件类型（保持设计一致性）
            CompletableFuture<Boolean> conversionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // 构建转换参数Map（统一传递扩展参数）
                    Map<String, Object> convertParams = new HashMap<>();
                    if (password != null && !password.trim().isEmpty()) {
                        convertParams.put("password", password);
                        logger.debug("🔑 检测到密码参数，将传递给转换策略 - FileId: {}", fileEvent.getFileId());
                    }
                    
                    // 统一调用带参数的转换方法
                    return fileConvertContext.convertFileWithParams(finalFileType, filePath, targetPath, 
                            targetFileName, targetFormat, convertParams);
                } catch (com.basemetas.fileview.convert.common.exception.UnsupportedException e) {
                    // 🔑 关键修复：引擎不支持异常需要重新抛出，让 ExecutionException 捕获
                    logger.warn("⚠️ CompletableFuture 中捕获到引擎不支持异常，重新抛出 - ErrorCode: {}", e.getErrorCode());
                    throw e; // 重新抛出，让外层的 ExecutionException 捕获
                } catch (Exception e) {
                    logger.error("转换过程中发生异常: {}", e.getMessage(), e);
                    return false;
                }
            }, conversionExecutor);

            boolean success;
            try {
                // 设置超时时间
                success = conversionFuture.get(conversionTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.error("❌ 文件转换超时: {}, 超时时间: {}s", fileEvent.getTargetFileName(), conversionTimeout / 1000);
                conversionFuture.cancel(true); // 取消任务
                success = false;
            } catch (InterruptedException e) {
                logger.error("❌ 文件转换被中断: {}", fileEvent.getTargetFileName());
                Thread.currentThread().interrupt();
                success = false;
            } catch (ExecutionException e) {
                long duration = System.currentTimeMillis() - startTime;
                Throwable cause = e.getCause();
                if (cause != null) {
                    // 检查是否为引擎能力不支持异常
                    if (cause instanceof com.basemetas.fileview.convert.common.exception.UnsupportedException) {
                        com.basemetas.fileview.convert.common.exception.UnsupportedException ex = 
                            (com.basemetas.fileview.convert.common.exception.UnsupportedException) cause;
                        logger.warn("⚠️ 转换引擎不支持 - FileId: {}, ErrorCode: {}, Message: {}",
                                   fileEvent.getFileId(), ex.getErrorCode(), ex.getMessage());
                        convertResultInfo.markConversionFailed(ex.getMessage(), duration);
                        convertResultInfo.setErrorCode(ex.getErrorCode());
                    }
                    // 检查是否为Windows环境下的字体管理器错误
                    else if (isWindowsFontError(cause)) {
                        logger.error("❗ 检测到Windows环境下的字体管理器错误: {}", fileEvent.getTargetFileName());
                        logger.error("错误详情: {}", cause.getMessage());
                        logger.error("建议: 请检查Java运行环境是否支持无头模式，或者联系管理员解决字体系统问题");
                        convertResultInfo.markConversionFailed("Windows字体系统错误: " + cause.getMessage(), duration);
                        publishWindowsFontErrorEvent(fileEvent, cause.getMessage(), duration, convertResultInfo);
                    } else {
                        logger.error("❗ 文件转换执行异常: {}", fileEvent.getTargetFileName(), cause);
                        convertResultInfo.markConversionFailed(cause.getMessage(), duration);
                    }
                } else {
                    logger.error("❗ 文件转换执行异常: {}", fileEvent.getTargetFileName(), e);
                    convertResultInfo.markConversionFailed(e.getMessage(), duration);
                }
                success = false;
            }

            long duration = System.currentTimeMillis() - startTime;

            if (success) {
                // 【性能优化】使用预生成的fullTargetPath，避免重复拼接
                String actualFilePath = fullTargetPath != null && !fullTargetPath.isEmpty()
                        ? fullTargetPath
                        : fileUtils.buildFullTargetPath(targetPath, targetFileName, targetFormat);

                Long fileSize = fileUtils.getFileSize(actualFilePath);

                // 标记转换成功
                convertResultInfo.markConversionSuccess(actualFilePath, duration);
                convertResultInfo.setConvertedFileSize(fileSize);

                // 🔑 检查是否为多页文件（文件夹结构）
                detectMultiPageStructure(actualFilePath, convertResultInfo);

                // 更新缓存
                convertResultCacheStrategy.cacheConvertResult(
                        fileEvent.getFileId(),
                        fileEvent.getTargetFormat(),
                        convertResultInfo);

                logger.info("✅ [转换成功] FileId: {}, FileName: {}, Duration: {}ms, SourceFormat: {}, TargetFormat: {}, OutputPath: {}, FileSize: {} bytes",
                        fileEvent.getFileId(), fileEvent.getTargetFileName(), duration,
                        fileEvent.getSourceFormat(), fileEvent.getTargetFormat(), actualFilePath, fileSize);

                // 📘 如果是多页文件，记录额外信息
                if (convertResultInfo.isMultiPage()) {
                    logger.info("📑 多页文件转换完成 - FileId: {}, TotalPages: {}, Directory: {}",
                            fileEvent.getFileId(), convertResultInfo.getTotalPages(),
                            convertResultInfo.getPagesDirectory());
                }
                // 发送转换成功的事件通知(只发送路径，由预览服务处理URL生成)
                publishConversionSuccessEvent(fileEvent, convertResultInfo);

            } else {
                // 转换失败，清除去重键允许重试
                clearDuplicateMessageKey(fileEvent);
                
                // 🔑 关键修复：如果 errorCode 已设置（ExecutionException 分支中设置），不要覆盖
                if (convertResultInfo.getErrorCode() == null || convertResultInfo.getErrorCode().isEmpty()) {
                    // 转换失败，更新信息
                    convertResultInfo.markConversionFailed("Conversion strategy returned false or timeout", duration);
                } else {
                    // errorCode 已设置，保持不变，仅更新 duration
                    logger.info("📌 保持已设置的 errorCode - FileId: {}, ErrorCode: {}, Message: {}",
                            fileEvent.getFileId(), convertResultInfo.getErrorCode(), 
                            convertResultInfo.getErrorMessage());
                }

                // 更新缓存
                convertResultCacheStrategy.cacheConvertResult(
                        fileEvent.getFileId(),
                        fileEvent.getTargetFormat(),
                        convertResultInfo);

                logger.error("❌ [转换失败] FileId: {}, FileName: {}, Duration: {}ms, ErrorCode: {}, ErrorMsg: {}",
                            fileEvent.getFileId(), fileEvent.getTargetFileName(), duration,
                            convertResultInfo.getErrorCode(), convertResultInfo.getErrorMessage());

                // 发送转换失败的事件通知
                publishConversionFailureEvent(fileEvent, convertResultInfo);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("💥 Exception during file conversion: {}", fileEvent.getTargetFileName(), e);

            // 转换异常，清除去重键允许重试
            clearDuplicateMessageKey(fileEvent);
            
            // 转换异常，更新信息
            convertResultInfo.markConversionFailed("转换过程中发生异常: " + e.getMessage(), duration);

            // 更新缓存
            convertResultCacheStrategy.cacheConvertResult(
                    fileEvent.getFileId(),
                    fileEvent.getTargetFormat(),
                    convertResultInfo);

            // 确保发送转换失败事件
            publishConversionFailureEvent(fileEvent, convertResultInfo);
        } finally {
            // 确保在任何情况下都发送转换完成事件（成功或失败）
            // 注意：这个逻辑已经在上面的if-else和catch块中处理了
        }
    }

    /**
     * 发布转换成功事件（增强版）
     */
    private void publishConversionSuccessEvent(FileEvent originalEvent, ConvertResultInfo convertResultInfo) {
        logger.info("📢 Publishing conversion success event for file: {}", originalEvent.getTargetFileName());

        try {
            // 构建转换完成事件（包含完整的预览信息）
            Map<String, Object> conversionCompletedEvent = new HashMap<>();
            conversionCompletedEvent.put("type", "CONVERSION_COMPLETED");
            conversionCompletedEvent.put("fileId", originalEvent.getFileId());
            
            // 使用统一的数据结构
            Map<String, Object> unifiedInfo = new HashMap<>();
            unifiedInfo.put("fileId", convertResultInfo.getFileId());
            unifiedInfo.put("originalFileName", convertResultInfo.getOriginalFileName());
            unifiedInfo.put("originalFilePath", convertResultInfo.getOriginalFilePath());
            unifiedInfo.put("originalFileFormat", convertResultInfo.getOriginalFileFormat());
            unifiedInfo.put("targetFileName", convertResultInfo.getTargetFileName());
            unifiedInfo.put("convertedFilePath", convertResultInfo.getConvertedFilePath());
            unifiedInfo.put("targetFormat", convertResultInfo.getTargetFormat());
            unifiedInfo.put("originalFileSize", convertResultInfo.getOriginalFileSize());
            unifiedInfo.put("convertedFileSize", convertResultInfo.getConvertedFileSize());
            unifiedInfo.put("status", convertResultInfo.getStatus() != null ? convertResultInfo.getStatus().name() : "UNKNOWN");
            unifiedInfo.put("convertedAt", convertResultInfo.getConvertedAt());
            unifiedInfo.put("conversionDuration", convertResultInfo.getConversionDuration());
            unifiedInfo.put("errorMessage", convertResultInfo.getErrorMessage());
            unifiedInfo.put("errorCode", convertResultInfo.getErrorCode()); // 错误代码 
            unifiedInfo.put("previewUrl", convertResultInfo.getPreviewUrl());
            unifiedInfo.put("previewFilePath", convertResultInfo.getPreviewFilePath());
            unifiedInfo.put("previewFileFormat", convertResultInfo.getPreviewFileFormat());
            unifiedInfo.put("previewFileSize", convertResultInfo.getPreviewFileSize());
            unifiedInfo.put("conversionRequired", convertResultInfo.isConversionRequired());
            unifiedInfo.put("previewMode", convertResultInfo.getPreviewMode());
            unifiedInfo.put("isMultiPage", convertResultInfo.isMultiPage());
            unifiedInfo.put("totalPages", convertResultInfo.getTotalPages());
            unifiedInfo.put("pagesDirectory", convertResultInfo.getPagesDirectory());
            unifiedInfo.put("cachedAt", convertResultInfo.getCachedAt());
            unifiedInfo.put("expiresAt", convertResultInfo.getExpiresAt());
            
            // 🔑 添加 encrypted 字段
            if (convertResultInfo.getEncrypted() != null) {
                unifiedInfo.put("encrypted", convertResultInfo.getEncrypted());
            }
            
            // 🔑 添加 requestBaseUrl 字段（从业务参数中获取）
            if (originalEvent.getBusinessParams() != null) {
                Object requestBaseUrl = originalEvent.getBusinessParams().get("requestBaseUrl");
                if (requestBaseUrl != null) {
                    unifiedInfo.put("requestBaseUrl", requestBaseUrl);
                    logger.debug("💾 从业务参数中获取 requestBaseUrl: {}", requestBaseUrl);
                }
            }

            conversionCompletedEvent.put("unifiedInfo", unifiedInfo);
            conversionCompletedEvent.put("timestamp", System.currentTimeMillis());
            conversionCompletedEvent.put("sourceService", "fileview-convert");

            // 构建事件头
            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", originalEvent.getFileId());
            headers.put("eventType", "CONVERSION_COMPLETED");
            headers.put("sourceService", "fileview-convert");
            headers.put("eventTag", "CONVERSION_COMPLETED");

            EventChannel channel = EventChannel.CONVERT_EVENTS;
            if (originalEvent.getEventType() == originalEvent.eventType.PREVIEW_REQUESTED) {
                // 发送到预览服务
                channel = EventChannel.PREVIEW_EVENTS;
            }

            eventPublisher.publish(channel, conversionCompletedEvent, headers);

            logger.info("✅ 转换完成事件发送成功 - FileId: {}, Channel: {}, EventType: {}",
                    originalEvent.getFileId(), channel, "CONVERSION_COMPLETED");

        } catch (Exception e) {
            logger.error("❌ 发送转换完成事件失败 - FileId: {}", originalEvent.getFileId(), e);
        }
    }

    /**
     * 发布转换失败事件（增强版）
     */
    private void publishConversionFailureEvent(FileEvent originalEvent, ConvertResultInfo convertResultInfo) {
        logger.warn("📢 Publishing conversion failure event for file: {}, error: {}",
                originalEvent.getTargetFileName(), convertResultInfo.getErrorMessage());

        try {
            // 构建转换失败事件
            Map<String, Object> conversionFailedEvent = new HashMap<>();
            conversionFailedEvent.put("type", "CONVERSION_FAILED");
            conversionFailedEvent.put("fileId", originalEvent.getFileId());
            conversionFailedEvent.put("error", convertResultInfo.getErrorMessage());
            conversionFailedEvent.put("errorCode", convertResultInfo.getErrorCode()); // 错误代码
            conversionFailedEvent.put("originalFileFormat", convertResultInfo.getOriginalFileFormat());
            conversionFailedEvent.put("targetFormat", convertResultInfo.getTargetFormat());
            conversionFailedEvent.put("originalFilePath", convertResultInfo.getOriginalFilePath());
            conversionFailedEvent.put("conversionDuration", convertResultInfo.getConversionDuration());
            conversionFailedEvent.put("status", convertResultInfo.getStatus().name());
            conversionFailedEvent.put("timestamp", System.currentTimeMillis());
            conversionFailedEvent.put("sourceService", "fileview-convert");
            
            // 构建事件头
            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", originalEvent.getFileId());
            headers.put("eventType", "CONVERSION_FAILED");
            headers.put("sourceService", "fileview-convert");
            headers.put("eventTag", "CONVERSION_COMPLETED");
            
            EventChannel channel = EventChannel.CONVERT_EVENTS;
            if (originalEvent.getEventType() == originalEvent.eventType.PREVIEW_REQUESTED) {
                // 发送到预览服务
                channel = EventChannel.PREVIEW_EVENTS;
            }

            eventPublisher.publish(channel, conversionFailedEvent, headers);

            logger.info("✅ 转换失败事件发送成功 - FileId: {}, Channel: {}, EventType: {}",
                    originalEvent.getFileId(), channel, "CONVERSION_FAILED");

        } catch (Exception e) {
            logger.error("❌ 发送转换失败事件失败 - FileId: {}", originalEvent.getFileId(), e);
        }
    }

    /**
     * 检查是否为Windows环境下的字体管理器错误
     */
    private boolean isWindowsFontError(Throwable cause) {
        if (cause == null) {
            return false;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("windows")) {
            return false;
        }

        String message = cause.getMessage();
        if (message != null) {
            return message.contains("HeadlessFontManager") ||
                    message.contains("sun.awt.HeadlessFontManager") ||
                    message.contains("FontManagerFactory") ||
                    message.contains("FontLoader") ||
                    message.contains("GraphicsEnvironment") ||
                    message.contains("createFont") ||
                    message.contains("loadFont") ||
                    message.contains("scanFontDir");
        }

        // 检查异常类型
        String className = cause.getClass().getSimpleName();
        return className.contains("InternalError") ||
                className.contains("HeadlessException") ||
                (className.contains("ClassNotFoundException") &&
                        cause.getMessage() != null &&
                        cause.getMessage().contains("HeadlessFontManager"));
    }

    /**
     * 发布Windows字体错误事件
     */
    private void publishWindowsFontErrorEvent(FileEvent originalEvent, String errorMessage, long duration,
            ConvertResultInfo convertResultInfo) {
        logger.warn("📢 发布Windows字体错误事件: {}, 错误: {}",
                originalEvent.getTargetFileName(), errorMessage);
        logger.warn("🛠️ 建议解决方案:");
        logger.warn("   1. 检查Java JVM的-Djava.awt.headless=true参数是否正确设置");
        logger.warn("   2. 确保在Windows服务器环境下运行时使用无头模式");
        logger.warn("   3. 联系系统管理员检查Java字体系统配置");

        // 这里可以发送特定的Windows字体错误通知
        publishConversionFailureEvent(originalEvent, convertResultInfo);
    }

    /**
     * 检测是否为多页文件（文件夹结构）
     * 对于OFD转换生成的文件夹结构，检查是否包含 page_0.png, page_1.png 等文件
     */
    private void detectMultiPageStructure(String filePath, ConvertResultInfo convertResultInfo) {
        try {
            File file = new File(filePath);

            // 检查是否为目录
            if (file.isDirectory()) {
                // 直接是目录，扫描 page_*.png 文件
                File[] pageFiles = file
                        .listFiles((dir, name) -> name.matches("page_\\d+\\.png") || name.matches("page_\\d+\\.jpg"));

                if (pageFiles != null && pageFiles.length > 0) {
                    // 这是多页文件
                    convertResultInfo.setMultiPage(true);
                    convertResultInfo.setTotalPages(pageFiles.length);
                    convertResultInfo.setPagesDirectory(filePath);

                    logger.info("📑 检测到多页文件结构 - Directory: {}, TotalPages: {}",
                            filePath, pageFiles.length);
                } else {
                    // 目录为空或没有页面文件
                    convertResultInfo.setMultiPage(false);
                    convertResultInfo.setTotalPages(0);
                    logger.debug("目录为空或没有页面文件: {}", filePath);
                }
            } else if (file.isFile()) {
                // 是文件，检查是否存在同名的多页文件夹
                // 例如: /path/to/file.png -> 检查 /path/to/file/ 目录
                String fileNameWithoutExt = fileUtils.getFileNameWithoutExtension(file.getName());
                File parentDir = file.getParentFile();
                File possibleFolder = new File(parentDir, fileNameWithoutExt);

                if (possibleFolder.exists() && possibleFolder.isDirectory()) {
                    // 找到了同名的文件夹，扫描 page_*.png 文件
                    File[] pageFiles = possibleFolder.listFiles(
                            (dir, name) -> name.matches("page_\\d+\\.png") || name.matches("page_\\d+\\.jpg"));

                    if (pageFiles != null && pageFiles.length > 1) {
                        // 这是多页文件（至少有2页才算多页）
                        convertResultInfo.setMultiPage(true);
                        convertResultInfo.setTotalPages(pageFiles.length);
                        convertResultInfo.setPagesDirectory(possibleFolder.getAbsolutePath());

                        logger.info("📑 检测到多页文件结构 - SingleFile: {}, Folder: {}, TotalPages: {}",
                                filePath, possibleFolder.getAbsolutePath(), pageFiles.length);
                    } else {
                        // 文件夹存在但只有1页或没有页面文件，视为单页
                        convertResultInfo.setMultiPage(false);
                        convertResultInfo.setTotalPages(1);
                        logger.debug("文件夹存在但只有1页: {}", filePath);
                    }
                } else {
                    // 没有同名文件夹，单文件
                    convertResultInfo.setMultiPage(false);
                    convertResultInfo.setTotalPages(1);
                    logger.debug("单文件，不是多页: {}", filePath);
                }
            } else {
                // 既不是文件也不是目录，默认为单页
                convertResultInfo.setMultiPage(false);
                convertResultInfo.setTotalPages(1);
                logger.debug("无法确定文件类型: {}", filePath);
            }

        } catch (Exception e) {
            logger.warn("⚠️ 检测多页文件结构失败: {}", filePath, e);
            // 错误处理：默认设置为单页
            convertResultInfo.setMultiPage(false);
            convertResultInfo.setTotalPages(1);
        }
    }

}