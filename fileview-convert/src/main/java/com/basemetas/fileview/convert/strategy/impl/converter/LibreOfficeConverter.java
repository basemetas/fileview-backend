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
package com.basemetas.fileview.convert.strategy.impl.converter;

import com.basemetas.fileview.convert.common.exception.UnsupportedException;
import com.basemetas.fileview.convert.common.exception.ErrorCode;
import com.basemetas.fileview.convert.strategy.model.LibreOfficeStatus;
import com.sun.star.document.UpdateDocMode;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * LibreOffice通用文档转换器
 * 
 * 基于LibreOffice Command Line的文档转换实现
 * 支持多种文档格式转换，特性：
 * - 开源免费，兼容性好
 * - 支持Linux环境命令行转换
 * - 智能格式检测和参数优化
 * - 支持Word、Excel、PPT等多种文档格式
 * 
 * @author 夫子
 */
@Component
public class LibreOfficeConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(LibreOfficeConverter.class);    
    
    // JODConverter 模式（支持密码）
    @Autowired(required = false)
    private DocumentConverter jodConverter;
      
    @Value("${libreoffice.jod.enabled:false}")
    private boolean jodEnabled;
    
    // CLI 模式（原命令行方式）
    @Value("${libreoffice.command.path:libreoffice}")
    private String libreOfficeCommand;
    
    @Value("${libreoffice.temp.dir:}")
    private String tempDir;
    
    @Value("${libreoffice.conversion.timeout:120}")
    private int conversionTimeoutSeconds;
    
    @Value("${libreoffice.enable:true}")
    private boolean libreOfficeEnabled;
    
    @Value("${libreoffice.headless:true}")
    private boolean headlessMode;
    
    @Value("${libreoffice.invisible:true}")
    private boolean invisibleMode;

    /**
     * LibreOffice 加密文档黑名单（这些格式带密码时不使用 LibreOffice，避免崩溃）
     */
    @Value("${libreoffice.encrypted-blacklist:doc,xls,ppt,wps,wpt,et,ett,dps,dpt}")
    private String encryptedBlacklist;
    
    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        if (jodEnabled && jodConverter != null) {
            logger.info("🔧 LibreOffice转换器初始化完成 - 模式: JODConverter（支持密码）");
        } else {
            logger.info("🔧 LibreOffice转换器初始化完成 - 模式: CLI（命令行）");
        }
    }
    
    /**
     * 转换文档
     * 
     * @param sourceFilePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param sourceFormat 源文件格式
     * @param targetFormat 目标文件格式
     * @param documentType 文档类型（Word/Excel/PPT）
     * @return 转换是否成功
     */
    public boolean convertDocument(String sourceFilePath, String targetFilePath, 
                                 String sourceFormat, String targetFormat, String documentType) {
        return convertDocument(sourceFilePath, targetFilePath, sourceFormat, targetFormat, documentType, null);
    }
    
    /**
     * 转换文档（支持密码参数）
     * 
     * @param sourceFilePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param sourceFormat 源文件格式
     * @param targetFormat 目标文件格式
     * @param documentType 文档类型（Word/Excel/PPT）
     * @param password 文档密码（可为null）
     * @return 转换是否成功
     */
    public boolean convertDocument(String sourceFilePath, String targetFilePath, 
                                 String sourceFormat, String targetFormat, 
                                 String documentType, String password) {
        if (!libreOfficeEnabled) {
            logger.warn("LibreOffice {}转换器已禁用", documentType);
            return false;
        }
        
        // 优先使用 JODConverter 模式（支持密码）
        if (jodEnabled && jodConverter != null) {
            logger.info("🔑 使用 JODConverter 模式转换 - 文档类型: {}, 带密码: {}", 
                       documentType, password != null && !password.trim().isEmpty());
            return convertWithJOD(sourceFilePath, targetFilePath, sourceFormat, targetFormat, documentType, password);
        }
        
        // 降级到 CLI 模式（不支持密码）
        if (password != null && !password.trim().isEmpty()) {
            logger.warn("⚠️ CLI 模式不支持密码文档，跳过此引擎 - 文档类型: {}", documentType);
            logger.info("提示：请启用 JODConverter 模式或 X2T 引擎处理带密码文档");
            return false;
        }
        
        logger.info("🔧 使用 CLI 模式转换 - 文档类型: {}", documentType);
        return convertWithCLI(sourceFilePath, targetFilePath, sourceFormat, targetFormat, documentType);
    }
    
    /**
     * 使用 JODConverter 模式转换(支持密码)
     */
    private boolean convertWithJOD(String sourceFilePath, String targetFilePath,
                                    String sourceFormat, String targetFormat,
                                    String documentType, String password) {
        try {
            // ⚠️ 黑名单检查：带密码的黑名单格式直接拒绝，避免 LibreOffice 崩溃
            if (password != null && !password.trim().isEmpty() && isInEncryptedBlacklist(sourceFormat)) {
                logger.warn("⚠️ {} 格式带密码文档在 LibreOffice 黑名单中，拒绝转换避免崩溃", sourceFormat.toUpperCase());
                throw new UnsupportedException(
                    String.valueOf(ErrorCode.UNSUPPORTED_CONVERSION.getCode()),
                    "LibreOffice 不支持带密码的 " + sourceFormat.toUpperCase() + " 文档"
                );
            }

            logger.info("开始JODConverter转换 - 源文件: {}, 目标文件: {}, 格式: {}, 带密码: {}", 
                       sourceFilePath, targetFilePath, sourceFormat.toUpperCase(), 
                       password != null && !password.trim().isEmpty());
            
            File sourceFile = new File(sourceFilePath);
            File targetFile = new File(targetFilePath);
            
            // 参考 kkFileView 的实现方式
            LocalConverter.Builder builder;
            
            if (password != null && !password.trim().isEmpty()) {
                logger.info("🔑 使用密码加载文档");
                
                // 构建加载属性（loadProperties）
                Map<String, Object> loadProperties = new HashMap<>();
                loadProperties.put("Hidden", true);  // 隐藏模式
                loadProperties.put("ReadOnly", true); // 只读模式
                loadProperties.put("UpdateDocMode", UpdateDocMode.NO_UPDATE); // 禁止更新
                loadProperties.put("Password", password); // 密码
                
                // 构建存储属性（storeProperties）
                Map<String, Object> filterData = new HashMap<>();
                filterData.put("EncryptFile", true);
                
                Map<String, Object> storeProperties = new HashMap<>();
                storeProperties.put("FilterData", filterData);
                
                // 使用 builder 模式设置 loadProperties 和 storeProperties
                builder = LocalConverter.builder()
                    .loadProperties(loadProperties)
                    .storeProperties(storeProperties);
                    
            } else {
                // 无密码转换
                builder = LocalConverter.builder();
            }
            
            // 执行转换
            builder.build()
                .convert(sourceFile)
                .to(targetFile)
                .execute();
            
            // 检查转换结果
            if (targetFile.exists() && targetFile.length() > 0) {
                logger.info("✅ JODConverter转换成功 - 目标文件: {}, 大小: {} bytes",
                           targetFilePath, targetFile.length());
                return true;
            } else {
                logger.error("❌ JODConverter转换失败 - 目标文件不存在或为空");
                return false;
            }
            
        } catch (UnsupportedException e) {
            // 🔑 关键修复:引擎不支持异常需要重新抛出
            logger.warn("⚠️ LibreOffice JOD 捕获到引擎不支持异常,重新抛出 - ErrorCode: {}", e.getErrorCode());
            throw e; // 重新抛出,让上层捕获
        } catch (OfficeException e) {
            // 🚨 动态识别旧式加密文档不支持的场景
            if (isLegacyEncryptionUnsupported(e, password, sourceFormat)) {
                logger.warn("⚠️ LibreOffice 不支持带密码的旧式 {} 文档 - 错误: {}", 
                           sourceFormat.toUpperCase(), e.getMessage());
                throw new UnsupportedException(
                    String.valueOf(ErrorCode.UNSUPPORTED_CONVERSION.getCode()),
                    "LibreOffice 不支持带密码的旧格式 " + sourceFormat.toUpperCase()
                );
            }
            logger.error("❌ JODConverter转换异常", e);
            return false;
        } catch (Exception e) {
            logger.error("❌ JODConverter转换失败", e);
            return false;
        }
    }
    
    /**
     * 使用 CLI 模式转换（不支持密码）
     */
    private boolean convertWithCLI(String sourceFilePath, String targetFilePath,
                                    String sourceFormat, String targetFormat,
                                    String documentType) {
        File tempInstanceDir = null;
        try {
            // 1. 验证参数
            if (!validateParameters(sourceFilePath, targetFilePath, sourceFormat, targetFormat, documentType)) {
                return false;
            }
            
            // 2. 检查LibreOffice服务可用性
            if (!isServiceAvailable()) {
                logger.error("LibreOffice {}服务不可用", documentType);
                return false;
            }
            
            // 3. 创建临时实例目录
            if (tempDir != null && !tempDir.trim().isEmpty()) {
                tempInstanceDir = new File(tempDir, "instance_" + System.currentTimeMillis());
                tempInstanceDir.mkdirs();
                logger.debug("创建LibreOffice临时配置目录: {}", tempInstanceDir.getAbsolutePath());
            }
            
            // 4. 准备转换参数（CLI 模式不支持密码）
            List<String> command = buildConvertCommand(sourceFilePath, targetFilePath, 
                                                     sourceFormat, targetFormat, documentType, tempInstanceDir);
            
            // 5. 执行转换
            boolean success = executeConversion(command, documentType);
            
            // 6. 处理文件重命名（LibreOffice会使用源文件名生成输出文件）
            if (success) {
                success = handleFileRename(sourceFilePath, targetFilePath, targetFormat, documentType);
            }
            
            if (success) {
                logger.info("LibreOffice {}文档转换成功 - 目标文件: {}", documentType, targetFilePath);
            } else {
                logger.error("LibreOffice {}文档转换失败", documentType);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("LibreOffice {}文档转换异常", documentType, e);
            return false;
        } finally {
            // 清理临时实例目录
            if (tempInstanceDir != null && tempInstanceDir.exists()) {
                cleanupTempInstanceDir(tempInstanceDir);
            }
        }
    }
    
    /**
     * 验证转换参数
     */
    private boolean validateParameters(String sourceFilePath, String targetFilePath, 
                                     String sourceFormat, String targetFormat, String documentType) {
        if (sourceFilePath == null || sourceFilePath.trim().isEmpty()) {
            logger.error("{}源文件路径不能为空", documentType);
            return false;
        }
        
        if (targetFilePath == null || targetFilePath.trim().isEmpty()) {
            logger.error("{}目标文件路径不能为空", documentType);
            return false;
        }
        
        // 验证源文件是否存在
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            logger.error("{}源文件不存在或不是有效文件: {}", documentType, sourceFilePath);
            return false;
        }
        
        // 注意：格式验证已在Strategy层完成，这里不再重复验证
        // Converter层只负责转换执行，信任上层传入的格式参数
        
        return true;
    }
    
    /**
     * 构建转换命令
     */
    private List<String> buildConvertCommand(String sourceFilePath, String targetFilePath, 
                                           String sourceFormat, String targetFormat, 
                                           String documentType, File tempInstanceDir) {
        List<String> command = new ArrayList<>();
        
        command.add(libreOfficeCommand);
        
        // 基础参数
        if (headlessMode) {
            command.add("--headless");
        }
        
        if (invisibleMode) {
            command.add("--invisible");
        }
        
        command.add("--nologo");
        command.add("--norestore");
        command.add("--nodefault");
        
        // 临时用户配置目录（必须设置，避免多实例冲突）
        if (tempInstanceDir != null) {
            command.add("-env:UserInstallation=" + tempInstanceDir.toURI().toString());
            logger.debug("设置LibreOffice临时配置目录: {}", tempInstanceDir.getAbsolutePath());
        }
        
        // 转换参数
        command.add("--convert-to");
        command.add(getLibreOfficeFormat(targetFormat, documentType));
        
        // 输出目录
        File targetFile = new File(targetFilePath);
        String outputDir = targetFile.getParent();
        if (outputDir != null) {
            command.add("--outdir");
            command.add(outputDir);
        }
        
        // 源文件
        command.add(sourceFilePath);
        
        logger.info("LibreOffice {}转换命令: {}", documentType, String.join(" ", command));
        
        return command;
    }
    
    /**
     * 获取LibreOffice格式参数
     */
    private String getLibreOfficeFormat(String targetFormat, String documentType) {
        // 根据文档类型选择对应的格式映射方法
        switch (documentType.toLowerCase()) {
            case "word":
                return getLibreOfficeFormatForWord(targetFormat);
            case "excel":
                return getLibreOfficeFormatForExcel(targetFormat);
            case "ppt":
                return getLibreOfficeFormatForPpt(targetFormat);
            default:
                return getLibreOfficeFormatGeneric(targetFormat);
        }
    }
    
    /**
     * 通用格式映射
     */
    private String getLibreOfficeFormatGeneric(String targetFormat) {
        String format = targetFormat.toLowerCase();
        
        switch (format) {
            case "pdf":
                return "pdf";
            case "png":
                return "pdf"; // PNG需要通过PDF中转
            case "jpg":
            case "jpeg":
                return "pdf"; // JPEG需要通过PDF中转
            case "bmp":
                return "pdf"; // BMP需要通过PDF中转
            case "gif":
                return "pdf"; // GIF需要通过PDF中转
            default:
                return format;
        }
    }
    
    /**
     * 获取LibreOffice格式参数（Word专用）
     */
    private String getLibreOfficeFormatForWord(String targetFormat) {
        String format = targetFormat.toLowerCase();
        
        switch (format) {
            case "html":
                return "html:XHTML Writer File:UTF8";
            case "txt":
                return "txt:Text (encoded):UTF8";
            case "docx":
                return "docx";
            default:
                return getLibreOfficeFormatGeneric(format);
        }
    }
    
    /**
     * 获取LibreOffice格式参数（Excel专用）
     */
    private String getLibreOfficeFormatForExcel(String targetFormat) {
        String format = targetFormat.toLowerCase();
        
        switch (format) {
            case "pdfa":
                return "pdf:writer_pdf_Export"; // PDF/A格式
            case "csv":
                return "csv:Text - txt - csv (StarCalc):44,34,76,1,,0,false,true,true,false,false";
            case "ods":
                return "ods";
            case "ots":
                return "ots";
            case "xlsx":
                return "xlsx";
            case "xlsm":
                return "xlsm";
            case "xltm":
                return "xltm";
            case "xltx":
                return "xltx";
            default:
                return getLibreOfficeFormatGeneric(format);
        }
    }
    
    /**
     * 获取LibreOffice格式参数（PPT专用）
     */
    private String getLibreOfficeFormatForPpt(String targetFormat) {
        String format = targetFormat.toLowerCase();
        
        switch (format) {
            case "pdfa":
                return "pdf:impress_pdf_Export"; // PDF/A格式
            case "odp":
                return "odp";
            case "otp":
                return "otp";
            case "pptx":
                return "pptx";
            case "pptm":
                return "pptm";
            case "potx":
                return "potx";
            case "potm":
                return "potm";
            case "ppsx":
                return "ppsx";
            case "ppsm":
                return "ppsm";
            default:
                return getLibreOfficeFormatGeneric(format);
        }
    }
    
    /**
     * 执行转换命令
     */
    private boolean executeConversion(List<String> command, String documentType) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            logger.debug("执行LibreOffice {}转换命令: {}", documentType, String.join(" ", command));
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程完成
            boolean finished = process.waitFor(conversionTimeoutSeconds, TimeUnit.SECONDS);
            
            if (!finished) {
                logger.error("LibreOffice {}转换超时，强制终止进程", documentType);
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            
            // 记录LibreOffice输出（无论成功失败）
            String outputStr = output.toString().trim();
            if (!outputStr.isEmpty()) {
                logger.info("LibreOffice {}输出: {}", documentType, outputStr);
            }
            
            if (exitCode == 0) {
                logger.debug("LibreOffice {}转换命令执行完成，退出码: 0", documentType);
                return true;
            } else {
                logger.error("LibreOffice {}转换失败，退出码: {}", documentType, exitCode);
                logger.error("LibreOffice 命令: {}", String.join(" ", command));
                return false;
            }
            
        } catch (Exception e) {
            logger.error("执行LibreOffice {}转换命令失败", documentType, e);
            return false;
        }
    }
    
    /**
     * 检查LibreOffice服务是否可用
     */
    public boolean isServiceAvailable() {
        if (!libreOfficeEnabled) {
            logger.debug("LibreOffice转换器已禁用");
            return false;
        }
        
        // 尝试多个可能的LibreOffice命令路径
        String[] possibleCommands = {
            libreOfficeCommand,
            "/usr/bin/libreoffice",
            "/usr/local/bin/libreoffice",
            "/opt/libreoffice/program/soffice",
            "/opt/libreoffice*/program/soffice",
            "soffice",
            "libreoffice7.5",
            "libreoffice7.6",
            "libreoffice24.8",
            "libreoffice25.8"
        };
        
        for (String command : possibleCommands) {
            if (testLibreOfficeCommand(command)) {
                if (!command.equals(libreOfficeCommand)) {
                    logger.info("找到可用的LibreOffice命令: {} (配置中的: {})", command, libreOfficeCommand);
                }
                return true;
            }
        }
        
        logger.error("所有可能的LibreOffice命令都不可用: {}", Arrays.asList(possibleCommands));
        return false;
    }
    
    /**
     * 测试指定的LibreOffice命令是否可用
     */
    private boolean testLibreOfficeCommand(String command) {
        try {
            logger.debug("正在测试LibreOffice命令: {}", command);
            
            // 执行版本检查命令
            ProcessBuilder processBuilder = new ProcessBuilder(command, "--version");
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                logger.debug("命令 {} 执行超时，强制终止进程", command);
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                logger.debug("命令 {} 执行成功，输出: {}", command, output.toString().trim());
                return true;
            } else {
                logger.debug("命令 {} 执行失败，退出码: {}, 输出: {}", command, exitCode, output.toString());
                return false;
            }
            
        } catch (Exception e) {
            logger.debug("命令 {} 测试异常: {}", command, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取LibreOffice状态信息
     */
    public LibreOfficeStatus getLibreOfficeStatus() {
        LibreOfficeStatus status = new LibreOfficeStatus();
        status.setEnabled(libreOfficeEnabled);
        status.setAvailable(isServiceAvailable());
        status.setCommand(libreOfficeCommand);
        status.setTimeout(conversionTimeoutSeconds);
        status.setHeadless(headlessMode);
        status.setInvisible(invisibleMode);
        status.setTempDir(tempDir);
        
        // 获取版本信息
        if (status.isAvailable()) {
            status.setVersion(getLibreOfficeVersion());
        }
        
        return status;
    }
    
    /**
     * 获取LibreOffice版本信息
     */
    private String getLibreOfficeVersion() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(libreOfficeCommand, "--version");
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(" ");
                }
            }
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                return output.toString().trim();
            }
            
        } catch (Exception e) {
            logger.debug("获取LibreOffice版本失败", e);
        }
        
        return "未知";
    }
    
    /**
     * 处理文件重命名（LibreOffice会使用源文件名生成输出文件）
     */
    private boolean handleFileRename(String sourceFilePath, String targetFilePath, String targetFormat, String documentType) {
        try {
            File sourceFile = new File(sourceFilePath);
            File targetFile = new File(targetFilePath);
            
            // 获取源文件的基础名（无扩展名）
            String sourceBaseName = sourceFile.getName();
            int lastDotIndex = sourceBaseName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                sourceBaseName = sourceBaseName.substring(0, lastDotIndex);
            }
            
            // LibreOffice生成的文件路径（使用源文件的基础名）
            String actualOutputPath = targetFile.getParent() + File.separator + sourceBaseName + "." + targetFormat.toLowerCase();
            File actualOutputFile = new File(actualOutputPath);
            
            logger.debug("检查LibreOffice生成的文件: {}", actualOutputPath);
            
            // 检查输出目录中的所有文件（调试）
            File outputDir = new File(targetFile.getParent());
            if (outputDir.exists() && outputDir.isDirectory()) {
                File[] files = outputDir.listFiles();
                if (files != null && files.length > 0) {
                    logger.info("输出目录中的文件: {}", java.util.Arrays.toString(files));
                } else {
                    logger.warn("输出目录为空: {}", outputDir.getAbsolutePath());
                }
            }
            
            // 检查LibreOffice生成的文件是否存在
            if (!actualOutputFile.exists()) {
                logger.error("LibreOffice生成的{}文件不存在: {}", documentType, actualOutputPath);
                return false;
            }
            
            // 如果生成的文件名已经是期望的文件名，则不需要重命名
            if (actualOutputFile.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                logger.debug("{}文件名已经正确，不需要重命名: {}", documentType, targetFilePath);
                return true;
            }
            
            // 重命名文件
            boolean renamed = actualOutputFile.renameTo(targetFile);
            if (renamed) {
                logger.info("{}文件重命名成功: {} -> {}", documentType, actualOutputPath, targetFilePath);
                return true;
            } else {
                // 如果重命名失败，尝试复制文件
                logger.warn("{}文件重命名失败，尝试复制文件: {} -> {}", documentType, actualOutputPath, targetFilePath);
                return copyFile(actualOutputFile, targetFile, documentType);
            }
            
        } catch (Exception e) {
            logger.error("处理{}文件重命名失败", documentType, e);
            return false;
        }
    }
    
    /**
     * 复制文件
     */
    private boolean copyFile(File source, File target, String documentType) {
        try {
            java.nio.file.Files.copy(source.toPath(), target.toPath(), 
                                   java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // 复制成功后删除原文件
            source.delete();
            logger.info("{}文件复制成功: {} -> {}", documentType, source.getAbsolutePath(), target.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("{}文件复制失败: {} -> {}", documentType, source.getAbsolutePath(), target.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 清理临时实例目录
     */
    private void cleanupTempInstanceDir(File tempInstanceDir) {
        if (tempInstanceDir == null || !tempInstanceDir.exists()) {
            return;
        }
        
        try {
            logger.debug("开始清理临时实例目录: {}", tempInstanceDir.getAbsolutePath());
            deleteDirectory(tempInstanceDir);
            logger.debug("✅ 清理临时实例目录成功: {}", tempInstanceDir.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("⚠️ 清理临时实例目录失败: {} - {}", tempInstanceDir.getAbsolutePath(), e.getMessage());
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }
        
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        
        if (!directory.delete()) {
            logger.debug("无法删除文件或目录: {}", directory.getAbsolutePath());
        }
    }
    
    /**
     * 判断是否为LibreOffice不支持的加密文档
     * 
     * 通过分析OfficeException错误信息，动态识别LibreOffice无法处理的加密文档
     * 优势：基于错误特征判断，不需要维护格式白名单，自动覆盖所有不支持的加密格式
     * 
     * 典型错误特征：
     * 1. "Wrong password" - 密码错误或加密方式不支持
     * 2. "Cannot decrypt" - 无法解密（旧式加密算法）
     * 3. "Unsupported encryption" - 明确不支持的加密方式
     * 4. "Invalid or corrupt file" + 带密码 - 可能是旧式加密导致识别失败
     * 5. "General input/output error" + 带密码 - 读取加密文件失败
     * 6. "DisposedException" / "CancellationException" + 带密码 - LibreOffice进程崩溃（旧式加密导致）
     * 
     * @param e OfficeException异常
     * @param password 文档密码
     * @param sourceFormat 源文件格式
     * @return 是否为不支持的加密文档
     */
    private boolean isLegacyEncryptionUnsupported(OfficeException e, String password, String sourceFormat) {
        // 仅当提供了密码时才可能是加密文档问题
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        
        String errorMsg = e.getMessage();
        if (errorMsg == null) {
            errorMsg = "";
        }
        
        String lowerMsg = errorMsg.toLowerCase();
        
        // 检查是否为LibreOffice进程崩溃（旧式加密文档常导致崩溃）
        Throwable cause = e.getCause();
        boolean isProcessCrash = 
            lowerMsg.contains("disposedexception") ||
            lowerMsg.contains("cancellationexception") ||
            lowerMsg.contains("task was cancelled") ||
            (cause != null && (
                cause.getClass().getSimpleName().contains("Cancellation") ||
                cause.getClass().getSimpleName().contains("Disposed")
            ));
        
        if (isProcessCrash) {
            logger.debug("检测到LibreOffice进程崩溃（可能因旧式加密） - 格式: {}, 错误: {}", sourceFormat, errorMsg);
            return true;
        }
        
        // 检查错误信息中的加密特征关键词
        boolean hasEncryptionError = 
            lowerMsg.contains("wrong password") ||
            lowerMsg.contains("decrypt") ||
            lowerMsg.contains("encryption") ||
            lowerMsg.contains("encrypted") ||
            (lowerMsg.contains("corrupt") && lowerMsg.contains("file")) ||
            lowerMsg.contains("input/output error") ||
            lowerMsg.contains("i/o error");
        
        if (hasEncryptionError) {
            // 🔑 只要出现加密相关错误，就认为LibreOffice不支持该加密文档
            // 不再限制格式白名单，避免遗漏其他旧式加密格式
            logger.debug("检测到LibreOffice不支持的加密文档 - 格式: {}, 错误: {}", sourceFormat, errorMsg);
            return true;
        }
        
        return false;
    }

    /**
     * 检查文件格式是否在加密黑名单中
     *
     * @param format 文件格式
     * @return 是否在黑名单中
     */
    private boolean isInEncryptedBlacklist(String format) {
        if (format == null || encryptedBlacklist == null) {
            return false;
        }
        String lowerFormat = format.toLowerCase().trim();
        String[] blacklistArray = encryptedBlacklist.split(",");
        for (String item : blacklistArray) {
            if (item.trim().equalsIgnoreCase(lowerFormat)) {
                return true;
            }
        }
        return false;
    }

}