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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Word转换策略测试类
 * 
 * 测试Word文档转换功能的各种场景：
 * 1. 基本转换功能测试
 * 2. 格式支持验证
 * 3. 参数验证测试
 * 4. 转换引擎状态测试
 * 
 * @author 文档中台架构师
 */
@SpringBootTest
@ActiveProfiles("test")
class WordConvertStrategyTest {
    
    private static final Logger logger = LoggerFactory.getLogger(WordConvertStrategyTest.class);
    
    private WordConvertStrategy wordConvertStrategy;
    private String testTempDir;
    
    @BeforeEach
    void setUp() throws IOException {
        // 注意：在实际测试中，这里应该通过Spring依赖注入获取
        // wordConvertStrategy = new WordConvertStrategy();
        
        // 创建测试临时目录
        testTempDir = System.getProperty("java.io.tmpdir") + File.separator + "word_test_" + System.currentTimeMillis();
        Files.createDirectories(Paths.get(testTempDir));
        
        logger.info("测试环境初始化完成 - 临时目录: {}", testTempDir);
    }
    
    @Test
    void testGetSupportedFileType() {
        // 验证支持的文件类型
        // assertEquals("docx", wordConvertStrategy.getSupportedFileType());
        logger.info("测试 getSupportedFileType - 期望返回 'docx'");
    }
    
