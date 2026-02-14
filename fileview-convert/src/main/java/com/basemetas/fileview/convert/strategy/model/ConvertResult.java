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
package com.basemetas.fileview.convert.strategy.model;

/**
 * 转换结果类
 * 
 * 封装文档转换操作的结果信息，包括成功/失败状态、使用的引擎、消息等
 * 统一不同引擎选择器的返回结果格式
 * 
 * @author 夫子
 */
public class ConvertResult {
    private final boolean success;
    private final Object usedEngine; // 使用Object类型以兼容不同引擎枚举
    private final String message;
    private final String errorMessage;
    private final Exception exception;
    private final String errorCode; // 错误代码，用于标识特定失败原因
    
    private ConvertResult(boolean success, Object usedEngine, String message, String errorMessage, Exception exception, String errorCode) {
        this.success = success;
        this.usedEngine = usedEngine;
        this.message = message;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.errorCode = errorCode;
    }
    
    public static ConvertResult success(Object engine, String message) {
        return new ConvertResult(true, engine, message, null, null, null);
    }
    
    public static ConvertResult failure(String errorMessage) {
        return new ConvertResult(false, null, null, errorMessage, null, null);
    }
    
    public static ConvertResult failure(String errorMessage, Exception exception) {
        return new ConvertResult(false, null, null, errorMessage, exception, null);
    }
    
    /**
     * 创建带错误代码的失败结果
     * @param errorCode 错误代码（如 ENGINE_UNSUPPORTED_ENCRYPTED_LEGACY）
     * @param errorMessage 错误消息
     * @return 失败结果
     */
    public static ConvertResult failureWithCode(String errorCode, String errorMessage) {
        return new ConvertResult(false, null, errorMessage, errorMessage, null, errorCode);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public Object getUsedEngine() { return usedEngine; }
    public String getMessage() { return message; }
    public String getErrorMessage() { return errorMessage; }
    public Exception getException() { return exception; }
    public String getErrorCode() { return errorCode; }
    
    // 引擎枚举兼容方法
    public com.basemetas.fileview.convert.strategy.impl.converter.ConvertEngineSelector.ConvertEngine getEngine() {
        if (usedEngine instanceof com.basemetas.fileview.convert.strategy.impl.converter.ConvertEngineSelector.ConvertEngine) {
            return (com.basemetas.fileview.convert.strategy.impl.converter.ConvertEngineSelector.ConvertEngine) usedEngine;
        }
        return null;
    }
}