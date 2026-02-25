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
package com.basemetas.fileview.preview.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.utils.CacheUtils;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * 文件预览结果缓存策略（只读版 - 已优化）
 * 
 * 职责：
 * - 从Redis查询转换服务缓存的预览信息（只读）
 * - 支持直接预览缓存的读写
 * - 为预览服务提供统一的缓存查询接口
 * - 启动时预热 Redis 连接池
 * 
 * 重要：
 * - 预览服务只负责读取Redis中的转换结果
 * - 所有转换缓存数据由转换服务统一写入和维护
 * - 直接预览缓存由预览服务自身维护
 * - 不在此服务中写入任何转换相关的缓存数据
 * 
 * 缓存键格式：
 * - 转换结果：convert:{fileId}:{targetFormat}
 * - 直接预览：preview:direct:{fileId}
 * 
 * 缓存值：转换服务的ConvertResultInfo完整对象（Map结构）
 * 
 * 
 * @author 夫子
 * @version 2.0
 * @since 2025-10-15
 */
@Component
public class CacheReadService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheReadService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PreviewCacheAssembler previewCacheAssembler;

    @Autowired
    private CacheUtils cacheUtils;
    
    /**
     * 应用启动后预热 Redis 连接池
     * 解决首次请求 Redis 查询过慢问题
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpRedisConnectionPool() {
        try {
            long startTime = System.currentTimeMillis();
            // 执行简单操作预热连接
            redisTemplate.opsForValue().get("health-check-warmup");
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ Redis 连接池预热完成 - 耗时: {}ms", duration);
        } catch (Exception e) {
            logger.warn("⚠️ Redis 连接池预热失败: {}", e.getMessage());
        }
    }
  
    /**
     * 从转换服务查询预览结果（简化版 - 只查询传统缓存）
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @return 预览缓存信息，如果未找到则返回null
     */
    public PreviewCacheInfo getCachedResult(String fileId, String targetFormat) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 查询缓存时fileId为空");
            return null;
        }
        
        if (targetFormat == null || targetFormat.trim().isEmpty()) {
            logger.warn("⚠️ 查询缓存时targetFormat为空 - FileId: {}", fileId);
            return null;
        }
        try {
            // 直接查询传统缓存（1次Redis查询）
            String cacheKey = cacheUtils.buildCacheKey(fileId, targetFormat);            
            Object cacheData = redisTemplate.opsForValue().get(cacheKey);           
            if (cacheData != null) {
                logger.debug("📦 获取到缓存数据 - Type: {}", cacheData.getClass().getName());               
                PreviewCacheInfo previewInfo = convertFromConvertResult(cacheData);              
                if (previewInfo != null) {
                    return previewInfo;
                } else {
                    logger.warn("⚠️ 缓存数据转换失败 - Key: {}", cacheKey);
                }
            } else {
                logger.debug("❌ 转换缓存未命中 - FileId: {}, Format: {}", fileId, targetFormat);
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("❌ 查询转换缓存失败 - FileId: {}, Format: {}", fileId, targetFormat, e);
            // 缓存查询失败不抛出异常，返回null让调用方处理
            return null;
        }
    }
    
    /**
     * 根据文件ID查询（按优先级尝试常见格式，如果都没找到则使用通配符查询）
     * 注意：此方法只查询指定fileId的缓存，不会跨文件查询
     * 优先级列表与FileFormatConfig中配置的默认目标格式保持一致
     * @param fileId 文件ID（精确匹配）
     * @return 预览缓存信息，如果未找到则返回null
     */
    public PreviewCacheInfo getCachedResult(String fileId) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 查询缓存时fileId为空");
            return null;
        }     
        // 1. 优先查询直接预览缓存
        PreviewCacheInfo directResult = getDirectPreviewCache(fileId);
        if (directResult != null) {
            return directResult;
        }
        
        // 2. 查询转换结果缓存（按优先级尝试目标格式）
        String[] commonFormats = { "pdf", "png", "xlsx", "json", "jpg" };
        
        // 批量构建键：先直接预览键，再各格式转换键
        String directKey = cacheUtils.buildDirectPreviewKey(fileId);
        List<String> keys = new ArrayList<>();
        keys.add(directKey);
        for (String fmt : commonFormats) {
            keys.add(cacheUtils.buildCacheKey(fileId, fmt));
        }
        
        // 单次请求批量获取（MGET 或管道）
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        if (values != null && !values.isEmpty()) {
            // 1) 直接预览缓存命中
            Object directVal = values.get(0);
            if (directVal != null) {
                PreviewCacheInfo directInfo = convertFromConvertResult(directVal);
                if (directInfo != null) {
                    return directInfo;
                }
            }
            // 2) 按优先级检查转换缓存命中
            for (int i = 0; i < commonFormats.length; i++) {
                Object val = values.get(i + 1);
                if (val != null) {
                    PreviewCacheInfo info = convertFromConvertResult(val);
                    if (info != null) {
                        return info;
                    }
                }
            }
        }
        
        // 3. 如果批量未命中，使用通配符查询作为兜底（避免频繁使用）
        try {
            String pattern = cacheUtils.buildCacheKey(fileId, "*");
            Set<String> keysWildcard = redisTemplate.keys(pattern);
            if (keysWildcard != null && !keysWildcard.isEmpty()) {
                for (String key : keysWildcard) {
                    if (key.endsWith(":status") || key.endsWith(":progress") || key.endsWith(":result")) {
                        continue;
                    }
                    Object cacheData = redisTemplate.opsForValue().get(key);
                    if (cacheData != null) {
                        PreviewCacheInfo previewInfo = convertFromConvertResult(cacheData);
                        if (previewInfo != null) {
                            return previewInfo;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ 通配符查询失败 - FileId: {}", fileId, e);
        }
        return null;
    }
    
    /**
     * 获取缓存的剩余生存时间
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @return 剩余生存时间（秒），-1表示永不过期，-2表示不存在
     */
    public Long getCacheTTL(String fileId, String targetFormat) {
        try {
            String key = cacheUtils.buildCacheKey(fileId, targetFormat);
            return redisTemplate.getExpire(key);
        } catch (Exception e) {
            logger.error("❌ 获取转换服务缓存TTL失败 - FileId: {}, Format: {}", fileId, targetFormat, e);
            return -2L; // 表示获取失败
        }
    }
    
    /**
     * 获取缓存的剩余生存时间（按优先级查找格式）
     * 优先级列表与FileFormatConfig中配置的默认目标格式保持一致
     * @param fileId 文件ID（精确匹配）
     * @return 剩余生存时间（秒），-1表示永不过期，-2表示不存在
     */
    public Long getCacheTTL(String fileId) {
        // 与getCachedResult保持一致的优先级列表
        String[] commonFormats = {
            "pdf", "xlsx", "png", "json",
            "jpg", "jpeg", "html", "htm",
            "txt", "md", "xls",
            "doc", "docx", "ppt", "pptx",
            "svg", "webp", "gif", "xml", "csv"
        };
        
        for (String format : commonFormats) {
            Long ttl = getCacheTTL(fileId, format);
            if (ttl != null && ttl >= 0) {
                return ttl;
            }
        }
        
        // 如果优先级列表都没找到，使用通配符查询
        try {
            String pattern = cacheUtils.buildCacheKey(fileId, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                // 返回第一个有效的TTL（过滤掉精细化缓存键）
                for (String key : keys) {
                    // 跳过精细化缓存键
                    if (key.endsWith(":status") || key.endsWith(":progress") || key.endsWith(":result")) {
                        continue;
                    }
                    
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl >= 0) {
                        return ttl;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️ 通配符查询TTL失败 - FileId: {}", fileId, e);
        }
        
        return -2L; // 未找到
    }
    
    /**
     * 从转换服务的ConvertResultInfo转换为PreviewCacheInfo
     */
    private PreviewCacheInfo convertFromConvertResult(Object cacheData) {
        try {
            return previewCacheAssembler.parseConvertCacheToPreviewCacheInfo(cacheData);
        } catch (Exception e) {
            logger.error("❌ 转换ConvertResultInfo失败", e);
        }
        return null;
    }
    
    /**
     * 查询直接预览缓存
     * @param fileId 文件ID
     * @return 预览缓存信息，如果未找到则返回null
     */
    public PreviewCacheInfo getDirectPreviewCache(String fileId) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 查询直接预览缓存时fileId为空");
            return null;
        }
        
        try {
            String cacheKey = cacheUtils.buildDirectPreviewKey(fileId);
            logger.debug("🔎 查询直接预览缓存 - FileId: {}, Key: {}", fileId, cacheKey);
            
            Object cacheData = redisTemplate.opsForValue().get(cacheKey);
            
            if (cacheData != null) {                
                PreviewCacheInfo previewInfo = convertFromConvertResult(cacheData);             
                if (previewInfo != null) {
                    return previewInfo;
                } else {
                    logger.warn("⚠️ 直接预览缓存数据转换失败 - FileId: {}", fileId);
                }
            } else {
                // 未命中不记录日志，减少日志噪音（转换文件走转换缓存是正常情况）
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("❌ 查询直接预览缓存失败 - FileId: {}", fileId, e);
            return null;
        }
    }
    
   
   
}