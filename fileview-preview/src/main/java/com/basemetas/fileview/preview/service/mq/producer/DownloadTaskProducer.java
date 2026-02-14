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
import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * 下载任务消息生产者
 */
@Service
public class DownloadTaskProducer {
    private static final Logger logger = LoggerFactory.getLogger(DownloadTaskProducer.class);
    
    @Autowired(required = false)
    private EventPublisher eventPublisher;
    
    /**
     * 发送下载任务消息
     */
    public void sendDownloadTask(DownloadTask task) {
        if (eventPublisher == null) {
            logger.warn("⚠️ 事件发布器不可用，跳过发送下载任务消息 - FileId: {}", task.getFileId());
            return;
        }
        try {
            DownloadTaskMessage message = new DownloadTaskMessage();
            message.setTaskId(task.getFileId()); // 使用fileId作为taskId
            message.setFileId(task.getFileId());
            message.setNetworkFileUrl(task.getNetworkFileUrl());
            message.setNetworkUsername(task.getNetworkUsername());
            message.setNetworkPassword(task.getNetworkPassword());
            message.setDownloadTargetPath(task.getDownloadTargetPath());
            message.setDownloadTimeout(task.getDownloadTimeout());
            message.setPassWord(task.getPassWord()); // 🔑 传递压缩包密码
            message.setClientId(task.getClientId()); // 🔑 传递客户端标识
            message.setRequestBaseUrl(task.getRequestBaseUrl()); // 🔑 传递 requestBaseUrl
            message.setFileName(task.getFileName()); // 🔑 传递前端文件名
            
            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", task.getFileId());
            headers.put("taskId", task.getFileId());
            headers.put("clientId", task.getClientId());
            headers.put("eventTag", "DOWNLOAD_TASK");
            
            logger.info("📤 发送下载任务消息 - FileId: {}", task.getFileId());
            
            eventPublisher.publish(EventChannel.DOWNLOAD_TASKS, message, headers);
            logger.info("✅ 发送下载任务消息成功 - FileId: {}", task.getFileId());
            
        } catch (Exception e) {
            logger.error("发送下载任务消息失败 - FileId: {}", task.getFileId(), e);
            throw new RuntimeException("发送下载任务消息失败", e);
        }
    }
}
