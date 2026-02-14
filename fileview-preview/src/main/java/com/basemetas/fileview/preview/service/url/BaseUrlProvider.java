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

/**
 * 提供基础URL（baseUrl）的抽象接口。
 * 优先从当前请求上下文解析，失败时回退到配置值。
 */
public interface BaseUrlProvider {
    /**
     * 获取基础URL，格式：scheme://host:port/contextPath
     */
    String getBaseUrl();
}
