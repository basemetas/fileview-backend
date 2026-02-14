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
package com.basemetas.fileview.convert.service.cache;

import com.basemetas.fileview.convert.model.ConvertResultInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文件转换结果缓存策略
 * 
 * 功能：
 * - 缓存完整的转换结果信息，包含预览所需的所有信息
 * - 支持按文件ID和目标格式的组合键缓存
 * - 根据转换状态自动调整TTL：成功24小时，转换中10分钟，失败60秒
 * 
 * 缓存键格式：convert:{fileId}:{targetFormat}
 * 缓存值：ConvertResultInfo完整对象
 * 
 * @author 夫子
 */
@Component
public class ConvertResultCacheStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ConvertResultCacheStrategy.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 缓存键前缀
    private static final String CACHE_PREFIX = "convert:";

    // TTL配置（秒）
    private static final long CONVERTING_TTL = 600L; // 转换中：10分钟
    private static final long SUCCESS_TTL = 86400L; // 转换成功：24小时
    private static final long FAILED_TTL = 60L; // 转换失败：60秒（允许快速重试）


    /**
     * 缓存转换结果信息
     * 根据转换状态自动设置TTL：
     * - 成功: 24小时
     * - 转换中: 10分钟
     * - 失败: 60秒
     * 
     * @param fileId            文件ID
     * @param targetFormat      目标格式
     * @param convertResultInfo 完整的转换结果信息
     */
    public void cacheConvertResult(String fileId, String targetFormat, ConvertResultInfo convertResultInfo) {
        try {
            String status = convertResultInfo.getStatus() != null ? convertResultInfo.getStatus().name() : "UNKNOWN";

            // 根据状态确定TTL
            long ttl = determineTTL(status);
            Duration duration = Duration.ofSeconds(ttl);

            // 1. 缓存转换结果
            String legacyKey = buildCacheKey(fileId, targetFormat);
            convertResultInfo.setExpiresAt(System.currentTimeMillis() + duration.toMillis());
            convertResultInfo.setCachedAt(System.currentTimeMillis());
            redisTemplate.opsForValue().set(legacyKey, convertResultInfo, duration);

            logger.info("✅ 缓存转换结果成功 - FileId: {}, Format: {}, Status: {}, TTL: {}s",
                    fileId, targetFormat, status, ttl);

        } catch (Exception e) {
            logger.error("❌ 缓存转换结果失败 - FileId: {}, Format: {}, Status: {}",
                    fileId, targetFormat, convertResultInfo.getStatus(), e);
        }
    }



    /**
     * 构建缓存键
     * 
     * @param fileId       文件ID
     * @param targetFormat 目标格式
     * @return 缓存键
     */
    private String buildCacheKey(String fileId, String targetFormat) {
        return CACHE_PREFIX + fileId + ":" + targetFormat.toLowerCase();
    }

    /**
     * 根据状态确定TTL策略
     */
    private long determineTTL(String status) {
        if (status == null) {
            return FAILED_TTL;
        }

        switch (status.toUpperCase()) {
            case "SUCCESS":
                return SUCCESS_TTL; // 24小时
            case "IN_PROGRESS":
            case "CONVERTING":
                return CONVERTING_TTL; // 10分钟
            case "FAILED":
            case "ERROR":
                return FAILED_TTL; // 1小时
            default:
                return CONVERTING_TTL; // 默认使用转换中TTL
        }
    }


}