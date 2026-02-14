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
import com.basemetas.fileview.preview.service.mq.event.FilePreviewEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件预览事件生产者
 * 负责发送文件预览事件到RocketMQ
 */
@Component
public class FilePreviewEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(FilePreviewEventProducer.class);
    
    @Autowired(required = false)
    private EventPublisher eventPublisher; 
    private static final String TOPIC = "file-events";
    
    /**
     * 发送文件预览事件到消息中间件
     * @param filePreviewEvent 文件预览事件对象
     * @return 发送是否成功
     */
    public boolean sendFilePreviewEvent(FilePreviewEvent filePreviewEvent) {
        if (eventPublisher == null) {
            logger.warn("⚠️ 事件发布器不可用，跳过发送预览事件 - FileId: {}", filePreviewEvent.getFileId());
            return false;
        }
        try {
            logger.info("📤 Preparing to send file preview event: {}", filePreviewEvent.getFileId());

            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", filePreviewEvent.getFileId());
            headers.put("eventType", filePreviewEvent.getEventType().name());
            headers.put("sourceService", filePreviewEvent.getSourceService());
            headers.put("eventTag", filePreviewEvent.getEventType().name());

            logger.info("📨 Sending message to channel: {}, eventType: {}", EventChannel.FILE_EVENTS, filePreviewEvent.getEventType());
            logger.debug("📄 Message content: {}", filePreviewEvent);
            
            eventPublisher.publish(EventChannel.FILE_EVENTS, filePreviewEvent, headers);

            logger.info("✅ File preview event sent successfully via EventPublisher!");
            return true;
            
        } catch (Exception e) {
            logger.error("💥 Exception occurred while sending file preview event: {}", filePreviewEvent, e);
            return false;
        }
    }
    
    /**
     * 异步发送文件预览事件（非阻塞）
     * @param filePreviewEvent 文件预览事件对象
     */
    public void sendFilePreviewEventAsync(FilePreviewEvent filePreviewEvent) {
        if (eventPublisher == null) {
            logger.warn("⚠️ 事件发布器不可用，跳过异步发送预览事件 - FileId: {}", filePreviewEvent.getFileId());
            return;
        }
        try {
            String destination = TOPIC + ":" + filePreviewEvent.getEventType();
            logger.info("📤 [MQ发送] 准备发送转换事件 - FileId: {}, Destination: {}, TargetFormat: {}, SourceFormat: {}", 
                       filePreviewEvent.getFileId(), destination, 
                       filePreviewEvent.getTargetFormat(), filePreviewEvent.getSourceFormat());
            
            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", filePreviewEvent.getFileId());
            headers.put("eventType", filePreviewEvent.getEventType().name());
            headers.put("sourceService", filePreviewEvent.getSourceService());
            headers.put("eventTag", filePreviewEvent.getEventType().name());

            eventPublisher.publishAsync(EventChannel.FILE_EVENTS, filePreviewEvent, headers);
            
        } catch (Exception e) {
            logger.error("💥 Exception occurred while sending async file preview event: {}", filePreviewEvent, e);
        }
    }
}