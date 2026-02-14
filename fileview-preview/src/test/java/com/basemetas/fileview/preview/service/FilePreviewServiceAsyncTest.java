package com.basemetas.fileview.preview.service;

import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FilePreviewServiceAsyncTest {

    @Autowired
    private FilePreviewService filePreviewService;

    @Test
    public void testProcessNetworkFilePreviewAsync() {
        // 创建测试请求
        FilePreviewRequest request = new FilePreviewRequest();
        request.setNetworkFileUrl("http://example.com/test-file.zip");
        request.setDownloadTargetPath("/tmp/downloads");
        request.setDownloadTimeout(30000);
        
        // 验证服务已正确注入
        assertNotNull(filePreviewService, "FilePreviewService应该被正确注入");
        
        // 测试方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 调用异步网络文件预览方法
            Map<String, Object> result = filePreviewService.processNetworkFilePreview(request, System.currentTimeMillis());
            
            // 验证返回结果包含必要的字段
            assertNotNull(result, "返回结果不应为null");
            assertTrue(result.containsKey("taskId"), "返回结果应包含taskId字段");
            assertTrue(result.containsKey("status"), "返回结果应包含status字段");
            assertEquals("DOWNLOADING", result.get("status"), "状态应为DOWNLOADING");
        }, "异步处理网络文件预览不应该抛出异常");
    }
}