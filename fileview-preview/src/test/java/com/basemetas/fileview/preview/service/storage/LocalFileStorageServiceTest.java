package com.basemetas.fileview.preview.service.storage;

import com.basemetas.fileview.preview.service.storage.impl.LocalFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class LocalFileStorageServiceTest {

    private LocalFileStorageService storageService;

    @BeforeEach
    public void setUp() {
        storageService = new LocalFileStorageService();
        // 设置测试配置
        ReflectionTestUtils.setField(storageService, "basePath", "d:/myWorkSpace/fileview-backend/fileTemp");
        ReflectionTestUtils.setField(storageService, "pathMappingEnabled", true);
        ReflectionTestUtils.setField(storageService, "pathMappingFrom", "/var/app/fileview-backend");
        ReflectionTestUtils.setField(storageService, "pathMappingTo", "d:/myWorkSpace/fileview-backend");
    }

    @Test
    public void testGetPhysicalPathWithMapping() {
        // 测试路径映射
        String inputPath = "/var/app/fileview-backend/fileTemp/preview/temp5.pdf";
        String expectedPath = "d:/myWorkSpace/fileview-backend/fileTemp/preview/temp5.pdf";
        
        String result = storageService.getPhysicalPath(inputPath);
        
        // 验证结果不包含重复映射
        assertFalse(result.contains("fileview-preview/d:"), "路径不应包含重复映射");
        assertFalse(result.contains("d:/myWorkSpace/fileview-backend/fileTemp/d:"), "路径不应包含重复映射");
        System.out.println("输入路径: " + inputPath);
        System.out.println("输出路径: " + result);
    }

    @Test
    public void testGetPhysicalPathAlreadyMapped() {
        // 测试已经映射过的路径
        String inputPath = "d:/myWorkSpace/fileview-backend/fileTemp/preview/temp5.pdf";
        
        String result = storageService.getPhysicalPath(inputPath);
        
        // 应该保持不变
        // 标准化路径分隔符后比较
        String normalizedResult = result.replace("\\", "/");
        String normalizedInput = inputPath.replace("\\", "/");
        assertEquals(normalizedInput, normalizedResult, "已映射路径应该保持不变");
        System.out.println("已映射路径输入: " + inputPath);
        System.out.println("已映射路径输出: " + result);
    }

    @Test
    public void testGetPhysicalPathWithComplexMapping() {
        // 测试复杂的路径映射场景，模拟实际问题
        String inputPath = "/var/app/fileview-backend/fileview-preview/d:/myWorkSpace/fileview-backend/fileTemp/preview/temp6.pdf";
        String result = storageService.getPhysicalPath(inputPath);
        
        // 验证结果不包含重复映射
        assertFalse(result.contains("fileview-preview/d:"), "路径不应包含重复映射");
        assertFalse(result.contains("d:/myWorkSpace/fileview-backend/fileTemp/d:"), "路径不应包含重复映射");
        
        // 验证结果是有效的Windows路径
        assertTrue(result.contains("myWorkSpace"), "结果应包含myWorkSpace");
        
        // 验证结果应该以d:开头的Windows路径
        assertTrue(result.startsWith("d:"), "结果应为Windows路径格式");
        
        System.out.println("复杂路径输入: " + inputPath);
        System.out.println("复杂路径输出: " + result);
    }
    
    @Test
    public void testGetPhysicalPathWithWindowsPath() {
        // 测试Windows路径
        String inputPath = "d:\\myWorkSpace\\fileview-backend\\fileTemp\\preview\\temp6.pdf";
        String result = storageService.getPhysicalPath(inputPath);
        
        // 应该保持不变
        assertTrue(result.contains("myWorkSpace"), "路径应包含myWorkSpace");
        assertFalse(result.contains("fileview-preview/d:"), "路径不应包含重复映射");
        System.out.println("Windows路径输入: " + inputPath);
        System.out.println("Windows路径输出: " + result);
    }
    
    @Test
    public void testGetPhysicalPathFixedComplexMapping() {
        // 测试修复后的复杂路径映射场景
        String inputPath = "/var/app/fileview-backend/fileview-preview/d:/myWorkSpace/fileview-backend/fileTemp/preview/temp6.pdf";
        String result = storageService.getPhysicalPath(inputPath);
        
        // 验证结果应该是正确的Windows路径
        String expectedPath = "d:/myWorkSpace/fileview-backend/fileTemp/preview/temp6.pdf";
        String normalizedResult = result.replace("\\", "/").toLowerCase();
        String normalizedExpected = expectedPath.toLowerCase();
        
        // 验证结果是否符合预期
        assertTrue(normalizedResult.contains("myworkspace/fileview-backend/filetemp/preview/temp6.pdf"), 
            "结果应该是正确的Windows路径: " + normalizedResult);
        
        System.out.println("修复测试 - 复杂路径输入: " + inputPath);
        System.out.println("修复测试 - 复杂路径输出: " + result);
        System.out.println("修复测试 - 期望路径: " + expectedPath);
    }
}