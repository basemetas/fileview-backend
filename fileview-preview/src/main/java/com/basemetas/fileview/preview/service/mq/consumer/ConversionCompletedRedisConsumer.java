
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
package com.basemetas.fileview.preview.service.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis Streams 的转换完成事件消费者。
 */
@Component
@ConditionalOnProperty(name = "mq.engine", havingValue = "redis")
public class ConversionCompletedRedisConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ConversionCompletedRedisConsumer.class);

    private static final String STREAM_KEY = "stream:preview-events";
    private static final String GROUP = "preview-conversion-consumer";
    private static final String CONSUMER_NAME_PREFIX = "preview-convert-";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired
    private ConversionCompletedConsumer conversionCompletedConsumer;

    /**
     * Stream 定期清理保留长度（消费后清理策略）
     * 默认保留最近 10000 条已确认消息
     */
    @Value("${mq.redis.stream.trim-length:10000}")
    private long streamTrimLength;

    private String consumerName;

    @PostConstruct
    public void init() {
        if (redisTemplate == null) {
            logger.warn("⚠️ RedisTemplate 未注入，ConversionCompletedRedisConsumer 将不会工作");
            return;
        }
        this.consumerName = CONSUMER_NAME_PREFIX + System.currentTimeMillis();

        // 使用分布式锁避免并发创建冲突
        String lockKey = "lock:consumer-group:init:" + STREAM_KEY + ":" + GROUP;
        Boolean locked = null;
        try {
            locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
            
            if (Boolean.TRUE.equals(locked)) {
                createStreamAndGroup();
            } else {
                // 等待其他实例创建完成
                Thread.sleep(2000);
                logger.info("ℹ️ [Redis] 等待其他实例创建消费组");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("⚠️ [Redis] 初始化被中断", e);
        } catch (Exception e) {
            handleGroupCreationError(e);
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private void createStreamAndGroup() {
        try {
            // 先确保 Stream 存在
            Boolean exists = redisTemplate.hasKey(STREAM_KEY);
            if (exists == null || !exists) {
                redisTemplate.opsForStream().add(STREAM_KEY, 
                    java.util.Collections.singletonMap("_init", "true"));
                logger.info("✅ [Redis] 创建 Stream - {}", STREAM_KEY);
            }
            
            // 创建消费组(使用 0-0 从头消费)
            redisTemplate.opsForStream().createGroup(
                STREAM_KEY, ReadOffset.from("0-0"), GROUP);
            logger.info("✅ [Redis] 创建消费组成功 - stream: {}, group: {}", STREAM_KEY, GROUP);
        } catch (Exception e) {
            // 二次检查BUSYGROUP(可能锁失效后被其他实例创建)
            String errorMsg = e.getMessage();
            Throwable cause = e.getCause();
            if ((errorMsg != null && errorMsg.contains("BUSYGROUP")) ||
                (cause != null && cause.getMessage() != null && 
                 cause.getMessage().contains("BUSYGROUP"))) {
                logger.info("ℹ️ [Redis] 消费组已存在 - group: {}", GROUP);
            } else {
                throw e; // 其他异常重新抛出
            }
        }
    }

    private void handleGroupCreationError(Exception e) {
        String msg = e.getMessage();
        Throwable cause = e.getCause();
        
        if ((msg != null && msg.contains("BUSYGROUP")) ||
            (cause != null && cause.getMessage() != null && 
             cause.getMessage().contains("BUSYGROUP"))) {
            logger.info("ℹ️ [Redis] 消费组已存在(并发创建正常)");
        } else {
            logger.error("❌ [Redis] 消费组创建异常", e);
        }
    }

    @Scheduled(fixedDelay = 200)
    public void consume() {
        if (redisTemplate == null || objectMapper == null) {
            return;
        }

        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, consumerName),
                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.from(">"))
            );

            if (records == null || records.isEmpty()) {
                return;
            }

            for (MapRecord<String, Object, Object> record : records) {
                Map<Object, Object> value = record.getValue();
                try {
                    Object payload = value.get("payload");
                    if (payload == null) {
                        logger.warn("⚠️ [Redis] 收到空payload，跳过 - id: {}", record.getId());
                        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = objectMapper.readValue(payload.toString(), Map.class);
                    conversionCompletedConsumer.onMessage(event);
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
                } catch (Exception e) {
                    logger.error("❌ [Redis] 处理转换事件失败 - id: {}", record.getId(), e);
                }
            }
        } catch (Exception e) {
            logger.error("💥 [Redis] 读取 preview-events 流异常", e);
        }
    }

    /**
     * 定期清理 Stream 中已确认的旧消息，防止内存积压
     * 每小时执行一次，保留最近 N 条消息
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void trimStream() {
        if (redisTemplate == null) {
            return;
        }

        try {
            // 使用 XTRIM 命令清理旧消息，保留最近 trimLength 条
            Long trimmed = redisTemplate.opsForStream().trim(STREAM_KEY, streamTrimLength, true);
            logger.info("✅ [Redis] Stream 定期清理完成 - stream: {}, 保留: {}, 清理: {}",
                    STREAM_KEY, streamTrimLength, trimmed != null ? trimmed : 0);
        } catch (Exception e) {
            logger.warn("⚠️ [Redis] Stream 清理失败 - stream: {}", STREAM_KEY, e);
        }
    }
}
