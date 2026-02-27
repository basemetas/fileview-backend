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
package com.basemetas.fileview.preview.model;

/**
 * 预览状态上下文
 * 
 * 用途：封装长轮询所需的所有数据，避免方法参数过多
 * 
 * 包含：
 * - 基础信息：fileId, targetFormat
 * - 核心数据：cacheInfo（缓存信息）
 * - 业务依赖：clientId（密码解锁），requestBaseUrl（URL拼接）
 * - 辅助信息：startTime（耗时计算）
 * 
 * @author 夫子
 */
public class PreviewStatusContext {
    private String fileId;
    private String targetFormat;
    private PreviewCacheInfo cacheInfo;
    private String clientId;
    private String requestBaseUrl;
    private long startTime;
    
    private PreviewStatusContext() {}
    
    public String getFileId() {
        return fileId;
    }
    
    public String getTargetFormat() {
        return targetFormat;
    }
    
    public PreviewCacheInfo getCacheInfo() {
        return cacheInfo;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getRequestBaseUrl() {
        return requestBaseUrl;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public static PreviewStatusContextBuilder builder() {
        return new PreviewStatusContextBuilder();
    }
    
    public static class PreviewStatusContextBuilder {
        private PreviewStatusContext context = new PreviewStatusContext();
        
        public PreviewStatusContextBuilder fileId(String fileId) {
            context.fileId = fileId;
            return this;
        }
        
        public PreviewStatusContextBuilder targetFormat(String targetFormat) {
            context.targetFormat = targetFormat;
            return this;
        }
        
        public PreviewStatusContextBuilder cacheInfo(PreviewCacheInfo cacheInfo) {
            context.cacheInfo = cacheInfo;
            return this;
        }
        
        public PreviewStatusContextBuilder clientId(String clientId) {
            context.clientId = clientId;
            return this;
        }
        
        public PreviewStatusContextBuilder requestBaseUrl(String requestBaseUrl) {
            context.requestBaseUrl = requestBaseUrl;
            return this;
        }
        
        public PreviewStatusContextBuilder startTime(long startTime) {
            context.startTime = startTime;
            return this;
        }
        
        public PreviewStatusContext build() {
            return context;
        }
    }
}
