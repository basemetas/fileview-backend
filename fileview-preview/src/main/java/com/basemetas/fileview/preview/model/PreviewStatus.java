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
package com.basemetas.fileview.preview.model;

import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.config.FileTypeMapper;
import com.basemetas.fileview.preview.service.FilePreviewService;
import com.basemetas.fileview.preview.service.cache.CacheReadService;
import com.basemetas.fileview.preview.service.password.PasswordUnlockService;
import com.basemetas.fileview.preview.service.response.PreviewResponseAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 预览状态枚举 + 策略模式
 * 
 * 每个状态封装自己的处理逻辑，减少主方法的分支复杂度
 * 
 * @author 夫子
 */
public enum PreviewStatus {
    
    /**
     * 转换成功
     * - 终态：是
     * - 需密码检查：是（加密文件）
     */
    SUCCESS("SUCCESS", true) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            
            PreviewCacheInfo cacheInfo = ctx.getCacheInfo();
            String fileId = ctx.getFileId();
            
            // 🔍 检查预览URL是否已就绪（非EPUB需要依赖预览URL）
            String originalFormat = cacheInfo.getOriginalFileFormat();
            String previewUrl = cacheInfo.getPreviewUrl();
            if (originalFormat == null || !"epub".equalsIgnoreCase(originalFormat)) {
                if (previewUrl == null || previewUrl.trim().isEmpty()) {
                    // 转换服务已写入 SUCCESS，但预览URL尚未写入缓存（预览服务事件消费可能尚未完成），继续轮询等待
                    logger.debug("⏳ SUCCESS状态但预览URL尚未就绪，继续轮询 - FileId: {}, OriginalFormat: {}", fileId, originalFormat);
                    return null;
                }
            }
            
            // 🔑 关键：检查加密文件的解锁状态
            if (previewService.isEncryptedArchive(cacheInfo)) {
                if (!isClientUnlocked(ctx, passwordService, fileUtils)) {
                    logger.info("🔒 加密文件SUCCESS状态，但 clientId 未解锁 - FileId: {}", fileId);
                    // 降级到 PASSWORD_REQUIRED
                    return PASSWORD_REQUIRED.handle(ctx, assembler, passwordService, 
                        previewService, fileTypeMapper, fileUtils, cacheReadService, urlExpirationHours);
                }
                logger.info("✅ 加密文件SUCCESS状态，clientId 已解锁 - FileId: {}", fileId);
            }
            
            // 构建成功响应
            Long remainingTtl;
            String targetFormat = ctx.getTargetFormat();
            if (targetFormat != null && !targetFormat.trim().isEmpty()) {
                remainingTtl = cacheReadService.getCacheTTL(fileId, targetFormat);
            } else {
                remainingTtl = cacheReadService.getCacheTTL(fileId);
            }
            
