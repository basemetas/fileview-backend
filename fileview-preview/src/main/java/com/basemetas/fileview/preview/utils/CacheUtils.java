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
package com.basemetas.fileview.preview.utils;

import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CacheUtils {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public String buildCacheKey(String fileId, String targetFormat) {
        return CacheKeyManager.buildConvertCacheKey(fileId, targetFormat);
    }

    /**
     * 构建精细化结果缓存键
     */
    public String buildResultKey(String fileId, String targetFormat) {
        return CacheKeyManager.buildConvertResultKey(fileId, targetFormat);
    }

     /**
     * 构建直接预览缓存键
     * @param fileId 文件ID
     * @return 缓存键
     */
    public String buildDirectPreviewKey(String fileId) {
        return CacheKeyManager.buildDirectPreviewKey(fileId);
    }
    
    public String getRedisValue(String key) {
        try {
            return redisTemplate != null ? redisTemplate.opsForValue().get(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void setRedisValue(String key, String value) {
        try {
            if (redisTemplate != null && value != null) {
                redisTemplate.opsForValue().set(key, value);
            }
        } catch (Exception e) {
            // 忽略写入失败，下载流程不受影响
        }
    }


}
