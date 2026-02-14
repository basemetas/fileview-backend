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

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从请求上下文中解析 baseUrl 的提供者；解析失败时回退到配置的 baseUrl。
 */
@Component
public class RequestAwareBaseUrlProvider implements BaseUrlProvider {
    private static final Logger logger = LoggerFactory.getLogger(RequestAwareBaseUrlProvider.class);

    @Value("${fileview.preview.url.base-url}")
    private String baseUrl;

    @Override
    public String getBaseUrl() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // 优先从 X-Forwarded-Proto 获取协议（代理场景）
                String scheme = request.getHeader("X-Forwarded-Proto");
                if (scheme == null || scheme.trim().isEmpty()) {
                    scheme = request.getScheme();
                }

                // 优先从代理头获取客户端实际使用的主机名和端口
                String forwardedHost = request.getHeader("X-Forwarded-Host");
                String forwardedPort = request.getHeader("X-Forwarded-Port");
                String host = request.getHeader("Host");
                
                // 调试日志：查看所有相关请求头
                String origin = request.getHeader("Origin");
                String referer = request.getHeader("Referer");
                logger.debug("📊 请求头调试 - Origin: {}, Referer: {}, Host: {}, X-Forwarded-Host: {}, X-Forwarded-Port: {}",
                        origin, referer, host, forwardedHost, forwardedPort);
                
                String serverName;
                int serverPort;

                // 优先级：X-Forwarded-Host > Origin > Referer > Host > request.getServerName()
                if (forwardedHost != null && !forwardedHost.trim().isEmpty()) {
                    // 从 X-Forwarded-Host 解析
                    int colonIndex = forwardedHost.indexOf(':');
                    if (colonIndex > 0) {
                        serverName = forwardedHost.substring(0, colonIndex);
                        try {
                            serverPort = Integer.parseInt(forwardedHost.substring(colonIndex + 1));
                        } catch (NumberFormatException e) {
                            serverPort = request.getServerPort();
                        }
                    } else {
                        serverName = forwardedHost;
                        // 如果有 X-Forwarded-Port，优先使用
                        if (forwardedPort != null && !forwardedPort.trim().isEmpty()) {
                            try {
                                serverPort = Integer.parseInt(forwardedPort);
                            } catch (NumberFormatException e) {
                                serverPort = request.getServerPort();
                            }
                        } else {
                            serverPort = request.getServerPort();
                        }
                    }
                    logger.debug("🌐 从 X-Forwarded-Host 解析 - ForwardedHost: {}, ForwardedPort: {}, ServerName: {}, ServerPort: {}", 
                            forwardedHost, forwardedPort, serverName, serverPort);
                } else if (origin != null && !origin.trim().isEmpty()) {
                    // 从 Origin 头解析（跨域请求场景）
                    try {
                        java.net.URI uri = new java.net.URI(origin);
                        serverName = uri.getHost();
                        serverPort = uri.getPort();
                        if (serverPort == -1) {
                            serverPort = "https".equals(uri.getScheme()) ? 443 : 80;
                        }
                        // 覆盖 scheme
                        if (uri.getScheme() != null) {
                            scheme = uri.getScheme();
                        }
                        logger.debug("🌐 从 Origin 头解析 - Origin: {}, ServerName: {}, ServerPort: {}", origin, serverName, serverPort);
                    } catch (Exception e) {
                        logger.warn("⚠️ Origin 解析失败，降级到 Host: {}", origin, e);
                        serverName = request.getServerName();
                        serverPort = request.getServerPort();
                    }
                } else if (referer != null && !referer.trim().isEmpty()) {
                    // 从 Referer 头解析（同域请求场景）
                    try {
                        java.net.URI uri = new java.net.URI(referer);
                        serverName = uri.getHost();
                        serverPort = uri.getPort();
                        if (serverPort == -1) {
                            serverPort = "https".equals(uri.getScheme()) ? 443 : 80;
                        }
                        // 覆盖 scheme
                        if (uri.getScheme() != null) {
                            scheme = uri.getScheme();
                        }
                        logger.debug("🌐 从 Referer 头解析 - Referer: {}, ServerName: {}, ServerPort: {}", referer, serverName, serverPort);
                    } catch (Exception e) {
                        logger.warn("⚠️ Referer 解析失败，降级到 Host: {}", referer, e);
                        serverName = request.getServerName();
                        serverPort = request.getServerPort();
                    }
                } else if (host != null && !host.trim().isEmpty()) {
                    // 从 Host 头解析
                    int colonIndex = host.indexOf(':');
                    if (colonIndex > 0) {
                        serverName = host.substring(0, colonIndex);
                        try {
                            serverPort = Integer.parseInt(host.substring(colonIndex + 1));
                        } catch (NumberFormatException e) {
                            serverPort = request.getServerPort();
                        }
                    } else {
                        serverName = host;
                        serverPort = request.getServerPort();
                    }
                    logger.debug("🌐 从 Host 头解析 - Host: {}, ServerName: {}, ServerPort: {}", host, serverName, serverPort);
                } else {
                    // 最后回退到 request 对象
                    serverName = request.getServerName();
                    serverPort = request.getServerPort();
                    logger.debug("⚠️ 从 request 解析 - ServerName: {}, ServerPort: {}", serverName, serverPort);
                }

                // 优先从 X-Forwarded-Prefix 请求头获取 contextPath
                String headerPrefix = request.getHeader("X-Forwarded-Prefix");
                String requestContextPath = request.getContextPath();
                String contextPath;

                if (headerPrefix != null && !headerPrefix.trim().isEmpty()) {
                    contextPath = normalizeContextPath(headerPrefix);
                    logger.info("🌐 使用 X-Forwarded-Prefix - HeaderPrefix: {}, Normalized: {}, RequestContextPath: {}",
                            headerPrefix, contextPath, requestContextPath);
                } else {
                    contextPath = normalizeContextPath(requestContextPath);
                    logger.debug("🌐 使用 request.getContextPath - ContextPath: {}, Normalized: {}",
                            requestContextPath, contextPath);
                }

                StringBuilder url = new StringBuilder();
                url.append(scheme).append("://").append(serverName);

                // 只在非标准端口时添加端口号
                if (("http".equals(scheme) && serverPort != 80) ||
                        ("https".equals(scheme) && serverPort != 443)) {
                    url.append(":").append(serverPort);
                }

                // 上下文路径不为空且不是根路径时追加
                if (contextPath != null && !contextPath.isEmpty() && !"/".equals(contextPath)) {
                    url.append(contextPath);
                }

                String dynamicUrl = url.toString();
                logger.info("✅ 动态生成baseUrl - Result: {}", dynamicUrl);
                return dynamicUrl;
            }
        } catch (Exception e) {
            logger.warn("⚠️ 无法从请求中获取baseUrl，使用配置值: {}", baseUrl, e);
        }

        // 回退：使用配置的baseUrl
        return baseUrl;
    }

    private String normalizeContextPath(String raw) {
        if (raw == null) {
            return "";
        }
        String ctx = raw.trim().replace("\\", "/");
        if (ctx.isEmpty()) {
            return "";
        }
        if ("/".equals(ctx)) {
            return "/";
        }
        if (!ctx.startsWith("/")) {
            ctx = "/" + ctx;
        }
        // 压缩多余的斜杠
        ctx = ctx.replaceAll("/+", "/");
        // 去掉尾部斜杠（保留根路径）
        if (ctx.length() > 1 && ctx.endsWith("/")) {
            ctx = ctx.substring(0, ctx.length() - 1);
        }
        return ctx;
    }
}
