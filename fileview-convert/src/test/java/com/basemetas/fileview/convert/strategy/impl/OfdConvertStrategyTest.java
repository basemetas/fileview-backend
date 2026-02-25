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
package com.basemetas.fileview.convert.strategy.impl;

import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OFD转换策略测试类
 */
class OfdConvertStrategyTest {
    
    private OfdConvertStrategy ofdConvertStrategy;
    private String testTempDir;
    
    @BeforeEach
    void setUp() throws IOException {
        ofdConvertStrategy = new OfdConvertStrategy();
        
        // 创建测试临时目录
        testTempDir = System.getProperty("java.io.tmpdir") + File.separator + "ofd_test_" + System.currentTimeMillis();
        Files.createDirectories(Paths.get(testTempDir));
    }
    
    @Test
    void testGetSupportedFileType() {
        assertEquals("ofd", ofdConvertStrategy.getSupportedSourceFormats());
    }
    
    @Test
    void testGetSupportedTargetFormats() {
        var supportedFormats = ofdConvertStrategy.getSupportedTargetFormats();
        assertNotNull(supportedFormats);
        assertTrue(supportedFormats.contains("pdf"));
        assertTrue(supportedFormats.contains("png"));
        assertTrue(supportedFormats.contains("jpg"));
        assertTrue(supportedFormats.contains("jpeg"));
    }
    
    @Test
    void testIsOfdLibraryAvailable() {
        // 测试OFDRW库是否可用
        boolean available = ofdConvertStrategy.isOfdLibraryAvailable();
        // 由于我们已经在pom.xml中添加了依赖，这应该返回true
        assertTrue(available, "OFDRW库应该可用");
    }
    
    @Test
    void testGetServiceStatus() {
        String status = ofdConvertStrategy.getServiceStatus();
        assertNotNull(status);
        assertTrue(status.contains("OFD转换服务状态"));
        assertTrue(status.contains("OFDRW库可用"));
        assertTrue(status.contains("支持的目标格式"));
    }
    
    @Test
    void testConvertWithInvalidInputs() {
        // 测试空文件路径
        boolean result = ofdConvertStrategy.convert("", testTempDir, "test", "pdf");
        assertFalse(result);
        
        // 测试空目标路径
        result = ofdConvertStrategy.convert("test.ofd", "", "test", "pdf");
        assertFalse(result);
        
        // 测试空文件名
        result = ofdConvertStrategy.convert("test.ofd", testTempDir, "", "pdf");
        assertFalse(result);
        
        // 测试不支持的格式
        result = ofdConvertStrategy.convert("test.ofd", testTempDir, "test", "unsupported");
        assertFalse(result);
    }
    
    @Test
    void testConvertWithNonExistentFile() {
        // 测试不存在的文件
        String nonExistentFile = testTempDir + File.separator + "nonexistent.ofd";
        boolean result = ofdConvertStrategy.convert(nonExistentFile, testTempDir, "test", "pdf");
        assertFalse(result);
    }
    
    @Test
    void testServiceIntegration() {
        // 测试默认转换方法
        String testFile = testTempDir + File.separator + "test.ofd";
        String targetPath = testTempDir + File.separator + "output";
        
        // 创建目标目录
        new File(targetPath).mkdirs();
        
        // 由于我们没有真实的OFD文件，这会失败，但可以验证方法调用
        boolean result = ofdConvertStrategy.convert(testFile, targetPath);
        assertFalse(result); // 预期失败，因为文件不存在
    }
    
    /**
     * 集成测试：如果有真实的OFD文件，可以取消注释这个测试
     */
    /*
    @Test
    void testRealOfdConversion() throws IOException {
        // 这个测试需要真实的OFD文件
        String realOfdFile = "path/to/real/test.ofd";
        String outputDir = testTempDir + File.separator + "real_output";
        Files.createDirectories(Paths.get(outputDir));
        
        // 测试转换为PDF
        boolean pdfResult = ofdConvertStrategy.convert(realOfdFile, outputDir, "test_output", "pdf");
        assertTrue(pdfResult);
        
        File pdfFile = new File(outputDir + File.separator + "test_output.pdf");
        assertTrue(pdfFile.exists());
        assertTrue(pdfFile.length() > 0);
        
        // 测试转换为PNG
        boolean pngResult = ofdConvertStrategy.convert(realOfdFile, outputDir, "test_output", "png");
        assertTrue(pngResult);
        
        File pngFile = new File(outputDir + File.separator + "test_output.png");
        assertTrue(pngFile.exists());
        assertTrue(pngFile.length() > 0);
    }
    */
    
    /**
     * 清理测试资源
     */
    void cleanUp() {
        try {
            Files.deleteIfExists(Paths.get(testTempDir));
        } catch (IOException e) {
            // 忽略清理异常
        }
    }
}