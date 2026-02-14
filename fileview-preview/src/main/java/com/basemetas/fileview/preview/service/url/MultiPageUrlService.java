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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.service.storage.FileStorageService;
import com.basemetas.fileview.preview.service.storage.impl.LocalFileStorageService;

/**
 * 负责生成多页文件的页面URL列表。
 */
@Component
public class MultiPageUrlService {
    private static final Logger logger = LoggerFactory.getLogger(MultiPageUrlService.class);

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private BaseUrlProvider baseUrlProvider;

    /**
     * 生成所有页面的预览URL（相对地址）
     * 注意：返回相对路径，由响应层根据请求上下文拼接 baseUrl
     * 
     * @param fileId 文件ID
     * @param pagesDirectory 页面目录
     * @param totalPages 总页数
     * @return 页码 -> 相对路径URL 的映射表
     */
    public Map<Integer, String> generatePageUrls(String fileId, String pagesDirectory, int totalPages) {
        Map<Integer, String> pageUrls = new HashMap<>();
        try {
            File directory = new File(pagesDirectory);
            if (!directory.exists() || !directory.isDirectory()) {
                logger.error("❌ 页面目录不存在: {}", pagesDirectory);
                return pageUrls;
            }

            // 遍历所有页面，页码从1开始（对应page_1.png, page_2.png, ...）
            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                String[] extensions = {"png", "jpg"};
                String pageFileName = null;

                for (String ext : extensions) {
                    String testFileName = "page_" + pageNum + "." + ext;
                    File pageFile = new File(directory, testFileName);
                    if (pageFile.exists()) {
                        pageFileName = testFileName;
                        break;
                    }
                }

                if (pageFileName != null) {
                    String pageFilePath = pagesDirectory + File.separator + pageFileName;

                    String pageUrl = null;
                    try {
                        if (fileStorageService.fileExists(pageFilePath)) {
                            // 直接获取相对路径 URL
                            pageUrl = fileStorageService.getFileUrl(pageFilePath, fileId);
                        }
                    } catch (Exception e) {
                        logger.warn("⚠️ 存储服务生成URL失败，使用降级方案: {}", pageFilePath);
                    }

                    // 如果存储服务未生成URL,使用降级方案（相对路径）
                    if (pageUrl == null || pageUrl.trim().isEmpty()) {
                        pageUrl = "/preview/api/files/" + fileId +
                                "/page/" + pageNum +
                                "?path=" + java.net.URLEncoder.encode(pageFilePath, java.nio.charset.StandardCharsets.UTF_8) +
                                "&t=" + System.currentTimeMillis();
                    }

                    pageUrls.put(pageNum, pageUrl);
                    logger.debug("📝 页面URL生成 - FileId: {}, Page: {}, URL: {}", fileId, pageNum, pageUrl);
                } else {
                    logger.warn("⚠️ 页面文件不存在 - FileId: {}, Page: {}", fileId, pageNum);
                }
            }
        } catch (Exception e) {
            logger.error("❌ 生成页面URL失败 - FileId: {}", fileId, e);
        }
        return pageUrls;
    }
}