            return assembler.buildSuccessFromCache(
                fileId,
                cacheInfo,
                remainingTtl,
                ctx.getStartTime(),
                ctx.getRequestBaseUrl(),
                urlExpirationHours,
                fileTypeMapper
            );
        }
        
        private boolean isClientUnlocked(PreviewStatusContext ctx, 
                                        PasswordUnlockService passwordService,
                                        com.basemetas.fileview.preview.utils.FileUtils fileUtils) {
            String clientId = ctx.getClientId();
            if (clientId == null || clientId.trim().isEmpty()) {
                return false;
            }
            
            PreviewCacheInfo cacheInfo = ctx.getCacheInfo();
            String fileId = ctx.getFileId();
            String localFilePath = cacheInfo.getOriginalFilePath();
            String localFileId = (localFilePath != null && !localFilePath.trim().isEmpty()) 
                    ? fileUtils.generateFileIdFromFileUrl(localFilePath) 
                    : fileId;
            
            return passwordService.isUnlocked(fileId, clientId) 
                    || passwordService.isUnlocked(localFileId, clientId);
        }
    },
    
    /**
     * 转换失败
     * - 终态：是
     * - 错误码映射：需要
     */
    FAILED("FAILED", true) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            
            PreviewCacheInfo cacheInfo = ctx.getCacheInfo();
            
            // 🔑 关键：错误码友好提示
            String cachedErrorCode = cacheInfo.getErrorCode();
            String errorMessage = "文件转换失败，请检查文件格式或重新上传";
            ErrorCode responseErrorCode = ErrorCode.CONVERSION_FAILED;
            
            // 特殊错误码处理
            if (String.valueOf(ErrorCode.UNSUPPORTED_CONVERSION.getCode()).equals(cachedErrorCode)) {
                String originalFormat = cacheInfo.getOriginalFileFormat();
                if (originalFormat != null && !originalFormat.isEmpty()) {
                    errorMessage = String.format(
                        "不支持带密码的旧格式 %s",
                        originalFormat.toUpperCase()
                    );
                } else {
                    errorMessage = "不支持带密码的旧格式文档";
                }
                responseErrorCode = ErrorCode.UNSUPPORTED_CONVERSION;
                logger.info("💡 转换引擎不支持 - FileId: {}, Format: {}", 
                    ctx.getFileId(), originalFormat);
            }
            
            logger.warn("❌ 转换失败 - FileId: {}, ErrorCode: {}", 
                ctx.getFileId(), cachedErrorCode);
            
            return assembler.buildFailureFromCache(
                ctx.getFileId(),
                "FAILED",
                errorMessage,
                responseErrorCode,
                cacheInfo,
                ctx.getTargetFormat(),
                ctx.getStartTime(),
                urlExpirationHours,
                fileTypeMapper
            );
        }
    },
    
    /**
     * 不支持的文件类型
     * - 终态：是
     */
    NOT_SUPPORTED("NOT_SUPPORTED", true) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            
            PreviewCacheInfo cacheInfo = ctx.getCacheInfo();
            String errorMsg = "不支持的文件类型: " 
                    + (cacheInfo.getOriginalFileFormat() != null 
                        ? cacheInfo.getOriginalFileFormat() 
                        : "unknown");
            
            logger.warn("❌ 不支持的文件类型 - FileId: {}", ctx.getFileId());
            
            return assembler.buildFailureFromCache(
                ctx.getFileId(),
                "NOT_SUPPORTED",
                errorMsg,
                ErrorCode.UNSUPPORTED_FILE_TYPE,
                cacheInfo,
                null,
                ctx.getStartTime(),
                urlExpirationHours,
                fileTypeMapper
            );
        }
    },
    
    /**
     * 需要密码
     * - 终态：是
     */
    PASSWORD_REQUIRED("PASSWORD_REQUIRED", true) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            
            logger.warn("🔑 文件需要密码 - FileId: {}", ctx.getFileId());
            
            return assembler.buildFailureFromCache(
                ctx.getFileId(),
                "PASSWORD_REQUIRED",
                "文件已加密，需要密码",
                ErrorCode.DOCUMENT_PASSWORD_REQUIRED,
                ctx.getCacheInfo(),
                null,
                ctx.getStartTime(),
                urlExpirationHours,
                fileTypeMapper
            );
        }
    },
    
    /**
     * 密码错误
     * - 终态：动态判断（已解锁为非终态）
     */
    PASSWORD_INCORRECT("PASSWORD_INCORRECT", false) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            
            // 🔑 关键：检查 clientId 是否已解锁
            if (isClientUnlocked(ctx, passwordService, fileUtils)) {
                logger.info("✅ PASSWORD_INCORRECT缓存，但 clientId 已解锁，继续等待转换 - FileId: {}", 
                    ctx.getFileId());
                // 已解锁，继续轮询
                return null;
            }
            
            // 未解锁，返回密码错误
            logger.warn("❌ 文件密码错误 - FileId: {}", ctx.getFileId());
            
            return assembler.buildFailureFromCache(
                ctx.getFileId(),
                "PASSWORD_INCORRECT",
                "文件密码错误，请重试",
                ErrorCode.DOCUMENT_PASSWORD_INCORRECT,
                ctx.getCacheInfo(),
                null,
                ctx.getStartTime(),
                urlExpirationHours,
                fileTypeMapper
            );
        }
        
        @Override
        public boolean isTerminal(PreviewStatusContext ctx, 
                                 PasswordUnlockService passwordService,
                                 com.basemetas.fileview.preview.utils.FileUtils fileUtils) {
            // 动态判断：已解锁为非终态，未解锁为终态
            return !isClientUnlocked(ctx, passwordService, fileUtils);
        }
        
        private boolean isClientUnlocked(PreviewStatusContext ctx, 
                                        PasswordUnlockService passwordService,
                                        com.basemetas.fileview.preview.utils.FileUtils fileUtils) {
            String clientId = ctx.getClientId();
            if (clientId == null || clientId.trim().isEmpty()) {
                return false;
            }
            
            PreviewCacheInfo cacheInfo = ctx.getCacheInfo();
            String fileId = ctx.getFileId();
            String localFilePath = cacheInfo.getOriginalFilePath();
            String localFileId = (localFilePath != null && !localFilePath.trim().isEmpty()) 
                    ? fileUtils.generateFileIdFromFileUrl(localFilePath) 
                    : fileId;
            
            return passwordService.isUnlocked(fileId, clientId) 
                    || passwordService.isUnlocked(localFileId, clientId);
        }
    },
    
    /**
     * 转换中（默认状态）
     * - 终态：否
     */
    CONVERTING("CONVERTING", false) {
        @Override
        public Map<String, Object> handle(
                PreviewStatusContext ctx,
                PreviewResponseAssembler assembler,
                PasswordUnlockService passwordService,
                FilePreviewService previewService,
                FileTypeMapper fileTypeMapper,
                com.basemetas.fileview.preview.utils.FileUtils fileUtils,
                CacheReadService cacheReadService,
                int urlExpirationHours) {
            // 继续轮询
            return null;
        }
    };
    
    // ==================== 枚举基础属性 ====================
    
    private static final Logger logger = LoggerFactory.getLogger(PreviewStatus.class);
    
    private final String statusName;
    private final boolean terminalByDefault;
    
    PreviewStatus(String statusName, boolean terminalByDefault) {
        this.statusName = statusName;
        this.terminalByDefault = terminalByDefault;
    }
    
    // ==================== 抽象方法 ====================
    
    /**
     * 处理状态并返回响应
     * 
     * @param ctx 状态上下文
     * @param assembler 响应组装器
     * @param passwordService 密码服务
     * @param previewService 预览服务
     * @param fileTypeMapper 文件类型映射器
     * @param fileUtils 文件工具
     * @param cacheReadService 缓存读取服务
     * @param urlExpirationHours URL过期时间（小时）
     * @return 终态返回响应，非终态返回 null（继续轮询）
     */
    public abstract Map<String, Object> handle(
        PreviewStatusContext ctx,
        PreviewResponseAssembler assembler,
        PasswordUnlockService passwordService,
        FilePreviewService previewService,
        FileTypeMapper fileTypeMapper,
        com.basemetas.fileview.preview.utils.FileUtils fileUtils,
        CacheReadService cacheReadService,
        int urlExpirationHours
    );
    
    /**
     * 是否为终态
     * 
     * @param ctx 上下文
     * @param passwordService 密码服务（用于动态判断）
     * @param fileUtils 文件工具
     * @return true=终态，false=继续轮询
     */
    public boolean isTerminal(PreviewStatusContext ctx, 
                             PasswordUnlockService passwordService,
                             com.basemetas.fileview.preview.utils.FileUtils fileUtils) {
        return terminalByDefault;
    }
    
    /**
     * 从字符串获取状态枚举
     * 
     * @param status 状态字符串
     * @return 对应的枚举常量，未找到则返回 CONVERTING
     */
    public static PreviewStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            return CONVERTING;
        }
        
        for (PreviewStatus ps : values()) {
            if (ps.statusName.equals(status)) {
                return ps;
            }
        }
        
        return CONVERTING;
    }
    
    public String getStatusName() {
        return statusName;
    }
}
