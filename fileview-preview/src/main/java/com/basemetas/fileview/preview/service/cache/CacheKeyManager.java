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

/**
 * Redis 缓存键统一管理类
 * 集中维护所有 Redis 缓存键的前缀和构建方法
 * 
 * @author 夫子
 */
public class CacheKeyManager {
    
    // ========================================
    // 预览相关缓存键
    // ========================================
    
    /**
     * 转换服务的缓存键前缀 (保持与转换服务一致)
     */
    public static final String CONVERT_CACHE_PREFIX = "convert:";
    
    /**
     * 转换结果后缀
     */
    public static final String RESULT_SUFFIX = ":result";
    
    /**
     * 直接预览缓存键前缀
     */
    public static final String DIRECT_PREVIEW_PREFIX = "preview:direct:";
    
    /**
     * EPUB 解压缓存键前缀
     */
    public static final String EPUB_EXTRACT_CACHE_PREFIX = "epub:extract:";
    
    // ========================================
    // 下载相关缓存键
    // ========================================
    
    /**
     * 文件内容哈希前缀
     */
    public static final String FILE_HASH_PREFIX = "file_hash:";
    
    /**
     * 文件路径映射前缀
     */
    public static final String FILE_PATH_MAPPING_PREFIX = "file_path:";
    
    /**
     * 下载任务前缀
     */
    public static final String DOWNLOAD_TASK_PREFIX = "download_task:";
    
    /**
     * ETag 缓存前缀
     */
    public static final String DOWNLOAD_ETAG_PREFIX = "download_etag:";
    
    /**
     * Last-Modified 缓存前缀
     */
    public static final String DOWNLOAD_LAST_MODIFIED_PREFIX = "download_last_modified:";
    
    // ========================================
    // 密码解锁相关缓存键
    // ========================================
    
    /**
     * 解锁状态键前缀
     */
    public static final String UNLOCK_KEY_PREFIX = "unlock:preview:";
    
    /**
     * 密码存储键前缀
     */
    public static final String PASSWORD_KEY_PREFIX = "unlock:preview:pwd:";
    
    // ========================================
    // 预览相关缓存键构建方法
    // ========================================
    
    /**
     * 构建转换缓存键
     * 
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @return 缓存键
     */
    public static String buildConvertCacheKey(String fileId, String targetFormat) {
        return CONVERT_CACHE_PREFIX + fileId + ":" + targetFormat.toLowerCase();
    }
    
    /**
     * 构建转换结果缓存键
     * 
     * @param fileId 文件ID
     * @param targetFormat 目标格式
     * @return 缓存键
     */
    public static String buildConvertResultKey(String fileId, String targetFormat) {
        return CONVERT_CACHE_PREFIX + fileId + ":" + targetFormat.toLowerCase() + RESULT_SUFFIX;
    }
    
    /**
     * 构建直接预览缓存键
     * 
     * @param fileId 文件ID
     * @return 缓存键
     */
    public static String buildDirectPreviewKey(String fileId) {
        return DIRECT_PREVIEW_PREFIX + fileId;
    }
    
    /**
     * 构建 EPUB 解压缓存键
     * 
     * @param fileId 文件ID
     * @return 缓存键
     */
    public static String buildEpubExtractKey(String fileId) {
        return EPUB_EXTRACT_CACHE_PREFIX + fileId;
    }
    
    // ========================================
    // 下载相关缓存键构建方法
    // ========================================
    
    /**
     * 构建文件哈希缓存键
     * 
     * @param fileHash 文件哈希值
     * @return 缓存键
     */
    public static String buildFileHashKey(String fileHash) {
        return FILE_HASH_PREFIX + fileHash;
    }
    
    /**
     * 构建文件路径映射缓存键
     * 
     * @param urlHash URL哈希值
     * @return 缓存键
     */
    public static String buildFilePathMappingKey(String urlHash) {
        return FILE_PATH_MAPPING_PREFIX + urlHash;
    }
    
    /**
     * 构建下载任务缓存键
     * 
     * @param urlHash URL哈希值
     * @return 缓存键
     */
    public static String buildDownloadTaskKey(String urlHash) {
        return DOWNLOAD_TASK_PREFIX + urlHash;
    }
    
    /**
     * 构建 ETag 缓存键
     * 
     * @param urlHash URL哈希值
     * @return 缓存键
     */
    public static String buildEtagKey(String urlHash) {
        return DOWNLOAD_ETAG_PREFIX + urlHash;
    }
    
    /**
     * 构建 Last-Modified 缓存键
     * 
     * @param urlHash URL哈希值
     * @return 缓存键
     */
    public static String buildLastModifiedKey(String urlHash) {
        return DOWNLOAD_LAST_MODIFIED_PREFIX + urlHash;
    }
    
    // ========================================
    // 密码解锁相关缓存键构建方法
    // ========================================
    
    /**
     * 构建解锁状态键
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @return 缓存键
     */
    public static String buildUnlockKey(String fileId, String clientId) {
        return UNLOCK_KEY_PREFIX + fileId + ":" + clientId;
    }
    
    /**
     * 构建密码存储键
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @return 缓存键
     */
    public static String buildPasswordKey(String fileId, String clientId) {
        return PASSWORD_KEY_PREFIX + fileId + ":" + clientId;
    }
    
    // ========================================
    // 私有构造函数（工具类不允许实例化）
    // ========================================
    
    private CacheKeyManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
