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
package com.basemetas.fileview.preview.controller;

import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.config.FileTypeMapper;
import com.basemetas.fileview.preview.config.PollingConfig;
import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.model.request.PollingRequest;
import com.basemetas.fileview.preview.service.FilePreviewService;
import com.basemetas.fileview.preview.model.response.ReturnResponse;
import com.basemetas.fileview.preview.service.cache.CacheReadService;
import com.basemetas.fileview.preview.service.download.DownloadTaskManager;
import com.basemetas.fileview.preview.service.response.PreviewResponseAssembler;
import com.basemetas.fileview.preview.service.url.RequestAwareBaseUrlProvider;
import com.basemetas.fileview.preview.utils.EncodingUtils;
import com.basemetas.fileview.preview.utils.HttpUtils;
import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.model.PreviewStatusContext;
import com.basemetas.fileview.preview.model.PreviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文件预览控制器（最终架构版）
 * 
 * 提供文件预览相关的REST API接口：
 * 1. 文件预览请求处理
 * 2. 预览状态查询（从转换服务Redis查询）
 * 3. 智能轮询 + 长轮询支持
 * 4. 健康检查
 * 
 * 架构设计：
 * - 预览服务查询转换服务在Redis中的缓存结果
 * - 保持原有的轮询机制，但数据来源为转换服务缓存
 * 
 * @author 夫子
 */
@RestController
@RequestMapping("/preview/api")
@CrossOrigin(origins = "*")
public class FilePreviewController {

    private static final Logger logger = LoggerFactory.getLogger(FilePreviewController.class);

    @Autowired
    private FilePreviewService previewService;

    @Autowired
    private CacheReadService cacheReadService;

    @Autowired
    private PollingConfig pollingConfig;

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private DownloadTaskManager downloadTaskManager;

    @Autowired
    private HttpUtils httpUtils;
    
    @Autowired
    private PreviewResponseAssembler previewResponseAssembler;
    
    @Autowired
    private com.basemetas.fileview.preview.utils.ClientIdExtractor clientIdExtractor;
    
    @Autowired
    private com.basemetas.fileview.preview.utils.FileUtils fileUtils;
    
    @Autowired
    private com.basemetas.fileview.preview.service.password.PasswordUnlockService passwordUnlockService;
    
    @Autowired
    private RequestAwareBaseUrlProvider baseUrlProvider;
    
