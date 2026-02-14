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

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.service.url.MultiPageUrlService;

@Component
public class CacheWriteService {
    private static final Logger logger = LoggerFactory.getLogger(CacheWriteService.class);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MultiPageUrlService multiPageUrlService;

    @Autowired
    private com.basemetas.fileview.preview.service.cache.PreviewCacheAssembler previewCacheAssembler;

    @Autowired
    private com.basemetas.fileview.preview.utils.CacheUtils cacheUtils;
 
    @Value("${fileview.preview.cache.conversion.success-ttl:86400}")
    private long successTtl;

    @Value("${fileview.preview.cache.conversion.failed-ttl:60}")
    private long failedTtl;

    @Value("${fileview.preview.cache.direct-preview-ttl:86400}")
    private long directPreviewTtl;

    /**
     * 更新预览URL到Redis缓存
     * 使用与转换服务相同的缓存键格式,确保客户端能轮询到结果
     */
    public void updatePreviewUrlToCache(String fileId, String targetFormat,
            String previewUrl, Long fileSize,
            Map<String, Object> conversionResult) {
        try {
            // 1. 构建缓存键(与转换服务保持一致)
            String cacheKey = cacheUtils.buildCacheKey(fileId, targetFormat);
            String resultKey = cacheUtils.buildResultKey(fileId, targetFormat);

            // 2. 读取现有缓存数据
            Object existingData = redisTemplate.opsForValue().get(cacheKey);

            Map<String, Object> cacheData;
            if (existingData instanceof Map) {
                cacheData = previewCacheAssembler.buildSuccessConvertCache((Map<String, Object>) existingData, previewUrl, fileSize, targetFormat);
                logger.info("✅ 读取到现有缓存数据,准备更新");
            } else {
                cacheData = previewCacheAssembler.buildSuccessConvertCache(conversionResult, previewUrl, fileSize, targetFormat);
                logger.info("ℹ️ 未找到现有缓存,创建新缓存数据");
            }

            // 3. 更新预览URL和文件大小
            cacheData.put("previewUrl", previewUrl);
            cacheData.put("previewFileSize", fileSize);
            cacheData.put("mode", "CONVERT");
            cacheData.put("status", "SUCCESS");
            cacheData.put("cachedAt", System.currentTimeMillis());
            cacheData.put("expiresAt", System.currentTimeMillis() + Duration.ofSeconds(successTtl).toMillis());
            // 🔑 从转换结果中获取 requestBaseUrl 并存入 data 字段
            String cachedBaseUrlForCache = null;
            Object baseUrlObj = conversionResult.get("requestBaseUrl");
            if (baseUrlObj != null) {
                cachedBaseUrlForCache = baseUrlObj.toString();
            }
            // 携带请求侧的 baseUrl，供前端或后续拼接使用
            if (cachedBaseUrlForCache != null && !cachedBaseUrlForCache.isEmpty()) {
                cacheData.put("requestBaseUrl", cachedBaseUrlForCache);
            }

            // 🔑 关键修复：保留 encrypted 字段（如果已存在）
            // 如果现有缓存中有 encrypted 字段，保持不变
            Object currentEncrypted = cacheData.get("encrypted");
            if (currentEncrypted == null) {
                // 如果是新缓存，且转换结果中有 encrypted 信息，保留它
                Object encryptedFromResult = conversionResult.get("encrypted");
                if (encryptedFromResult != null) {
                    cacheData.put("encrypted", encryptedFromResult);
                    logger.info("🔒 保留 encrypted 字段 - FileId: {}, Encrypted: {}", fileId, encryptedFromResult);
                } else {
                    logger.info("⚠️ 转换结果中没有 encrypted 字段 - FileId: {}", fileId);
                }
            } else {
                logger.info("🔍 现有缓存已有 encrypted 字段，保持不变 - FileId: {}, Encrypted: {}", fileId, currentEncrypted);
            }

            // 🔑 关键修复：确保预览文件格式正确设置为源文件格式而不是目标格式
            // 从转换结果中获取原始文件格式
            String originalFileFormat = (String) conversionResult.get("originalFileType");
            if (originalFileFormat != null && !originalFileFormat.trim().isEmpty()) {
                cacheData.put("previewFileFormat", originalFileFormat);
            } else {
                // 如果无法获取原始文件格式，使用目标格式作为后备
                cacheData.put("previewFileFormat", targetFormat);
            }

            // 🔑 关键修复：确保原始文件路径和文件名正确设置
            // 从转换结果中获取原始文件路径和文件名
            String originalFilePath = (String) conversionResult.get("originalFilePath");
            String originalFileName = (String) conversionResult.get("originalFileName");

            if (originalFilePath != null && !originalFilePath.trim().isEmpty()) {
                cacheData.put("originalFilePath", originalFilePath);
            }

            if (originalFileName != null && !originalFileName.trim().isEmpty()) {
                cacheData.put("originalFileName", originalFileName);
            }

            // 4. 写入传统缓存键(主键)
            redisTemplate.opsForValue().set(
                    cacheKey,
                    cacheData,
                    Duration.ofSeconds(successTtl));

            logger.info("✅ 转换预览URL已更新到主缓存 - FileId: {}, Key: {}", fileId, cacheKey);

            // 5. 同时更新精细化缓存结构(如果需要)
            redisTemplate.opsForValue().set(
                    resultKey,
                    cacheData,
                    Duration.ofSeconds(successTtl));

            logger.info("✅ 转换预览URL已更新到结果缓存 - FileId: {}, Key: {}", fileId, resultKey);

        } catch (Exception e) {
            logger.error("❌ 更新预览URL到缓存失败 - FileId: {}, Format: {}, URL: {}",
                    fileId, targetFormat, previewUrl, e);
            // 不抛出异常,避免影响消息消费
        }
    }

