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
package com.basemetas.fileview.preview.service.password;

import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import com.basemetas.fileview.preview.utils.PasswordEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * 密码解锁服务
 * 管理文件密码的验证、存储和检查
 */
@Service
public class PasswordUnlockService {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordUnlockService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private PasswordEncryptionUtil passwordEncryptionUtil;
    
    @Value("${preview.security.password.ttlSeconds:1800}")
    private long passwordTtlSeconds;
    
    /**
     * 检查客户端是否已解锁该文件
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @return 是否已解锁
     */
    public boolean isUnlocked(String fileId, String clientId) {
        String unlockKey = buildUnlockKey(fileId, clientId);
        Boolean exists = redisTemplate.hasKey(unlockKey);
        
        boolean unlocked = Boolean.TRUE.equals(exists);
        logger.debug("检查解锁状态 - FileId: {}, ClientId: {}, Unlocked: {}", fileId, clientId, unlocked);
        
        return unlocked;
    }
    
    /**
     * 标记客户端已解锁该文件
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @param password 密码（将被加密存储）
     */
    public void markUnlocked(String fileId, String clientId, String password) {
        String unlockKey = buildUnlockKey(fileId, clientId);
        String passwordKey = buildPasswordKey(fileId, clientId);
        
        // 存储解锁状态
        redisTemplate.opsForValue().set(unlockKey, "1", passwordTtlSeconds, TimeUnit.SECONDS);
        
        // 加密并存储密码
        String encryptedPassword = passwordEncryptionUtil.encrypt(password);
        redisTemplate.opsForValue().set(passwordKey, encryptedPassword, passwordTtlSeconds, TimeUnit.SECONDS);
        
        logger.info("标记解锁成功 - FileId: {}, ClientId: {}, TTL: {}s", fileId, clientId, passwordTtlSeconds);
    }
    
    /**
     * 获取已存储的密码（解密后）
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     * @return 密码明文，如果未找到返回 null
     */
    public String getPassword(String fileId, String clientId) {
        String passwordKey = buildPasswordKey(fileId, clientId);
        Object encryptedPassword = redisTemplate.opsForValue().get(passwordKey);
        
        if (encryptedPassword == null) {
            logger.debug("未找到存储的密码 - FileId: {}, ClientId: {}", fileId, clientId);
            return null;
        }
        
        try {
            String password = passwordEncryptionUtil.decrypt(encryptedPassword.toString());
            logger.debug("成功获取存储的密码 - FileId: {}, ClientId: {}", fileId, clientId);
            return password;
        } catch (Exception e) {
            logger.error("密码解密失败 - FileId: {}, ClientId: {}", fileId, clientId, e);
            return null;
        }
    }
    
    /**
     * 清除解锁状态
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     */
    public void clearUnlock(String fileId, String clientId) {
        String unlockKey = buildUnlockKey(fileId, clientId);
        String passwordKey = buildPasswordKey(fileId, clientId);
        
        redisTemplate.delete(unlockKey);
        redisTemplate.delete(passwordKey);
        
        logger.info("清除解锁状态 - FileId: {}, ClientId: {}", fileId, clientId);
    }
    
    /**
     * 刷新解锁状态的TTL（可选，用于滑动窗口）
     * 
     * @param fileId 文件ID
     * @param clientId 客户端ID
     */
    public void refreshUnlock(String fileId, String clientId) {
        String unlockKey = buildUnlockKey(fileId, clientId);
        String passwordKey = buildPasswordKey(fileId, clientId);
        
        redisTemplate.expire(unlockKey, passwordTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(passwordKey, passwordTtlSeconds, TimeUnit.SECONDS);
        
        logger.debug("刷新解锁TTL - FileId: {}, ClientId: {}", fileId, clientId);
    }
    
    /**
     * 构建解锁状态键
     */
    private String buildUnlockKey(String fileId, String clientId) {
        return CacheKeyManager.buildUnlockKey(fileId, clientId);
    }
    
    /**
     * 构建密码存储键
     */
    private String buildPasswordKey(String fileId, String clientId) {
        return CacheKeyManager.buildPasswordKey(fileId, clientId);
    }
}