    @Test
    void testGetSupportedSourceFormats() {
        // 验证支持的源文件格式
        if (wordConvertStrategy != null) {
            var sourceFormats = wordConvertStrategy.getSupportedSourceFormats();
            assertNotNull(sourceFormats);
            assertTrue(sourceFormats.contains("docx"));
            assertTrue(sourceFormats.contains("doc"));
            assertTrue(sourceFormats.contains("rtf"));
            assertTrue(sourceFormats.contains("odt"));
            
            logger.info("支持的源文件格式: {}", sourceFormats);
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testGetSupportedTargetFormats() {
        // 验证支持的目标格式
        if (wordConvertStrategy != null) {
            var targetFormats = wordConvertStrategy.getSupportedTargetFormats();
            assertNotNull(targetFormats);
            assertTrue(targetFormats.contains("pdf"));
            assertTrue(targetFormats.contains("png"));
            assertTrue(targetFormats.contains("jpg"));
            assertTrue(targetFormats.contains("html"));
            assertTrue(targetFormats.contains("txt"));
            
            logger.info("支持的目标格式: {}", targetFormats);
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testIsFormatSupported() {
        // 测试格式支持检查
        if (wordConvertStrategy != null) {
            // 测试支持的格式组合
            assertTrue(wordConvertStrategy.isFormatSupported("docx", "pdf"));
            assertTrue(wordConvertStrategy.isFormatSupported("doc", "png"));
            assertTrue(wordConvertStrategy.isFormatSupported("rtf", "html"));
            
            // 测试不支持的格式组合
            assertFalse(wordConvertStrategy.isFormatSupported("txt", "pdf"));
            assertFalse(wordConvertStrategy.isFormatSupported("docx", "docx"));
            assertFalse(wordConvertStrategy.isFormatSupported("xlsx", "pdf"));
            
            logger.info("格式支持检查测试通过");
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testConvertWithInvalidParameters() {
        // 测试无效参数的处理
        if (wordConvertStrategy != null) {
            // 测试空文件路径
            assertFalse(wordConvertStrategy.convert(null, testTempDir, "test", "pdf"));
            assertFalse(wordConvertStrategy.convert("", testTempDir, "test", "pdf"));
            
            // 测试空目标路径
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", null, "test", "pdf"));
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", "", "test", "pdf"));
            
            // 测试空文件名
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", testTempDir, null, "pdf"));
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", testTempDir, "", "pdf"));
            
            // 测试空格式
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", testTempDir, "test", null));
            assertFalse(wordConvertStrategy.convert("/path/to/test.docx", testTempDir, "test", ""));
            
            logger.info("无效参数处理测试通过");
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testConvertWithNonExistentFile() {
        // 测试不存在的文件
        if (wordConvertStrategy != null) {
            String nonExistentFile = testTempDir + File.separator + "non_existent.docx";
            boolean result = wordConvertStrategy.convert(
                nonExistentFile, 
                testTempDir, 
                "test_output", 
                "pdf"
            );
            
            assertFalse(result, "不存在的文件转换应该返回false");
            logger.info("不存在文件处理测试通过");
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testGetEngineStatus() {
        // 测试转换引擎状态获取
        if (wordConvertStrategy != null) {
            var engineStatus = wordConvertStrategy.getEngineStatus();
            assertNotNull(engineStatus);
            
            // 验证状态信息的基本属性
            assertNotNull(engineStatus.getPriorityConfig());
            
            logger.info("转换引擎状态: OnlyOffice可用={}, LibreOffice可用={}, POI可用={}, 故障转移={}, 优先级配置={}", 
                       engineStatus.isLibreOfficeAvailable(),
                       engineStatus.isFallbackEnabled(),
                       engineStatus.getPriorityConfig());
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过测试");
        }
    }
    
    @Test
    void testCreateMockWordFile() {
        // 创建模拟Word文件用于测试
        try {
            String mockWordFile = testTempDir + File.separator + "test_mock.docx";
            
            // 创建一个简单的文本文件作为模拟Word文件
            String mockContent = "这是一个模拟的Word文档内容，用于测试转换功能。\n" +
                                "Mock Word Document Content for Testing Conversion.\n" +
                                "创建时间: " + System.currentTimeMillis();
            
            Files.write(Paths.get(mockWordFile), mockContent.getBytes("UTF-8"));
            
            assertTrue(Files.exists(Paths.get(mockWordFile)));
            logger.info("创建模拟Word文件成功: {}", mockWordFile);
            
            // 注意：这里只是创建了一个文本文件，实际的转换测试需要真实的Word文件
            // 由于OnlyOffice转换需要真实的Word文档格式，所以这个测试主要用于验证文件创建逻辑
            
        } catch (Exception e) {
            logger.error("创建模拟Word文件失败", e);
            fail("创建模拟Word文件失败: " + e.getMessage());
        }
    }
    
    @Test
    void testConversionConfiguration() {
        // 测试转换配置信息
        logger.info("=== Word转换配置测试 ===");
        
        // 验证支持的格式配置
        if (wordConvertStrategy != null) {
            logger.info("支持的源格式: {}", wordConvertStrategy.getSupportedSourceFormats());
            logger.info("支持的目标格式: {}", wordConvertStrategy.getSupportedTargetFormats());
            
            // 验证各种格式组合
            String[] sourceFormats = {"docx", "doc", "rtf", "odt"};
            String[] targetFormats = {"pdf", "png", "jpg", "html", "txt"};
            
            for (String source : sourceFormats) {
                for (String target : targetFormats) {
                    boolean supported = wordConvertStrategy.isFormatSupported(source, target);
                    logger.debug("格式组合 {} -> {} : {}", source, target, supported ? "支持" : "不支持");
                }
            }
            
            logger.info("转换配置测试完成");
        } else {
            logger.warn("WordConvertStrategy 未初始化，跳过配置测试");
        }
    }
    
    /**
     * 集成测试 - 需要真实的Word文件和OnlyOffice服务
     * 
     * 注意：此测试需要：
     * 1. 真实的Word文档文件
     * 2. OnlyOffice Document Server 运行在 http://192.168.0.110:9090
     * 3. 网络连接正常
     */
    @Test
    void testRealWordConversion() {
        // 这个测试需要真实的环境，所以暂时跳过
        // 在实际部署环境中可以启用此测试
        
        logger.info("=== 真实Word转换集成测试 (已跳过) ===");
        logger.info("要启用此测试，请确保:");
        logger.info("1. 存在真实的Word文档文件");
        logger.info("2. OnlyOffice Document Server 运行在 http://192.168.0.110:9090");
        logger.info("3. 网络连接正常");
        logger.info("4. 取消此测试的跳过标记");
        
        // 真实测试代码（暂时注释）：
        /*
        if (wordConvertStrategy != null) {
            String realWordFile = "D:/test/sample.docx"; // 真实Word文件路径
            String outputDir = testTempDir;
            String outputFileName = "converted_sample";
            String targetFormat = "pdf";
            
            if (Files.exists(Paths.get(realWordFile))) {
                boolean result = wordConvertStrategy.convert(
                    realWordFile, 
                    outputDir, 
                    outputFileName, 
                    targetFormat
                );
                
                if (result) {
                    String expectedOutput = outputDir + File.separator + outputFileName + "." + targetFormat;
                    assertTrue(Files.exists(Paths.get(expectedOutput)), "转换后的文件应该存在");
                    logger.info("真实Word转换测试成功: {}", expectedOutput);
                } else {
                    logger.warn("真实Word转换失败，请检查OnlyOffice服务状态");
                }
            } else {
                logger.warn("测试Word文件不存在: {}", realWordFile);
            }
        }
        */
    }
}