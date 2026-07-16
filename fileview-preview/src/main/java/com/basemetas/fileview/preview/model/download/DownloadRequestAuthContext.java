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
package com.basemetas.fileview.preview.model.download;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网络文件下载时透传的鉴权上下文。
 *
 * 仅保存被配置规则选中的请求头与 Cookie，不绑定任何业务字段名。
 */
public class DownloadRequestAuthContext {

    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, String> cookies = new LinkedHashMap<>();

    /**
     * 当前鉴权上下文的稳定摘要。
     *
     * 不参与实际鉴权，也不是可回放的令牌；
     * 当前仅作为鉴权上下文的辅助标识保留，便于日志排查和后续扩展。
     *
     * 默认值 public 表示当前下载请求没有额外透传的鉴权上下文。
     */
    private String authContextHash = "public";

    public static DownloadRequestAuthContext empty() {
        return new DownloadRequestAuthContext();
    }

    public boolean hasNoForwardedAuth() {
        return (headers == null || headers.isEmpty())
                && (cookies == null || cookies.isEmpty());
    }

    /**
     * 返回当前鉴权上下文的稳定摘要。
     */
    public String resolveAuthContextHash() {
        if (authContextHash == null || authContextHash.trim().isEmpty()) {
            return "public";
        }
        return authContextHash;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new LinkedHashMap<>();
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public void setCookies(Map<String, String> cookies) {
        this.cookies = cookies != null ? cookies : new LinkedHashMap<>();
    }

    public String getAuthContextHash() {
        return authContextHash;
    }

    public void setAuthContextHash(String authContextHash) {
        this.authContextHash = authContextHash;
    }
}
