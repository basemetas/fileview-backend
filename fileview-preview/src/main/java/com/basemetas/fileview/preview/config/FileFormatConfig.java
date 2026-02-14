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
package com.basemetas.fileview.preview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.preview.model.FormatInfo;
import com.basemetas.fileview.preview.model.FormatValidationResult;
import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 文件格式配置类
 * 统一维护文件预览服务支持的文件格式信息
 * 
 * 职责：
 * 1. 维护需要转换的文件格式及其默认转换格式
 * 2. 维护无需转换的文件格式
 * 3. 提供格式验证和查询功能
 * 4. 在发起预览请求时提供格式校验和默认格式设置
 * 
 * @author 夫子
 */
@Component
public class FileFormatConfig {

    private static final Logger logger = LoggerFactory.getLogger(FileFormatConfig.class);

    /**
     * 需要转换的文件格式配置
     * 格式: 源格式 -> 默认转换格式
     */
    private final Map<String, FormatInfo> conversionRequiredFormats = new LinkedHashMap<>();

    /**
     * 无需转换的文件格式集合
     * 按类别分组
     */
    private final Map<FileCategory, Set<String>> directPreviewFormats = new EnumMap<>(FileCategory.class);

    /**
     * 所有支持的格式（需要转换 + 无需转换）
     */
    private final Set<String> allSupportedFormats = new HashSet<>();

    public FileFormatConfig() {
        initializeConversionRequiredFormats();
        initializeDirectPreviewFormats();
        buildAllSupportedFormats();
    }

    /**
     * Spring初始化后的回调
     */
    @PostConstruct
    public void postInit() {
        // logger.info("========== 文件格式配置初始化完成 ==========");
        // logger.info("需要转换的格式数量: {}", conversionRequiredFormats.size());
        // logger.info("无需转换的格式数量: {}", allSupportedFormats.size() - conversionRequiredFormats.size());
        // logger.info("支持的格式总数: {}", allSupportedFormats.size());

        // // 输出需要转换的格式详情
        // logger.info("--- 需要转换的文件格式 ---");
        // conversionRequiredFormats.forEach((ext, info) -> {
        //     logger.debug("[{}] {} -> {} ({})",
        //             info.getCategory(), ext, info.getDefaultTargetFormat(), info.getDescription());
        // });

        // // 输出无需转换的格式
        // logger.info("--- 无需转换的文件格式 ---");
        // directPreviewFormats.forEach((category, formats) -> {
        //     logger.debug("[{}] {}", category.getDescription(), formats);
        // });

        // logger.info("==========================================");
    }

