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
package com.basemetas.fileview.convert.common.exception;
/**
 * 统一错误码枚举
 * 
 * 错误码格式: 
 * 范围: 10000 - 99999
 * 
 * @author 夫子
 * @version 1.0.0
 * @see ERROR_CODE_SYSTEM.md
 */
public enum ErrorCode {
    
    // ========== 通用错误 (10000-19999) ==========
    UNKNOWN_ERROR(10000, "未知错误", 500),
    SYSTEM_ERROR(10001, "系统内部错误", 500),
    OPERATION_FAILED(10002, "操作失败", 500),
    SERVICE_UNAVAILABLE(10003, "服务不可用", 503),
    SERVICE_TIMEOUT(10004, "服务超时", 504),
    CONFIGURATION_ERROR(10005, "配置错误", 500),
    INITIALIZATION_FAILED(10006, "初始化失败", 500),
    
    // ========== 参数验证错误 (20000-29999) ==========
    INVALID_PARAMETER(20000, "无效参数", 400),
    MISSING_REQUIRED_PARAMETER(20001, "缺少必需参数", 400),
    INVALID_FILE_ID(20002, "无效的文件ID", 400),
    INVALID_FILE_TYPE(20003, "无效的文件类型", 400),
    INVALID_TARGET_FORMAT(20004, "无效的目标格式", 400),
    UNSUPPORTED_FILE_TYPE(20005, "不支持的文件类型", 400),
    UNSUPPORTED_TARGET_FORMAT(20006, "不支持的目标格式", 400),
    INVALID_URL_FORMAT(20007, "无效的URL格式", 400),
    INVALID_FILE_PATH(20008, "无效的文件路径", 400),
    FILE_TYPE_MISMATCH(20009, "文件类型不匹配", 400),
    
    // ========== 文件操作错误 (30000-39999) ==========
    FILE_NOT_FOUND(30000, "文件不存在", 404),
    FILE_READ_ERROR(30001, "文件读取失败", 500),
    FILE_WRITE_ERROR(30002, "文件写入失败", 500),
    FILE_DELETE_ERROR(30003, "文件删除失败", 500),
    FILE_CORRUPTED(30004, "文件损坏", 400),
    FILE_LOCKED(30005, "文件被锁定", 423),
    FILE_TOO_LARGE(30006, "文件过大", 413),
    FILE_EMPTY(30007, "文件为空", 400),
    DIRECTORY_NOT_FOUND(30008, "目录不存在", 404),
    DIRECTORY_CREATE_FAILED(30009, "目录创建失败", 500),
    FILE_PERMISSION_DENIED(30010, "文件权限不足", 403),
    UNSAFE_FILE_PATH(30011, "不安全的文件路径", 400),
    FILE_DOWNLOAD_FAILED(30012, "文件下载失败", 500),
    FILE_UPLOAD_FAILED(30013, "文件上传失败", 500),
    FILE_EXTRACT_FAILED(30014, "文件解压失败", 500),
    NETWORK_DOWNLOAD_FAILED(30015, "网络文件下载失败", 500),
    
    // ========== 转换引擎错误 - 通用 (40000-40099) ==========
    CONVERSION_FAILED(40000, "转换失败", 500),
    CONVERSION_TIMEOUT(40001, "转换超时", 504),
    CONVERSION_CANCELLED(40002, "转换取消", 499),
    CONVERSION_ENGINE_NOT_AVAILABLE(40003, "转换引擎不可用", 503),
    CONVERSION_ENGINE_ERROR(40004, "转换引擎错误", 500),
    CONVERSION_QUEUE_FULL(40005, "转换队列已满", 503),
    CONVERSION_IN_PROGRESS(40006, "转换正在进行中", 202),
    UNSUPPORTED_CONVERSION(40007, "不支持的转换类型", 400),
    CONVERSION_RESULT_NOT_FOUND(40008, "转换结果不存在", 404),
    
    // ========== 转换引擎错误 - LibreOffice (40100-40199) ==========
    LIBREOFFICE_NOT_INSTALLED(40100, "LibreOffice未安装", 503),
    LIBREOFFICE_DISABLED(40101, "LibreOffice转换器已禁用", 503),
    LIBREOFFICE_CONVERSION_FAILED(40102, "LibreOffice转换失败", 500),
    LIBREOFFICE_CONVERSION_TIMEOUT(40103, "LibreOffice转换超时", 504),
    LIBREOFFICE_PROCESS_ERROR(40104, "LibreOffice进程错误", 500),
    LIBREOFFICE_INVALID_EXIT_CODE(40105, "LibreOffice异常退出", 500),
    LIBREOFFICE_COMMAND_FAILED(40106, "LibreOffice命令执行失败", 500),
    LIBREOFFICE_OUTPUT_ERROR(40107, "LibreOffice输出错误", 500),
  
