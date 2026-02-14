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
package com.basemetas.fileview.preview.model.download;


/**
 * 下载任务模型
 */
public class DownloadTask {
    private String taskId;           // 任务ID
    private String fileId;           // 文件ID
    private String networkFileUrl;   // 网络文件URL
    private String networkUsername;  // 用户名（可选）
    private String networkPassword;  // 密码（可选）
    private String downloadTargetPath; // 下载目标路径
    private int downloadTimeout;    // 下载超时时间
    private DownloadTaskStatus status; // 任务状态
    private String localFilePath;   // 下载后的本地文件路径
    private String errorMessage;    // 错误信息
    private long fileSize;          // 文件大小
    private long createdTime;       // 创建时间
    private long finishedTime;      // 完成时间
    private double progress;        // 下载进度
    private String passWord;        // 🔑 压缩包密码（用于加密压缩包）
    private String clientId;  // 🔑 客户端标识（用于密码解锁状态查询）
    private String requestBaseUrl;  // 🔑 请求的 baseUrl（用于生成预览地址）
    private String fileName;        // 原始文件名（来自前端，可选）
    
    // 构造函数
    public DownloadTask() {
        this.createdTime = System.currentTimeMillis();
        this.status = DownloadTaskStatus.PENDING;
        this.progress = 0.0;
    }
    
    // Getter和Setter方法
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getNetworkFileUrl() {
        return networkFileUrl;
    }
    
    public void setNetworkFileUrl(String networkFileUrl) {
        this.networkFileUrl = networkFileUrl;
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
    
    public String getDownloadTargetPath() {
        return downloadTargetPath;
    }
    
    public void setDownloadTargetPath(String downloadTargetPath) {
        this.downloadTargetPath = downloadTargetPath;
    }
    
    public int getDownloadTimeout() {
        return downloadTimeout;
    }
    
    public void setDownloadTimeout(int downloadTimeout) {
        this.downloadTimeout = downloadTimeout;
    }
    
    public DownloadTaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(DownloadTaskStatus status) {
        this.status = status;
    }
    
    public String getLocalFilePath() {
        return localFilePath;
    }
    
    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }
    
    public long getFinishedTime() {
        return finishedTime;
    }
    
    public void setFinishedTime(long finishedTime) {
        this.finishedTime = finishedTime;
    }
    
    public double getProgress() {
        return progress;
    }
    
    public void setProgress(double progress) {
        this.progress = progress;
    }
    
    public String getPassWord() {
        return passWord;
    }
    
    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getRequestBaseUrl() {
        return requestBaseUrl;
    }
    
    public void setRequestBaseUrl(String requestBaseUrl) {
        this.requestBaseUrl = requestBaseUrl;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    @Override
    public String toString() {
        return "DownloadTask{" +
                "taskId='" + taskId + '\'' +
                ", fileId='" + fileId + '\'' +
                ", status=" + status +
                ", progress=" + progress +
                ", createdTime=" + createdTime +
                '}';
    }
}