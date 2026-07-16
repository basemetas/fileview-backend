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

import com.basemetas.fileview.preview.model.download.DownloadRequestAuthContext;
import java.net.HttpURLConnection;
import java.net.http.HttpRequest;
import org.slf4j.Logger;

/**
 * 下载鉴权上下文的公共应用工具。
 *
 * 负责将鉴权上下文写入具体 HTTP 请求对象。
 */
public final class DownloadRequestAuthSupport {

    private DownloadRequestAuthSupport() {
    }

    public static void applyTo(HttpRequest.Builder builder, DownloadRequestAuthContext authContext,
                               Logger logger) {
        if (builder == null) {
            return;
        }
        applyInternal(authContext, builder::setHeader, logger);
    }

    public static void applyTo(HttpURLConnection connection, DownloadRequestAuthContext authContext,
                               Logger logger) {
        if (connection == null) {
            return;
        }
        applyInternal(authContext, connection::setRequestProperty, logger);
    }

    private static void applyInternal(DownloadRequestAuthContext authContext, HeaderWriter headerWriter,
                                      Logger logger) {
        if (authContext == null || authContext.hasNoForwardedAuth() || headerWriter == null) {
            return;
        }

        StringBuilder cookieBuilder = new StringBuilder();
        authContext.getHeaders().forEach((name, value) -> {
            if (isBlank(name) || isBlank(value)) {
                return;
            }
            if (isManagedCookieHeader(name)) {
                logIgnoredManagedCookieHeader(logger);
                return;
            }
            if (isUnsafeHeader(name)) {
                logIgnoredUnsafeHeader(logger, name);
                return;
            }
            headerWriter.set(name, value);
        });

        authContext.getCookies().forEach((name, value) -> {
            if (isBlank(name) || isBlank(value)) {
                return;
            }
            appendCookie(cookieBuilder, name + "=" + value);
        });

        if (cookieBuilder.length() > 0) {
            headerWriter.set("Cookie", cookieBuilder.toString());
        }
    }

    private static boolean isUnsafeHeader(String name) {
        return "host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name);
    }

    private static boolean isManagedCookieHeader(String name) {
        return "cookie".equalsIgnoreCase(name);
    }

    private static void logIgnoredUnsafeHeader(Logger logger, String name) {
        if (logger != null) {
            logger.debug("⏭️ 忽略不安全的透传请求头 - Header: {}", name);
        }
    }

    private static void logIgnoredManagedCookieHeader(Logger logger) {
        if (logger != null) {
            logger.debug("⏭️ 忽略通过 HEADER 透传的 Cookie 请求头 - Cookie 只能通过 COOKIE 类型传递");
        }
    }

    private static void appendCookie(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface HeaderWriter {
        void set(String name, String value);
    }
}