    // ========== 外部服务错误 (50000-59999) ==========
    EXTERNAL_SERVICE_ERROR(50000, "外部服务错误", 502),
    EXTERNAL_SERVICE_UNAVAILABLE(50001, "外部服务不可用", 503),
    EXTERNAL_SERVICE_TIMEOUT(50002, "外部服务超时", 504),
    EXTERNAL_API_ERROR(50003, "外部API错误", 502),
    REDIS_CONNECTION_ERROR(50004, "Redis连接错误", 500),
    CACHE_SERVICE_ERROR(50005, "缓存服务错误", 500),
    MESSAGE_QUEUE_ERROR(50006, "消息队列错误", 500),
    
    // ========== 系统资源错误 (60000-69999) ==========
    RESOURCE_EXHAUSTED(60000, "资源耗尽", 503),
    OUT_OF_MEMORY(60001, "内存不足", 503),
    DISK_SPACE_FULL(60002, "磁盘空间不足", 507),
    CPU_OVERLOAD(60003, "CPU过载", 503),
    THREAD_POOL_EXHAUSTED(60004, "线程池耗尽", 503),
    CONNECTION_POOL_EXHAUSTED(60005, "连接池耗尽", 503),
    RATE_LIMIT_EXCEEDED(60006, "速率限制超限", 429),
    
    // ========== 网络通信错误 (70000-79999) ==========
    NETWORK_ERROR(70000, "网络错误", 500),
    CONNECTION_FAILED(70001, "连接失败", 502),
    CONNECTION_TIMEOUT(70002, "连接超时", 504),
    READ_TIMEOUT(70003, "读取超时", 504),
    WRITE_TIMEOUT(70004, "写入超时", 504),
    DNS_RESOLUTION_FAILED(70005, "DNS解析失败", 502),
    SSL_ERROR(70006, "SSL错误", 495),
    
    // ========== 安全权限错误 (80000-89999) ==========
    AUTHENTICATION_REQUIRED(80000, "需要认证", 401),
    AUTHENTICATION_FAILED(80001, "认证失败", 401),
    AUTHORIZATION_FAILED(80002, "授权失败", 403),
    ACCESS_DENIED(80003, "访问拒绝", 403),
    TOKEN_EXPIRED(80004, "令牌过期", 401),
    TOKEN_INVALID(80005, "令牌无效", 401),
    PERMISSION_DENIED(80006, "权限不足", 403),
    LICENSE_EXPIRED(80007, "授权过期", 402),
    SECURITY_VIOLATION(80008, "安全违规", 403),
    
    // ========== 业务逻辑错误 (90000-99999) ==========
    BUSINESS_RULE_VIOLATION(90000, "业务规则违规", 400),
    PREVIEW_NOT_READY(90001, "预览未就绪", 202),
    PREVIEW_EXPIRED(90002, "预览已过期", 410),
    CACHE_MISS(90003, "缓存未命中", 404),
    DOCUMENT_ENCRYPTED(90004, "文档已加密", 400),
    DOCUMENT_PASSWORD_REQUIRED(90005, "需要文档密码", 400),
    DOCUMENT_PASSWORD_INCORRECT(90006, "文档密码错误", 400),
    CONVERSION_NOT_REQUIRED(90007, "无需转换", 200),
    PREVIEW_FAILED(90008, "预览生成失败", 500);
    
    /** 错误码 */
    private final int code;
    
    /** 错误消息 */
    private final String message;
    
    /** HTTP状态码 */
    private final int httpStatus;
    
    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
    
    /**
     * 根据错误码查找枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
    
    /**
     * 从LibreOffice退出码映射到统一错误码
     */
    public static ErrorCode fromLibreOfficeExitCode(int exitCode) {
        if (exitCode == 0) {
            return null; // 成功无错误码
        }
        return LIBREOFFICE_INVALID_EXIT_CODE;
    }
    
    /**
     * 判断是否为可重试错误
     */
    public boolean isRetryable() {
        return this == SERVICE_TIMEOUT ||
               this == SERVICE_UNAVAILABLE ||
               this == CONVERSION_TIMEOUT ||
               this == NETWORK_ERROR ||
               this == CONNECTION_TIMEOUT ||
               this == READ_TIMEOUT ||
               this == EXTERNAL_SERVICE_TIMEOUT ||
               this == LIBREOFFICE_CONVERSION_TIMEOUT;
    }
    
    /**
     * 判断是否为客户端错误（4xx）
     */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }
    
    /**
     * 判断是否为服务端错误（5xx）
     */
    public boolean isServerError() {
        return httpStatus >= 500 && httpStatus < 600;
    }
    
    @Override
    public String toString() {
        return code + ": " + message;
    }
}