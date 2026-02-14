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
package com.basemetas.fileview.convert.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 构建信息日志打印器
 * 在应用启动时打印版本、编译时间等构建信息
 */
@Component
public class BuildInfoLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildInfoLogger.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final BuildProperties buildProperties;
    
    public BuildInfoLogger(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void logBuildInfo() {
        logger.info("=".repeat(80));
        logger.info("🚀 应用启动信息");
        logger.info("=".repeat(80));
        logger.info("📦 服务名称: {}", buildProperties.getName());
        // 流水线版本号（如果有）
        String releaseVersion = buildProperties.get("releaseVersion");
        if (releaseVersion != null && !"local-dev".equals(releaseVersion)) {
            logger.info("📌 发布版本: {}", releaseVersion);
        }
        logger.info("👥 构建组: {}", buildProperties.getGroup());
        logger.info("📝 Artifact: {}", buildProperties.getArtifact());
        logger.info("🕐 编译时间: {}", 
            buildProperties.getTime().atZone(ZoneId.systemDefault()).format(FORMATTER));
        logger.info("☕ Java版本: {}", System.getProperty("java.version"));
        logger.info("🖥️  操作系统: {} {}", 
            System.getProperty("os.name"), 
            System.getProperty("os.version"));
        logger.info("=".repeat(80));
    }
}
