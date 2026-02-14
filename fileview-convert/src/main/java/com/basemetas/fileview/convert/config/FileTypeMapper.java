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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 文件类型映射器
 * 统一管理文件扩展名到文件类别及转换策略的映射关系
 * 
 * 职责：
 * 1. 维护文件扩展名与文件类别的映射关系
 * 2. 提供扩展名提取功能（支持特殊格式如tar.gz）
 * 3. 支持动态注册新的文件类型映射
 * 4. 提供查询接口获取支持的文件类型信息
 */
@Component
public class FileTypeMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(FileTypeMapper.class);
    
    /**
     * 扩展名到文件类别的映射
     */
    private final Map<String, FileCategory> extensionToCategoryMap = new HashMap<>();
    
    /**
     * 文件类别到扩展名集合的映射（反向索引）
     */
    private final Map<FileCategory, Set<String>> categoryToExtensionsMap = new EnumMap<>(FileCategory.class);
    
    /**
     * 特殊扩展名集合（如tar.gz, tgz等复合扩展名）
     */
    private final Set<String> specialExtensions = new HashSet<>();
    
    /**
     * 文件类别到支持的源格式映射（格式能力注册）
     */
    private final Map<FileCategory, Set<String>> supportedSourceFormatsMap = new EnumMap<>(FileCategory.class);
    
    /**
     * 文件类别到支持的目标格式映射（格式能力注册）
     */
    private final Map<FileCategory, Set<String>> supportedTargetFormatsMap = new EnumMap<>(FileCategory.class);
    
    public FileTypeMapper() {
        initializeDefaultMappings();
    }
    
    /**
     * Spring初始化后的回调，用于日志输出
     */
    @PostConstruct
    public void postInit() {
        // logger.info("========== 文件类型映射器初始化完成 ==========");
        // logger.info("支持的文件类别总数: {}", categoryToExtensionsMap.size());
        // logger.info("支持的扩展名总数: {}", extensionToCategoryMap.size());
        
        // // 输出每个类别的格式能力
        // for (FileCategory category : FileCategory.values()) {
        //     Set<String> extensions = categoryToExtensionsMap.get(category);
        //     Set<String> sourceFormats = supportedSourceFormatsMap.get(category);
        //     Set<String> targetFormats = supportedTargetFormatsMap.get(category);
            
        //     if (extensions != null && !extensions.isEmpty()) {
        //         logger.info("[{}] 扩展名({})种: {}", 
        //             category.getDescription(), extensions.size(), extensions);
        //     }
        //     if (sourceFormats != null && !sourceFormats.isEmpty()) {
        //         logger.info("[{}] 支持源格式({})种: {}", 
        //             category.getDescription(), sourceFormats.size(), sourceFormats);
        //     }
        //     if (targetFormats != null && !targetFormats.isEmpty()) {
        //         logger.info("[{}] 支持目标格式({})种: {}", 
        //             category.getDescription(), targetFormats.size(), targetFormats);
        //     }
        // }
        // logger.info("=============================================");
    }
    
    /**
     * 初始化默认的文件类型映射
     */
    private void initializeDefaultMappings() {
        registerImageExtensions();
        registerArchiveExtensions();
        registerOfdExtensions();
        registerDocumentExtensions();
        registerSpreadsheetExtensions();
        registerPresentationExtensions();
        registerVisioExtensions();
        registerAudioVideoExtensions();
        registerCadExtensions();
        register3DExtensions();
        registerSpecialExtensions();
        
        // 初始化格式能力映射
        registerFormatCapabilities();
    }
    
    /**
     * 注册图片文件扩展名
     */
    private void registerImageExtensions() {
        registerExtensions(FileCategory.IMAGE, 
            "tiff", "tif", "tiff", "psd", "emf", "bmp", "wmf", 
            "jpg", "jpeg", "png", "webp", "gif", "svg", "tga" );
    }
    
    /**
     * 注册压缩文件扩展名
     */
    private void registerArchiveExtensions() {
        registerExtensions(FileCategory.ARCHIVE, 
            "zip", "jar", "war", "ear", "tar", "tar.gz", "tgz", "rar", "7z");
    }
    
    /**
     * 注册OFD文件扩展名
     */
    private void registerOfdExtensions() {
        registerExtensions(FileCategory.OFD, "ofd");
    }
    
    /**
     * 注册文档文件扩展名
     */
    private void registerDocumentExtensions() {
        registerExtensions(FileCategory.DOCUMENT,
            "djvu", "doc", "docm", "docx", "dot", "dotm", "dotx", "epub", "fb2", "fodt",
            "htm", "html", "md", "hwp", "hwpx", "mht", "mhtml", "odt", "ott", "oxps",
            "pages", "pdf", "rtf", "stw", "sxw", "txt", "wps", "wpt", "xml", "xps");
    }
    
    /**
     * 注册表格文件扩展名
     */
    private void registerSpreadsheetExtensions() {
        registerExtensions(FileCategory.SPREADSHEET,
            "csv", "et", "ett", "fods", "numbers", "ods", "ots", "sxc",
            "xls", "xlsb", "xlsm", "xlsx", "xlt", "xltm", "xltx");
    }
    
    /**
     * 注册演示文件扩展名
     */
    private void registerPresentationExtensions() {
        registerExtensions(FileCategory.PRESENTATION,
            "dps", "dpt", "fodp", "key", "odg", "odp", "otp", "pot",
            "potm", "potx", "pps", "ppsm", "ppsx", "ppt", "pptm", "pptx", "sxi");
    }
    
    /**
     * 注册流程图文件扩展名
     */
    private void registerVisioExtensions() {
        registerExtensions(FileCategory.VISIO,
            "vsd", "vsdm", "vsdx", "vssm", "vssx", "vstm", "vstx", "bpmn", "xmind", "drawio");
    }
    
    /**
     * 注册音视频文件扩展名
     */
    private void registerAudioVideoExtensions() {
        registerExtensions(FileCategory.AV,
            "mp3", "wav", "mp4", "flv", "avi", "mkv", "mov", "wmv",
            "m4a", "aac", "ogg", "wma", "flac",
            "webm", "m4v", "3gp", "mpg", "mpeg");
    }
    
    /**
     * 注册CAD文件扩展名
     */
    private void registerCadExtensions() {
        registerExtensions(FileCategory.CAD,
            "cad", "dwg", "dxf", "dwf");
    }
    
    /**
     * 注册3D文件扩展名
     */
    private void register3DExtensions() {
        registerExtensions(FileCategory.THREE_D,
            "gltf", "glb", "obj", "stl", "fbx", "ply", "dae", "wrl", "3ds", "3mf");
    }
    
    /**
     * 注册特殊扩展名（复合扩展名）
     */
    private void registerSpecialExtensions() {
        specialExtensions.add("tar.gz");
        specialExtensions.add("tgz");
        extensionToCategoryMap.put("tar.gz", FileCategory.ARCHIVE);
        extensionToCategoryMap.put("tgz", FileCategory.ARCHIVE);
    }
    
    /**
     * 注册格式能力（源格式和目标格式）
     */
    private void registerFormatCapabilities() {
        // 图片格式能力
        registerFormatCapability(FileCategory.IMAGE,
            Arrays.asList("tiff", "tif", "png", "jpg", "jpeg", "bmp", "gif", "webp", "psd", "svg", "emf", "wmf", "tga"),
            Arrays.asList("png", "jpg", "jpeg", "bmp", "gif", "webp", "tiff", "svg", "pdf")
        );
        
        // 压缩文件格式能力
        registerFormatCapability(FileCategory.ARCHIVE,
            Arrays.asList("zip", "jar", "war", "ear", "tar", "tar.gz", "tgz", "rar", "7z"),
            Arrays.asList("zip", "json") // 压缩文件主要转JSON或解压
        );
        
        // OFD格式能力
        registerFormatCapability(FileCategory.OFD,
            Arrays.asList("ofd"),
            Arrays.asList("pdf", "png", "jpg", "jpeg", "svg", "html", "txt")
        );
        
        // 文档格式能力
        registerFormatCapability(FileCategory.DOCUMENT,
            Arrays.asList("djvu", "doc", "docm", "docx", "dot", "dotm", "dotx", "epub", "fb2", "fodt",
                         "htm", "html", "md", "hwp", "hwpx", "mht", "mhtml", "odt", "ott", "oxps",
                         "pages", "pdf", "rtf", "stw", "sxw", "txt", "wps", "wpt", "xml", "xps"),
            Arrays.asList("pdf", "png", "jpg", "jpeg", "html", "docx", "txt")
        );
        
        // 表格格式能力
        registerFormatCapability(FileCategory.SPREADSHEET,
            Arrays.asList("csv", "et", "ett", "fods", "numbers", "ods", "ots", "sxc",
                         "xls", "xlsb", "xlsm", "xlsx", "xlt", "xltm", "xltx", "xml"),
            Arrays.asList("bmp", "csv", "gif", "jpg", "ods", "ots", "pdf", "pdfa",
                         "png", "xlsm", "xlsx", "xltm", "xltx")
        );
        
        // 演示文件格式能力
        registerFormatCapability(FileCategory.PRESENTATION,
            Arrays.asList("dps", "dpt", "fodp", "key", "odg", "odp", "otp", "pot",
                         "potm", "potx", "pps", "ppsm", "ppsx", "ppt", "pptm", "pptx", "sxi"),
            Arrays.asList("bmp", "gif", "jpg", "odp", "otp", "pdf", "pdfa", "png",
                         "potm", "potx", "ppsm", "ppsx", "pptm", "pptx")
        );
        
        // 流程图文件格式能力
        registerFormatCapability(FileCategory.VISIO,
            Arrays.asList("vsdm","vsd", "vsdx", "vssm", "vssx","vstm","vstx"),
            Arrays.asList("bmp", "gif", "jpg", "pdf", "pdfa", "png")
        );
        
        // 音视频文件格式能力
        registerFormatCapability(FileCategory.AV,
            Arrays.asList("mp3", "wav", "mp4", "flv", "avi", "mkv", "mov", "wmv",
                         "m4a", "aac", "ogg", "wma", "flac",
                         "webm", "m4v", "3gp", "mpg", "mpeg"),
            Arrays.asList("mp4", "webm", "mp3", "wav", "ogg") // 常规转换格式
        );
        
        // CAD文件格式能力
        registerFormatCapability(FileCategory.CAD,
            Arrays.asList("cad", "dwg", "dxf", "dwf"),
            Arrays.asList("pdf", "png", "jpg", "jpeg", "svg", "dxf")
        );
        
        // 3D文件格式能力
        registerFormatCapability(FileCategory.THREE_D,
            Arrays.asList("gltf", "glb", "obj", "stl", "fbx", "ply", "dae", "wrl", "3ds", "3mf"),
            Arrays.asList("gltf", "glb", "obj", "stl", "fbx", "png", "jpg") // 3D模型转换和截图
        );
    }
    
    /**
     * 批量注册扩展名到指定文件类别
     * 
     * @param category 文件类别
     * @param extensions 扩展名列表
     */
    public void registerExtensions(FileCategory category, String... extensions) {
        Set<String> extensionSet = categoryToExtensionsMap.computeIfAbsent(
            category, k -> new HashSet<>());
        
        for (String ext : extensions) {
            String normalized = ext.toLowerCase();
            extensionToCategoryMap.put(normalized, category);
            extensionSet.add(normalized);
        }
    }
    
    /**
     * 注册单个扩展名到指定文件类别
     * 
     * @param category 文件类别
     * @param extension 扩展名
     */
    public void registerExtension(FileCategory category, String extension) {
        String normalized = extension.toLowerCase();
        extensionToCategoryMap.put(normalized, category);
        categoryToExtensionsMap.computeIfAbsent(category, k -> new HashSet<>())
            .add(normalized);
    }
    
    /**
     * 根据文件扩展名获取文件类别
     * 
     * @param fileExtension 文件扩展名
     * @return 文件类别，如果不支持则返回null
     */
    public FileCategory getFileCategory(String fileExtension) {
        if (!StringUtils.hasText(fileExtension)) {
            return null;
        }
        return extensionToCategoryMap.get(fileExtension.toLowerCase());
    }
    
    /**
     * 根据文件扩展名获取对应的转换策略类型
     * 
     * @param fileExtension 文件扩展名
     * @return 策略类型标识，如果不支持则返回null
     */
    public String getStrategyType(String fileExtension) {
        FileCategory category = getFileCategory(fileExtension);
        return category != null ? category.getStrategyType() : null;
    }
    
    /**
     * 从文件路径中提取文件扩展名
     * 支持特殊格式如tar.gz
     * 支持以点号开头的文件名（如.gitignore, .env等）
     * 
     * @param filePath 文件路径
     * @return 文件扩展名（小写），如果无法提取则返回空字符串
     */
    public String extractExtension(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "";
        }
        
        // 提取文件名（去除路径）
        String fileName = filePath;
        int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            fileName = filePath.substring(lastSeparator + 1);
        }
        
        // 检查特殊扩展名（如tar.gz）
        String lowerFileName = fileName.toLowerCase();
        for (String specialExt : specialExtensions) {
            if (lowerFileName.endsWith("." + specialExt)) {
                return specialExt;
            }
        }
        
        // 处理以点号开头的文件（如 .gitignore, .env）
        // 这类文件整个文件名就是“扩展名”
        if (fileName.startsWith(".") && fileName.indexOf(".", 1) == -1) {
            // 只有一个点号在开头，如 .gitignore
            return fileName.substring(1).toLowerCase(); // 返回 gitignore
        }
        
        // 常规扩展名提取
        if (!fileName.contains(".")) {
            return "";
        }
        
        int lastDot = fileName.lastIndexOf(".");
        // 避免点号在开头的情况（如 .gitignore.bak 应该返回 bak）
        if (lastDot == 0) {
            return "";
        }
        
        return fileName.substring(lastDot + 1).toLowerCase();
    }
    
    /**
     * 检查指定扩展名是否被支持
     * 
     * @param fileExtension 文件扩展名
     * @return true表示支持，false表示不支持
     */
    public boolean isSupported(String fileExtension) {
        if (!StringUtils.hasText(fileExtension)) {
            return false;
        }
        return extensionToCategoryMap.containsKey(fileExtension.toLowerCase());
    }
    
    /**
     * 获取指定文件类别支持的所有扩展名
     * 
     * @param category 文件类别
     * @return 扩展名集合（不可修改）
     */
    public Set<String> getSupportedExtensions(FileCategory category) {
        return Collections.unmodifiableSet(
            categoryToExtensionsMap.getOrDefault(category, Collections.emptySet()));
    }
    
    /**
     * 获取所有支持的文件扩展名
     * 
     * @return 所有扩展名的集合（不可修改）
     */
    public Set<String> getAllSupportedExtensions() {
        return Collections.unmodifiableSet(extensionToCategoryMap.keySet());
    }
    
    /**
     * 获取所有文件类别到扩展名的映射关系
     * 
     * @return 完整的映射关系（不可修改）
     */
    public Map<FileCategory, Set<String>> getAllCategoryMappings() {
        Map<FileCategory, Set<String>> result = new EnumMap<>(FileCategory.class);
        categoryToExtensionsMap.forEach((category, extensions) -> 
            result.put(category, Collections.unmodifiableSet(new HashSet<>(extensions))));
        return Collections.unmodifiableMap(result);
    }
    
    /**
     * 移除指定扩展名的映射
     * 
     * @param extension 要移除的扩展名
     * @return true表示成功移除，false表示该扩展名不存在
     */
    public boolean removeExtension(String extension) {
        String normalized = extension.toLowerCase();
        FileCategory category = extensionToCategoryMap.remove(normalized);
        if (category != null) {
            Set<String> extensions = categoryToExtensionsMap.get(category);
            if (extensions != null) {
                extensions.remove(normalized);
            }
            specialExtensions.remove(normalized);
            return true;
        }
        return false;
    }
    
    /**
     * 获取扩展名总数
     * 
     * @return 支持的扩展名总数
     */
    public int getSupportedExtensionCount() {
        return extensionToCategoryMap.size();
    }
    
    // ==================== 格式能力管理方法 ====================
    
    /**
     * 注册格式能力（源格式和目标格式）
     * 
     * @param category 文件类别
     * @param sourceFormats 支持的源格式列表
     * @param targetFormats 支持的目标格式列表
     */
    public void registerFormatCapability(FileCategory category, 
                                        List<String> sourceFormats, 
                                        List<String> targetFormats) {
        if (sourceFormats != null && !sourceFormats.isEmpty()) {
            Set<String> sourceSet = supportedSourceFormatsMap.computeIfAbsent(
                category, k -> new HashSet<>());
            sourceFormats.forEach(format -> sourceSet.add(format.toLowerCase()));
        }
        
        if (targetFormats != null && !targetFormats.isEmpty()) {
            Set<String> targetSet = supportedTargetFormatsMap.computeIfAbsent(
                category, k -> new HashSet<>());
            targetFormats.forEach(format -> targetSet.add(format.toLowerCase()));
        }
    }
    
    /**
     * 获取指定文件类别支持的源格式
     * 
     * @param category 文件类别
     * @return 支持的源格式集合（不可修改）
     */
    public Set<String> getSupportedSourceFormats(FileCategory category) {
        Set<String> formats = supportedSourceFormatsMap.get(category);
        return formats != null ? Collections.unmodifiableSet(formats) : Collections.emptySet();
    }
    
    /**
     * 获取指定文件类别支持的目标格式
     * 
     * @param category 文件类别
     * @return 支持的目标格式集合（不可修改）
     */
    public Set<String> getSupportedTargetFormats(FileCategory category) {
        Set<String> formats = supportedTargetFormatsMap.get(category);
        return formats != null ? Collections.unmodifiableSet(formats) : Collections.emptySet();
    }
    
    /**
     * 根据扩展名获取支持的源格式
     * 
     * @param extension 文件扩展名
     * @return 支持的源格式集合，如果扩展名不被支持则返回空集合
     */
    public Set<String> getSupportedSourceFormatsByExtension(String extension) {
        FileCategory category = getFileCategory(extension);
        return category != null ? getSupportedSourceFormats(category) : Collections.emptySet();
    }
    
    /**
     * 根据扩展名获取支持的目标格式
     * 
     * @param extension 文件扩展名
     * @return 支持的目标格式集合，如果扩展名不被支持则返回空集合
     */
    public Set<String> getSupportedTargetFormatsByExtension(String extension) {
        FileCategory category = getFileCategory(extension);
        return category != null ? getSupportedTargetFormats(category) : Collections.emptySet();
    }
    
    /**
     * 检查指定格式转换是否被支持
     * 
     * @param category 文件类别
     * @param sourceFormat 源格式
     * @param targetFormat 目标格式
     * @return true表示支持该转换，false表示不支持
     */
    public boolean isConversionSupported(FileCategory category, String sourceFormat, String targetFormat) {
        if (category == null || sourceFormat == null || targetFormat == null) {
            return false;
        }
        
        Set<String> sourceFormats = supportedSourceFormatsMap.get(category);
        Set<String> targetFormats = supportedTargetFormatsMap.get(category);
        
        boolean sourceSupported = sourceFormats != null && 
                                 sourceFormats.contains(sourceFormat.toLowerCase());
        boolean targetSupported = targetFormats != null && 
                                 targetFormats.contains(targetFormat.toLowerCase());
        
        return sourceSupported && targetSupported;
    }
    
    /**
     * 检查指定扩展名的格式转换是否被支持
     * 
     * @param extension 文件扩展名
     * @param sourceFormat 源格式
     * @param targetFormat 目标格式
     * @return true表示支持该转换，false表示不支持
     */
    public boolean isConversionSupportedByExtension(String extension, 
                                                     String sourceFormat, 
                                                     String targetFormat) {
        FileCategory category = getFileCategory(extension);
        return isConversionSupported(category, sourceFormat, targetFormat);
    }
    
    /**
     * 检查源格式是否被支持
     * 
     * @param category 文件类别
     * @param sourceFormat 源格式
     * @return true表示支持，false表示不支持
     */
    public boolean isSourceFormatSupported(FileCategory category, String sourceFormat) {
        if (category == null || sourceFormat == null) {
            return false;
        }
        Set<String> formats = supportedSourceFormatsMap.get(category);
        return formats != null && formats.contains(sourceFormat.toLowerCase());
    }
    
    /**
     * 检查目标格式是否被支持
     * 
     * @param category 文件类别
     * @param targetFormat 目标格式
     * @return true表示支持，false表示不支持
     */
    public boolean isTargetFormatSupported(FileCategory category, String targetFormat) {
        if (category == null || targetFormat == null) {
            return false;
        }
        Set<String> formats = supportedTargetFormatsMap.get(category);
        return formats != null && formats.contains(targetFormat.toLowerCase());
    }
    
    /**
     * 根据文件类别获取所有格式（源+目标，合并去重）
     * 
     * @param category 文件类别
     * @return 所有格式的集合（不可修改）
     */
    public Set<String> getFormatsByCategory(FileCategory category) {
        if (category == null) {
            return Collections.emptySet();
        }
        
        Set<String> allFormats = new HashSet<>();
        
        Set<String> sourceFormats = supportedSourceFormatsMap.get(category);
        if (sourceFormats != null) {
            allFormats.addAll(sourceFormats);
        }
        
        Set<String> targetFormats = supportedTargetFormatsMap.get(category);
        if (targetFormats != null) {
            allFormats.addAll(targetFormats);
        }
        
        // 如果没有注册格式能力，回退到扩展名集合
        if (allFormats.isEmpty()) {
            Set<String> extensions = categoryToExtensionsMap.get(category);
            if (extensions != null) {
                allFormats.addAll(extensions);
            }
        }
        
        return Collections.unmodifiableSet(allFormats);
    }
    
    /**
     * 获取所有文件类别的格式能力映射
     * 
     * @return 格式能力映射（类别 -> (源格式集合, 目标格式集合)）
     */
    public Map<FileCategory, FormatCapability> getAllFormatCapabilities() {
        Map<FileCategory, FormatCapability> result = new EnumMap<>(FileCategory.class);
        
        for (FileCategory category : FileCategory.values()) {
            Set<String> sourceFormats = supportedSourceFormatsMap.get(category);
            Set<String> targetFormats = supportedTargetFormatsMap.get(category);
            
            if (sourceFormats != null || targetFormats != null) {
                result.put(category, new FormatCapability(
                    sourceFormats != null ? new HashSet<>(sourceFormats) : Collections.emptySet(),
                    targetFormats != null ? new HashSet<>(targetFormats) : Collections.emptySet()
                ));
            }
        }
        
        return result;
    }
    
    /**
     * 格式能力包装类
     */
    public static class FormatCapability {
        private final Set<String> sourceFormats;
        private final Set<String> targetFormats;
        
        public FormatCapability(Set<String> sourceFormats, Set<String> targetFormats) {
            this.sourceFormats = Collections.unmodifiableSet(sourceFormats);
            this.targetFormats = Collections.unmodifiableSet(targetFormats);
        }
        
        public Set<String> getSourceFormats() {
            return sourceFormats;
        }
        
        public Set<String> getTargetFormats() {
            return targetFormats;
        }
    }
}
