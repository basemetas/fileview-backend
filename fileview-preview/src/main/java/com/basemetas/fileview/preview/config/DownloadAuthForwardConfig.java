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
package com.basemetas.fileview.preview.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网络文件下载鉴权转发配置。
 *
 * 用于从当前预览请求中抽取指定的请求头或 Cookie，并按规则转发到
 * 预览服务的网络下载请求中，避免在开源项目中写死任何业务鉴权字段名。
 */
@Component
@ConfigurationProperties(prefix = "fileview.network.download.auth-forward")
public class DownloadAuthForwardConfig {

    /**
     * 是否启用鉴权转发。
     */
    private boolean enabled = false;

    /**
     * 是否仅允许向当前请求同 host 的下载 URL 转发鉴权信息。
     * 开启后只允许同 host，忽略 allowedHostPatterns。
     */
    private boolean sameHostOnly = true;

    /**
     * 允许转发鉴权信息的目标 host 模式列表。
     * 仅在 sameHostOnly=false 时生效，用作跨 host 白名单。
     * 支持 example.com、*.example.com 等写法。
     */
    private List<String> allowedHostPatterns = new ArrayList<>();

    /**
     * 抽取与转发规则列表。
     */
    private List<ForwardRule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSameHostOnly() {
        return sameHostOnly;
    }

    public void setSameHostOnly(boolean sameHostOnly) {
        this.sameHostOnly = sameHostOnly;
    }

    public List<String> getAllowedHostPatterns() {
        return allowedHostPatterns;
    }

    public void setAllowedHostPatterns(List<String> allowedHostPatterns) {
        this.allowedHostPatterns =
                allowedHostPatterns != null ? allowedHostPatterns : new ArrayList<>();
    }

    public List<ForwardRule> getRules() {
        return rules;
    }

    public void setRules(List<ForwardRule> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    public boolean hasRules() {
        return rules != null && !rules.isEmpty();
    }

    public enum SourceType {
        HEADER,
        COOKIE
    }

    public enum TargetType {
        HEADER,
        COOKIE
    }

    public static class ForwardRule {
        private SourceType sourceType = SourceType.COOKIE;
        private String sourceName;
        private TargetType targetType = TargetType.COOKIE;
        private String targetName;
        private String valuePrefix = "";
        private String valueSuffix = "";

        public SourceType getSourceType() {
            return sourceType;
        }

        public void setSourceType(SourceType sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public TargetType getTargetType() {
            return targetType;
        }

        public void setTargetType(TargetType targetType) {
            this.targetType = targetType;
        }

        public String getTargetName() {
            return targetName;
        }

        public void setTargetName(String targetName) {
            this.targetName = targetName;
        }

        public String getValuePrefix() {
            return valuePrefix;
        }

        public void setValuePrefix(String valuePrefix) {
            this.valuePrefix = valuePrefix != null ? valuePrefix : "";
        }

        public String getValueSuffix() {
            return valueSuffix;
        }

        public void setValueSuffix(String valueSuffix) {
            this.valueSuffix = valueSuffix != null ? valueSuffix : "";
        }
    }
}
