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

/**
 * 文件预览服务统一异常类
 * 
 * 所有业务异常都应继承此类，使用统一的错误码体系
 * 
 * @author 夫子
 * @version 1.0.0
 */
public class FileViewException extends RuntimeException {
    
    /** 错误码 */
    private final ErrorCode errorCode;
    
    /** 详细错误信息 */
    private final String details;
    
    /** 文件ID（如果适用） */
    private String fileId;
    
    // ========== 构造函数 ==========
    
    public FileViewException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }
    
    public FileViewException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public FileViewException(ErrorCode errorCode, String details, Throwable cause) {
        super(errorCode.getMessage() + ": " + details, cause);
        this.errorCode = errorCode;
        this.details = details;
    }
    
    public FileViewException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = cause.getMessage();
    }
    
    // ========== 静态工厂方法 ==========
    
    /**
     * 创建基础异常
     */
    public static FileViewException of(ErrorCode errorCode) {
        return new FileViewException(errorCode);
    }
    
    /**
     * 创建带详情的异常
     */
    public static FileViewException of(ErrorCode errorCode, String details) {
        return new FileViewException(errorCode, details);
    }
    
    /**
     * 创建带原因的异常
     */
    public static FileViewException of(ErrorCode errorCode, Throwable cause) {
        return new FileViewException(errorCode, cause);
    }
    
    /**
     * 创建带详情和原因的异常
     */
    public static FileViewException of(ErrorCode errorCode, String details, Throwable cause) {
        return new FileViewException(errorCode, details, cause);
    }
    
    // ========== 链式调用方法 ==========
    
    public FileViewException withFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }
    
    // ========== Getters ==========
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public String getDetails() {
        return details;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }
    
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }
    
    /**
     * 转换为错误响应
     */
    public ErrorResponse toErrorResponse() {
        return ErrorResponse.of(errorCode, details, fileId);
    }
}