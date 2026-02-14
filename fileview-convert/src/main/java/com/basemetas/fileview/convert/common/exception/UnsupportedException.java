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
 * 不支持异常
 * 
 * 用于标识转换引擎因能力限制而无法处理特定类型文件的场景
 * 例如：LibreOffice 不支持带密码的旧式 Office 文档
 * 
 * @author 夫子
 */
public class UnsupportedException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * 构造函数
     * 
     * @param errorCode 错误代码（如 ENGINE_UNSUPPORTED_ENCRYPTED_LEGACY）
     * @param message 错误消息
     */
    public UnsupportedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 获取错误代码
     * 
     * @return 错误代码
     */
    public String getErrorCode() {
        return errorCode;
    }
}
