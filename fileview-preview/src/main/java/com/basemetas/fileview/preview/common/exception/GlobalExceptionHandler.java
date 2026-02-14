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

import com.basemetas.fileview.preview.model.response.ReturnResponse;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 
 * 统一处理所有异常，使用统一的错误码体系
 * 
 * @author 夫子
 * @version 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理FileViewException（业务异常）
     */
    @ExceptionHandler(FileViewException.class)
    public ResponseEntity<Map<String, Object>> handleFileViewException(
            FileViewException e, 
            HttpServletRequest request) {
        
        logger.error("业务异常 - 错误码: {}, 消息: {}, 路径: {}", 
                    e.getErrorCode().getCode(), e.getMessage(), request.getRequestURI());
        
        return ReturnResponse.error(HttpStatus.valueOf(e.getHttpStatus()), e.getErrorCode());
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException e,
            HttpServletRequest request) {
        
        logger.error("参数验证失败 - 路径: {}", request.getRequestURI());
        
        String details = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        return ReturnResponse.badRequest("参数验证失败: " + details);
    }
    
    /**
     * 处理IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request) {
        
        logger.error("非法参数异常 - 路径: {}, 消息: {}", request.getRequestURI(), e.getMessage());
        
        return ReturnResponse.badRequest(e.getMessage());
    }
    
    /**
     * 处理RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e,
            HttpServletRequest request) {
        
        logger.error("运行时异常 - 路径: {}", request.getRequestURI(), e);
        
        return ReturnResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
    }
    
    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknownException(
            Exception e,
            HttpServletRequest request) {
        
        // 忽略客户端提前断开连接的异常（Broken pipe）
        if (isClientAbortException(e)) {
            logger.debug("🔌 客户端提前断开连接 - 路径: {}, 原因: {}", 
                request.getRequestURI(), e.getMessage());
            // 返回空响应，不记录错误
            return null;
        }
        
        logger.error("未知异常 - 路径: {}", request.getRequestURI(), e);
        
        return ReturnResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_ERROR);
    }
    
    /**
     * 判断是否为客户端断开连接异常
     */
    private boolean isClientAbortException(Throwable e) {
        if (e == null) {
            return false;
        }
        
        String className = e.getClass().getName();
        String message = e.getMessage();
        
        // 检查异常类型
        if (className.contains("ClientAbortException") || 
            className.contains("AsyncRequestNotUsableException")) {
            return true;
        }
        
        // 检查异常消息
        if (message != null && (message.contains("Broken pipe") || 
                               message.contains("Connection reset by peer") ||
                               message.contains("An established connection was aborted"))) {
            return true;
        }
        
        // 递归检查原因
        return isClientAbortException(e.getCause());
    }
}