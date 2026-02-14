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
package com.basemetas.fileview.convert.service.mq.event;

import java.util.Map;
import java.util.HashMap;

/**
 * 文件事件模型
 * 
 * 用于事件驱动的文件转换处理，支持：
 * 1. 文件转换请求事件
 * 2. 转换完成事件
 * 3. 转换失败事件
 * 
 * @author 夫子
 * @version 1.0
 */
public class FileEvent {
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        /** 预览请求 - 用户发起预览请求 */
        PREVIEW_REQUESTED,
        /** 转换请求 - 用户发起转换请求 */
        CONVERT_REQUESTED,
    }
    
    // ========== 基础字段 ==========
    /** 文件ID */
    public String fileId;
    
    /** 原始文件路径 */
    public String filePath;

    /** 原始文件路径 */
    private String fileName;

    /** 源文件格式（扩展名） */
    public String sourceFormat;
    
    /** 文件类型 */
    public String fileType;
    
    /** 事件类型 */
    public EventType eventType;
    
    /** 事件来源服务 */
    public String sourceService;
    
    // ========== 转换相关字段 ==========
    /** 目标转换格式 */
    public String targetFormat;
    
    /** 转换目标路径 */
    public String targetPath;
    
    /** 转换目标文件名 */
    public String targetFileName;
    
    // ========== 性能优化字段 ==========
    /** 完整目标路径（已在Factory生成） */
    public String fullTargetPath;
    
    /** 文件密码 - 用于加密文件的转换 */
    private String passWord;
    
    /** 是否为加密文件 */
    private Boolean encrypted;
    
    
    /** 业务参数（透传） */
    public Map<String, Object> businessParams = new HashMap<>();
    
    /** 转换参数（透传） */
    public Map<String, Object> convertParams = new HashMap<>();
    
    // ========== 构造函数 ==========
    public FileEvent() {
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
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
    
    public EventType getEventType() {
        return eventType;
    }
    
    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
    
    public String getSourceService() {
        return sourceService;
    }
    
    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
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
    
    public String getSourceFormat() {
        return sourceFormat;
    }
    
    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }
    
    public Map<String, Object> getBusinessParams() {
        return businessParams;
    }
    
    public void setBusinessParams(Map<String, Object> businessParams) {
        this.businessParams = businessParams;
    }
    
    public Map<String, Object> getConvertParams() {
        return convertParams;
    }
    
    public void setConvertParams(Map<String, Object> convertParams) {
        this.convertParams = convertParams;
    }
    
    // ========== 便捷方法 ==========
    
    /**
     * 添加业务参数
     */
    public void addBusinessParam(String key, Object value) {
        this.businessParams.put(key, value);
    }
    
    /**
     * 添加转换参数
     */
    public void addConvertParam(String key, Object value) {
        this.convertParams.put(key, value);
    }
    
    @Override
    public String toString() {
        return "FileEvent{" +
                "fileId='" + fileId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileType='" + fileType + '\'' +
                ", eventType=" + eventType +
                ", sourceService='" + sourceService + '\'' +
                ", targetFormat='" + targetFormat + '\'' +
                ", targetPath='" + targetPath + '\'' +
                ", targetFileName='" + targetFileName + '\'' +
                ", fullTargetPath='" + fullTargetPath + '\'' +
                ", sourceFormat='" + sourceFormat + '\'' +
                ", passWord='" + (passWord != null ? "***" : null) + '\'' +
                ", businessParams=" + businessParams +
                ", convertParams=" + convertParams +
                '}';
    }
}