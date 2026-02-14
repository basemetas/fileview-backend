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
package com.basemetas.fileview.preview.model.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一API响应封装类
 * 
 * 提供标准化的响应格式
 * 
 * @author 夫子
 * @version 1.0
 */
public class ReturnResponse<T> {
    
    private int code;
    private String message;
    private T data;
    private Long timestamp;
    
    // ========== 构造函数 ==========
    
    private ReturnResponse() {
        this.timestamp = Instant.now().toEpochMilli();
    }
    
    private ReturnResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }
    
    // ========== 成功响应构建方法 ==========
    
    /**
     * 创建成功响应（无数据）
     */
    public static <T> ResponseEntity<Map<String, Object>> success() {
        return success(null, "操作成功");
    }
    
    /**
     * 创建成功响应（带数据）
     */
    public static <T> ResponseEntity<Map<String, Object>> success(T data) {
        return success(data, "操作成功");
    }
    
    /**
     * 创建成功响应（带数据和消息）
     */
    public static <T> ResponseEntity<Map<String, Object>> success(T data, String message) {
        ReturnResponse<T> response = new ReturnResponse<>(0, message, data);
        return ResponseEntity.ok(response.toMap());
    }
    
    /**
     * 创建成功响应（仅消息）
     */
    public static ResponseEntity<Map<String, Object>> successMessage(String message) {
        ReturnResponse<Object> response = new ReturnResponse<>(0, message, null);
        return ResponseEntity.ok(response.toMap());
    }
    
    // ========== 失败响应构建方法 ==========
    
    /**
     * 创建失败响应（仅错误消息）
     */
    public static ResponseEntity<Map<String, Object>> error(String errorMessage) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.SYSTEM_ERROR);
    }
    
    /**
     * 创建失败响应（带HTTP状态码和业务错误码）
     */
    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, ErrorCode errorCode) {
        ReturnResponse<Object> response = new ReturnResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
        return ResponseEntity.status(status).body(response.toMap());
    }
    
    /**
     * 创建失败响应（带HTTP状态码、业务错误码和消息）
     */
    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, int errorCode, String errorMessage) {
        ReturnResponse<Object> response = new ReturnResponse<>(errorCode, errorMessage, null);
        return ResponseEntity.status(status).body(response.toMap());
    }
    
    /**
     * 创建参数验证失败响应
     */
    public static ResponseEntity<Map<String, Object>> badRequest(String errorMessage) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PARAMETER);
    }
    
    /**
     * 创建参数验证失败响应（带业务错误码）
     */
    public static ResponseEntity<Map<String, Object>> badRequest(ErrorCode errorCode) {
        return error(HttpStatus.BAD_REQUEST, errorCode);
    }
    
    /**
     * 创建未找到资源响应
     */
    public static ResponseEntity<Map<String, Object>> notFound(String errorMessage) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND);
    }
    
    /**
     * 创建服务不可用响应
     */
    public static ResponseEntity<Map<String, Object>> serviceUnavailable(String errorMessage) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE);
    }
    
    // ========== 转换为Map方法 ==========
    
    /**
     * 将响应对象转换为Map
     * 便于返回灵活的JSON结构
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("timestamp", timestamp);
        
        if (message != null) {
            map.put("message", message);
        }
        
        if (data != null) {
            map.put("data", data);
        }
        
        return map;
    }
    
    // ========== Builder模式（可选） ==========
    
    /**
     * 创建响应构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private int code = 0;
        private String message;
        private T data;
        
        public Builder<T> code(int code) {
            this.code = code;
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public ResponseEntity<Map<String, Object>> build() {
            ReturnResponse<T> response = new ReturnResponse<>(code, message, data);
            HttpStatus status = code == 0 ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(response.toMap());
        }
        
        public ResponseEntity<Map<String, Object>> build(HttpStatus status) {
            ReturnResponse<T> response = new ReturnResponse<>(code, message, data);
            return ResponseEntity.status(status).body(response.toMap());
        }
    }
    
    // ========== Getters ==========
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public T getData() {
        return data;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
}