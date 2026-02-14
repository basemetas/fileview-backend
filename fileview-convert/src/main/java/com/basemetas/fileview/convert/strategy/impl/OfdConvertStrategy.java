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
import com.basemetas.fileview.convert.common.ValidateAndNormalized;
import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.config.OfdFontProperties;
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import com.basemetas.fileview.convert.utils.EnvironmentUtils;
import com.basemetas.fileview.convert.utils.FontUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import javax.imageio.ImageIO;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.converter.ImageMaker;
import org.ofdrw.converter.export.ImageExporter;
import org.ofdrw.converter.export.OFDExporter;
import org.ofdrw.converter.export.PDFExporterPDFBox;
import org.ofdrw.converter.export.SVGExporter;
import org.ofdrw.converter.export.TextExporter;
import org.ofdrw.converter.export.HTMLExporter;
import org.ofdrw.converter.ConvertHelper;
import org.ofdrw.converter.FontLoader;

/**
 * OFD文档转换策略实现类
 * 
 * 支持OFD格式转换为PDF等格式
 * OFD（Open Fixed-layout Document）是中国国家标准的电子文件格式
 * 
 * 特性：
 * - 支持OFD到PDF转换
 * - 多线程并行转换优化
 * - 中文环境支持（Linux）
 * - 内存优化和错误恢复机制
 * 
 * @author 夫子
 */
