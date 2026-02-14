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
package com.basemetas.fileview.preview.controller;

import com.basemetas.fileview.preview.model.PreviewCacheInfo;
import com.basemetas.fileview.preview.service.cache.CacheReadService;
import com.basemetas.fileview.preview.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 资源访问控制器
 * 
 * EPUB 文件是特殊的 ZIP 压缩包，内部包含：
 * - META-INF/container.xml：容器信息
 * - *.opf：包文件（Package File）
 * - *.html, *.xhtml：内容文件
 * - *.css, *.jpg, *.png：样式和图片资源
 * 
 * 前端 EPUB 阅读器（如 epub.js）需要通过 HTTP 访问这些内部资源
 * 
 * 解决方案：
 * 1. 统一资源访问接口（自动解压 + 资源访问）
 * 2. 首次访问时自动解压到临时目录
 * 3. 后续访问直接返回已解压的资源（避免重复解压）
 * 4. 避免 Spring Security 对 META-INF 路径的拦截
 * 
 * 使用方式（前端）：
 * 
 * 方式1：GET请求（适用于epub.js等阅读器）
 * ```javascript
 * const book = ePub(`/preview/epub/${fileId}/`);
 * // epub.js会自动使用GET请求加载资源
 * ```
 * 
 * 方式2：POST请求（适用于自定义调用）
 * ```javascript
 * const response = await fetch('/preview/epub/resource', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({
 *     fileId: 'preview_xxx',
 *     resourcePath: 'META-INF/container.xml'
 *   })
 * });
 * ```
 * 
 * 安全机制：
 * 1. 文件ID验证（防止恶意构造）
 * 2. 路径遍历防护（防止访问系统文件）
 * 3. 文件大小限制（防止解压炸弹）
 * 4. 文件类型白名单（只允许EPUB相关资源）
 * 5. EPUB文件路径白名单验证（只能访问fileTemp目录）
 * 6. 分布式缓存（Redis）- 支持多实例共享
 * 7. 自动去重解压（已解压的不重复解压）
 * 
 * @author 夫子
 */
@RestController
@RequestMapping("/preview/api/epub")
public class EpubResourceController {

    private static final Logger logger = LoggerFactory.getLogger(EpubResourceController.class);

    @Autowired
    private CacheReadService cacheReadService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // EPUB 解压临时目录
    @Value("${fileview.preview.storage.uncompress-dir:../fileTemp/source/uncompress}")
    private String uncompressDir;

    // 允许的EPUB文件根目录（安全白名单）
    @Autowired
    private FileUtils fileUtils;

    // Redis 缓存键前缀：epub:extract:{fileId} -> 解压目录路径
    // 使用 CacheKeyManager 统一管理

    // EPUB 解压缓存有效期（24小时）
    private static final long EPUB_CACHE_TTL = 86400L;

    // 文件ID验证正则（只允许字母、数字、下划线、短横线）
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    // 单个文件大小限制（100MB）
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    // 解压后总大小限制（500MB，防止解压炸弹）
    private static final long MAX_EXTRACTED_SIZE = 500 * 1024 * 1024;

    // EPUB资源文件扩展名白名单
    private static final String[] ALLOWED_EXTENSIONS = {
        ".xml", ".opf", ".ncx", ".html", ".xhtml", ".htm",
        ".css", ".js", ".jpg", ".jpeg", ".png", ".gif", ".svg", ".webp",
        ".ttf", ".woff", ".woff2", ".otf", ".eot",
        ".mp3", ".mp4", ".webm", ".ogg"
    };
    
    // EPUB特殊文件名白名单（无扩展名的文件）
    private static final String[] ALLOWED_FILENAMES = {
        "mimetype", "container.xml", "content.opf", "toc.ncx"
    };

    /**
     * EPUB资源请求DTO（用于JSON请求）
     */
    public static class EpubResourceRequest {
        private String fileId;
        private String resourcePath;
        
        public String getFileId() {
            return fileId;
        }
        
        public void setFileId(String fileId) {
            this.fileId = fileId;
        }
        
        public String getResourcePath() {
            return resourcePath;
        }
        
