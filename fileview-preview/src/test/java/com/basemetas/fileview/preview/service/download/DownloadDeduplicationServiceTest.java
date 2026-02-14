package com.basemetas.fileview.preview.service.download;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DownloadDeduplicationServiceTest {

    @Autowired
    private DownloadDeduplicationService downloadDeduplicationService;

    @Test
    public void testCheckFileExistsByHash() {
        String fileUrl = "http://example.com/test-file.zip";
        
        // 验证服务已正确注入
        assertNotNull(downloadDeduplicationService, "DownloadDeduplicationService应该被正确注入");
        
        // 测试方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 检查文件是否存在（应该返回null，因为文件不存在）
            String existingFilePath = downloadDeduplicationService.checkFileExistsByHash(fileUrl);
            assertNull(existingFilePath, "对于不存在的文件，应该返回null");
        }, "检查文件是否存在不应该抛出异常");
    }

    @Test
    public void testRecordFileHash() {
        String fileUrl = "http://example.com/test-file.zip";
        String filePath = "/tmp/test-file.zip";
        
        // 验证服务方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 记录文件哈希（会记录到Redis，但因为文件不存在，不会实际计算哈希）
            downloadDeduplicationService.recordFileHash(fileUrl, filePath);
        }, "记录文件哈希不应该抛出异常");
    }
}