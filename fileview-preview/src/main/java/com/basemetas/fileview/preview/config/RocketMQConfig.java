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

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

/**
 * RocketMQ配置类
 * 
 * 功能：
 * - 配置RocketMQ生产者和消费者
 * - 提供消息队列连接验证
 * - 管理预览相关的主题和标签
 * 
 * @author 夫子
 */
@Configuration
@ConditionalOnProperty(name = "mq.engine", havingValue = "rocketmq")
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RocketMQConfig.class);
    
    @Value("${rocketmq.name-server:}")
    private String nameServer;
    
    @Value("${rocketmq.producer.group:preview-producer-group}")
    private String producerGroup;
    
    @Value("${rocketmq.consumer.group:preview-consumer-group}")
    private String consumerGroup;
    
    // 预览相关的主题和标签
    public static final String PREVIEW_TOPIC = "preview-events";
    public static final String CONVERSION_TOPIC = "file-events";
    public static final String DOWNLOAD_TOPIC = "download-tasks";
    
    public static final String TAG_PREVIEW_REQUEST = "PREVIEW_REQUEST";
    public static final String TAG_PREVIEW_READY = "PREVIEW_READY";
    public static final String TAG_PREVIEW_FAILED = "PREVIEW_FAILED";
    public static final String TAG_CONVERSION_TRIGGER = "CONVERSION_TRIGGER";
    
    @PostConstruct
    public void init() {
        logger.info("🚀 RocketMQ配置初始化完成");
        logger.info("📡 NameServer: {}", nameServer);
        logger.info("📤 Producer Group: {}", producerGroup);
        logger.info("📥 Consumer Group: {}", consumerGroup);
        logger.info("📋 Preview Topic: {}", PREVIEW_TOPIC);
        logger.info("📋 Conversion Topic: {}", CONVERSION_TOPIC);
        logger.info("📋 Download Topic: {}", DOWNLOAD_TOPIC);
        
        // 记录支持的标签
        logger.info("🏷️ 支持的消息标签:");
        logger.info("   - {}: 预览请求", TAG_PREVIEW_REQUEST);
        logger.info("   - {}: 预览准备完成", TAG_PREVIEW_READY);
        logger.info("   - {}: 预览失败", TAG_PREVIEW_FAILED);
        logger.info("   - {}: 转换触发", TAG_CONVERSION_TRIGGER);
    }
    
    /**
     * 获取预览主题目标地址
     * @param tag 消息标签
     * @return 完整的目标地址
     */
    public static String getPreviewDestination(String tag) {
        return PREVIEW_TOPIC + ":" + tag;
    }
    
    /**
     * 获取转换主题目标地址
     * @param tag 消息标签
     * @return 完整的目标地址
     */
    public static String getConversionDestination(String tag) {
        return CONVERSION_TOPIC + ":" + tag;
    }
    
    /**
     * 获取下载主题目标地址
     * @return 完整的目标地址
     */
    public static String getDownloadDestination() {
        return DOWNLOAD_TOPIC;
    }
}