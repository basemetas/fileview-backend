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
package com.basemetas.fileview.preview.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一错误响应模型
 * 
 * 所有错误响应都应使用此模型，确保错误信息格式统一
 * 
 * @author 夫子
 * @version 1.0.0
 */
public class ErrorResponse {
    
    /** 成功标志（始终为false） */
    private boolean success = false;
    
    /** 错误码 */
    private int errorCode;
    
    /** 错误消息 */
    private String errorMessage;
    
    /** 详细错误描述 */
    private String details;
    
    /** 时间戳 */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /** 请求路径 */
    private String path;
    
    /** 文件ID（如果适用） */
    private String fileId;
    
    /** HTTP状态码 */
    private int httpStatus;
    
    /** 是否可重试 */
    private boolean retryable;
    
    /** 扩展信息 */
    private Map<String, Object> extra;
    
    // ========== 构造函数 ==========
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(ErrorCode errorCode) {
        this();
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
        this.httpStatus = errorCode.getHttpStatus();
        this.retryable = errorCode.isRetryable();
    }
    
    public ErrorResponse(ErrorCode errorCode, String details) {
        this(errorCode);
        this.details = details;
    }
    
    public ErrorResponse(ErrorCode errorCode, String details, String fileId) {
        this(errorCode, details);
        this.fileId = fileId;
    }
    
    // ========== 静态工厂方法 ==========
    
    /**
     * 创建基础错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode);
    }
    
    /**
     * 创建带详情的错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode, String details) {
        return new ErrorResponse(errorCode, details);
    }
    
    /**
     * 创建带文件ID的错误响应
     */
    public static ErrorResponse of(ErrorCode errorCode, String details, String fileId) {
        return new ErrorResponse(errorCode, details, fileId);
    }
    
    /**
     * 从异常创建错误响应
     */
    public static ErrorResponse fromException(Exception e) {
        if (e instanceof FileViewException) {
            FileViewException fve = (FileViewException) e;
            return new ErrorResponse(fve.getErrorCode(), fve.getMessage());
        }
        return new ErrorResponse(ErrorCode.UNKNOWN_ERROR, e.getMessage());
    }

    /**
     * 从LibreOffice退出码创建错误响应
     */
    public static ErrorResponse fromLibreOfficeExitCode(int exitCode, String output) {
        ErrorCode errorCode = ErrorCode.fromLibreOfficeExitCode(exitCode);
        if (errorCode == null) {
            return null; // 成功无需错误响应
        }
        return new ErrorResponse(errorCode, "LibreOffice退出码: " + exitCode + ", 输出: " + output);
    }
    
    // ========== 链式调用方法 ==========
    
    public ErrorResponse withDetails(String details) {
        this.details = details;
        return this;
    }
    
    public ErrorResponse withFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }
    
    public ErrorResponse withPath(String path) {
        this.path = path;
        return this;
    }
    
    public ErrorResponse withExtra(String key, Object value) {
        if (this.extra == null) {
            this.extra = new HashMap<>();
        }
        this.extra.put(key, value);
        return this;
    }
    
    // ========== Getters and Setters ==========
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public void setRetryable(boolean retryable) {
        this.retryable = retryable;
    }
    
    public Map<String, Object> getExtra() {
        return extra;
    }
    
    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
    
    @Override
    public String toString() {
        return "ErrorResponse{" +
                "errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                ", details='" + details + '\'' +
                ", timestamp=" + timestamp +
                ", fileId='" + fileId + '\'' +
                ", httpStatus=" + httpStatus +
                ", retryable=" + retryable +
                '}';
    }
}