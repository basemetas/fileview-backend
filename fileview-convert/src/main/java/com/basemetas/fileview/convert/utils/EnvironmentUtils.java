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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 环境检测与系统处理工具类
 * 
 * 集中管理所有与系统环境相关的检测和处理逻辑：
 * - WSL2 环境检测
 * - 操作系统类型检测
 * - 外部命令可用性检测
 * - 系统架构信息
 * - 中文环境初始化
 * - PDF容错环境设置
 * 
 * 设计原则：
 * - 结果缓存：避免重复检测
 * - 线程安全：使用双重检查锁定
 * - 多重验证：提高检测准确性
 * 
 * @author 夫子
 */
@Component
public class EnvironmentUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentUtils.class);
    
    // 环境配置状态标志
    private static volatile Boolean chineseEnvironmentInitialized = false;
    private static final Object chineseInitLock = new Object();
    
    // 缓存结果
    private static volatile Boolean cachedWslResult = null;
    private static volatile Boolean cached7zAvailable = null;
    private static volatile Boolean cachedImageMagickAvailable = null;
    private static volatile String cachedOsName = null;
    private static volatile Boolean cachedIsLinux = null;
    
    // ======================== 操作系统检测 ========================
    
    /**
     * 检测是否为Linux系统
     * 
     * @return true 如果运行在Linux环境
     */
    public static boolean isLinuxSystem() {
        if (cachedIsLinux != null) {
            return cachedIsLinux;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cachedIsLinux != null) {
                return cachedIsLinux;
            }
            
            String osName = System.getProperty("os.name");
            cachedIsLinux = osName != null && osName.toLowerCase().contains("linux");
            return cachedIsLinux;
        }
    }
    
    // ======================== WSL2 环境检测 ========================
    
    /**
     * 检测是否为 WSL2 环境
     * 
     * 使用多重验证机制确保检测精确性：
     * 1. 检查 /proc/version 文件
     * 2. 检查 /proc/sys/kernel/osrelease 文件
     * 3. 检查环境变量 WSL_DISTRO_NAME 和 WSL_INTEROP
     * 
     * @return true 如果运行在 WSL2 环境
     */
    public static boolean isWslEnvironment() {
        // 双重检查锁定优化性能
        if (cachedWslResult != null) {
            return cachedWslResult;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cachedWslResult != null) {
                return cachedWslResult;
            }
            
            cachedWslResult = detectWslEnvironment();
            if (cachedWslResult) {
                logger.info("🔍 检测到 WSL2 运行环境");
            }
            return cachedWslResult;
        }
    }
    
    /**
     * 实际执行 WSL 环境检测
     */
    private static boolean detectWslEnvironment() {
        try {
            // 方法1: 检查 /proc/version
            File procVersion = new File("/proc/version");
            if (procVersion.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(procVersion))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String lowerLine = line.toLowerCase();
                        if (lowerLine.contains("microsoft") || lowerLine.contains("wsl")) {
                            logger.debug("WSL 环境检测成功 (procVersion): {}", line);
                            return true;
                        }
                    }
                }
            }
            
            // 方法2: 检查 /proc/sys/kernel/osrelease
            File osRelease = new File("/proc/sys/kernel/osrelease");
            if (osRelease.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(osRelease))) {
                    String line = reader.readLine();
                    if (line != null && line.toLowerCase().contains("microsoft")) {
                        logger.debug("WSL 环境检测成功 (osrelease): {}", line);
                        return true;
                    }
                }
            }
            
            // 方法3: 检查环境变量
            String wslDistro = System.getenv("WSL_DISTRO_NAME");
            String wslInterop = System.getenv("WSL_INTEROP");
            if (wslDistro != null || wslInterop != null) {
                logger.debug("WSL 环境检测成功 (env): WSL_DISTRO_NAME={}, WSL_INTEROP={}", 
                    wslDistro, wslInterop);
                return true;
            }
        } catch (Exception e) {
            logger.debug("WSL 环境检测失败: {}", e.getMessage());
        }
        return false;
    }
    
    // ======================== 外部命令检测 ========================
    
    /**
     * 检查外部 7z 命令是否可用
     * 
     * @return true 如果 7z 命令可执行
     */
    public static boolean isExternal7zAvailable() {
        if (cached7zAvailable != null) {
            return cached7zAvailable;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cached7zAvailable != null) {
                return cached7zAvailable;
            }
            
            cached7zAvailable = detectExternal7z();
            return cached7zAvailable;
        }
    }
    
    /**
     * 实际检测 7z 命令
     */
    private static boolean detectExternal7z() {
        try {
            ProcessBuilder pb = new ProcessBuilder("7z");
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            logger.debug("外部 7z 命令可用");
            return true;
        } catch (Exception e) {
            logger.debug("外部 7z 命令不可用: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查 ImageMagick 命令是否可用
     * 
     * @return true 如果 magick 或 convert 命令可执行
     */
    public static boolean isImageMagickAvailable() {
        if (cachedImageMagickAvailable != null) {
            return cachedImageMagickAvailable;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cachedImageMagickAvailable != null) {
                return cachedImageMagickAvailable;
            }
            
            // 尝试 magick 命令 (ImageMagick 7.x)
            if (isCommandAvailable("magick", "--version")) {
                logger.debug("ImageMagick 7.x 可用 (magick)");
                cachedImageMagickAvailable = true;
                return true;
            }
            
            // 尝试 convert 命令 (ImageMagick 6.x)
            if (isCommandAvailable("convert", "--version")) {
                logger.debug("ImageMagick 6.x 可用 (convert)");
                cachedImageMagickAvailable = true;
                return true;
            }
            
            logger.debug("ImageMagick 不可用");
            cachedImageMagickAvailable = false;
            return false;
        }
    }
    
    /**
     * 通用命令可用性检测
     * 
     * @param command 命令名称
     * @param args 命令参数
     * @return true 如果命令可执行
     */
    public static boolean isCommandAvailable(String command, String... args) {
        try {
            String[] cmdArray = new String[args.length + 1];
            cmdArray[0] = command;
            System.arraycopy(args, 0, cmdArray, 1, args.length);
            
            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0 || p.exitValue() == 1; // 某些命令版本信息返回1
        } catch (Exception e) {
            return false;
        }
    }
    
    // ======================== 操作系统检测 ========================
    
    /**
     * 获取操作系统名称
     * 
     * @return 操作系统名称（小写）
     */
    public static String getOsName() {
        if (cachedOsName == null) {
            cachedOsName = System.getProperty("os.name", "").toLowerCase();
        }
        return cachedOsName;
    }
    
    /**
     * 检查是否为 Linux 系统
     * 
     * @return true 如果运行在 Linux
     */
    public static boolean isLinux() {
        if (cachedIsLinux == null) {
            cachedIsLinux = getOsName().contains("linux");
        }
        return cachedIsLinux;
    }
    
    // ======================== 环境信息获取 ========================
    
    /**
     * 获取 Java 版本
     * 
     * @return Java 版本号
     */
    public static String getJavaVersion() {
        return System.getProperty("java.version", "unknown");
    }
    
    /**
     * 获取当前工作目录
     * 
     * @return 工作目录路径
     */
    public static String getCurrentWorkingDirectory() {
        return System.getProperty("user.dir", "");
    }
    
    /**
     * 获取系统临时目录
     * 
     * @return 临时目录路径
     */
    public static String getTempDirectory() {
        return System.getProperty("java.io.tmpdir", "/tmp");
    }
    
    /**
     * 获取文件分隔符
     * 
     * @return 文件分隔符（Windows: \, Unix: /）
     */
    public static String getFileSeparator() {
        return File.separator;
    }
    
    // ======================== 环境信息汇总 ========================
    
    /**
     * 打印完整的环境信息（用于调试）
     */
    public static void printEnvironmentInfo() {
        logger.info("==================== 环境信息 ====================");
        logger.info("操作系统: {} ({})", getOsName(), System.getProperty("os.version"));
        logger.info("Java 版本: {}", getJavaVersion());
        logger.info("工作目录: {}", getCurrentWorkingDirectory());
        logger.info("临时目录: {}", getTempDirectory());
        logger.info("WSL2 环境: {}", isWslEnvironment());
        logger.info("7z 可用: {}", isExternal7zAvailable());
        logger.info("ImageMagick 可用: {}", isImageMagickAvailable());
        logger.info("================================================");
    }
    
    /**
     * 清除所有缓存（主要用于测试）
     */
    public static void clearCache() {
        cachedWslResult = null;
        cached7zAvailable = null;
        cachedImageMagickAvailable = null;
        cachedOsName = null;
        cachedIsLinux = null;
        logger.debug("环境检测缓存已清除");
    }
    
    // ======================== 中文环境配置 ========================
    
    /**
     * 初始化中文环境支持，解决Linux下中文乱码问题
     */
    public static void initializeChineseEnvironment() {
        synchronized (chineseInitLock) {
            if (chineseEnvironmentInitialized) {
                return;
            }

            try {
                logger.info("=== 初始化中文环境支持 ===");

                // 1. 设置系统属性强制UTF-8编码
                System.setProperty("file.encoding", "UTF-8");
                System.setProperty("sun.jnu.encoding", "UTF-8");
                System.setProperty("java.awt.headless", "true");

                // 2. 设置字体相关属性
                System.setProperty("awt.useSystemAAFontSettings", "on");
                System.setProperty("swing.aatext", "true");
                System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel");

                // 3. 强制设置默认字符集为UTF-8（仅对新创建的对象有效）
                try {
                    java.lang.reflect.Field defaultCharsetField = java.nio.charset.Charset.class
                            .getDeclaredField("defaultCharset");
                    defaultCharsetField.setAccessible(true);
                    defaultCharsetField.set(null, StandardCharsets.UTF_8);
                    logger.info("已设置JVM默认字符集为UTF-8");
                } catch (Exception e) {
                    logger.warn("无法设置JVM默认字符集，可能影响中文处理: {}", e.getMessage());
                }

                // 4. 检查并记录可用的中文字体
                checkAvailableChineseFonts();

                // 5. 预设环境变量（对子进程有效）
                setupEnvironmentVariables();

                chineseEnvironmentInitialized = true;
                logger.info("中文环境支持初始化完成");

            } catch (Exception e) {
                logger.error("初始化中文环境支持失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 检查系统可用的中文字体
     * 在无头环境中安全地检查字体支持
     */
    private static void checkAvailableChineseFonts() {
        try {
            // 检查是否为无头环境
            boolean isHeadless = GraphicsEnvironment.isHeadless();
            if (isHeadless) {
                logger.info("检测到无头环境(Headless)，跳过图形字体检查");
                checkFontDirectories();
                return;
            }

            logger.info("检查系统可用字体...");

            // 尝试安全地获取GraphicsEnvironment
            GraphicsEnvironment ge;
            try {
                ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            } catch (InternalError | HeadlessException e) {
                logger.warn("无法访问GraphicsEnvironment，可能是无头环境或字体配置问题: {}", e.getMessage());
                checkFontDirectories();
                return;
            }

            // 尝试获取字体列表
            Font[] fonts;
            try {
                fonts = ge.getAllFonts();
            } catch (Exception | Error e) {
                logger.warn("无法获取系统字体列表: {}", e.getMessage());
                checkFontDirectories();
                return;
            }

            logger.info("系统共有 {} 个字体", fonts.length);

            // 直接遍历系统字体，查找支持中文的字体
            int foundChineseFonts = 0;
            for (Font font : fonts) {
                try {
                    if (font.canDisplay('中') && font.canDisplay('文')) {
                        logger.info("✅ 找到可用中文字体: {} (Family: {})", 
                            font.getFontName(), font.getFamily());
                        foundChineseFonts++;
                        
                        // 只记录前 10 个，避免日志过多
                        if (foundChineseFonts >= 10) {
                            logger.info("... 还有更多中文字体，仅显示前10个");
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 忽略个别字体检查失败
                }
            }

            if (foundChineseFonts == 0) {
                logger.warn("⚠️  系统中未找到可用的中文字体，可能导致中文显示为方块");
                logger.warn("建议安装中文字体包：");
                logger.warn("  Ubuntu/Debian: sudo apt-get install fonts-wqy-microhei fonts-wqy-zenhei");
                logger.warn("  CentOS/RHEL: sudo yum install wqy-microhei-fonts wqy-zenhei-fonts");
            } else {
                logger.info("系统共找到 {} 个可用的中文字体", foundChineseFonts);
            }

            checkFontDirectories();

        } catch (Exception | Error e) {
            logger.warn("检查中文字体时发生异常，可能是无头环境或字体配置问题: {}", e.getMessage());
            try {
                checkFontDirectories();
            } catch (Exception ex) {
                logger.debug("检查字体目录也失败: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * 检查字体目录是否存在（无头环境安全方法）
     */
    private static void checkFontDirectories() {
        String[] fontPaths = { "/usr/share/fonts", "/usr/local/share/fonts", "/opt/fonts", "/app/fonts" };
        for (String path : fontPaths) {
            try {
                File fontDir = new File(path);
                if (fontDir.exists()) {
                    logger.info("字体目录存在: {}", path);
                    File[] fontFiles = fontDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttf") ||
                            name.toLowerCase().endsWith(".otf") ||
                            name.toLowerCase().endsWith(".ttc"));
                    if (fontFiles != null && fontFiles.length > 0) {
                        logger.info("  -> 发现 {} 个字体文件", fontFiles.length);
                    }
                } else {
                    logger.debug("字体目录不存在: {}", path);
                }
            } catch (Exception e) {
                logger.debug("检查字体目录{}时出错: {}", path, e.getMessage());
            }
        }
    }
    
    /**
     * 设置环境变量以支持中文处理
     */
    public static void setupEnvironmentVariables() {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            java.util.Map<String, String> env = pb.environment();

            env.put("LANG", "en_US.UTF-8");
            env.put("LC_ALL", "en_US.UTF-8");
            env.put("LC_CTYPE", "en_US.UTF-8");

            logger.info("已设置UTF-8编码环境变量");

        } catch (Exception e) {
            logger.warn("设置环境变量失败: {}", e.getMessage());
        }
    }
    
    // ======================== PDF环境配置 ========================
    
    /**
     * 设置PDF容错环境，处理字体和变换矩阵问题
     * 基于官方最佳实践和实际问题优化PDFBox行为
     */
    public static void setupPdfTolerantEnvironment() {
        logger.debug("设置PDF容错环境...");

        // 1. 抑制PDFBox变换矩阵警告
        System.setProperty("org.apache.pdfbox.util.Matrix.level", "ERROR");
        System.setProperty("org.apache.pdfbox.pdfparser.level", "ERROR");
        System.setProperty("org.apache.pdfbox.pdmodel.PDPageContentStream.level", "ERROR");
        System.setProperty("org.apache.pdfbox.rendering.level", "ERROR");
        System.setProperty("org.apache.pdfbox.cos.level", "ERROR");

        // 2. 设置字体回退和容错机制
        System.setProperty("ofdrw.font.fallback", "true");
        System.setProperty("ofdrw.font.ignoreErrors", "true");
        System.setProperty("ofdrw.font.useSystemFallback", "true");
        System.setProperty("ofdrw.font.enableSmartMapping", "true");

        // 3. 设置PDF生成容错选项
        System.setProperty("pdfbox.fontcache", "false");
        System.setProperty("pdfbox.useCachedFontMetrics", "false");
        System.setProperty("pdfbox.rendering.lazy", "true");
        System.setProperty("pdfbox.forgiving", "true");

        // 4. 设置日志级别
        System.setProperty("org.apache.pdfbox.util.level", "ERROR");

        // 5. iText字体解析器日志级别控制
        System.setProperty("com.itextpdf.io.font.level", "ERROR");
        System.setProperty("com.itextpdf.io.font.ItextOpenTypeParser.level", "ERROR");
        System.setProperty("com.itextpdf.io.font.ItextTrueTypeFont.level", "ERROR");
        System.setProperty("com.itextpdf.io.source.level", "ERROR");

        // 6. OFDRW特定的容错设置
        System.setProperty("ofdrw.converter.ignoreWarnings", "true");
        System.setProperty("ofdrw.converter.forgiving", "true");
        System.setProperty("ofdrw.renderer.ignoreErrors", "true");

        logger.debug("✅ PDF容错环境设置完成");
    }
    
    /**
     * 恢复PDF环境设置
     */
    public static void restorePdfEnvironment() {
        String[] propertiesToClear = {
                "org.apache.pdfbox.util.Matrix.level",
                "org.apache.pdfbox.pdfparser.level",
                "org.apache.pdfbox.pdmodel.PDPageContentStream.level",
                "org.apache.pdfbox.rendering.level",
                "org.apache.pdfbox.cos.level",
                "org.apache.pdfbox.util.level",
                "ofdrw.font.fallback",
                "ofdrw.font.ignoreErrors",
                "ofdrw.font.useSystemFallback",
                "ofdrw.font.enableSmartMapping",
                "pdfbox.fontcache",
                "pdfbox.useCachedFontMetrics",
                "pdfbox.rendering.lazy",
                "pdfbox.forgiving",
                "ofdrw.converter.ignoreWarnings",
                "ofdrw.converter.forgiving",
                "ofdrw.renderer.ignoreErrors",
                "com.itextpdf.io.font.level",
                "com.itextpdf.io.font.ItextOpenTypeParser.level",
                "com.itextpdf.io.font.ItextTrueTypeFont.level",
                "com.itextpdf.io.source.level"
        };

        for (String property : propertiesToClear) {
            System.clearProperty(property);
        }

        logger.debug("✅ PDF环境设置已恢复");
    }
}
