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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CAD2X 外部转换器适配器
 * 通过进程调用 cad2x 二进制文件进行 CAD 文件转换
 * 支持 Linux (x64/arm64)
 * 
 * @author 夫子
 */
@Component
public class Cad2xConverter {
    private static final Logger logger = LoggerFactory.getLogger(Cad2xConverter.class);

    // 架构检测（Linux环境）
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
    private static final boolean IS_X64 = OS_ARCH.contains("amd64") || OS_ARCH.contains("x86_64");
    private static final boolean IS_ARM64 = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64");

    @Value("${cad.convert.cad2x.enable:true}")
    private boolean cad2xEnabled;

    @Value("${cad2x.converter.path:}")
    private String cad2xConverterPath;

    @Value("${cad2x.temp.dir:}")
    private String tempDir;

    @Value("${cad2x.conversion.timeout:120}")
    private int conversionTimeoutSeconds;

    // 可选渲染参数
    @Value("${cad2x.options.center:true}")
    private boolean optionCenter;

    @Value("${cad2x.options.encoding:}")
    private String optionEncoding;

    @Value("${cad2x.options.font:}")
    private String optionFont;

    @Value("${cad2x.options.fontDirs:}")
    private String optionFontDirs;

    @Value("${cad2x.options.page:2970x2100}")
    private String optionPage;

    @Value("${cad2x.options.margins:2.0,2.0,2.0,2.0}")
    private String optionMargins;

    /**
     * 检查服务可用性
     */
    public boolean isServiceAvailable() {
        try {
            if (!cad2xEnabled) {
                logger.warn("CAD2X 转换器已禁用");
                return false;
            }

            String executable = getExecutablePath();
            if (executable == null) {
                logger.error("当前架构不支持 CAD2X: ARCH={}", OS_ARCH);
                return false;
            }

            File exeFile = new File(executable);
            if (!exeFile.exists()) {
                logger.error("CAD2X 可执行文件不存在: {}", exeFile.getAbsolutePath());
                return false;
            }

            if (!exeFile.canExecute()) {
                logger.error("CAD2X 可执行文件无执行权限: {}", exeFile.getAbsolutePath());
                return false;
            }

            String tmp = getTempDirectory();
            File tmpDirFile = new File(tmp);
            if (!tmpDirFile.exists()) {
                Files.createDirectories(Paths.get(tmp));
            }

            if (!tmpDirFile.canWrite()) {
                logger.error("CAD2X 临时目录不可写: {}", tmp);
                return false;
            }

            logger.debug("CAD2X 服务可用 - 可执行文件: {}", executable);
            return true;

        } catch (Exception e) {
            logger.error("CAD2X 可用性检查异常", e);
            return false;
        }
    }

    /**
     * 执行 CAD 文件转换
     * 
     * @param sourceFilePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param sourceFormat 源文件格式
     * @param targetFormat 目标格式
     * @return 转换是否成功
     */
    public boolean convert(String sourceFilePath, String targetFilePath, String sourceFormat, String targetFormat) {
        logger.info("开始 CAD2X 转换 - 源文件: {}, 目标格式: {}", sourceFilePath, targetFormat);

        try {
            if (!isServiceAvailable()) {
                return false;
            }

            String executable = getExecutablePath();
            List<String> command = buildCommand(executable, sourceFilePath, targetFilePath, targetFormat);

            logger.info("执行 CAD2X 命令: {}", String.join(" ", command));

            boolean success = executeCommand(command, targetFilePath, targetFormat, sourceFilePath);

            if (success) {
                logger.info("CAD2X 转换成功 - 目标文件: {}", targetFilePath);
            } else {
                logger.error("CAD2X 转换失败 - 源文件: {}", sourceFilePath);
            }

            return success;

        } catch (Exception e) {
            logger.error("CAD2X 转换异常 - 源文件: {}", sourceFilePath, e);
            return false;
        }
    }

