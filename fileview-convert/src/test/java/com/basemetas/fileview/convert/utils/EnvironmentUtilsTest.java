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
package com.basemetas.fileview.convert.utils;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EnvironmentUtils 测试类
 */
class EnvironmentUtilsTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentUtilsTest.class);
    
    @Test
    void testOperatingSystemDetection() {
        logger.info("操作系统: {}", EnvironmentUtils.getOsName());
        
        // Linux 环境检测
        boolean isLinux = EnvironmentUtils.isLinuxSystem();
        logger.info("Linux 环境: {}", isLinux);
        assertNotNull(EnvironmentUtils.getOsName());
    }
    
    @Test
    void testJavaVersion() {
        logger.info("Java 版本: {}", EnvironmentUtils.getJavaVersion());
        assertNotNull(EnvironmentUtils.getJavaVersion());
    }
    
    @Test
    void testEnvironmentInfo() {
        logger.info("当前工作目录: {}", EnvironmentUtils.getCurrentWorkingDirectory());
        logger.info("临时目录: {}", EnvironmentUtils.getTempDirectory());
        logger.info("文件分隔符: {}", EnvironmentUtils.getFileSeparator());
        
        assertNotNull(EnvironmentUtils.getCurrentWorkingDirectory());
        assertNotNull(EnvironmentUtils.getTempDirectory());
        assertNotNull(EnvironmentUtils.getFileSeparator());
    }
    
    @Test
    void testWslDetection() {
        boolean isWsl = EnvironmentUtils.isWslEnvironment();
        logger.info("WSL 环境: {}", isWsl);
        
        // 如果是 Linux 且为 WSL，应该能正确检测
        if (EnvironmentUtils.isLinuxSystem() && isWsl) {
            logger.info("检测到 WSL2 环境");
        }
    }
    
    @Test
    void testExternalCommands() {
        boolean has7z = EnvironmentUtils.isExternal7zAvailable();
        boolean hasImageMagick = EnvironmentUtils.isImageMagickAvailable();
        
        logger.info("7z 可用: {}", has7z);
        logger.info("ImageMagick 可用: {}", hasImageMagick);
    }
    
    @Test
    void testPrintEnvironmentInfo() {
        EnvironmentUtils.printEnvironmentInfo();
    }
    
    @Test
    void testCacheClearing() {
        // 先调用一次
        EnvironmentUtils.isWslEnvironment();
        
        // 清除缓存
        EnvironmentUtils.clearCache();
        
        // 再次调用应该重新检测
        EnvironmentUtils.isWslEnvironment();
    }
}
