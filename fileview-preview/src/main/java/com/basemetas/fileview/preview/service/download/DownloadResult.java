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
package com.basemetas.fileview.preview.service.download;

/**
 * 下载结果封装类
 */
public class DownloadResult {
    private String status;
    private String filePath;
    private String taskId;
    private String message;
    
    // 私有构造函数
    private DownloadResult(String status, String filePath, String taskId, String message) {
        this.status = status;
        this.filePath = filePath;
        this.taskId = taskId;
        this.message = message;
    }
    
    /**
     * 创建成功结果
     * 
     * @param filePath 文件路径
     * @return 下载结果
     */
    public static DownloadResult success(String filePath) {
        return new DownloadResult("SUCCESS", filePath, null, "下载成功");
    }
    
    /**
     * 创建待处理结果
     * 
     * @param taskId 任务ID
     * @return 下载结果
     */
    public static DownloadResult pending(String taskId) {
        return new DownloadResult("PENDING", null, taskId, "下载任务正在进行中");
    }
    
    /**
     * 创建失败结果
     * 
     * @param message 错误信息
     * @return 下载结果
     */
    public static DownloadResult failure(String message) {
        return new DownloadResult("FAILURE", null, null, message);
    }
    
    // Getter方法
    public String getStatus() {
        return status;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return "DownloadResult{" +
                "status='" + status + '\'' +
                ", filePath='" + filePath + '\'' +
                ", taskId='" + taskId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}