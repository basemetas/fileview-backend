package com.basemetas.fileview.preview.service.download;

import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AsyncDownloadIntegrationTest {

    @Autowired
    private DownloadTaskManager downloadTaskManager;

    @Test
    public void testAsyncDownloadFlow() throws InterruptedException {
        // 创建测试请求
        FilePreviewRequest request = new FilePreviewRequest();
        String fileId = "test-file-" + UUID.randomUUID().toString();
        request.setFileId(fileId);
        request.setNetworkFileUrl("http://example.com/test-file.zip");
        request.setDownloadTargetPath("/tmp/downloads");
        request.setDownloadTimeout(30000);

        // 1. 创建下载任务
        DownloadTask task = downloadTaskManager.createTask(request);
        assertNotNull(task);
        assertEquals(DownloadTaskStatus.PENDING, task.getStatus());

        // 2. 验证任务已创建
        DownloadTask retrievedTask = downloadTaskManager.getTask(task.getFileId()); // 使用fileId
        assertNotNull(retrievedTask);
        assertEquals(task.getFileId(), retrievedTask.getTaskId()); // 修改断言
        assertEquals(DownloadTaskStatus.PENDING, retrievedTask.getStatus());

        // 3. 更新任务状态为处理中
        downloadTaskManager.updateTaskStatus(task.getFileId(), DownloadTaskStatus.DOWNLOADING); // 使用fileId
        retrievedTask = downloadTaskManager.getTask(task.getFileId()); // 使用fileId
        assertEquals(DownloadTaskStatus.DOWNLOADING, retrievedTask.getStatus());

        // 4. 模拟下载完成
        String localFilePath = "/tmp/downloads/test-file.zip";
        downloadTaskManager.updateTaskSuccess(task.getFileId(), localFilePath); // 使用fileId

        // 5. 验证任务成功完成
        retrievedTask = downloadTaskManager.getTask(task.getFileId()); // 使用fileId
        assertEquals(DownloadTaskStatus.DOWNLOADED, retrievedTask.getStatus());
        assertEquals(localFilePath, retrievedTask.getLocalFilePath());
        assertEquals(100.0, retrievedTask.getProgress());
        assertNotNull(retrievedTask.getFinishedTime());
    }

    @Test
    public void testDownloadTaskFailure() {
        // 创建测试请求
        FilePreviewRequest request = new FilePreviewRequest();
        request.setFileId("fail-test-file");
        request.setNetworkFileUrl("http://example.com/fail-test.zip");
        request.setDownloadTargetPath("/tmp/downloads");
        request.setDownloadTimeout(30000);

        // 创建任务
        DownloadTask task = downloadTaskManager.createTask(request);

        // 模拟下载失败
        String errorMessage = "网络连接超时";
        downloadTaskManager.updateTaskFailed(task.getFileId(), errorMessage); // 使用fileId

        // 验证任务失败状态
        DownloadTask failedTask = downloadTaskManager.getTask(task.getFileId()); // 使用fileId
        assertEquals(DownloadTaskStatus.FAILED, failedTask.getStatus());
        assertEquals(errorMessage, failedTask.getErrorMessage());
        assertNotNull(failedTask.getFinishedTime());
    }
}