    /**
     * 处理多页文件（文件夹结构）
     * 生成所有页面的预览URL并缓存
     */
    public void handleMultiPageFile(String fileId, String targetFormat,
            String pagesDirectory, int totalPages,
            Map<String, Object> conversionResult,
            long startTime) {
        try {
            logger.info("📑 开始处理多页文件 - FileId: {}, TotalPages: {}, Directory: {}",
                    fileId, totalPages, pagesDirectory);

            // 生成所有页面的URL（相对路径）
            Map<Integer, String> pageUrls = multiPageUrlService.generatePageUrls(fileId, pagesDirectory, totalPages);

            if (pageUrls.isEmpty()) {
                logger.error("❌ 未能生成任何页面URL - FileId: {}", fileId);
                return;
            }

            logger.info("✅ 成功生成{}个页面URL - FileId: {}", pageUrls.size(), fileId);

            // 计算总文件大小
            Long totalFileSize = calculateTotalFileSize(pagesDirectory, totalPages);

            // 🔑 关键修复:将多页信息更新到Redis缓存
            updateMultiPageCache(fileId, targetFormat, pageUrls, totalPages,
                    pagesDirectory, totalFileSize, conversionResult);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("🎉 多页文件处理完成 - FileId: {}, TotalPages: {}, Duration: {}ms",
                    fileId, totalPages, duration);

        } catch (Exception e) {
            logger.error("❌ 处理多页文件失败 - FileId: {}", fileId, e);
        }
    }

    /**
     * 计算所有页面的总文件大小
     */
    public Long calculateTotalFileSize(String pagesDirectory, int totalPages) {
        try {
            long totalSize = 0;
            File directory = new File(pagesDirectory);

            // ✅ 修复：页码从1开始
            for (int i = 1; i <= totalPages; i++) {
                String[] extensions = { "png", "jpg" };
                for (String ext : extensions) {
                    File pageFile = new File(directory, "page_" + i + "." + ext);
                    if (pageFile.exists()) {
                        totalSize += pageFile.length();
                        break;
                    }
                }
            }

            return totalSize > 0 ? totalSize : null;
        } catch (Exception e) {
            logger.warn("⚠️ 计算总文件大小失败: {}", pagesDirectory, e);
            return null;
        }
    }

