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
package com.basemetas.fileview.preview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件预览服务启动类
 * 
 * 提供文件预览功能的独立微服务，支持：
 * 1. 服务器文件预览 - 文件已在存储服务上
 * 2. 本地文件上传预览 - 需要先上传到存储服务
 * 3. 网络文件下载预览 - 需要先下载到存储服务
 * 
 * 核心特性：
 * - 智能缓存策略
 * - 事件驱动架构
 * - 多格式支持
 * - 高并发处理
 * 
 * @author 夫子
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class FilePreviewApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(FilePreviewApplication.class, args);
    }
}