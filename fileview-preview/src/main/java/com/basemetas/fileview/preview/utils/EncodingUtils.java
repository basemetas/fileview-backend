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

import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 编码工具类
 * 
 * 处理文件路径和文件名的中文编码问题
 * 
 * @author 夫子
 * @version 1.0
 */
@Component
public class EncodingUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(EncodingUtils.class);
    
    // ========== URL解码方法 ==========
    
    /**
     * 解码URL编码的字符串（如果需要）
     * 
     * @param value 待解码的字符串
     * @return 解码后的字符串，如果解码失败则返回原字符串
     */
    public static String decodeIfNeeded(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        
        try {
            // 检查是否包含URL编码字符（%xx格式）
            if (value.contains("%")) {
                String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                logger.debug("URL解码: {} -> {}", value, decoded);
                return decoded;
            }
            return value;
        } catch (Exception e) {
            logger.warn("URL解码失败，返回原字符串: {}", value, e);
            return value;
        }
    }
    
    /**
     * 强制解码URL编码的字符串
     * 
     * @param value 待解码的字符串
     * @return 解码后的字符串
     */
    public static String forceDecode(String value) {
        if (value == null) {
            return null;
        }
        
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("URL解码失败: {}", value, e);
            return value;
        }
    }
    
    // ========== Request对象编码处理 ==========
    
    /**
     * 处理Request对象中的中文编码
     * 自动解码 filePath 和 targetFileName
     * 
     * @param request 转换请求对象
     */
    public static void processRequestEncoding(FilePreviewRequest request) {
        if (request == null) {
            return;
        }
        try {
            // 处理文件路径
            if (request.getSrcRelativePath() != null) {
                String decodedPath = decodeIfNeeded(request.getSrcRelativePath());
                if (!decodedPath.equals(request.getSrcRelativePath())) {
                    request.setSrcRelativePath(decodedPath);
                    logger.debug("文件路径已解码");
                }
            }
            
            // 处理文件名
            if (request.getFileName() != null) {
                String decodedName = decodeIfNeeded(request.getFileName());
                if (!decodedName.equals(request.getFileName())) {
                    request.setFileName(decodedName);
                    logger.debug("文件名已解码");
                }
            }
            
            // 处理目标文件路径
            if (request.getTargetPath() != null) {
                String decodedTargetPath = decodeIfNeeded(request.getTargetPath());
                if (!decodedTargetPath.equals(request.getTargetPath())) {
                    request.setTargetPath(decodedTargetPath);
                    logger.debug("目标路径已解码");
                }
            }
            
            // 处理目标文件名
            if (request.getTargetFileName() != null) {
                String decodedTargetName = decodeIfNeeded(request.getTargetFileName());
                if (!decodedTargetName.equals(request.getTargetFileName())) {
                    request.setTargetFileName(decodedTargetName);
                    logger.debug("目标文件名已解码");
                }
            }
        } catch (Exception e) {
            logger.warn("处理请求编码时出现异常", e);
        }
    }
    
    // ========== 批量编码处理 ==========
    
    /**
     * 批量解码字符串数组
     * 
     * @param values 待解码的字符串数组
     * @return 解码后的字符串数组
     */
    public static String[] decodeArray(String... values) {
        if (values == null || values.length == 0) {
            return values;
        }
        
        String[] decodedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            decodedValues[i] = decodeIfNeeded(values[i]);
        }
        
        return decodedValues;
    }
    
    // ========== 编码检测 ==========
    
    /**
     * 检查字符串是否已经URL编码
     * 
     * @param value 待检查的字符串
     * @return true-已编码, false-未编码
     */
    public static boolean isUrlEncoded(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // 简单检测：包含%xx格式的字符
        return value.contains("%") && value.matches(".*%[0-9A-Fa-f]{2}.*");
    }
    
    /**
     * 检查字符串是否包含中文字符
     * 
     * @param value 待检查的字符串
     * @return true-包含中文, false-不包含中文
     */
    public static boolean containsChinese(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // 检测中文字符（Unicode范围：\u4e00-\u9fa5）
        return value.matches(".*[\u4e00-\u9fa5]+.*");
    }
    
    // ========== 编码转换 ==========
    
    /**
     * 将字符串从ISO-8859-1转换为UTF-8
     * （处理某些框架默认使用ISO-8859-1编码的情况）
     * 
     * @param value 原始字符串
     * @return 转换后的字符串
     */
    public static String convertToUtf8(String value) {
        if (value == null) {
            return null;
        }
        
        try {
            byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("编码转换失败: {}", value, e);
            return value;
        }
    }

    /**
     * 计算字符串的MD5哈希值
     */
    public String calculateMD5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 转为16进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // 只取前16位
            return hexString.substring(0, 16);

        } catch (Exception e) {
            logger.error("❌ MD5计算失败", e);
            // 降级：使用hashCode
            return String.format("%08x", input.hashCode());
        }
    } 
}