        public void setResourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
        }
    }

    /**
     * 访问 EPUB 资源（POST方式 - JSON请求体）
     * 
     * 功能：
     * 1. 首次访问时自动解压 EPUB 文件
     * 2. 后续访问直接返回已解压的资源
     * 3. 支持访问所有 EPUB 内部资源
     * 
     * URL 格式: POST /preview/epub/resource
     * Content-Type: application/json
     * 请求体：{"fileId": "xxx", "resourcePath": "xxx"}
     * 
     * @param request JSON请求体
     * @return 资源内容
     */
    @PostMapping("/resource")
    public ResponseEntity<Resource> getEpubResourceByPost(@RequestBody EpubResourceRequest request) {
        
        // 参数验证
        if (request.getFileId() == null || request.getFileId().trim().isEmpty()) {
            logger.warn("⚠️ POST请求缺少fileId参数");
            return ResponseEntity.badRequest().build();
        }
        if (request.getResourcePath() == null || request.getResourcePath().trim().isEmpty()) {
            logger.warn("⚠️ POST请求缺少resourcePath参数 - FileId: {}", request.getFileId());
            return ResponseEntity.badRequest().build();
        }
        
        return getEpubResource(request.getFileId(), request.getResourcePath());
    }
    
    /**
     * 处理CORS预检请求（OPTIONS）
     */
    @RequestMapping(method = RequestMethod.OPTIONS, value = "/**")
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*")
                .header(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
                .build();
    }

    /**
     * 访问 EPUB 资源（GET方式 - 用于epub.js等阅读器）
     * 
     * URL 格式: GET /preview/epub/{fileId}/**
     * 例如: /preview/epub/preview_xxx/META-INF/container.xml
     * 
     * @param fileId 文件ID
     * @return 资源内容
     */
    @GetMapping("/{fileId}/**")
    public ResponseEntity<Resource> getEpubResourceByGet(
            @PathVariable String fileId,
            HttpServletRequest request) {
        
        // 从URL路径中提取资源路径
        String requestPath = request.getRequestURI();
        String prefix = "/preview/api/epub/" + fileId;
        
        if (!requestPath.startsWith(prefix)) {
            logger.warn("⚠️ 无效的请求路径: {}", requestPath);
            return ResponseEntity.badRequest().build();
        }
        
        // 提取资源路径（去除前缀和开头的斜杠）
        String resourcePath = requestPath.substring(prefix.length());
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        
        // 如果资源路径为空，默认返回 container.xml（EPUB 的入口文件）
        if (resourcePath.isEmpty()) {
            logger.info("📖 访问 EPUB 基础URL，默认返回 container.xml - FileId: {}", fileId);
            resourcePath = "META-INF/container.xml";
        }
        
        try {
            resourcePath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("⚠️ 资源路径解码失败: {}", resourcePath, e);
            return ResponseEntity.badRequest().build();
        }
        
        return getEpubResource(fileId, resourcePath);
    }

    /**
     * EPUB资源访问核心方法（被POST和GET方法共享）
     */
    private ResponseEntity<Resource> getEpubResource(String fileId, String resourcePath) {

        try {
            // 1. 验证文件ID格式
            if (!isValidFileId(fileId)) {
                logger.warn("🚫 非法的文件ID格式: {}", fileId);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 2. 确保EPUB已解压（如果未解压则自动解压）
            String extractedPath = ensureEpubExtracted(fileId);
            if (extractedPath == null) {
                logger.warn("⚠️ EPUB解压失败 - FileId: {}", fileId);
                return ResponseEntity.notFound()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 3. 获取并验证资源路径
            if (resourcePath == null || resourcePath.trim().isEmpty()) {
                logger.warn("⚠️ 资源路径为空 - FileId: {}", fileId);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }
            
            // URL解码
            String decodedResourcePath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
            
            logger.debug("📖 EPUB资源请求 - FileId: {}, Resource: {}", fileId, decodedResourcePath);

            // 4. 安全检查：防止路径遍历
            if (decodedResourcePath.contains("..") || decodedResourcePath.startsWith("/")) {
                logger.warn("🚫 不安全的资源路径: {}", decodedResourcePath);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 5. 构建完整路径
            Path targetFile = Paths.get(extractedPath, decodedResourcePath).normalize();
            Path baseDir = Paths.get(extractedPath).normalize();
            
            // 6. 再次安全检查：确保路径在基准目录内
            if (!targetFile.startsWith(baseDir)) {
                logger.warn("🚫 检测到路径遍历攻击尝试: {}", decodedResourcePath);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 7. 检查文件是否存在
            if (!Files.exists(targetFile)) {
                logger.warn("⚠️ 资源不存在: {}", decodedResourcePath);
                return ResponseEntity.notFound()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 8. 检查是否为文件（不允许访问目录）
            if (Files.isDirectory(targetFile)) {
                logger.warn("🚫 尝试访问目录: {}", decodedResourcePath);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 9. 文件类型白名单检查
            if (!isAllowedFileType(decodedResourcePath)) {
                logger.warn("🚫 不允许的文件类型: {}", decodedResourcePath);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 10. 检查文件大小
            long fileSize = Files.size(targetFile);
            if (fileSize > MAX_FILE_SIZE) {
                logger.warn("🚫 文件过大: {} ({} bytes)", decodedResourcePath, fileSize);
                return ResponseEntity.badRequest()
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .build();
            }

            // 11. 创建资源
            Resource resource = new UrlResource(targetFile.toUri());

            // 12. 确定 Content-Type
            String contentType = determineContentType(decodedResourcePath);

            logger.info("✅ EPUB资源访问成功 - FileId: {}, Resource: {}, Size: {} bytes",
                    fileId, decodedResourcePath, fileSize);

            // 13. 返回资源（完整的CORS支持）
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    // CORS 头（完整配置）
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Type, Content-Length, Content-Disposition")
                    .header(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
                    .body(resource);

        } catch (Exception e) {
            logger.error("💥 EPUB资源访问异常 - FileId: {}", fileId, e);
            return ResponseEntity.internalServerError()
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .build();
        }
    }

    /**
     * 确保 EPUB 已解压（如果未解压则自动解压）
     * 
     * @param fileId 文件ID
     * @return 解压后的目录路径，失败返回 null
     */
    private synchronized String ensureEpubExtracted(String fileId) {

        try {
            // 1. 检查 Redis 缓存：如果已解压，直接返回
            String cacheKey = CacheKeyManager.buildEpubExtractKey(fileId);
            Object cachedPathObj = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedPathObj != null) {
                String cachedPath = cachedPathObj.toString();
                // 验证目录是否仍然存在
                if (Files.exists(Paths.get(cachedPath))) {
                    logger.debug("📦 使用Redis缓存的解压路径 - FileId: {}, Path: {}", fileId, cachedPath);
                    return cachedPath;
                } else {
                    logger.warn("⚠️ Redis缓存的解压目录已不存在，重新解压 - FileId: {}", fileId);
                    redisTemplate.delete(cacheKey);
                }
            }

            // 2. 从预览缓存获取原始EPUB文件路径
            PreviewCacheInfo cacheInfo = cacheReadService.getCachedResult(fileId);
            if (cacheInfo == null || cacheInfo.getOriginalFilePath() == null) {
                logger.warn("⚠️ 未找到EPUB文件信息 - FileId: {}", fileId);
                return null;
            }

            String epubPath = cacheInfo.getOriginalFilePath();
            logger.info("📖 开始自动解压EPUB - FileId: {}, Path: {}", fileId, epubPath);

            // 3. 安全验证：EPUB文件路径必须在允许的目录内
            if (!isPathAllowed(epubPath)) {
                logger.warn("🚫 EPUB文件路径不在允许的目录内: {}", epubPath);
                return null;
            }

            // 4. 检查文件是否存在
            Path epubFile = Paths.get(epubPath).normalize();
            if (!Files.exists(epubFile)) {
                logger.warn("⚠️ EPUB文件不存在: {}", epubPath);
                return null;
            }

            // 5. 验证文件类型（必须是.epub）
            if (!epubFile.getFileName().toString().toLowerCase().endsWith(".epub")) {
                logger.warn("🚫 文件不是EPUB格式: {}", epubFile.getFileName());
                return null;
            }

            // 6. 检查文件大小
            long fileSize = Files.size(epubFile);
            if (fileSize > MAX_FILE_SIZE) {
                logger.warn("🚫 EPUB文件过大: {} bytes", fileSize);
                return null;
            }

            // 7. 创建解压目录
            Path targetDir = Paths.get(uncompressDir, "epub", fileId).normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // 8. 解压 EPUB（ZIP）文件，带安全检查
            long totalExtractedSize = 0;
            try (ZipFile zipFile = new ZipFile(epubFile.toFile())) {
                var entries = zipFile.entries();
                
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    
                    // 跳过隐藏文件和系统文件
                    if (entry.getName().startsWith(".") || entry.getName().contains("/.")) {
                        continue;
                    }

                    Path targetPath = targetDir.resolve(entry.getName()).normalize();
                    
                    // 安全检查1：防止路径遍历
                    if (!targetPath.startsWith(targetDir)) {
                        logger.warn("🚫 检测到路径遍历攻击: {}", entry.getName());
                        continue;
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        // 安全检查2：文件扩展名白名单
                        if (!isAllowedFileType(entry.getName())) {
                            logger.warn("🚫 不允许的文件类型: {}", entry.getName());
                            continue;
                        }

                        // 安全检查3：文件大小限制
                        long entrySize = entry.getSize();
                        if (entrySize > MAX_FILE_SIZE) {
                            continue;
                        }

                        // 安全检查4：总解压大小限制（防止解压炸弹）
                        totalExtractedSize += entrySize;
                        if (totalExtractedSize > MAX_EXTRACTED_SIZE) {
                            logger.error("🚫 解压总大小超限，疑似解压炸弹攻击");
                            deleteDirectory(targetDir);
                            return null;
                        }

                        Files.createDirectories(targetPath.getParent());
                        try (InputStream in = zipFile.getInputStream(entry)) {
                            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            // 9. 缓存解压路径到 Redis（24小时有效期）
            String extractedPath = targetDir.toAbsolutePath().toString();
            redisTemplate.opsForValue().set(cacheKey, extractedPath, Duration.ofSeconds(EPUB_CACHE_TTL));

            logger.info("✅ EPUB自动解压完成并缓存到Redis - FileId: {}, Size: {} bytes, CacheKey: {}", 
                       fileId, totalExtractedSize, cacheKey);
            return extractedPath;

        } catch (Exception e) {
            logger.error("💥 EPUB自动解压失败 - FileId: {}", fileId, e);
            return null;
        }
    }

    // ==================== 安全验证方法 ====================

    /**
     * 验证文件ID格式（只允许字母、数字、下划线、短横线）
     */
    private boolean isValidFileId(String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            return false;
        }
        if (fileId.length() > 100) { // 限制长度
            return false;
        }
        return FILE_ID_PATTERN.matcher(fileId).matches();
    }

    /**
     * 验证EPUB文件路径是否在允许的目录内
     */
    /**
     * 检查EPUB文件路径安全性（复用FileUtils的isSecurePath）
     * 包括：
     * 1. 路径遍历防护（.. 和 ./）
     * 2. 系统目录拦截（/etc, /proc, /sys 等）
     */
    private boolean isPathAllowed(String epubPath) {
        return fileUtils.isSecurePath(epubPath);
    }

    /**
     * 检查文件类型是否在白名单内
     */
    private boolean isAllowedFileType(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        String lowerFileName = fileName.toLowerCase();
        
        // 1. 检查扩展名白名单
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        
        // 2. 检查特殊文件名白名单（EPUB必需的无扩展名文件）
        String fileNameOnly = fileName.contains("/") 
            ? fileName.substring(fileName.lastIndexOf("/") + 1) 
            : fileName;
        
        for (String allowedName : ALLOWED_FILENAMES) {
            if (fileNameOnly.equalsIgnoreCase(allowedName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                    .sorted((a, b) -> -a.compareTo(b)) // 先删除文件，后删除目录
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            logger.error("删除文件失败: {}", path, e);
                        }
                    });
            }
        } catch (Exception e) {
            logger.error("删除目录失败: {}", directory, e);
        }
    }

    /**
     * 根据文件路径确定 Content-Type
     */
    private String determineContentType(String path) {
        String lowerPath = path.toLowerCase();
        
        // 特殊文件名处理
        if (lowerPath.endsWith("mimetype")) {
            return "application/octet-stream";
        }
        
        if (lowerPath.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerPath.endsWith(".opf")) {
            return "application/oebps-package+xml";
        } else if (lowerPath.endsWith(".ncx")) {
            return "application/x-dtbncx+xml";
        } else if (lowerPath.endsWith(".html") || lowerPath.endsWith(".xhtml") || lowerPath.endsWith(".htm")) {
            return "text/html";
        } else if (lowerPath.endsWith(".css")) {
            return "text/css";
        } else if (lowerPath.endsWith(".js")) {
            return "application/javascript";
        } else if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerPath.endsWith(".png")) {
            return "image/png";
        } else if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerPath.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lowerPath.endsWith(".ttf")) {
            return "font/ttf";
        } else if (lowerPath.endsWith(".woff")) {
            return "font/woff";
        } else if (lowerPath.endsWith(".woff2")) {
            return "font/woff2";
        } else if (lowerPath.endsWith(".otf")) {
            return "font/otf";
        } else {
            return "application/octet-stream";
        }
    }
}
