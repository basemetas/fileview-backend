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
package com.basemetas.fileview.preview.service.cache;

import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.model.response.FilePreviewResponse;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的缓存数据组装器，用于构建 PreviewCacheInfo 和转换缓存 Map。
 */
@Component
public class PreviewCacheAssembler {
    /**
     * 构建直接预览的缓存信息（PreviewCacheInfo）
     */
    public PreviewCacheInfo buildNotSupportedDirectCacheInfo(
            String fileId,
            String fileName,
            String filePath,
            String fileFormat,
            long fileSize) {
        PreviewCacheInfo cacheInfo = new PreviewCacheInfo();
        cacheInfo.setFileId(fileId);
        cacheInfo.setOriginalFileFormat(fileFormat);
        cacheInfo.setStatus("NOT_SUPPORTED");
        cacheInfo.setCachedAt(System.currentTimeMillis());
        cacheInfo.setOriginalFileName(fileName);
        cacheInfo.setOriginalFilePath(filePath);
        cacheInfo.setOriginalFileSize(fileSize);
        cacheInfo.setMode("");
        return cacheInfo;
    }

    /**
     * 构建直接预览的缓存信息（PreviewCacheInfo）
     */
    public PreviewCacheInfo buildDirectCacheInfo(
            String fileId,
            String previewUrl,
            String filePath,
            String fileName,
            String fileFormat,
            long fileSize) {
        PreviewCacheInfo cacheInfo = new PreviewCacheInfo();
        cacheInfo.setFileId(fileId);
        cacheInfo.setPreviewUrl(previewUrl);
        cacheInfo.setOriginalFilePath(filePath);
        cacheInfo.setOriginalFileName(fileName);
        cacheInfo.setOriginalFileFormat(fileFormat);
        cacheInfo.setPreviewFileFormat(fileFormat);
        cacheInfo.setOriginalFileSize(fileSize);
        cacheInfo.setPreviewFileSize(fileSize);
        cacheInfo.setConversionRequired(false);
        cacheInfo.setMode(FilePreviewResponse.PreviewMode.DIRECT.name());
        cacheInfo.setStatus(FilePreviewResponse.PreviewStatus.SUCCESS.name());
        cacheInfo.setCachedAt(System.currentTimeMillis());
        return cacheInfo;
    }


