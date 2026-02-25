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
package com.basemetas.fileview.preview.service.download;

import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.service.cache.CacheKeyManager;
import com.basemetas.fileview.preview.utils.CacheUtils;
import com.basemetas.fileview.preview.utils.HttpUtils;
import com.basemetas.fileview.preview.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;

/**
 * 文件下载服务
 * 
 * 支持协议：
 * - HTTP/HTTPS
 * - FTP/FTPS (支持URL中携带用户名密码：ftp://user:pass@host/path)
 * - SFTP (通过JSch实现)
 * - S3 (通过AWS SDK实现)
 * 
 * @author 夫子
 */
@Service
public class FileDownloadService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileDownloadService.class);
    
    /**
     * FTP URL 正则表达式 - 预编译以提高性能
     * 格式：ftp://[user:pass@]host/path
     */
    private static final Pattern FTP_URL_PATTERN = Pattern.compile("ftp://(?:([^:]+):([^@]+)@)?([^/]+)(.*)");
    
    @Value("${fileview.network.download.connect-timeout:3000}")
    private int connectTimeout;
    
    @Value("${fileview.network.download.read-timeout:60000}")
    private int readTimeout;
    
    @Value("${fileview.network.download.max-retry:3}")
    private int maxRetry;
    
    @Value("${fileview.network.download.retry-base-delay:300}")
    private int retryBaseDelay;
    
    @Value("${fileview.network.download.buffer-size:65536}")
    private int bufferSize;

    @Autowired
    private CacheUtils cacheUtils;
    @Autowired
    private HttpUtils httpUtils;

    private HttpClient httpClient;
    
    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeout))
            .version(HttpClient.Version.HTTP_2)
            .build();
        logger.info("初始化 HttpClient - ConnectTimeout: {}ms, ReadTimeout: {}ms, HTTP/2 enabled", 
            connectTimeout, readTimeout);
    }
    
    /**
     * 下载网络文件到本地
     * 
     * @param fileUrl 文件URL
     * @param targetPath 目标路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param timeout 超时时间
     * @return 下载后的本地文件路径
     */
    public String downloadFile(String fileUrl, String targetPath, String username, 
                              String password, int timeout) {
        return downloadFile(fileUrl, targetPath, username, password, timeout, null);
    }
    
    /**
     * 下载网络文件到本地
     * 
     * @param fileUrl 文件URL
     * @param targetPath 目标路径
     * @param username 用户名（可选）
     * @param password 密码（可选）
     * @param timeout 超时时间
     * @param fileName 自定义文件名（可选，非空时将直接使用该名称，不复用旧文件）
     * @return 下载后的本地文件路径
     */
    public String downloadFile(String fileUrl, String targetPath, String username, 
                              String password, int timeout, String fileName) {
        try {
            logger.info("🌐 开始下载网络文件 - URL: {}, Target: {}", httpUtils.encodeUrl(fileUrl), targetPath);
            
            // 1. 验证参数
            if (fileUrl == null || fileUrl.isEmpty()) {
                throw FileViewException.of(
                    ErrorCode.INVALID_PARAMETER,
                    "文件URL不能为空"
                );
            }
            
            if (targetPath == null || targetPath.trim().isEmpty()) {
                throw FileViewException.of(
                    ErrorCode.INVALID_PARAMETER,
                    "下载目标路径不能为空"
                );
            }
            
            // 2. 清理URL，移除开头的空格等非法字符
            fileUrl = fileUrl.trim();
            
            // 3. 对URL进行编码处理，支持中文和特殊字符（如空格）
            String encodedUrl = httpUtils.encodeUrl(fileUrl);
            logger.debug("🔗 URL编码 - Original: {}, Encoded: {}", httpUtils.maskSensitiveUrl(fileUrl), httpUtils.maskSensitiveUrl(encodedUrl));
            
            // 4. 解析URL协议
            URI uri = new URI(encodedUrl);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isEmpty()) {
                throw new IllegalArgumentException("无法解析URL协议: " + encodedUrl);
            }
            String protocol = scheme.toLowerCase();
            
            // 5. 确保目标目录存在
            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    throw new IOException("无法创建目标目录: " + targetPath);
                }
            }           
            // 6. 确定本地文件名并构建目标文件路径
            String effectiveFileName;
            if (fileName != null && !fileName.trim().isEmpty()) {
                effectiveFileName = sanitizeFileName(fileName);
            } else {
                effectiveFileName = httpUtils.extractFileNameFromUrl(encodedUrl);
            }
            String targetFilePath = Paths.get(targetPath, effectiveFileName).toString();           
            logger.info("📥 文件下载信息 - Protocol: {}, FileName: {}, Timeout: {}ms", 
                       protocol, effectiveFileName, timeout);         
            // 复用已下载文件（URL级去重）—— 仅在未指定自定义文件名时尝试复用
            String reusePath = null;
            if (fileName == null || fileName.trim().isEmpty()) {
                reusePath = tryReuseCachedFile(encodedUrl);
            }
            if (reusePath != null) {
                logger.info("♻️ 命中已下载缓存，直接复用 - LocalPath: {}", reusePath);
                return reusePath;
            }
            
            // 7. 根据协议选择下载方式（使用编码后URL）
            switch (protocol) {
                case "http":
                case "https":
                    downloadHttpFile(encodedUrl, targetFilePath, timeout);
                    break;
                    
                case "ftp":
                case "ftps":
                    downloadFtpFile(encodedUrl, targetFilePath, username, password, timeout);
                    break;
                    
                case "sftp":
                    downloadSftpFile(uri, targetFilePath, username, password, timeout);
                    break;
                    
                case "s3":
                    throw new UnsupportedOperationException("S3协议需要通过扩展参数传递认证信息，请使用downloadS3File方法");
                    
                default:
                    throw FileViewException.of(
                        ErrorCode.INVALID_PARAMETER,
                        "不支持的协议: " + protocol
                    );
            }
            
            logger.info("✅ 文件下载成功 - LocalPath: {}", targetFilePath);
            
            return targetFilePath;
            
        } catch (FileViewException e) {
            throw e;
        } catch (Exception e) {
            String maskedUrl = httpUtils.maskSensitiveUrl(fileUrl);
            String simplifiedError = "下载网络文件失败: " + e.getMessage();
            logger.error("❌ 下载网络文件失败 - URL: {}", maskedUrl);
            throw FileViewException.of(
                ErrorCode.SYSTEM_ERROR,
                simplifiedError,
                e
            );
        }
    }
    
    /**
     * 下载HTTP/HTTPS文件（使用java.net.http.HttpClient + NIO零拷贝 + 指数退避重试）
     */
    private void downloadHttpFile(String fileUrl, String targetFilePath, int timeout) throws IOException {
        long httpStartTime = System.currentTimeMillis();
        
        // 1. 先尝试条件请求（HEAD）以复用本地文件
        long conditionalCheckStart = System.currentTimeMillis();
        if (tryConditionalRequest(fileUrl, targetFilePath)) {
            long conditionalCheckTime = System.currentTimeMillis() - conditionalCheckStart;
            logger.info("⏱️ 条件请求命中304耗时: {}ms - URL: {}", conditionalCheckTime, httpUtils.maskSensitiveUrl(fileUrl));
            return;
        }
        long conditionalCheckTime = System.currentTimeMillis() - conditionalCheckStart;
        if (conditionalCheckTime > 100) {
            logger.info("⏱️ 条件请求耗时: {}ms (未命中)", conditionalCheckTime);
        }
        
        // 2. 带指数退避的重试逻辑
        IOException lastException = null;
        for (int attempt = 0; attempt < maxRetry; attempt++) {
            try {
                long attemptStart = System.currentTimeMillis();
                performHttpDownload(fileUrl, targetFilePath, timeout);
                long attemptTime = System.currentTimeMillis() - attemptStart;
                long totalHttpTime = System.currentTimeMillis() - httpStartTime;
                logger.info("⏱️ HTTP下载总耗时: {}ms (实际下载: {}ms, 尝试次数: {}) - URL: {}", 
                    totalHttpTime, attemptTime, attempt + 1, httpUtils.maskSensitiveUrl(fileUrl));
                return; // 成功则直接返回
            } catch (IOException | InterruptedException e) {
                lastException = e instanceof IOException ? (IOException)e : new IOException(e);
                
                if (attempt < maxRetry - 1) {
                    long delay = retryBaseDelay * (long)Math.pow(2, attempt);
                    logger.warn("❗ 下载失败，{}ms后重试 ({}/{}) - Error: {}", 
                        delay, attempt + 1, maxRetry, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("重试被中断", ie);
                    }
                } else {
                    logger.error("❌ 下载失败，已重试{}次 - URL: {}", maxRetry, httpUtils.maskSensitiveUrl(fileUrl));
                }
            }
        }
        throw lastException;
    }
    
    /**
     * 执行HTTP下载（使用java.net.http.HttpClient）
     */
    private void performHttpDownload(String fileUrl, String targetFilePath, int timeout) 
            throws IOException, InterruptedException {
        
        long requestStart = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(fileUrl))
            .timeout(Duration.ofMillis(timeout))
            .header("User-Agent", "FileView-Preview-Service/1.0")
            .GET()
            .build();
        
        long sendStart = System.currentTimeMillis();
        long requestBuildTime = sendStart - requestStart;
        
        HttpResponse<InputStream> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofInputStream());
        
        long responseTime = System.currentTimeMillis() - sendStart;
        logger.info("⏱️ HTTP请求耗时: 构建={}ms, 响应={}ms", requestBuildTime, responseTime);
        
        int statusCode = response.statusCode();
        if (statusCode != 200) {
            throw new IOException(String.format("HTTP请求失败: %d, URL: %s", 
                statusCode, httpUtils.maskSensitiveUrl(fileUrl)));
        }
        
        // 获取文件大小
        long contentLength = response.headers()
            .firstValueAsLong("Content-Length")
            .orElse(-1L);
        logger.info("📊 文件大小: {} bytes", contentLength > 0 ? contentLength : "未知");
        
        // 使用NIO零拷贝写入文件
        long writeStart = System.currentTimeMillis();
        long transferred = 0;
        try (InputStream in = response.body();
             FileOutputStream fos = new FileOutputStream(targetFilePath);
             FileChannel outChannel = fos.getChannel();
             ReadableByteChannel inChannel = Channels.newChannel(in)) {
            
            transferred = outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
            long writeTime = System.currentTimeMillis() - writeStart;
            double speedMBps = contentLength > 0 ? (transferred / 1024.0 / 1024.0) / (writeTime / 1000.0) : 0;
            logger.info("✅ HTTP文件下载完成 - 总计: {} bytes, 写盘耗时: {}ms, 速度: {} MB/s", 
                transferred, writeTime, String.format("%.2f", speedMBps));
        }
        
        // ⭐ 文件完整性校验：对比实际下载大小与Content-Length
        if (contentLength > 0 && transferred != contentLength) {
            // 删除不完整的文件
            new File(targetFilePath).delete();
            throw new IOException(String.format(
                "文件下载不完整 - 预期: %d bytes, 实际: %d bytes (%.1f%%), URL: %s",
                contentLength, transferred, 
                (transferred * 100.0 / contentLength),
                httpUtils.maskSensitiveUrl(fileUrl)
            ));
        }
        
        // 保存响应头中的ETag / Last-Modified
        saveHttpResponseHeaders(fileUrl, response);
    }
    
    /**
     * 尝试条件请求（HEAD + If-None-Match/If-Modified-Since）
     * @return true 如果命中304并成功复用，false 如果需要重新下载
     */
    private boolean tryConditionalRequest(String fileUrl, String targetFilePath) {
        try {
            String urlHash = httpUtils.computeUrlHash(fileUrl);
            String etag = cacheUtils.getRedisValue(CacheKeyManager.DOWNLOAD_ETAG_PREFIX + urlHash);
            String lastModified = cacheUtils.getRedisValue(CacheKeyManager.DOWNLOAD_LAST_MODIFIED_PREFIX + urlHash);
            String fileHashForUrl = cacheUtils.getRedisValue(CacheKeyManager.FILE_PATH_MAPPING_PREFIX + urlHash);
            String cachedPath = fileHashForUrl != null ? cacheUtils.getRedisValue(CacheKeyManager.FILE_HASH_PREFIX + fileHashForUrl) : null;
            
            if ((etag == null && lastModified == null) || cachedPath == null) {
                return false; // 没有缓存信息，直接下载
            }
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofMillis(connectTimeout))
                .header("User-Agent", "FileView-Preview-Service/1.0")
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
            
            if (etag != null) {
                requestBuilder.header("If-None-Match", etag);
            }
            if (lastModified != null) {
                requestBuilder.header("If-Modified-Since", lastModified);
            }
            
            HttpResponse<Void> response = httpClient.send(
                requestBuilder.build(), 
                HttpResponse.BodyHandlers.discarding());
            
            if (response.statusCode() == 304 && new File(cachedPath).exists()) {
                copyIfNeeded(cachedPath, targetFilePath);
                logger.info("✅ 304 Not Modified，复用本地文件 - {}", cachedPath);
                return true;
            }
        } catch (Exception e) {
            logger.debug("HEAD 条件请求失败，回退到 GET", e);
        }
        return false;
    }
    
    /**
     * 保存HTTP响应头到Redis
     */
    private void saveHttpResponseHeaders(String fileUrl, HttpResponse<?> response) {
        try {
            String urlHash = httpUtils.computeUrlHash(fileUrl);
            response.headers().firstValue("ETag").ifPresent(etag -> 
                cacheUtils.setRedisValue(CacheKeyManager.DOWNLOAD_ETAG_PREFIX + urlHash, etag));
            response.headers().firstValue("Last-Modified").ifPresent(lastMod -> 
                cacheUtils.setRedisValue(CacheKeyManager.DOWNLOAD_LAST_MODIFIED_PREFIX + urlHash, lastMod));
        } catch (Exception e) {
            logger.warn("保存HTTP响应头失败", e);
        }
    }
    
    /**
     * 下载FTP文件
     * 
     * 支持URL格式：
     * - ftp://host/path/file
     * - ftp://user:pass@host/path/file
     */
    private void downloadFtpFile(String fileUrl, String targetFilePath, 
                                 String username, String password, int timeout) throws IOException {
        
        // 解析URL中的认证信息
        FtpCredentials credentials = parseFtpUrl(fileUrl, username, password);
        
        logger.info("🔐 FTP连接信息 - Host: {}, User: {}, Password: {}", 
                   credentials.host, 
                   credentials.username != null ? credentials.username : "anonymous",
                   credentials.password != null ? "***" : "null");
        
        // 构建带认证的URL
        String authenticatedUrl;
        if (credentials.username != null && credentials.password != null) {
            // 不需要再次编码用户名和密码，因为URL已经被encodeUrl处理过
            // 如果用户名密码已经在URL中，parseFtpUrl已经提取出来了
            // 如果是从参数传入的，也不需要编码（FTP URL不支持特殊字符）
            authenticatedUrl = String.format("ftp://%s:%s@%s%s",
                credentials.username,
                credentials.password,
                credentials.host,
                credentials.path
            );
            logger.debug("🔗 构建认证URL - Host: {}, Path: {}", credentials.host, credentials.path);
        } else {
            authenticatedUrl = fileUrl;
        }
        
        URL url = new URL(authenticatedUrl);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(targetFilePath)) {
            
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long totalBytesRead = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            
            logger.info("✅ FTP文件下载完成 - 总计: {} bytes", totalBytesRead);
            
            // ⭐ 校验下载文件大小
            File downloadedFile = new File(targetFilePath);
            if (!downloadedFile.exists() || downloadedFile.length() == 0) {
                throw new IOException("下载文件不存在或为空: " + targetFilePath);
            }
            if (downloadedFile.length() != totalBytesRead) {
                logger.warn("⚠️ 磁盘文件大小不匹配 - 预期: {}, 实际: {}", 
                    totalBytesRead, downloadedFile.length());
            }
        }
    }
    
    /**
     * 下载SFTP文件 
     * 
     */
    private void downloadSftpFile(URI uri, String targetFilePath, 
                                  String username, String password, int timeout) throws IOException {
        com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
        com.jcraft.jsch.Session session = null;
        com.jcraft.jsch.ChannelSftp channelSftp = null;
        
        try {
            // 创建会话
            session = jsch.getSession(username, uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 22);
            if (password != null) {
                session.setPassword(password);
            }
            
            // 配置会话
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no"); // 不验证主机密钥
            session.setConfig(config);
            session.setTimeout(timeout);
            
            // 连接
            logger.info("🔗 连接SFTP服务器 - Host: {}, Port: {}, User: {}", 
                       uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 22, username);
            session.connect();
            
            // 打开SFTP通道
            channelSftp = (com.jcraft.jsch.ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            
            // 下载文件
            String remoteFilePath = uri.getPath();
            logger.info("📥 下载SFTP文件 - Remote: {}, Local: {}", remoteFilePath, targetFilePath);
            channelSftp.get(remoteFilePath, targetFilePath);
            
            // 获取文件信息
            com.jcraft.jsch.SftpATTRS attrs = channelSftp.stat(remoteFilePath);
            if (attrs != null) {
                logger.info("📊 SFTP文件大小: {} bytes", attrs.getSize());
            }
            
            logger.info("✅ SFTP文件下载完成 - Remote: {}, Local: {}", remoteFilePath, targetFilePath);
            
        } catch (com.jcraft.jsch.SftpException e) {
            logger.error("❌ SFTP操作失败 - Host: {}, Path: {}", uri.getHost(), uri.getPath(), e);
            throw new IOException("SFTP操作失败: " + e.getMessage(), e);
        } catch (com.jcraft.jsch.JSchException e) {
            logger.error("❌ SFTP连接失败 - Host: {}", uri.getHost(), e);
            throw new IOException("SFTP连接失败: " + e.getMessage(), e);
        } finally {
            // 关闭资源
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * 解析FTP URL中的认证信息
     */
    private FtpCredentials parseFtpUrl(String url, String fallbackUsername, String fallbackPassword) {
        try {
            // 利用预编译的正则表达式解析 ftp://user:pass@host/path 格式
            Matcher matcher = FTP_URL_PATTERN.matcher(url);
            
            if (matcher.matches()) {
                String urlUsername = matcher.group(1);
                String urlPassword = matcher.group(2);
                String host = matcher.group(3);
                String path = matcher.group(4);
                
                // URL中的认证信息优先
                String username = urlUsername != null ? urlUsername : fallbackUsername;
                String password = urlPassword != null ? urlPassword : fallbackPassword;
                
                return new FtpCredentials(host, path, username, password);
            }
            
            // 如果解析失败，使用默认格式
            URI uri = new URI(url);
            return new FtpCredentials(
                uri.getHost(),
                uri.getPath(),
                fallbackUsername,
                fallbackPassword
            );
            
        } catch (Exception e) {
            logger.warn("⚠️ FTP URL解析失败，使用默认解析", e);
            try {
                URI uri = new URI(url);
                return new FtpCredentials(uri.getHost(), uri.getPath(), fallbackUsername, fallbackPassword);
            } catch (Exception ex) {
                throw new IllegalArgumentException("无效的FTP URL: " + url, ex);
            }
        }
    }

    private String tryReuseCachedFile(String encodedUrl) {
        String urlHash = httpUtils.computeUrlHash(encodedUrl);
        String fileHash = cacheUtils.getRedisValue(CacheKeyManager.FILE_PATH_MAPPING_PREFIX + urlHash);
        if (fileHash != null) {
            String existingPath = cacheUtils.getRedisValue(CacheKeyManager.FILE_HASH_PREFIX + fileHash);
            if (existingPath != null) {
                File f = new File(existingPath);
                if (f.exists() && f.isFile()) {
                    return existingPath;
                }
            }
        }
        return null;
    }

    /**
     * 对外部传入的文件名进行基础清洗，防止包含路径分隔符等非法字符
     */
    private String sanitizeFileName(String rawFileName) {
        if (rawFileName == null) {
            return "download_" + System.currentTimeMillis();
        }
        String name = rawFileName.trim();
        if (name.isEmpty()) {
            return "download_" + System.currentTimeMillis();
        }
        // 只保留最后一段，防止路径注入（/ 或 \\）
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < name.length() - 1) {
            name = name.substring(slashIndex + 1);
        }
        // 去除可能的相对路径标记
        name = name.replace("..", "");
        if (name.isEmpty()) {
            return "download_" + System.currentTimeMillis();
        }
        return name;
    }
    private void copyIfNeeded(String src, String dest) throws IOException {
        if (src == null || dest == null || src.equals(dest)) {
            return;
        }
        File srcFile = new File(src);
        if (!srcFile.exists()) { return; }
        Files.copy(srcFile.toPath(), new File(dest).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * FTP认证凭据
     */
    private static class FtpCredentials {
        final String host;
        final String path;
        final String username;
        final String password;
        
        FtpCredentials(String host, String path, String username, String password) {
            this.host = host;
            this.path = path;
            this.username = username;
            this.password = password;
        }
    }
}
