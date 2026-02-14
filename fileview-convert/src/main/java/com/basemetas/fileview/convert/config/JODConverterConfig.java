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
package com.basemetas.fileview.convert.config;

import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * JODConverter 配置类
 * 用于管理 LibreOffice 服务进程，支持带密码文档转换
 */
@Configuration
@ConditionalOnProperty(name = "libreoffice.jod.enabled", havingValue = "true")
public class JODConverterConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(JODConverterConfig.class);
    
    @Value("${libreoffice.jod.office-home:/opt/libreoffice}")
    private String officeHome;
    
    @Value("${libreoffice.jod.port-numbers:2002}")
    private String portNumbers;
    
    @Value("${libreoffice.jod.max-tasks-per-process:100}")
    private int maxTasksPerProcess;
    
    @Value("${libreoffice.jod.task-execution-timeout:300000}")
    private long taskExecutionTimeout;
    
    @Value("${libreoffice.jod.task-queue-timeout:30000}")
    private long taskQueueTimeout;
    
    @Value("${libreoffice.jod.process-timeout:120000}")
    private long processTimeout;
    
    @Value("${libreoffice.jod.process-retry-interval:250}")
    private long processRetryInterval;
    
    @Value("${libreoffice.jod.max-process-count:1}")
    private int maxProcessCount;
    
    /**
     * 创建 OfficeManager Bean
     * 负责管理 LibreOffice 进程池
     */
    @Bean
    public OfficeManager officeManager() {
        logger.info("🚀 初始化 JODConverter OfficeManager");
        logger.info("📁 LibreOffice 安装路径: {}", officeHome);
        logger.info("🔌 监听端口: {}", portNumbers);
        logger.info("⚙️  最大任务数/进程: {}", maxTasksPerProcess);
        logger.info("⏱️  任务超时时间: {}ms", taskExecutionTimeout);
        
        // 验证 LibreOffice 安装路径
        File officeHomeFile = new File(officeHome);
        if (!validateLibreOfficeInstallation(officeHomeFile)) {
            logger.error("❌ LibreOffice 未正确安装: {}", officeHome);
            throw new IllegalArgumentException("Invalid LibreOffice installation: " + officeHome);
        }
        
        // 解析端口号数组
        int[] ports = parsePortNumbers(portNumbers);
        
        try {
            OfficeManager manager = LocalOfficeManager.builder()
                .officeHome(officeHomeFile)
                .portNumbers(ports)
                .maxTasksPerProcess(maxTasksPerProcess)
                .taskExecutionTimeout(taskExecutionTimeout)
                .taskQueueTimeout(taskQueueTimeout)
                .processTimeout(processTimeout)
                .processRetryInterval(processRetryInterval)
                .install()
                .build();
            
            // 启动 LibreOffice 进程
            manager.start();
            logger.info("✅ JODConverter OfficeManager 启动成功");
            
            return manager;
            
        } catch (Exception e) {
            logger.error("❌ JODConverter OfficeManager 启动失败", e);
            throw new RuntimeException("Failed to start JODConverter OfficeManager", e);
        }
    }
    
    /**
     * 创建 DocumentConverter Bean
     * 用于执行文档转换操作
     */
    @Bean
    public DocumentConverter documentConverter(OfficeManager officeManager) {
        logger.info("🔧 创建 JODConverter DocumentConverter");
        return LocalConverter.make(officeManager);
    }
    
    /**
     * 解析端口号字符串
     */
    private int[] parsePortNumbers(String portNumbers) {
        String[] parts = portNumbers.split(",");
        int[] ports = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                ports[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                logger.warn("⚠️ 无效的端口号: {}, 使用默认端口 2002", parts[i]);
                ports[i] = 2002;
            }
        }
        return ports;
    }
    
    /**
     * 验证 LibreOffice 安装
     * 改进：通过执行命令验证，类似 java -version
     * 
     * @param officeHome LibreOffice 安装目录
     * @return 是否有效安装
     */
    private boolean validateLibreOfficeInstallation(File officeHome) {
        // 方式1：优先尝试执行 soffice --version 命令
        if (validateByCommand()) {
            logger.info("✅ LibreOffice 命令验证通过（系统 PATH）");
            return true;
        }
        
        // 方式2：如果命令不可用，检查指定目录下的 soffice
        if (!officeHome.exists() || !officeHome.isDirectory()) {
            logger.warn("⚠️ LibreOffice 安装目录不存在: {}", officeHome.getAbsolutePath());
            return false;
        }
        
        File sofficeExecutable = new File(officeHome, "program/soffice");
        if (!sofficeExecutable.exists()) {
            logger.warn("⚠️ soffice 可执行文件不存在: {}", sofficeExecutable.getAbsolutePath());
            return false;
        }
        
        if (!sofficeExecutable.canExecute()) {
            logger.warn("⚠️ soffice 没有可执行权限: {}", sofficeExecutable.getAbsolutePath());
            return false;
        }
        
        logger.info("✅ LibreOffice 文件验证通过 - soffice: {}", sofficeExecutable.getAbsolutePath());
        return true;
    }
    
    /**
     * 通过执行命令验证 LibreOffice 是否已安装
     * 类似 java -version 的方式
     * 
     * @return 是否安装
     */
    private boolean validateByCommand() {
        try {
            // 尝试执行 soffice --version
            ProcessBuilder processBuilder = new ProcessBuilder("soffice", "--version");
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程结束（最多5秒）
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                String version = output.toString().trim();
                logger.info("✅ 检测到 LibreOffice: {}", version);
                return true;
            } else {
                logger.debug("soffice --version 执行失败，退出码: {}", 
                           finished ? process.exitValue() : "超时");
                return false;
            }
            
        } catch (java.io.IOException e) {
            // soffice 命令不存在
            logger.debug("soffice 命令不在系统 PATH 中");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("LibreOffice 命令验证被中断");
            return false;
        } catch (Exception e) {
            logger.debug("LibreOffice 命令验证失败: {}", e.getMessage());
            return false;
        }
    }
}
