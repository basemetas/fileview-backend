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
package com.basemetas.fileview.preview.service.mq.producer;

import com.basemetas.fileview.preview.service.mq.event.EventChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Redis Streams 的事件发布实现。
 */
@Component
@ConditionalOnProperty(name = "mq.engine", havingValue = "redis")
public class RedisStreamEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RedisStreamEventPublisher.class);

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * Stream 最大长度（使用 MAXLEN 策略防止无限膨胀）
     * 默认保留最近 50000 条消息
     */
    @Value("${mq.redis.stream.max-length:50000}")
    private long streamMaxLength;

    @Override
    public void publish(EventChannel channel, Object payload, Map<String, Object> headers) {
        if (redisTemplate == null || objectMapper == null) {
            logger.warn("⚠️ RedisTemplate/ObjectMapper 不可用，跳过事件发送 - channel: {}", channel);
            return;
        }

        String streamKey = resolveStreamKey(channel);
        Map<String, Object> body = new HashMap<>();
        try {
            body.put("payload", objectMapper.writeValueAsString(payload));
            if (headers != null) {
                body.putAll(headers);
            }

            // 使用 MAXLEN ~ N 策略限制 Stream 长度，防止内存无限膨胀
            // Spring Data Redis 的 add 方法原生支持 maxlen 参数
            RecordId id = redisTemplate.opsForStream().add(streamKey, body);
            
            // 在添加后立即执行 trim 以控制长度（模拟 MAXLEN 效果）
            if (streamMaxLength > 0) {
                redisTemplate.opsForStream().trim(streamKey, streamMaxLength, true);
            }
            
            logger.info("✅ [Redis] 发送事件成功 - stream: {}, id: {}, maxLen: {}",
                    streamKey, id != null ? id.getValue() : "null", streamMaxLength);
        } catch (Exception e) {
            logger.error("❌ [Redis] 发送事件失败 - stream: {}", streamKey, e);
        }
    }

    private String resolveStreamKey(EventChannel channel) {
        switch (channel) {
            case FILE_EVENTS:
                return "stream:file-events";
            case PREVIEW_EVENTS:
                return "stream:preview-events";
            case CONVERT_EVENTS:
                return "stream:convert-events";
            case DOWNLOAD_TASKS:
                return "stream:download-tasks";
            default:
                throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
    }
}