    /**
     * 更新多页信息到Redis缓存
     */
    public void updateMultiPageCache(String fileId, String targetFormat,
            Map<Integer, String> pageUrls, int totalPages,
            String pagesDirectory, Long totalFileSize,
            Map<String, Object> conversionResult) {
        try {
            // 1. 构建缓存键
            String cacheKey = cacheUtils.buildCacheKey(fileId, targetFormat);
            String resultKey = cacheUtils.buildResultKey(fileId, targetFormat);

            logger.info("📝 开始更新多页缓存 - CacheKey: {}, TotalPages: {}", cacheKey, totalPages);

            // 2. 读取现有缓存数据
            Object existingData = redisTemplate.opsForValue().get(cacheKey);

            Map<String, Object> cacheData;
            if (existingData instanceof Map) {
                cacheData = previewCacheAssembler.buildMultiPageConvertCache((Map<String, Object>) existingData, pageUrls, totalPages, pagesDirectory, totalFileSize, targetFormat);
                logger.info("✅ 读取到现有缓存数据,准备更新");
            } else {
                cacheData = previewCacheAssembler.buildMultiPageConvertCache(conversionResult, pageUrls, totalPages, pagesDirectory, totalFileSize, targetFormat);
                logger.info("ℹ️ 未找到现有缓存,创建新缓存数据");
            }

            // 3. 更新多页信息
            cacheData.put("isMultiPage", true);
            cacheData.put("totalPages", totalPages);
            cacheData.put("pagesDirectory", pagesDirectory);
            cacheData.put("pageUrls", pageUrls);

            // 第一页作为默认预览URL
            if (pageUrls.containsKey(1)) {
                cacheData.put("previewUrl", pageUrls.get(1));
            }

            cacheData.put("previewFileSize", totalFileSize);
            cacheData.put("status", "SUCCESS");
            cacheData.put("cachedAt", System.currentTimeMillis());
            // 设置过期时间戳（TTL由服务控制）
            cacheData.put("expiresAt", System.currentTimeMillis() + Duration.ofSeconds(successTtl).toMillis());

            // 🔑 关键修复：保留 encrypted 字段（如果已存在）
            Object currentEncrypted = cacheData.get("encrypted");
            if (currentEncrypted == null) {
                Object encryptedFromResult = conversionResult.get("encrypted");
                if (encryptedFromResult != null) {
                    cacheData.put("encrypted", encryptedFromResult);
                    logger.info("🔒 多页缓存：保留 encrypted 字段 - FileId: {}, Encrypted: {}", fileId, encryptedFromResult);
                } else {
                    logger.info("⚠️ 多页缓存：转换结果中没有 encrypted 字段 - FileId: {}", fileId);
                }
            } else {
                logger.info("🔍 多页缓存：现有缓存已有 encrypted 字段 - FileId: {}, Encrypted: {}", fileId, currentEncrypted);
            }

            // 🔑 关键修复：确保预览文件格式正确设置为源文件格式而不是目标格式
            // 从转换结果中获取原始文件格式
            String originalFileFormat = (String) conversionResult.get("originalFileType");
            if (originalFileFormat != null && !originalFileFormat.trim().isEmpty()) {
                cacheData.put("previewFileFormat", originalFileFormat);
            } else {
                // 如果无法获取原始文件格式，使用目标格式作为后备
                cacheData.put("previewFileFormat", targetFormat);
            }

            // 🔑 关键修复：确保原始文件路径和文件名正确设置
            // 从转换结果中获取原始文件路径和文件名
            String originalFilePath = (String) conversionResult.get("originalFilePath");
            String originalFileName = (String) conversionResult.get("originalFileName");

            if (originalFilePath != null && !originalFilePath.trim().isEmpty()) {
                cacheData.put("originalFilePath", originalFilePath);
            }

            if (originalFileName != null && !originalFileName.trim().isEmpty()) {
                cacheData.put("originalFileName", originalFileName);
            }

            // 4. 写入Redis
            redisTemplate.opsForValue().set(
                    cacheKey,
                    cacheData,
                    Duration.ofSeconds(successTtl));

            logger.info("✅ 多页信息已更新到主缓存 - Key: {}, Pages: {}, TTL: {}小时",
                    cacheKey, totalPages, successTtl / 3600);

            // 5. 同时更新结果缓存
            redisTemplate.opsForValue().set(
                    resultKey,
                    cacheData,
                    Duration.ofSeconds(successTtl));

            logger.info("✅ 多页信息已更新到结果缓存 - Key: {}", resultKey);

            logger.info("🎉 多页缓存更新成功 - FileId: {}, TotalPages: {}, FirstPageURL: {}",
                    fileId, totalPages, pageUrls.get(1));

        } catch (Exception e) {
            logger.error("❌ 更新多页缓存失败 - FileId: {}", fileId, e);
        }
    }

    /**
     * 更新失败状态到Redis缓存
     * 确保客户端轮询时能获取到失败信息
     */
    public void updateFailedStatusToCache(String fileId, String targetFormat,
            String error, Object errorCode, String originalFileFormat) {
        try {
            // 1. 构建缓存键(与转换服务保持一致)
            String cacheKey = cacheUtils.buildCacheKey(fileId, targetFormat);
            String resultKey = cacheUtils.buildResultKey(fileId, targetFormat);

            logger.info("📝 开始更新失败状态到缓存 - CacheKey: {}, ResultKey: {}", cacheKey, resultKey);

            // 2. 读取现有缓存数据
            Object existingData = redisTemplate.opsForValue().get(cacheKey);

            Map<String, Object> cacheData = previewCacheAssembler.buildFailedSimpleConvertCache(
                fileId, targetFormat, error, errorCode, originalFileFormat);

            // 3. 更新为失败状态
            cacheData.put("mode", "CONVERT");
            cacheData.put("status", "FAILED");
            cacheData.put("error", error != null ? error : "Unknown error");
            // 🔑 关键修复：保存 errorCode 字段，供预览服务识别特定错误类型
            if (errorCode != null) {
                cacheData.put("errorCode", errorCode.toString());
                logger.info("📌 设置 errorCode - FileId: {}, ErrorCode: {}", fileId, errorCode);
            } else {
                cacheData.put("errorCode", "UNKNOWN");
            }
            cacheData.put("cachedAt", System.currentTimeMillis());
            cacheData.put("failedAt", System.currentTimeMillis());

            // 失败结果保留极短时间(与转换服务保持一致)
            cacheData.put("expiresAt", System.currentTimeMillis() + Duration.ofSeconds(failedTtl).toMillis());

            // 4. 写入主缓存键
            redisTemplate.opsForValue().set(
                    cacheKey,
                    cacheData,
                    Duration.ofSeconds(failedTtl));

            logger.info("✅ 失败状态已更新到主缓存 - Key: {}, Error: {}, TTL: {}s",
                    cacheKey, error, failedTtl);

            // 5. 同时更新结果缓存
            redisTemplate.opsForValue().set(
                    resultKey,
                    cacheData,
                    Duration.ofSeconds(failedTtl));

            logger.info("✅ 失败状态已更新到结果缓存 - Key: {}", resultKey);

            logger.info("🎉 失败状态缓存更新成功 - FileId: {}, Format: {}, Error: {}",
                    fileId, targetFormat, error);

        } catch (Exception e) {
            logger.error("❌ 更新失败状态到缓存失败 - FileId: {}, Format: {}",
                    fileId, targetFormat, e);
            // 不抛出异常,避免影响消息消费
        }
    }

