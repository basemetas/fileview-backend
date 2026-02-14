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

import com.basemetas.fileview.convert.service.mq.event.FileEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 文件事件生产者
 * 负责发送文件转换事件到RocketMQ
 */
@Component
public class FileEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(FileEventProducer.class);
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate; 
    private static final String TOPIC = "file-events";
    
    /**
     * 发送文件事件到RocketMQ
     * @param fileEvent 文件事件对象
     * @return 发送是否成功
     */
    public boolean sendFileEvent(FileEvent fileEvent) {
        try {
            logger.info("📤 Preparing to send file event: {}", fileEvent.getFileId());
            String destination = TOPIC + ":" + fileEvent.getEventType();
            logger.info("📨 Sending message to: {}", destination);
            logger.debug("📄 Message content: {}", fileEvent);
            
            // 发送消息
            SendResult sendResult = rocketMQTemplate.syncSend(
                destination, 
                MessageBuilder.withPayload(fileEvent)
                    .setHeader("fileId", fileEvent.getFileId())
                    .setHeader("fileType", fileEvent.getFileType())
                    .setHeader("eventType", fileEvent.getEventType().name())
                    .setHeader("sourceService", fileEvent.getSourceService())
                    .build()
            );
            
            // 检查发送结果
            if (sendResult != null && SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
                logger.info("✅ File event sent successfully!");
                logger.info("📊 Send details - MessageId: {}, QueueId: {}, QueueOffset: {}", 
                           sendResult.getMsgId(), 
                           sendResult.getMessageQueue().getQueueId(),
                           sendResult.getQueueOffset());
                return true;
            } else {
                logger.error("❌ Failed to send file event, send status: {}", 
                            sendResult != null ? sendResult.getSendStatus() : "null");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("💥 Exception occurred while sending file event: {}", fileEvent, e);
            return false;
        }
    }
    
    /**
     * 异步发送文件事件（非阻塞）
     * @param fileEvent 文件事件对象
     */
    public void sendFileEventAsync(FileEvent fileEvent) {
        try {
            logger.info("📤 Preparing to send file event asynchronously: {}", fileEvent.getFileId());           
            String destination = TOPIC + ":" + fileEvent.getEventType();            
            // 异步发送消息
            rocketMQTemplate.asyncSend(destination, 
                MessageBuilder.withPayload(fileEvent)
                    .setHeader("fileId", fileEvent.getFileId())
                    .setHeader("fileType", fileEvent.getFileType())
                    .setHeader("eventType", fileEvent.getEventType().name())
                    .setHeader("sourceService", fileEvent.getSourceService())
                    .build(),
                new org.apache.rocketmq.client.producer.SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        logger.info("✅ Async file event sent successfully! MessageId: {}", sendResult.getMsgId());
                    }
                    
                    @Override
                    public void onException(Throwable e) {
                        logger.error("❌ Async file event send failed: {}", fileEvent, e);
                    }
                }
            );
            
        } catch (Exception e) {
            logger.error("💥 Exception occurred while sending async file event: {}", fileEvent, e);
        }
    }
  
}