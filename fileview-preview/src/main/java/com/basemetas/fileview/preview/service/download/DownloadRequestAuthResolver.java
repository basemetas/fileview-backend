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

import com.basemetas.fileview.preview.config.DownloadAuthForwardConfig;
import com.basemetas.fileview.preview.model.download.DownloadRequestAuthContext;
import com.basemetas.fileview.preview.utils.EncodingUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 从当前预览请求中解析下载所需的鉴权上下文。
 */
@Service
public class DownloadRequestAuthResolver {

    private static final Logger logger = LoggerFactory.getLogger(DownloadRequestAuthResolver.class);

    @Autowired
    private DownloadAuthForwardConfig authForwardConfig;

    @Autowired
    private EncodingUtils encodingUtils;

    public DownloadRequestAuthContext resolve(HttpServletRequest request, String networkFileUrl) {
        if (request == null || !authForwardConfig.isEnabled() || !authForwardConfig.hasRules()) {
            return DownloadRequestAuthContext.empty();
        }

        if (!isForwardingAllowed(request, networkFileUrl)) {
            logger.debug("⏭️ 跳过下载鉴权转发 - URL: {}", networkFileUrl);
            return DownloadRequestAuthContext.empty();
        }

        DownloadRequestAuthContext authContext = new DownloadRequestAuthContext();
        for (DownloadAuthForwardConfig.ForwardRule rule : authForwardConfig.getRules()) {
            if (rule == null || rule.getSourceName() == null || rule.getSourceName().trim().isEmpty()) {
                continue;
            }

            if (shouldSkipCookieHeaderRule(rule)) {
                continue;
            }

            String sourceValue = extractSourceValue(request, rule);
            if (sourceValue == null || sourceValue.isBlank()) {
                continue;
            }

            String targetName = normalizeTargetName(rule);
            String targetValue = rule.getValuePrefix() + sourceValue.trim() + rule.getValueSuffix();

            DownloadAuthForwardConfig.TargetType targetType = rule.getTargetType();
            if (targetType == DownloadAuthForwardConfig.TargetType.HEADER) {
                authContext.getHeaders().put(targetName, targetValue);
            } else if (targetType == DownloadAuthForwardConfig.TargetType.COOKIE) {
                authContext.getCookies().put(targetName, targetValue);
            } else {
                logger.warn("⚠️ 跳过无效的鉴权转发目标类型 - SourceName: {}, TargetName: {}, TargetType: {}",
                        rule.getSourceName(), targetName, targetType);
            }
        }

        if (authContext.hasNoForwardedAuth()) {
            return DownloadRequestAuthContext.empty();
        }

        authContext.setAuthContextHash(buildAuthContextHash(authContext));
        logger.debug("✅ 下载鉴权上下文解析完成 - Headers: {}, Cookies: {}, AuthHash: {}",
                authContext.getHeaders().keySet(),
                authContext.getCookies().keySet(),
                authContext.resolveAuthContextHash());
        return authContext;
    }

    private boolean shouldSkipCookieHeaderRule(DownloadAuthForwardConfig.ForwardRule rule) {
        DownloadAuthForwardConfig.SourceType sourceType = rule.getSourceType();
        String sourceName = rule.getSourceName();
        if (sourceType == DownloadAuthForwardConfig.SourceType.HEADER && "cookie".equalsIgnoreCase(sourceName)) {
            logger.warn("⚠️ 跳过无效的鉴权转发来源配置 - Cookie 只能通过 COOKIE 类型传递, SourceName: {}",
                    sourceName);
            return true;
        }

        DownloadAuthForwardConfig.TargetType targetType = rule.getTargetType();
        String targetName = normalizeTargetName(rule);
        if (targetType == DownloadAuthForwardConfig.TargetType.HEADER && "cookie".equalsIgnoreCase(targetName)) {
            logger.warn("⚠️ 跳过无效的鉴权转发目标配置 - Cookie 只能通过 COOKIE 类型传递, TargetName: {}",
                    targetName);
            return true;
        }

        return false;
    }

