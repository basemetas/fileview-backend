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

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;

/**
 * ClientId 提取工具类
 * 从请求头或查询参数中提取 clientId
 */
@Component
public class ClientIdExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientIdExtractor.class);
    
    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final String PARAM_CLIENT_ID = "clientId";
    
    /**
     * 从请求中提取 clientId
     * 优先从请求头读取,其次从查询参数读取
     * 
     * @param request HTTP请求
     * @return clientId，如果未找到返回 null
     */
    public String extractClientId(HttpServletRequest request) {
        // 1. 尝试从请求头获取
        String clientId = request.getHeader(HEADER_CLIENT_ID);
        
        if (clientId != null && !clientId.trim().isEmpty()) {
            logger.debug("从请求头获取 clientId: {}", clientId);
            return clientId.trim();
        }
        
        // 2. 尝试从查询参数获取
        clientId = request.getParameter(PARAM_CLIENT_ID);
        
        if (clientId != null && !clientId.trim().isEmpty()) {
            logger.debug("从查询参数获取 clientId: {}", clientId);
            return clientId.trim();
        }
        
        logger.warn("未找到 clientId - 请求路径: {}", request.getRequestURI());
        return null;
    }
    
    /**
     * 验证 clientId 格式
     * 放宽验证规则：只要是非空字符串且长度合理即可
     * 支持 UUIDv4、自定义字符串等多种格式
     * 
     * @param clientId 客户端ID
     * @return 是否有效
     */
    public boolean isValidClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = clientId.trim();
        
        // 验证长度：至少 6 个字符，最多 128 个字符
        if (trimmed.length() < 6 || trimmed.length() > 128) {
            logger.warn("clientId 长度不合理: {} (长度: {})", clientId, trimmed.length());
            return false;
        }
        
        // 验证字符集：只允许字母、数字、连字符、下划线
        if (!trimmed.matches("^[a-zA-Z0-9\\-_]+$")) {
            logger.warn("clientId 包含非法字符: {}", clientId);
            return false;
        }
        
        // 如果是 UUID 格式，记录日志但不强制要求
        if (trimmed.length() == 36 && trimmed.charAt(8) == '-' && trimmed.charAt(13) == '-') {
            logger.debug("clientId 使用 UUID 格式: {}", clientId);
        } else {
            logger.debug("clientId 使用自定义格式: {}", clientId);
        }
        
        return true;
    }
    
    /**
     * 从请求中提取并验证 clientId
     * 
     * @param request HTTP请求
     * @return clientId，如果无效返回 null
     */
    public String extractAndValidateClientId(HttpServletRequest request) {
        String clientId = extractClientId(request);
        
        if (clientId == null) {
            return null;
        }
        
        if (!isValidClientId(clientId)) {
            logger.warn("无效的 clientId 格式: {}", clientId);
            return null;
        }
        
        return clientId;
    }

    /**
     * 从 HTTP 请求中提取 clientId 并设置到请求对象中
     * 优先级：请求体 > 请求头 > 查询参数
     * 
     * @param request 预览请求对象
     * @param httpRequest HTTP请求
     */
    public void extractAndSetClientId(FilePreviewRequest request, HttpServletRequest httpRequest) {
        String clientId = request.getClientId();
        
        // 如果请求体中没有 clientId，尝试从请求头读取
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = httpRequest.getHeader(HEADER_CLIENT_ID);
            logger.debug("🔑 从请求头读取 clientId: {}", clientId);
        }
        
        // 如果请求头也没有，尝试从查询参数读取（容错机制）
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = httpRequest.getParameter(PARAM_CLIENT_ID);
            logger.debug("🔑 从查询参数读取 clientId: {}", clientId);
        }
        
        // 设置到请求对象中
        if (clientId != null && !clientId.trim().isEmpty()) {
            request.setClientId(clientId);
            logger.info("🎯 设置 clientId: {}", clientId);
        } else {
            logger.debug("⚠️ 未提供 clientId，将无法使用密码解锁功能");
        }
    }
}
