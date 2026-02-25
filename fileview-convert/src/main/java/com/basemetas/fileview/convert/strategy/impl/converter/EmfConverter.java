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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * EMF（Enhanced Metafile）格式转换工具类
 * 提供多种EMF转换方法，解决ImageMagick在处理EMF格式时的各种问题
 */
public class EmfConverter {
    private static final Logger logger = LoggerFactory.getLogger(EmfConverter.class);
     /**
     * 使用多种策略转换EMF文件
     */
    public static boolean convertEmfFile(String sourcePath, String targetPath, String targetFormat) {
        logger.info("开始多策略EMF转换: {} -> {} (格式: {})", sourcePath, targetPath, targetFormat);
        
        // 策略1：使用LibreOffice（首选策略）
        if (convertWithLibreOffice(sourcePath, targetPath, targetFormat)) {
            return true;
        }
        // 策略2：使用ImageMagick的密度设置
        if (convertWithDensitySettings(sourcePath, targetPath, targetFormat)) {
            return true;
        }       
        // 策略3：使用ImageMagick的简化命令
        if (convertWithSimpleCommand(sourcePath, targetPath, targetFormat)) {
            return true;
        }
        // 策略4：使用inkscape（如果可用）
        if (convertWithInkscape(sourcePath, targetPath, targetFormat)) {
            return true;
        }  
        logger.error("所有EMF转换策略都失败了");
        return false;
    }
    
    /**
     * 策略2：使用ImageMagick的密度设置转换EMF
     */
    private static boolean convertWithDensitySettings(String sourcePath, String targetPath, String targetFormat) {
        try {
            logger.info("尝试策略2：使用ImageMagick最简转换EMF（完全不使用图像操作）");
            
            String magickPath = findMagickPath();
            if (magickPath == null) {
                logger.warn("未找到ImageMagick，跳过策略2");
                return false;
            }
            
            List<String> command = new ArrayList<>();
            command.add(magickPath);
            
            // 完全最简化的转换，不使用任何可能引起问题的参数
            command.add("-define");
            command.add("delegate:decode=");  // 禁用delegate
            command.add(sourcePath);
            command.add(targetPath);
            
            return executeCommand(command, "策略2（最简转换）");
            
        } catch (Exception e) {
            logger.warn("策略2转换失败", e);
            return false;
        }
    }
    
    /**
     * 策略3：使用ImageMagick的简化命令转换EMF
     */
    private static boolean convertWithSimpleCommand(String sourcePath, String targetPath, String targetFormat) {
        try {
            logger.info("尝试策略3：使用ImageMagick纯粹简化命令转换EMF（不使用任何参数）");
            
            String magickPath = findMagickPath();
            if (magickPath == null) {
                logger.warn("未找到ImageMagick，跳过策略3");
                return false;
            }
            
            List<String> command = new ArrayList<>();
            command.add(magickPath);
            command.add(sourcePath);
            command.add(targetPath);
            
            return executeCommand(command, "策略3（纯简化命令）");
            
        } catch (Exception e) {
            logger.warn("策略3转换失败", e);
            return false;
        }
    }
    
    /**
     * 策略4：使用Inkscape转换EMF（主要用于SVG转换）
     */
    private static boolean convertWithInkscape(String sourcePath, String targetPath, String targetFormat) {
        try {
            logger.info("尝试策略4：使用Inkscape转换EMF");
            
            String inkscapePath = findInkscapePath();
            if (inkscapePath == null) {
                logger.warn("未找到Inkscape，跳过策略4");
                return false;
            }
            
            List<String> command = new ArrayList<>();
            command.add(inkscapePath);
            
            // Inkscape参数设置
            switch (targetFormat.toLowerCase()) {
                case "png":
                    command.add("--export-type=png");
                    command.add("--export-dpi=300");
                    command.add("--export-background=white");
                    command.add("--export-filename=" + targetPath);
                    command.add(sourcePath);
                    break;
                    
                case "svg":
                    command.add("--export-type=svg");
                    command.add("--export-filename=" + targetPath);
                    command.add(sourcePath);
                    break;
                    
                case "jpg":
                case "jpeg":
                    // Inkscape无法直接输出JPEG，先转为PNG再转换
                    logger.warn("Inkscape无法直接转换为JPEG格式");
                    return false;
                    
                default:
                    logger.warn("Inkscape不支持目标格式: {}", targetFormat);
                    return false;
            }
            
            return executeCommand(command, "策略4（Inkscape）");
            
        } catch (Exception e) {
            logger.warn("策略4转换失败", e);
            return false;
        }
    }
    