    /**
     * 构建 FAILED 转换缓存 Map
     */
    public Map<String, Object> buildFailedConvertCache(
            String fileId,
            String fileName,
            String filePath,
            String fileFormat,
            long fileSize,
            String targetFormat,
            String errorMessage) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("status", "FAILED");
        cacheData.put("originalFileName", fileName);
        cacheData.put("originalFilePath", filePath);
        cacheData.put("originalFileFormat", fileFormat);
        cacheData.put("originalFileSize", fileSize);
        if (targetFormat != null && !targetFormat.isEmpty()) {
            cacheData.put("previewFileFormat", targetFormat);
        }
        cacheData.put("error", errorMessage != null ? errorMessage : "文件转换失败");
        cacheData.put("mode", "CONVERT");
        cacheData.put("cachedAt", System.currentTimeMillis());
        cacheData.put("expiresAt", System.currentTimeMillis() + 86400000L); // 24小时
        return cacheData;
    }

    /**
     * 构建 NOT_SUPPORTED 转换缓存 Map
     */
    public Map<String, Object> buildNotSupportedConvertCache(
            String fileId,
            String fileName,
            String filePath,
            String fileFormat,
            long fileSize,
            String errorMessage) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("status", "NOT_SUPPORTED");
        cacheData.put("originalFileName", fileName);
        cacheData.put("originalFilePath", filePath);
        cacheData.put("originalFileFormat", fileFormat);
        cacheData.put("originalFileSize", fileSize);
        cacheData.put("error", errorMessage != null ? errorMessage : "不支持的文件类型");
        cacheData.put("mode", "CONVERT");
        cacheData.put("cachedAt", System.currentTimeMillis());
        cacheData.put("expiresAt", System.currentTimeMillis() + 86400000L); // 24小时
        return cacheData;
    }

    /**
     * 构建 PASSWORD_REQUIRED 转换缓存 Map
     */
    public Map<String, Object> buildPasswordRequiredConvertCache(
            String fileId,
            String fileName,
            String filePath,
            String fileFormat,
            long fileSize) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("status", "PASSWORD_REQUIRED");
        cacheData.put("originalFileName", fileName);
        cacheData.put("originalFilePath", filePath);
        cacheData.put("originalFileFormat", fileFormat);
        cacheData.put("originalFileSize", fileSize);
        cacheData.put("encrypted", true);
        cacheData.put("passwordRequired", true);
        cacheData.put("mode", "CONVERT");
        cacheData.put("cachedAt", System.currentTimeMillis());
        cacheData.put("expiresAt", System.currentTimeMillis() + 86400000L); // 24小时
        return cacheData;
    }

    /**
     * 构建 PASSWORD_INCORRECT 转换缓存 Map
     */
    public Map<String, Object> buildPasswordIncorrectConvertCache(
            String fileId,
            String fileName,
            String filePath,
            String fileFormat,
            long fileSize) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("status", "PASSWORD_INCORRECT");
        cacheData.put("originalFileName", fileName);
        cacheData.put("originalFilePath", filePath);
        cacheData.put("originalFileFormat", fileFormat);
        cacheData.put("originalFileSize", fileSize);
        cacheData.put("encrypted", true);
        cacheData.put("passwordRequired", true);
        cacheData.put("passwordCorrect", false);
        cacheData.put("mode", "CONVERT");
        cacheData.put("cachedAt", System.currentTimeMillis());
        cacheData.put("expiresAt", System.currentTimeMillis() + 86400000L); // 24小时
        return cacheData;
    }

    /**
     * 构建 SUCCESS 转换缓存 Map（从转换结果派生）
     */
    public Map<String, Object> buildSuccessConvertCache(
            Map<String, Object> conversionResult,
            String previewUrl,
            Long fileSize,
            String targetFormat) {
        Map<String, Object> cacheData = new HashMap<>();
        if (conversionResult != null) {
            cacheData.putAll(conversionResult);
        }
        cacheData.put("previewUrl", previewUrl);
        cacheData.put("previewFileSize", fileSize);
        cacheData.put("mode", "CONVERT");
        cacheData.put("status", "SUCCESS");
        cacheData.put("cachedAt", System.currentTimeMillis());
        String originalFileFormat = conversionResult != null ? (String) conversionResult.get("originalFileType") : null;
        if (originalFileFormat != null && !originalFileFormat.trim().isEmpty()) {
            cacheData.put("previewFileFormat", originalFileFormat);
        } else {
            cacheData.put("previewFileFormat", targetFormat);
        }
        Object baseUrlObj = conversionResult != null ? conversionResult.get("requestBaseUrl") : null;
        if (baseUrlObj != null) {
            cacheData.put("requestBaseUrl", baseUrlObj.toString());
        }
        Object encryptedFromResult = conversionResult != null ? conversionResult.get("encrypted") : null;
        if (encryptedFromResult != null) {
            cacheData.put("encrypted", encryptedFromResult);
        }
        return cacheData;
    }

    /**
     * 构建多页 SUCCESS 转换缓存 Map
     */
    public Map<String, Object> buildMultiPageConvertCache(
            Map<String, Object> conversionResult,
            Map<Integer, String> pageUrls,
            int totalPages,
            String pagesDirectory,
            Long totalFileSize,
            String targetFormat) {
        Map<String, Object> cacheData = buildSuccessConvertCache(conversionResult, pageUrls != null ? pageUrls.getOrDefault(1, null) : null, totalFileSize, targetFormat);
        cacheData.put("isMultiPage", true);
        cacheData.put("totalPages", totalPages);
        cacheData.put("pagesDirectory", pagesDirectory);
        cacheData.put("pageUrls", pageUrls);
        return cacheData;
    }

    /**
     * 构建简化 FAILED 转换缓存 Map
     */
    public Map<String, Object> buildFailedSimpleConvertCache(
            String fileId,
            String targetFormat,
            String error,
            Object errorCode,
            String originalFileFormat) {
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("fileId", fileId);
        cacheData.put("targetFormat", targetFormat);
        if (originalFileFormat != null && !originalFileFormat.trim().isEmpty()) {
            cacheData.put("originalFileFormat", originalFileFormat);
        }
        cacheData.put("mode", "CONVERT");
        cacheData.put("status", "FAILED");
        cacheData.put("error", error != null ? error : "Unknown error");
        cacheData.put("errorCode", errorCode != null ? errorCode.toString() : "UNKNOWN");
        cacheData.put("cachedAt", System.currentTimeMillis());
        return cacheData;
    }

    /**
     * 构建直接预览缓存的 Map（与转换服务保持一致的结构）
     */
    public Map<String, Object> buildDirectPreviewCache(PreviewCacheInfo previewInfo) {
        Map<String, Object> cacheData = new HashMap<>();
        if (previewInfo == null) {
            return cacheData;
        }
        cacheData.put("fileId", previewInfo.getFileId());
        cacheData.put("previewUrl", previewInfo.getPreviewUrl());
        cacheData.put("originalFilePath", previewInfo.getOriginalFilePath());
        cacheData.put("originalFileName", previewInfo.getOriginalFileName());
        cacheData.put("originalFileFormat", previewInfo.getOriginalFileFormat());
        cacheData.put("previewFileFormat", previewInfo.getPreviewFileFormat());
        cacheData.put("originalFileSize", previewInfo.getOriginalFileSize());
        cacheData.put("previewFileSize", previewInfo.getPreviewFileSize());
        cacheData.put("conversionRequired", previewInfo.isConversionRequired());
        cacheData.put("previewMode", previewInfo.getMode());
        cacheData.put("status", previewInfo.getStatus() != null ? previewInfo.getStatus() : "SUCCESS");
        cacheData.put("cachedAt", System.currentTimeMillis());
        if (previewInfo.isMultiPage()) {
            cacheData.put("isMultiPage", true);
            cacheData.put("totalPages", previewInfo.getTotalPages());
            cacheData.put("pagesDirectory", previewInfo.getPagesDirectory());
            cacheData.put("pageUrls", previewInfo.getPageUrls());
        }
        return cacheData;
    }

    /**
     * 解析转换缓存 Map -> PreviewCacheInfo
     */
    public PreviewCacheInfo parseConvertCacheToPreviewCacheInfo(Object cacheDataObj) {
        try {
            if (cacheDataObj instanceof java.util.Map) {
                java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) cacheDataObj;
                com.basemetas.fileview.preview.model.PreviewCacheInfo previewInfo = new com.basemetas.fileview.preview.model.PreviewCacheInfo();
                Object fileId = dataMap.get("fileId");
                previewInfo.setFileId(fileId != null ? fileId.toString() : null);
                Object previewUrl = dataMap.get("previewUrl");
                previewInfo.setPreviewUrl(previewUrl != null ? previewUrl.toString() : null);
                Object originalFilePath = dataMap.get("originalFilePath");
                previewInfo.setOriginalFilePath(originalFilePath != null ? originalFilePath.toString() : null);
                Object originalFileName = dataMap.get("originalFileName");
                previewInfo.setOriginalFileName(originalFileName != null ? originalFileName.toString() : null);
                Object originalFileFormat = dataMap.get("originalFileFormat");
                previewInfo.setOriginalFileFormat(originalFileFormat != null ? originalFileFormat.toString() : null);
                Object previewFileFormat = dataMap.get("previewFileFormat");
                previewInfo.setPreviewFileFormat(previewFileFormat != null ? previewFileFormat.toString() : null);
                Object originalFileSize = dataMap.get("originalFileSize");
                if (originalFileSize instanceof Number) {
                    previewInfo.setOriginalFileSize(((Number) originalFileSize).longValue());
                }
                Object previewFileSize = dataMap.get("previewFileSize");
                if (previewFileSize instanceof Number) {
                    previewInfo.setPreviewFileSize(((Number) previewFileSize).longValue());
                }
                Object conversionRequired = dataMap.get("conversionRequired");
                if (conversionRequired instanceof Boolean) {
                    previewInfo.setConversionRequired((Boolean) conversionRequired);
                }
                Object mode = dataMap.get("previewMode");
                previewInfo.setMode(mode != null ? mode.toString() : null);
                Object cachedAt = dataMap.get("cachedAt");
                if (cachedAt instanceof Number) {
                    previewInfo.setCachedAt(((Number) cachedAt).longValue());
                }
                Object expiresAt = dataMap.get("expiresAt");
                if (expiresAt instanceof Number) {
                    previewInfo.setExpiresAt(((Number) expiresAt).longValue());
                }
                Object encryptedObj = dataMap.get("encrypted");
                if (encryptedObj instanceof Boolean) {
                    previewInfo.setEncrypted((Boolean) encryptedObj);
                } else if (encryptedObj != null) {
                    previewInfo.setEncrypted(Boolean.parseBoolean(encryptedObj.toString()));
                }
                Object isMultiPage = dataMap.get("isMultiPage");
                if (isMultiPage instanceof Boolean) {
                    previewInfo.setMultiPage((Boolean) isMultiPage);
                }
                Object totalPages = dataMap.get("totalPages");
                if (totalPages instanceof Number) {
                    previewInfo.setTotalPages(((Number) totalPages).intValue());
                }
                Object pagesDirectory = dataMap.get("pagesDirectory");
                previewInfo.setPagesDirectory(pagesDirectory != null ? pagesDirectory.toString() : null);
                Object pageUrlsObj = dataMap.get("pageUrls");
                if (pageUrlsObj instanceof java.util.Map) {
                    java.util.Map<Integer, String> pageUrls = new java.util.HashMap<>();
                    java.util.Map<?, ?> rawMap = (java.util.Map<?, ?>) pageUrlsObj;
                    for (java.util.Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        try {
                            Integer pageNum;
                            if (entry.getKey() instanceof Integer) {
                                pageNum = (Integer) entry.getKey();
                            } else {
                                pageNum = Integer.parseInt(entry.getKey().toString());
                            }
                            pageUrls.put(pageNum, entry.getValue().toString());
                        } catch (Exception ignore) {}
                    }
                    previewInfo.setPageUrls(pageUrls);
                }
                Object statusObj = dataMap.get("status");
                if (statusObj != null) {
                    previewInfo.setStatus(statusObj.toString());
                } else {
                    previewInfo.setStatus("UNKNOWN");
                }
                // 🔑 关键修复：解析 errorCode 字段
                Object errorCodeObj = dataMap.get("errorCode");
                if (errorCodeObj != null) {
                    previewInfo.setErrorCode(errorCodeObj.toString());
                }
                return previewInfo;
            }
        } catch (Exception ignore) {}
        return null;
    }

}
