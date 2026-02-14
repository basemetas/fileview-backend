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
 * 专门测试999.ofd并行转换中第一页问题的调试类
 */
class ParallelFirstPageDebugTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ParallelFirstPageDebugTest.class);
    
    @Test
    void testSerialVsParallelConversion() throws IOException {
        String sourcePath = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\source\\999.ofd";
        String serialOutputDir = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\serial_test";
        String parallelOutputDir = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\parallel_test";
        
        // 确保输出目录存在
        Files.createDirectories(Paths.get(serialOutputDir));
        Files.createDirectories(Paths.get(parallelOutputDir));
        
        // 清理之前的文件
        cleanupDirectory(serialOutputDir);
        cleanupDirectory(parallelOutputDir);
        
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        logger.info("=== 测试串行转换 ===");
        // 强制使用串行转换
        boolean serialResult = strategy.convertWithSerialMode(sourcePath, serialOutputDir + "/999_serial.png", "png");
        logger.info("串行转换结果: {}", serialResult);
        
        // 检查串行转换结果
        File serialMainFile = new File(serialOutputDir, "999_serial.png");
        File serialFolder = new File(serialOutputDir, "999");
        
        logger.info("串行转换文件检查:");
        logger.info("- 主文件: {} (存在: {}, 大小: {})", 
                   serialMainFile.getAbsolutePath(), 
                   serialMainFile.exists(), 
                   serialMainFile.exists() ? serialMainFile.length() : 0);
        
        if (serialFolder.exists()) {
            File[] serialFiles = serialFolder.listFiles();
            logger.info("- 文件夹: {} (文件数: {})", serialFolder.getAbsolutePath(), 
                       serialFiles != null ? serialFiles.length : 0);
            if (serialFiles != null) {
                for (File file : serialFiles) {
                    logger.info("  * {}: {} bytes", file.getName(), file.length());
                }
            }
        }
        
        logger.info("=== 测试并行转换 ===");
        // 强制使用并行转换
        boolean parallelResult = strategy.convertWithParallelMode(sourcePath, parallelOutputDir + "/999_parallel.png", "png");
        logger.info("并行转换结果: {}", parallelResult);
        
        // 检查并行转换结果
        File parallelMainFile = new File(parallelOutputDir, "999_parallel.png");
        File parallelFolder = new File(parallelOutputDir, "999");
        
        logger.info("并行转换文件检查:");
        logger.info("- 主文件: {} (存在: {}, 大小: {})", 
                   parallelMainFile.getAbsolutePath(), 
                   parallelMainFile.exists(), 
                   parallelMainFile.exists() ? parallelMainFile.length() : 0);
        
        if (parallelFolder.exists()) {
            File[] parallelFiles = parallelFolder.listFiles();
            logger.info("- 文件夹: {} (文件数: {})", parallelFolder.getAbsolutePath(), 
                       parallelFiles != null ? parallelFiles.length : 0);
            if (parallelFiles != null) {
                for (File file : parallelFiles) {
                    logger.info("  * {}: {} bytes", file.getName(), file.length());
                }
            }
        }
        
        // 比较结果
        logger.info("=== 比较分析 ===");
        if (serialMainFile.exists() && parallelMainFile.exists()) {
            logger.info("串行主文件大小: {} bytes", serialMainFile.length());
            logger.info("并行主文件大小: {} bytes", parallelMainFile.length());
            
            if (serialMainFile.length() != parallelMainFile.length()) {
                logger.warn("⚠️ 主文件大小不一致!");
            } else {
                logger.info("✅ 主文件大小一致");
            }
        } else {
            logger.error("❌ 主文件检查失败:");
            logger.error("  串行主文件存在: {}", serialMainFile.exists());
            logger.error("  并行主文件存在: {}", parallelMainFile.exists());
        }
    }
    
    private void cleanupDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        cleanupDirectory(file.getAbsolutePath());
                    }
                    file.delete();
                }
            }
        }
    }
}