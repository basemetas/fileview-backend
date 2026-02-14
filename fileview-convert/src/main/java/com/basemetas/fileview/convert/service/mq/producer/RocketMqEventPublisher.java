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
package com.basemetas.fileview.convert.service.mq.producer;

import com.basemetas.fileview.convert.service.mq.event.EventChannel;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 基于 RocketMQ 的事件发布实现。
 */
@Component
@ConditionalOnProperty(name = "mq.engine", havingValue = "rocketmq", matchIfMissing = true)
public class RocketMqEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqEventPublisher.class);

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void publish(EventChannel channel, Object payload, Map<String, Object> headers) {
        if (rocketMQTemplate == null) {
            logger.warn("⚠️ RocketMQTemplate 不可用，跳过事件发送 - channel: {}", channel);
            return;
        }

        String destination = buildDestination(channel, headers);
        MessageBuilder<Object> builder = MessageBuilder.withPayload(payload);
        if (headers != null) {
            headers.forEach(builder::setHeader);
        }

        logger.info("📤 [RocketMQ] 发送事件 - destination: {}, payloadType: {}",
                destination, payload != null ? payload.getClass().getSimpleName() : "null");

        rocketMQTemplate.syncSend(destination, builder.build());
    }

    @Override
    public void publishAsync(EventChannel channel, Object payload, Map<String, Object> headers) {
        if (rocketMQTemplate == null) {
            logger.warn("⚠️ RocketMQTemplate 不可用，跳过异步事件发送 - channel: {}", channel);
            return;
        }

        String destination = buildDestination(channel, headers);
        MessageBuilder<Object> builder = MessageBuilder.withPayload(payload);
        if (headers != null) {
            headers.forEach(builder::setHeader);
        }

        rocketMQTemplate.asyncSend(destination, builder.build(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                logger.info("✅ [RocketMQ] 异步事件发送成功 - destination: {}, msgId: {}",
                        destination, sendResult != null ? sendResult.getMsgId() : "null");
            }

            @Override
            public void onException(Throwable e) {
                logger.error("❌ [RocketMQ] 异步事件发送失败 - destination: {}", destination, e);
            }
        });
    }

    private String buildDestination(EventChannel channel, Map<String, Object> headers) {
        Object tagObj = headers != null
                ? headers.getOrDefault("eventTag", headers.get("eventType"))
                : null;
        String tag = tagObj != null ? tagObj.toString() : "DEFAULT";

        switch (channel) {
            case FILE_EVENTS:
                return "file-events:" + tag;
            case PREVIEW_EVENTS:
                return "preview-events:" + tag;
            case CONVERT_EVENTS:
                return "convert-events:" + tag;
            case DOWNLOAD_TASKS:
                // 下载任务当前不区分Tag
                return "download-tasks";
            default:
                throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
    }
}
