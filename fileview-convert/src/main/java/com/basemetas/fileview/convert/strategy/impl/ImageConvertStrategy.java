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

import com.basemetas.fileview.convert.annotation.ConvertStrategy;
import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import com.basemetas.fileview.convert.strategy.impl.converter.EmfConverter;
import com.basemetas.fileview.convert.utils.EnvironmentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 图片格式转换策略实现类
 * 
 * 支持多种图片格式转换：
 * - 源格式：bmp, gif, ico, jfif, jpeg, jpg, png, svg, tiff, webp, heic, raw等
 * - 目标格式：bmp, gif, ico, jfif, jpeg, jpg, png, svg, tiff, webp等
 * 
 * 特性：
 * - 基于ImageMagick的高性能转换
 * - 支持批量转换和压缩优化
 * - 智能文件路径解析（解决中文编码问题）
 * - 完善的错误处理和日志记录
 * - 图片质量控制和尺寸调整
 * 
 * @author 夫子
 */
@ConvertStrategy(category = FileCategory.IMAGE, description = "图片格式转换（ImageMagick）", priority = 100)
public class ImageConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ImageConvertStrategy.class);

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @PostConstruct
    public void init() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.IMAGE);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.IMAGE);

        logger.info("图片转换策略初始化完成 - 类型: IMAGE, 支持源格式({}种): {}",
                sourceFormats.size(), sourceFormats);
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);
    }

    @Override
    public boolean convert(String filePath, String targetPath) {
        return false;
    }

    @Override
    public boolean convert(String filePath, String targetPath, String targetFileName, String targetFormat) {
        logger.info("开始图片转换 - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}",
                filePath, targetPath, targetFileName, targetFormat);

        try {
            // 构建目标文件路径
            String targetFilePath = new File(targetPath, targetFileName).getAbsolutePath();
            
            // 执行转换
            return convertWithImageMagick(filePath, targetFilePath, targetFormat);

        } catch (Exception e) {
            logger.error("图片转换异常 - 源文件: {}, 目标路径: {}", filePath, targetPath, e);
            return false;
        }
    }

    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.IMAGE, sourceFormat, targetFormat);
    }

    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.IMAGE);
    }

    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.IMAGE);
    }

    /**
     * 获取支持的图片格式列表
     */
    public Set<String> getSupportedFormats() {
        // 图片格式的源格式和目标格式是一样的，返回源格式即可
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.IMAGE);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || !filePath.contains(".")) {
            return "";
        }
        return filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 使用ImageMagick进行图片转换
     */
    private boolean convertWithImageMagick(String filePath, String targetFilePath, String targetFormat) {
        try {
            logger.info("Starting ImageMagick conversion: {} -> {}", filePath, targetFilePath);

            // 验证源文件存在
            File sourceFile = new File(filePath);
            if (!sourceFile.exists()) {
                logger.error("Source file does not exist: {}", filePath);
                return false;
            }

            // 确保目标目录存在
            File targetFile = new File(targetFilePath);
            File targetDir = targetFile.getParentFile();
            if (targetDir != null && !targetDir.exists()) {
                logger.info("Creating target directory: {}", targetDir.getAbsolutePath());
                if (!targetDir.mkdirs()) {
                    logger.error("Failed to create target directory: {}", targetDir.getAbsolutePath());
                    return false;
                }
            }

            // 直接使用magick命令进行转换
            return convertWithMagickCommand(filePath, targetFilePath, targetFormat);

        } catch (Exception e) {
            logger.error("Image conversion failed from {} to {}", filePath, targetFilePath, e);
            return false;
        }
    }

    /**
     * 使用magick命令进行图片转换（仅Linux环境）
     */
    private boolean convertWithMagickCommand(String sourcePath, String targetPath, String targetFormat) {
        try {
            logger.info("Using magick command for conversion: {} -> {}", sourcePath, targetPath);

            // 构建Linux magick命令
            ProcessBuilder pb = buildLinuxMagickCommand(sourcePath, targetPath, targetFormat);

            // 设置环境变量以支持UTF-8编码
            Map<String, String> env = pb.environment();
            env.put("LANG", "en_US.UTF-8");
            env.put("LC_ALL", "en_US.UTF-8");

            pb.redirectErrorStream(true);

            logger.info("Executing command: {}", String.join(" ", pb.command()));

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

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // 验证输出文件
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("Image conversion successful: {} bytes", targetFile.length());
                    return true;
                } else {
                    logger.error("Output file not created or empty: {}", targetPath);
                    return false;
                }
            } else {
                logger.error("Magick command failed with exit code: {}", exitCode);
                logger.error("Command output: {}", output.toString());

                // 如果是EMF格式并且出现任何错误，都尝试备用方法
                String sourceFormat = getFileExtension(sourcePath);
                if ("emf".equalsIgnoreCase(sourceFormat) || "wmf".equalsIgnoreCase(sourceFormat)) {
                    logger.warn("检测到EMF/WMF格式转换失败，尝试备用转换方法");
                    return convertEmfWithAlternativeMethod(sourcePath, targetPath, targetFormat);
                }

                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to execute magick command", e);
            return false;
        }
    }

    /**
     * 构廽Linux环境下的magick命令（支持ImageMagick 7.x）
     */
    private ProcessBuilder buildLinuxMagickCommand(String sourcePath, String targetPath, String targetFormat) {
        // Linux常见的ImageMagick 7.x安装路径
        String[] possibleMagickPaths = {
                "/usr/bin/magick", // 标准系统安装路径
                "/usr/local/bin/magick", // 本地编译安装路径
                "/opt/imagemagick/bin/magick", // 可选软件目录
                "magick" // 系统PATH中的命令
        };

        String magickPath = "magick"; // 默认使用PATH中的命令

        // 查找可用的magick命令路径
        for (String path : possibleMagickPaths) {
            if (new File(path).exists() && new File(path).canExecute()) {
                magickPath = path;
                logger.info("Found ImageMagick 7.x at: {}", path);
                break;
            }
        }

        // 检查是否为EMF格式，需要特殊处理
        String sourceFormat = getFileExtension(sourcePath);
        if ("emf".equalsIgnoreCase(sourceFormat) || "wmf".equalsIgnoreCase(sourceFormat)) {
            ProcessBuilder emfPb = buildEmfSpecificCommand(magickPath, sourcePath, targetPath, targetFormat);
            if (emfPb == null) {
                // EmfConverter已成功处理，返回一个空的ProcessBuilder避免后续执行
                return new ProcessBuilder("echo", "EmfConverter handled");
            }
            return emfPb;
        }

        // 根据目标格式添加特定的转换参数
        ProcessBuilder pb;
        switch (targetFormat.toLowerCase()) {
            case "jpg":
            case "jpeg":
                // JPEG格式：设置质量参数
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-quality", "90", targetPath);
                logger.info("Using JPEG conversion with quality 90");
                break;
            case "png":
                // PNG格式：优化压缩
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-compress", "zip", targetPath);
                logger.info("Using PNG conversion with zip compression");
                break;
            case "webp":
                // WebP格式：设置质量和压缩方式
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-quality", "85", "-define",
                        "webp:lossless=false", targetPath);
                logger.info("Using WebP conversion with quality 85");
                break;
            case "tiff":
            case "tif":
                // TIFF格式：使用LZW压缩
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-compress", "lzw", targetPath);
                logger.info("Using TIFF conversion with LZW compression");
                break;
            case "bmp":
                // BMP格式：无压缩
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-compress", "none", targetPath);
                logger.info("Using BMP conversion without compression");
                break;
            case "gif":
                // GIF格式：针对动画和静态图片优化
                pb = new ProcessBuilder(magickPath, sourcePath, "-flatten", "-coalesce", "-colors", "256", targetPath);
                logger.info("Using GIF conversion with 256 colors");
                break;
            default:
                // 默认转换，不添加特殊参数
                pb = new ProcessBuilder(magickPath, sourcePath, targetPath);
                logger.info("Using default conversion for format: {}", targetFormat);
        }

        logger.info("Using Linux magick command: {}", magickPath);
        return pb;
    }

    /**
     * 为EMF/WMF格式构建特殊的转换命令
     * 优先使用EmfConverter（含LibreOffice策略），失败后再使用ImageMagick
     */
    private ProcessBuilder buildEmfSpecificCommand(String magickPath, String sourcePath, String targetPath,
            String targetFormat) {
        logger.warn("检测到EMF/WMF格式");
        
        // 优先使用EmfConverter（包含LibreOffice等多种策略）
        if (EmfConverter.convertEmfFile(sourcePath, targetPath, targetFormat)) {
            logger.info("使用EmfConverter转换成功");
            // 返回null表示已处理，不需要继续执行ImageMagick命令
            return null;
        }
        
        logger.info("EmfConverter转换失败，回退到ImageMagick策略: {} -> {}", sourcePath, targetPath);

        ProcessBuilder pb;

        // EMF格式使用最基础的转换策略，完全避免图像操作参数
        switch (targetFormat.toLowerCase()) {
            case "png":
                // EMF转PNG：只使用最基本的参数，不做任何图像操作
                pb = new ProcessBuilder(magickPath,
                        "-define", "delegate:decode=", // 禁用delegate
                        sourcePath,
                        targetPath);
                logger.info("Using minimal EMF to PNG conversion (no image operations)");
                break;

            case "jpg":
            case "jpeg":
                // EMF转JPEG：只使用最基本的参数
                pb = new ProcessBuilder(magickPath,
                        "-define", "delegate:decode=",
                        sourcePath,
                        targetPath);
                logger.info("Using minimal EMF to JPEG conversion (no image operations)");
                break;

            case "svg":
                // EMF转SVG：使用最基本的转换
                pb = new ProcessBuilder(magickPath,
                        "-define", "delegate:decode=",
                        sourcePath,
                        targetPath);
                logger.info("Using minimal EMF to SVG conversion (no image operations)");
                break;

            default:
                // 其他格式使用最基本的转换
                pb = new ProcessBuilder(magickPath,
                        "-define", "delegate:decode=",
                        sourcePath,
                        targetPath);
                logger.info("Using minimal EMF to {} conversion (no image operations)", targetFormat);
        }

        return pb;
    }

    /**
     * 备用的EMF转换方法，当ImageMagick的delegate失败时使用
     */
    private boolean convertEmfWithAlternativeMethod(String sourcePath, String targetPath, String targetFormat) {
        logger.info("尝试使用备用方法转换EMF文件: {} -> {}", sourcePath, targetPath);

        try {
            // 优先使用专用的EMF转换工具类
            if (EmfConverter.convertEmfFile(sourcePath, targetPath, targetFormat)) {
                logger.info("使用EmfConversionUtil转换成功");
                return true;
            }

            // 如果专用工具失败，尝试原有的简化转换方法
            if (convertWithSimplifiedMagick(sourcePath, targetPath)) {
                return true;
            }

            // 如果是Linux系统，尝试自动安装LibreOffice
            if (EnvironmentUtils.isLinuxSystem() && attemptLibreOfficeInstallation()) {
                logger.info("尝试安装LibreOffice后再次转换...");
                if (convertWithSimpleMagickCommand(sourcePath, targetPath)) {
                    return true;
                }
            }

            // 输出可用工具信息，帮助运维人员诊断
            logger.warn("所有EMF转换方法都失败，工具可用性检查:");
            logger.warn(EmfConverter.getAvailableTools());

            // 提供详细的解决方案建议
            logger.warn("EMF转换失败解决方案:");
            logger.warn("1. 安装ImageMagick 7.x: 确保安装了最新版本的ImageMagick");
            logger.warn("2. 安装LibreOffice: sudo apt-get install libreoffice");
            logger.warn("3. 安装Inkscape: sudo apt-get install inkscape");
            logger.warn("4. 配置ImageMagick的delegates.xml文件以支持EMF格式");
            logger.warn("5. 检查文件权限：确保转换工具有读取源文件和写入目标目录的权限");
            logger.warn("6. 验证EMF文件格式：确保源文件是有效的EMF格式");
            logger.warn("7. 考虑将EMF文件预先转换为PNG或SVG格式");

            return false;

        } catch (Exception e) {
            logger.error("备用EMF转换方法失败", e);
            return false;
        }
    }

    /**
     * 使用简化的magick命令转换EMF，避免delegate问题（仅Linux环境）
     */
    private boolean convertWithSimplifiedMagick(String sourcePath, String targetPath) {
        try {
            // Linux环境
            String magickPath = "magick";
            String[] linuxPaths = { "/usr/local/bin/magick", "/usr/bin/magick" };
            for (String path : linuxPaths) {
                if (new File(path).exists()) {
                    magickPath = path;
                    break;
                }
            }

            logger.info("使用增强magick命令转换EMF: {}", magickPath);

            // 获取目标格式
            String targetFormat = getFileExtension(targetPath);

            ProcessBuilder pb;

            // 根据目标格式选择不同的转换策略
            switch (targetFormat.toLowerCase()) {
                case "png":
                    // PNG格式：使用高密度设置
                    pb = new ProcessBuilder(
                            magickPath,
                            "-density", "300",
                            "-background", "white",
                            "-alpha", "remove",
                            sourcePath,
                            "-resize", "1920x1920>", // 限制最大尺寸
                            targetPath);
                    logger.info("使用高密度PNG转换策略");
                    break;

                case "jpg":
                case "jpeg":
                    // JPEG格式：使用高密度和质量设置
                    pb = new ProcessBuilder(
                            magickPath,
                            "-density", "300",
                            "-background", "white",
                            "-alpha", "remove",
                            sourcePath,
                            "-resize", "1920x1920>",
                            "-quality", "90",
                            targetPath);
                    logger.info("使用高密度JPEG转换策略");
                    break;

                case "svg":
                    // SVG格式：保持矢量格式
                    pb = new ProcessBuilder(
                            magickPath,
                            "-density", "150",
                            "-background", "transparent",
                            sourcePath,
                            targetPath);
                    logger.info("使用SVG矢量转换策略");
                    break;

                default:
                    // 默认格式：使用基本转换
                    pb = new ProcessBuilder(
                            magickPath,
                            "-density", "200",
                            "-background", "white",
                            sourcePath,
                            targetPath);
                    logger.info("使用通用转换策略");
            }

            // 设置环境变量
            Map<String, String> env = pb.environment();
            env.put("MAGICK_THREAD_LIMIT", "1"); // 限制线程数
            env.put("MAGICK_MEMORY_LIMIT", "256MB"); // 限制内存使用

            pb.redirectErrorStream(true);

            logger.info("执行增强EMF转换命令: {}", String.join(" ", pb.command()));

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

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("增强EMF转换成功: {} bytes", targetFile.length());
                    return true;
                } else {
                    logger.warn("增强EMF转换后目标文件不存在或为空");
                }
            } else {
                logger.warn("增强EMF转换失败，退出码: {}", exitCode);
                if (output.length() > 0) {
                    logger.warn("命令输出: {}", output.toString());
                }
            }

            // 如果增强转换失败，尝试最基本的转换
            logger.info("尝试最简化的EMF转换方法...");
            return convertWithMinimalMagick(magickPath, sourcePath, targetPath);

        } catch (Exception e) {
            logger.error("增强EMF转换异常", e);
            return false;
        }
    }

    /**
     * 使用最简化的magick命令转换EMF，仅使用最基本的参数
     */
    private boolean convertWithMinimalMagick(String magickPath, String sourcePath, String targetPath) {
        try {
            logger.info("尝试最简化的EMF转换: {} -> {}", sourcePath, targetPath);

            // 构建最基本的转换命令，不使用任何可能引起问题的参数
            ProcessBuilder pb = new ProcessBuilder(
                    magickPath,
                    sourcePath,
                    targetPath);

            // 设置最小化的环境变量
            Map<String, String> env = pb.environment();
            env.put("MAGICK_THREAD_LIMIT", "1");
            env.put("MAGICK_MEMORY_LIMIT", "128MB");

            pb.redirectErrorStream(true);

            logger.info("执行最简化EMF转换命令: {}", String.join(" ", pb.command()));

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

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("最简化EMF转换成功: {} bytes", targetFile.length());
                    return true;
                }
            }

            logger.warn("最简化EMF转换失败，退出码: {}", exitCode);
            if (output.length() > 0) {
                logger.warn("命令输出: {}", output.toString());
            }

            return false;

        } catch (Exception e) {
            logger.error("最简化EMF转换异常", e);
            return false;
        }
    }

    /**
     * 尝试自动安装LibreOffice（仅限Linux系统）
     */
    private boolean attemptLibreOfficeInstallation() {
        if (!EnvironmentUtils.isLinuxSystem()) {
            return false;
        }

        try {
            logger.info("尝试自动安装LibreOffice...");

            // 尝试使用apt-get安装
            ProcessBuilder pb = new ProcessBuilder("sudo", "apt-get", "install", "-y", "libreoffice-headless");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS); // 5分钟超时

            if (finished && process.exitValue() == 0) {
                logger.info("LibreOffice安装成功");
                return true;
            } else {
                logger.warn("LibreOffice自动安装失败或超时");
                return false;
            }

        } catch (Exception e) {
            logger.warn("LibreOffice自动安装失败", e);
            return false;
        }
    }

    /**
     * 使用最简单的magick命令进行转换
     */
    private boolean convertWithSimpleMagickCommand(String sourcePath, String targetPath) {
        try {
            String magickPath = "/usr/local/bin/magick";
            if (!new File(magickPath).exists()) {
                magickPath = "magick";
            }

            ProcessBuilder pb = new ProcessBuilder(magickPath, sourcePath, targetPath);
            pb.redirectErrorStream(true);

            logger.info("执行最简单的magick转换: {} {} {}", magickPath, sourcePath, targetPath);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("简单magick转换成功: {} bytes", targetFile.length());
                    return true;
                }
            }

            logger.warn("简单magick转换失败，退出码: {}", exitCode);
            return false;

        } catch (Exception e) {
            logger.error("简单magick转换异常", e);
            return false;
        }
    }

}
