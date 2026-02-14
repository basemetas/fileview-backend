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
package com.basemetas.fileview.preview.service.response;

import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.config.FileTypeMapper;
import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.model.response.FilePreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的预览响应组装器，用于Controller和Service复用。
 */
@Component
public class PreviewResponseAssembler {
    private static final Logger logger = LoggerFactory.getLogger(PreviewResponseAssembler.class);

    public Map<String, Object> buildPollingResponse(String fileId, String status, String message, long startTime, int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        if (fileId != null) {
            response.put("fileId", fileId);
        }
        if (status != null) {
            response.put("status", status);
        }
        if (message != null && !message.isBlank()) {
            response.put("message", message);
        }
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        if ("CONVERTING".equals(status) || "PROCESSING".equals(status) || "DOWNLOADING".equals(status)) {
            response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
            response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        }
        return response;
    }

    public String computePreviewFileFormat(PreviewCacheInfo cacheInfo, FileTypeMapper fileTypeMapper) {
        if (cacheInfo == null) {
            return "";
        }
        String mode = cacheInfo.getMode();
        String originalFormat = cacheInfo.getOriginalFileFormat();
        if ("DIRECT".equals(mode)) {
            return originalFormat != null ? originalFormat : "";
        }
        if ("archive".equals(fileTypeMapper.getStrategyType(originalFormat))) {
            return "archive";
        }
        return cacheInfo.getPreviewFileFormat() != null ? cacheInfo.getPreviewFileFormat() : "";
    }

    public Map<String, Object> buildSuccessFromCache(
            String fileId,
            PreviewCacheInfo cacheInfo,
            Long remainingTtl,
            long startTime,
            String requestBaseUrl,
            int urlExpirationHours,
            FileTypeMapper fileTypeMapper) {
        
        Map<String, Object> response = buildPollingResponse(fileId, "SUCCESS", null, startTime, urlExpirationHours);

        String relativeUrl = cacheInfo.getPreviewUrl();
        String originalFormat = cacheInfo.getOriginalFileFormat();
        
        // 🔑 关键改动：动态拼接 baseUrl + 相对地址
        if ("epub".equalsIgnoreCase(originalFormat)) {
            // EPUB 格式特殊处理
            String epubBaseUrl = (requestBaseUrl != null && !requestBaseUrl.isEmpty()) ? requestBaseUrl : "";
            String epubResourceUrl = epubBaseUrl + "/preview/api/epub/" + fileId + "/";
            response.put("previewUrl", epubResourceUrl);
        } else {
            // 其他格式：拼接 baseUrl + 相对路径
            String fullUrl = (relativeUrl != null && !relativeUrl.isEmpty()) 
                ? (requestBaseUrl + relativeUrl) 
                : "";
            response.put("previewUrl", fullUrl);
        }

        response.put("mode", cacheInfo.getMode());
        response.put("cacheRemainingTtl", remainingTtl);
        response.put("originalFileFormat", cacheInfo.getOriginalFileFormat());
        response.put("previewFileFormat", computePreviewFileFormat(cacheInfo, fileTypeMapper));
        response.put("originalFileSize", cacheInfo.getOriginalFileSize());
        response.put("previewFileSize", cacheInfo.getPreviewFileSize());
        response.put("conversionRequired", cacheInfo.isConversionRequired());
        response.put("originalFileName", cacheInfo.getOriginalFileName());
        response.put("originalFilePath", cacheInfo.getOriginalFilePath());
        if (cacheInfo.getEncrypted() != null) {
            response.put("encrypted", cacheInfo.getEncrypted());
        }
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        if (cacheInfo.isMultiPage()) {
            response.put("multiPage", true);
            response.put("totalPages", cacheInfo.getTotalPages());
            // 🔑 多页URL拼接
            Map<Integer, String> relativePageUrls = cacheInfo.getPageUrls();
            Map<Integer, String> fullPageUrls = new HashMap<>();
            if (relativePageUrls != null) {
                for (Map.Entry<Integer, String> entry : relativePageUrls.entrySet()) {
                    String pageUrl = entry.getValue();
                    String fullPageUrl = (pageUrl != null && !pageUrl.isEmpty()) 
                        ? (requestBaseUrl + pageUrl) 
                        : "";
                    fullPageUrls.put(entry.getKey(), fullPageUrl);
                }
            }
            response.put("pageUrlMap", fullPageUrls);
        }
        
        return response;
    }

