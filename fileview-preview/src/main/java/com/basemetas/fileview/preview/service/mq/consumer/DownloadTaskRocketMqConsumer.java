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

import com.basemetas.fileview.preview.config.RocketMQConfig;
import com.basemetas.fileview.preview.model.download.DownloadTaskMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ 的下载任务消费者适配器。
 */
@Component
@ConditionalOnProperty(name = "mq.engine", havingValue = "rocketmq", matchIfMissing = true)
@RocketMQMessageListener(topic = RocketMQConfig.DOWNLOAD_TOPIC, consumerGroup = "download-consumer-group")
public class DownloadTaskRocketMqConsumer implements RocketMQListener<DownloadTaskMessage> {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTaskRocketMqConsumer.class);

    @Autowired
    private DownloadTaskConsumer downloadTaskConsumer;

    @Override
    public void onMessage(DownloadTaskMessage message) {
        logger.debug("[RocketMQ] Delegating DownloadTaskMessage to core consumer - fileId: {}",
                message != null ? message.getFileId() : "null");
        downloadTaskConsumer.onMessage(message);
    }
}
