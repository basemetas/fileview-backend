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
package com.basemetas.fileview.convert.service.checker;

import com.basemetas.fileview.convert.utils.EnvironmentUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * ImageMagick 可用性检查工具类
 * 
 * 提供 ImageMagick 的安装检测和版本验证功能
 * 支持 Linux、Windows、macOS 等多平台
 * 
 * @author 夫子
 */
public class ImageMagickChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageMagickChecker.class);
    
    /**
     * Linux/macOS 常见的 ImageMagick 安装路径
     */
    private static final String[] LINUX_MAGICK_PATHS = {
            "/usr/bin/magick",
            "/usr/local/bin/magick",
            "/opt/imagemagick/bin/magick",
            "magick"
    };
    
    /**
     * Windows 常见的 ImageMagick 安装路径
     */
    private static final String[] WINDOWS_MAGICK_PATHS = {
            "D:\\devTool\\ImageMagick-7.1.2-Q16\\magick.exe",
            "C:\\Program Files\\ImageMagick-7.1.2-Q16\\magick.exe",
            "magick"
    };
    
    /**
     * 检查 ImageMagick 是否可用
     * 
     * @return 是否可用
     */
    public static boolean isAvailable() {
        return EnvironmentUtils.isImageMagickAvailable();
    }
    
    /**
     * 检查 ImageMagick 是否可用
     * 
     * @param verbose 是否输出详细日志
     * @return 是否可用
     */
    public static boolean isAvailable(boolean verbose) {
        String osName = EnvironmentUtils.getOsName();
        
        String[] testPaths;
        if (osName.contains("windows")) {
            testPaths = WINDOWS_MAGICK_PATHS;
        } else {
            testPaths = LINUX_MAGICK_PATHS;
        }
        
        for (String command : testPaths) {
            if (testMagickCommand(command, verbose)) {
                return true;
            }
        }
        
        if (verbose) {
            logger.warn("ImageMagick not found on system: {}", osName);
        }
        
        return false;
    }
    
    /**
     * 测试单个 magick 命令是否可用
     * 
     * @param command magick 命令路径
     * @param verbose 是否输出详细日志
     * @return 是否可用
     */
    private static boolean testMagickCommand(String command, boolean verbose) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取版本信息
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("ImageMagick") && verbose) {
                        logger.info("Found ImageMagick: {}", line.trim());
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (verbose) {
                    logger.info("ImageMagick is available at: {}", command);
                }
                return true;
            }
        } catch (Exception e) {
            if (verbose) {
                logger.debug("Failed to test ImageMagick command: {} - {}", command, e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * 获取 ImageMagick 版本信息
     * 
     * @return 版本信息，如果不可用则返回 null
     */
    public static String getVersion() {
        String osName = EnvironmentUtils.getOsName();
        String[] testPaths = osName.contains("windows") ? WINDOWS_MAGICK_PATHS : LINUX_MAGICK_PATHS;
        
        for (String command : testPaths) {
            try {
                ProcessBuilder pb = new ProcessBuilder(command, "-version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // 读取版本信息
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && line.contains("ImageMagick")) {
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            return line.trim();
                        }
                    }
                }
            } catch (Exception e) {
                // 继续尝试下一个路径
            }
        }
        
        return null;
    }
    
    /**
     * 获取 ImageMagick 可执行文件路径
     * 
     * @return 可执行文件路径，如果不可用则返回 null
     */
    public static String getExecutablePath() {
        String osName = EnvironmentUtils.getOsName();
        String[] testPaths = osName.contains("windows") ? WINDOWS_MAGICK_PATHS : LINUX_MAGICK_PATHS;
        
        for (String command : testPaths) {
            // 如果是绝对路径，检查文件是否存在
            if (command.contains("/") || command.contains("\\")) {
                File file = new File(command);
                if (file.exists() && file.canExecute()) {
                    return command;
                }
            }
            
            // 尝试执行命令验证
            if (testMagickCommand(command, false)) {
                return command;
            }
        }
        
        return null;
    }
    
    /**
     * 提供安装建议（仅用于日志输出）
     * 
     * @return 安装建议文本
     */
    public static String getInstallationSuggestions() {
        String osName = EnvironmentUtils.getOsName();
        
        StringBuilder sb = new StringBuilder();
        sb.append("ImageMagick installation suggestions:\n");
        
        if (osName.contains("linux")) {
            sb.append("  1. Ubuntu/Debian: sudo apt-get update && sudo apt-get install imagemagick\n");
            sb.append("  2. CentOS/RHEL: sudo yum install ImageMagick\n");
            sb.append("  3. Fedora: sudo dnf install ImageMagick\n");
            sb.append("  4. From source: wget https://imagemagick.org/download/ImageMagick.tar.gz\n");
            sb.append("     tar xzf ImageMagick.tar.gz && cd ImageMagick-7.* && ./configure && make && sudo make install\n");
            sb.append("  5. Verify: magick -version\n");
        } else if (osName.contains("windows")) {
            sb.append("  1. Download from: https://imagemagick.org/script/download.php#windows\n");
            sb.append("  2. Install ImageMagick-7.x.x-Q16-x64-dll.exe\n");
            sb.append("  3. Add to PATH or configure absolute path in application.yml\n");
            sb.append("  4. Verify: magick -version\n");
        } else if (osName.contains("mac")) {
            sb.append("  1. Using Homebrew: brew install imagemagick\n");
            sb.append("  2. Using MacPorts: sudo port install ImageMagick\n");
            sb.append("  3. Verify: magick -version\n");
        }
        
        return sb.toString();
    }
}
