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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.service.url.BaseUrlProvider;

@Component
public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    @Autowired
    private BaseUrlProvider baseUrlProvider;

    // URL安全校验配置
    @Value("${fileview.network.security.trusted-sites:}")
    private String trustedSitesConfig;

    @Value("${fileview.network.security.untrusted-sites:}")
    private String untrustedSitesConfig;

    /**
     * 从当前HTTP请求中动态获取baseUrl
     * 格式: scheme://host:port/contextPath
     * 
     * @return 动态获取的baseUrl，失败时返回配置的baseUrl
     */
    public String getDynamicBaseUrl() {
        return baseUrlProvider.getBaseUrl();
    }

    /**
     * 清理URL，确保协议部分没有非法字符
     * 
     * @param url 原始URL
     * @return 清理后的URL
     */
    public String cleanUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            // 移除开头的空格和其他非法字符
            url = url.trim();

            // 查找协议分隔符
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                // 如果没有找到协议分隔符，直接返回
                return url;
            }

            // 提取协议部分并清理
            String protocol = url.substring(0, protocolEnd).trim().toLowerCase();
            String remaining = url.substring(protocolEnd + 3);

            // 确保协议是合法的（只包含字母）
            if (!protocol.matches("[a-z]+")) {
                // 如果协议包含非法字符，尝试找到第一个合法的协议
                int firstLetter = -1;
                for (int i = 0; i < protocol.length(); i++) {
                    if (Character.isLetter(protocol.charAt(i))) {
                        firstLetter = i;
                        break;
                    }
                }

                if (firstLetter != -1) {
                    protocol = protocol.substring(firstLetter);
                    // 再次检查协议是否合法
                    if (!protocol.matches("[a-z]+")) {
                        // 如果仍然不合法，返回原始URL
                        return url;
                    }
                } else {
                    // 如果没有找到字母，返回原始URL
                    return url;
                }
            }

            // 重新组装URL
            return protocol + "://" + remaining;
        } catch (Exception e) {
            logger.warn("⚠️ URL清理失败，使用原始URL: {}", url, e);
            return url;
        }
    }

    /**
     * 对URL进行编码处理，支持中文和特殊字符（如空格）
     * 只编码路径部分，保持协议、主机、端口、查询参数、片段不变
     * 
     * @param url 原始URL
     * @return 编码后URL
     */
    public String encodeUrl(String url) {
        try {
            // 如果URL看起来已经编码过了（包含编码的协议分隔符），尝试解码后再处理
            if (url.contains("%3A%2F%2F")) {
                logger.debug("URL看起来已经编码过了，尝试解码: {}", maskSensitiveUrl(url));
                try {
                    // 先解码URL
                    String decodedUrl = URLDecoder.decode(url, "UTF-8");
                    logger.debug("解码后的URL: {}", maskSensitiveUrl(decodedUrl));
                    url = decodedUrl;
                } catch (Exception decodeException) {
                    logger.warn("URL解码失败，使用原始URL: {}", maskSensitiveUrl(url), decodeException);
                    return url;
                }
            }

            // 解析URL的各个部分
            int protocolEnd = url.indexOf("://");
            if (protocolEnd == -1) {
                throw new IllegalArgumentException("无效的URL格式: " + url);
            }

            String protocol = url.substring(0, protocolEnd);
            String remaining = url.substring(protocolEnd + 3);

            // 分离认证信息、主机、路径
            String auth = "";
            String host;
            String pathAndQuery;

            // 检查是否有认证信息
            int authEnd = remaining.indexOf('@');
            if (authEnd != -1) {
                auth = remaining.substring(0, authEnd + 1);
                remaining = remaining.substring(authEnd + 1);
            }

            // 分离主机和路径
            int pathStart = remaining.indexOf('/');
            if (pathStart == -1) {
                host = remaining;
                pathAndQuery = "";
            } else {
                host = remaining.substring(0, pathStart);
                pathAndQuery = remaining.substring(pathStart);
            }

            // 🔧 分离路径、查询参数和片段
            String path;
            String query = "";
            String fragment = "";
            
            // 提取片段（#后面的部分）
            int fragmentStart = pathAndQuery.indexOf('#');
            if (fragmentStart != -1) {
                fragment = pathAndQuery.substring(fragmentStart); // 包含#
                pathAndQuery = pathAndQuery.substring(0, fragmentStart);
            }
            
            // 提取查询参数（?后面的部分）
            int queryStart = pathAndQuery.indexOf('?');
            if (queryStart != -1) {
                query = pathAndQuery.substring(queryStart); // 包含?
                path = pathAndQuery.substring(0, queryStart);
            } else {
                path = pathAndQuery;
            }

            // 对路径部分进行编码（按段编码，保持/分隔符）
            if (!path.isEmpty()) {
                String[] pathSegments = path.split("/");
                StringBuilder encodedPath = new StringBuilder();

                for (int i = 0; i < pathSegments.length; i++) {
                    if (i > 0) { // 只有在不是第一个段时才添加/
                        encodedPath.append("/");
                    }
                    if (!pathSegments[i].isEmpty()) {
                        // 编码每个路径段，但保持已经编码的%字符
                        String segment = pathSegments[i];
                        if (!isAlreadyEncoded(segment)) {
                            segment = URLEncoder.encode(segment, "UTF-8")
                                    .replace("+", "%20") // 空格用%20而不是+
                                    .replace("%2F", "/"); // 保持路径分隔符
                        }
                        encodedPath.append(segment);
                    }
                }
                path = encodedPath.toString();
            }

            // 重新组装URL（查询参数和片段保持原样，不编码）
            String encodedUrl = protocol + "://" + auth + host + path + query + fragment;

            return encodedUrl;

        } catch (Exception e) {
            logger.warn("⚠️ URL编码失败，使用原始URL: {}", url, e);
            return url;
        }
    }

    public String computeUrlHash(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // 退化：使用简单hashCode
            return Integer.toHexString(url.hashCode());
        }
    }

    /**
     * 屏蔽URL中的敏感信息（密码）
     */
    public String maskSensitiveUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");
    }

    /**
     * 从URL中提取文件名
     */
    public String extractFileNameFromUrl(String fileUrl) {
        try {
            // 🔧 修复：处理URL中可能存在的未编码字符（如空格）
            // 先尝试直接解析，如果失败则进行编码后再解析
            java.net.URI uri;
            try {
                uri = new java.net.URI(fileUrl);
            } catch (java.net.URISyntaxException e) {
                // URL包含非法字符（如空格），需要编码
                logger.debug("URL包含非法字符，进行编码处理: {}", fileUrl);
                java.net.URL url = new java.net.URL(fileUrl);
                uri = new java.net.URI(url.getProtocol(), url.getAuthority(), url.getPath(), url.getQuery(), url.getRef());
            }
            
            String path = uri.getPath();

            if (path == null || path.isEmpty() || path.endsWith("/")) {
                return "download_" + System.currentTimeMillis();
            }

            String fileName = path.substring(path.lastIndexOf('/') + 1);

            // URL解码（容错处理：解码失败时保留原始字符串，至少可以提取扩展名）
            try {
                fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            } catch (IllegalArgumentException decodeEx) {
                logger.warn("⚠️ URL解码失败，保留原始文件名 - FileName: {}, Error: {}", fileName, decodeEx.getMessage());
                // 保留原始 fileName 不变，继续后续处理
            }

            return fileName.isEmpty() ? "download_" + System.currentTimeMillis() : fileName;

        } catch (Exception e) {
            logger.warn("⚠️ 从URL提取文件名失败，使用默认名称 - URL: {}, Error: {}", fileUrl, e.getMessage());
            return "download_" + System.currentTimeMillis();
        }
    }

    /**
     * 从 URL 中提取文件名（简化版，用于 URL 判断）
     * 返回小写格式的文件名，用于扩展名匹配
     */
    public String extractSimpleFileNameFromUrl(String url) {
        try {
            // 移除查询参数
            int queryIndex = url.indexOf('?');
            if (queryIndex > 0) {
                url = url.substring(0, queryIndex);
            }

            // 提取最后一个路径分隔符后的内容
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                return url.substring(lastSlash + 1).toLowerCase();
            }
        } catch (Exception e) {
            logger.debug("提取文件名失败", e);
        }
        return null;
    }

    /**
     * 确定文件类型
     */
    public String determineContentType(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String contentType = Files.probeContentType(path);

            if (contentType != null) {
                return contentType;
            }

            // 手动映射常见文件类型
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".pdf")) {
                return "application/pdf";
            } else if (fileName.endsWith(".png")) {
                return "image/png";
            } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                return "image/gif";
            } else if (fileName.endsWith(".svg")) {
                return "image/svg+xml";
            } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                return "text/html";
            } else if (fileName.endsWith(".txt")) {
                return "text/plain";
            } else if (fileName.endsWith(".json")) {
                return "application/json";
            } else if (fileName.endsWith(".xml")) {
                return "text/xml";
            } else {
                return "application/octet-stream";
            }

        } catch (Exception e) {
            logger.warn("⚠️ 无法确定文件类型: {}", filePath, e);
            return "application/octet-stream";
        }
    }

    // ========================================
    // URL安全校验相关方法
    // ========================================

    /**
     * 验证URL是否符合安全策略
     * 
     * @param urlString 待验证的URL
     * @return 验证结果对象
     */
    public UrlValidationResult validateUrlSecurity(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return UrlValidationResult.failed("URL不能为空");
        }

        try {
            // 解析URL
            java.net.URL url = new java.net.URL(urlString.trim());
            String host = url.getHost();

            if (host == null || host.trim().isEmpty()) {
                return UrlValidationResult.failed("URL格式无效，无法解析主机名");
            }

            host = host.toLowerCase();

            // 1. 黑名单优先：检查是否在不信任站点列表中
            if (isUntrustedSite(host)) {
                logger.warn("🚫 URL被拒绝：来自不信任站点 - Host: {}, URL: {}", host, urlString);
                return UrlValidationResult.failed("安全拒绝：该站点在不信任列表中");
            }

            // 2. 白名单检查：如果配置了信任站点，则必须在信任列表中
            if (hasTrustedSitesConfig()) {
                if (!isTrustedSite(host)) {
                    logger.warn("🚫 URL被拒绝：不在信任站点列表中 - Host: {}, URL: {}", host, urlString);
                    return UrlValidationResult.failed("安全拒绝：该站点不在信任列表中");
                }
            }

            logger.debug("✅ URL安全校验通过 - Host: {}", host);
            return UrlValidationResult.success();

        } catch (Exception e) {
            logger.error("❌ URL解析失败 - URL: {}, Error: {}", urlString, e.getMessage());
            return UrlValidationResult.failed("URL格式无效: " + e.getMessage());
        }
    }

    /**
     * 检查字符串是否已经被URL编码
     */
    private boolean isAlreadyEncoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 更严格的检查：检查是否整个字符串看起来像一个完整的URL
        // 如果包含://，认为这是一个完整的URL，而不是单独的已编码段
        if (str.contains("://")) {
            return false;
        }
        // 检查是否看起来像一个已经编码的完整URL（包含编码的协议分隔符）
        if (str.contains("%3A%2F%2F")) {
            return true;
        }
        // 检查是否包含%后跟2个十六进制字符，认为已编码
        return str.matches(".*%[0-9A-Fa-f]{2}.*");
    }

    /**
     * 检查是否配置了信任站点
     */
    private boolean hasTrustedSitesConfig() {
        return trustedSitesConfig != null && !trustedSitesConfig.trim().isEmpty();
    }

    /**
     * 检查主机是否在信任站点列表中
     */
    private boolean isTrustedSite(String host) {
        if (!hasTrustedSitesConfig()) {
            return true; // 未配置信任站点，默认允许所有
        }

        java.util.List<String> trustedSites = parseSites(trustedSitesConfig);
        return matchesSites(host, trustedSites);
    }

    /**
     * 检查主机是否在不信任站点列表中
     */
    private boolean isUntrustedSite(String host) {
        if (untrustedSitesConfig == null || untrustedSitesConfig.trim().isEmpty()) {
            return false; // 未配置不信任站点，默认不拒绝
        }

        java.util.List<String> untrustedSites = parseSites(untrustedSitesConfig);
        return matchesSites(host, untrustedSites);
    }

    /**
     * 解析站点配置字符串
     */
    private java.util.List<String> parseSites(String sitesConfig) {
        java.util.List<String> sites = new java.util.ArrayList<>();
        if (sitesConfig == null || sitesConfig.trim().isEmpty()) {
            return sites;
        }

        String[] parts = sitesConfig.split(",");
        for (String part : parts) {
            String site = part.trim().toLowerCase();
            if (!site.isEmpty()) {
                sites.add(site);
            }
        }

        return sites;
    }

    /**
     * 检查主机是否匹配站点列表
     * 支持通配符模式，例如：*.example.com
     */
    private boolean matchesSites(String host, java.util.List<String> sites) {
        for (String site : sites) {
            if (matchesSite(host, site)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查主机是否匹配单个站点模式
     * 
     * 支持的模式：
     * 1. 精确匹配：example.com
     * 2. 通配符匹配：*.example.com（匹配所有子域名）
     * 3. 顶级域名匹配：example.com（也匹配 www.example.com, api.example.com 等）
     */
    private boolean matchesSite(String host, String sitePattern) {
        // 精确匹配
        if (host.equals(sitePattern)) {
            return true;
        }

        // 通配符匹配：*.example.com
        if (sitePattern.startsWith("*.")) {
            String domain = sitePattern.substring(2); // 去掉 "*."
            // 检查是否是该域名或其子域名
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }

        // 域名后缀匹配（子域名匹配）
        // 例如：配置 example.com，则 www.example.com, api.example.com 都匹配
        if (host.endsWith("." + sitePattern)) {
            return true;
        }

        return false;
    }

    /**
     * URL验证结果类
     */
    public static class UrlValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private UrlValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static UrlValidationResult success() {
            return new UrlValidationResult(true, null);
        }

        public static UrlValidationResult failed(String errorMessage) {
            return new UrlValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

}