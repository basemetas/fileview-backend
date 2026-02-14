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

/**
 * 专门诊断999.ofd文件第一页转换问题的测试类
 */
class Ofd999DiagnosticTest {
    
    private static final Logger logger = LoggerFactory.getLogger(Ofd999DiagnosticTest.class);
    
    @Test
    void diagnose999OfdFile() {
        String filePath = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\source\\999.ofd";
        
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        logger.info("开始诊断999.ofd文件...");
        
        // 使用诊断方法
        String diagnosis = strategy.diagnoseOfdFile(filePath);
        
        logger.info("诊断结果:\n{}", diagnosis);
        
        // 尝试实际转换测试
        String outputDir = "D:\\myWorkSpace\\fileview-backend\\fileTemp\\output";
        String targetFileName = "999_diagnostic_test";
        String targetFormat = "png";
        
        logger.info("尝试实际转换测试...");
        
        boolean result = strategy.convert(filePath, outputDir, targetFileName, targetFormat);
        
        logger.info("转换结果: {}", result ? "成功" : "失败");
        
        if (result) {
            // 检查生成的文件
            java.io.File outputFile = new java.io.File(outputDir + "\\" + targetFileName + "." + targetFormat);
            java.io.File folderDir = new java.io.File(outputDir + "\\" + "999");
            
            logger.info("输出文件检查:");
            logger.info("- 主文件存在: {} (大小: {} bytes)", outputFile.exists(), outputFile.exists() ? outputFile.length() : 0);
            logger.info("- 文件夹存在: {}", folderDir.exists());
            
            if (folderDir.exists() && folderDir.isDirectory()) {
                java.io.File[] files = folderDir.listFiles();
                if (files != null) {
                    logger.info("- 文件夹中的文件数量: {}", files.length);
                    for (java.io.File file : files) {
                        logger.info("  * {}: {} bytes", file.getName(), file.length());
                    }
                }
            }
        }
    }
}