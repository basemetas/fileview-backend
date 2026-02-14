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
 * 下载任务消息模型
 * 用于在消息队列中传输下载任务信息
 */
public class DownloadTaskMessage {
    private String taskId;
    private String fileId;
    private String networkFileUrl;
    private String networkUsername;
    private String networkPassword;
    private String downloadTargetPath;
    private int downloadTimeout;
    private String passWord;  // 🔑 压缩包密码（用于加密压缩包）
    private String clientId;  // 🔑 客户端标识（用于密码解锁状态查询）
    private String requestBaseUrl;  // 🔑 请求的 baseUrl（用于生成预览地址）
    private String fileName;  // 🔑 前端传入的文件名（可选）
    
    // 构造函数
    public DownloadTaskMessage() {
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
        return "DownloadTaskMessage{" +
                "taskId='" + taskId + '\'' +
                ", fileId='" + fileId + '\'' +
                ", networkFileUrl='" + networkFileUrl + '\'' +
                ", downloadTargetPath='" + downloadTargetPath + '\'' +
                ", downloadTimeout=" + downloadTimeout +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}