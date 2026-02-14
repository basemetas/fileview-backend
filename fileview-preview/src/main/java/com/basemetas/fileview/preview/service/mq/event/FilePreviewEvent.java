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
package com.basemetas.fileview.preview.service.mq.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件预览事件模型
 * 
 * 用于事件驱动的文件预览处理，支持：
 * 1. 预览请求事件
 * 2. 转换触发事件
 * 3. 预览完成事件
 * 
 * @author 夫子
 */
public class FilePreviewEvent {

    /**
     * 预览事件类型枚举
     */
    public enum PreviewEventType {
        /** 预览请求 - 用户发起预览请求 */
        PREVIEW_REQUESTED,
        /** 转换请求 - 用户发起转换请求 */
        CONVERT_REQUESTED,
    }

    // ========== 基础字段 ==========
    /** 文件ID */
    private String fileId;

    /** 原始文件路径 */
    private String filePath;

    /** 原始文件路径 */
    private String fileName;

    /** 文件类型 */
    private String sourceFormat;

    /** 预览事件类型 */
    private PreviewEventType eventType;

    /** 事件时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /** 事件来源服务 */
    private String sourceService;

    // ========== 预览相关字段 ==========
    /** 预览URL（如果已生成） */
    private String previewUrl;

    /** 预览文件路径（如果已生成） */
    private String previewFilePath;

    /** 预览文件类型 */
    private String previewSourceFormat;

    // ========== 转换相关字段 ==========
    /** 是否需要转换 */
    private boolean conversionRequired;

    /** 目标转换格式 */
    private String targetFormat;

    /** 转换目标路径 */
    private String targetPath;

    /** 完整目标路径 */
    private String fullTargetPath;

    /** 转换目标文件名 */
    private String targetFileName;

    /** 文件密码 - 用于加密文件的转换 */
    private String passWord;
    
    /** 是否为加密文件 */
    private Boolean encrypted;

    // ========== 状态字段 ==========
    /** 处理状态 */
    private String status;

    /** 错误消息（如果有） */
    private String errorMessage;

    /** 处理进度（0-100） */
    private Integer progress;

    // ========== 扩展字段 ==========
    /** 扩展参数 */
    private Map<String, Object> extendedParams;
    
    /** 业务参数（透传给转换服务）*/
    private Map<String, Object> businessParams;

    // ========== 构造函数 ==========
    public FilePreviewEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public FilePreviewEvent(String fileId, String filePath, String sourceFormat,
            PreviewEventType eventType, String sourceService) {
        this();
        this.fileId = fileId;
        this.filePath = filePath;
        this.sourceFormat = sourceFormat;
        this.eventType = eventType;
        this.sourceService = sourceService;
    }
    // ========== Getters and Setters ==========

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public PreviewEventType getEventType() {
        return eventType;
    }

    public void setEventType(PreviewEventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
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

    public String getPreviewSourceFormat() {
        return previewSourceFormat;
    }

    public void setPreviewSourceFormat(String previewSourceFormat) {
        this.previewSourceFormat = previewSourceFormat;
    }

    public boolean isConversionRequired() {
        return conversionRequired;
    }

    public void setConversionRequired(boolean conversionRequired) {
        this.conversionRequired = conversionRequired;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

    public String getFullTargetPath() {
        return fullTargetPath;
    }

    public void setFullTargetPath(String fullTargetPath) {
        this.fullTargetPath = fullTargetPath;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }
    
    public Boolean getEncrypted() {
        return encrypted;
    }
    
    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Map<String, Object> getExtendedParams() {
        return extendedParams;
    }

    public void setExtendedParams(Map<String, Object> extendedParams) {
        this.extendedParams = extendedParams;
    }
    
    public Map<String, Object> getBusinessParams() {
        return businessParams;
    }
    
    public void setBusinessParams(Map<String, Object> businessParams) {
        this.businessParams = businessParams;
    }

    @Override
    public String toString() {
        return "FilePreviewEvent{" +
                "fileId='" + fileId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", sourceFormat='" + sourceFormat + '\'' +
                ", eventType=" + eventType +
                ", timestamp=" + timestamp +
                ", sourceService='" + sourceService + '\'' +
                ", previewUrl='" + previewUrl + '\'' +
                ", previewFilePath='" + previewFilePath + '\'' +
                ", previewSourceFormat='" + previewSourceFormat + '\'' +
                ", conversionRequired=" + conversionRequired +
                ", targetFormat='" + targetFormat + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", targetFileName='" + targetFileName + '\'' +
                ", passWord='" + (passWord != null ? "***" : null) + '\'' +
                ", status='" + status + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", progress=" + progress +
                ", extendedParams=" + extendedParams +
                '}';
    }
}