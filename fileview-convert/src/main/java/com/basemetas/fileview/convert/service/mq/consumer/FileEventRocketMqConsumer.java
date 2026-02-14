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
package com.basemetas.fileview.convert.service.mq.consumer;

import com.basemetas.fileview.convert.service.mq.event.FileEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ 的 FileEvent 适配器，负责监听 MQ 并委托给核心处理类。
 */
@Component
@ConditionalOnProperty(name = "mq.engine", havingValue = "rocketmq", matchIfMissing = true)
@RocketMQMessageListener(topic = "file-events", consumerGroup = "file-convert-consumer", selectorExpression = "*")
public class FileEventRocketMqConsumer implements RocketMQListener<FileEvent> {

    private static final Logger logger = LoggerFactory.getLogger(FileEventRocketMqConsumer.class);

    @Autowired
    private FileEventConsumer fileEventConsumer;

    @Override
    public void onMessage(FileEvent message) {
        logger.debug("[RocketMQ] Delegating FileEvent to core consumer - fileId: {}",
                message != null ? message.getFileId() : "null");
        fileEventConsumer.onMessage(message);
    }
}
