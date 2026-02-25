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
package com.basemetas.fileview.preview.service;

import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.config.FileFormatConfig;
import com.basemetas.fileview.preview.config.FileTypeMapper;
import com.basemetas.fileview.preview.config.StorageConfig;
import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.service.password.FilePasswordValidator.PasswordValidationResult;
import com.basemetas.fileview.preview.service.password.FilePasswordValidator;
import com.basemetas.fileview.preview.service.password.PasswordUnlockService;
import com.basemetas.fileview.preview.service.response.PreviewResponseAssembler;
import com.basemetas.fileview.preview.service.ArchiveExtractService.ArchivePasswordException;
import com.basemetas.fileview.preview.service.ArchiveExtractService.ArchiveExtractionException;
import com.basemetas.fileview.preview.service.cache.CacheWriteService;
import com.basemetas.fileview.preview.service.cache.PreviewCacheAssembler;
import com.basemetas.fileview.preview.service.cache.CacheReadService;
import com.basemetas.fileview.preview.service.download.DownloadTaskManager;
import com.basemetas.fileview.preview.service.mq.producer.DownloadTaskProducer;
import com.basemetas.fileview.preview.service.mq.event.FilePreviewEvent;
import com.basemetas.fileview.preview.service.mq.producer.FilePreviewEventProducer;
import com.basemetas.fileview.preview.service.url.PreviewUrlService;
import com.basemetas.fileview.preview.utils.FileUtils;
import com.basemetas.fileview.preview.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件预览服务核心实现（最终架构版）
 * 
 * 负责处理文件预览的核心业务逻辑：
 * 1. 服务器文件预览 - 文件已在存储服务上
 * 2. 网络文件下载预览 - 需要先下载到存储服务
 * 
 * 架构设计：
 * - 预览服务查询转换服务在Redis中的缓存结果
 * - 支持智能轮询和长轮询，等待转换完成
 * - 专注于预览业务逻辑，转换结果由转换服务统一管理
 * 
 * @author 夫子
 */
@Service
public class FilePreviewService {

    private static final Logger logger = LoggerFactory.getLogger(FilePreviewService.class);

    @Autowired
    private CacheReadService cacheReadService;

    @Autowired
    private CacheWriteService cacheWriteService;

    @Autowired
    private StorageConfig storageConfig;

    @Autowired
    private PreviewResponseAssembler previewResponseAssembler;

    @Autowired
    private FileFormatConfig fileFormatConfig;

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private PreviewUrlService previewUrlService;

    @Autowired
    private DownloadTaskManager downloadTaskManager;

    @Autowired
    private DownloadTaskProducer downloadTaskProducer;

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private ArchiveExtractService archiveExtractService;

    @Autowired
    private FilePreviewEventProducer filePreviewEventProducer;

    @Autowired
    private PasswordUnlockService passwordUnlockService;

    @Autowired
    private FilePasswordValidator filePasswordValidator;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PreviewCacheAssembler previewCacheAssembler;

    @Value("${fileview.preview.xlsx.smart-preview-enabled:true}")
    private boolean xlsxSmartPreviewEnabled;

    @Value("${fileview.preview.url.expiration-hours:24}")
    private int urlExpirationHours;

    /**
     * 处理服务器文件预览
     */
    public Map<String, Object> processServerFilePreview(FilePreviewRequest request, long startTime) {
        return processServerFilePreview(request, startTime, null);
    }
    
