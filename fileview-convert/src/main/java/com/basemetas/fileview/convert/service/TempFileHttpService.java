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
package com.basemetas.fileview.convert.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 临时文件HTTP访问服务
 * 
 * 为OnlyOffice转换提供临时的HTTP文件访问能力
 * 支持文件的临时共享和自动清理
 * 
 * @author 夫子
 */
@Service
@Configuration
public class TempFileHttpService {
    
    private static final Logger logger = LoggerFactory.getLogger(TempFileHttpService.class);
    
    @Value("${onlyoffice.file-access.http.base-path:/temp}")
    private String tempHttpBasePath;
    
    @Value("${onlyoffice.file-access.http.cleanup-interval:300}")
    private int cleanupIntervalSeconds;
    
    @Value("${onlyoffice.file-access.http.expire-time:3600}")
    private int fileExpireTimeSeconds;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    @Value("${onlyoffice.file-access.http.host:}")
    private String tempHttpHost;
    
    // 临时文件映射表：URL路径 -> 本地文件信息
    private final ConcurrentHashMap<String, TempFileInfo> tempFileMap = new ConcurrentHashMap<>();
    
    // 定时清理服务 - 使用 ScheduledThreadPoolExecutor 替代 Executors.newSingleThreadScheduledExecutor()
    // 避免线程数不可控和资源耗尽风险
    private final ScheduledExecutorService cleanupService = new ScheduledThreadPoolExecutor(
        1, // 核心线程数
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "temp-file-cleanup-" + threadNumber.getAndIncrement());
                thread.setDaemon(true); // 设置为守护线程，应用关闭时自动退出
                return thread;
            }
        },
        new ThreadPoolExecutor.DiscardPolicy() // 拒绝策略：丢弃任务（清理任务可容忍丢失）
    );
    
    /**
     * 注册临时文件HTTP访问的Servlet
     */
    @Bean
    public ServletRegistrationBean<TempFileServlet> tempFileServlet() {
        TempFileServlet servlet = new TempFileServlet(tempFileMap);
        ServletRegistrationBean<TempFileServlet> registration = new ServletRegistrationBean<>(servlet);
        registration.addUrlMappings(tempHttpBasePath + "/*");
        return registration;
    }
    
    /**
     * 启动定时清理任务
     */
    public void startCleanupTask() {
        cleanupService.scheduleAtFixedRate(this::cleanupExpiredFiles, 
                                         cleanupIntervalSeconds, 
                                         cleanupIntervalSeconds, 
                                         TimeUnit.SECONDS);
        logger.info("启动临时文件清理任务 - 清理间隔: {}秒, 文件过期时间: {}秒", 
                   cleanupIntervalSeconds, fileExpireTimeSeconds);
    }
    
    /**
     * 为文件创建临时HTTP访问URL
     * 
     * @param localFilePath 本地文件路径
     * @return HTTP访问URL
     */
    public String createTempFileUrl(String localFilePath) {
        logger.debug("开始创建临时文件URL - 本地路径: {}", localFilePath);
        
        if (localFilePath == null || localFilePath.trim().isEmpty()) {
            logger.error("本地文件路径不能为空");
            return null;
        }
        
        File localFile = new File(localFilePath);
        logger.debug("验证本地文件 - 路径: {}, 存在: {}, 是文件: {}, 大小: {}", 
                    localFilePath, localFile.exists(), localFile.isFile(), 
                    localFile.exists() ? localFile.length() : "N/A");
        
        if (!localFile.exists()) {
            logger.error("本地文件不存在: {}", localFilePath);
            return null;
        }
        
        if (!localFile.isFile()) {
            logger.error("本地路径不是文件: {}", localFilePath);
            return null;
        }
        
        try {
            // 生成唯一的URL路径
            String fileName = localFile.getName();
            String uniqueId = System.currentTimeMillis() + "_" + Math.abs(fileName.hashCode());
            String urlPath = uniqueId + "_" + fileName;  // 使用原始文件名作为Map的Key
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
            String encodedUrlPath = uniqueId + "_" + encodedFileName;  // 编码后用于URL传输
            
            logger.debug("生成URL路径信息 - 文件名: {}, 唯一ID: {}, Map Key: {}, URL路径: {}", 
                        fileName, uniqueId, urlPath, encodedUrlPath);
            
            // 创建临时文件信息，使用原始文件名作为Key
            TempFileInfo fileInfo = new TempFileInfo(localFilePath, System.currentTimeMillis());
            tempFileMap.put(urlPath, fileInfo);
            
            logger.debug("添加临时文件映射 - URL路径: {}, 本地路径: {}, 映射数量: {}", 
                        urlPath, localFilePath, tempFileMap.size());
            
            // 构建完整的HTTP URL（使用编码后的路径）
            String hostAddress = getActualHostAddress();
            String httpUrl = String.format("http://%s:%d%s/%s", 
                                         hostAddress, serverPort, tempHttpBasePath, encodedUrlPath);
            
            logger.info("创建临时文件访问URL - 本地路径: {}, HTTP URL: {}", localFilePath, httpUrl);
            return httpUrl;
            
        } catch (Exception e) {
            logger.error("创建临时文件URL失败 - 本地路径: {}", localFilePath, e);
            return null;
        }
    }
    
    /**
     * 获取实际的主机地址
     */
    private String getActualHostAddress() {
        // 如果配置了明确的主机地址，优先使用
        if (tempHttpHost != null && !tempHttpHost.trim().isEmpty()) {
            logger.debug("使用配置的主机地址: {}", tempHttpHost);
            return tempHttpHost.trim();
        }
        
        try {
            // 首先尝试获取环境变量中的主机地址
            String envHost = System.getenv("HOST_ADDRESS");
            if (envHost != null && !envHost.trim().isEmpty()) {
                logger.info("使用环境变量HOST_ADDRESS的主机地址: {}", envHost);
                return envHost.trim();
            }
            
            // 自动检测实际的IP地址
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            
            // 避免使用127.0.0.1
            if ("127.0.0.1".equals(hostAddress) || "localhost".equals(hostAddress)) {
                // 尝试获取网络接口的IP地址
                java.util.Enumeration<java.net.NetworkInterface> interfaces = 
                    java.net.NetworkInterface.getNetworkInterfaces();
                
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface networkInterface = interfaces.nextElement();
                    if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                        continue;
                    }
                    
                    java.util.Enumeration<java.net.InetAddress> addresses = 
                        networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && 
                            address instanceof java.net.Inet4Address) {
                            hostAddress = address.getHostAddress();
                            logger.info("检测到网络接口IP地址: {}", hostAddress);
                            return hostAddress;
                        }
                    }
                }
                
                // 如果找不到外部IP，使用localhost
                logger.warn("无法找到外部IP地址，使用localhost");
                return "localhost";
            }
            
            logger.info("使用主机地址: {}", hostAddress);
            return hostAddress;
            
        } catch (Exception e) {
            logger.warn("获取主机地址失败，使用localhost: {}", e.getMessage());
            return "localhost";
        }
    }
    
    /**
     * 删除临时文件URL映射
     * 
     * @param httpUrl 之前创建的HTTP URL
     */
    public void removeTempFileUrl(String httpUrl) {
        if (httpUrl == null || !httpUrl.contains(tempHttpBasePath)) {
            return;
        }
        
        try {
            // 从URL中提取路径部分
            String urlPath = httpUrl.substring(httpUrl.lastIndexOf('/') + 1);
            TempFileInfo removed = tempFileMap.remove(urlPath);
            
            if (removed != null) {
                logger.info("删除临时文件URL映射 - URL路径: {}, 本地路径: {}", 
                           urlPath, removed.getLocalFilePath());
            }
            
        } catch (Exception e) {
            logger.warn("删除临时文件URL映射失败: {}", httpUrl, e);
        }
    }
    
    /**
     * 清理过期的临时文件映射
     */
    private void cleanupExpiredFiles() {
        try {
            long currentTime = System.currentTimeMillis();
            long expireThreshold = fileExpireTimeSeconds * 1000L;
            
            int cleanedCount = 0;
            for (String urlPath : tempFileMap.keySet()) {
                TempFileInfo fileInfo = tempFileMap.get(urlPath);
                if (fileInfo != null && (currentTime - fileInfo.getCreateTime()) > expireThreshold) {
                    tempFileMap.remove(urlPath);
                    cleanedCount++;
                    logger.debug("清理过期临时文件映射 - URL路径: {}, 本地路径: {}", 
                                urlPath, fileInfo.getLocalFilePath());
                }
            }
            
            if (cleanedCount > 0) {
                logger.info("清理过期临时文件映射完成 - 清理数量: {}, 剩余数量: {}", 
                           cleanedCount, tempFileMap.size());
            }
            
        } catch (Exception e) {
            logger.error("清理过期临时文件映射失败", e);
        }
    }
    
    /**
     * 获取当前临时文件映射状态
     */
    public TempFileStatus getStatus() {
        return new TempFileStatus(tempFileMap.size(), tempHttpBasePath, fileExpireTimeSeconds);
    }
    
    /**
     * 临时文件信息类
     */
    public static class TempFileInfo {
        private final String localFilePath;
        private final long createTime;
        
        public TempFileInfo(String localFilePath, long createTime) {
            this.localFilePath = localFilePath;
            this.createTime = createTime;
        }
        
        public String getLocalFilePath() { return localFilePath; }
        public long getCreateTime() { return createTime; }
    }
    
    /**
     * 临时文件状态信息
     */
    public static class TempFileStatus {
        private final int activeMappings;
        private final String basePath;
        private final int expireTimeSeconds;
        
        public TempFileStatus(int activeMappings, String basePath, int expireTimeSeconds) {
            this.activeMappings = activeMappings;
            this.basePath = basePath;
            this.expireTimeSeconds = expireTimeSeconds;
        }
        
        public int getActiveMappings() { return activeMappings; }
        public String getBasePath() { return basePath; }
        public int getExpireTimeSeconds() { return expireTimeSeconds; }
    }
    
    /**
     * 临时文件HTTP访问Servlet
     */
    public static class TempFileServlet extends HttpServlet {
        private static final Logger logger = LoggerFactory.getLogger(TempFileServlet.class);
        private final ConcurrentHashMap<String, TempFileInfo> tempFileMap;
        
        public TempFileServlet(ConcurrentHashMap<String, TempFileInfo> tempFileMap) {
            this.tempFileMap = tempFileMap;
        }
        
        @Override
        protected void doHead(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String urlPath = pathInfo.substring(1);
            try {
                urlPath = URLDecoder.decode(urlPath, StandardCharsets.UTF_8.name());
                TempFileInfo fileInfo = tempFileMap.get(urlPath);
                if (fileInfo == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                File localFile = new File(fileInfo.getLocalFilePath());
                if (!localFile.exists() || !localFile.isFile()) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                String fileName = localFile.getName();
                String contentType = getContentType(fileName);
                response.setContentType(contentType);
                response.setContentLengthLong(localFile.length());
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
        
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            
            String pathInfo = request.getPathInfo();
            logger.debug("收到临时文件访问请求 - PathInfo: {}", pathInfo);
            
            if (pathInfo == null || pathInfo.length() <= 1) {
                logger.warn("临时文件路径无效: {}", pathInfo);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件路径无效");
                return;
            }
            
            // 去掉开头的 '/'
            String urlPath = pathInfo.substring(1);
            
            try {
                // URL解码
                urlPath = URLDecoder.decode(urlPath, StandardCharsets.UTF_8.name());
                logger.debug("解码后的URL路径: {}", urlPath);
                
                // 查找文件映射
                TempFileInfo fileInfo = tempFileMap.get(urlPath);
                if (fileInfo == null) {
                    logger.warn("临时文件映射不存在: {}", urlPath);
                    logger.warn("当前临时文件映射数量: {}", tempFileMap.size());
                    // 打印所有映射（仅用于调试）
                    if (logger.isDebugEnabled()) {
                        tempFileMap.forEach((key, value) -> logger.debug("映射项 - Key: {}, Value: {}", key, value.getLocalFilePath()));
                    }
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已过期");
                    return;
                }
                
                logger.debug("找到临时文件映射 - URL路径: {}, 本地路径: {}", urlPath, fileInfo.getLocalFilePath());
                
                // 验证本地文件是否存在
                File localFile = new File(fileInfo.getLocalFilePath());
                if (!localFile.exists() || !localFile.isFile()) {
                    logger.error("本地文件不存在或不是有效文件: {}", fileInfo.getLocalFilePath());
                    tempFileMap.remove(urlPath); // 清理无效映射
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "本地文件不存在");
                    return;
                }
                
                // 记录请求来源IP（用于调试网络访问问题）
                String clientIP = request.getRemoteAddr();
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    clientIP = forwardedFor.split(",")[0].trim();
                }
                logger.info("临时文件访问请求来源 - IP: {}, URL路径: {}, 本地文件: {}", 
                           clientIP, urlPath, fileInfo.getLocalFilePath());
                
                // 设置响应头
                String fileName = localFile.getName();
                String contentType = getContentType(fileName);
                response.setContentType(contentType);
                logger.debug("设置响应Content-Type: {}", contentType);
                
                // 正确编码中文文件名
                String encodedFileName;
                try {
                    // 使用RFC 5987标准编码中文文件名
                    encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                        .replace("+", "%20"); // 替换+号为%20（空格编码）
                    response.setHeader("Content-Disposition", 
                        String.format("inline; filename*=UTF-8''%s", encodedFileName));
                } catch (Exception e) {
                    // 编码失败时使用安全的ASCII文件名
                    String safeFileName = fileName.replaceAll("[^\\w.-]", "_");
                    response.setHeader("Content-Disposition", 
                        String.format("inline; filename=\"%s\"", safeFileName));
                    logger.warn("文件名编码失败，使用安全文件名: {} -> {}", fileName, safeFileName);
                }
                
                response.setContentLengthLong(localFile.length());
                response.setHeader("Accept-Ranges", "bytes");
                
                logger.info("提供临时文件访问 - URL路径: {}, 本地路径: {}, 大小: {} bytes", 
                            urlPath, fileInfo.getLocalFilePath(), localFile.length());
                
                // 传输文件内容
                try (FileInputStream fileInput = new FileInputStream(localFile);
                     BufferedInputStream bufferedInput = new BufferedInputStream(fileInput);
                     OutputStream output = response.getOutputStream()) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = bufferedInput.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    output.flush();
                    
                    logger.info("临时文件传输完成 - URL路径: {}, 传输字节数: {}", urlPath, totalBytes);
                }
                
            } catch (Exception e) {
                logger.error("临时文件访问异常 - URL路径: {}", urlPath, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件访问异常: " + e.getMessage());
            }
        }
        
        /**
         * 根据文件名获取MIME类型
         */
        private String getContentType(String fileName) {
            if (fileName == null) {
                return "application/octet-stream";
            }
            
            String lowerName = fileName.toLowerCase();
            if (lowerName.endsWith(".pdf")) {
                return "application/pdf";
            } else if (lowerName.endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (lowerName.endsWith(".doc")) {
                return "application/msword";
            } else if (lowerName.endsWith(".txt")) {
                return "text/plain; charset=utf-8";
            } else if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
                return "text/html; charset=utf-8";
            } else if (lowerName.endsWith(".png")) {
                return "image/png";
            } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else {
                return "application/octet-stream";
            }
        }
    }
}