    @Value("${fileview.preview.url.expiration-hours:24}")
    private int urlExpirationHours;
    

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.debug("健康检查请求");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "FileView Preview Service");
        response.put("version", "1.0.0");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 处理文件预览请求
     * 
     * @param request     预览请求
     * @param httpRequest HTTP请求对象
     * @return 统一响应格式
     */
    @PostMapping("/localFile")
    public ResponseEntity<Map<String, Object>> processPreviewRequest(
            @Valid @RequestBody FilePreviewRequest request,
            HttpServletRequest httpRequest) {
        logger.info("📋 收到文件预览请求 - FileId: {}, Type: {}", request.getFileId(), request.getPreviewType());
        long startTime = System.currentTimeMillis();
        
        // 提取 clientId（优先从请求头，其次从查询参数）
        clientIdExtractor.extractAndSetClientId(request, httpRequest);
        
        // 处理中文编码
        EncodingUtils.processRequestEncoding(request);
        try {
            // 🔑 解析请求的 baseUrl
            String requestBaseUrl = httpUtils.getDynamicBaseUrl();
            logger.debug("🌐 解析请求 baseUrl - BaseUrl: {}", requestBaseUrl);
            
            // Service层会抛出FileViewException，由GlobalExceptionHandler统一处理
            Map<String, Object> response = previewService.processServerFilePreview(request, startTime, requestBaseUrl);
            
            // 统一处理响应
            return handlePreviewResponse(response, httpRequest, startTime, false);
        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理文件预览请求时发生异常 - FileId: {}", request.getFileId(), e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理文件预览请求时发生异常: " + e.getMessage(),
                    e).withFileId(request.getFileId());
        }
    }

    /**
     * 网络文件预览请求
     * 
     * @param request     预览请求
     * @param httpRequest HTTP请求对象
     * @return 统一响应格式
     */
    @PostMapping("/netFile")
    public ResponseEntity<Map<String, Object>> previewServerFile(
            @Valid @RequestBody FilePreviewRequest request,
            HttpServletRequest httpRequest) {
        logger.info("📋 收到文件预览请求 - FileId: {}, httpUrl: {}", request.getFileId(), request.getNetworkFileUrl());
        long startTime = System.currentTimeMillis();
        
        // 提取 clientId（优先从请求头，其次从查询参数）
        clientIdExtractor.extractAndSetClientId(request, httpRequest);
        
        // 🔑 解析请求的 baseUrl
        String requestBaseUrl = httpUtils.getDynamicBaseUrl();
        logger.debug("🌐 解析请求 baseUrl - BaseUrl: {}", requestBaseUrl);
        
        try {
            // Service层会抛出FileViewException，由GlobalExceptionHandler统一处理
            Map<String, Object> response = previewService.processNetworkFilePreview(request, startTime, requestBaseUrl);          
            // 统一处理响应（网络文件支持DOWNLOADING状态）
            return handlePreviewResponse(response, httpRequest, startTime, true);
        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理网络文件预览请求时发生异常 - FileId: {}", request.getFileId(), e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理网络文件预览请求时发生异常: " + e.getMessage(),
                    e).withFileId(request.getFileId());
        }
    }

    /**
     * 智能轮询接口 - 长轮询实现
     * 支持客户端长时间等待转换完成，减少频繁请求
     * 
     * 
     * @param request     轮询请求参数
     * @param httpRequest HTTP请求对象
     * @return 统一响应格式
     */
    @PostMapping("/status/poll")
    public ResponseEntity<Map<String, Object>> pollPreviewStatus(
            @Valid @RequestBody PollingRequest request, HttpServletRequest httpRequest) {
        long startTime = System.currentTimeMillis();

        // 🔑 关键修复：提取 clientId
        String clientId = clientIdExtractor.extractClientId(httpRequest);
        logger.debug("🎯 提取 clientId - ClientId: {}", clientId);

        // 从请求体中获取参数
        String fileId = null;
        String targetFormat = null;
        Integer timeout = null;
        Integer interval = null;

        if (request != null) {
            fileId = request.getFileId();
            targetFormat = request.getTargetFormat();
            timeout = request.getTimeout();
            interval = request.getInterval();
        }
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 长轮询请求参数错误 - fileId为空");
            return ReturnResponse.badRequest(ErrorCode.MISSING_REQUIRED_PARAMETER);
        }

        try {
            // 配置空指针检查（必须在使用前检查）
            if (pollingConfig == null) {
                logger.error("❌ pollingConfig未正确注入");
                return ReturnResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
            }
            
            // 使用配置的默认值
            if (timeout == null) {
                timeout = pollingConfig.getDefaultTimeout();
            }
            if (interval == null) {
                interval = pollingConfig.getDefaultInterval();
            }

            // 参数验证和调整
            timeout = Math.max(5, Math.min(timeout, pollingConfig.getMaxTimeout()));
            interval = Math.max(pollingConfig.getMinInterval(),
                    Math.min(interval, pollingConfig.getMaxInterval()));
            
            // 🔑 关键修复：在进入异步线程前提取 requestBaseUrl
            String requestBaseUrl = baseUrlProvider.getBaseUrl();
            
            // 🔍 预检查：如果缓存中已是终态，直接返回，避免进入长轮询
            ResponseEntity<Map<String, Object>> preCheckResult = 
                    preCheckAndReturnIfTerminal(fileId, targetFormat, clientId, requestBaseUrl, startTime, httpRequest);
            if (preCheckResult != null) {
                return preCheckResult;
            }
            
            logger.info("🔄 开始长轮询 - FileId: {}, TargetFormat: {}, Timeout: {}s, Interval: {}ms",
                    fileId, (targetFormat != null ? targetFormat : "按优先级"), timeout, interval);
            // 长轮询逻辑
            final int finalTimeout = timeout;
            final int finalInterval = interval;
            final String finalTargetFormat = targetFormat;
            final long finalStartTime = startTime;
            final String finalFileId = fileId;
            final String finalClientId = clientId;  // 🔑 传递 clientId
            final String finalRequestBaseUrl = requestBaseUrl;  // 🔑 传递 requestBaseUrl
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                return performLongPollingWithDownloadStatus(finalFileId, finalTargetFormat, finalTimeout, finalInterval,
                        finalStartTime, finalClientId, finalRequestBaseUrl);  // 🔑 传递 requestBaseUrl
            });

            try {
                // 等待结果，额外给5秒缓冲时间
                Map<String, Object> response = future.get(timeout + 5, TimeUnit.SECONDS);
                long duration = System.currentTimeMillis() - startTime;
                response.put("processingDuration", duration);
                response.put("requestPath", httpRequest.getRequestURI());
                logger.info("✅ 长轮询完成 - FileId: {}, Status: {}, Duration: {}ms",
                        finalFileId, response.get("status"), duration);
                return ReturnResponse.success(response, "长轮询完成");
            } catch (TimeoutException e) {
                logger.warn("⏰ 长轮询超时 - FileId: {}, Timeout: {}s", finalFileId, timeout);
                Map<String, Object> timeoutResponse = buildPollingResponse(
                    finalFileId,
                    "CONVERTING",
                    "轮询超时，转换可能仍在进行中，请稍后重试",
                    startTime
                );
                timeoutResponse.put("requestPath", httpRequest.getRequestURI());
                return ReturnResponse.success(timeoutResponse, "轮询超时");
            } catch (ExecutionException e) {
                logger.error("❌ 长轮询执行异常 - FileId: {}", finalFileId, e.getCause());

                // 将ExecutionException包装为FileViewException
                Throwable cause = e.getCause();
                if (cause instanceof FileViewException) {
                    FileViewException fileViewException = (FileViewException) cause;
                    return ReturnResponse.error(HttpStatus.valueOf(fileViewException.getHttpStatus()),
                            fileViewException.getErrorCode());
                }
                return ReturnResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
            }
        } catch (FileViewException e) {
            return ReturnResponse.error(HttpStatus.valueOf(e.getHttpStatus()), e.getErrorCode());
        } catch (Exception e) {
            logger.error("💥 长轮询接口异常 - FileId: {}", fileId, e);
            return ReturnResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 构建标准化的轮询响应
     * 统一处理公共字段，避免重复代码
     *
     * @param fileId 文件ID
     * @param status 状态（SUCCESS/FAILED/CONVERTING/NOT_SUPPORTED/PROCESSING等）
     * @param message 消息（可选）
     * @param startTime 开始时间
     * @return 标准化响应Map
     */
    private Map<String, Object> buildPollingResponse(String fileId, String status, String message, long startTime) {
        return previewResponseAssembler.buildPollingResponse(fileId, status, message, startTime, urlExpirationHours);
    }
    /**
     * 从缓存信息构建失败/不支持响应
     * 统一填充FAILED/NOT_SUPPORTED状态的所有字段
     *
     * @param fileId 文件ID
     * @param status 状态（FAILED或NOT_SUPPORTED）
     * @param errorMessage 错误消息
     * @param errorCode 错误码
     * @param cacheInfo 缓存信息（可为null）
     * @param targetFormat 目标格式（用于FAILED状态的previewFileFormat计算）
     * @param startTime 开始时间
     * @return 失败响应Map
     */
    private Map<String, Object> buildFailureResponseFromCache(String fileId, String status, String errorMessage, 
                                                                ErrorCode errorCode, PreviewCacheInfo cacheInfo, 
                                                                String targetFormat, long startTime) {
        return previewResponseAssembler.buildFailureFromCache(
                fileId,
                status,
                errorMessage,
                errorCode,
                cacheInfo,
                targetFormat,
                startTime,
                urlExpirationHours,
                fileTypeMapper
        );
    }
    /**
     * 执行长轮询逻辑（重构版 - 状态枚举策略）
     * 
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @param timeoutSeconds 超时时间（秒）
     * @param intervalMs 轮询间隔（毫秒）
     * @param startTime 开始时间
     * @param clientId 客户端ID
     * @param requestBaseUrl 请求基础URL
     * @return 轮询结果响应
     */
    private Map<String, Object> performLongPollingWithDownloadStatus(
            String fileId, String targetFormat, int timeoutSeconds, int intervalMs,
            long startTime, String clientId, String requestBaseUrl) {
        
        int maxAttempts = (timeoutSeconds * 1000) / intervalMs;
        long firstCheckTime = System.currentTimeMillis();
        boolean fileIdEverExistedInCache = false;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // ==================== 1. 下载状态检查 ====================
                Map<String, Object> downloadResult = checkDownloadTaskStatus(fileId, startTime);
                if (downloadResult != null) {
                    return downloadResult;
                }
                
                // ==================== 2. 查询缓存信息 ====================
                PreviewCacheInfo cacheInfo = queryCacheInfo(fileId, targetFormat);
                if (cacheInfo == null) {
                    long elapsedSinceFirstCheck = System.currentTimeMillis() - firstCheckTime;
                    if (!fileIdEverExistedInCache && elapsedSinceFirstCheck > 3000) {
                        logger.warn("⚠️ 长轮询宽限期内未发现缓存记录，视为文件ID不存在或已过期 - FileId: {}, Elapsed: {}ms",
                                fileId, elapsedSinceFirstCheck);
                        Map<String, Object> notFoundResponse = buildPollingResponse(
                                fileId,
                                "NOT_FOUND",
                                "文件ID不存在或已过期，请重新提交预览请求",
                                startTime
                        );
                        notFoundResponse.put("errorCode", ErrorCode.FILE_NOT_FOUND.getCode());
                        return notFoundResponse;
                    }
                    // 缓存不存在且仍在宽限期内，继续轮询
                    Thread.sleep(calculateAdaptiveInterval(attempt, intervalMs));
                    continue;
                }
                
                fileIdEverExistedInCache = true;
                
                // ==================== 3. 构建状态上下文 ====================
                PreviewStatusContext context = PreviewStatusContext.builder()
                        .fileId(fileId)
                        .targetFormat(targetFormat)
                        .cacheInfo(cacheInfo)
                        .clientId(clientId)
                        .requestBaseUrl(requestBaseUrl)
                        .startTime(startTime)
                        .build();
                
                // ==================== 4. 提取状态并处理 ====================
                String statusStr = cacheInfo.getStatus();
                PreviewStatus status = PreviewStatus.fromString(statusStr);
                
                logger.debug("🔍 状态检查 - FileId: {}, Status: {}, Attempt: {}/{}", 
                        fileId, status.getStatusName(), attempt + 1, maxAttempts);
                
                // 处理状态
                Map<String, Object> response = status.handle(
                        context,
                        previewResponseAssembler,
                        passwordUnlockService,
                        previewService,
                        fileTypeMapper,
                        fileUtils,
                        cacheReadService,
                        urlExpirationHours
                );
                
                // 检查是否为终态
                if (response != null || status.isTerminal(context, passwordUnlockService, fileUtils)) {
                    if (response != null) {
                        logger.info("🎯 长轮询命中终态结果 - FileId: {}, Status: {}, Attempt: {}/{}", 
                                fileId, status.getStatusName(), attempt + 1, maxAttempts);
                        return response;
                    }
                }
                
                // ==================== 5. 非终态，继续轮询 ====================
                Thread.sleep(calculateAdaptiveInterval(attempt, intervalMs));
                
                // 每10次记录日志
                if ((attempt + 1) % 10 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.debug("⏳ 长轮询进行中 - FileId: {}, Attempt: {}/{}, Elapsed: {}ms",
                            fileId, attempt + 1, maxAttempts, elapsed);
                }
                
            } catch (InterruptedException e) {
                logger.warn("🛑 长轮询被中断 - FileId: {}", fileId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("💥 长轮询检查异常 - FileId: {}, Attempt: {}", fileId, attempt, e);
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // ==================== 6. 超时，返回 CONVERTING ====================
        logger.info("⏰ 长轮询超时 - FileId: {}, Attempts: {}", fileId, maxAttempts);
        return previewResponseAssembler.buildConvertingResponse(
                fileId, "文件正在处理中，请稍候...", startTime, urlExpirationHours);
    }

    /**
     * 查询缓存信息（辅助方法）
     * 
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @return 缓存信息，不存在返回 null
     */
    private PreviewCacheInfo queryCacheInfo(String fileId, String targetFormat) {
        if (targetFormat != null && !targetFormat.trim().isEmpty()) {
            return cacheReadService.getCachedResult(fileId, targetFormat);
        } else {
            return cacheReadService.getCachedResult(fileId);
        }
    }

    /**
     * 检查下载任务状态
     * 
     * @param fileId    文件ID
     * @param startTime 请求开始时间
     * @return 如果下载失败返回失败响应，否则返回null继续轮询
     */
    private Map<String, Object> checkDownloadTaskStatus(String fileId, long startTime) {
        DownloadTask downloadTask = downloadTaskManager.getTask(fileId);
        if (downloadTask == null) {
            // 没有下载任务，继续检查转换状态
            return null;
        }
        
        DownloadTaskStatus downloadStatus = downloadTask.getStatus();
        
        // 下载失败，直接返回失败响应
        if (downloadStatus == DownloadTaskStatus.FAILED) {
            String errorMessage = "文件下载失败";
            logger.warn("❌ 下载任务失败 - FileId: {}, Error: {}", fileId, errorMessage);
            
            Map<String, Object> failedResponse = buildPollingResponse(
                fileId,
                "FAILED",
                errorMessage,
                startTime
            );
            failedResponse.put("error", errorMessage);
            failedResponse.put("errorCode", ErrorCode.SYSTEM_ERROR.getCode());
            failedResponse.put("downloadProgress", downloadTask.getProgress());
            
            return failedResponse;
        }
        
        // 下载进行中，记录进度后继续轮询
        if (downloadStatus == DownloadTaskStatus.DOWNLOADING) {
            return null;
        }
        
        // 下载完成，继续检查转换状态
        if (downloadStatus == DownloadTaskStatus.DOWNLOADED) {
            return null;
        }
        
        // 其他状态，继续轮询
        return null;
    }

    /**
     * 预检查：如果缓存中已是终态，直接返回完整响应
     * 
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @param clientId 客户端ID
     * @param requestBaseUrl 请求基础URL
     * @param startTime 开始时间
     * @param httpRequest HTTP请求对象
     * @return 如果命中终态返回完整响应，否则返回null
     */
    private ResponseEntity<Map<String, Object>> preCheckAndReturnIfTerminal(
            String fileId,
            String targetFormat,
            String clientId,
            String requestBaseUrl,
            long startTime,
            HttpServletRequest httpRequest) {
        
        PreviewCacheInfo preCheckCache = queryCacheInfo(fileId, targetFormat);
        if (preCheckCache == null) {
            return null;
        }
        
        PreviewStatusContext preCheckContext = PreviewStatusContext.builder()
                .fileId(fileId)
                .targetFormat(targetFormat)
                .cacheInfo(preCheckCache)
                .clientId(clientId)
                .requestBaseUrl(requestBaseUrl)
                .startTime(startTime)
                .build();
        
        PreviewStatus preCheckStatus = PreviewStatus.fromString(preCheckCache.getStatus());
        Map<String, Object> preCheckResponse = preCheckStatus.handle(
                preCheckContext,
                previewResponseAssembler,
                passwordUnlockService,
                previewService,
                fileTypeMapper,
                fileUtils,
                cacheReadService,
                urlExpirationHours
        );
        
        if (preCheckResponse != null) {
            long duration = System.currentTimeMillis() - startTime;
            preCheckResponse.put("processingDuration", duration);
            preCheckResponse.put("requestPath", httpRequest.getRequestURI());
            logger.info("✅ 长轮询预检查命中终态结果 - FileId: {}, Status: {}, Duration: {}ms", 
                    fileId, preCheckResponse.get("status"), duration);
            return ReturnResponse.success(preCheckResponse, "长轮询完成");
        }
        
        return null;
    }

    /**
     * 计算自适应检查间隔
     * 前期检查频繁，后期逐渐放慢，减少系统压力
     */
    private int calculateAdaptiveInterval(int attemptCount, int baseInterval) {
        PollingConfig.SmartPollingStrategy strategy = pollingConfig.getSmartStrategy();

        if (attemptCount < strategy.getPhase1Attempts()) {
            // 第一阶段：使用配置的第一阶段间隔
            return strategy.getPhase1Interval();
        } else if (attemptCount < strategy.getPhase1Attempts() + strategy.getPhase2Attempts()) {
            // 第二阶段：使用配置的第二阶段间隔
            return strategy.getPhase2Interval();
        } else {
            // 第三阶段：使用配置的第三阶段间隔
            return strategy.getPhase3Interval();
        }
    }

    /**
     * 文件访问端点 - 提供转换后文件的HTTP访问
     * 这个端点允许前端直接访问转换后的文件用于预览
     * 
     * @param fileId 文件ID
     * @param path   文件路径
     * @return 文件内容
     */
    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String fileId,
            @RequestParam(required = false) String path) {

        try {
            logger.info("📁 文件访问请求 - FileId: {}, Path: {}", fileId, path);

            // 参数验证
            if (fileId == null || fileId.trim().isEmpty()) {
                logger.warn("⚠️ 文件ID为空");
                return ResponseEntity.badRequest().build();
            }

            // 从缓存中获取文件信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            if (cacheInfo == null) {
                logger.warn("⚠️ 未找到文件缓存信息 - FileId: {}", fileId);
                return ResponseEntity.notFound().build();
            }

            // 确定文件路径
            String filePath;
            if (path != null && !path.trim().isEmpty()) {
                // 使用传入的路径参数（URL解码）
                filePath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            } else {
                // 使用缓存中的预览文件路径
                filePath = cacheInfo.getOriginalFilePath();
                if (filePath == null || filePath.trim().isEmpty()) {
                    // 如果没有预览文件路径，返回错误
                    logger.warn("⚠️ 无法获取文件路径 - FileId: {}", fileId);
                    return ResponseEntity.notFound().build();
                }
            }

            if (filePath == null || filePath.trim().isEmpty()) {
                logger.warn("⚠️ 文件路径为空 - FileId: {}", fileId);
                return ResponseEntity.notFound().build();
            }

            // 检查文件是否存在
            Path file = Paths.get(filePath);
            if (!Files.exists(file)) {
                logger.warn("⚠️ 文件不存在 - FileId: {}, Path: {}", fileId, filePath);
                return ResponseEntity.notFound().build();
            }

            // 安全检查：防止路径遍历攻击
            if (!fileUtils.isSecurePath(filePath)) {
                logger.warn("🚫 不安全的文件路径 - FileId: {}, Path: {}", fileId, filePath);
                return ResponseEntity.badRequest().build();
            }

            // 创建文件资源
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("⚠️ 文件不可读 - FileId: {}, Path: {}", fileId, filePath);
                return ResponseEntity.notFound().build();
            }

            // 确定文件类型
            String contentType = httpUtils.determineContentType(filePath);

            // 获取文件名并进行URL编码（支持中文文件名）
            String fileName = file.getFileName().toString();
            String encodedFileName =fileUtils.encodeFileName(fileName);

            logger.info("✅ 文件访问成功 - FileId: {}, FileName: {}, ContentType: {}, Size: {} bytes",
                    fileId, fileName, contentType, Files.size(file));

            // 返回文件响应（使用RFC 5987标准编码中文文件名）
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600") // 1小时缓存
                    .body(resource);

        } catch (Exception e) {
            logger.error("💥 文件访问异常 - FileId: {}, Path: {}", fileId, path, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 直接访问文件端点 - 不通过fileId，直接传递文件路径
     * 主要用于转换服务直接生成的预览URL
     * 
     * @param filePath 文件路径
     * @return 文件内容
     */
    @GetMapping("/file")
    public ResponseEntity<Resource> serveDirectFile(@RequestParam String filePath) {

        try {
            logger.info("📁 直接文件访问请求 - Path: {}", filePath);

            if (filePath == null || filePath.trim().isEmpty()) {
                logger.warn("⚠️ 文件路径为空");
                return ResponseEntity.badRequest().build();
            }

            // URL解码（容错处理：解码失败时保留原始路径）
            String decodedPath = filePath;
            try {
                decodedPath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                logger.warn("⚠️ URL解码失败，使用原始路径 - Path: {}, Error: {}", filePath, e.getMessage());
                // 保留原始 filePath
            }

            // 检查文件是否存在
            Path file = Paths.get(decodedPath);
            if (!Files.exists(file)) {
                logger.warn("⚠️ 文件不存在 - Path: {}", decodedPath);
                return ResponseEntity.notFound().build();
            }

            // 安全检查
            if (!fileUtils.isSecurePath(decodedPath)) {
                logger.warn("🚫 不安全的文件路径 - Path: {}", decodedPath);
                return ResponseEntity.badRequest().build();
            }

            // 创建文件资源
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("⚠️ 文件不可读 - Path: {}", decodedPath);
                return ResponseEntity.notFound().build();
            }

            // 确定文件类型
            String contentType = httpUtils.determineContentType(decodedPath);

            // 获取文件名并进行URL编码（支持中文文件名）
            String fileName = file.getFileName().toString();
            String encodedFileName = fileUtils.encodeFileName(fileName);

            logger.info("✅ 直接文件访问成功 - FileName: {}, ContentType: {}, Size: {} bytes",
                    fileName, contentType, Files.size(file));

            // 返回文件响应（使用RFC 5987标准编码中文文件名）
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600") // 1小时缓存
                    .body(resource);

        } catch (Exception e) {
            logger.error("💥直接文件访问异常 - Path: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 统一处理预览响应
     * 
     * @param response        Service层返回的响应
     * @param httpRequest     HTTP请求对象
     * @param startTime       请求开始时间
     * @param isNetworkFile   是否网络文件（网络文件支持DOWNLOADING状态）
     * @return 统一响应格式
     */
    private ResponseEntity<Map<String, Object>> handlePreviewResponse(
            Map<String, Object> response, 
            HttpServletRequest httpRequest,
            long startTime,
            boolean isNetworkFile) {
        
        // 设置请求路径
        response.put("requestPath", httpRequest.getRequestURI());

        // 确保必要的字段存在
        if (!response.containsKey("fileId")) {
            response.put("fileId", "");
        }
        String fileId = (String) response.get("fileId");
        
        // 获取状态
        String status = (String) response.get("status");
        if (status == null) {
            status = "UNKNOWN";
            response.put("status", status);
        }

        // 处理各种状态
        if ("SUCCESS".equals(status)) {
            logger.info("✅ 文件预览请求处理成功 - FileId: {}, URL: {}",
                    fileId, response.get("previewUrl"));
            return ReturnResponse.success(response, "文件预览请求处理成功");
            
        } else if ("CONVERTING".equals(status)) {
            logger.info("🔄 文件正在转换中 - FileId: {}, Progress: {}%",
                    fileId, response.get("conversionProgress"));
            if (isNetworkFile) {
                response.put("message", "文件正在转换中");
            }
            return ReturnResponse.success(response, "文件正在转换中");
            
        } else if ("DOWNLOADING".equals(status)) {
            // 仅网络文件支持此状态
            if (isNetworkFile) {
                logger.info("📥 文件正在下载中 - FileId: {}", fileId);
                response.put("message", "文件下载任务已提交，请通过fileId轮询下载状态");
                return ReturnResponse.success(response, "文件下载任务已提交");
            }
            // 本地文件不应有此状态，继续处理为其他状态
            
        } else if ("NOT_SUPPORTED".equals(status)) {
            String fileFormat = (String) response.get("originalFileFormat");
            if (fileFormat == null) {
                fileFormat = "unknown";
            }
            logger.warn("⚠️ 不支持的文件类型 - FileId: {}, Type: {}", fileId, fileFormat);
            
            // 从缓存获取完整信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            String errorMsg = "不支持的文件类型: " + fileFormat;
            Map<String, Object> notSupportedResponse = previewResponseAssembler.buildFailureFromCache(
                fileId,
                "NOT_SUPPORTED",
                errorMsg,
                ErrorCode.UNSUPPORTED_FILE_TYPE,
                cacheInfo,
                null,
                startTime,
                urlExpirationHours,
                fileTypeMapper
            );
            notSupportedResponse.put("requestPath", httpRequest.getRequestURI());
            return ReturnResponse.success(notSupportedResponse, "不支持的文件类型");
            
        } else if ("PASSWORD_REQUIRED".equals(status)) {
            logger.warn("🔒 文件需要密码 - FileId: {}", fileId);
            
            // 从缓存获取完整信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            String errorMsg = isNetworkFile ? "压缩包已加密，需要提供密码" : "文件已加密，需要提供密码";
            Map<String, Object> passwordRequiredResponse = previewResponseAssembler.buildFailureFromCache(
                fileId,
                "PASSWORD_REQUIRED",
                errorMsg,
                ErrorCode.DOCUMENT_PASSWORD_REQUIRED,
                cacheInfo,
                null,
                startTime,
                urlExpirationHours,
                fileTypeMapper
            );
            passwordRequiredResponse.put("requestPath", httpRequest.getRequestURI());
            return ReturnResponse.success(passwordRequiredResponse, "需要密码");
            
        } else if ("PASSWORD_INCORRECT".equals(status)) {
            logger.warn("❌ 密码错误 - FileId: {}", fileId);
            
            // 从缓存获取完整信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            String errorMsg = "压缩包密码错误";
            Map<String, Object> passwordIncorrectResponse = previewResponseAssembler.buildFailureFromCache(
                fileId,
                "PASSWORD_INCORRECT",
                errorMsg,
                ErrorCode.DOCUMENT_PASSWORD_INCORRECT,
                cacheInfo,
                null,
                startTime,
                urlExpirationHours,
                fileTypeMapper
            );
            passwordIncorrectResponse.put("requestPath", httpRequest.getRequestURI());
            return ReturnResponse.success(passwordIncorrectResponse, "密码错误");
        }
        
        // 处理其他未知状态
        Object messageObj = response.get("message");
        String message = messageObj != null ? messageObj.toString() : "文件预览请求处理失败";
        logger.warn("❌ 文件预览请求处理失败 - FileId: {}, Status: {}, Message: {}",
                fileId, status, message);
        return ReturnResponse.badRequest("文件预览请求处理失败: " + message);
    }

   

    /**
     * 获取指定页面预览 - 多页文件支持
     * 
     * @param fileId      文件ID
     * @param pageNumber  页码（从0开始）
     * @param httpRequest HTTP请求对象
     * @return 单页预览响应
     */
    @GetMapping("/files/{fileId}/page/{pageNumber}")
    public ResponseEntity<Resource> getPagePreview(
            @PathVariable String fileId,
            @PathVariable int pageNumber,
            HttpServletRequest httpRequest) {

        try {
            logger.info("📑 多页文件页面请求 - FileId: {}, Page: {}", fileId, pageNumber);

            // 参数验证
            if (fileId == null || fileId.trim().isEmpty()) {
                logger.warn("⚠️ 文件ID为空");
                return ResponseEntity.badRequest().build();
            }

            if (pageNumber < 0) {
                logger.warn("⚠️ 页码不合法: {}", pageNumber);
                return ResponseEntity.badRequest().build();
            }

            // 从缓存中获取多页信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            if (cacheInfo == null) {
                logger.warn("⚠️ 未找到文件缓存信息 - FileId: {}", fileId);
                return ResponseEntity.notFound().build();
            }

            // 检查是否为多页文件
            if (!cacheInfo.isMultiPage()) {
                logger.warn("⚠️ 不是多页文件 - FileId: {}", fileId);
                return ResponseEntity.badRequest().build();
            }

            // 检查页码是否超出范围
            if (pageNumber >= cacheInfo.getTotalPages()) {
                logger.warn("⚠️ 页码超出范围 - FileId: {}, Page: {}, TotalPages: {}",
                        fileId, pageNumber, cacheInfo.getTotalPages());
                return ResponseEntity.badRequest().build();
            }

            // 获取页面URL
            Map<Integer, String> pageUrls = cacheInfo.getPageUrls();
            if (pageUrls == null || !pageUrls.containsKey(pageNumber)) {
                logger.warn("⚠️ 未找到页面URL - FileId: {}, Page: {}", fileId, pageNumber);
                return ResponseEntity.notFound().build();
            }

            // 获取页面文件路径
            String pagesDirectory = cacheInfo.getPagesDirectory();
            if (pagesDirectory == null || pagesDirectory.trim().isEmpty()) {
                logger.error("❌ 页面目录为空 - FileId: {}", fileId);
                return ResponseEntity.notFound().build();
            }

            // 查找页面文件
            String[] extensions = { "png", "jpg" };
            Path pageFile = null;

            for (String ext : extensions) {
                Path testFile = Paths.get(pagesDirectory, "page_" + pageNumber + "." + ext);
                if (Files.exists(testFile)) {
                    pageFile = testFile;
                    break;
                }
            }

            if (pageFile == null || !Files.exists(pageFile)) {
                logger.warn("⚠️ 页面文件不存在 - FileId: {}, Page: {}, Directory: {}",
                        fileId, pageNumber, pagesDirectory);
                return ResponseEntity.notFound().build();
            }

            // 安全检查
            if (!fileUtils.isSecurePath(pageFile.toString())) {
                logger.warn("🚫 不安全的文件路径 - FileId: {}, Path: {}", fileId, pageFile);
                return ResponseEntity.badRequest().build();
            }

            // 创建文件资源
            Resource resource = new UrlResource(pageFile.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("⚠️ 页面文件不可读 - FileId: {}, Page: {}", fileId, pageNumber);
                return ResponseEntity.notFound().build();
            }

            // 确定文件类型
            String contentType = httpUtils.determineContentType(pageFile.toString());

            // 获取文件名
            String fileName = pageFile.getFileName().toString();
            String encodedFileName = fileUtils.encodeFileName(fileName);

            logger.info("✅ 页面访问成功 - FileId: {}, Page: {}, FileName: {}, Size: {} bytes",
                    fileId, pageNumber, fileName, Files.size(pageFile));

            // 返回文件响应
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600") // 1小时缓存
                    .body(resource);

        } catch (Exception e) {
            logger.error("💥  页面访问异常 - FileId: {}, Page: {}", fileId, pageNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有页面信息 - 多页文件支持
     * 
     * 返回格式：
     * {
     * "success": true,
     * "message": "多页文件预览成功",
     * "timestamp": 1234567890,
     * "data": {
     * "fileId": "xxx",
     * "isMultiPage": true,
     * "totalPages": 5,
     * "pageUrls": {...},
     * "pageUrlsList": [...],
     * ...
     * }
     * ...
     * }
     * 
     * @param fileId      文件ID
     * @param httpRequest HTTP请求对象
     * @return 多页预览响应（包含统一响应字段）
     */
    @GetMapping("/files/{fileId}/pages")
    public ResponseEntity<Map<String, Object>> getAllPages(
            @PathVariable String fileId,
            HttpServletRequest httpRequest) {

        try {
            logger.info("📑 获取所有页面信息 - FileId: {}", fileId);

            // 参数验证
            if (fileId == null || fileId.trim().isEmpty()) {
                throw FileViewException.of(ErrorCode.INVALID_FILE_ID, "文件ID不能为空");
            }

            // 从缓存中获取多页信息
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            if (cacheInfo == null) {
                throw FileViewException.of(
                        ErrorCode.FILE_NOT_FOUND,
                        "未找到文件缓存信息").withFileId(fileId);
            }

            // 检查是否为多页文件
            if (!cacheInfo.isMultiPage()) {
                // 不是多页文件，返回错误响应
                Map<String, Object> errorResponse = previewResponseAssembler.buildNotMultiPageResponse(
                        fileId,
                        httpRequest.getRequestURI(),
                        urlExpirationHours
                );
                return ReturnResponse.success(errorResponse, "文件不是多页文件");
            }

            // 构建成功响应
            Long remainingTtl = cacheReadService.getCacheTTL(fileId);
            // 🔑 获取请求上下文 baseUrl
            String requestBaseUrl = httpUtils.getDynamicBaseUrl();
            Map<String, Object> response = previewResponseAssembler.buildMultiPageResponse(
                    fileId,
                    cacheInfo,
                    remainingTtl,
                    httpRequest.getRequestURI(),
                    urlExpirationHours,
                    requestBaseUrl
            );

            logger.info("✅ 多页信息获取成功 - FileId: {}, TotalPages: {}",
                    fileId, cacheInfo.getTotalPages());

            return ReturnResponse.success(response, "多页文件预览成功");

        } catch (FileViewException e) {
            throw e; // 直接抛出，由GlobalExceptionHandler处理
        } catch (Exception e) {
            logger.error("💪 获取多页信息异常 - FileId: {}", fileId, e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "获取多页信息异常: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }
}