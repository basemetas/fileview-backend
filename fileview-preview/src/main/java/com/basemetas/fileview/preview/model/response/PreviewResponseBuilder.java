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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 预览响应构建器
 * 
 * 使用建造者模式统一构建预览服务的响应对象，避免重复代码
 * 
 * 使用示例：
 * <pre>
 * Map<String, Object> response = PreviewResponseBuilder.create()
 *     .fileId(fileId)
 *     .status("DOWNLOADING")
 *     .processingDuration(duration)
 *     .build();
 * </pre>
 * 
 * @author 夫子
 */
public class PreviewResponseBuilder {
    
    private final Map<String, Object> response;
    
    private PreviewResponseBuilder() {
        this.response = new HashMap<>();
        // 设置默认值
        initDefaults();
    }
    
    /**
     * 创建构建器实例
     */
    public static PreviewResponseBuilder create() {
        return new PreviewResponseBuilder();
    }
    
    /**
     * 初始化默认值
     */
    private void initDefaults() {
        response.put("originalFileName", "");
        response.put("originalFilePath", "");
        response.put("originalFileFormat", "");
        response.put("originalFileSize", 0);
        response.put("previewUrl", "");
        response.put("previewFileSize", 0);
        response.put("previewFileFormat", "");
        response.put("mode", "");
        response.put("conversionRequired", false);
        response.put("urlExpirationSeconds", 24 * 3600L);
        response.put("urlExpirationTime", LocalDateTime.now().plusHours(24));
        response.put("cacheRemainingTtl", 0);
    }
    
    public PreviewResponseBuilder fileId(String fileId) {
        response.put("fileId", fileId != null ? fileId : "");
        return this;
    }
    
    public PreviewResponseBuilder status(String status) {
        response.put("status", status);
        return this;
    }
    
    public PreviewResponseBuilder processingDuration(long duration) {
        response.put("processingDuration", duration);
        return this;
    }
    
    public PreviewResponseBuilder originalFileName(String fileName) {
        if (fileName != null) {
            response.put("originalFileName", fileName);
        }
        return this;
    }
    
    public PreviewResponseBuilder originalFilePath(String filePath) {
        if (filePath != null) {
            response.put("originalFilePath", filePath);
        }
        return this;
    }
    
    public PreviewResponseBuilder originalFileFormat(String format) {
        if (format != null) {
            response.put("originalFileFormat", format);
        }
        return this;
    }
    
    public PreviewResponseBuilder originalFileSize(long size) {
        response.put("originalFileSize", size);
        return this;
    }
    
    public PreviewResponseBuilder previewUrl(String url) {
        if (url != null) {
            response.put("previewUrl", url);
        }
        return this;
    }
    
    public PreviewResponseBuilder previewFileSize(long size) {
        response.put("previewFileSize", size);
        return this;
    }
    
    public PreviewResponseBuilder previewFileFormat(String format) {
        if (format != null) {
            response.put("previewFileFormat", format);
        }
        return this;
    }
    
    public PreviewResponseBuilder mode(String mode) {
        if (mode != null) {
            response.put("mode", mode);
        }
        return this;
    }
    
    public PreviewResponseBuilder conversionRequired(boolean required) {
        response.put("conversionRequired", required);
        return this;
    }
    
    public PreviewResponseBuilder urlExpirationSeconds(long seconds) {
        response.put("urlExpirationSeconds", seconds);
        return this;
    }
    
    public PreviewResponseBuilder urlExpirationTime(LocalDateTime time) {
        if (time != null) {
            response.put("urlExpirationTime", time);
        }
        return this;
    }
    
    public PreviewResponseBuilder cacheRemainingTtl(long ttl) {
        response.put("cacheRemainingTtl", ttl);
        return this;
    }
    
    public PreviewResponseBuilder errorCode(String code) {
        if (code != null) {
            response.put("errorCode", code);
        }
        return this;
    }
    
    public PreviewResponseBuilder errorCode(int code) {
        response.put("errorCode", String.valueOf(code));
        return this;
    }
    
    public PreviewResponseBuilder errorMessage(String message) {
        if (message != null) {
            response.put("errorMessage", message);
        }
        return this;
    }
    
    /**
     * 添加自定义字段
     */
    public PreviewResponseBuilder custom(String key, Object value) {
        if (key != null && value != null) {
            response.put(key, value);
        }
        return this;
    }
    
    /**
     * 批量添加字段（用于从缓存数据构建）
     */
    public PreviewResponseBuilder merge(Map<String, Object> data) {
        if (data != null) {
            response.putAll(data);
        }
        return this;
    }
    
    /**
     * 构建最终响应对象
     */
    public Map<String, Object> build() {
        return response;
    }
}
