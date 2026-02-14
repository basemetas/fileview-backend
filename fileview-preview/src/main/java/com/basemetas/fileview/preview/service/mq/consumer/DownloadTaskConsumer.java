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

import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.model.download.DownloadTaskMessage;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.service.download.DownloadDeduplicationService;
import com.basemetas.fileview.preview.service.download.DownloadResult;
import com.basemetas.fileview.preview.service.download.DownloadTaskManager;
import com.basemetas.fileview.preview.service.FilePreviewService;
import com.basemetas.fileview.preview.utils.DownloadUtils;
import com.basemetas.fileview.preview.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.Map;

/**
 * 下载任务消息消费者核心处理类
 * 只负责处理 DownloadTaskMessage，不直接绑定具体MQ实现。
 */
@Component
public class DownloadTaskConsumer {
    private static final Logger logger = LoggerFactory.getLogger(DownloadTaskConsumer.class);

    @Autowired
    private DownloadDeduplicationService downloadDeduplicationService;

    @Autowired
    private DownloadTaskManager taskManager;

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private FilePreviewService filePreviewService;
    
    @Autowired
    private DownloadUtils downloadUtils;

    public void onMessage(DownloadTaskMessage message) {
        String fileId = message.getFileId(); // 使用fileId作为任务标识符
        long mqReceiveTime = System.currentTimeMillis();
        logger.info("📨 MQ消息接收 - FileId: {}, ReceiveTime: {}", fileId, mqReceiveTime);

        try {
            // 验证消息参数
            if (message.getNetworkFileUrl() == null || message.getNetworkFileUrl().trim().isEmpty()) {
                String errorMsg = "网络文件URL不能为空";
                logger.error("下载任务参数验证失败 - FileId: {}, 错误: {}", fileId, errorMsg);
                taskManager.updateTaskFailed(fileId, errorMsg); // 使用fileId
                return;
            }

            if (message.getDownloadTargetPath() == null || message.getDownloadTargetPath().trim().isEmpty()) {
                String errorMsg = "下载目标路径不能为空";
                logger.error("下载任务参数验证失败 - FileId: {}, 错误: {}", fileId, errorMsg);
                taskManager.updateTaskFailed(fileId, errorMsg); // 使用fileId
                return;
            }

            // 清理网络文件URL，移除开头的空格等非法字符
            String networkFileUrl = message.getNetworkFileUrl().trim();

            // 额外的URL清理，确保协议部分没有非法字符
            networkFileUrl = httpUtils.cleanUrl(networkFileUrl);

            // 更新消息中的URL
            message.setNetworkFileUrl(networkFileUrl);

            // 更新任务状态为处理中
            taskManager.updateTaskStatus(fileId, DownloadTaskStatus.DOWNLOADING); // 使用fileId

            String fileUrl=message.getNetworkFileUrl();
            String targetPath=message.getDownloadTargetPath();
            String username=message.getNetworkUsername();//存储服务器用户名
            String password=message.getNetworkPassword();//存储服务器用户密码
            int timeout=message.getDownloadTimeout();
            String fileName = message.getFileName();
            
            // 智能判断是否使用智能下载模式
            boolean useSmartDownload = downloadUtils.shouldUseSmartDownload(fileUrl);
            long downloadStartTime = System.currentTimeMillis();
            long mqToDownloadDelay = downloadStartTime - mqReceiveTime;
            logger.info("⏱️ MQ队列等待+准备耗时: {}ms - FileId: {}", mqToDownloadDelay, fileId);
            logger.debug("下载模式选择 - FileId: {}, UseSmartDownload: {}, URL: {}", 
                fileId, useSmartDownload, httpUtils.maskSensitiveUrl(fileUrl));
            
            DownloadResult downloadResult = downloadDeduplicationService.downloadWithDeduplication(
                fileUrl, targetPath, username, password, timeout, useSmartDownload, fileName);
            long downloadEndTime = System.currentTimeMillis();
            long downloadDuration = downloadEndTime - downloadStartTime;
            logger.info("⏱️ 下载执行耗时: {}ms - FileId: {}", downloadDuration, fileId);

            if ("SUCCESS".equals(downloadResult.getStatus())) {
                String localFilePath = downloadResult.getFilePath();

                // 更新任务成功状态
                taskManager.updateTaskSuccess(fileId, localFilePath); // 使用fileId
                long totalDownloadTime = System.currentTimeMillis() - mqReceiveTime;
                logger.info("✅ 下载任务执行成功 - FileId: {}, LocalPath: {}, 总耗时: {}ms", fileId, localFilePath, totalDownloadTime);

                // 触发文件预览流程
                long previewStartTime = System.currentTimeMillis();
                triggerFilePreview(message, localFilePath);
                long previewTriggerTime = System.currentTimeMillis() - previewStartTime;
                logger.debug("⏱️ 触发预览耗时: {}ms - FileId: {}", previewTriggerTime, fileId);
            } else if ("PENDING".equals(downloadResult.getStatus())) {
                // 任务正在进行中，这种情况不应该发生，因为我们在同一个消费者中处理任务
                logger.warn("意外的PENDING状态 - FileId: {}, TaskId: {}", fileId, downloadResult.getTaskId());
                // 更新任务状态为下载中，继续等待
                taskManager.updateTaskStatus(fileId, DownloadTaskStatus.DOWNLOADING);
            } else {
                // 下载失败
                String errorMsg = downloadResult.getMessage() != null ? downloadResult.getMessage() : "下载失败";
                logger.error("下载任务执行失败 - FileId: {}, 错误: {}", fileId, errorMsg);
                taskManager.updateTaskFailed(fileId, errorMsg); // 使用fileId
            }

        } catch (Exception e) {
            String simplifiedError = e.getMessage();
            if (simplifiedError == null || simplifiedError.isEmpty()) {
                simplifiedError = "未知错误: " + e.getClass().getSimpleName();
            }
            logger.error("下载任务执行失败 - FileId: {}, 错误: {}", fileId, simplifiedError);
            // 更新任务失败状态
            taskManager.updateTaskFailed(fileId, simplifiedError); // 使用fileId
        }
    }

    /**
     * 触发文件预览流程
     */
    private void triggerFilePreview(DownloadTaskMessage message, String localFilePath) {
        try {
            File downloadedFile = new File(localFilePath);
            if (!downloadedFile.exists() || !downloadedFile.isFile()) {
                logger.error("下载的文件不存在: {}", localFilePath);
                return;
            }

            // 创建 FilePreviewRequest 对象，复用 FilePreviewService 的逻辑
            FilePreviewRequest request = new FilePreviewRequest();
            request.setFileId(message.getFileId());
            request.setSrcRelativePath(localFilePath);
            request.setFileName(downloadedFile.getName());
            request.setSourceService("networkFile-preview-service");
            // 🔑 关键修复：传递密码参数，用于加密压缩包
            request.setPassword(message.getPassWord());
            // 🔑 关键修复：传递 clientId，用于查询密码解锁状态
            request.setClientId(message.getClientId());

            // 调用FilePreviewService处理服务器文件预览逻辑
            long startTime = System.currentTimeMillis();
            // 🔑 传递 requestBaseUrl
            String requestBaseUrl = message.getRequestBaseUrl();
            Map<String, Object> result = filePreviewService.processServerFilePreview(request, startTime, requestBaseUrl);

            // 记录处理结果
            String status = (String) result.get("status");
            logger.debug("文件下载完成，开始转换 - FileId: {}, Status: {}", message.getFileId(), status);

        } catch (Exception e) {
            logger.error("触发文件预览失败 - FileId: {}", message.getFileId(), e);
        }
    }
}