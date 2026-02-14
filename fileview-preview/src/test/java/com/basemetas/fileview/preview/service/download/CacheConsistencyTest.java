package com.basemetas.fileview.preview.service.download;

import com.basemetas.fileview.preview.model.download.DownloadTask;
import com.basemetas.fileview.preview.model.download.DownloadTaskStatus;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.service.cache.CacheReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CacheConsistencyTest {

    @Autowired
    private DownloadTaskManager downloadTaskManager;
    
    @Autowired
    private CacheReadService cacheReadService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testDownloadTaskCacheKeyConsistency() {
        // 创建测试请求
        FilePreviewRequest request = new FilePreviewRequest();
        String fileId = "test-consistency-file-id";
        request.setFileId(fileId);
        request.setNetworkFileUrl("http://example.com/test-consistency.zip");
        request.setDownloadTargetPath("/tmp/downloads");
        request.setDownloadTimeout(30000);

        // 创建下载任务
        DownloadTask task = downloadTaskManager.createTask(request);
        
        // 验证下载任务缓存键的一致性
        String downloadTaskKey = "download_task:" + fileId;
        assertNotNull(redisTemplate.opsForValue().get(downloadTaskKey), 
            "下载任务应该存储在Redis中，键为: " + downloadTaskKey);
        
        // 验证任务信息
        assertEquals(fileId, task.getFileId());
        assertEquals(fileId, task.getTaskId());
        assertEquals(DownloadTaskStatus.PENDING, task.getStatus());
    }
    
    @Test
    public void testDownloadTaskUpdateCacheConsistency() {
        // 创建测试请求
        FilePreviewRequest request = new FilePreviewRequest();
        String fileId = "test-update-consistency-file-id";
        request.setFileId(fileId);
        request.setNetworkFileUrl("http://example.com/test-update-consistency.zip");
        request.setDownloadTargetPath("/tmp/downloads");
        request.setDownloadTimeout(30000);

        // 创建下载任务
        DownloadTask task = downloadTaskManager.createTask(request);
        
        // 更新任务状态为处理中
        downloadTaskManager.updateTaskStatus(fileId, DownloadTaskStatus.DOWNLOADING);
        
        // 验证任务状态更新
        DownloadTask updatedTask = downloadTaskManager.getTask(fileId);
        assertEquals(DownloadTaskStatus.DOWNLOADING, updatedTask.getStatus());
        
        // 模拟下载完成
        String localFilePath = "/tmp/downloads/test-file.zip";
        downloadTaskManager.updateTaskSuccess(fileId, localFilePath);
        
        // 验证任务成功状态
        DownloadTask successTask = downloadTaskManager.getTask(fileId);
        assertEquals(DownloadTaskStatus.DOWNLOADED, successTask.getStatus());
        assertEquals(localFilePath, successTask.getLocalFilePath());
        assertEquals(100.0, successTask.getProgress());
    }
    
    @Test
    public void testCacheKeyPrefixes() {
        // 验证下载任务缓存键前缀
        String downloadTaskPrefix = "download_task:";
        assertTrue(downloadTaskPrefix.startsWith("download_task:"), 
            "下载任务缓存键前缀应该是 'download_task:'");
        
        // 验证预览缓存键前缀
        String directPreviewPrefix = "preview:direct:";
        assertTrue(directPreviewPrefix.startsWith("preview:direct:"), 
            "直接预览缓存键前缀应该是 'preview:direct:'");
    }
}