    public Map<String, Object> buildFailureFromCache(
            String fileId,
            String status,
            String errorMessage,
            ErrorCode errorCode,
            PreviewCacheInfo cacheInfo,
            String targetFormat,
            long startTime,
            int urlExpirationHours,
            FileTypeMapper fileTypeMapper) {
        Map<String, Object> response = buildPollingResponse(fileId, status, errorMessage, startTime, urlExpirationHours);
        response.put("error", errorMessage);
        response.put("errorCode", errorCode.getCode());
        if (cacheInfo != null) {
            response.put("originalFileName", cacheInfo.getOriginalFileName() != null ? cacheInfo.getOriginalFileName() : "");
            response.put("originalFilePath", cacheInfo.getOriginalFilePath() != null ? cacheInfo.getOriginalFilePath() : "");
            response.put("originalFileFormat", cacheInfo.getOriginalFileFormat() != null ? cacheInfo.getOriginalFileFormat() : "");
            response.put("originalFileSize", cacheInfo.getOriginalFileSize() != null ? cacheInfo.getOriginalFileSize() : 0);
            if ("FAILED".equals(status)) {
                String originalFormat = cacheInfo.getOriginalFileFormat();
                String previewFormat = ("archive".equals(fileTypeMapper.getStrategyType(originalFormat))) ? "archive"
                        : (targetFormat != null ? targetFormat : "");
                response.put("previewFileFormat", previewFormat);
            } else {
                response.put("previewFileFormat", "");
            }
        } else {
            response.put("originalFileName", "");
            response.put("originalFilePath", "");
            response.put("originalFileFormat", "");
            response.put("originalFileSize", 0);
            response.put("previewFileFormat", "");
        }
        response.put("previewUrl", "");
        response.put("previewFileSize", 0);
        response.put("mode", "");
        response.put("conversionRequired", false);
        response.put("cacheRemainingTtl", 0);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    public Map<String, Object> buildConvertingResponse(String fileId, String message, long startTime, int urlExpirationHours) {
        Map<String, Object> response = buildPollingResponse(fileId, "PROCESSING", message, startTime, urlExpirationHours);
        response.put("errorCode", 0);
        response.put("originalFileName", "");
        response.put("originalFilePath", "");
        response.put("originalFileFormat", "");
        response.put("originalFileSize", 0);
        response.put("previewUrl", "");
        response.put("previewFileSize", 0);
        response.put("previewFileFormat", "");
        response.put("mode", "");
        response.put("conversionRequired", false);
        response.put("cacheRemainingTtl", 0);
        return response;
    }

    public Map<String, Object> buildPasswordRequiredFromCache(PreviewCacheInfo cacheInfo, long startTime, int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", cacheInfo.getFileId());
        response.put("status", "PASSWORD_REQUIRED");
        response.put("originalFileName", cacheInfo.getOriginalFileName());
        response.put("originalFileFormat", cacheInfo.getOriginalFileFormat());
        response.put("originalFilePath", cacheInfo.getOriginalFilePath());
        response.put("originalFileSize", cacheInfo.getOriginalFileSize());
        response.put("encrypted", true);
        response.put("passwordRequired", true);
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_REQUIRED.getCode());
        response.put("errorMessage", "文件已加密，请输入密码");
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    public Map<String, Object> buildPasswordIncorrectFromCache(PreviewCacheInfo cacheInfo, long startTime, int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", cacheInfo.getFileId());
        response.put("status", "PASSWORD_INCORRECT");
        response.put("originalFileName", cacheInfo.getOriginalFileName());
        response.put("originalFileFormat", cacheInfo.getOriginalFileFormat());
        response.put("originalFilePath", cacheInfo.getOriginalFilePath());
        response.put("originalFileSize", cacheInfo.getOriginalFileSize());
        response.put("encrypted", true);
        response.put("passwordRequired", true);
        response.put("passwordCorrect", false);
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_INCORRECT.getCode());
        response.put("errorMessage", "压缩包密码错误");
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    public Map<String, Object> buildNotSupportedResponse(
            String fileId,
            String fileFormat,
            String fileName,
            String filePath,
            long fileSize,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "NOT_SUPPORTED");
        response.put("originalFileFormat", fileFormat != null ? fileFormat : "");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("originalFilePath", filePath != null ? filePath : "");
        response.put("originalFileSize", fileSize);
        response.put("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE.getCode());
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    public Map<String, Object> buildConverting(
            String fileId,
            String filePath,
            String fileName,
            String fileFormat,
            long fileSize,
            String targetFormat,
            long startTime,
            int urlExpirationHours,
            FileTypeMapper fileTypeMapper) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("previewUrl", "");
        response.put("mode", FilePreviewResponse.PreviewMode.CONVERT.name());
        response.put("status", FilePreviewResponse.PreviewStatus.CONVERTING.name());
        response.put("conversionProgress", 0);
        response.put("originalFilePath", filePath != null ? filePath : "");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("originalFileFormat", fileFormat != null ? fileFormat : "");
        response.put("originalFileSize", fileSize);
        response.put("previewFileFormat",
                ("archive".equals(fileTypeMapper.getStrategyType(fileFormat))) ? "archive" : targetFormat);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    public Map<String, Object> buildDirectPreviewResponse(
            String fileId,
            String relativePreviewUrl,
            String filePath,
            String fileName,
            String fileFormat,
            long fileSize,
            long startTime,
            int urlExpirationHours,
            String requestBaseUrl) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        
        // 🔑 关键改动：动态拼接 baseUrl + 相对地址
        if ("epub".equalsIgnoreCase(fileFormat)) {
            // EPUB文件特殊处理
            String epubBaseUrl = (requestBaseUrl != null && !requestBaseUrl.isEmpty()) ? requestBaseUrl : "";
            String epubResourceUrl = epubBaseUrl + "/preview/api/epub/" + fileId + "/";
            response.put("previewUrl", epubResourceUrl);
        } else {
            // 其他格式：拼接 baseUrl + 相对路径
            String fullUrl = (relativePreviewUrl != null && !relativePreviewUrl.isEmpty()) 
                ? (requestBaseUrl + relativePreviewUrl) 
                : "";
            response.put("previewUrl", fullUrl);
        }
        
        response.put("mode", FilePreviewResponse.PreviewMode.DIRECT.name());
        response.put("status", FilePreviewResponse.PreviewStatus.SUCCESS.name());
        response.put("originalFileFormat", fileFormat);
        response.put("originalFilePath", filePath != null ? filePath : "");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("previewFileFormat", fileFormat);
        response.put("originalFileSize", fileSize);
        response.put("previewFileSize", fileSize);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 构建 PASSWORD_REQUIRED 响应（不包含缓存逻辑）
     */
    public Map<String, Object> buildPasswordRequiredArchive(
            String fileId,
            String filePath,
            String fileFormat,
            long fileSize,
            String fileName,
            String archiveFormat,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "PASSWORD_REQUIRED");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("originalFilePath", filePath != null ? filePath : "");
        response.put("originalFileFormat", fileFormat != null ? fileFormat : "");
        response.put("originalFileSize", fileSize);
        response.put("archiveFormat", archiveFormat);
        response.put("encrypted", true);
        response.put("passwordRequired", true);
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_REQUIRED.getCode());
        response.put("errorMessage", errorMessage != null ? errorMessage : "压缩包已加密，需要密码");
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建 PASSWORD_INCORRECT 响应（不包含缓存逻辑）
     */
    public Map<String, Object> buildPasswordIncorrectArchive(
            String fileId,
            String filePath,
            String fileFormat,
            long fileSize,
            String fileName,
            String archiveFormat,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "PASSWORD_INCORRECT");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("originalFilePath", filePath != null ? filePath : "");
        response.put("originalFileFormat", fileFormat != null ? fileFormat : "");
        response.put("originalFileSize", fileSize);
        response.put("archiveFormat", archiveFormat);
        response.put("encrypted", true);
        response.put("passwordRequired", true);
        response.put("passwordCorrect", false);
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_INCORRECT.getCode());
        response.put("errorMessage", errorMessage != null ? errorMessage : "压缩包密码错误");
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建 DOWNLOADING 响应
     */
    public Map<String, Object> buildDownloadingResponse(
            String fileId,
            String fileName,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "DOWNLOADING");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建简单 NOT_SUPPORTED 响应（用于无效参数场景）
     */
    public Map<String, Object> buildInvalidParameterResponse(
            String fileId,
            String fileName,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "NOT_SUPPORTED");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("errorCode", ErrorCode.INVALID_PARAMETER.getCode());
        response.put("errorMessage", errorMessage);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建简单 NOT_SUPPORTED 响应（用于不支持格式场景）
     */
    public Map<String, Object> buildUnsupportedFormatResponse(
            String fileId,
            String fileName,
            String fileFormat,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "NOT_SUPPORTED");
        response.put("originalFileName", fileName != null ? fileName : "");
        response.put("originalFileFormat", fileFormat != null ? fileFormat : "");
        response.put("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE.getCode());
        response.put("errorMessage", errorMessage);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建 PASSWORD_REQUIRED 简单响应（用于压缩包提取场景）
     */
    public Map<String, Object> buildSimplePasswordRequiredResponse(
            String fileId,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "PASSWORD_REQUIRED");
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_REQUIRED.getCode());
        response.put("errorMessage", errorMessage);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建 PASSWORD_INCORRECT 简单响应（用于压缩包提取场景）
     */
    public Map<String, Object> buildSimplePasswordIncorrectResponse(
            String fileId,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "PASSWORD_INCORRECT");
        response.put("errorCode", ErrorCode.DOCUMENT_PASSWORD_INCORRECT.getCode());
        response.put("errorMessage", errorMessage);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建简单 NOT_SUPPORTED 响应（用于解压失败场景）
     */
    public Map<String, Object> buildSimpleNotSupportedResponse(
            String fileId,
            String errorMessage,
            long startTime,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId != null ? fileId : "");
        response.put("status", "NOT_SUPPORTED");
        response.put("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE.getCode());
        response.put("errorMessage", errorMessage);
        response.put("processingDuration", System.currentTimeMillis() - startTime);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建多页文件信息响应（成功场景）
     * 注意：动态拼接 baseUrl + 相对地址
     */
    public Map<String, Object> buildMultiPageResponse(
            String fileId,
            PreviewCacheInfo cacheInfo,
            Long remainingTtl,
            String requestPath,
            int urlExpirationHours,
            String requestBaseUrl) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("errorCode", 0);
        response.put("message", "多页文件预览成功");
        response.put("path", requestPath);
        response.put("status", FilePreviewResponse.PreviewMode.CONVERT.name());
        response.put("mode", "CONVERT");
        response.put("multiPage", true);
        response.put("totalPages", cacheInfo.getTotalPages());
        
        // 🔑 多页URL拼接
        Map<Integer, String> relativePageUrls = cacheInfo.getPageUrls();
        Map<Integer, String> fullPageUrls = new HashMap<>();
        if (relativePageUrls != null) {
            for (Map.Entry<Integer, String> entry : relativePageUrls.entrySet()) {
                String pageUrl = entry.getValue();
                String fullPageUrl = (pageUrl != null && !pageUrl.isEmpty()) 
                    ? (requestBaseUrl + pageUrl) 
                    : "";
                fullPageUrls.put(entry.getKey(), fullPageUrl);
            }
        }
        response.put("pageUrls", fullPageUrls);
        
        // 🔑 主预览URL拼接
        String relativePreviewUrl = cacheInfo.getPreviewUrl();
        String fullPreviewUrl = (relativePreviewUrl != null && !relativePreviewUrl.isEmpty()) 
            ? (requestBaseUrl + relativePreviewUrl) 
            : "";
        response.put("previewUrl", fullPreviewUrl);
        
        response.put("originalFileFormat", cacheInfo.getOriginalFileFormat());
        response.put("previewFileFormat", cacheInfo.getPreviewFileFormat());
        response.put("previewFileSize", cacheInfo.getPreviewFileSize());
        response.put("conversionRequired", true);
        response.put("fromCache", true);
        response.put("cacheRemainingTtl", remainingTtl);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }

    /**
     * 构建非多页文件错误响应
     */
    public Map<String, Object> buildNotMultiPageResponse(
            String fileId,
            String requestPath,
            int urlExpirationHours) {
        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("errorCode", ErrorCode.FILE_NOT_FOUND.getCode());
        response.put("message", "文件不是多页文件");
        response.put("multiPage", false);
        response.put("path", requestPath);
        response.put("urlExpirationSeconds", (long) urlExpirationHours * 3600);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(urlExpirationHours));
        return response;
    }
}
