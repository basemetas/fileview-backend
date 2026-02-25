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

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.List;

/**
 * 字体工具类
 * 
 * 扫描系统字体目录，获取可用字体名称列表
 * 
 * @author 夫子
 */
public class FontUtils {
    private static final Logger logger = LoggerFactory.getLogger(FontUtils.class);

    /**
     * 扫描系统字体目录，获取可用的字体名称集合
     * 
     * @param scanPaths 自定义扫描路径列表
     * @return 可用字体名称集合（包含 Font Name 和 Full Name）
     */
    public static Set<String> scanAvailableFonts(List<String> scanPaths) {
        Set<String> availableFonts = new HashSet<>();
        GraphicsEnvironment ge = null;

        try {
            // 1. 通过 GraphicsEnvironment 获取系统字体（优先方式）
            try {
                ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Font[] allFonts = ge.getAllFonts();
                
                for (Font font : allFonts) {
                    // 添加字体名称
                    availableFonts.add(font.getFontName());
                    availableFonts.add(font.getName());
                    availableFonts.add(font.getFamily());
                }
            } catch (Exception e) {
                logger.warn("无法通过 GraphicsEnvironment 获取字体: {}", e.getMessage());
            }

            // 2. 扫描字体目录文件（补充方式）
            List<String> fontDirs = getFontDirectories(scanPaths);           
            for (String dir : fontDirs) {
                File fontDir = new File(dir);
                if (!fontDir.exists() || !fontDir.isDirectory()) {
                    continue;
                }
            }

        } catch (Exception e) {
            logger.error("扫描系统字体时发生异常: {}", e.getMessage(), e);
        }

        return availableFonts;
    }

    /**
     * 获取字体扫描目录列表
     */
    public static List<String> getFontDirectories(List<String> customPaths) {
        List<String> fontDirs = new ArrayList<>();

        // 添加自定义路径
        if (customPaths != null && !customPaths.isEmpty()) {
            fontDirs.addAll(customPaths);
        }

        // 仅针对 Linux 环境添加系统默认路径
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux")) {
            fontDirs.add("/usr/share/fonts");
            fontDirs.add("/usr/local/share/fonts");
            fontDirs.add("/opt/fonts");
            fontDirs.add("/app/fonts");
            fontDirs.add(System.getProperty("user.home") + "/.fonts");
        } else {
            logger.warn("当前OFD转换仅支持Linux环境，检测到操作系统: {}，不会追加系统默认字体目录", osName);
        }

        return fontDirs;
    }

    /**
     * 递归扫描字体目录
     */
    private static int scanFontDirectory(File dir, Set<String> availableFonts, GraphicsEnvironment ge) {
        int count = 0;
        
        File[] files = dir.listFiles();
        if (files == null) {
            return count;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                count += scanFontDirectory(file, availableFonts, ge);
            } else if (isFontFile(file)) {
                // 尝试解析并注册字体文件（关键！）
                try (FileInputStream fis = new FileInputStream(file)) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, fis);
                    if (font != null) {
                        // 注册字体到 GraphicsEnvironment
                        boolean registered = ge.registerFont(font);
                        
                        availableFonts.add(font.getFontName());
                        availableFonts.add(font.getName());
                        availableFonts.add(font.getFamily());
                        count++;
                        
                        if (font.getFamily().contains("Source Han") || font.getFamily().contains("思源")) {
                            logger.info("✅ 成功注册思源字体: {} -> Family={}, FontName={}, registered={}", 
                                file.getName(), font.getFamily(), font.getFontName(), registered);
                        } else {
                            logger.trace("解析并注册字体: {} -> {} (registered: {})", 
                                file.getName(), font.getFontName(), registered);
                        }
                    }
                } catch (Exception e) {
                    logger.trace("无法解析字体文件 {}: {}", file.getName(), e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * 判断是否为字体文件
     */
    private static boolean isFontFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".ttf") || 
               name.endsWith(".otf") || 
               name.endsWith(".ttc") ||
               name.endsWith(".pfb") ||
               name.endsWith(".pfa");
    }

    /**
     * 检查指定字体是否可用
     */
    public static boolean isFontAvailable(String fontName, Set<String> availableFonts) {
        if (fontName == null || availableFonts == null) {
            return false;
        }

        // 精确匹配
        if (availableFonts.contains(fontName)) {
            return true;
        }

        // 模糊匹配（包含关系）
        return availableFonts.stream()
            .anyMatch(f -> f.equalsIgnoreCase(fontName) || 
                          f.contains(fontName) || 
                          fontName.contains(f));
    }
    
    // ======================== 字体相关错误检测 ========================
    
    /**
     * 检查是否为字体相关错误
     */
    public static boolean isFontRelatedError(Throwable e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message != null) {
            return message.contains("FontManagerFactory") ||
                    message.contains("FontConfiguration") ||
                    message.contains("Fontconfig") ||
                    message.contains("FontLoader") ||
                    message.contains("font") && message.contains("null") ||
                    message.contains("HeadlessException") ||
                    message.contains("HeadlessFontManager") ||
                    message.contains("sun.awt.HeadlessFontManager") ||
                    message.contains("sun.font") ||
                    message.contains("createFont") ||
                    message.contains("Font.") ||
                    message.contains("GraphicsEnvironment");
        }

        String className = e.getClass().getSimpleName();
        if (className.contains("Font") ||
                className.contains("Headless") ||
                className.contains("InternalError") ||
                className.contains("ClassNotFoundException")) {
            return true;
        }

        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                String methodName = element.getMethodName();
                String className2 = element.getClassName();
                if (className2.contains("FontManager") ||
                        className2.contains("FontLoader") ||
                        className2.contains("FontConfiguration") ||
                        className2.contains("FontManagerFactory") ||
                        className2.contains("GraphicsEnvironment") ||
                        className2.contains("sun.font") ||
                        className2.contains("sun.awt") ||
                        methodName.contains("font") ||
                        methodName.contains("Font") ||
                        methodName.contains("createFont") ||
                        methodName.contains("loadFont") ||
                        methodName.contains("scanFontDir")) {
                    return true;
                }
            }
        }

        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isFontRelatedError(cause);
        }

        return false;
    }
    
    /**
     * 根据字体名猜测适合的兜底字体
     */
    public static String guessFallbackFont(String fontName) {
        if (fontName == null) {
            return "Source Han Serif CN";
        }
        String name = fontName.toLowerCase();
        if (name.contains("hei") || name.contains("黑") || name.contains("sans") || name.contains("yahei")) {
            return "Source Han Sans SC";
        }
        return "Source Han Serif CN";
    }
}
