package com.basemetas.fileview.preview.service;

import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import com.basemetas.fileview.preview.utils.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class FilePreviewServiceDownloadTest {

    @Autowired
    private FilePreviewService filePreviewService;
    
    @Autowired
    private FileUtils fileUtils;

    @Test
    public void testProcessNetworkFilePreviewWithNullDownloadTargetPath() {
        // 创建测试请求，不设置下载目标路径
        FilePreviewRequest request = new FilePreviewRequest();
        request.setNetworkFileUrl("https://test.moqisoft.com/wo.xlsx");
        // 不设置downloadTargetPath，让它使用默认值
        
        // 验证服务已正确注入
        assertNotNull(filePreviewService, "FilePreviewService应该被正确注入");
        
        // 测试方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 调用网络文件预览方法
            Map<String, Object> result = filePreviewService.processNetworkFilePreview(request, System.currentTimeMillis());
            
            // 验证返回结果包含必要的字段
            assertNotNull(result, "返回结果不应为null");
            assertTrue(result.containsKey("taskId"), "返回结果应包含taskId字段");
            assertTrue(result.containsKey("status"), "返回结果应包含status字段");
            assertEquals("DOWNLOADING", result.get("status"), "状态应为DOWNLOADING");
        }, "处理网络文件预览（下载目标路径为空）不应该抛出异常");
    }
    
    @Test
    public void testProcessNetworkFilePreviewWithEmptyDownloadTargetPath() {
        // 创建测试请求，设置空的下载目标路径
        FilePreviewRequest request = new FilePreviewRequest();
        request.setNetworkFileUrl("https://test.moqisoft.com/wo.xlsx");
        request.setDownloadTargetPath(""); // 设置为空字符串
        
        // 验证服务已正确注入
        assertNotNull(filePreviewService, "FilePreviewService应该被正确注入");
        
        // 测试方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 调用网络文件预览方法
            Map<String, Object> result = filePreviewService.processNetworkFilePreview(request, System.currentTimeMillis());
            
            // 验证返回结果包含必要的字段
            assertNotNull(result, "返回结果不应为null");
            assertTrue(result.containsKey("taskId"), "返回结果应包含taskId字段");
            assertTrue(result.containsKey("status"), "返回结果应包含status字段");
            assertEquals("DOWNLOADING", result.get("status"), "状态应为DOWNLOADING");
        }, "处理网络文件预览（下载目标路径为空字符串）不应该抛出异常");
    }
    
    @Test
    public void testProcessNetworkFilePreviewWithNullFileId() {
        // 创建测试请求，不设置FileId
        FilePreviewRequest request = new FilePreviewRequest();
        request.setNetworkFileUrl("https://test.moqisoft.com/wo.xlsx");
        // 不设置FileId，让它自动生成
        
        // 验证服务已正确注入
        assertNotNull(filePreviewService, "FilePreviewService应该被正确注入");
        assertNotNull(fileUtils, "FileUtils应该被正确注入");
        
        // 测试方法调用不会抛出异常
        assertDoesNotThrow(() -> {
            // 调用网络文件预览方法
            Map<String, Object> result = filePreviewService.processNetworkFilePreview(request, System.currentTimeMillis());
            
            // 验证返回结果包含必要的字段
            assertNotNull(result, "返回结果不应为null");
            assertTrue(result.containsKey("taskId"), "返回结果应包含taskId字段");
            assertTrue(result.containsKey("status"), "返回结果应包含status字段");
            assertTrue(result.containsKey("fileId"), "返回结果应包含fileId字段");
            assertEquals("DOWNLOADING", result.get("status"), "状态应为DOWNLOADING");
            
            // 验证生成的fileId不为空
            String fileId = (String) result.get("fileId");
            assertNotNull(fileId, "生成的fileId不应为null");
            assertFalse(fileId.isEmpty(), "生成的fileId不应为空");
        }, "处理网络文件预览（FileId为空）不应该抛出异常");
    }
}