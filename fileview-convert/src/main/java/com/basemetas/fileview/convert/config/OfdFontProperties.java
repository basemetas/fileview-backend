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
package com.basemetas.fileview.convert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * OFD字体配置属性
 * 
 * 支持配置化的字体映射和兜底策略
 * 
 * @author 夫子
 */
@Component
@ConfigurationProperties(prefix = "ofd.font")
public class OfdFontProperties {

    /**
     * 字体目录扫描路径（多个路径用逗号分隔）
     * 默认为系统标准字体目录
     */
    private List<String> scanPaths = new ArrayList<>();

    public List<String> getScanPaths() {
        return scanPaths;
    }

    public void setScanPaths(List<String> scanPaths) {
        this.scanPaths = scanPaths;
    }

    @PostConstruct
    public void init() {
    }
}