     /**
     * 缓存直接预览结果
     * @param fileId 文件ID
     * @param previewInfo 预览信息
     */
    public void cacheDirectPreview(String fileId, PreviewCacheInfo previewInfo) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 缓存直接预览时fileId为空");
            return;
        }
        
        if (previewInfo == null) {
            logger.warn("⚠️ 缓存直接预览时previewInfo为空 - FileId: {}", fileId);
            return;
        }
         
        try {
            String cacheKey = cacheUtils.buildDirectPreviewKey(fileId);
            
            // 构建缓存数据（使用组装器）
            Map<String, Object> cacheData = previewCacheAssembler.buildDirectPreviewCache(previewInfo);
            cacheData.put("expiresAt", System.currentTimeMillis() + directPreviewTtl * 1000);
            
            // 写入Redis，设置TTL
            redisTemplate.opsForValue().set(cacheKey, cacheData, Duration.ofSeconds(directPreviewTtl));
            
            logger.info("✅ 直接预览结果已缓存 - FileId: {}, Key: {}", fileId, cacheKey);
            
        } catch (Exception e) {
            logger.error("❌ 缓存直接预览结果失败 - FileId: {}", fileId, e);
            // 缓存失败不抛出异常，不影响主流程
        }
    }
    
    /**
     * 清除直接预览缓存
     * 用于清除错误状态缓存（如PASSWORD_REQUIRED、PASSWORD_INCORRECT）
     * @param fileId 文件ID
     */
    public void clearDirectPreviewCache(String fileId) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 清除直接预览缓存时fileId为空");
            return;
        }
        
        try {
            String cacheKey = cacheUtils.buildDirectPreviewKey(fileId);
            Boolean deleted = redisTemplate.delete(cacheKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("✅ 清除直接预览缓存成功 - FileId: {}, Key: {}", fileId, cacheKey);
            } else {
                logger.debug("⚠️ 直接预览缓存不存在或已过期 - FileId: {}, Key: {}", fileId, cacheKey);
            }
            
        } catch (Exception e) {
            logger.error("❌ 清除直接预览缓存失败 - FileId: {}", fileId, e);
            // 清除失败不抛出异常，不影响主流程
        }
    }
    
    /**
     * 清除转换缓存（所有格式）
     * 用于清除错误状态缓存，强制重新转换
     * @param fileId 文件ID
     */
    public void clearConvertCache(String fileId) {
        // 参数验证
        if (fileId == null || fileId.trim().isEmpty()) {
            logger.warn("⚠️ 清除转换缓存时fileId为空");
            return;
        }       
        try {
            // 使用通配符查询所有转换缓存键
            String pattern = cacheUtils.buildCacheKey(fileId, "*");
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                logger.info("✅ 清除转换缓存成功 - FileId: {}, 清除键数: {}", fileId, deletedCount);
            } else {
                logger.debug("⚠️ 未找到转换缓存 - FileId: {}, Pattern: {}", fileId, pattern);
            }
            
        } catch (Exception e) {
            logger.error("❌ 清除转换缓存失败 - FileId: {}", fileId, e);
            // 清除失败不抛出异常，不影响主流程
        }
    }
    
    /**
     * 清除所有缓存（直接预览 + 转换缓存）
     * @param fileId 文件ID
     */
    public void clearAllCache(String fileId) {
        clearDirectPreviewCache(fileId);
        clearConvertCache(fileId);
    }
    

}
