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
package com.basemetas.fileview.convert.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 完整的转换结果信息对象
 * 包含转换过程的所有信息以及预览所需的完整数据
 * 
 * @author 夫子
 */
public class ConvertResultInfo {
    
    /**
     * 转换状态枚举
     */
    public enum ConversionStatus {
        /**
         * 转换中
         */
        CONVERTING("转换中"),
        /**
         * 转换成功
         */
        SUCCESS("转换成功"),
        /**
         * 转换失败
         */
        FAILED("转换失败"),
        /**
         * 转换取消
         */
        CANCELLED("转换取消");
        
        private final String description;
        
        ConversionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // 基础信息
    private String fileId;
    private String originalFileName;
    private String originalFilePath;
    private String originalFileFormat;
    private String targetFileName;
    private String convertedFilePath;
    private String targetFormat;
    
    // 文件大小信息
    private Long originalFileSize;
    private Long convertedFileSize;
    
    // 转换过程信息
    private ConversionStatus status;
    private long convertedAt;
    private long conversionDuration;
    private String errorMessage;
    private String errorCode; // 错误代码，用于标识特定失败原因
    
    // 预览相关信息
    private String previewUrl;
    private String previewFilePath;
    private String previewFileFormat;
    private Long previewFileSize;
    private boolean conversionRequired;
    private String previewMode; // DIRECT 或 CONVERTED
    private Boolean encrypted; // 是否为加密文件
      
    // 多页文件支持
    private boolean isMultiPage;        // 是否为多页文件
    private int totalPages;             // 总页数
    private String pagesDirectory;      // 多页文件存储目录
    
    // 缓存相关信息
    private long cachedAt;
    private long expiresAt;
    
    // 扩展元数据
    private Map<String, Object> metadata;
    
    // 构造函数
    public ConvertResultInfo() {
        this.cachedAt = System.currentTimeMillis();
        this.metadata = new HashMap<>();
    }
    
    public ConvertResultInfo(String fileId, String originalFilePath, String originalFileFormat) {
        this();
        this.fileId = fileId;
        this.originalFilePath = originalFilePath;
        this.originalFileFormat = originalFileFormat;
        this.status = ConversionStatus.CONVERTING;
    }
    
    // Getter and Setter methods
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    
    public String getOriginalFileName() {
        return originalFileName;
    }
    
    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
    public String getOriginalFilePath() {
        return originalFilePath;
    }
    
    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }
    
    public String getConvertedFilePath() {
        return convertedFilePath;
    }
    
    public void setConvertedFilePath(String convertedFilePath) {
        this.convertedFilePath = convertedFilePath;
    }
    
    public String getOriginalFileFormat() {
        return originalFileFormat;
    }
    
    public void setOriginalFileFormat(String originalFileFormat) {
        this.originalFileFormat = originalFileFormat;
    }
    
    public String getTargetFormat() {
        return targetFormat;
    }
    
    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getTargetFileName() {
        return targetFileName;
    }
    
    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }
    
    public Long getOriginalFileSize() {
        return originalFileSize;
    }
    
    public void setOriginalFileSize(Long originalFileSize) {
        this.originalFileSize = originalFileSize;
    }
    
    public Long getConvertedFileSize() {
        return convertedFileSize;
    }
    
    public void setConvertedFileSize(Long convertedFileSize) {
        this.convertedFileSize = convertedFileSize;
    }
    
    public ConversionStatus getStatus() {
        return status;
    }
    
    public void setStatus(ConversionStatus status) {
        this.status = status;
    }
    
    public long getConvertedAt() {
        return convertedAt;
    }
    
    public long getConversionDuration() {
        return conversionDuration;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getPreviewUrl() {
        return previewUrl;
    }
    
    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }
    
    public String getPreviewFilePath() {
        return previewFilePath;
    }
    
    public void setPreviewFilePath(String previewFilePath) {
        this.previewFilePath = previewFilePath;
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
    
    public boolean isConversionRequired() {
        return conversionRequired;
    }
    
    public void setConversionRequired(boolean conversionRequired) {
        this.conversionRequired = conversionRequired;
    }
    
    public String getPreviewMode() {
        return previewMode;
    }
    
    public void setPreviewMode(String previewMode) {
        this.previewMode = previewMode;
    }
    
    public Boolean getEncrypted() {
        return encrypted;
    }
    
    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }
    
    public boolean isMultiPage() {
        return isMultiPage;
    }
    
    public void setMultiPage(boolean multiPage) {
        isMultiPage = multiPage;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    public String getPagesDirectory() {
        return pagesDirectory;
    }
    
    public void setPagesDirectory(String pagesDirectory) {
        this.pagesDirectory = pagesDirectory;
    }
    
    public long getCachedAt() {
        return cachedAt;
    }
    
    public void setCachedAt(long cachedAt) {
        this.cachedAt = cachedAt;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    // 便利方法
    
    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }
    
    /**
     * 标记转换成功
     */
    public void markConversionSuccess(String convertedFilePath, long duration) {
        this.status = ConversionStatus.SUCCESS;
        this.convertedFilePath = convertedFilePath;
        this.conversionDuration = duration;
        this.convertedAt = System.currentTimeMillis();
        this.errorMessage = null;
        
        // 设置预览相关信息
        this.previewFilePath = convertedFilePath;
        this.previewFileFormat = this.targetFormat;
        this.previewMode = "CONVERT";
        this.conversionRequired = true;
    }
    
    /**
     * 标记转换失败
     */
    public void markConversionFailed(String errorMessage, long duration) {
        this.status = ConversionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.conversionDuration = duration;
        this.convertedAt = System.currentTimeMillis();
    }
    
    @Override
    public String toString() {
        return "ConvertResultInfo{" +
                "fileId='" + fileId + '\'' +
                ", originalFileFormat='" + originalFileFormat + '\'' +
                ", targetFormat='" + targetFormat + '\'' +
                ", status=" + status +
                ", previewUrl='" + previewUrl + '\'' +
                ", previewMode='" + previewMode + '\'' +
                ", conversionRequired=" + conversionRequired +
                ", conversionDuration=" + conversionDuration +
                ", cachedAt=" + cachedAt +
                '}';
    }
}