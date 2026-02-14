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

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;

/**
 * RocketMQ 配置类
 * 用于配置文件转换服务的消息队列功能
 * 
 * 主要功能：
 * 1. 配置 RocketMQ 生产者和消费者
 * 2. 设置消息重试机制
 * 3. 配置序列化方式
 * 4. 提供连接参数配置
 */
@Configuration
@ConditionalOnProperty(name = "mq.engine", havingValue = "rocketmq")
public class RocketMQConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RocketMQConfig.class);
    
    @Value("${rocketmq.name-server}")
    private String nameServer;
    
    @Value("${rocketmq.producer.group}")
    private String producerGroup;
    
    @PostConstruct
    public void init() {
        logger.info("🚀 Initializing RocketMQ Config...");
        logger.info("📡 NameServer: {}", nameServer);
        logger.info("👥 Producer Group: {}", producerGroup);
    }
    
    /**
     * 配置消息序列化转换器
     * 确保 FileEvent 对象能正确序列化和反序列化
     */
    @Bean
    @Primary
    public org.springframework.messaging.converter.MessageConverter messageConverter() {
        logger.info("📦 Configuring Jackson Message Converter...");
        return new org.springframework.messaging.converter.MappingJackson2MessageConverter();
    }
    
    /**
     * 配置自定义RocketMQ模板（如果需要特殊配置）
     */
    @Bean
    public DefaultMQProducer defaultMQProducer() {
        logger.info("🔧 Creating DefaultMQProducer with nameServer: {}", nameServer);
        DefaultMQProducer producer = new DefaultMQProducer();
        producer.setProducerGroup(producerGroup);
        producer.setNamesrvAddr(nameServer);
        // 设置发送超时时间
        producer.setSendMsgTimeout(3000);
        // 设置重试次数
        producer.setRetryTimesWhenSendFailed(2);
        producer.setRetryTimesWhenSendAsyncFailed(2);
        return producer;
    }
}