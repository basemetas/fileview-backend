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
package com.basemetas.fileview.preview.model.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件预览请求模型
 * 
 * 支持三种预览场景：
 * 1. 服务器文件预览 - 文件已在存储服务上
 * 2. 本地文件上传预览 - 需要先上传到存储服务
 * 3. 网络文件下载预览 - 需要先下载到存储服务
 * 
 * @author 夫子
 */
public class FilePreviewRequest {
    
    /**
     * 预览类型枚举
     */
    public enum PreviewType {
        /** 服务器文件预览 - 文件已在存储服务上 */
        SERVER_FILE,
        /** 网络文件下载预览 - 需要先下载到存储服务 */
        NETWORK_DOWNLOAD
    }
    
    // ========== 基础字段 ==========
    /** 
     * 文件ID - 唯一标识
     * 对于SERVER_FILE类型，必须由调用方提供
     * 对于LOCAL_UPLOAD类型，可以是临时ID（如temp_xxx），系统会自动生成稳定ID
     * 对于NETWORK_DOWNLOAD类型，如果为空，系统会基于URL自动生成
     */
    private String fileId;
    
    /** 
     * 预览类型
     * 如果不指定，系统会根据提供的参数自动判断：
     * - 提供networkFileUrl → NETWORK_DOWNLOAD
     * - 提供localFilePath → LOCAL_UPLOAD
     * - 提供srcRelativePath → SERVER_FILE
     */
    private PreviewType previewType;
    
    /** 请求时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestTime;
    
    /** 请求来源服务 */
    private String sourceService;
    
    // ========== 服务器文件预览字段 ==========
    /** 源文件路径（用于SERVER_FILE类型） - 可以是目录或完整文件路径 */
    private String srcRelativePath;
    
    /** 源文件名 - 当srcRelativePath为目录时必须提供 */
    private String fileName;
    
    private String targetPath;
    /** 目标文件名 - 如果为空则从fileName提取（去掉扩展名） */
    private String targetFileName;
    
    /** 文件密码 - 用于加密文件的预览 */
    private String password;
    
    /** 客户端ID - 用于标识请求端，实现密码解锁状态管理 */
    private String clientId;
    
    // ========== 网络文件下载字段 ==========
    /** 网络文件URL（用于NETWORK_DOWNLOAD类型） */
    private String networkFileUrl;
    
    /** 下载目标路径（用于NETWORK_DOWNLOAD类型） */
    private String downloadTargetPath;
    
    /** 网络文件用户名（FTP/SFTP/S3等需要认证的协议） */
    private String networkUsername;
    
    /** 网络文件密码（FTP/SFTP等需要认证的协议） */
    private String networkPassword;
    
    /** S3 Access Key（S3协议使用） */
    private String s3AccessKey;
    
    /** S3 Secret Key（S3协议使用） */
    private String s3SecretKey;
    
    /** S3 Bucket名称（S3协议使用） */
    private String s3Bucket;
    
    /** S3 Region（S3协议使用） */
    private String s3Region;
    
    /** 下载超时时间（毫秒），默认60秒 */
    private int downloadTimeout = 60000;
    
    // ========== 预览配置字段 ==========
    /** 是否强制重新生成预览（忽略缓存） */
    private boolean forceRegenerate = false;
    
    /** 预览格式偏好（如果不指定则使用默认规则） */
    private String preferredFormat;
    
    /** 扩展参数 */
    private Map<String, Object> extendedParams;
    
    // ========== 构造函数 ==========
    public FilePreviewRequest() {
        this.requestTime = LocalDateTime.now();
    }
    
    /**
     * 创建服务器文件预览请求
     */
    public static FilePreviewRequest forServerFile(String fileId, String srcRelativePath) {
        FilePreviewRequest request = new FilePreviewRequest();
        request.setFileId(fileId);
        request.setPreviewType(PreviewType.SERVER_FILE);
        request.setSrcRelativePath(srcRelativePath);
        return request;
    }
    