    /**
     * 构建命令行参数
     */
    private List<String> buildCommand(String executable, String sourceFilePath, String targetFilePath, String targetFormat) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);

        // 通用选项
        if (optionCenter) {
            cmd.add("-ac");
        }

        if (notBlank(optionEncoding)) {
            cmd.add("-e");
            cmd.add(optionEncoding);
        }

        if (notBlank(optionFont)) {
            cmd.add("-f");
            cmd.add(optionFont);
        }

        if (notBlank(optionFontDirs)) {
            cmd.add("-l");
            cmd.add(optionFontDirs);
        }

        if (notBlank(optionPage)) {
            cmd.add("-p");
            cmd.add(optionPage);
        }

        if (notBlank(optionMargins)) {
            cmd.add("-m");
            cmd.add(optionMargins);
        }

        // 输出格式和文件
        if ("pdf".equalsIgnoreCase(targetFormat)) {
            // PDF 格式：使用 -o pdf + -t 输出目录
            String outDir = new File(targetFilePath).getParent();
            if (outDir == null || outDir.isEmpty()) {
                outDir = getTempDirectory();
            }
            cmd.add("-o");
            cmd.add("pdf");
            cmd.add(sourceFilePath);
            cmd.add("-t");
            cmd.add(outDir);
        } else {
            // 其他格式：源文件 + -o 输出文件
            // 注意：源文件路径需要在 -o 参数之前
            cmd.add(sourceFilePath);
            cmd.add("-o");
            cmd.add(targetFilePath);
        }

        return cmd;
    }

    /**
     * 执行命令
     */
    private boolean executeCommand(List<String> command, String targetFilePath, String targetFormat, String sourceFilePath) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(new File(getTempDirectory()));

            // 设置环境变量，避免编码问题（Linux环境）
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("LC_ALL", "en_US.UTF-8");

            process = pb.start();

            // 读取输出（用于调试）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(conversionTimeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                logger.error("CAD2X 转换超时: {}秒", conversionTimeoutSeconds);
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();

            if (exitCode == 0) {
                // 输出调试信息
                if (output.length() > 0) {
                    logger.info("CAD2X 输出: {}", output.toString().trim());
                }
                // 验证输出文件
                if ("pdf".equalsIgnoreCase(targetFormat)) {
                    // PDF 需要处理文件名
                    return handlePdfOutput(sourceFilePath, targetFilePath);
                } else {
                    // 其他格式直接验证
                    File outFile = new File(targetFilePath);
                    if (outFile.exists() && outFile.length() > 0) {
                        logger.info("CAD2X 转换成功，输出文件: {}, 大小: {} bytes", targetFilePath, outFile.length());
                        return true;
                    } else {
                        // 尝试从源文件目录查找输出文件（cad2x 可能忽略 -o 参数的路径）
                        File sourceFile = new File(sourceFilePath);
                        String baseName = getBaseName(sourceFilePath);
                        File sourceDir = sourceFile.getParentFile();
                        File generatedFile = new File(sourceDir, baseName + "." + targetFormat.toLowerCase());
                        
                        if (generatedFile.exists() && generatedFile.length() > 0) {
                            logger.info("CAD2X 在源文件目录生成了输出: {}", generatedFile.getAbsolutePath());
                            // 移动到目标目录
                            try {
                                if (outFile.exists()) {
                                    outFile.delete();
                                }
                                // 确保目标目录存在
                                File targetDir = outFile.getParentFile();
                                if (targetDir != null && !targetDir.exists()) {
                                    targetDir.mkdirs();
                                }
                                
                                boolean moved = generatedFile.renameTo(outFile);
                                if (moved) {
                                    logger.info("成功移动文件: {} -> {}", generatedFile.getAbsolutePath(), outFile.getAbsolutePath());
                                    return true;
                                } else {
                                    logger.error("移动文件失败: {} -> {}", generatedFile.getAbsolutePath(), outFile.getAbsolutePath());
                                    return false;
                                }
                            } catch (Exception e) {
                                logger.error("移动文件异常", e);
                                return false;
                            }
                        } else {
                            logger.error("输出文件不存在或为空: {}", targetFilePath);
                            // 尝试列出目标目录内容
                            File targetDir = outFile.getParentFile();
                            if (targetDir != null && targetDir.exists()) {
                                logger.error("目标目录内容: {}", Arrays.toString(targetDir.list()));
                            }
                            // 列出源文件目录内容
                            if (sourceDir != null && sourceDir.exists()) {
                                logger.error("源文件目录内容: {}", Arrays.toString(sourceDir.list()));
                            }
                            return false;
                        }
                    }
                }
            } else {
                logger.error("CAD2X 进程退出码: {}, 输出: {}", exitCode, output.toString().trim());
                return false;
            }

        } catch (Exception e) {
            logger.error("CAD2X 进程执行异常", e);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return false;
        }
    }

    /**
     * 处理 PDF 输出（cad2x 生成的 PDF 文件名基于源文件）
     */
    private boolean handlePdfOutput(String sourceFilePath, String targetFilePath) {
        String outDir = new File(targetFilePath).getParent();
        if (outDir == null || outDir.isEmpty()) {
            outDir = getTempDirectory();
        }

        String baseName = getBaseName(sourceFilePath);
        File expectedFile = new File(outDir, baseName + ".pdf");

        if (expectedFile.exists() && expectedFile.length() > 0) {
            File targetFile = new File(targetFilePath);

            // 如果生成的文件和目标文件路径不同，需要移动
            if (!expectedFile.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                try {
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    boolean renamed = expectedFile.renameTo(targetFile);
                    if (!renamed) {
                        logger.error("移动 PDF 文件失败: {} -> {}", expectedFile.getAbsolutePath(), targetFile.getAbsolutePath());
                        return false;
                    }
                } catch (Exception e) {
                    logger.error("移动 PDF 文件异常", e);
                    return false;
                }
            }
            return true;
        } else {
            logger.error("未找到 PDF 输出文件: {}", expectedFile.getAbsolutePath());
            return false;
        }
    }

    /**
     * 获取可执行文件路径（根据架构自动选择，Linux环境）
     */
    private String getExecutablePath() {
        // 如果配置了路径，直接使用
        if (cad2xConverterPath != null && !cad2xConverterPath.trim().isEmpty()) {
            return cad2xConverterPath.trim();
        }

        // 自动检测并返回对应架构的二进制文件（Linux环境）
        String basePath = System.getProperty("user.dir");
        String binPath = null;

        if (IS_ARM64) {
            binPath = basePath + "/cad2x/linux_arm64/cad2x";
        } else if (IS_X64) {
            binPath = basePath + "/cad2x/linux_x64/cad2x";
        }

        return binPath;
    }

    /**
     * 获取临时目录
     */
    private String getTempDirectory() {
        String tmp = tempDir;
        if (tmp == null || tmp.trim().isEmpty()) {
            tmp = System.getProperty("java.io.tmpdir");
        }
        return tmp.trim();
    }

    /**
     * 获取文件基本名（无扩展名）
     */
    private static String getBaseName(String path) {
        String name = new File(path).getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    /**
     * 检查字符串是否非空
     */
    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