    /**
     * 初始化需要转换的文件格式
     */
    private void initializeConversionRequiredFormats() {
        // 1. 文档文件：doc、docx、dot、dotx、dotm、wps、wpt，rtf，默认转换为pdf格式
        registerConversionFormat("doc", "pdf", FileCategory.DOCUMENT, "Microsoft Word 97-2003");
        registerConversionFormat("docx", "pdf", FileCategory.DOCUMENT, "Microsoft Word 2007+");
        registerConversionFormat("dot", "pdf", FileCategory.DOCUMENT, "Microsoft Word模板 97-2003");
        registerConversionFormat("dotx", "pdf", FileCategory.DOCUMENT, "Microsoft Word模板 2007+");
        registerConversionFormat("dotm", "pdf", FileCategory.DOCUMENT, "Microsoft Word启用宏的模板");
        registerConversionFormat("wps", "pdf", FileCategory.DOCUMENT, "WPS文字文档");
        registerConversionFormat("wpt", "pdf", FileCategory.DOCUMENT, "WPS文字模板");
        registerConversionFormat("rtf", "pdf", FileCategory.DOCUMENT, "富文本格式");

        // 2. 表格文件：xls、xlsm、xlt、xltx、xltm、xla、xlam、et、ett，默认转换为xlsx格式
        registerConversionFormat("xls", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel 97-2003");
        registerConversionFormat("xlsm", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel启用宏的工作簿");
        registerConversionFormat("xlt", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel模板 97-2003");
        registerConversionFormat("xltx", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel模板 2007+");
        registerConversionFormat("xltm", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel启用宏的模板");
        registerConversionFormat("xla", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel加载项 97-2003");
        registerConversionFormat("xlam", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel加载项 2007+");
        registerConversionFormat("et", "xlsx", FileCategory.SPREADSHEET, "WPS表格文档");
        registerConversionFormat("ett", "xlsx", FileCategory.SPREADSHEET, "WPS表格模板");
        // xlsx智能处理：非加密直接预览，加密时转xlsx（POI解密转换）
        registerConversionFormat("xlsx", "xlsx", FileCategory.SPREADSHEET, "Microsoft Excel 2007+（加密转换）");

        // 3. 演示文件：ppt、pptx、dps、dpt，默认转换为pdf格式
        registerConversionFormat("ppt", "pdf", FileCategory.PRESENTATION, "Microsoft PowerPoint 97-2003");
        registerConversionFormat("pptx", "pdf", FileCategory.PRESENTATION, "Microsoft PowerPoint 2007+");
        registerConversionFormat("dps", "pdf", FileCategory.PRESENTATION, "WPS演示文档");
        registerConversionFormat("dpt", "pdf", FileCategory.PRESENTATION, "WPS演示模板");

        // 4. 版式文件：ofd，默认转换为pdf格式
        registerConversionFormat("ofd", "pdf", FileCategory.OFD, "开放版式文档");

        // 5. 图片文件：bmp、psd、tif、tiff、emf、wmf，默认转换为png格式
        registerConversionFormat("bmp", "png", FileCategory.IMAGE, "位图");
        registerConversionFormat("psd", "png", FileCategory.IMAGE, "Adobe Photoshop文档");
        registerConversionFormat("tif", "png", FileCategory.IMAGE, "标签图像文件格式");
        registerConversionFormat("tiff", "png", FileCategory.IMAGE, "标签图像文件格式");
        registerConversionFormat("emf", "png", FileCategory.IMAGE, "增强型图元文件");
        registerConversionFormat("wmf", "png", FileCategory.IMAGE, "Windows图元文件");

        // 6. 压缩文件：zip、jar、tar、tar.gz、tgz、rar、7z，默认转换为json格式
        registerConversionFormat("zip", "json", FileCategory.ARCHIVE, "ZIP压缩包");
        registerConversionFormat("jar", "json", FileCategory.ARCHIVE, "Java归档文件");
        registerConversionFormat("tar", "json", FileCategory.ARCHIVE, "TAR归档文件");
        registerConversionFormat("tar.gz", "json", FileCategory.ARCHIVE, "TAR+GZIP压缩包");
        registerConversionFormat("tgz", "json", FileCategory.ARCHIVE, "TAR+GZIP压缩包");
        registerConversionFormat("rar", "json", FileCategory.ARCHIVE, "RAR压缩包");
        registerConversionFormat("7z", "json", FileCategory.ARCHIVE, "7-Zip压缩包");

        // 7. 流程图文件：vsdm、vsdx，默认转换为pdf格式
        registerConversionFormat("vsdm", "pdf", FileCategory.VISIO, "Microsoft Visio启用宏的绘图");
        registerConversionFormat("vsdx", "pdf", FileCategory.VISIO, "Microsoft Visio绘图");
        registerConversionFormat("vssm", "pdf", FileCategory.VISIO, "Microsoft Visio启用宏的绘图");
        registerConversionFormat("vssx", "pdf", FileCategory.VISIO, "Microsoft Visio绘图");
        registerConversionFormat("vstm", "pdf", FileCategory.VISIO, "Microsoft Visio启用宏的绘图");
        registerConversionFormat("vstx", "pdf", FileCategory.VISIO, "Microsoft Visio绘图");
        registerConversionFormat("vsd", "pdf", FileCategory.VISIO, "Microsoft Visio绘图");

        //8. CAD文件：dwg
        registerConversionFormat("dwg", "dxf", FileCategory.CAD, "CAD文件");
    }

    /**
     * 初始化无需转换的文件格式
     */
    private void initializeDirectPreviewFormats() {
        // 1. 表格文件：xlsx、csv（xlsx由XlsxPreviewConfig智能处理）
        registerDirectPreviewFormats(FileCategory.SPREADSHEET, "xlsx", "csv");

        // 2. 版式文件：pdf
        registerDirectPreviewFormats(FileCategory.PDF, "pdf");

        // 3. 图片文件：jpg、jpeg、png、webp、gif、svg、tga
        registerDirectPreviewFormats(FileCategory.IMAGE, "jpg", "jpeg", "png", "webp", "gif", "svg", "tga");

        // 4. 音视频文件：mp3、wav、mp4、flv等
        registerDirectPreviewFormats(FileCategory.AV, "mp3", "m4a", "wav", "aac", "ac3", "au", "flac", "ogg", "aif",
                "aifc", "aiff", "wma", "wav", "mp4", "m4v", "webm", "mpg", "mpeg", "m2v", "m4p", "m4v", "mj2", "ogv",
                "qt", "vob", "flv", "avi", "mkv", "mov", "wmv");

        // 5. 文档文件：txt、xml、java、php、py、js、css、md、epub
        registerDirectPreviewFormats(FileCategory.DOCUMENT, "txt", "md", "markdown","js", "jsx", "ts", "tsx", "mjs", "cjs", "coffee",
                "litcoffee", "html", "htm", "css", "scss", "sass", "less", "py", "pyw", "pyt", "pyx", "pyo", "rpy","epub",
                "gyp", "rb", "rbx", "rjs", "gemspec", "rake", "thor", "ru", "java", "jsp", "jspx", "jhtml", "tag",
                "jsf", "php", "php3", "php4", "php5", "phtml", "phpt", "c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "hh",
                "ino", "tcc", "cs", "cshtml", "razor", "sh", "bash", "zsh", "fish", "ksh", "csh", "tcsh", "bashrc",
                "bash_profile", "sql", "pgsql", "sqlite", "db", "db3", "dsql", "json", "geojson", "ldjson", "yaml",
                "yml", "mdown", "mkd", "mkdn", "mdwn", "mdtxt", "rst", "go", "gomod", "gohtml", "lua", "pde", "r",
                "rmd", "rnw", "rhtml", "rs", "toml", "swift", "dart", "kt", "kts", "gradle", "groovy", "scala", "sc",
                "hs", "lhs", "m", "mm", "objc", "objcpp", "ex", "exs", "erl", "hrl", "pl", "pm", "t", "pod", "tcl",
                "tk", "groovy", "gvy", "gsh", "grvy", "vhd", "vhdl", "tex", "ltx", "bib", "cls", "sty", "ins", "dtx",
                "xml", "xsd", "xsl", "xslt", "svg", "Dockerfile", "ejs", "jade", "pug", "cshtml", "razor", "vbhtml",
                "vue", "json5", "mdx", "tf", "tfvars", "conf", "nginx", "http", "rest", "graphql", "gql", "sh", "bash",
                "zsh", "fish", "toml", "cmake", "asm", "s", "schema", "hcl", "svelte","gitignore","cmd","log");

        // 6. 流程图文件：bpmn、xmind
        registerDirectPreviewFormats(FileCategory.VISIO, "bpmn", "drawio", "xmind");

        // 7. CAD文件：cad（注意：实际可能需要dwg、dxf等）
        registerDirectPreviewFormats(FileCategory.CAD, "dxf");

        // 8. 3D文件：GLTF、GLB、OBJ、STL、FBX、PLY、DAE、WRL、3DS、3MF
        registerDirectPreviewFormats(FileCategory.THREE_D, "gltf", "glb", "obj", "stl", "fbx", "ply", "dae", "wrl",
                "3ds", "3mf","3dm");
    }

    /**
     * 注册需要转换的格式
     */
    private void registerConversionFormat(String sourceFormat, String targetFormat,
            FileCategory category, String description) {
        String normalized = sourceFormat.toLowerCase();
        conversionRequiredFormats.put(normalized, new FormatInfo(
                normalized, targetFormat.toLowerCase(), category.getDescription(), description, true));
    }

    /**
     * 批量注册无需转换的格式
     */
    private void registerDirectPreviewFormats(FileCategory category, String... formats) {
        Set<String> formatSet = directPreviewFormats.computeIfAbsent(
                category, k -> new HashSet<>());

        for (String format : formats) {
            String normalized = format.toLowerCase();
            formatSet.add(normalized);
        }
    }

    /**
     * 构建所有支持的格式集合
     */
    private void buildAllSupportedFormats() {
        allSupportedFormats.addAll(conversionRequiredFormats.keySet());
        directPreviewFormats.values().forEach(allSupportedFormats::addAll);
    }

    // ==================== 公共查询方法 ====================

    /**
     * 判断指定格式是否需要转换
     * 
     * @param format 文件格式（扩展名）
     * @return true表示需要转换，false表示无需转换或不支持
     */
    public boolean needsConversion(String format) {
        if (format == null || format.trim().isEmpty()) {
            return false;
        }
        return conversionRequiredFormats.containsKey(format.toLowerCase());
    }

    /**
     * 判断指定格式是否支持直接预览
     * 
     * @param format 文件格式（扩展名）
     * @return true表示支持直接预览，false表示需要转换或不支持
     */
    public boolean supportsDirectPreview(String format) {
        if (format == null || format.trim().isEmpty()) {
            return false;
        }
        String normalized = format.toLowerCase();
        return directPreviewFormats.values().stream()
                .anyMatch(formats -> formats.contains(normalized));
    }

    /**
     * 判断指定格式是否被支持（需要转换 + 无需转换）
     * 
     * @param format 文件格式（扩展名）
     * @return true表示支持，false表示不支持
     */
    public boolean isSupported(String format) {
        if (format == null || format.trim().isEmpty()) {
            return false;
        }
        return allSupportedFormats.contains(format.toLowerCase());
    }

    /**
     * 获取指定格式的默认转换格式
     * 
     * @param sourceFormat 源格式
     * @return 默认转换格式，如果不需要转换则返回null
     */
    public String getDefaultTargetFormat(String sourceFormat) {
        if (sourceFormat == null || sourceFormat.trim().isEmpty()) {
            return null;
        }
        FormatInfo info = conversionRequiredFormats.get(sourceFormat.toLowerCase());
        return info != null ? info.getDefaultTargetFormat() : null;
    }

    /**
     * 获取指定格式的详细信息
     * 
     * @param format 文件格式
     * @return 格式信息，如果不存在则返回null
     */
    public FormatInfo getFormatInfo(String format) {
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        return conversionRequiredFormats.get(format.toLowerCase());
    }

    /**
     * 获取所有需要转换的格式
     * 
     * @return 需要转换的格式集合（不可修改）
     */
    public Set<String> getConversionRequiredFormats() {
        return Collections.unmodifiableSet(conversionRequiredFormats.keySet());
    }

    /**
     * 获取所有无需转换的格式
     * 
     * @return 无需转换的格式集合（不可修改）
     */
    public Set<String> getDirectPreviewFormats() {
        Set<String> result = new HashSet<>();
        directPreviewFormats.values().forEach(result::addAll);
        return Collections.unmodifiableSet(result);
    }

    /**
     * 获取所有支持的格式
     * 
     * @return 所有支持的格式集合（不可修改）
     */
    public Set<String> getAllSupportedFormats() {
        return Collections.unmodifiableSet(allSupportedFormats);
    }

    /**
     * 获取按类别分组的无需转换格式
     * 
     * @return 类别 -> 格式集合的映射（不可修改）
     */
    public Map<FileCategory, Set<String>> getDirectPreviewFormatsByCategory() {
        Map<FileCategory, Set<String>> result = new EnumMap<>(FileCategory.class);
        directPreviewFormats.forEach(
                (category, formats) -> result.put(category, Collections.unmodifiableSet(new HashSet<>(formats))));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取按类别分组的需要转换格式
     * 
     * @return 类别 -> 格式信息集合的映射
     */
    public Map<FileCategory, List<FormatInfo>> getConversionRequiredFormatsByCategory() {
        Map<FileCategory, List<FormatInfo>> result = new EnumMap<>(FileCategory.class);
        conversionRequiredFormats.values().forEach(info -> {
            // 根据描述找到对应的FileCategory
            FileCategory category = findCategoryByDescription(info.getCategory());
            if (category != null) {
                result.computeIfAbsent(category, k -> new ArrayList<>()).add(info);
            }
        });
        return result;
    }

    /**
     * 验证格式并返回校验结果
     * 
     * @param format 文件格式
     * @return 校验结果
     */
    public FormatValidationResult validateFormat(String format) {
        if (format == null || format.trim().isEmpty()) {
            return new FormatValidationResult(false, "文件格式不能为空", null);
        }

        String normalized = format.toLowerCase();

        // 检查是否需要转换
        if (conversionRequiredFormats.containsKey(normalized)) {
            FormatInfo info = conversionRequiredFormats.get(normalized);
            return new FormatValidationResult(true,
                    "格式需要转换", info.getDefaultTargetFormat());
        }

        // 检查是否支持直接预览
        if (supportsDirectPreview(normalized)) {
            return new FormatValidationResult(true,
                    "格式支持直接预览", null);
        }

        // 不支持的格式
        return new FormatValidationResult(false,
                "不支持的文件格式: " + format, null);
    }

    /**
     * 根据描述找到对应的FileCategory
     */
    private FileCategory findCategoryByDescription(String description) {
        for (FileCategory category : FileCategory.values()) {
            if (category.getDescription().equals(description)) {
                return category;
            }
        }
        return null;
    }

    // ==================== Office/WPS 格式判断方法 ====================

    /**
     * 判断是否为 OOXML 格式
     */
    public boolean isOoxmlFormat(String format) {
        if (format == null) return false;
        String lower = format.toLowerCase();
        return "docx".equals(lower) || "xlsx".equals(lower) || "pptx".equals(lower) ||
               "docm".equals(lower) || "xlsm".equals(lower) || "pptm".equals(lower);
    }

    /**
     * 判断是否为 OLE2 格式（不含 WPS）
     */
    public boolean isOle2Format(String format) {
        if (format == null) return false;
        String lower = format.toLowerCase();
        return "doc".equals(lower) || "xls".equals(lower) || "ppt".equals(lower);
    }

    /**
     * 判断是否为 WPS 格式
     */
    public boolean isWpsFormat(String format) {
        if (format == null) return false;
        String lower = format.toLowerCase();
        return "wps".equals(lower) || "wpt".equals(lower) ||
               "et".equals(lower) || "ett".equals(lower) ||
               "dps".equals(lower) || "dpt".equals(lower);
    }

    /**
     * 判断是否为 Office 文档（包括 OOXML、OLE2、WPS）
     */
    public boolean isOfficeDocument(String format) {
        return isOoxmlFormat(format) || isOle2Format(format) || isWpsFormat(format);
    }

    /**
     * 判断是否为旧式加密格式（无法直接验证密码）
     * 包括：doc、ppt 和所有 WPS 格式
     */
    public boolean isLegacyEncryptedFormat(String format) {
        if (format == null) return false;
        String lower = format.toLowerCase();
        return "doc".equals(lower) || "ppt".equals(lower) || isWpsFormat(lower);
    }

    /**
     * 获取文件类型描述（用于日志）
     */
    public String getFileTypeDescription(String format) {
        if (format == null) return "Unknown";
        String lower = format.toLowerCase();
        if ("doc".equals(lower)) return "Word";
        if ("ppt".equals(lower)) return "PPT";
        if ("xls".equals(lower)) return "Excel";
        if ("wps".equals(lower) || "wpt".equals(lower)) return "WPS文字";
        if ("et".equals(lower) || "ett".equals(lower)) return "WPS表格";
        if ("dps".equals(lower) || "dpt".equals(lower)) return "WPS演示";
        if (isOoxmlFormat(lower)) return "Office";
        return format.toUpperCase();
    }
}