    private boolean isForwardingAllowed(HttpServletRequest request, String networkFileUrl) {
        if (networkFileUrl == null || networkFileUrl.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = URI.create(networkFileUrl.trim());
            String targetHost = uri.getHost();
            if (targetHost == null || targetHost.isBlank()) {
                return false;
            }

            targetHost = targetHost.toLowerCase();
            if (authForwardConfig.isSameHostOnly()) {
                // 同 host 模式下只允许转发到当前请求所在 host，忽略跨 host 白名单。
                String requestHost = request.getServerName();
                return requestHost != null && requestHost.equalsIgnoreCase(targetHost);
            }

            List<String> allowedHostPatterns = authForwardConfig.getAllowedHostPatterns();
            if (allowedHostPatterns != null && !allowedHostPatterns.isEmpty()) {
                // 关闭同 host 限制后，如果配置了白名单，则仅允许转发到白名单中的 host。
                return matchesAnyHostPattern(targetHost, allowedHostPatterns);
            }

            // 关闭同 host 限制且未配置白名单时，默认允许转发到任意 host。
            return true;
        } catch (Exception e) {
            logger.warn("⚠️ 解析下载鉴权目标地址失败，已跳过鉴权转发 - URL: {}", networkFileUrl, e);
            return false;
        }
    }

    private boolean matchesAnyHostPattern(String targetHost, List<String> allowedHostPatterns) {
        for (String pattern : allowedHostPatterns) {
            if (matchesHostPattern(targetHost, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesHostPattern(String host, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }

        String normalizedPattern = pattern.trim().toLowerCase();
        if (host.equals(normalizedPattern)) {
            // 精确匹配根域名本身，例如 example.com -> example.com。
            return true;
        }

        if (normalizedPattern.startsWith("*.")) {
            String domain = normalizedPattern.substring(2);
            // 通配子域名模式同时允许根域名和任意层级子域名，
            // 例如 *.example.com -> example.com / a.example.com / b.c.example.com。
            // 这里用 "." + domain 来保证命中的是完整域名边界，
            // 避免 badexample.com 这类“纯字符串后缀相同”的误匹配。
            return host.equals(domain) || host.endsWith("." + domain);
        }

        // 非通配模式下不再做后缀匹配，避免 example.com 误放行 files.example.com。
        return false;
    }

    private String extractSourceValue(HttpServletRequest request, DownloadAuthForwardConfig.ForwardRule rule) {
        String sourceName = rule.getSourceName().trim();
        DownloadAuthForwardConfig.SourceType sourceType = rule.getSourceType();
        if (sourceType == DownloadAuthForwardConfig.SourceType.HEADER) {
            return request.getHeader(sourceName);
        }

        if (sourceType == DownloadAuthForwardConfig.SourceType.COOKIE) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }

            for (Cookie cookie : cookies) {
                if (cookie != null && sourceName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }

            return null;
        }

        logger.warn("⚠️ 跳过无效的鉴权转发来源类型 - SourceName: {}, SourceType: {}",
                sourceName, sourceType);
        return null;
    }

    private String normalizeTargetName(DownloadAuthForwardConfig.ForwardRule rule) {
        if (rule.getTargetName() == null || rule.getTargetName().trim().isEmpty()) {
            return rule.getSourceName().trim();
        }
        return rule.getTargetName().trim();
    }

    private String buildAuthContextHash(DownloadRequestAuthContext authContext) {
        StringBuilder builder = new StringBuilder();
        authContext.getHeaders().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> builder
                        .append("H:")
                        .append(entry.getKey().toLowerCase())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n'));

        authContext.getCookies().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> builder
                        .append("C:")
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n'));

        return encodingUtils.calculateMD5(builder.toString());
    }
}
