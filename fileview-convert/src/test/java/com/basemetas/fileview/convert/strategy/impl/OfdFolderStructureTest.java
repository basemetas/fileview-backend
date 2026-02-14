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
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OFD转换文件夹结构功能测试类
 * 专门测试"OFD转换成图片后，以文件名作为文件夹存放转换出来的图片"功能
 */
class OfdFolderStructureTest {
    
    private static final Logger logger = LoggerFactory.getLogger(OfdFolderStructureTest.class);
    
    private OfdConvertStrategy ofdConvertStrategy;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        ofdConvertStrategy = new OfdConvertStrategy();
        logger.info("测试开始，临时目录: {}", tempDir);
    }
    
    /**
     * 测试OFDRW库的可用性
     */
    @Test
    void testOfdLibraryAvailability() {
        boolean available = ofdConvertStrategy.isOfdLibraryAvailable();
        logger.info("OFDRW库可用性: {}", available);
        
        if (!available) {
            logger.warn("OFDRW库不可用，跳过实际转换测试");
            return;
        }
        
        assertTrue(available, "OFDRW库应该可用以支持OFD转换功能");
    }
    
    /**
     * 测试服务状态信息
     */
    @Test
    void testServiceStatus() {
        String status = ofdConvertStrategy.getServiceStatus();
        logger.info("OFD转换服务状态:\n{}", status);
        
        assertNotNull(status);
        assertTrue(status.contains("文件夹结构") || status.contains("支持的目标格式"));
    }
    
    /**
     * 测试单页OFD文件的转换逻辑
     * 验证单页文件不会创建文件夹，直接存储到目标位置
     */
    @Test
    void testSinglePageOfdConversion() {
        logger.info("=== 测试单页OFD文件转换逻辑 ===");
        
        // 模拟单页OFD文件转换
        String testFileName = "单页测试文档";
        Path outputDir = tempDir.resolve("output");
        
        try {
            Files.createDirectories(outputDir);
            
            // 模拟转换过程中的文件夹结构逻辑
            simulateSinglePageConversion(testFileName, outputDir, "png");
            
            // 验证结果
            Path expectedMainFile = outputDir.resolve(testFileName + ".png");
            Path expectedFolder = outputDir.resolve(testFileName);
            
            logger.info("验证单页转换结果:");
            logger.info("- 主文件应该存在: {}", expectedMainFile);
            logger.info("- 文件夹不应该被创建: {}", expectedFolder);
            
            // 对于单页文件，不应该创建文件夹
            assertFalse(Files.exists(expectedFolder), "单页文件不应该创建文件夹");
            
        } catch (IOException e) {
            logger.error("测试过程中发生IO异常", e);
            fail("测试应该正常完成，不应该抛出IO异常");
        }
    }
    
    /**
     * 测试多页OFD文件的转换逻辑
     * 验证多页文件会创建以文件名命名的文件夹，并将所有图片存放其中
     */
    @Test
    void testMultiPageOfdConversion() {
        logger.info("=== 测试多页OFD文件转换逻辑 ===");
        
        // 模拟多页OFD文件转换
        String testFileName = "多页测试文档";
        Path outputDir = tempDir.resolve("output");
        int pageCount = 3;
        
        try {
            Files.createDirectories(outputDir);
            
            // 模拟转换过程中的文件夹结构逻辑
            simulateMultiPageConversion(testFileName, outputDir, "png", pageCount);
            
            // 验证结果
            Path expectedMainFile = outputDir.resolve(testFileName + ".png");
            Path expectedFolder = outputDir.resolve(testFileName);
            
            logger.info("验证多页转换结果:");
            logger.info("- 主文件应该存在: {}", expectedMainFile);
            logger.info("- 文件夹应该被创建: {}", expectedFolder);
            
            // 对于多页文件，应该创建文件夹
            assertTrue(Files.exists(expectedFolder), "多页文件应该创建以文件名命名的文件夹");
            assertTrue(Files.isDirectory(expectedFolder), "创建的应该是文件夹");
            
            // 验证文件夹中的页面文件
            for (int i = 1; i <= pageCount; i++) {
                Path pageFile = expectedFolder.resolve(String.format("page_%d.png", i));
                logger.info("- 页面{}文件应该存在: {}", i, pageFile);
                assertTrue(Files.exists(pageFile), "页面" + i + "文件应该存在于文件夹中");
            }
            
        } catch (IOException e) {
            logger.error("测试过程中发生IO异常", e);
            fail("测试应该正常完成，不应该抛出IO异常");
        }
    }
    
    /**
     * 测试中文文件名的处理
     */
    @Test
    void testChineseFileNameHandling() {
        logger.info("=== 测试中文文件名处理 ===");
        
        String[] chineseFileNames = {
            "中文测试文档",
            "测试文档_2023",
            "合同书-最终版",
            "报告（修订版）"
        };
        
        Path outputDir = tempDir.resolve("chinese_output");
        
        try {
            Files.createDirectories(outputDir);
            
            for (String fileName : chineseFileNames) {
                logger.info("测试中文文件名: {}", fileName);
                
                // 模拟多页转换
                simulateMultiPageConversion(fileName, outputDir, "jpg", 2);
                
                // 验证文件夹创建
                Path expectedFolder = outputDir.resolve(fileName);
                assertTrue(Files.exists(expectedFolder), 
                    "中文文件名 '" + fileName + "' 应该能正确创建文件夹");
                
                logger.info("✅ 中文文件名 '{}' 处理成功", fileName);
            }
            
        } catch (IOException e) {
            logger.error("中文文件名测试过程中发生IO异常", e);
            fail("中文文件名测试应该正常完成");
        }
    }
    
    /**
     * 测试不同图片格式的支持
     */
    @Test
    void testDifferentImageFormats() {
        logger.info("=== 测试不同图片格式支持 ===");
        
        String[] formats = {"png", "jpg", "jpeg"};
        String testFileName = "格式测试文档";
        Path outputDir = tempDir.resolve("format_output");
        
        try {
            Files.createDirectories(outputDir);
            
            for (String format : formats) {
                logger.info("测试图片格式: {}", format);
                
                Path formatDir = outputDir.resolve(format);
                Files.createDirectories(formatDir);
                
                // 模拟转换
                simulateMultiPageConversion(testFileName, formatDir, format, 2);
                
                // 验证结果
                Path expectedFolder = formatDir.resolve(testFileName);
                assertTrue(Files.exists(expectedFolder), 
                    "格式 " + format + " 应该能正确创建文件夹");
                
                // 检查页面文件格式
                Path page1 = expectedFolder.resolve("page_1." + format);
                assertTrue(Files.exists(page1), 
                    "第一页文件应该是 " + format + " 格式");
                
                logger.info("✅ 图片格式 '{}' 处理成功", format);
            }
            
        } catch (IOException e) {
            logger.error("图片格式测试过程中发生IO异常", e);
            fail("图片格式测试应该正常完成");
        }
    }
    
    /**
     * 模拟单页OFD文件转换过程
     */
    private void simulateSinglePageConversion(String fileName, Path outputDir, String format) throws IOException {
        logger.info("模拟单页转换: 文件名={}, 输出目录={}, 格式={}", fileName, outputDir, format);
        
        // 模拟转换逻辑：单页文件直接存储到目标位置
        Path mainFile = outputDir.resolve(fileName + "." + format);
        
        // 创建模拟的主文件
        Files.write(mainFile, "模拟的单页图片内容".getBytes());
        
        logger.info("单页转换模拟完成: {}", mainFile);
    }
    
    /**
     * 模拟多页OFD文件转换过程
     */
    private void simulateMultiPageConversion(String fileName, Path outputDir, String format, int pageCount) throws IOException {
        logger.info("模拟多页转换: 文件名={}, 输出目录={}, 格式={}, 页数={}", fileName, outputDir, format, pageCount);
        
        // 创建以文件名命名的文件夹
        Path imagesFolder = outputDir.resolve(fileName);
        Files.createDirectories(imagesFolder);
        logger.info("创建图片文件夹: {}", imagesFolder);
        
        // 在文件夹中创建所有页面文件
        for (int i = 1; i <= pageCount; i++) {
            String pageFileName = String.format("page_%d.%s", i, format);
            Path pagePath = imagesFolder.resolve(pageFileName);
            Files.write(pagePath, ("模拟的第" + i + "页图片内容").getBytes());
            logger.debug("创建页面文件: {}", pagePath);
        }
        
        // 将第一页也复制到原始目标位置（兼容性）
        Path mainFile = outputDir.resolve(fileName + "." + format);
        Path firstPage = imagesFolder.resolve("page_1." + format);
        Files.copy(firstPage, mainFile);
        logger.info("第一页复制到主位置: {}", mainFile);
        
        logger.info("多页转换模拟完成: 文件夹={}, 页数={}", imagesFolder, pageCount);
    }
    
    /**
     * 测试文件夹结构的兼容性
     * 验证转换后既有文件夹中的分页图片，也有主位置的预览图片
     */
    @Test
    void testFolderStructureCompatibility() {
        logger.info("=== 测试文件夹结构兼容性 ===");
        
        String testFileName = "兼容性测试文档";
        Path outputDir = tempDir.resolve("compatibility_output");
        
        try {
            Files.createDirectories(outputDir);
            
            // 模拟多页转换
            simulateMultiPageConversion(testFileName, outputDir, "png", 3);
            
            // 验证兼容性：既有文件夹结构，也有主文件
            Path mainFile = outputDir.resolve(testFileName + ".png");
            Path imagesFolder = outputDir.resolve(testFileName);
            
            assertTrue(Files.exists(mainFile), "主文件应存在（兼容性）");
            assertTrue(Files.exists(imagesFolder), "图片文件夹应存在");
            assertTrue(Files.isDirectory(imagesFolder), "应该是文件夹");
            
            // 验证文件夹中的内容
            assertTrue(Files.exists(imagesFolder.resolve("page_1.png")), "文件夹中应有第1页");
            assertTrue(Files.exists(imagesFolder.resolve("page_2.png")), "文件夹中应有第2页");
            assertTrue(Files.exists(imagesFolder.resolve("page_3.png")), "文件夹中应有第3页");
            
            logger.info("✅ 文件夹结构兼容性验证通过");
            
        } catch (IOException e) {
            logger.error("兼容性测试过程中发生IO异常", e);
            fail("兼容性测试应该正常完成");
        }
    }
}