    /**
     * 处理服务器文件预览（支持传入 requestBaseUrl）
     */
    public Map<String, Object> processServerFilePreview(FilePreviewRequest request, long startTime, String requestBaseUrl) {
        try {
            String srcRelativePath = request.getSrcRelativePath();
            if (srcRelativePath == null || srcRelativePath.trim().isEmpty()) {
                throw FileViewException.of(
                        ErrorCode.MISSING_REQUIRED_PARAMETER,
                        "源文件路径不能为空");
            }
            // 处理文件路径和文件名逻辑
            String actualFilePath = fileUtils.processFilePath(srcRelativePath, request.getFileName());
            if (actualFilePath == null) {
                throw FileViewException.of(
                        ErrorCode.INVALID_FILE_PATH,
                        "文件路径处理失败: " + srcRelativePath).withFileId(request.getFileId());
            }
            
            // 🚫 安全检查：在Linux环境下禁止访问系统级目录
            if (fileUtils.isSystemDirectory(actualFilePath)) {
                logger.error("🚫 安全拒绝：尝试访问系统目录 - Path: {}", actualFilePath);
                throw FileViewException.of(
                        ErrorCode.ACCESS_DENIED,
                        "安全拒绝：不允许访问系统目录: " + actualFilePath)
                        .withFileId(request.getFileId());
            }
            String clientId = request.getClientId();
            String fileId = request.getFileId();
            if (fileId == null || fileId.isEmpty()) {
                fileId = fileUtils.generateFileIdFromFileUrl(actualFilePath);
            }
            // 判断是否是压缩包内部文件，如果是则解压，并返回解压后的文件路径
            boolean isShouldExtract = archiveExtractService.shouldExtract(actualFilePath).isShouldExtract();
            if (isShouldExtract) {
                try {
                    actualFilePath = handleArchiveExtraction(
                            actualFilePath, fileId, clientId, request.getPassword(), startTime);
                } catch (ArchiveExtractionResponseException e) {
                    // 解压过程中发生需要立即返回的异常（如密码错误、不支持的格式等）
                    return e.getResponse();
                }
            }
            // 先从缓存中查询
            if (!request.isForceRegenerate()) {
                Map<String, Object> cachedResponse = handleCacheCheck(fileId, clientId, 
                        request.getPassword(), startTime, requestBaseUrl);
                if (cachedResponse != null) {
                    return cachedResponse;
                }
            }
            // 验证文件是否存在
            File file = new File(actualFilePath);
            if (!file.exists() || !file.isFile()) {
                throw FileViewException.of(
                        ErrorCode.FILE_NOT_FOUND,
                        "文件不存在: " + actualFilePath).withFileId(request.getFileId());
            }
            // 检查文件大小（使用StorageConfig配置）
            long fileSize = file.length();
            long maxSize = storageConfig.getMaxFileSizeBytes();
            if (fileSize > maxSize) {
                throw FileViewException.of(
                        ErrorCode.FILE_TOO_LARGE,
                        String.format("文件大小 %.2f MB 超过限制 %.2f MB",
                                fileSize / 1024.0 / 1024.0, maxSize / 1024.0 / 1024.0))
                        .withFileId(request.getFileId());
            }

            // 获取文件信息（使用FileTypeMapper正确处理复合扩展名如tar.gz）
            String fileFormat = fileTypeMapper.extractExtension(file.getName());
            if (fileFormat == null || fileFormat.trim().isEmpty()) {
                throw FileViewException.of(
                        ErrorCode.INVALID_FILE_TYPE,
                        "无法识别文件类型: " + file.getName()).withFileId(request.getFileId());
            }

            // 处理目标文件名
            // 如果fileName为空，从actualFilePath中提取文件名
            String fileName = request.getFileName();
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = file.getName();
                request.setFileName(fileName);
                logger.debug("从文件路径中提取文件名: {}", fileName);
            }
            String targetFileName = fileUtils.processTargetFileName(request.getTargetFileName(), fileName);

            if (request.getSourceService() == null || request.getSourceService().isEmpty()) {
                request.setSourceService("serverFile-preview-service");
            }
            // 判断预览模式和处理逻辑
            return processFilePreview(fileId, actualFilePath, fileFormat, fileSize, targetFileName, startTime, request, requestBaseUrl);

        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理服务器文件预览失败 - FileId: {}", request.getFileId(), e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理服务器文件预览失败: " + e.getMessage(),
                    e).withFileId(request.getFileId());
        }
    }

    /**
     * 处理网络文件下载预览（异步方式）
     */
    public Map<String, Object> processNetworkFilePreview(FilePreviewRequest request, long startTime) {
        return processNetworkFilePreview(request, startTime, null);
    }
    
    /**
     * 处理网络文件下载预览（支持传入 requestBaseUrl）
     */
    public Map<String, Object> processNetworkFilePreview(FilePreviewRequest request, long startTime, String requestBaseUrl) {
        String fileId = "";
        try {
            // 1. 验证参数
            String networkFileUrl = request.getNetworkFileUrl();
            if (networkFileUrl == null || networkFileUrl.trim().isEmpty()) {
                throw FileViewException.of(
                        ErrorCode.MISSING_REQUIRED_PARAMETER,
                        "网络文件URL不能为空");
            }
            
            // 🚫 2. URL安全校验（信任站点/不信任站点）
            HttpUtils.UrlValidationResult securityResult = httpUtils.validateUrlSecurity(networkFileUrl);
            if (!securityResult.isValid()) {
                logger.error("🚫 URL安全校验失败 - URL: {}, Reason: {}", networkFileUrl, securityResult.getErrorMessage());
                throw FileViewException.of(
                        ErrorCode.ACCESS_DENIED,
                        securityResult.getErrorMessage());
            }
            // 3. 验证文件格式（在下载前检查）
            String urlFileName = httpUtils.extractFileNameFromUrl(networkFileUrl);
            String requestFileName = request.getFileName();
            String fileNameForType = urlFileName;
            // 如果前端提供了 fileName 且能提取出有效扩展名，则优先使用前端文件名进行类型判断
            if (requestFileName != null && !requestFileName.trim().isEmpty()) {
                String clientExt = fileTypeMapper.extractExtension(requestFileName);
                if (clientExt != null && !clientExt.isEmpty()) {
                    fileNameForType = requestFileName;
                }
            }
            String fileExtension = fileTypeMapper.extractExtension(fileNameForType);

            String displayFileName = (requestFileName != null && !requestFileName.trim().isEmpty())
                    ? requestFileName
                    : urlFileName;

            if (fileExtension == null || fileExtension.isEmpty()) {
                logger.warn("⚠️ 无法从URL或前端文件名提取文件扩展名 - URL: {}, UrlFileName: {}, RequestFileName: {}", 
                        networkFileUrl, urlFileName, requestFileName);
                return previewResponseAssembler.buildInvalidParameterResponse(
                        request.getFileId() != null ? request.getFileId() : "",
                        displayFileName,
                        "无法识别文件类型，请确保URL或fileName包含有效的文件扩展名",
                        startTime,
                        urlExpirationHours
                );
            }

            if (!fileTypeMapper.isSupported(fileExtension)) {
                logger.warn("⚠️ 不支持的文件格式 - Extension: {}, FileName: {}", fileExtension, displayFileName);
                return previewResponseAssembler.buildUnsupportedFormatResponse(
                        request.getFileId() != null ? request.getFileId() : "",
                        displayFileName,
                        fileExtension,
                        "不支持的文件格式: ." + fileExtension,
                        startTime,
                        urlExpirationHours
                );
            }

            logger.info("✅ 文件格式验证通过 - Extension: {}, Category: {}",
                    fileExtension, fileTypeMapper.getFileCategory(fileExtension));

            // 3. 对于网络下载，如果fileId为空，基于URL自动生成
            fileId = request.getFileId();
            if (fileId == null || fileId.trim().isEmpty()) {
                fileId = fileUtils.generateFileIdFromFileUrl(request.getNetworkFileUrl());
                // 设置回request对象，确保后续流程能获取到正确的fileId
                request.setFileId(fileId);
            }

            // 4. 检查缓存（如果不需要强制重新生成）
            if (!request.isForceRegenerate()) {
                Map<String, Object> cachedResponse = handleNetworkFileCacheCheck(
                        fileId, request, displayFileName, startTime, requestBaseUrl);
                if (cachedResponse != null) {
                    return cachedResponse;
                }
            }

            // 5. 确定下载目标路径（如果没有指定，使用默认路径）
            String downloadTargetPath = request.getDownloadTargetPath();
            if (downloadTargetPath == null || downloadTargetPath.trim().isEmpty()) {
                downloadTargetPath = storageConfig.getDownloadDir();
                // 设置回request对象，确保后续流程能获取到正确的路径
                request.setDownloadTargetPath(downloadTargetPath);
            }

            // 6. 创建异步下载任务
            DownloadTask downloadTask = downloadTaskManager.createTask(request);
            // 🔑 设置 requestBaseUrl 到下载任务
            if (requestBaseUrl != null && !requestBaseUrl.isEmpty()) {
                downloadTask.setRequestBaseUrl(requestBaseUrl);
                // 更新到 Redis
                downloadTaskManager.updateTask(downloadTask);
            }

            // 7. 发送下载任务到消息队列
            downloadTaskProducer.sendDownloadTask(downloadTask);

            // 8. 立即返回fileId，让客户端轮询状态
            return previewResponseAssembler.buildDownloadingResponse(
                    fileId,
                    displayFileName,
                    startTime,
                    urlExpirationHours
            );

        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理网络文件下载预览失败 - FileId: {}", fileId, e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理网络文件下载预览失败: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }

    /**
     * 处理文件预览核心逻辑
     */
    private Map<String, Object> processFilePreview(String fileId, String filePath, String fileFormat,
            long fileSize, String targetFileName, long startTime, FilePreviewRequest request, String requestBaseUrl) {
        try {
            // 📊 XLSX智能预览：检测加密状态决定预览方式
            if ("xlsx".equalsIgnoreCase(fileFormat) && xlsxSmartPreviewEnabled) {
                return handleXlsxSmartPreview(fileId, filePath, fileFormat, fileSize, targetFileName, 
                        startTime, request, requestBaseUrl);
            }
            
            // 🔑 关键逻辑：对于压缩包文件、Office文档、PDF文件，在发送转换之前先检测是否需要密码
            PasswordValidationResult validationResult;
            try {
                validationResult = handlePasswordValidation(
                        fileId, filePath, fileFormat, fileSize, request, startTime);
            } catch (PasswordValidationResponseException e) {
                // 密码验证过程中发生需要立即返回的异常（密码错误、需要密码等）
                return e.getResponse();
            }

            // 判断是否需要转换
            if (requiresConversion(fileFormat)) {
                // A场景：需要转换
                // 🔑 如果是加密文件，将 encrypted 信息和密码传递给转换模块
                if (validationResult != null && validationResult.isEncrypted()) {
                    // 添加 encrypted 标记
                    request.addExtendedParam("encrypted", true);

                    // 🔑 关键修复：将密码传递给转换模块
                    String clientId = request.getClientId();
                    String password = passwordUnlockService.getPassword(fileId, clientId);
                    if (password != null && !password.trim().isEmpty()) {
                        request.setPassword(password);
                    }
                }
                return processConversionPreview(fileId, filePath, fileFormat, fileSize, targetFileName, startTime,
                        request, requestBaseUrl);
            } else if (supportDirectPreview(fileFormat)) {
                // B场景：直接预览
                return processDirectPreview(fileId, filePath, fileFormat, fileSize, startTime, request, requestBaseUrl);
            } else {
                // 不支持的文件类型
                Map<String, Object> response = previewResponseAssembler.buildNotSupportedResponse(
                        fileId,
                        fileFormat,
                        request.getFileName(),
                        filePath,
                        fileSize,
                        startTime,
                        urlExpirationHours
                );

                // 缓存不支持的文件类型结果到Redis，避免长轮询一直查询
                try {
                    PreviewCacheInfo cacheInfo = previewCacheAssembler.buildNotSupportedDirectCacheInfo(
                        fileId,
                        request.getFileName(),
                        filePath,
                        fileFormat,
                        fileSize
                    );
                    // 写入直接预览缓存
                    cacheWriteService.cacheDirectPreview(fileId, cacheInfo);
                } catch (Exception cacheEx) {
                    // 缓存失败不影响主流程
                    logger.warn("⚠️ 缓存不支持的文件类型结果失败 - FileId: {}, Error: {}", fileId, cacheEx.getMessage());
                }

                return response;
            }

        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理文件预览失败 - FileId: {}, FileType: {}", fileId, fileFormat, e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理文件预览失败: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }

    /**
     * XLSX智能预览处理
     * 检测加密状态：加密→转换，非加密→直接预览
     */
    private Map<String, Object> handleXlsxSmartPreview(String fileId, String filePath, String fileFormat,
            long fileSize, String targetFileName, long startTime, 
            FilePreviewRequest request, String requestBaseUrl) {
        
        logger.info("📊 XLSX智能预览 - FileId: {}", fileId);
        
        String password = request.getPassword();
        String clientId = request.getClientId();
        password = resolvePasswordFromCache(fileId, clientId, password);
        
        PasswordValidationResult validationResult;
        if (password == null || password.trim().isEmpty()) {
            validationResult = filePasswordValidator.quickDetectEncryption(filePath, fileFormat);
        } else {
            validationResult = filePasswordValidator.validatePassword(filePath, password, fileFormat);
        }     
        if (validationResult.isEncrypted()) {      
            if (password == null || password.trim().isEmpty()) {
                return buildPasswordRequiredResponse(fileId, filePath, fileFormat, fileSize,
                        request.getFileName(), validationResult, startTime);
            }
            
            if (validationResult.isPasswordCorrect() != null && !validationResult.isPasswordCorrect()) {
                return buildPasswordIncorrectResponse(fileId, filePath, fileFormat, fileSize,
                        request.getFileName(), validationResult, startTime);
            }
            
            // 🔑 关键修复：记录 clientId 的解锁状态
            if (clientId != null && !clientId.trim().isEmpty()) {
                passwordUnlockService.markUnlocked(fileId, clientId, password);
            }
            
            // 添加 encrypted 标记
            request.addExtendedParam("encrypted", true);
            request.setPassword(password);
            
            return processConversionPreview(fileId, filePath, fileFormat, fileSize, targetFileName, startTime,
                    request, requestBaseUrl);
        }      
        logger.info("🔓 XLSX未加密，直接预览 - FileId: {}", fileId);
        return processDirectPreview(fileId, filePath, fileFormat, fileSize, startTime, request, requestBaseUrl);
    }

    /**
     * 处理需要转换的文件预览（A场景）
     */
    private Map<String, Object> processConversionPreview(String fileId, String filePath, String fileFormat,
            long fileSize, String targetFileName, long startTime, FilePreviewRequest request, String requestBaseUrl) {
        try {
            logger.info("🔄 处理转换预览 - FileId: {}, FileType: {}, TargetFileName: {}", fileId, fileFormat, targetFileName);

            // 确定目标格式
            String targetFormat = request.getPreferredFormat() != null ? request.getPreferredFormat()
                    : fileFormatConfig.getDefaultTargetFormat(fileFormat.toLowerCase());

            // 如果没有配置默认格式，使用pdf作为fallback
            if (targetFormat == null) {
                targetFormat = "pdf";
                logger.warn("⚠️ 文件类型 {} 未配置默认转换格式，使用默认值: pdf", fileFormat);
            }

            FilePreviewEvent previewEvent = new FilePreviewEvent();
            // 【增强功能】如果目标路径为空，则使用默认配置的路径
            String targetPath = request.getTargetPath();
            if (targetPath == null || targetPath.trim().isEmpty()) {
                targetPath = storageConfig.getPreviewDir();
            }
            // 【性能优化3】提前生成完整的目标文件路径（避免Consumer转换后再拼接）
            String fullTargetPath = fileUtils.buildFullTargetPath(  
                    targetPath, // 使用处理后的目标路径
                    targetFileName, // 使用处理后的目标文件名
                    targetFormat);
            // 更新事件特定字段
            previewEvent.setFileId(fileId);
            previewEvent.setFilePath(filePath);
            previewEvent.setFileName(request.getFileName());
            previewEvent.setSourceFormat(fileFormat);
            previewEvent.setTargetPath(targetPath);
            previewEvent.setTargetFormat(targetFormat);
            previewEvent.setTargetFileName(targetFileName + "." + targetFormat);
            previewEvent.setFullTargetPath(fullTargetPath);
            previewEvent.setEventType(FilePreviewEvent.PreviewEventType.PREVIEW_REQUESTED);
            previewEvent.setSourceService(request.getSourceService());
            previewEvent.setConversionRequired(true);
            // 🔑 关键修复：传递密码到转换服务
            previewEvent.setPassWord(request.getPassword());
            // 🔑 关键修复：传递 encrypted 状态到转换服务
            if (request.getExtendedParams() != null) {
                Object encrypted = request.getExtendedParams().get("encrypted");
                if (encrypted instanceof Boolean) {
                    previewEvent.setEncrypted((Boolean) encrypted);
                    logger.info("🔒 设置 encrypted 到事件 - FileId: {}, Encrypted: {}", fileId, encrypted);
                }
            }
            // 🔑 将 requestBaseUrl 添加到 extendedParams 和 businessParams，供转换服务使用
            if (requestBaseUrl != null && !requestBaseUrl.isEmpty()) {
                // 设置到 extendedParams
                Map<String, Object> extendedParams = previewEvent.getExtendedParams();
                if (extendedParams == null) {
                    extendedParams = new java.util.HashMap<>();
                    previewEvent.setExtendedParams(extendedParams);
                }
                extendedParams.put("requestBaseUrl", requestBaseUrl);
                
                // 🔑 关键：同时设置到 businessParams，用于与 FileEvent 的 businessParams 字段对应
                Map<String, Object> businessParams = previewEvent.getBusinessParams();
                if (businessParams == null) {
                    businessParams = new java.util.HashMap<>();
                    previewEvent.setBusinessParams(businessParams);
                }
                businessParams.put("requestBaseUrl", requestBaseUrl);
                
                logger.debug("🌐 添加 requestBaseUrl 到事件 - FileId: {}, BaseUrl: {}", fileId, requestBaseUrl);
            }
            // 发送预览事件到RocketMQ
            long mqSendStart = System.currentTimeMillis();
            filePreviewEventProducer.sendFilePreviewEventAsync(previewEvent);
            long mqSendTime = System.currentTimeMillis() - mqSendStart;
            long totalProcessTime = System.currentTimeMillis() - startTime;
            logger.debug("⏱️ 转换MQ发送耗时: {}ms, 总处理耗时: {}ms - FileId: {}, TargetFormat: {}", 
                mqSendTime, totalProcessTime, fileId, targetFormat);
            // 返回转换中状态
            Map<String, Object> response = previewResponseAssembler.buildConverting(
                    fileId,
                    filePath,
                    request.getFileName(),
                    fileFormat,
                    fileSize,
                    targetFormat,
                    startTime,
                    urlExpirationHours,
                    fileTypeMapper
            );
            return response;
        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理转换预览失败 - FileId: {}", fileId, e);
            throw FileViewException.of(
                    ErrorCode.CONVERSION_FAILED,
                    "处理转换预览失败: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }

    /**
     * 处理直接预览（B场景）- 支持缓存版
     */
    private Map<String, Object> processDirectPreview(String fileId, String filePath, String fileFormat,
            long fileSize, long startTime, FilePreviewRequest request, String requestBaseUrl) {
        try {
            logger.info("➡️ 处理直接预览 - FileId: {}, FileType: {}", fileId, fileFormat);

            // 生成预览URL（相对地址）
            String previewUrl = previewUrlService.generatePreviewUrl(fileId, filePath);

            // 构建预览响应
            Map<String, Object> response = previewResponseAssembler.buildDirectPreviewResponse(
                    fileId,
                    previewUrl,
                    filePath,
                    request.getFileName(),
                    fileFormat,
                    fileSize,
                    startTime,
                    urlExpirationHours,
                    requestBaseUrl != null ? requestBaseUrl : httpUtils.getDynamicBaseUrl()
            );

            logger.info("✅ 直接预览处理完成 - FileId: {}, URL: {}", fileId, previewUrl);

            // 缓存直接预览结果到Redis
            try {
                PreviewCacheInfo cacheInfo = previewCacheAssembler.buildDirectCacheInfo(
                        fileId,
                        previewUrl,
                        filePath,
                        request.getFileName(),
                        fileFormat,
                        fileSize
                );

                // 写入缓存
                cacheWriteService.cacheDirectPreview(fileId, cacheInfo);

                logger.info("📦 直接预览结果已缓存 - FileId: {}", fileId);

            } catch (Exception cacheEx) {
                // 缓存失败不影响主流程
                logger.warn("⚠️ 缓存直接预览结果失败 - FileId: {}, Error: {}", fileId, cacheEx.getMessage());
            }

            return response;

        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            logger.error("❌ 处理直接预览失败 - FileId: {}", fileId, e);
            throw FileViewException.of(
                    ErrorCode.SYSTEM_ERROR,
                    "处理直接预览失败: " + e.getMessage(),
                    e).withFileId(fileId);
        }
    }

    /**
     * 从缓存构建响应
     */
    private Map<String, Object> buildResponseFromCache(PreviewCacheInfo cacheInfo,
            long startTime, String requestBaseUrl) {
        Long remainingTtl = cacheReadService.getCacheTTL(cacheInfo.getFileId());
        return previewResponseAssembler.buildSuccessFromCache(
                cacheInfo.getFileId(),
                cacheInfo,
                remainingTtl,
                startTime,
                requestBaseUrl,
                urlExpirationHours,
                fileTypeMapper
        );
    }

    /**
     * 判断文件类型是否需要转换
     */
    private boolean requiresConversion(String fileFormat) {
        return fileFormatConfig.needsConversion(fileFormat);
    }

    /**
     * 判断文件类型是否支持直接预览
     */
    private boolean supportDirectPreview(String fileFormat) {
        return fileFormatConfig.supportsDirectPreview(fileFormat);
    }
    

    /**
     * 判断是否为加密压缩包
     * 通过检查缓存信息中的 encrypted 标记来判断
     */
    public boolean isEncryptedArchive(PreviewCacheInfo cacheInfo) {
        if (cacheInfo == null) {
            return false;
        }

        // 检查 encrypted 字段
        Boolean encrypted = cacheInfo.getEncrypted();
        if (encrypted != null && encrypted) {
            logger.debug("🔒 检测到加密文件 - FileId: {}", cacheInfo.getFileId());
            return true;
        }

        return false;
    }

    /**
     * 从缓存信息构建 PASSWORD_REQUIRED 响应
     */
    private Map<String, Object> buildPasswordRequiredResponseFromCache(PreviewCacheInfo cacheInfo, long startTime) {
        return previewResponseAssembler.buildPasswordRequiredFromCache(cacheInfo, startTime, urlExpirationHours);
    }

    /**
     * 从缓存信息构建 PASSWORD_INCORRECT 响应
     */
    private Map<String, Object> buildPasswordIncorrectResponseFromCache(PreviewCacheInfo cacheInfo, long startTime) {
        return previewResponseAssembler.buildPasswordIncorrectFromCache(cacheInfo, startTime, urlExpirationHours);
    }

    /**
     * 构建 PASSWORD_REQUIRED 响应并缓存
     */
    private Map<String, Object> buildPasswordRequiredResponse(String fileId, String filePath,
            String fileFormat, long fileSize, String fileName,
            PasswordValidationResult validationResult,
            long startTime) {

        // 构建响应
        Map<String, Object> response = previewResponseAssembler.buildPasswordRequiredArchive(
                fileId,
                filePath,
                fileFormat,
                fileSize,
                fileName,
                validationResult.getArchiveFormat(),
                validationResult.getErrorMessage(),
                startTime,
                urlExpirationHours
        );

        // 缓存到 Redis，让长轮询能查询到并停止
        // 🔑 修复：压缩文件应该缓存到转换缓存，而不是直接预览缓存
        try {
            // 获取目标格式（压缩文件默认转 JSON）
            String targetFormat = fileFormatConfig.getDefaultTargetFormat(fileFormat.toLowerCase());
            if (targetFormat == null) {
                targetFormat = "json";
            }

            // 构建转换缓存数据
            Map<String, Object> cacheData = previewCacheAssembler.buildPasswordRequiredConvertCache(
                fileId,
                fileName,
                filePath,
                fileFormat,
                fileSize
            );

            // 写入转换缓存
            String cacheKey = "convert:" + fileId + ":" + targetFormat.toLowerCase();
            redisTemplate.opsForValue().set(cacheKey, cacheData, Duration.ofSeconds(86400));
            logger.info("📌 PASSWORD_REQUIRED 状态已缓存到转换缓存 - FileId: {}, Key: {}", fileId, cacheKey);
        } catch (Exception cacheEx) {
            logger.warn("⚠️ 缓存 PASSWORD_REQUIRED 状态失败 - FileId: {}", fileId, cacheEx);
        }

        return response;
    }

    /**
     * 构建 PASSWORD_INCORRECT 响应并缓存
     */
    private Map<String, Object> buildPasswordIncorrectResponse(String fileId, String filePath,
            String fileFormat, long fileSize, String fileName,
            PasswordValidationResult validationResult,
            long startTime) {

        // 构建响应
        Map<String, Object> response = previewResponseAssembler.buildPasswordIncorrectArchive(
                fileId,
                filePath,
                fileFormat,
                fileSize,
                fileName,
                validationResult.getArchiveFormat(),
                validationResult.getErrorMessage(),
                startTime,
                urlExpirationHours
        );

        // 缓存到 Redis，让长轮询能查询到并停止
        // 🔑 修复：压缩文件应该缓存到转换缓存，而不是直接预览缓存
        try {
            // 获取目标格式（压缩文件默认转 JSON）
            String targetFormat = fileFormatConfig.getDefaultTargetFormat(fileFormat.toLowerCase());
            if (targetFormat == null) {
                targetFormat = "json";
            }

            // 构建转换缓存数据
            Map<String, Object> cacheData = previewCacheAssembler.buildPasswordIncorrectConvertCache(
                fileId,
                fileName,
                filePath,
                fileFormat,
                fileSize
            );

            // 写入转换缓存
            String cacheKey = "convert:" + fileId + ":" + targetFormat.toLowerCase();
            redisTemplate.opsForValue().set(cacheKey, cacheData, Duration.ofSeconds(86400));
            logger.info("📌 PASSWORD_INCORRECT 状态已缓存到转换缓存 - FileId: {}, Key: {}", fileId, cacheKey);
        } catch (Exception cacheEx) {
            logger.warn("⚠️ 缓存 PASSWORD_INCORRECT 状态失败 - FileId: {}", fileId, cacheEx);
        }

        return response;
    }

    /**
     * 处理压缩包解压及相关异常
     * 
     * @param actualFilePath 实际文件路径
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @param password 解压密码
     * @param startTime 开始时间
     * @return 解压成功后的文件路径
     * @throws ArchiveExtractionResponseException 需要立即返回响应时抛出（密码错误、不支持的格式等）
     * @throws FileViewException 解压失败时抛出
     */
    private String handleArchiveExtraction(String actualFilePath, String fileId, String clientId, 
                                          String password, long startTime) {
        try {
            return archiveExtractService.extractArchiveFileWithPassword(
                    actualFilePath, fileId, clientId, password);
        } catch (ArchivePasswordException e) {
            // 密码异常处理
            Map<String, Object> response;
            if ("WRONG_PASSWORD".equals(e.getErrorCode())) {
                response = previewResponseAssembler.buildSimplePasswordIncorrectResponse(
                        fileId,
                        e.getMessage(),
                        startTime,
                        urlExpirationHours
                );
            } else {
                response = previewResponseAssembler.buildSimplePasswordRequiredResponse(
                        fileId,
                        e.getMessage(),
                        startTime,
                        urlExpirationHours
                );
            }
            // 通过抛出特殊异常来传递响应
            throw new ArchiveExtractionResponseException(response);

        } catch (ArchiveExtractService.UnsupportedFileFormatException e) {
            // 不支持的文件格式异常处理 - 返回NOT_SUPPORTED响应而不是抛出异常
            logger.warn("⚠️ 压缩包内不支持的文件格式 - FileId: {}, Message: {}", fileId, e.getMessage());
            Map<String, Object> response = previewResponseAssembler.buildSimpleNotSupportedResponse(
                    fileId,
                    e.getMessage(),
                    startTime,
                    urlExpirationHours
            );
            // 通过抛出特殊异常来传递响应
            throw new ArchiveExtractionResponseException(response);

        } catch (ArchiveExtractionException e) {
            // 解压失败异常处理
            throw FileViewException.of(
                    ErrorCode.FILE_EXTRACT_FAILED,
                    e.getMessage()).withFileId(fileId);
        }
    }

        // 处理网络文件缓存检查逻辑（包含密码重试和本地文件验证）
        // 🔁 注意：该方法只在网络文件入口调用一次，不在转换执行链路中重复调用
        private Map<String, Object> handleNetworkFileCacheCheck(String fileId, FilePreviewRequest request,
                                                                 String fileName, long startTime, String requestBaseUrl) {
            PreviewCacheInfo cachedResult = cacheReadService.getCachedResult(fileId);
            if (cachedResult == null) {
                return null;
            }

            String cachedStatus = cachedResult.getStatus();
            String localFilePath = cachedResult.getOriginalFilePath();
            String localFileId = fileUtils.generateFileIdFromFileUrl(localFilePath);
            String clientId = request.getClientId();
            String password = passwordUnlockService.getPassword(localFileId, clientId);
        
        // 🔑 关键逻辑：如果请求中包含密码，且缓存状态为 PASSWORD_REQUIRED、PASSWORD_INCORRECT 或 FAILED
        // 则先检查本地文件是否存在，如果存在则直接验证密码，无需重新下载
        if (password != null && !password.trim().isEmpty()) {
            if ("PASSWORD_REQUIRED".equals(cachedStatus) || 
                "PASSWORD_INCORRECT".equals(cachedStatus) ||
                "FAILED".equals(cachedStatus)) {
                logger.info("🔄 请求包含密码，检查是否需要重新下载 - FileId: {}", fileId);
                request.setPassword(password);
                
                // 检查是否有已下载的任务记录
                DownloadTask existingTask = downloadTaskManager.getTask(fileId);
                if (existingTask != null
                        && existingTask.getStatus() == DownloadTaskStatus.DOWNLOADED
                        && existingTask.getLocalFilePath() != null) {
                    File localFile = new File(localFilePath);

                    // 如果本地文件存在，直接验证密码并处理预览
                    if (localFile.exists()) {
                        logger.info("✅ 本地文件已存在，直接验证密码 - LocalPath: {}", localFilePath);

                        // 🔑 清除转换缓存，允许重新转换
                        cacheWriteService.clearConvertCache(fileId);

                        // 🔑 关键修复：设置本地文件路径到请求对象，让 processServerFilePreview 能正确处理
                        request.setSrcRelativePath(localFilePath);
                        // 从本地文件路径提取文件名
                        String localFileName = new File(localFilePath).getName();
                        if (request.getFileName() == null || request.getFileName().isEmpty()) {
                            request.setFileName(localFileName);
                        }

                        // 直接调用 processServerFilePreview 处理本地文件
                        return processServerFilePreview(request, startTime, requestBaseUrl);
                    } else {
                        logger.warn("⚠️ 本地文件不存在，需要重新下载 - LocalPath: {}", localFilePath);
                        // 清除缓存，重新下载
                        cacheWriteService.clearAllCache(fileId);
                        return null;
                    }
                } else {
                    logger.info("📝 未找到已下载的任务记录，需要重新下载 - FileId: {}", fileId);
                    // 清除缓存，重新下载
                    cacheWriteService.clearDirectPreviewCache(fileId);
                    return null;
                }
            }
        }

        // 如枟缓存没有被清除，继续检查其他状态
        // 对于 SUCCESS 状态，需要检查加密文件的解锁状态
        if ("SUCCESS".equals(cachedStatus)) {
            // 🔑 调试日志：检查缓存中的 encrypted 字段
            logger.info("🔍 检查缓存加密状态 - FileId: {}, Encrypted: {}",
                    localFileId, cachedResult.getEncrypted());

            // 🔐 安全检查：如果是加密压缩包，验证 clientId 是否已解锁
            if (isEncryptedArchive(cachedResult)) {
                logger.info("🔒 检测到加密文件 - FileId: {}, ClientId: {}", localFileId, clientId);
                if (clientId == null || clientId.trim().isEmpty()) {
                    logger.warn("⚠️ 加密文件缓存命中，但未提供 clientId - FileId: {}", localFileId);
                    return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
                }
                if (!passwordUnlockService.isUnlocked(localFileId, clientId)) {
                    logger.warn("🔒 加密文件缓存命中，但 clientId 未解锁 - FileId: {}, ClientId: {}", localFileId, clientId);
                    return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
                }
                logger.info("✅ 加密文件缓存命中， clientId 已解锁 - FileId: {}, ClientId: {}", fileId, clientId);
            }
            logger.info("✅ 命中 SUCCESS 缓存 - FileId: {}", localFileId);
            return buildResponseFromCache(cachedResult, startTime, requestBaseUrl);
        }

        // 🔑 对于 PASSWORD_REQUIRED 和 PASSWORD_INCORRECT 状态，直接返回，不重复创建下载任务
        if ("PASSWORD_REQUIRED".equals(cachedStatus)) {
            logger.info("🔒 命中 PASSWORD_REQUIRED 缓存 - FileId: {}", fileId);
            return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
        }

        if ("PASSWORD_INCORRECT".equals(cachedStatus)) {
            logger.info("❌ 命中 PASSWORD_INCORRECT 缓存 - FileId: {}", fileId);
            return buildPasswordIncorrectResponseFromCache(cachedResult, startTime);
        }

        // 对于 NOT_SUPPORTED 等终态状态，也直接返回
        if ("NOT_SUPPORTED".equals(cachedStatus)) {
            logger.info("⚠️ 命中 NOT_SUPPORTED 缓存 - FileId: {}", fileId);
            return buildResponseFromCache(cachedResult, startTime, requestBaseUrl);
        }

        // 其他状态（DOWNLOADING, CONVERTING 等）继续往下执行
        logger.debug("🔄 缓存状态为 {} - FileId: {}，继续处理", cachedStatus, fileId);
        return null;
    }

    /**
     * 处理文件密码验证逻辑
     * 
     * @param fileId 文件ID
     * @param filePath 文件路径
     * @param fileFormat 文件格式
     * @param fileSize 文件大小
     * @param request 预览请求
     * @param startTime 开始时间
     * @return 密码验证结果，如果不需要验证则返回null
     * @throws PasswordValidationResponseException 密码验证失败需要立即返回响应时抛出
     */
    private PasswordValidationResult handlePasswordValidation(String fileId, String filePath, 
                                                               String fileFormat, long fileSize,
                                                               FilePreviewRequest request, long startTime) {
        String strategyType = fileTypeMapper.getStrategyType(fileFormat);
        
        // 检测需要密码验证的文件类型：压缩包、Office文档、PDF
        boolean needsPasswordCheck = "archive".equals(strategyType) || 
                                     fileUtils.isOfficeDocument(fileFormat) || 
                                     "pdf".equalsIgnoreCase(fileFormat);
        
        if (!needsPasswordCheck) {
            return null;
        }
        
        logger.debug("📝 检测到需要密码验证的文件 - FileId: {}, Format: {}, Type: {}", 
                   fileId, fileFormat, strategyType);

        // 使用 FilePasswordValidator 快速检测密码需求（无需完全解压或加载文档）
        String password = request.getPassword();
        String clientId = request.getClientId();
        // 🔑 密码获取策略：支持网络文件和服务器文件
        password = resolvePasswordFromCache(fileId, clientId, password);
        PasswordValidationResult validationResult;
        
        // 🚀 性能优化：无密码时使用轻量检测模式，只检测是否加密
        if (password == null || password.trim().isEmpty()) {
            validationResult = filePasswordValidator.quickDetectEncryption(filePath, fileFormat);
            logger.debug("🚀 轻量检测结果 - FileId: {}, Encrypted: {}", fileId, validationResult.isEncrypted());
        } else {
            // 有密码时，进行完整密码验证
            validationResult = filePasswordValidator.validatePassword(filePath, password, fileFormat);
            logger.debug("🔑 密码验证结果 - FileId: {}, Encrypted: {}, PasswordCorrect: {}", 
                       fileId, validationResult.isEncrypted(), validationResult.isPasswordCorrect());
        }
        
        // 如果文件加密
        if (validationResult.isEncrypted()) {
            String fileTypeDesc = "archive".equals(strategyType) ? "压缩包" : 
                                 ("pdf".equalsIgnoreCase(fileFormat) ? "PDF" : "Office文档");
            
            // 密码为空
            if (password == null || password.trim().isEmpty()) {
                logger.warn("🔒 {}需要密码 - FileId: {}, Format: {}", fileTypeDesc, fileId, fileFormat);
                Map<String, Object> response = buildPasswordRequiredResponse(fileId, filePath, fileFormat, fileSize,
                        request.getFileName(), validationResult, startTime);
                throw new PasswordValidationResponseException(response);
            }

            // 密码不正确校验
            if (validationResult.isPasswordCorrect() != null && 
                !validationResult.isPasswordCorrect()) {
                logger.warn("❌ {}密码错误 - FileId: {}, Format: {}", fileTypeDesc, fileId, fileFormat);
                Map<String, Object> response = buildPasswordIncorrectResponse(fileId, filePath, fileFormat, fileSize,
                        request.getFileName(), validationResult, startTime);
                throw new PasswordValidationResponseException(response);
            }

            // 密码正确（可以明确判断）
            if (validationResult.isPasswordCorrect() != null && validationResult.isPasswordCorrect()) {
                logger.info("✅ {}密码验证通过 - FileId: {}", fileTypeDesc, fileId);

                // 🔑 记录 clientId 的解锁状态
                if (clientId != null && !clientId.trim().isEmpty()) {
                    passwordUnlockService.markUnlocked(fileId, clientId, password);
                    logger.info("🔓 记录解锁状态 - FileId: {}, ClientId: {}", fileId, clientId);
                } else {
                    logger.warn("⚠️ 密码验证成功但未提供 clientId - FileId: {}", fileId);
                }
            } else {
                // passwordCorrect == null：预览侧无法判断密码是否正确（典型为旧式加密 DOC/PPT）
                logger.warn("⚠️ {}加密文件无法在预览侧验证密码，交由转换服务验证 - FileId: {}, Format: {}",
                        fileTypeDesc, fileId, fileFormat);

                // 为了后续转换能够获取密码，这里仍然记录解锁状态（密码正确性由转换服务最终判定）
                if (clientId != null && !clientId.trim().isEmpty()) {
                    passwordUnlockService.markUnlocked(fileId, clientId, password);
                    logger.info("🔓 记录解锁状态（密码正确性未知） - FileId: {}, ClientId: {}", fileId, clientId);
                } else {
                    logger.warn("⚠️ 无法验证密码且未提供 clientId - FileId: {}", fileId);
                }
            }
        }
        
        return validationResult;
    }
    
    /**
     * 🔑 密码获取策略：支持网络文件和服务器文件
     * 
     * 对于网络文件，fileId（网络URL生成）与localFileId（本地路径生成）不一致。
     * 密码存储在localFileId上，需要先从缓存获取localFilePath，再查询密码。
     * 
     * @param fileId 文件ID（网络URL或本地路径生成）
     * @param clientId 客户端ID
     * @param password 请求中的密码（可能为null）
     * @return 解析后的密码，如果请求已有密码则直接返回
     */
    private String resolvePasswordFromCache(String fileId, String clientId, String password) {
        // 如果请求中已有密码，直接返回
        if (password != null && !password.trim().isEmpty()) {
            return password;
        }
        
        // 1. 先尝试从缓存获取localFilePath，然后用localFileId查询密码
        PreviewCacheInfo cachedResult = cacheReadService.getCachedResult(fileId);
        if (cachedResult != null) {
            String localFilePath = cachedResult.getOriginalFilePath();
            if (localFilePath != null && !localFilePath.trim().isEmpty()) {
                String localFileId = fileUtils.generateFileIdFromFileUrl(localFilePath);
                // 只有fileId与localFileId不同时才需要用localFileId查询
                if (!localFileId.equals(fileId)) {
                    String cachedPassword = passwordUnlockService.getPassword(localFileId, clientId);
                    if (cachedPassword != null && !cachedPassword.trim().isEmpty()) {
                        logger.debug("🔑 从缓存恢复密码 - localFileId: {}, clientId: {}", localFileId, clientId);
                        return cachedPassword;
                    }
                }
            }
        }
        
        // 2. 回退：用当前fileId查询密码
        String directPassword = passwordUnlockService.getPassword(fileId, clientId);
        if (directPassword != null && !directPassword.trim().isEmpty()) {
            logger.debug("🔑 直接获取密码 - fileId: {}, clientId: {}", fileId, clientId);
            return directPassword;
        }
        
        return null;
    }

    /**
     * 内部异常类，用于在密码验证流程中传递需要立即返回的响应
     */
    private static class PasswordValidationResponseException extends RuntimeException {
        private final Map<String, Object> response;

        public PasswordValidationResponseException(Map<String, Object> response) {
            this.response = response;
        }

        public Map<String, Object> getResponse() {
            return response;
        }
    }

    /**
     * 处理缓存检查及密码验证逻辑
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @param password 密码
     * @param startTime 开始时间
     * @param requestBaseUrl 请求基础URL
     * @return 如果缓存命中且验证通过，返回响应Map；否则返回null继续后续处理
     */
    private Map<String, Object> handleCacheCheck(String fileId, String clientId, 
                                                  String password, long startTime, String requestBaseUrl) {
        PreviewCacheInfo cachedResult = cacheReadService.getCachedResult(fileId);
        if (cachedResult == null) {
            return null;
        }

        String cachedStatus = cachedResult.getStatus();
        
        // 🔑 关键逻辑：如果请求中包含密码，且缓存状态为 PASSWORD_REQUIRED、PASSWORD_INCORRECT 或 FAILED，则清除缓存重新处理
        if (password != null && !password.trim().isEmpty()) {
            if ("PASSWORD_REQUIRED".equals(cachedStatus) || 
                "PASSWORD_INCORRECT".equals(cachedStatus) ||
                "FAILED".equals(cachedStatus)) {
                logger.info("🔄 请求包含密码，清除缓存重新处理 - FileId: {}, Status: {}", fileId, cachedStatus);
                cacheWriteService.clearAllCache(fileId); // 🔑 清除所有缓存（直接预览 + 转换缓存）
                return null; // 返回null，继续后续处理流程
            }
        }
        
        // 如果缓存状态为 SUCCESS，执行加密文件的解锁验证
        if ("SUCCESS".equals(cachedStatus)) {
            // 🔐 安全检查：如果是加密压缩包，验证 clientId 是否已解锁
            if (isEncryptedArchive(cachedResult)) {
                if (clientId == null || clientId.trim().isEmpty()) {
                    logger.warn("⚠️ 加密文件缓存命中，但未提供 clientId - FileId: {}", fileId);
                    return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
                }
                // 🔑 关键修复：密码解锁使用 localFileId（基于本地文件路径生成），需同时检查
                String localFilePath = cachedResult.getOriginalFilePath();
                String localFileId = (localFilePath != null && !localFilePath.trim().isEmpty()) 
                        ? fileUtils.generateFileIdFromFileUrl(localFilePath) : fileId;
                boolean isUnlocked = passwordUnlockService.isUnlocked(fileId, clientId) 
                        || passwordUnlockService.isUnlocked(localFileId, clientId);
                if (!isUnlocked) {
                    logger.warn("🔒 加密文件缓存命中，但 clientId 未解锁 - FileId: {}, LocalFileId: {}, ClientId: {}", fileId, localFileId, clientId);
                    return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
                }
                logger.info("✅ 加密文件缓存命中，clientId 已解锁 - FileId: {}, LocalFileId: {}, ClientId: {}", fileId, localFileId, clientId);
            }
            logger.info("✅ 命中缓存 - FileId: {}", fileId);
            return buildResponseFromCache(cachedResult, startTime, requestBaseUrl);
        }
        
        // 🚀 缓存短路优化：PASSWORD_REQUIRED 状态直接返回，避免重复文件检测
        if ("PASSWORD_REQUIRED".equals(cachedStatus)) {
            // 如果请求没有密码，检查 clientId 是否已解锁
            if (password == null || password.trim().isEmpty()) {
                // 🔑 关键修复：检查 clientId 是否已解锁，如果已解锁则清缓存重试
                if (clientId != null && !clientId.trim().isEmpty()) {
                    // 检查是否已解锁（同时检查 fileId 和 localFileId）
                    String localFilePath = cachedResult.getOriginalFilePath();
                    String localFileId = (localFilePath != null && !localFilePath.trim().isEmpty()) 
                            ? fileUtils.generateFileIdFromFileUrl(localFilePath) : fileId;
                    boolean isUnlocked = passwordUnlockService.isUnlocked(fileId, clientId) 
                            || passwordUnlockService.isUnlocked(localFileId, clientId);
                    
                    if (isUnlocked) {
                        logger.info("🔓 PASSWORD_REQUIRED缓存命中，但 clientId 已解锁，清除缓存重新转换 - FileId: {}, LocalFileId: {}, ClientId: {}", 
                                fileId, localFileId, clientId);
                        // 从 Redis 获取密码
                        String unlockedPassword = passwordUnlockService.getPassword(fileId, clientId);
                        if (unlockedPassword == null || unlockedPassword.trim().isEmpty()) {
                            unlockedPassword = passwordUnlockService.getPassword(localFileId, clientId);
                        }
                        if (unlockedPassword != null && !unlockedPassword.trim().isEmpty()) {
                            logger.info("✅ 从 Redis 获取到密码，清除缓存并重新转换 - FileId: {}", fileId);
                            cacheWriteService.clearAllCache(fileId);
                            // 返回 null，继续后续处理（会带着密码重新转换）
                            return null;
                        }
                    }
                }
                
                logger.info("🚀 缓存短路：PASSWORD_REQUIRED状态且未提供密码 - FileId: {}", fileId);
                return buildPasswordRequiredResponseFromCache(cachedResult, startTime);
            }
            // 有密码则继续处理（在前面已经清除缓存）
        }
        
        // 🚀 缓存短路优化：PASSWORD_INCORRECT 状态直接返回
        if ("PASSWORD_INCORRECT".equals(cachedStatus)) {
            if (password == null || password.trim().isEmpty()) {
                logger.info("🚀 缓存短路：PASSWORD_INCORRECT状态且未提供密码 - FileId: {}", fileId);
                return buildPasswordIncorrectResponseFromCache(cachedResult, startTime);
            }
        }
        
        // 其他缓存状态，返回null继续后续处理
        return null;
    }

    /**
     * 内部异常类，用于在压缩包解压流程中传递需要立即返回的响应
     */
    private static class ArchiveExtractionResponseException extends RuntimeException {
        private final Map<String, Object> response;

        public ArchiveExtractionResponseException(Map<String, Object> response) {
            this.response = response;
        }

        public Map<String, Object> getResponse() {
            return response;
        }
    }

}