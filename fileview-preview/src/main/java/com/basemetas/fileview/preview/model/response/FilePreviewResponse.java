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
package com.basemetas.fileview.preview.model.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.basemetas.fileview.preview.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件预览统一响应模型
 * 
 * 功能：
 * - 兼容单页和多页文件预览
 * - 提供统一的错误码和消息格式
 * - 包含预览URL、文件信息、缓存状态等完整信息
 * 
 * 响应格式：
 * {
 *   "errorCode": 0,                    // 成功为0，失败为错误码
 *   "message": "预览成功",              // 响应消息
 *   "timestamp": "2025-10-15T15:00:00", // 响应时间
 *   "path": "/api/preview/request",     // 请求路径
 *   "fileId": "xxx",
 *   "status": "SUCCESS",
 *   "previewUrl": "...",
 *   "multiPage": true,                   // 多页文件支持
 *   "totalPages": 5,
 *   "pageUrls": [...],
 *   ...
 * }
 * 
 * @author 夫子
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilePreviewResponse {
    
    // ========== 统一响应字段 ==========
    /** 错误码（成功为0，失败为具体错误码） */
    private int errorCode;
    
    /** 请求路径 */
    private String path;
    
    /** 响应时间戳（用于统一响应格式，与responseTime保持一致） */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 预览状态枚举
     */
    public enum PreviewStatus {
        /** 成功 - 可直接预览 */
        SUCCESS,
        /** 转换中 - 需要等待转换完成 */
        CONVERTING,
        /** 失败 - 预览生成失败 */
        FAILED,
        /** 不支持 - 文件类型不支持预览 */
        NOT_SUPPORTED
    }
    
    /**
     * 预览模式枚举
     */
    public enum PreviewMode {
        /** 直接预览 - 无需转换，直接返回原文件URL */
        DIRECT,
        /** 转换预览 - 需要转换后预览 */
        CONVERT
    }
    
    // ========== 基础响应字段 ==========
    /** 文件ID */
    private String fileId;
    
    /** 预览状态 */
    private PreviewStatus status;
    
    /** 预览模式 */
    private PreviewMode mode;
    
    /** 响应消息 */
    private String message;
    
    /** 响应时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime responseTime;
    
    // ========== 预览URL信息 ==========
    /** 预览URL */
    private String previewUrl;
    
    /** 预览URL有效期（秒） */
    private Long urlExpirationSeconds;
    
    /** 预览URL过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime urlExpirationTime;
    
    // ========== 文件信息 ==========
    /** 原始文件名 */
    private String originalFileName;
    
    /** 原始文件类型 */
    private String originalFileFormat;
    
    /** 原始文件大小（字节） */
    private Long originalFileSize;
    
    /** 预览文件类型（转换后的类型） */
    private String previewFileFormat;
    
    /** 预览文件大小（字节） */
    private Long previewFileSize;
    
    // ========== 缓存信息 ==========
    /** 是否来自缓存 */
    private boolean fromCache;
    
    /** 缓存命中时间 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cacheHitTime;
    
    /** 缓存剩余有效期（秒） */
    private Long cacheRemainingTtl;
    
    // ========== 处理信息 ==========
    /** 处理耗时（毫秒） */
    private Long processingDuration;
    
    /** 是否需要转换 */
    private boolean conversionRequired;
    
    /** 转换状态（如果需要转换） */
    private String conversionStatus;
    
    /** 转换进度（百分比，0-100） */
    private Integer conversionProgress;
    
    // ========== 多页文件支持 ==========
    /** 是否为多页文件 */
    private Boolean multiPage;
    
    /** 总页数（多页文件） */
    private Integer totalPages;
    
    /** 多页文件的页面URL映射（页码 -> URL） */
    private Map<Integer, String> pageUrlMap;
    
    // ========== 扩展信息 ==========
    /** 扩展属性 */
    private Map<String, Object> extendedProperties;
    
    // ========== 构造函数 ==========
    public FilePreviewResponse() {
        this.responseTime = LocalDateTime.now();
        this.timestamp = this.responseTime; // 保持timestamp与responseTime一致
        this.errorCode = 0; // 默认成功
    }
    
    /**
     * 创建成功响应
     */
    public static FilePreviewResponse success(String fileId, String previewUrl, PreviewMode mode) {
        FilePreviewResponse response = new FilePreviewResponse();
        response.setErrorCode(0);
        response.setFileId(fileId);
        response.setStatus(PreviewStatus.SUCCESS);
        response.setMode(mode);
        response.setPreviewUrl(previewUrl);
        response.setMessage("预览生成成功");
        return response;
    }
    
    /**
     * 创建从缓存获取的成功响应
     */
    public static FilePreviewResponse successFromCache(String fileId, String previewUrl, PreviewMode mode, Long cacheRemainingTtl) {
        FilePreviewResponse response = success(fileId, previewUrl, mode);
        response.setFromCache(true);
        response.setCacheHitTime(LocalDateTime.now());
        response.setCacheRemainingTtl(cacheRemainingTtl);
        response.setMessage("预览生成成功（来自缓存）");
        return response;
    }
    
    /**
     * 创建转换中响应
     */
    public static FilePreviewResponse converting(String fileId, Integer progress) {
        FilePreviewResponse response = new FilePreviewResponse();
        response.setFileId(fileId);
        response.setStatus(PreviewStatus.CONVERTING);
        response.setMode(PreviewMode.CONVERT);
        response.setConversionRequired(true);
        response.setConversionStatus("converting");
        response.setConversionProgress(progress != null ? progress : 0);
        response.setMessage("文件正在转换中，请稍候...");
        return response;
    }
    
    /**
     * 创建失败响应
     */
    public static FilePreviewResponse failed(String fileId, String errorMessage) {
        FilePreviewResponse response = new FilePreviewResponse();
        response.setErrorCode(ErrorCode.PREVIEW_FAILED.getCode()); // 预览失败错误码
        response.setFileId(fileId);
        response.setStatus(PreviewStatus.FAILED);
        response.setMessage("预览生成失败: " + errorMessage);
        return response;
    }
    
    /**
     * 创建不支持响应
     */
    public static FilePreviewResponse notSupported(String fileId, String fileFormat) {
        FilePreviewResponse response = new FilePreviewResponse();
        response.setErrorCode(ErrorCode.UNSUPPORTED_FILE_TYPE.getCode()); // 不支持的文件类型
        response.setFileId(fileId);
        response.setStatus(PreviewStatus.NOT_SUPPORTED);
        response.setOriginalFileFormat(fileFormat);
        response.setMessage("文件类型 '" + fileFormat + "' 不支持预览");
        return response;
    }
    
    // ========== 链式调用方法 ==========
    
    /**
     * 设置请求路径
     */
    public FilePreviewResponse withPath(String path) {
        this.path = path;
        return this;
    }
    
    /**
     * 设置错误码
     */
    public FilePreviewResponse withErrorCode(int errorCode) {
        this.errorCode = errorCode;
        return this;
    }
    
    /**
     * 设置消息
     */
    public FilePreviewResponse withMessage(String message) {
        this.message = message;
        return this;
    }
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return errorCode == 0 && status == PreviewStatus.SUCCESS;
    }
    
    /**
     * 判断是否失败
     */
    public boolean isError() {
        return !isSuccess();
    }
    
    // ========== Getters and Setters ==========
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public PreviewStatus getStatus() {
        return status;
    }
    
    public void setStatus(PreviewStatus status) {
        this.status = status;
    }
    
    public PreviewMode getMode() {
        return mode;
    }
    
    public void setMode(PreviewMode mode) {
        this.mode = mode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getResponseTime() {
        return responseTime;
    }
    
    public void setResponseTime(LocalDateTime responseTime) {
        this.responseTime = responseTime;
        this.timestamp = responseTime; // 保持同步
    }
    
    public String getPreviewUrl() {
        return previewUrl;
    }
    
    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }
    
    public Long getUrlExpirationSeconds() {
        return urlExpirationSeconds;
    }
    
    public void setUrlExpirationSeconds(Long urlExpirationSeconds) {
        this.urlExpirationSeconds = urlExpirationSeconds;
    }
    
    public LocalDateTime getUrlExpirationTime() {
        return urlExpirationTime;
    }
    
    public void setUrlExpirationTime(LocalDateTime urlExpirationTime) {
        this.urlExpirationTime = urlExpirationTime;
    }
    
    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
    
    public String getOriginalFileFormat() {
        return originalFileFormat;
    }
    
    public void setOriginalFileFormat(String originalFileFormat) {
        this.originalFileFormat = originalFileFormat;
    }
    
    public Long getOriginalFileSize() {
        return originalFileSize;
    }
    
    public void setOriginalFileSize(Long originalFileSize) {
        this.originalFileSize = originalFileSize;
    }
    
    public String getPreviewFileFormat() {
        return previewFileFormat;
    }
    
    public void setPreviewFileFormat(String previewFileFormat) {
        this.previewFileFormat = previewFileFormat;
    }
    
    public Long getPreviewFileSize() {
        return previewFileSize;
    }
    
    public void setPreviewFileSize(Long previewFileSize) {
        this.previewFileSize = previewFileSize;
    }
    
    public boolean isFromCache() {
        return fromCache;
    }
    
    public void setFromCache(boolean fromCache) {
        this.fromCache = fromCache;
    }
    
    public LocalDateTime getCacheHitTime() {
        return cacheHitTime;
    }
    
    public void setCacheHitTime(LocalDateTime cacheHitTime) {
        this.cacheHitTime = cacheHitTime;
    }
    
    public Long getCacheRemainingTtl() {
        return cacheRemainingTtl;
    }
    
    public void setCacheRemainingTtl(Long cacheRemainingTtl) {
        this.cacheRemainingTtl = cacheRemainingTtl;
    }
    
    public Long getProcessingDuration() {
        return processingDuration;
    }
    
    public void setProcessingDuration(Long processingDuration) {
        this.processingDuration = processingDuration;
    }
    
    public boolean isConversionRequired() {
        return conversionRequired;
    }
    
    public void setConversionRequired(boolean conversionRequired) {
        this.conversionRequired = conversionRequired;
    }
    
    public String getConversionStatus() {
        return conversionStatus;
    }
    
    public void setConversionStatus(String conversionStatus) {
        this.conversionStatus = conversionStatus;
    }
    
    public Integer getConversionProgress() {
        return conversionProgress;
    }
    
    public void setConversionProgress(Integer conversionProgress) {
        this.conversionProgress = conversionProgress;
    }
    
    public Map<String, Object> getExtendedProperties() {
        return extendedProperties;
    }
    
    public void setExtendedProperties(Map<String, Object> extendedProperties) {
        this.extendedProperties = extendedProperties;
    }
    
    public Boolean getMultiPage() {
        return multiPage;
    }
    
    public void setMultiPage(Boolean multiPage) {
        this.multiPage = multiPage;
    }
    
    public Integer getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
    
    public Map<Integer, String> getPageUrlMap() {
        return pageUrlMap;
    }
    
    public void setPageUrlMap(Map<Integer, String> pageUrlMap) {
        this.pageUrlMap = pageUrlMap;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        this.responseTime = timestamp; // 保持同步
    }
    
    @Override
    public String toString() {
        return "FilePreviewResponse{" +
                "errorCode=" + errorCode +
                ", path='" + path + '\'' +
                ", fileId='" + fileId + '\'' +
                ", status=" + status +
                ", mode=" + mode +
                ", message='" + message + '\'' +
                ", responseTime=" + responseTime +
                ", previewUrl='" + previewUrl + '\'' +
                ", urlExpirationSeconds=" + urlExpirationSeconds +
                ", urlExpirationTime=" + urlExpirationTime +
                ", originalFileName='" + originalFileName + '\'' +
                ", originalFileType='" + originalFileFormat + '\'' +
                ", originalFileSize=" + originalFileSize +
                ", previewFileType='" + previewFileFormat + '\'' +
                ", previewFileSize=" + previewFileSize +
                ", fromCache=" + fromCache +
                ", cacheHitTime=" + cacheHitTime +
                ", cacheRemainingTtl=" + cacheRemainingTtl +
                ", processingDuration=" + processingDuration +
                ", conversionRequired=" + conversionRequired +
                ", conversionStatus='" + conversionStatus + '\'' +
                ", conversionProgress=" + conversionProgress +
                ", multiPage=" + multiPage +
                ", totalPages=" + totalPages +
                ", pageUrlMap=" + pageUrlMap +
                ", extendedProperties=" + extendedProperties +
                '}';
    }
}