    /**
     * 创建服务器文件预览请求（带文件名）
     */
    public static FilePreviewRequest forServerFile(String fileId, String srcRelativePath, String fileName) {
        FilePreviewRequest request = new FilePreviewRequest();
        request.setFileId(fileId);
        request.setPreviewType(PreviewType.SERVER_FILE);
        request.setSrcRelativePath(srcRelativePath);
        request.setFileName(fileName);
        return request;
    }
    /**
     * 创建网络文件下载预览请求
     */
    public static FilePreviewRequest forNetworkDownload(String fileId, String networkFileUrl, String downloadTargetPath) {
        FilePreviewRequest request = new FilePreviewRequest();
        request.setFileId(fileId);
        request.setPreviewType(PreviewType.NETWORK_DOWNLOAD);
        request.setNetworkFileUrl(networkFileUrl);
        request.setDownloadTargetPath(downloadTargetPath);
        return request;
    }  
    // ========== Getters and Setters ==========
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public PreviewType getPreviewType() {
        return previewType;
    }
    
    public void setPreviewType(PreviewType previewType) {
        this.previewType = previewType;
    }
    
    public LocalDateTime getRequestTime() {
        return requestTime;
    }
    
    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }
    
    public String getSourceService() {
        return sourceService;
    }
    
    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }
    
    public String getSrcRelativePath() {
        return srcRelativePath;
    }
    
    public void setSrcRelativePath(String srcRelativePath) {
        this.srcRelativePath = srcRelativePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getTargetFileName() {
        return targetFileName;
    }
    
    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

     public String getTargetPath() {
        return targetPath;
    }
    
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getNetworkFileUrl() {
        return networkFileUrl;
    }
    
    public void setNetworkFileUrl(String networkFileUrl) {
        this.networkFileUrl = networkFileUrl;
    }
    
    public String getDownloadTargetPath() {
        return downloadTargetPath;
    }
    
    public void setDownloadTargetPath(String downloadTargetPath) {
        this.downloadTargetPath = downloadTargetPath;
    }
    
    public String getNetworkUsername() {
        return networkUsername;
    }
    
    public void setNetworkUsername(String networkUsername) {
        this.networkUsername = networkUsername;
    }
    
    public String getNetworkPassword() {
        return networkPassword;
    }
    
    public void setNetworkPassword(String networkPassword) {
        this.networkPassword = networkPassword;
    }
    
    public String getS3AccessKey() {
        return s3AccessKey;
    }
    
    public void setS3AccessKey(String s3AccessKey) {
        this.s3AccessKey = s3AccessKey;
    }
    
    public String getS3SecretKey() {
        return s3SecretKey;
    }
    
    public void setS3SecretKey(String s3SecretKey) {
        this.s3SecretKey = s3SecretKey;
    }
    
    public String getS3Bucket() {
        return s3Bucket;
    }
    
    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket;
    }
    
    public String getS3Region() {
        return s3Region;
    }
    
    public void setS3Region(String s3Region) {
        this.s3Region = s3Region;
    }
    
    public int getDownloadTimeout() {
        return downloadTimeout;
    }
    
    public void setDownloadTimeout(int downloadTimeout) {
        this.downloadTimeout = downloadTimeout;
    }
    
    public boolean isForceRegenerate() {
        return forceRegenerate;
    }
    
    public void setForceRegenerate(boolean forceRegenerate) {
        this.forceRegenerate = forceRegenerate;
    }
    
    public String getPreferredFormat() {
        return preferredFormat;
    }
    
    public void setPreferredFormat(String preferredFormat) {
        this.preferredFormat = preferredFormat;
    }
    
    public Map<String, Object> getExtendedParams() {
        return extendedParams;
    }
    
    public void setExtendedParams(Map<String, Object> extendedParams) {
        this.extendedParams = extendedParams;
    }


    
    
    @Override
    public String toString() {
        return "FilePreviewRequest{" +
                "fileId='" + fileId + '\'' +
                ", previewType=" + previewType +
                ", requestTime=" + requestTime +
                ", sourceService='" + sourceService + '\'' +
                ", srcRelativePath='" + srcRelativePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileName='" + targetPath + '\'' +
                ", targetFileName='" + targetFileName + '\'' +
                ", passWord='" + (password != null ? "***" : null) + '\'' +
                ", clientId='" + (clientId != null ? clientId : null) + '\'' +
                ", networkFileUrl='" + networkFileUrl + '\'' +
                ", downloadTargetPath='" + downloadTargetPath + '\'' +
                ", forceRegenerate=" + forceRegenerate +
                ", preferredFormat='" + preferredFormat + '\'' +
                ", extendedParams=" + extendedParams +
                '}';
    }
}