@ConvertStrategy(category = FileCategory.OFD, description = "OFD文档转换（国标电子文件格式）", priority = 100)
public class OfdConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(OfdConvertStrategy.class);

    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private ValidateAndNormalized validateAndNormalized;

    @Autowired
    private OfdFontProperties ofdFontProperties;

    @Value("${ofd.convert.default-target-format:pdf}")
    private String defaultTargetFormat;

    @Value("${ofd.convert.parallel.min-pages:3}")
    private int minPagesForParallel;

    @Value("${ofd.convert.parallel.max-threads:4}")
    private int maxParallelThreads;

    // 转换锁映射，防止同一文件的并发转换
    private static final ConcurrentHashMap<String, ReentrantLock> conversionLocks = new ConcurrentHashMap<>();

    // 多页并行转换线程池（懒加载）
    private static volatile ExecutorService parallelConversionPool;
    private static final Object poolLock = new Object();

    // BouncyCastle版本诊断标志
    private static boolean bounycCastleDiagnosisRun = false;

    // 系统可用字体集合（LRU缓存以限制内存占用）
    // 2C4G优化：使用LinkedHashMap实现LRU，最多缓存500个字体
    private static final Map<String, Boolean> availableFonts = Collections.synchronizedMap(
        new LinkedHashMap<String, Boolean>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 500; // LRU最大缓存500个字体
            }
        }
    );

    // OFDRW字体加载标志（确保只初始化一次）
    private static volatile boolean ofdFontsLoaded = false;
    private static final Object fontLoadLock = new Object();

    static {
        // 静态初始化中文环境支持
        EnvironmentUtils.initializeChineseEnvironment();
    }

    /**
     * 定时清理无用的转换锁（每小时执行一次）
     * 避免conversionLocks持续增长导致内存泄漏
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void cleanupUnusedLocks() {
        int initialSize = conversionLocks.size();
        conversionLocks.entrySet().removeIf(entry -> {
            ReentrantLock lock = entry.getValue();
            // 移除未被持有且无等待线程的锁
            return !lock.isLocked() && !lock.hasQueuedThreads();
        });
        int removed = initialSize - conversionLocks.size();
        if (removed > 0) {
            logger.info("清理无用转换锁: 移除 {} 个，剩余 {} 个", removed, conversionLocks.size());
        }
    }

    @PostConstruct
    public void init() {
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);

        logger.info("OFD转换策略初始化完成 - 类型: OFD, 支持源格式: ofd");
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);

        // 在应用启动时一次性初始化 OFDRW 字体加载器
        initializeOFDRWFontLoader();
    }

    @Override
    public boolean convert(String filePath, String targetPath) {
        return convert(filePath, targetPath, "converted_ofd", defaultTargetFormat);
    }

    @Override
    public boolean convert(String filePath, String targetPath, String targetFileName, String targetFormat) {
        logger.info("开始OFD文件转换 - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}",
                filePath, targetPath, targetFileName, targetFormat);

        // 运行BouncyCastle版本诊断（仅在第一次转换时执行）
        if (!bounycCastleDiagnosisRun) {
            diagnoseBouncyCastleVersion();
            bounycCastleDiagnosisRun = true;
        }

        try {
            // 1. 使用统一工具类验证参数
            ValidateAndNormalized.ValidationResult validationResult = validateAndNormalized
                    .validateConversionParameters(filePath, targetPath, targetFileName, targetFormat);

            if (!validationResult.isSuccess()) {
                logger.error("参数验证失败: {}", validationResult.getMessage());
                return false;
            }

            // 获取验证后的源文件路径
            String actualSourcePath = validationResult.getCorrectedPath();

            // 2. 检查目标格式是否支持
            if (!isTargetFormatSupported(targetFormat)) {
                Set<String> supportedFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);
                logger.error("不支持的目标格式: {}. 支持的格式: {}", targetFormat, supportedFormats);
                return false;
            }

            // 3. 构建目标文件完整路径
            String targetFilePath = validateAndNormalized.buildTargetFilePathEnhanced(targetPath, targetFileName,
                    targetFormat);

            // 4. 确保目标目录存在
            if (!validateAndNormalized.ensureTargetDirectory(targetFilePath)) {
                logger.error("无法创建或访问目标目录");
                return false;
            }

            // 5. 根据目标格式选择转换方法
            return performOfdConversion(actualSourcePath, targetFilePath, targetFormat);

        } catch (Exception e) {
            logger.error("OFD文件转换异常 - 源文件: {}, 目标路径: {}", filePath, targetPath, e);
            return false;
        }
    }

    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.OFD, sourceFormat, targetFormat);
    }

    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.OFD);
    }

    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);
    }


    /**
     * 检查是否为资源相关的错误
     */
    private static boolean isResourceRelatedError(Throwable e) {
        if (e == null) {
            return false;
        }

        String message = e.getMessage();
        if (message != null) {
            return message.contains("无法切换路径到") ||
                    message.contains("目录不存在") ||
                    message.contains("ResourceLocator") ||
                    message.contains("ErrorPathException") ||
                    message.contains("PageBlock无法渲染") ||
                    message.contains("getImage") && message.contains("Res_");
        }

        // 检查异常类型
        String className = e.getClass().getSimpleName();
        if (className.contains("ErrorPath") || className.contains("Resource")) {
            return true;
        }

        // 检查堆栈跟踪
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                String methodName = element.getMethodName();
                String className2 = element.getClassName();
                if (className2.contains("ResourceLocator") ||
                        className2.contains("ResourceManage") ||
                        methodName.contains("getImage") ||
                        methodName.contains("getResource")) {
                    return true;
                }
            }
        }

        // 检查原因异常
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isResourceRelatedError(cause);
        }

        return false;
    }

    /**
     * 检查是否为字体相关错误
     */
    private static boolean isFontRelatedError(Throwable e) {
        return FontUtils.isFontRelatedError(e);
    }

    /**
     * 使用资源修复模式转换OFD到图片（针对资源缺失问题）
     */
    private boolean convertOfdToImageWithResourceFix(String sourcePath, String targetPath, String targetFormat) {
        logger.info("尝试使用资源修复模式转换OFD: {} -> {}", sourcePath, targetPath);

        try {
            // 设置严格的容错模式
            System.setProperty("ofdrw.renderer.ignoreImageError", "true");
            System.setProperty("ofdrw.renderer.ignoreMissingResource", "true");
            System.setProperty("ofdrw.renderer.defaultImagePlaceholder", "true");

            logger.info("启用容错模式，忽略缺失的资源文件");

            // 1. 首先尝试仅转换第一页（通常第一页不会有资源问题）
            if (convertOfdFirstPageOnly(sourcePath, targetPath, targetFormat)) {
                logger.info("第一页转换成功，忽略其他可能有问题的页面");
                return true;
            }

            // 2. 如果第一页转换也失败，尝试使用简化模式
            logger.warn("第一页转换也失败，尝试用简化模式");
            return convertOfdToImageWithFallback(sourcePath, targetPath, targetFormat);

        } catch (Exception e) {
            logger.error("资源修复模式转换也失败: {}", e.getMessage());
            return false;
        } finally {
            // 清理容错设置
            System.clearProperty("ofdrw.renderer.ignoreImageError");
            System.clearProperty("ofdrw.renderer.ignoreMissingResource");
            System.clearProperty("ofdrw.renderer.defaultImagePlaceholder");
        }
    }

    /**
     * 仅转换OFD文件的第一页（用于处理资源缺失问题）
     */
    private boolean convertOfdFirstPageOnly(String sourcePath, String targetPath, String targetFormat) {
        logger.info("尝试仅转换第一页: {} -> {}", sourcePath, targetPath);

        try {
            Path sourcePath_p = Paths.get(sourcePath);
            File targetFile = new File(targetPath);

            // 确保目标目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 创建临时目录
            Path tempDir = parentDir.toPath().resolve("temp_first_page_" + System.currentTimeMillis());
            Files.createDirectories(tempDir);

            try {
                // 使用ImageMaker直接转换第一页
                try (OFDReader reader = new OFDReader(sourcePath_p)) {
                    ImageMaker imageMaker = new ImageMaker(reader, 20.0f);

                    // 只转换第一页（索引0）
                    BufferedImage pageImage = imageMaker.makePage(0);

                    if (pageImage == null) {
                        logger.warn("第一页转换结果为空");
                        return false;
                    }

                    // 保存图片
                    String formatName = targetFormat.toLowerCase();
                    if ("jpg".equals(formatName)) {
                        formatName = "jpeg";
                    }

                    boolean saved = ImageIO.write(pageImage, formatName, targetFile);
                    if (saved && targetFile.exists() && targetFile.length() > 0) {
                        logger.info("第一页转换成功: {} bytes", targetFile.length());
                        return true;
                    } else {
                        logger.warn("第一页图片保存失败");
                        return false;
                    }
                }

            } finally {
                // 清理临时目录
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    logger.debug("清理临时文件失败: {}", path);
                                }
                            });
                } catch (IOException e) {
                    logger.debug("清理临时目录失败: {}", tempDir);
                }
            }
        } catch (Exception e) {
            logger.warn("第一页转换失败: {}", e.getMessage());
            return false;
        }

    }

    /**
     * 使用无字体模式转换OFD到图片（备用方案，仅Linux环境）
     */
    private boolean convertOfdToImageWithoutFont(String sourcePath, String targetPath, String targetFormat) {
        logger.info("尝试使用无字体模式转换OFD: {} -> {}", sourcePath, targetPath);

        try {
            // 设置严格的无头模式
            System.setProperty("java.awt.headless", "true");
            System.setProperty("java2d.font.usePlatformFont", "false");
            System.setProperty("sun.java2d.fontpath", "");
            System.setProperty("sun.font.fontmanager", "sun.font.NullFontManager");

            // 额外的安全设置
            System.setProperty("awt.useSystemAAFontSettings", "off");
            System.setProperty("swing.aatext", "false");
            System.setProperty("sun.java2d.renderer.verbose", "false");

            // 使用最简单的转换方式
            logger.info("开始无字体模式转换...");

            // 调用备用转换方法
            return convertOfdToImageWithFallback(sourcePath, targetPath, targetFormat);

        } catch (Exception e) {
            logger.error("无字体模式转换也失败: {}", e.getMessage());

            // 如果仍然失败，尝试最简单的第一页转换
            logger.warn("尝试最简单的第一页转换方式...");
            try {
                return convertOfdFirstPageOnly(sourcePath, targetPath, targetFormat);
            } catch (Exception ex) {
                logger.error("最简单的第一页转换也失败: {}", ex.getMessage());
                return false;
            }
        }
    }


    /**
     * 获取或创建多页并行转换线程池
     */
    private ExecutorService getParallelConversionPool() {
        if (parallelConversionPool == null) {
            synchronized (poolLock) {
                if (parallelConversionPool == null) {
                    parallelConversionPool = Executors.newFixedThreadPool(maxParallelThreads, r -> {
                        Thread t = new Thread(r, "ofd-parallel-conversion-" + System.currentTimeMillis());
                        t.setDaemon(true); // 设为守护线程，防止阻塞应用退出
                        return t;
                    });
                    logger.info("初始化OFD多页并行转换线程池，最大线程数: {}", maxParallelThreads);
                }
            }
        }
        return parallelConversionPool;
    }

    /**
     * 检查目标格式是否支持
     */
    private boolean isTargetFormatSupported(String format) {
        if (format == null) {
            return false;
        }
        Set<String> supportedFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);
        return supportedFormats.contains(format.toLowerCase());
    }

    /**
     * 获取文件基本名（不包含扩展名）
     */
    private String getBaseName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    /**
     * 执行OFD文件转换
     */
    private boolean performOfdConversion(String sourcePath, String targetPath, String targetFormat) {
        logger.info("开始OFD文件转换: {} -> {}, 格式: {}", sourcePath, targetPath, targetFormat);

        // 使用目标文件路径作为锁的key，防止同一文件的并发转换
        String lockKey = targetPath;
        ReentrantLock lock = conversionLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());

        if (!lock.tryLock()) {
            logger.warn("文件正在转换中，跳过本次转换: {}", targetPath);
            return true; // 返回true表示不需要重复处理
        }

        try {
            // 验证源文件
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                logger.error("源OFD文件不存在: {}", sourcePath);
                return false;
            }

            // 检查目标文件是否已经存在且有效
            File targetFile = new File(targetPath);
            if (targetFile.exists() && targetFile.length() > 1024) { // 至少1KB
                logger.info("目标文件已存在，跳过转换: {} ({} bytes)", targetPath, targetFile.length());
                return true;
            }

            // 根据目标格式选择转换方法
            switch (targetFormat.toLowerCase()) {
                case "pdf":
                    return convertOfdToPdf(sourcePath, targetPath);
                case "png":
                case "jpg":
                case "jpeg":
                case "svg": // 新增：SVG也走统一的图片转换路径
                    return convertOfdToImage(sourcePath, targetPath, targetFormat);
                case "html":
                    return convertOfdToHtml(sourcePath, targetPath);
                case "txt":
                    return convertOfdToText(sourcePath, targetPath);
                default:
                    logger.error("不支持的目标格式: {}", targetFormat);
                    return false;
            }

        } catch (Exception e) {
            logger.error("OFD文件转换失败", e);
            return false;
        } finally {
            lock.unlock();
            // 清理锁映射，防止内存泄漏
            if (!lock.hasQueuedThreads()) {
                conversionLocks.remove(lockKey);
            }
        }
    }

    /**
     * 将OFD文件转换为PDF
     */
    private boolean convertOfdToPdf(String sourcePath, String targetPath) {
        logger.info("📝 开始 OFD 转 PDF 转换: {} -> {}", sourcePath, targetPath);

        // 使用多策略回退机制进行转换
        return convertOfdToPdfWithFallback(sourcePath, targetPath);
    }

    /**
     * 使用多种策略转换OFD到PDF，处理字体和变换矩阵问题
     * 基于OFDRW最佳实践，参考 OFD2PDFTest.java 的实现方式
     */
    private boolean convertOfdToPdfWithFallback(String sourcePath, String targetPath) {
        logger.info("🚀 开始多策略OFD转PDF转换（基于OFDRW最佳实践）: {} -> {}", sourcePath, targetPath);

        // 策略1：标准转换（使用官方推荐的ConvertHelper）
        logger.info("🔄 尝试策略1: 标准转换（ConvertHelper + PDFExporterPDFBox）");
        if (convertOfdToPdfStandard(sourcePath, targetPath)) {
            logger.info("✅ 策略1 标准转换成功！");
            return true;
        }

        // 策略2：容错模式转换（忽略字体警告 + 智能警告过滤）
        logger.warn("⚠️  策略1失败，尝试策略2: 容错模式转换（增强字体处理 + 警告抑制）");
        if (convertOfdToPdfTolerant(sourcePath, targetPath)) {
            logger.info("✅ 策略2 容错模式转换成功！");
            return true;
        }

        // 策略3：图片中介转换（最后的备选方案）
        logger.warn("⚠️  策略2也失败，尝试策略3: 图片中介转换（OFD→PNG→PDF）");
        if (convertOfdToPdfViaImage(sourcePath, targetPath)) {
            logger.info("✅ 策略3 图片中介转换成功！");
            return true;
        }

        // 所有策略都失败
        logger.error("❌ 所有PDF转换策略都失败了！");
        logger.error("   ▶ 策略1（标准转换）: 失败");
        logger.error("   ▶ 策略2（容错模式）: 失败");
        logger.error("   ▶ 策略3（图片中介）: 失败");
        logger.error("建议检查：");
        logger.error("   1. OFD文件是否完整且无损坏");
        logger.error("   2. 是否包含不兼容的数字签名或加密");
        logger.error("   3. 字体文件是否缺失或损坏");
        logger.error("   4. 系统内存是否足够");

        return false;
    }

    /**
     * 策略1：标准OFD转PDF（基于OFDRW最佳实践）
     * 参考官方测试代码 OFD2PDFTest.java 的实现方式
     */
    private boolean convertOfdToPdfStandard(String sourcePath, String targetPath) {
        try {
            logger.info("🚀 策略1：标准OFD转PDF转换（基于OFDRW最佳实践）");

            Path ofdPath = Paths.get(sourcePath);
            Path pdfPath = Paths.get(targetPath);
            
            // 诊断：提取并记录OFD文件中实际使用的字体
            try {
                extractAndLogOfdFonts(sourcePath);
            } catch (Exception e) {
                logger.warn("无法提取OFD字体信息: {}", e.getMessage());
            }
            
            logger.info("开始执行OFD转PDF转换，源文件: {}，目标文件: {}", sourcePath, targetPath);

            // 使用OFDRW官方推荐的ConvertHelper（与 OFD2PDFTest.java 相同）
            try {
                ConvertHelper.toPdf(ofdPath, pdfPath);

                // 验证输出文件
                File targetFile = new File(targetPath);
                if (targetFile.exists() && targetFile.length() > 0) {
                    return true;
                } else {
                    logger.warn("ConvertHelper 执行完成，但目标文件不存在或为空");
                }
            } catch (Exception e) {
                logger.warn("ConvertHelper 转换失败: {}，尝试备选方案 PDFExporterPDFBox", e.getMessage());

                try (OFDExporter exporter = new PDFExporterPDFBox(ofdPath, pdfPath)) {
                    exporter.export();

                    File targetFile = new File(targetPath);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        return true;
                    } else {
                        logger.warn("PDFExporterPDFBox 执行完成，但目标文件不存在或为空");
                    }
                } catch (Exception exporterEx) {
                    logger.warn("PDFExporterPDFBox 也失败: {} - {}", exporterEx.getClass().getSimpleName(), exporterEx.getMessage());
                    throw exporterEx;
                }
            }

        } catch (Exception e) {
            logger.warn("标准OFD转PDF失败：{} - {}", e.getClass().getSimpleName(), e.getMessage());
            // 记录关键错误信息以便调试
            if (e.getMessage() != null) {
                if (e.getMessage().contains("HYe3gj") || e.getMessage().contains("Font69")) {
                    logger.warn("   -> 检测到字体相关错误，将在容错模式中处理");
                } else if (e.getMessage().contains("transformation matrix")) {
                    logger.warn("   -> 检测到PDF变换矩阵警告，将在容错模式中抑制");
                }
            }
            // 打印关键堆栈信息（分析问题）
            logger.warn("错误堆栈（前5行）:");
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
                logger.warn("  [{}] {}", i, stackTrace[i]);
            }
        }
        return false;
    }

    /**
     * 初始化OFDRW字体加载器，参考官方最佳实践
     * 基于官方测试代码 OFD2PDFTest.java 的字体配置方案
     * 
     * 增强版：支持智能字体优先和可配置化映射
     */
    private void initializeOFDRWFontLoader() {
        // 双重检查锁，确保只初始化一次
        if (ofdFontsLoaded) {
            return;
        }

        synchronized (fontLoadLock) {
            if (ofdFontsLoaded) {
                return;
            }

            try {
                // 关闭字体加载器调试信息以减少日志噪音
                FontLoader.DEBUG = false;

                FontLoader fontLoader = FontLoader.getInstance();
                
                // 加载思源字体文件到 OFDRW
                loadSourceHanFontsToOFDRW(fontLoader);

                // 预先扫描并缓存系统可用字体集合
                loadSystemFontsToCache();

                // 标记为已加载
                ofdFontsLoaded = true;
                logger.info("OFDRW字体加载器初始化完成（仅执行一次）");

            } catch (Exception e) {
                logger.error("初始化OFDRW字体加载器失败", e);
                throw new RuntimeException("OFDRW字体加载器初始化失败", e);
            }
        }
    }

    /**
     * 策略2：容错模式OFD转PDF（处理字体和PDF变换矩阵问题）
     * 基于官方最佳实践，增强容错处理能力
     */
    private boolean convertOfdToPdfTolerant(String sourcePath, String targetPath) {
        try {
            logger.info("🛡 策略2：容错模式OFD转PDF转换（增强字体和矩阵处理）");

            // 设置全面的PDF和字体容错处理
            EnvironmentUtils.setupPdfTolerantEnvironment();

            try {
                Path ofdPath = Paths.get(sourcePath);
                Path pdfPath = Paths.get(targetPath);

                // 先尝试使用ConvertHelper（它内置了更好的容错机制）
                try {
                    logger.debug("容错模式：使用 ConvertHelper");
                    ConvertHelper.toPdf(ofdPath, pdfPath);

                    File targetFile = new File(targetPath);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        logger.info("✅ 容错模式 ConvertHelper 转换成功: {} bytes", targetFile.length());
                        return true;
                    }

                } catch (Exception e) {
                    logger.debug("ConvertHelper容错模式失败，尝试直接PDFExporter: {}", e.getMessage());

                    // 如果ConvertHelper失败，使用直接的PDFExporter
                    logger.debug("容错模式：使用 PDFExporterPDFBox");
                    try (OFDExporter exporter = new PDFExporterPDFBox(ofdPath, pdfPath)) {
                        exporter.export();
                    }

                    File targetFile = new File(targetPath);
                    if (targetFile.exists() && targetFile.length() > 0) {
                        logger.info("✅ 容错模式 PDFExporterPDFBox 转换成功: {} bytes", targetFile.length());
                        return true;
                    }
                }

            } finally {
                // 恢复原始环境设置
                EnvironmentUtils.restorePdfEnvironment();
            }

        } catch (Exception e) {
            logger.warn("❌ 容错模式OFD转PDF失败: {}", e.getMessage());
            // 提供更详细的错误诊断信息
            if (e.getMessage() != null) {
                if (e.getMessage().contains("OutOfMemory")) {
                    logger.warn("   -> 内存不足，建议使用图片中介转换");
                } else if (e.getMessage().contains("IOException")) {
                    logger.warn("   -> 文件IO问题，请检查文件权限和磁盘空间");
                }
            }
        }
        return false;
    }

    /**
     * 策略3：通过图片中介转换OFD到PDF
     */
    private boolean convertOfdToPdfViaImage(String sourcePath, String targetPath) {
        try {
            logger.info("策略3：通过图片中介转换OFD到PDF");

            // 创建临时图片目录
            String tempDir = System.getProperty("java.io.tmpdir");
            String tempImageDir = tempDir + "/ofd_pdf_temp_" + System.currentTimeMillis();
            new File(tempImageDir).mkdirs();

            try {
                // 先转换为PNG图片
                String tempImagePath = tempImageDir + "/temp_image.png";
                if (convertOfdToImage(sourcePath, tempImagePath, "png")) {
                    // 然后使用图片转PDF工具
                    return convertImageToPdf(tempImagePath, targetPath);
                }

            } finally {
                // 清理临时文件
                try {
                    deleteDirectory(new File(tempImageDir));
                } catch (Exception cleanupEx) {
                    logger.debug("清理临时目录失败: {}", cleanupEx.getMessage());
                }
            }

        } catch (Exception e) {
            logger.warn("图片中介转换OFD到PDF失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 将图片转换为PDF
     */
    private boolean convertImageToPdf(String imagePath, String pdfPath) {
        try {
            // 这里需要使用PDFBox或iText库将图片转换为PDF
            // 由于这是一个简化实现，我们使用基本的图片到PDF转换
            logger.info("将图片转换为PDF: {} -> {}", imagePath, pdfPath);

            // 实际实现需要添加具体的图片到PDF转换逻辑
            // 这里返回false，提示需要实现该功能
            logger.warn("图片转PDF功能需要进一步实现");
            return false;

        } catch (Exception e) {
            logger.error("图片转PDF失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 将OFD文件转换为图片或矢量格式（统一处理）
     * 使用ImageExporter/SVGExporter实现（OFDRW 2.3.7推荐方式）
     * 针对Linux环境中文乱码问题进行了优化
     * 多页图片/SVG以文件名作为文件夹存放
     * 统一支持: PNG, JPG, JPEG, SVG
     */
    private boolean convertOfdToImage(String sourcePath, String targetPath, String targetFormat) {
        logger.info("转换OFD到{}格式: {} -> {}, 格式: {}",
                isSvgFormat(targetFormat) ? "矢量" : "图片", sourcePath, targetPath, targetFormat);

        long startTime = System.currentTimeMillis();

        try {
            // 确保中文环境已初始化
            EnvironmentUtils.initializeChineseEnvironment();

            Path sourcePath_p = Paths.get(sourcePath);
            File targetFile = new File(targetPath);

            // 获取原始文件名（不含扩展名）用于创建文件夹
            String sourceFileName = getBaseName(Paths.get(sourcePath).getFileName().toString());

            // 确保目标目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 检查目标文件是否已存在，如果存在且大小合理，直接返回成功
            if (targetFile.exists() && targetFile.length() > 1024) { // 至少1KB
                logger.info("目标{}文件已存在，跳过转换: {} ({} bytes)",
                        isSvgFormat(targetFormat) ? "SVG" : "图片", targetPath, targetFile.length());
                return true;
            }

            logger.info("开始使用{}转换OFD到{}（支持中文）: {} -> {}",
                    isSvgFormat(targetFormat) ? "SVGExporter" : "ImageExporter",
                    isSvgFormat(targetFormat) ? "SVG" : "图片", sourcePath, targetPath);

            // SVG格式使用专门的转换逻辑（因为SVGExporter API不同）
            if (isSvgFormat(targetFormat)) {
                return convertOfdToSvgUnified(sourcePath_p, targetFile, targetFormat, sourceFileName);
            }

            // 图片格式继续使用原有的高级转换逻辑
            // 先检查页面数，决定使用串行还是并行转换
            int pageCount = getOfdPageCount(sourcePath_p);
            logger.info("OFD文件总页数: {}", pageCount);

            if (pageCount >= minPagesForParallel) {
                logger.info("页面数>={}，启用并行转换模式（但优先尝试串行模式避免页面索引问题）", minPagesForParallel);
                // 先尝试串行模式，如果失败再使用并行模式
                boolean serialSuccess = convertOfdToImageSerialWithChineseSupportAndFolderStructure(sourcePath_p,
                        targetFile, targetFormat, sourceFileName);
                if (serialSuccess) {
                    logger.info("串行模式转换成功，无需使用并行模式");
                    return true;
                } else {
                    logger.warn("串行模式转换失败，尝试并行模式");
                    return convertOfdToImageParallelWithChineseSupportAndFolderStructure(sourcePath_p, targetFile,
                            targetFormat, pageCount, sourceFileName);
                }
            } else {
                logger.info("页面数<{}，使用串行转换模式", minPagesForParallel);
                return convertOfdToImageSerialWithChineseSupportAndFolderStructure(sourcePath_p, targetFile,
                        targetFormat, sourceFileName);
            }

        } catch (NoSuchMethodError e) {
            // 专门处理BouncyCastle版本不兼容问题
            logger.error("检测到BouncyCastle版本兼容性问题，尝试使用简化方式转换: {}", e.getMessage());
            return convertOfdToImageWithFallback(sourcePath, targetPath, targetFormat);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            logger.error("OFD转{}过程中发生异常: {}, 耗时: {}ms",
                    isSvgFormat(targetFormat) ? "SVG" : "图片", e.getMessage(), duration, e);

            // 检查是否是字体相关的异常
            if (isFontRelatedError(e)) {
                logger.warn("检测到字体相关异常，尝试使用无字体模式处理");
                return convertOfdToImageWithoutFont(sourcePath, targetPath, targetFormat);
            }

            // 检查是否是资源相关的异常
            if (isResourceRelatedError(e)) {
                logger.warn("检测到OFD资源缺失异常，尝试使用简化模式转换");
                return convertOfdToImageWithResourceFix(sourcePath, targetPath, targetFormat);
            }

            // 如果是数字签名相关的异常，尝试使用备用方法
            if (e.getMessage() != null
                    && (e.getMessage().contains("SES_") || e.getMessage().contains("DERIA5String"))) {
                logger.warn("检测到数字签名处理异常，尝试使用简化方式转换");
                return convertOfdToImageWithFallback(sourcePath, targetPath, targetFormat);
            }
            return false;
        }
    }

    /**
     * 判断是否为SVG格式
     */
    private boolean isSvgFormat(String format) {
        return "svg".equalsIgnoreCase(format);
    }

    /**
     * 统一的SVG转换逻辑（整合到图片转换框架中）
     * 复用图片转换的文件存放逻辑和错误处理机制
     */
    private boolean convertOfdToSvgUnified(Path ofdPath, File targetFile, String targetFormat, String sourceFileName) {
        logger.info("使用统一框架转换OFD到SVG: {} -> {}", ofdPath, targetFile.getAbsolutePath());

        // 创建临时目录用于存放生成的SVG文件
        Path tempDir = targetFile.getParentFile().toPath().resolve("temp_svg_unified_" + System.currentTimeMillis());

        try {
            Files.createDirectories(tempDir);

            try (SVGExporter exporter = new SVGExporter(ofdPath, tempDir, 15.0)) {
                logger.info("SVGExporter创建成功，开始执行导出...");

                // 执行导出
                exporter.export();

                // 获取生成的SVG文件路径
                List<Path> svgFilePaths = exporter.getSvgFilePaths();

                if (svgFilePaths == null || svgFilePaths.isEmpty()) {
                    logger.error("SVGExporter未生成任何SVG文件");
                    return false;
                }

                // 单页文件直接复制到目标位置
                if (svgFilePaths.size() == 1) {
                    Path firstSvg = svgFilePaths.get(0);
                    if (!Files.exists(firstSvg)) {
                        logger.error("生成的SVG文件不存在: {}", firstSvg);
                        return false;
                    }

                    Files.copy(firstSvg, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("单页SVG转换成功: {} bytes", targetFile.length());
                    return true;
                }

                // 多页文件：创建以文件名命名的文件夹（复用图片转换的逻辑）
                File svgFolder = new File(targetFile.getParentFile(), sourceFileName);
                if (!svgFolder.exists()) {
                    if (!svgFolder.mkdirs()) {
                        logger.error("无法创建SVG存放文件夹: {}", svgFolder.getAbsolutePath());
                        return false;
                    }
                }

                logger.info("多页SVG文件，将所有SVG存放到文件夹: {}", svgFolder.getAbsolutePath());

                // 将所有页面SVG复制到文件夹中
                int copiedPages = 0;
                for (int i = 0; i < svgFilePaths.size(); i++) {
                    Path sourcePage = svgFilePaths.get(i);
                    if (Files.exists(sourcePage)) {
                        String pageFileName = String.format("page_%d.%s", i + 1, targetFormat.toLowerCase());
                        Path targetPagePath = svgFolder.toPath().resolve(pageFileName);
                        Files.copy(sourcePage, targetPagePath, StandardCopyOption.REPLACE_EXISTING);
                        copiedPages++;
                        logger.debug("复制第{}页SVG到: {}", i + 1, targetPagePath);
                    }
                }

                // 将第一页也复制到原始目标位置（兼容性，复用图片转换的逻辑）
                if (!svgFilePaths.isEmpty() && Files.exists(svgFilePaths.get(0))) {
                    Path firstPageSource = svgFilePaths.get(0);
                    logger.info("准备复制第一页SVG到原始目标位置...");
                    logger.info("- 源文件: {} (大小: {} bytes)", firstPageSource, Files.size(firstPageSource));
                    logger.info("- 目标文件: {}", targetFile.getAbsolutePath());

                    Files.copy(firstPageSource, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // 验证复制结果
                    if (targetFile.exists() && targetFile.length() > 0) {
                        logger.info("✅ 第一页SVG复制到原始目标位置成功: {} (大小: {} bytes)", targetFile.getAbsolutePath(),
                                targetFile.length());
                    } else {
                        logger.error("❌ 第一页SVG复制到原始目标位置失败或文件为空");
                    }
                }

                logger.info("多页SVG转换成功: 在文件夹中生成了{}个SVG文件", copiedPages);
                return true;
            }

        } catch (Exception e) {
            logger.error("统一SVG转换失败: {}", e.getMessage(), e);
            return false;
        } finally {
            // 清理临时目录（复用图片转换的清理逻辑）
            cleanupTempDirectory(tempDir);
        }
    }

    /**
     * 将OFD文件转换为HTML
     * 使用HTMLExporter实现
     */
    private boolean convertOfdToHtml(String sourcePath, String targetPath) {
        logger.info("转换OFD到HTML: {} -> {}", sourcePath, targetPath);

        try {
            Path ofdPath = Paths.get(sourcePath);
            Path htmlPath = Paths.get(targetPath);

            // 确保目标目录存在
            File targetFile = htmlPath.toFile();
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 使用 HTMLExporter 进行转换
            try (HTMLExporter exporter = new HTMLExporter(ofdPath, htmlPath)) {
                exporter.export(); // 导出全部页面
            }

            // 验证输出文件
            if (targetFile.exists() && targetFile.length() > 0) {
                logger.info("OFD转HTML成功: {} bytes", targetFile.length());
                return true;
            } else {
                logger.error("HTML文件创建失败或为空: {}", targetPath);
                return false;
            }

        } catch (Exception e) {
            logger.error("OFD转HTML过程中发生异常", e);
            return false;
        }
    }

    /**
     * 将OFD文件转换为纯文本
     * 使用TextExporter实现
     */
    private boolean convertOfdToText(String sourcePath, String targetPath) {
        logger.info("转换OFD到纯文本: {} -> {}", sourcePath, targetPath);

        try {
            Path ofdPath = Paths.get(sourcePath);
            Path txtPath = Paths.get(targetPath);

            // 确保目标目录存在
            File targetFile = txtPath.toFile();
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 使用 TextExporter 进行转换
            try (TextExporter exporter = new TextExporter(ofdPath, txtPath)) {
                exporter.export(); // 导出全部页面
            }

            // 验证输出文件
            if (targetFile.exists()) {
                logger.info("OFD转文本成功: {} bytes", targetFile.length());
                // 注意：文本转换可能会生成空文件，这取决于OFD文件的内容
                return true;
            } else {
                logger.error("文本文件创建失败: {}", targetPath);
                return false;
            }

        } catch (Exception e) {
            logger.error("OFD转文本过程中发生异常", e);
            return false;
        }
    }

    /**
     * 检查ofdrw-converter库是否可用
     */
    public boolean isOfdLibraryAvailable() {
        try {
            // 尝试加载ofdrw-converter的核心类
            Class.forName("org.ofdrw.reader.OFDReader");
            Class.forName("org.ofdrw.converter.export.OFDExporter");
            Class.forName("org.ofdrw.converter.export.ImageExporter");
            Class.forName("org.ofdrw.converter.export.PDFExporterPDFBox");
            Class.forName("org.ofdrw.converter.export.SVGExporter");
            Class.forName("org.ofdrw.converter.export.HTMLExporter");
            Class.forName("org.ofdrw.converter.export.TextExporter");
            return true;
        } catch (ClassNotFoundException e) {
            logger.warn("ofdrw-converter库不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取OFD转换服务状态信息
     */
    public String getServiceStatus() {
        Set<String> supportedFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);
        StringBuilder status = new StringBuilder();
        status.append("OFD转换服务状态:\n");
        status.append("- ofdrw-converter库可用: ").append(isOfdLibraryAvailable()).append("\n");
        status.append("- 支持的目标格式: ").append(supportedFormats).append("\n");
        status.append("- 默认转换格式: ").append(defaultTargetFormat).append("\n");
        status.append("- 并行转换阈值: 页面数 >= ").append(minPagesForParallel).append("\n");
        status.append("- 最大并行线程数: ").append(maxParallelThreads).append("\n");

        try {
            status.append("- ofdrw-converter版本: ").append(getOfdLibraryVersion()).append("\n");
        } catch (Exception e) {
            status.append("- ofdrw-converter版本: 无法获取\n");
        }

        return status.toString();
    }

    /**
     * 诊断OFD文件的页面结构和可访问性
     */
    public String diagnoseOfdFile(String filePath) {
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append("=== OFD文件诊断报告 ===\n");
        diagnosis.append("文件路径: ").append(filePath).append("\n");

        try {
            Path ofdPath = Paths.get(filePath);
            if (!Files.exists(ofdPath)) {
                diagnosis.append("错误: 文件不存在\n");
                return diagnosis.toString();
            }

            diagnosis.append("文件大小: ").append(Files.size(ofdPath)).append(" bytes\n");

            try (OFDReader reader = new OFDReader(ofdPath)) {
                int reportedPageCount = reader.getNumberOfPages();
                diagnosis.append("报告的页面数: ").append(reportedPageCount).append("\n");

                // 逐页测试访问
                diagnosis.append("\n逐页访问测试结果:\n");
                int validPageCount = 0;

                for (int pageNum = 1; pageNum <= Math.min(reportedPageCount, 10); pageNum++) { // 最多测试10页
                    try {
                        ImageMaker testMaker = new ImageMaker(reader, 5.0f);
                        BufferedImage testImage = testMaker.makePage(pageNum);

                        if (testImage != null) {
                            validPageCount = pageNum;
                            diagnosis.append(String.format("  页面 %d: ✅ 可访问 (%dx%d)\n",
                                    pageNum, testImage.getWidth(), testImage.getHeight()));
                        } else {
                            diagnosis.append(String.format("  页面 %d: ❌ 返回空图像\n", pageNum));
                            break;
                        }
                    } catch (Exception e) {
                        diagnosis.append(String.format("  页面 %d: ❌ 错误 - %s\n", pageNum, e.getMessage()));
                        break;
                    }
                }

                diagnosis.append("\n总结:\n");
                diagnosis.append("报告页面数: ").append(reportedPageCount).append("\n");
                diagnosis.append("实际可访问: ").append(validPageCount).append("\n");

                if (validPageCount < reportedPageCount) {
                    diagnosis.append("⚠️  警告: 存在不可访问的页面，建议使用串行转换模式\n");
                } else {
                    diagnosis.append("✅ 所有页面都可访问，适合使用并行转换\n");
                }

            }

        } catch (Exception e) {
            diagnosis.append("诊断失败: ").append(e.getMessage()).append("\n");
        }

        diagnosis.append("=== 诊断结束 ===\n");
        return diagnosis.toString();
    }

    /**
     * 诊断BouncyCastle版本信息，帮助排查版本兼容性问题
     */
    private void diagnoseBouncyCastleVersion() {
        try {
            logger.debug("=== BouncyCastle版本诊断开始 ===");

            // 检查运行时classpath中的BouncyCastle jar包
            try {
                ClassLoader classLoader = this.getClass().getClassLoader();
                java.net.URL providerUrl = classLoader
                        .getResource("org/bouncycastle/jce/provider/BouncyCastleProvider.class");
                logger.debug("BouncyCastle Provider类位置: {}", providerUrl);

                java.net.URL derUrl = classLoader.getResource("org/bouncycastle/asn1/DERIA5String.class");
                logger.debug("DERIA5String类位置: {}", derUrl);
            } catch (Exception e) {
                logger.debug("无法获取BouncyCastle类文件位置: {}", e.getMessage());
            }

            // 检查bcprov版本
            try {
                Class<?> providerClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
                Package pkg = providerClass.getPackage();
                String version = pkg != null ? pkg.getImplementationVersion() : "版本未知";
                logger.info("✅ BouncyCastle Provider版本: {}", version);

                // 检查DERIA5String类和方法
                Class<?> derClass = Class.forName("org.bouncycastle.asn1.DERIA5String");
                logger.debug("DERIA5String类: 可用");

                // 特别检查问题方法
                try {
                    derClass.getMethod("getInstance", Object.class);
                    logger.debug("DERIA5String.getInstance(Object)方法: ✅ 存在");
                } catch (NoSuchMethodException e) {
                    logger.error("❌ DERIA5String.getInstance(Object)方法不存在 - BouncyCastle版本不兼容!");
                }

                // 检查jar包路径
                String jarPath = providerClass.getProtectionDomain().getCodeSource().getLocation().toString();
                logger.debug("BouncyCastle Provider Jar路径: {}", jarPath);

            } catch (ClassNotFoundException e) {
                logger.error("❌ 找不到BouncyCastle类: {}", e.getMessage());
            }

            logger.debug("=== BouncyCastle版本诊断完成 ===");

        } catch (Exception e) {
            logger.error("❌ BouncyCastle版本诊断失败: {}", e.getMessage());
        }
    }

    /**
     * 获取ofdrw-converter库版本信息
     */
    private String getOfdLibraryVersion() {
        try {
            // 尝试获取版本信息
            Package pkg = OFDReader.class.getPackage();
            if (pkg != null && pkg.getImplementationVersion() != null) {
                return pkg.getImplementationVersion();
            }
            return "未知版本";
        } catch (Exception e) {
            return "版本信息不可用";
        }
    }

    /**
     * 备用的OFD转图片方法 - 跳过数字签名处理
     * 当遇到BouncyCastle兼容性问题时使用
     */
    private boolean convertOfdToImageWithFallback(String sourcePath, String targetPath, String targetFormat) {
        logger.info("使用备用方法转换OFD到图片: {} -> {}, 格式: {}", sourcePath, targetPath, targetFormat);

        try {
            // 使用最简化的转换方式，完全绕过数字签名处理
            Path ofdPath = Paths.get(sourcePath);
            File targetFile = new File(targetPath);

            // 创建临时目录
            Path tempDir = targetFile.getParentFile().toPath().resolve("temp_fallback_" + System.currentTimeMillis());
            Files.createDirectories(tempDir);

            try {
                logger.info("使用简化的OFD解析方式...");

                // 转换第一页 - 使用最简化的方式
                Path tempImagePath = tempDir.resolve("page_1." + targetFormat.toLowerCase());

                // 尝试使用最基本的ImageMaker方式，不访问可能引起数字签名问题的功能
                boolean converted = convertWithBasicImageMaker(ofdPath, tempImagePath, targetFormat);

                if (converted && Files.exists(tempImagePath) && Files.size(tempImagePath) > 0) {
                    // 复制到目标位置
                    Files.copy(tempImagePath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("备用方法转换成功: {} bytes", targetFile.length());
                    return true;
                } else {
                    logger.warn("备用方法也无法生成有效图片，可能是OFD文件本身有问题");
                    // 如果转换失败，生成错误占位符图片
                    Path errorImagePath = tempDir.resolve("error_placeholder." + targetFormat.toLowerCase());
                    if (generateErrorPlaceholderImage(errorImagePath, targetFormat, "OFD文件转换失败\n可能包含不支持的内容")) {
                        Files.copy(errorImagePath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logger.info("生成错误占位符图片作为备用: {} bytes", targetFile.length());
                        return true;
                    }
                    return false;
                }

            } finally {
                // 清理临时目录
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    logger.warn("删除临时文件失败: {}", path);
                                }
                            });
                } catch (IOException e) {
                    logger.warn("清理临时目录失败: {}", tempDir);
                }
            }

        } catch (NoSuchMethodError e) {
            logger.error("备用转换方法发生NoSuchMethodError，BouncyCastle版本不兼容: {}", e.getMessage(), e);
            // 直接生成错误占位符图片到目标路径
            try {
                File targetFile = new File(targetPath);
                Path targetFilePath = targetFile.toPath();
                return generateErrorPlaceholderImage(targetFilePath, targetFormat, "BouncyCastle版本不兼容\n该OFD文件无法解析");
            } catch (Exception ex) {
                logger.error("生成错误占位符图片也失败了: {}", ex.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("备用转换方法也失败了: {}", e.getMessage(), e);
            // 尝试生成错误占位符图片
            try {
                File targetFile = new File(targetPath);
                Path targetFilePath = targetFile.toPath();
                return generateErrorPlaceholderImage(targetFilePath, targetFormat, "OFD文件转换失败\n请检查文件格式");
            } catch (Exception ex) {
                logger.error("生成错误占位符图片也失败了: {}", ex.getMessage());
                return false;
            }
        }
    }

  

    /**
     * 使用最基本的ImageMaker进行转换，尽可能避免触发数字签名相关代码
     */
    private boolean convertWithBasicImageMaker(Path ofdPath, Path outputPath, String format) {
        try {
            logger.info("尝试使用基本的OFD读取方式...");

            // 使用最简化的方式创建OFDReader，不进行复杂的解析
            try (OFDReader reader = new OFDReader(ofdPath)) {
                logger.info("基本的OFDReader创建成功");

                // 获取页面数，但不进行深度解析
                int pageCount = 1; // 先假设只有一页，避免调用可能有问题的方法

                try {
                    pageCount = reader.getNumberOfPages();
                    logger.info("获取到页面数: {}", pageCount);
                } catch (Exception e) {
                    logger.warn("无法获取页面数，使用默认值1: {}", e.getMessage());
                }

                if (pageCount > 0) {
                    logger.info("开始创建ImageMaker...");

                    // 使用较低的DPI以减少复杂度
                    ImageMaker imageMaker = new ImageMaker(reader, 10.0f); // 降低DPI以提高性能和稳定性
                    logger.info("基本的ImageMaker创建成功");

                    // 关闭可能引起问题的配置
                    try {
                        if (imageMaker.config != null) {
                            imageMaker.config.setDrawBoundary(false);
                            logger.info("已关闭边界绘制");
                        }
                    } catch (Exception e) {
                        logger.warn("无法设置边界绘制配置，继续执行: {}", e.getMessage());
                    }

                    logger.info("开始生成第一页图片...");

                    // 使用最简单的方式生成图片，避免触发复杂的数字签名处理
                    BufferedImage image = null;
                    try {
                        // 尝试生成第一页图片
                        image = imageMaker.makePage(1); // 页码从1开始
                        logger.info("图片生成成功");
                    } catch (NoSuchMethodError e) {
                        logger.error("生成图片时发生NoSuchMethodError，确认是BouncyCastle版本不兼容: {}", e.getMessage());
                        // 直接生成错误占位符图片
                        return generateErrorPlaceholderImage(outputPath, format,
                                "该OFD文件包含数字签名\nBouncyCastle版本不兼容\n无法解析");
                    } catch (Exception e) {
                        logger.error("生成图片时发生异常，可能是数字签名问题: {}", e.getMessage());
                        // 如果仍然是BouncyCastle的问题，返回false让上层处理
                        if (e.getMessage() != null
                                && (e.getMessage().contains("DERIA5String") || e.getMessage().contains("SES_"))) {
                            logger.error("确认是BouncyCastle版本不兼容问题，该OFD文件包含数字签名且无法处理");
                            // 生成一个简单的错误图片作为占位符
                            return generateErrorPlaceholderImage(outputPath, format, "该OFD文件包含数字签名\n暂时无法解析");
                        }
                        throw e; // 其他异常继续抛出
                    }

                    if (image != null) {
                        logger.info("开始保存图片到文件: {}", outputPath);
                        ImageIO.write(image, format.toUpperCase(), outputPath.toFile());

                        if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                            logger.info("图片保存成功: {} bytes", Files.size(outputPath));
                            return true;
                        } else {
                            logger.error("图片保存失败或文件为空");
                            return false;
                        }
                    } else {
                        logger.error("生成的图片为空");
                        return false;
                    }
                } else {
                    logger.error("OFD文件没有可用页面");
                    return false;
                }
            }

        } catch (NoSuchMethodError e) {
            logger.error("基本的OFD转换发生NoSuchMethodError，BouncyCastle版本不兼容: {}", e.getMessage(), e);
            // 生成错误占位符图片
            try {
                return generateErrorPlaceholderImage(outputPath, format, "BouncyCastle版本不兼容\n该OFD文件无法解析");
            } catch (Exception ex) {
                logger.error("生成错误占位符图片也失败了: {}", ex.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("基本的OFD转换失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 生成错误占位符图片，用于无法正常转换的OFD文件
     */
    private boolean generateErrorPlaceholderImage(Path outputPath, String format, String errorMessage) {
        try {
            logger.info("生成错误占位符图片: {}", outputPath);

            // 创建一个简单的错误图片
            int width = 800;
            int height = 600;
            BufferedImage errorImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = errorImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 设置背景色
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // 设置文字色和字体
            g2d.setColor(Color.RED);
            Font font = new Font("Microsoft YaHei", Font.BOLD, 24);
            g2d.setFont(font);

            // 绘制错误信息
            FontMetrics fm = g2d.getFontMetrics();
            String[] lines = errorMessage.split("\\n");
            int y = height / 2 - (lines.length * fm.getHeight()) / 2;

            for (String line : lines) {
                int x = (width - fm.stringWidth(line)) / 2;
                g2d.drawString(line, x, y);
                y += fm.getHeight();
            }

            g2d.dispose();

            // 保存图片
            ImageIO.write(errorImage, format.toUpperCase(), outputPath.toFile());

            // 显式释放BufferedImage占用的图形资源
            errorImage.flush();

            if (Files.exists(outputPath) && Files.size(outputPath) > 0) {
                logger.info("错误占位符图片生成成功: {} bytes", Files.size(outputPath));
                return true;
            } else {
                logger.error("错误占位符图片生成失败");
                return false;
            }

        } catch (Exception e) {
            logger.error("生成错误占位符图片时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取OFD文件的页面数，增加错误处理和重试机制
     */
    private int getOfdPageCount(Path ofdPath) {
        int retryCount = 3;
        Exception lastException = null;

        for (int i = 0; i < retryCount; i++) {
            try (OFDReader reader = new OFDReader(ofdPath)) {
                int pageCount = reader.getNumberOfPages();
                logger.info("OFD文件页面数: {} (第{}次尝试)", pageCount, i + 1);

                // 验证页面数的合理性
                if (pageCount <= 0) {
                    logger.warn("获取到的页面数不合理: {}, 将重试", pageCount);
                    continue;
                }

                // 对于单页文件，跳过复杂的页面访问测试以提高性能
                if (pageCount == 1) {
                    logger.debug("单页文件，跳过复杂页面访问测试");
                    return pageCount;
                }

                // 验证页面索引的有效性（仅对多页文件）
                try {
                    // 测试访问第一页和最后一页
                    testPageAccess(reader, 1, pageCount);
                    return pageCount;
                } catch (Exception e) {
                    logger.warn("页面访问测试失败 (第{}次尝试): {}", i + 1, e.getMessage());
                    lastException = e;

                    // 如果是最后一次尝试，降级处理
                    if (i == retryCount - 1) {
                        logger.warn("页面访问测试多次失败，尝试使用串行模式获取页面数");
                        return getPageCountWithSerialAccess(reader, pageCount);
                    }
                }

            } catch (Exception e) {
                logger.warn("无法获取OFD文件页面数 (第{}次尝试): {}", i + 1, e.getMessage());
                lastException = e;

                // 添加短暂延迟后重试
                if (i < retryCount - 1) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.error("多次尝试后仍无法获取OFD文件页面数，使用默认值1", lastException);
        return 1;
    }

    /**
     * 测试页面访问的有效性
     * 注意：OFDRW的makePage使用0基索引（第一页是0，第二页是1）
     */
    private void testPageAccess(OFDReader reader, int startPage, int totalPages) throws Exception {
        // 重用ImageMaker实例，而不是每次都创建新的
        ImageMaker testMaker = new ImageMaker(reader, 5.0f);

        // 测试第一页 (使用0基索引)
        if (totalPages >= 1) {
            try {
                BufferedImage testImage = testMaker.makePage(0); // 第一页索引是0
                if (testImage == null) {
                    throw new Exception("第1页返回null图像");
                }
                // 显式释放BufferedImage资源
                testImage.flush();
                logger.debug("第1页访问测试通过 (索引0)");
            } catch (Exception e) {
                logger.debug("第1页访问测试失败 (索引0): {}", e.getMessage());
                throw e;
            }
        }

        // 如果页面数大于1，测试最后一页
        if (totalPages > 1) {
            try {
                BufferedImage testImage = testMaker.makePage(totalPages - 1); // 最后一页索引是totalPages-1
                if (testImage == null) {
                    throw new Exception("第" + totalPages + "页返回null图像");
                }
                // 显式释放BufferedImage资源
                testImage.flush();
                logger.debug("第{}页访问测试通过 (索引{})", totalPages, totalPages - 1);
            } catch (Exception e) {
                logger.debug("第{}页访问测试失败 (索引{}): {}", totalPages, totalPages - 1, e.getMessage());
                // 最后一页访问失败时，尝试逐页验证找到实际的最大页面数
                logger.warn("最后一页访问失败，开始逐页验证实际页面数");
                throw new Exception("需要串行验证页面数: " + e.getMessage());
            }
        }
    }

    /**
     * 通过串行访问的方式确定实际可访问的页面数
     * 注意：使用0基索引进行测试
     */
    private int getPageCountWithSerialAccess(OFDReader reader, int reportedPageCount) {
        logger.info("使用串行访问模式验证页面数，报告的页面数: {}", reportedPageCount);

        int actualPageCount = 0;

        // 重用ImageMaker实例，而不是每次都创建新的
        ImageMaker testMaker = new ImageMaker(reader, 5.0f);

        // 使用0基索引进行测试（第一页是0，第二页是1）
        for (int pageIndex = 0; pageIndex < reportedPageCount; pageIndex++) {
            try {
                BufferedImage testImage = testMaker.makePage(pageIndex);
                if (testImage != null) {
                    actualPageCount = pageIndex + 1; // 实际页面数 = 索引 + 1
                    // 显式释放BufferedImage资源
                    testImage.flush();
                    logger.debug("页面 {} (索引{}) 可访问", actualPageCount, pageIndex);
                } else {
                    logger.warn("页面 {} (索引{}) 返回null，停止检查", pageIndex + 1, pageIndex);
                    break;
                }
            } catch (Exception e) {
                logger.warn("页面 {} (索引{}) 访问失败: {}", pageIndex + 1, pageIndex, e.getMessage());
                // 检查是否是索引超出范围的错误
                if (e.getMessage() != null && (e.getMessage().contains("不是有效索引") || e.getMessage().contains("invalid")
                        || e.getMessage().contains("index"))) {
                    logger.info("检测到索引超出范围错误，实际页面数为: {}", actualPageCount);
                }
                break;
            }
        }

        logger.info("串行验证完成，实际可访问页面数: {} (报告: {})", actualPageCount, reportedPageCount);
        return Math.max(actualPageCount, 1); // 至少返回1页
    }

    /**
     * 串行转换模式，添加中文支持和文件夹结构
     */
    private boolean convertOfdToImageSerialWithChineseSupportAndFolderStructure(Path ofdPath, File targetFile,
            String targetFormat, String sourceFileName) {
        logger.info("使用串行模式转换OFD到图片（中文支持+文件夹结构）");

        // 创建临时目录用于存放生成的图片
        Path tempDir = targetFile.getParentFile().toPath()
                .resolve("temp_img_serial_folder_" + System.currentTimeMillis());

        try {
            Files.createDirectories(tempDir);

            // 在子进程中设置正确的环境变量
            ProcessBuilder pb = new ProcessBuilder();
            setupChineseEnvironmentForProcess(pb);

            try (ImageExporter exporter = new ImageExporter(ofdPath, tempDir, targetFormat, 20.0)) {
                logger.info("ImageExporter创建成功，开始执行导出...");

                // 配置ImageExporter以支持中文
                configureImageExporterForChinese(exporter);

                // 执行导出
                exporter.export();

                // 获取生成的图片文件路径
                List<Path> imgFilePaths = exporter.getImgFilePaths();

                if (imgFilePaths == null || imgFilePaths.isEmpty()) {
                    logger.error("ImageExporter未生成任何图片文件");
                    return false;
                }

                // 单页文件直接复制到目标位置
                if (imgFilePaths.size() == 1) {
                    Path firstImage = imgFilePaths.get(0);
                    if (!Files.exists(firstImage)) {
                        logger.error("生成的图片文件不存在: {}", firstImage);
                        return false;
                    }

                    Files.copy(firstImage, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("单页串行转换成功: {} bytes", targetFile.length());
                    return true;
                }

                // 多页文件：创建以文件名命名的文件夹
                File imagesFolder = new File(targetFile.getParentFile(), sourceFileName);
                if (!imagesFolder.exists()) {
                    if (!imagesFolder.mkdirs()) {
                        logger.error("无法创建图片存放文件夹: {}", imagesFolder.getAbsolutePath());
                        return false;
                    }
                }

                logger.info("多页文件，将所有图片存放到文件夹: {}", imagesFolder.getAbsolutePath());

                // 将所有页面图片复制到文件夹中
                int copiedPages = 0;
                for (int i = 0; i < imgFilePaths.size(); i++) {
                    Path sourcePage = imgFilePaths.get(i);
                    if (Files.exists(sourcePage)) {
                        String pageFileName = String.format("page_%d.%s", i + 1, targetFormat.toLowerCase());
                        Path targetPagePath = imagesFolder.toPath().resolve(pageFileName);
                        Files.copy(sourcePage, targetPagePath, StandardCopyOption.REPLACE_EXISTING);
                        copiedPages++;
                        logger.debug("复制第{}页图片到: {}", i + 1, targetPagePath);
                    }
                }

                // 将第一页也复制到原始目标位置（兼容性）
                if (!imgFilePaths.isEmpty() && Files.exists(imgFilePaths.get(0))) {
                    Path firstPageSource = imgFilePaths.get(0);
                    logger.info("准备复制第一页到原始目标位置...");
                    logger.info("- 源文件: {} (大小: {} bytes)", firstPageSource, Files.size(firstPageSource));
                    logger.info("- 目标文件: {}", targetFile.getAbsolutePath());

                    Files.copy(firstPageSource, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // 验证复制结果
                    if (targetFile.exists() && targetFile.length() > 0) {
                        logger.info("✅ 第一页复制到原始目标位置成功: {} (大小: {} bytes)", targetFile.getAbsolutePath(),
                                targetFile.length());
                    } else {
                        logger.error("❌ 第一页复制到原始目标位置失败或文件为空");
                    }
                }

                logger.info("多页串行转换成功: 在文件夹中生成了{}个图片文件", copiedPages);
                return true;
            }

        } catch (Exception e) {
            logger.error("串行转换失败: {}", e.getMessage(), e);
            return false;
        } finally {
            // 清理临时目录
            cleanupTempDirectory(tempDir);
        }
    }

    /**
     * 并行转换模式，添加中文支持和页面索引验证和文件夹结构
     */
    private boolean convertOfdToImageParallelWithChineseSupportAndFolderStructure(Path ofdPath, File targetFile,
            String targetFormat, int pageCount, String sourceFileName) {
        logger.info("使用并行模式转换OFD到图片（中文支持+文件夹结构），总页数: {}", pageCount);

        long startTime = System.currentTimeMillis();
        ExecutorService executor = getParallelConversionPool();

        // 创建临时目录
        Path tempDir = targetFile.getParentFile().toPath()
                .resolve("temp_img_parallel_folder_" + System.currentTimeMillis());

        try {
            Files.createDirectories(tempDir);

            // 再次验证页面数的有效性
            int validatedPageCount = validateAndAdjustPageCount(ofdPath, pageCount);
            if (validatedPageCount != pageCount) {
                logger.warn("页面数已调整: {} -> {}", pageCount, validatedPageCount);
            }

            // 使用final变量以便在Lambda中引用
            final int finalPageCount = validatedPageCount > 0 ? validatedPageCount : pageCount;

            // 额外的安全检查：确保至少转换第一页
            if (finalPageCount <= 0) {
                logger.error("无效的页面数: {}，但文件存在，强制设置为1页", finalPageCount);
                final int safeFinalPageCount = 1;

                logger.info("开始创建{}个并行转换任务（安全模式）", safeFinalPageCount);

                // 创建并行任务列表 - 使用更安全的方式
                List<CompletableFuture<PageConversionResult>> futures = new ArrayList<>();
                for (int pageNum = 1; pageNum <= safeFinalPageCount; pageNum++) {
                    final int currentPageNum = pageNum;
                    CompletableFuture<PageConversionResult> future = CompletableFuture
                            .supplyAsync(() -> convertSinglePageWithValidation(ofdPath, tempDir, targetFormat,
                                    currentPageNum, safeFinalPageCount), executor);
                    futures.add(future);
                }

                // 等待所有任务完成
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));

                // 设置超时，防止无限等待
                allTasks.get(120, TimeUnit.SECONDS); // 增加到120秒超时，给多页转换更多时间

                // 收集结果
                List<PageConversionResult> results = futures.stream()
                        .map(future -> {
                            try {
                                return future.get();
                            } catch (Exception e) {
                                logger.error("获取任务结果失败: {}", e.getMessage());
                                return new PageConversionResult(-1, false, null, e.getMessage());
                            }
                        })
                        .filter(result -> result.pageNumber > 0) // 过滤无效结果
                        .sorted((a, b) -> Integer.compare(a.pageNumber, b.pageNumber))
                        .toList();

                // 检查是否有成功的页面
                if (!results.isEmpty() && results.get(0).success) {
                    // 复制第一页到目标位置
                    PageConversionResult firstPage = results.get(0);
                    Files.copy(firstPage.outputPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("安全模式转换成功: 第{}页", firstPage.pageNumber);
                    return true;
                } else {
                    logger.error("安全模式转换也失败了");
                    return false;
                }
            }

            logger.info("开始创建{}个并行转换任务", finalPageCount);

            // 创建并行任务列表 - 使用更安全的方式
            List<CompletableFuture<PageConversionResult>> futures = new ArrayList<>();
            for (int pageNum = 1; pageNum <= finalPageCount; pageNum++) {
                final int currentPageNum = pageNum;
                CompletableFuture<PageConversionResult> future = CompletableFuture
                        .supplyAsync(() -> convertSinglePageWithValidation(ofdPath, tempDir, targetFormat,
                                currentPageNum, finalPageCount), executor);
                futures.add(future);
            }

            // 等待所有任务完成
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            // 设置超时，防止无限等待
            allTasks.get(120, TimeUnit.SECONDS); // 增加到120秒超时，给多页转换更多时间

            // 收集结果
            List<PageConversionResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            logger.error("获取任务结果失败: {}", e.getMessage());
                            return new PageConversionResult(-1, false, null, e.getMessage());
                        }
                    })
                    .filter(result -> result.pageNumber > 0) // 过滤无效结果
                    .sorted((a, b) -> Integer.compare(a.pageNumber, b.pageNumber))
                    .toList();

            // 检查是否有失败的页面
            long failedPages = results.stream().filter(r -> !r.success).count();
            long successPages = results.stream().filter(r -> r.success).count();

            logger.info("并行转换结果: 成功{}页, 失败{}页, 总计{}页", successPages, failedPages, results.size());

            if (successPages == 0) {
                logger.error("所有页面转换失败");
                return false;
            }

            if (failedPages > 0) {
                logger.warn("部分页面转换失败，但将继续处理成功的页面");

                // 记录失败的页面
                results.stream()
                        .filter(r -> !r.success)
                        .forEach(r -> logger.warn("失败页面: {} - {}", r.pageNumber, r.errorMessage));
            }

            // 获取成功的结果
            List<PageConversionResult> successResults = results.stream()
                    .filter(r -> r.success && r.outputPath != null && Files.exists(r.outputPath))
                    .sorted((a, b) -> Integer.compare(a.pageNumber, b.pageNumber))
                    .toList();

            if (successResults.isEmpty()) {
                logger.error("没有成功转换的页面");
                return false;
            }

            // 单页文件直接复制到目标位置
            if (successResults.size() == 1) {
                PageConversionResult firstPage = successResults.get(0);
                Files.copy(firstPage.outputPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("单页并行转换成功: 第{}页", firstPage.pageNumber);
                return true;
            }

            // 多页文件：创建以文件名命名的文件夹
            File imagesFolder = new File(targetFile.getParentFile(), sourceFileName);
            if (!imagesFolder.exists()) {
                if (!imagesFolder.mkdirs()) {
                    logger.error("无法创建图片存放文件夹: {}", imagesFolder.getAbsolutePath());
                    return false;
                }
            }

            logger.info("多页文件，将所有图片存放到文件夹: {}", imagesFolder.getAbsolutePath());

            // 将所有成功转换的页面复制到文件夹中
            int copiedPages = 0;
            for (PageConversionResult result : successResults) {
                try {
                    String pageFileName = String.format("page_%d.%s", result.pageNumber, targetFormat.toLowerCase());
                    Path targetPagePath = imagesFolder.toPath().resolve(pageFileName);
                    Files.copy(result.outputPath, targetPagePath, StandardCopyOption.REPLACE_EXISTING);
                    copiedPages++;
                    logger.debug("复制第{}页图片到: {}", result.pageNumber, targetPagePath);
                } catch (Exception e) {
                    logger.warn("复制第{}页失败: {}", result.pageNumber, e.getMessage());
                }
            }

            // 将第一页也复制到原始目标位置（兼容性）
            if (!successResults.isEmpty()) {
                PageConversionResult firstPage = successResults.get(0);
                logger.info("准备复制第一页到原始目标位置...");
                logger.info("- 源文件: {} (大小: {} bytes)", firstPage.outputPath, Files.size(firstPage.outputPath));
                logger.info("- 目标文件: {}", targetFile.getAbsolutePath());

                Files.copy(firstPage.outputPath, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 验证复制结果
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("✅ 第一页复制到原始目标位置成功: {} (大小: {} bytes)", targetFile.getAbsolutePath(),
                            targetFile.length());
                } else {
                    logger.error("❌ 第一页复制到原始目标位置失败或文件为空");
                }
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            logger.info("多页并行转换成功: 在文件夹中生成了{}个图片文件，耗时: {}ms，成功转换{}/{}页",
                    copiedPages, duration, successPages, finalPageCount);

            return true;

        } catch (TimeoutException e) {
            logger.error("并行转换超时: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("并行转换失败: {}", e.getMessage(), e);
            return false;
        } finally {
            // 清理临时目录
            cleanupTempDirectory(tempDir);
        }
    }

    /**
     * 验证并调整页面数，增加更严格的验证
     */
    private int validateAndAdjustPageCount(Path ofdPath, int reportedPageCount) {
        logger.info("开始验证页面数: {}", reportedPageCount);

        try (OFDReader reader = new OFDReader(ofdPath)) {
            // 再次获取页面数确认
            int actualPageCount = reader.getNumberOfPages();
            if (actualPageCount != reportedPageCount) {
                logger.warn("页面数不一致: 报告={}, 实际={}", reportedPageCount, actualPageCount);
                reportedPageCount = actualPageCount;
            }

            // 对于多页文件，进行更严格的验证
            if (reportedPageCount > 1) {
                logger.info("对{}页文件进行严格验证", reportedPageCount);

                // 逐页验证每个页面的可访问性
                int validatedPageCount = validateEachPageAccess(reader, reportedPageCount);

                if (validatedPageCount != reportedPageCount) {
                    logger.warn("经验证后的实际页面数: {} (原报告: {})", validatedPageCount, reportedPageCount);
                    // 确保至少返回1页，即使验证失败
                    return Math.max(validatedPageCount, 1);
                }
            }

            return reportedPageCount;
        } catch (Exception e) {
            logger.warn("验证页面数失败，使用原始值: {}", e.getMessage());
            return reportedPageCount;
        }
    }

    /**
     * 逐页验证每个页面的可访问性
     */
    private int validateEachPageAccess(OFDReader reader, int reportedPageCount) {
        logger.info("开始逐页验证{}个页面的可访问性", reportedPageCount);

        int validPageCount = 0;
        int consecutiveFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 2; // 最多允许连续2次失败

        for (int pageNum = 1; pageNum <= reportedPageCount; pageNum++) {
            try {
                // 使用低分辨率进行快速验证
                ImageMaker testMaker = new ImageMaker(reader, 3.0f); // 更低的DPI加快验证
                BufferedImage testImage = testMaker.makePage(pageNum);

                if (testImage != null && testImage.getWidth() > 0 && testImage.getHeight() > 0) {
                    validPageCount = pageNum;
                    consecutiveFailures = 0; // 重置连续失败计数
                    // 显式释放BufferedImage资源
                    testImage.flush();
                    logger.debug("页面 {} 验证通过 ({}x{})", pageNum, testImage.getWidth(), testImage.getHeight());
                } else {
                    logger.warn("页面 {} 验证失败: 图像为空或尺寸为0", pageNum);
                    consecutiveFailures++;
                    // 如果是第一页失败，给予更多宽容，继续尝试第二页
                    if (pageNum == 1) {
                        logger.warn("第一页验证失败，但继续验证后续页面");
                        continue;
                    }
                    // 如果连续失败次数过多，停止验证
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        logger.warn("连续{}次验证失败，停止验证", consecutiveFailures);
                        break;
                    }
                }

            } catch (org.ofdrw.converter.GeneralConvertException e) {
                if (e.getMessage() != null && e.getMessage().contains("不是有效索引")) {
                    logger.info("页面 {} 索引超出范围，实际最大页面数为: {}", pageNum, validPageCount);
                } else {
                    logger.warn("页面 {} OFDRW转换异常: {}", pageNum, e.getMessage());
                }
                consecutiveFailures++;
                // 如果是第一页失败，给予更多宽容
                if (pageNum == 1) {
                    logger.warn("第一页OFDRW异常，但继续验证后续页面: {}", e.getMessage());
                    continue;
                }
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    break;
                }
            } catch (Exception e) {
                logger.warn("页面 {} 验证失败: {}", pageNum, e.getMessage());
                consecutiveFailures++;
                // 如果是第一页失败，给予更多宽容
                if (pageNum == 1) {
                    logger.warn("第一页验证异常，但继续验证后续页面: {}", e.getMessage());
                    continue;
                }
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    break;
                }
            }
        }

        // 如果没有任何页面验证成功，但文件确实存在，则假设至少有1页
        if (validPageCount == 0 && reportedPageCount > 0) {
            logger.warn("所有页面验证都失败，但文件存在，假设至少有1页可用");
            validPageCount = 1;
        }

        logger.info("逐页验证完成，有效页面数: {} / {}", validPageCount, reportedPageCount);
        return validPageCount;
    }

    /**
     * 带验证的单页转换方法
     */
    private PageConversionResult convertSinglePageWithValidation(Path ofdPath, Path tempDir, String targetFormat,
            int pageNumber, int totalPages) {
        try {
            logger.debug("开始转换第{}/{}页（验证模式）", pageNumber, totalPages);

            // 页面索引验证 - 对第一页给予更多宽容
            if (pageNumber < 1) {
                String errorMsg = String.format("页面索引不能小于1: %d", pageNumber);
                logger.error(errorMsg);
                return new PageConversionResult(pageNumber, false, null, errorMsg);
            }

            // 对于非第一页，检查索引超出范围
            if (pageNumber > 1 && pageNumber > totalPages) {
                String errorMsg = String.format("页面索引超出范围: %d (有效范围: 1-%d)", pageNumber, totalPages);
                logger.error(errorMsg);
                return new PageConversionResult(pageNumber, false, null, errorMsg);
            }

            // 生成该页的临时文件名
            String pageFileName = String.format("page_%d.%s", pageNumber, targetFormat.toLowerCase());
            Path pageOutputPath = tempDir.resolve(pageFileName);

            // 使用独立的OFDReader实例避免并发问题
            try (OFDReader reader = new OFDReader(ofdPath)) {
                // 再次验证页面数 - 但对第一页给予更多宽容
                int currentTotalPages = reader.getNumberOfPages();
                if (pageNumber > 1 && pageNumber > currentTotalPages) {
                    String errorMsg = String.format("页面索引超出范围: %d > %d (当前文件页数)", pageNumber, currentTotalPages);
                    logger.error(errorMsg);
                    return new PageConversionResult(pageNumber, false, null, errorMsg);
                } else if (pageNumber == 1 && currentTotalPages <= 0) {
                    logger.warn("文件报告的页数为{}，但仍尝试转换第一页", currentTotalPages);
                }

                // 对于单页文件，直接进行转换，避免额外的页面测试
                if (currentTotalPages == 1) {
                    // 使用ImageMaker转换指定页面
                    ImageMaker imageMaker = new ImageMaker(reader, 15.0f);

                    // 配置ImageMaker以支持中文渲染
                    configureImageMakerForChinese(imageMaker);

                    // 生成指定页面的图片
                    BufferedImage image = imageMaker.makePage(pageNumber);
                    if (image != null) {
                        // 保存图片
                        ImageIO.write(image, targetFormat.toUpperCase(), pageOutputPath.toFile());
                        // 显式释放BufferedImage资源
                        image.flush();

                        long fileSize = Files.size(pageOutputPath);
                        logger.debug("第{}页转换成功: {} bytes", pageNumber, fileSize);
                        return new PageConversionResult(pageNumber, true, pageOutputPath, null);
                    } else {
                        String errorMsg = "生成的图片为空";
                        logger.error("第{}页{}", pageNumber, errorMsg);
                        return new PageConversionResult(pageNumber, false, null, errorMsg);
                    }
                } else {
                    // 多页文件保持原有逻辑
                    // 使用ImageMaker转换指定页面
                    ImageMaker imageMaker = new ImageMaker(reader, 15.0f);

                    // 配置ImageMaker以支持中文渲染
                    configureImageMakerForChinese(imageMaker);

                    // 生成指定页面的图片
                    BufferedImage image = imageMaker.makePage(pageNumber);
                    if (image != null) {
                        // 保存图片
                        ImageIO.write(image, targetFormat.toUpperCase(), pageOutputPath.toFile());
                        // 显式释放BufferedImage资源
                        image.flush();

                        long fileSize = Files.size(pageOutputPath);
                        logger.debug("第{}页转换成功: {} bytes", pageNumber, fileSize);
                        return new PageConversionResult(pageNumber, true, pageOutputPath, null);
                    } else {
                        String errorMsg = "生成的图片为空";
                        logger.error("第{}页{}", pageNumber, errorMsg);
                        return new PageConversionResult(pageNumber, false, null, errorMsg);
                    }
                }
            }

        } catch (org.ofdrw.converter.GeneralConvertException e) {
            // 专门处理OFDRW转换异常
            String errorMsg = "页面索引不合法: " + e.getMessage();
            logger.error("第{}页转换失败 (OFDRW错误): {}", pageNumber, errorMsg);

            // 记录更多诊断信息
            logger.error("诊断信息 - 页面索引: {}, 总页数: {}, 错误类型: GeneralConvertException", pageNumber, totalPages);

            return new PageConversionResult(pageNumber, false, null, errorMsg);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            logger.error("第{}页转换失败: {}", pageNumber, errorMsg, e);
            return new PageConversionResult(pageNumber, false, null, errorMsg);
        }
    }

    /**
     * 为进程设置中文环境变量
     */
    private void setupChineseEnvironmentForProcess(ProcessBuilder pb) {
        java.util.Map<String, String> env = pb.environment();
        env.put("LANG", "en_US.UTF-8");
        env.put("LC_ALL", "en_US.UTF-8");
        env.put("LC_CTYPE", "en_US.UTF-8");
        env.put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8");
    }

    /**
     * 配置ImageExporter以支持中文
     */
    private void configureImageExporterForChinese(ImageExporter exporter) {
        try {
            // 尝试设置字体配置（如果ImageExporter支持的话）
            logger.debug("配置ImageExporter中文支持");
            // 注：具体的配置方法取决于OFDRW库的API
        } catch (Exception e) {
            logger.debug("配置ImageExporter中文支持时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 配置ImageMaker以支持中文渲染
     */
    private void configureImageMakerForChinese(ImageMaker imageMaker) {
        try {
            // 关闭可能引起问题的配置
            if (imageMaker.config != null) {
                imageMaker.config.setDrawBoundary(false);
                logger.debug("已关闭边界绘制");
            }

            // 设置渲染提示以支持中文字体
            logger.debug("配置ImageMaker中文字体支持");

        } catch (Exception e) {
            logger.debug("配置ImageMaker中文支持时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 清理临时目录
     */
    private void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a)) // 先删除文件，再删除目录
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                logger.warn("删除临时文件失败: {}", path);
                            }
                        });
            }
        } catch (IOException e) {
            logger.warn("清理临时目录失败: {}", tempDir);
        }
    }

    /**
     * 页面转换结果封装类
     */
    private static class PageConversionResult {
        final int pageNumber;
        final boolean success;
        final Path outputPath;
        final String errorMessage;

        PageConversionResult(int pageNumber, boolean success, Path outputPath, String errorMessage) {
            this.pageNumber = pageNumber;
            this.success = success;
            this.outputPath = outputPath;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 强制使用串行模式转换OFD到图片（用于调试对比）
     */
    public boolean convertWithSerialMode(String sourcePath, String targetPath, String targetFormat) {
        logger.info("强制使用串行模式转换OFD到图片: {} -> {}, 格式: {}", sourcePath, targetPath, targetFormat);

        try {
            // 确保中文环境已初始化
            EnvironmentUtils.initializeChineseEnvironment();

            Path sourcePath_p = Paths.get(sourcePath);
            File targetFile = new File(targetPath);

            // 获取原始文件名（不含扩展名）用于创建文件夹
            String sourceFileName = getBaseName(Paths.get(sourcePath).getFileName().toString());

            // 确保目标目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            logger.info("强制使用串行转换模式");
            return convertOfdToImageSerialWithChineseSupportAndFolderStructure(sourcePath_p, targetFile, targetFormat,
                    sourceFileName);

        } catch (Exception e) {
            logger.error("强制串行转换失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 强制使用并行模式转换OFD到图片（用于调试对比）
     */
    public boolean convertWithParallelMode(String sourcePath, String targetPath, String targetFormat) {
        logger.info("强制使用并行模式转换OFD到图片: {} -> {}, 格式: {}", sourcePath, targetPath, targetFormat);

        try {
            // 确保中文环境已初始化
            EnvironmentUtils.initializeChineseEnvironment();

            Path sourcePath_p = Paths.get(sourcePath);
            File targetFile = new File(targetPath);

            // 获取原始文件名（不含扩展名）用于创建文件夹
            String sourceFileName = getBaseName(Paths.get(sourcePath).getFileName().toString());

            // 确保目标目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("无法创建目标目录: {}", parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 获取页面数
            int pageCount = getOfdPageCount(sourcePath_p);
            logger.info("强制使用并行转换模式，页面数: {}", pageCount);
            return convertOfdToImageParallelWithChineseSupportAndFolderStructure(sourcePath_p, targetFile, targetFormat,
                    pageCount, sourceFileName);

        } catch (Exception e) {
            logger.error("强制并行转换失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 直接让 OFDRW 扫描字体目录
     * OFDRW 使用 Font.createFont() 扫描字体，即使在 headless 模式下也能正常工作
     */
    private void loadSourceHanFontsToOFDRW(FontLoader fontLoader) {
        try {
            logger.info("📝 开始让 OFDRW 加载字体...");
            
            // 从配置或默认路径获取字体目录（委托给 FontUtils 统一处理）
            List<String> scanPaths = FontUtils.getFontDirectories(ofdFontProperties.getScanPaths());
            
            // 1. 扫描 TTF 字体（OFDRW 自动处理）
            logger.info("📁 扫描 TTF 字体目录...");
            for (String fontDir : scanPaths) {
                File dir = new File(fontDir);
                if (dir.exists() && dir.isDirectory()) {
                    fontLoader.scanFontDir(dir);
                }
            }
            
            // 2. 手动添加 OTF 字体（OFDRW 的 Font.createFont 可能无法解析 OTF）
            logger.info("📝 手动添加思源 OTF 字体映射...");
            int otfCount = 0;
            for (String fontDir : scanPaths) {
                File dir = new File(fontDir);
                if (!dir.exists() || !dir.isDirectory()) continue;
                
                File[] otfFiles = dir.listFiles((d, name) -> 
                    name.toLowerCase().endsWith(".otf") && 
                    (name.contains("SourceHan") || name.contains("思源")));
                    
                if (otfFiles != null) {
                    for (File otfFile : otfFiles) {
                        try {
                            // 使用 AWT Font 解析字体名
                            Font awtFont = Font.createFont(Font.TRUETYPE_FONT, otfFile);
                            String fontName = awtFont.getFontName();
                            String family = awtFont.getFamily();
                            
                            // 添加到 OFDRW 映射
                            fontLoader.addSystemFontMapping(fontName, otfFile.getAbsolutePath());
                            if (!fontName.equals(family)) {
                                fontLoader.addSystemFontMapping(family, otfFile.getAbsolutePath());
                            }
                            otfCount++;
                        } catch (Exception e) {
                            // 跳过解析失败的 OTF 字体
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("OFDRW 字体加载失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载系统字体到LRU缓存
     */
    private void loadSystemFontsToCache() {
        try {
            Set<String> scannedFonts = FontUtils.scanAvailableFonts(ofdFontProperties.getScanPaths());
            for (String font : scannedFonts) {
                availableFonts.put(font, Boolean.TRUE);
            }
            logger.info("已加载 {} 个系统字体到LRU缓存", availableFonts.size());
        } catch (Exception e) {
            logger.warn("加载系统字体失败: {}", e.getMessage());
        }
    }

    /**
     * 获取系统可用字体集合（从 LRU 缓存）
     */
    private Set<String> getAvailableFonts() {
        return availableFonts.keySet();
    }

    /**
     * 提取并记录OFD文件中实际使用的字体（用于诊断）
     * 直接使用ZIP解析，最简单可靠
     */
    private void extractAndLogOfdFonts(String ofdFilePath) {
        logger.info("🔍 开始提取OFD文件字体信息...");
        
        Set<String> usedFonts = new HashSet<>();
        
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(ofdFilePath)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            int xmlFileCount = 0;
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 处理所有XML文件（PublicRes.xml包含字体定义）
                if (entryName.toLowerCase().endsWith(".xml")) {
                    
                    try (java.io.InputStream is = zipFile.getInputStream(entry);
                         java.io.BufferedReader reader = new java.io.BufferedReader(
                                 new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                        
                        StringBuilder content = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line);
                        }
                        
                        extractFontsFromXmlContent(content.toString(), usedFonts);
                        xmlFileCount++;
                    } catch (Exception e) {
                        logger.debug("读取XML文件失败: {} - {}", entryName, e.getMessage());
                    }
                }
            }
            
            logger.info("📊 已扫描 {} 个XML文件", xmlFileCount);
            
            // 输出OFD中实际使用的字体
            if (!usedFonts.isEmpty()) {
                logger.info("📝 OFD文件中使用的字体（共 {} 个）:", usedFonts.size());
                usedFonts.forEach(font -> logger.info("   - {}", font));
                
                // 检查未映射的字体
                checkUnmappedFonts(usedFonts);
            } else {
                logger.info("🤔 未能提取到字体信息，可能是嵌入式字体或OFD结构特殊");
            }
            
        } catch (Exception e) {
            logger.warn("⚠️  提取OFD字体信息失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            logger.debug("详细错误", e);
        }
    }
    
    /**
     * 从XML内容中提取字体名称
     */
    private void extractFontsFromXmlContent(String xmlContent, Set<String> usedFonts) {
        if (xmlContent == null || xmlContent.isEmpty()) return;
        
        try {
            // 提取 FontName 属性（最常见格式）: <ofd:Font FontName="宋体"/>
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("FontName=\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(xmlContent);
            while (matcher.find()) {
                String fontName = matcher.group(1).trim();
                if (!fontName.isEmpty()) {
                    usedFonts.add(fontName);
                }
            }
            
            // 提取 FamilyName 属性（备用）
            pattern = java.util.regex.Pattern.compile("FamilyName=\"([^\"]+)\"");
            matcher = pattern.matcher(xmlContent);
            while (matcher.find()) {
                String fontName = matcher.group(1).trim();
                if (!fontName.isEmpty()) {
                    usedFonts.add(fontName);
                }
            }
            
            // 提取 FontName 标签内容（兼容格式）: <FontName>宋体</FontName>
            pattern = java.util.regex.Pattern.compile("<FontName>([^<]+)</FontName>");
            matcher = pattern.matcher(xmlContent);
            while (matcher.find()) {
                String fontName = matcher.group(1).trim();
                if (!fontName.isEmpty()) {
                    usedFonts.add(fontName);
                }
            }
        } catch (Exception e) {
            // 忽略字体解析异常，避免影响主流程
        }
    }
    
    /**
     * 检查未映射的字体
     */
    private void checkUnmappedFonts(Set<String> usedFonts) {
        try {
            Set<String> unmappedFonts = new HashSet<>();
            Set<String> availableFonts = getAvailableFonts();

            for (String font : usedFonts) {
                // 1. 先看系统/扫描目录中是否已有该字体
                boolean foundInSystem = availableFonts.stream()
                        .anyMatch(available -> available.equalsIgnoreCase(font));

                // 2. 再看是否有名字上的部分匹配（例如“宋体 Regular” vs “宋体”）
                boolean partialMatch = availableFonts.stream()
                        .anyMatch(available -> available.toLowerCase().contains(font.toLowerCase())
                                || font.toLowerCase().contains(available.toLowerCase()));

                if (!foundInSystem && !partialMatch) {
                    unmappedFonts.add(font);
                }
            }

            if (!unmappedFonts.isEmpty()) {
                FontLoader fontLoader = FontLoader.getInstance();
                for (String font : unmappedFonts) {
                    String fallbackTarget = guessFallbackFont(font);
                    try {
                        // 这里的别名映射仅作为“缺失字体 → 思源兜底”使用，不再处理其它映射策略
                        fontLoader.addAliasMapping(font, fallbackTarget);
                    } catch (Exception e) {
                        logger.warn("为字体 '{}' 应用兜底映射失败: {}", font, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查字体兜底映射失败: {}", e.getMessage());
        }
    }

    /**
     * 兜底：根据字体名猜测使用思源宋体或思源黑体
     */
    private String guessFallbackFont(String fontName) {
        return FontUtils.guessFallbackFont(fontName);
    }

}
