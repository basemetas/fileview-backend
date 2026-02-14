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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 真实的999.ofd转换测试，检查第一页转换问题
 */
class Real999ConversionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(Real999ConversionTest.class);
    
    @Test
    void testReal999Conversion() throws IOException {
        String sourcePath = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\source\\999.ofd";
        String outputDir = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\debug_output";
        String targetFileName = "999";
        String targetFormat = "png";
        
        // 确保输出目录存在
        Files.createDirectories(Paths.get(outputDir));
        
        // 清理之前的文件
        File mainFile = new File(outputDir, targetFileName + "." + targetFormat);
        File folderDir = new File(outputDir, targetFileName);
        
        if (mainFile.exists()) {
            mainFile.delete();
            logger.info("删除旧的主文件: {}", mainFile.getAbsolutePath());
        }
        
        if (folderDir.exists()) {
            deleteDirectory(folderDir);
            logger.info("删除旧的文件夹: {}", folderDir.getAbsolutePath());
        }
        
        logger.info("=== 开始真实转换测试 ===");
        logger.info("源文件: {}", sourcePath);
        logger.info("输出目录: {}", outputDir);
        logger.info("目标文件名: {}", targetFileName);
        logger.info("目标格式: {}", targetFormat);
        
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        // 执行转换
        boolean result = strategy.convert(sourcePath, outputDir, targetFileName, targetFormat);
        
        logger.info("=== 转换结果: {} ===", result ? "成功" : "失败");
        
        // 详细检查结果
        logger.info("=== 结果文件检查 ===");
        
        // 检查主文件（第一页）
        if (mainFile.exists()) {
            logger.info("✅ 主文件存在: {} (大小: {} bytes)", mainFile.getAbsolutePath(), mainFile.length());
        } else {
            logger.error("❌ 主文件不存在: {}", mainFile.getAbsolutePath());
        }
        
        // 检查文件夹
        if (folderDir.exists() && folderDir.isDirectory()) {
            logger.info("✅ 文件夹存在: {}", folderDir.getAbsolutePath());
            
            File[] files = folderDir.listFiles();
            if (files != null && files.length > 0) {
                logger.info("文件夹中的文件数量: {}", files.length);
                for (File file : files) {
                    logger.info("  - {}: {} bytes", file.getName(), file.length());
                }
                
                // 特别检查第一页文件
                File page1File = new File(folderDir, "page_1." + targetFormat);
                if (page1File.exists()) {
                    logger.info("✅ 第一页文件存在于文件夹中: {} (大小: {} bytes)", page1File.getAbsolutePath(), page1File.length());
                } else {
                    logger.error("❌ 第一页文件不存在于文件夹中");
                }
            } else {
                logger.error("❌ 文件夹为空");
            }
        } else {
            logger.error("❌ 文件夹不存在: {}", folderDir.getAbsolutePath());
        }
        
        logger.info("=== 检查完成 ===");
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}