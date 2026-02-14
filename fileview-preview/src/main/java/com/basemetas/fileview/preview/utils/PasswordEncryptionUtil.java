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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * 使用 AES-256-GCM 对密码进行加密存储
 */
@Component
public class PasswordEncryptionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordEncryptionUtil.class);
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    @Value("${preview.security.password.crypto.secret:preview-default-secret-key-change-in-production}")
    private String secretKey;
    
    /**
     * 加密密码
     * @param password 明文密码
     * @return Base64编码的加密密文
     */
    public String encrypt(String password) {
        try {
            // 生成密钥
            SecretKey key = getSecretKey();
            
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            // 加密
            byte[] cipherText = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            
            // 合并 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            
            // Base64编码
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            logger.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
    }
    
    /**
     * 解密密码
     * @param encryptedPassword Base64编码的加密密文
     * @return 明文密码
     */
    public String decrypt(String encryptedPassword) {
        try {
            // Base64解码
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedPassword);
            
            // 分离 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            // 生成密钥
            SecretKey key = getSecretKey();
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            // 解密
            byte[] plainText = cipher.doFinal(cipherText);
            
            return new String(plainText, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            logger.error("密码解密失败", e);
            throw new RuntimeException("密码解密失败", e);
        }
    }
    
    /**
     * 生成密钥
     */
    private SecretKey getSecretKey() {
        try {
            // 使用配置的密钥生成固定的256位密钥
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            byte[] keyHash = new byte[32]; // 256 bits
            
            // 简单的密钥派生（生产环境建议使用 PBKDF2）
            System.arraycopy(keyBytes, 0, keyHash, 0, Math.min(keyBytes.length, 32));
            
            return new SecretKeySpec(keyHash, "AES");
            
        } catch (Exception e) {
            logger.error("生成密钥失败", e);
            throw new RuntimeException("生成密钥失败", e);
        }
    }
}