    /**
     * 策略1：使用LibreOffice转换EMF
     */
    private static boolean convertWithLibreOffice(String sourcePath, String targetPath, String targetFormat) {
        try {
            logger.info("尝试策略1：使用LibreOffice转换EMF");
            
            String libreofficePath = findLibreOfficePath();
            if (libreofficePath == null) {
                logger.warn("未找到LibreOffice，跳过策略1");
                return false;
            }
            
            // LibreOffice支持的格式有限
            if (!targetFormat.toLowerCase().matches("png|jpg|jpeg|svg|pdf")) {
                logger.warn("LibreOffice不支持目标格式: {}", targetFormat);
                return false;
            }
            
            // 确保目标目录存在（防御性检查）
            File targetFile = new File(targetPath);
            File targetDir = targetFile.getParentFile();
            
            // 如果无法获取父目录，使用当前目录
            if (targetDir == null) {
                logger.warn("无法获取目标文件的父目录，使用当前目录: {}", targetPath);
                targetDir = new File(".");
            }
            
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            
            List<String> command = new ArrayList<>();
            command.add(libreofficePath);
            command.add("--headless");
            command.add("--convert-to");
            command.add(targetFormat.toLowerCase());
            command.add("--outdir");
            command.add(targetDir.getAbsolutePath());
            command.add(sourcePath);
            
            boolean success = executeCommand(command, "策略1（LibreOffice）");
            
            // LibreOffice可能生成的文件名与期望的不同，需要重命名
            if (success) {
                String expectedFileName = new File(sourcePath).getName().replaceAll("\\.[^.]+$", "." + targetFormat);
                File generatedFile = new File(targetDir, expectedFileName);
                if (generatedFile.exists() && !generatedFile.getAbsolutePath().equals(targetPath)) {
                    if (generatedFile.renameTo(targetFile)) {
                        logger.info("LibreOffice转换成功，文件已重命名: {} -> {}", generatedFile.getName(), targetFile.getName());
                        return true;
                    } else {
                        logger.warn("文件重命名失败: {} -> {}", generatedFile.getAbsolutePath(), targetPath);
                    }
                } else if (generatedFile.exists() && generatedFile.getAbsolutePath().equals(targetPath)) {
                    logger.info("LibreOffice转换成功，文件名匹配，无需重命名");
                    return true;
                }
            }
            
            return success;
            
        } catch (Exception e) {
            logger.warn("策略1转换失败", e);
            return false;
        }
    }
    
    /**
     * 查找ImageMagick magick命令路径（仅Linux环境）
     */
    private static String findMagickPath() {
        String[] possiblePaths = {
            "/usr/local/bin/magick",
            "/usr/bin/magick",
            "/opt/imagemagick/bin/magick",
            "magick"
        };
        
        for (String path : possiblePaths) {
            if (isCommandAvailable(path)) {
                logger.debug("找到ImageMagick: {}", path);
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * 查找Inkscape命令路径（仅Linux环境）
     */
    private static String findInkscapePath() {
        String[] possiblePaths = {
            "/usr/bin/inkscape",
            "/usr/local/bin/inkscape",
            "inkscape"
        };
        
        for (String path : possiblePaths) {
            if (isCommandAvailable(path)) {
                logger.debug("找到Inkscape: {}", path);
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * 查找LibreOffice命令路径（仅Linux环境）
     */
    private static String findLibreOfficePath() {
        String[] possiblePaths = {
            "/usr/bin/libreoffice",
            "/usr/local/bin/libreoffice",
            "/opt/libreoffice/program/soffice",
            "libreoffice",
            "soffice"
        };
        
        for (String path : possiblePaths) {
            if (isCommandAvailable(path)) {
                logger.debug("找到LibreOffice: {}", path);
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * 检查命令是否可用
     */
    private static boolean isCommandAvailable(String command) {
        try {
            if (command.contains("/") || command.contains("\\")) {
                // 绝对路径检查
                return new File(command).exists() && new File(command).canExecute();
            } else {
                // 命令在PATH中检查
                ProcessBuilder pb = new ProcessBuilder(command, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished) {
                    return process.exitValue() == 0;
                } else {
                    process.destroyForcibly();
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 执行命令并等待结果
     */
    private static boolean executeCommand(List<String> command, String strategyName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            
            // 设置环境变量
            Map<String, String> env = pb.environment();
            env.put("LANG", "en_US.UTF-8");
            env.put("LC_ALL", "en_US.UTF-8");
            
            pb.redirectErrorStream(true);
            
            logger.info("执行{}命令: {}", strategyName, String.join(" ", command));
            
            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS); // 30秒超时
            
            if (!finished) {
                logger.warn("{}命令执行超时，强制终止", strategyName);
                process.destroyForcibly();
                return false;
            }
            
            int exitCode = process.exitValue();
            
            if (exitCode == 0) {
                // 检查目标文件是否生成
                String targetPath = command.get(command.size() - 1);
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("{}转换成功: {} bytes", strategyName, targetFile.length());
                    return true;
                } else {
                    logger.warn("{}转换后目标文件不存在或为空", strategyName);
                }
            } else {
                logger.warn("{}命令失败，退出码: {}", strategyName, exitCode);
                if (output.length() > 0) {
                    logger.warn("命令输出: {}", output.toString().trim());
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("{}命令执行异常", strategyName, e);
            return false;
        }
    }
    
    /**
     * 获取可用的EMF转换工具信息
     */
    public static String getAvailableTools() {
        StringBuilder info = new StringBuilder();
        info.append("可用的EMF转换工具:\n");
        
        String magick = findMagickPath();
        if (magick != null) {
            info.append("✓ ImageMagick: ").append(magick).append("\n");
        } else {
            info.append("✗ ImageMagick: 未找到\n");
        }
        
        String inkscape = findInkscapePath();
        if (inkscape != null) {
            info.append("✓ Inkscape: ").append(inkscape).append("\n");
        } else {
            info.append("✗ Inkscape: 未找到\n");
        }
        
        String libreoffice = findLibreOfficePath();
        if (libreoffice != null) {
            info.append("✓ LibreOffice: ").append(libreoffice).append("\n");
        } else {
            info.append("✗ LibreOffice: 未找到\n");
        }
        
        return info.toString();
    }
}