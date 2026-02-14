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
package com.basemetas.fileview.preview.service.url;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 预览URL生成服务
 * 负责生成所有预览相关的URL（单文件预览、单页预览等）
 */
@Service
public class PreviewUrlService {
    private static final Logger logger = LoggerFactory.getLogger(PreviewUrlService.class);

    @Autowired
    private BaseUrlProvider baseUrlProvider;

    /**
     * 生成预览URL（相对地址）
     * 
     * 重要：此方法会对文件路径进行URL编码，确保URL格式正确
     * 注意：返回相对地址，由响应层根据请求上下文拼接 baseUrl
     * 
     * @param fileId   文件ID
     * @param filePath 文件路径（会被URL编码）
     * @return 预览URL相对路径，格式：/preview/api/files/{fileId}?filePath={encodedPath}&t={timestamp}
     * 
     * 编码规则：
     * - 使用 UTF-8 编码
     * - 空格编码为 + 或 %20
     * - 中文字符编码为 %XX%XX 格式
     * - 特殊字符（&, =, ?等）会被正确转义
     */
    public String generatePreviewUrl(String fileId, String filePath) {
        return generateRelativePreviewUrl(fileId, filePath);
    }

    /**
     * 生成相对路径预览URL（内部方法）
     * 
     * @param fileId   文件ID
     * @param filePath 文件路径（会被URL编码）
     * @return 相对路径URL
     */
    private String generateRelativePreviewUrl(String fileId, String filePath) {
        try {
            // 参数验证
            if (filePath == null || filePath.trim().isEmpty()) {
                logger.warn("⚠️ 文件路径为空，生成默认预览URL - FileId: {}", fileId);
                return "/preview/api/files/" + fileId + "?t=" + System.currentTimeMillis();
            }

            // 使用URLEncoder进行URL编码，与转换服务保持一致
            String encodedFilePath = java.net.URLEncoder.encode(filePath, java.nio.charset.StandardCharsets.UTF_8);

            return "/preview/api/files/" + fileId +
                    "?filePath=" + encodedFilePath +
                    "&t=" + System.currentTimeMillis();

        } catch (Exception e) {
            logger.error("❌ URL编码失败 - FileId: {}, FilePath: {}", fileId, filePath, e);
            // 降级处理：返回默认URL，不抛出异常
            return "/preview/api/files/" + fileId + "?t=" + System.currentTimeMillis();
        }
    }

    /**
     * 生成单页预览URL（相对地址）
     * 
     * @param fileId 文件ID
     * @param pageNum 页码
     * @param pageFilePath 页面文件路径
     * @return 页面预览URL相对路径
     */
    public String generatePageUrl(String fileId, int pageNum, String pageFilePath) {
        try {
            return "/preview/api/files/" + fileId +
                    "/page/" + pageNum +
                    "?path=" + java.net.URLEncoder.encode(pageFilePath, java.nio.charset.StandardCharsets.UTF_8) +
                    "&t=" + System.currentTimeMillis();
        } catch (Exception e) {
            logger.error("❌ URL编码失败 - FileId: {}, Page: {}", fileId, pageNum, e);
            return "/preview/api/files/" + fileId + "/page/" + pageNum;
        }
    }
}
