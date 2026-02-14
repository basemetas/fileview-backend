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

import com.basemetas.fileview.convert.strategy.model.DocumentProfile;
import com.basemetas.fileview.convert.strategy.model.ConvertResult;
import com.basemetas.fileview.convert.strategy.model.EngineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.convert.service.checker.EngineHealthCheckService;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一文档转换引擎选择器
 * 
 * 智能选择最适合的转换引擎：
 * LibreOffice - 开源方案，适合简单文档
 * 
 * 支持故障转移和引擎优先级配置
 * 
 * @author 夫子
 */
@Component
public class ConvertEngineSelector {

    private static final Logger logger = LoggerFactory.getLogger(ConvertEngineSelector.class);

    @Autowired
    private LibreOfficeConverter libreOfficeConverter;

    @Autowired(required = false)
    private EngineHealthCheckService engineHealthCheckService;

    // 全局配置：是否启用基于文档特征的智能引擎选择
    @Value("${convert.engine.smart-selection.enabled:false}")
    private boolean smartSelectionEnabled;

    // Word配置
    @Value("${word.convert.engine.priority:libreoffice}")
    private String wordEnginePriorityConfig;

    @Value("${word.convert.engine.fallback:true}")
    private boolean wordEnableFallback;

    @Value("${word.convert.file.size.threshold:10485760}") // 10MB
    private long wordFileSizeThreshold;

    @Value("${word.convert.libreoffice.enable:false}")
    private boolean wordLibreOfficeEnabled;

    // PPT配置
    @Value("${ppt.convert.engine.priority:libreoffice}")
    private String pptEnginePriorityConfig;

    @Value("${ppt.convert.engine.fallback:true}")
    private boolean pptEnableFallback;

    @Value("${ppt.convert.file.size.threshold:100485760}") // 100MB，PPT文件可能包含大量媒体
    private long pptFileSizeThreshold;

    @Value("${ppt.convert.libreoffice.enable:false}")
    private boolean pptLibreOfficeEnabled;

    // Excel配置
    @Value("${excel.convert.engine.priority:libreoffice}")
    private String excelEnginePriorityConfig;

    @Value("${excel.convert.engine.fallback:true}")
    private boolean excelEnableFallback;

    @Value("${excel.convert.file.size.threshold:50485760}") // 50MB，Excel文件通常较大
    private long excelFileSizeThreshold;

    @Value("${excel.convert.libreoffice.enable:false}")
    private boolean excelLibreOfficeEnabled;

    // Visio配置
    @Value("${visio.convert.engine.priority:libreoffice}")
    private String visioEnginePriorityConfig;

    @Value("${visio.convert.engine.fallback:true}")
    private boolean visioEnableFallback;

    @Value("${visio.convert.file.size.threshold:50485760}") // 50MB
    private long visioFileSizeThreshold;

    @Value("${visio.convert.libreoffice.enable:true}")
    private boolean visioLibreOfficeEnabled;

    /**
     * 转换引擎枚举
     */
    public enum ConvertEngine {
        /**
         * LibreOffice命令行
         */
        LIBREOFFICE("libreoffice", "LibreOffice Command Line");

        private final String code;
        private final String description;

        ConvertEngine(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static ConvertEngine fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ConvertEngine engine : values()) {
                if (engine.code.equalsIgnoreCase(code.trim())) {
                    return engine;
                }
            }
            return null;
        }
    }

    /**
     * 文档类型枚举
     */
    public enum DocumentType {
        /**
         * Word文档
         */
        WORD("word"),
        /**
         * PowerPoint演示文稿
         */
        PPT("ppt"),
        /**
         * Excel电子表格
         */
        EXCEL("excel"),
        /**
         * Visio图表
         */
        VISIO("visio");

        private final String type;

        DocumentType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * 转换文档并自动选择最佳引擎（带密码支持）
     * 
     * @param documentType   文档类型
     * @param sourceFilePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param sourceFormat   源文件格式
     * @param targetFormat   目标文件格式
     * @param password       密码（可选）
     * @return 转换结果信息
     */
    public ConvertResult convertDocument(DocumentType documentType, String sourceFilePath, String targetFilePath,
            String sourceFormat, String targetFormat, String password) {
        // 1. 分析文档特征（仅在智能选择启用时）
        DocumentProfile profile = null;
        if (smartSelectionEnabled) {
            profile = analyzeDocument(documentType, sourceFilePath, sourceFormat, targetFormat);
        }

        // 2. 选择转换引擎列表
        List<ConvertEngine> engines = selectEngines(documentType, profile);

        if (engines.isEmpty()) {
            logger.error("没有可用的转换引擎");
            return ConvertResult.failure("没有可用的转换引擎");
        }

        // 3. 依次尝试转换
        Exception lastException = null;
        for (ConvertEngine engine : engines) {
            try {
                logger.info("尝试使用转换引擎: {} - {}", engine.getCode(), engine.getDescription());

                boolean success = convertWithEngine(engine, documentType, sourceFilePath, targetFilePath,
                        sourceFormat, targetFormat, password);

                if (success) {
                    return ConvertResult.success(engine, "转换成功完成");
                } else {
                    logger.warn("转换失败 - 引擎: {}", engine.getDescription());
                }

            } catch (com.basemetas.fileview.convert.common.exception.UnsupportedException e) {
                // 引擎能力不支持，直接返回带 errorCode 的失败结果
                logger.warn("转换引擎能力不支持 - 引擎: {}, code: {}, message: {}", 
                           engine.getDescription(), e.getErrorCode(), e.getMessage());
                return ConvertResult.failureWithCode(e.getErrorCode(), e.getMessage());
                
            } catch (Exception e) {
                lastException = e;
                logger.error("转换引擎异常 - 引擎: {}", engine.getDescription(), e);

                // 如果禁用故障转移，直接返回失败
                if (!isFallbackEnabled(documentType)) {
                    return ConvertResult.failure("转换失败: " + e.getMessage());
                }
            }
        }

        // 所有引擎都失败
        String errorMessage = "所有转换引擎都失败了";
        if (lastException != null) {
            errorMessage += ": " + lastException.getMessage();
        }

        logger.error(errorMessage);
        return ConvertResult.failure(errorMessage);
    }

    /**
     * 转换文档并自动选择最佳引擎（兼容方法）
     */
    public ConvertResult convertDocument(DocumentType documentType, String sourceFilePath, String targetFilePath,
            String sourceFormat, String targetFormat) {
        return convertDocument(documentType, sourceFilePath, targetFilePath, sourceFormat, targetFormat, null);
    }

    /**
     * 分析文档特征
     */
    private DocumentProfile analyzeDocument(DocumentType documentType, String sourceFilePath, String sourceFormat,
            String targetFormat) {
        DocumentProfile profile = new DocumentProfile();

        try {
            File sourceFile = new File(sourceFilePath);
            profile.setFileSize(sourceFile.length());
            profile.setFileName(sourceFile.getName());
            profile.setSourceFormat(sourceFormat);
            profile.setTargetFormat(targetFormat);

            // 判断文档复杂度
            profile.setComplexity(determineComplexity(documentType, profile));

            logger.debug("文档分析结果 - 类型: {}, 文件: {}, 大小: {} bytes, 复杂度: {}",
                    documentType, profile.getFileName(), profile.getFileSize(), profile.getComplexity());

        } catch (Exception e) {
            logger.warn("文档分析失败，使用默认配置", e);
            profile.setComplexity(DocumentProfile.DocumentComplexity.MEDIUM);
        }

        return profile;
    }

    /**
     * 判断文档复杂度
     */
    private DocumentProfile.DocumentComplexity determineComplexity(DocumentType documentType, DocumentProfile profile) {
        long fileSizeThreshold = getFileSizeThreshold(documentType);

        // 根据文件大小判断
        if (profile.getFileSize() > fileSizeThreshold) {
            return DocumentProfile.DocumentComplexity.HIGH;
        } else if (profile.getFileSize() < 1024 * 1024) { // 1MB
            return DocumentProfile.DocumentComplexity.LOW;
        } else {
            return DocumentProfile.DocumentComplexity.MEDIUM;
        }
    }

    /**
     * 选择转换引擎列表（基于文档特征智能选择）
     */
    private List<ConvertEngine> selectEngines(DocumentType documentType, DocumentProfile profile) {
        List<ConvertEngine> engines = new ArrayList<>();

        // 解析配置的引擎优先级
        String enginePriorityConfig = getEnginePriorityConfig(documentType);
        List<String> priorityList = Arrays.asList(enginePriorityConfig.split(","));
        logger.debug("配置的引擎优先级: {}", priorityList);

        // 根据文档复杂度调整引擎选择策略（仅在智能选择启用且profile不为null时）
        List<String> optimizedPriorityList;
        if (smartSelectionEnabled && profile != null) {
            logger.debug("根据文档特征选择引擎 - 文件大小: {} bytes, 复杂度: {}",
                    profile.getFileSize(), profile.getComplexity());
            optimizedPriorityList = optimizeEnginePriority(priorityList, profile);
            logger.debug("优化后的引擎优先级: {}", optimizedPriorityList);
        } else {
            logger.debug("智能引擎选择已禁用，使用配置的引擎优先级");
            optimizedPriorityList = priorityList;
        }

        for (String engineCode : optimizedPriorityList) {
            ConvertEngine engine = ConvertEngine.fromCode(engineCode.trim());
            logger.debug("检查引擎: {} -> {}", engineCode.trim(), engine);

            if (engine != null) {
                boolean available = isEngineAvailable(documentType, engine);
                logger.debug("引擎 {} 可用性: {}", engine, available);

                if (available) {
                    engines.add(engine);
                }
            }
        }

        // 如果没有可用引擎，按默认顺序添加
        if (engines.isEmpty()) {
            logger.warn("配置的引擎都不可用，尝试默认引擎序列");
            if (isLibreOfficeEnabled(documentType)) {
                boolean libreOfficeAvailable = isEngineAvailable(documentType, ConvertEngine.LIBREOFFICE);
                logger.debug("LibreOffice引擎 - 启用: {}, 可用: {}", isLibreOfficeEnabled(documentType), libreOfficeAvailable);
                if (libreOfficeAvailable) {
                    engines.add(ConvertEngine.LIBREOFFICE);
                }
            }

        }

        logger.debug("选择的转换引擎序列: {}", engines);
        return engines;
    }

    /**
     * 根据文档特征优化引擎优先级
     * 
     * 优化策略：
     * 1. 高复杂度文档：优先使用X2T（性能好）或OnlyOffice（质量高）
     * 2. 低复杂度文档：优先使用LibreOffice（轻量）或POI（快速）
     * 3. 中等复杂度：保持配置顺序
     * 
     * 可通过配置参数 convert.engine.smart-selection.enabled 控制是否启用
     */
    private List<String> optimizeEnginePriority(List<String> originalPriority, DocumentProfile profile) {
        // 检查是否启用智能选择
        if (!smartSelectionEnabled) {
            logger.debug("智能引擎选择已禁用，使用配置的引擎优先级");
            return originalPriority;
        }

        // 如果文档特征未知，直接返回原配置
        if (profile.getComplexity() == null) {
            return originalPriority;
        }

        List<String> optimized = new ArrayList<>(originalPriority);

        switch (profile.getComplexity()) {
            case HIGH:
                //社区版未有此功能
                break;
            case LOW:
                // 低复杂度文档：LibreOffice和POI优先
                logger.debug("检测到低复杂度文档，优先使用LibreOffice或POI引擎");
                optimized.sort((a, b) -> {
                    boolean aIsPreferred = a.equalsIgnoreCase("libreoffice") ;
                    boolean bIsPreferred = b.equalsIgnoreCase("libreoffice") ;
                    if (aIsPreferred && !bIsPreferred) {
                        return -1;
                    }
                    if (!aIsPreferred && bIsPreferred) {
                        return 1;
                    } else {
                        return 0;
                    }

                });
                break;

            case MEDIUM:
            default:
                // 中等复杂度：保持原配置顺序
                logger.debug("检测到中等复杂度文档，保持配置的引擎优先级");
                break;
        }

        return optimized;
    }

    /**
     * 检查引擎是否可用（使用后台健康检查服务）
     */
    private boolean isEngineAvailable(DocumentType documentType, ConvertEngine engine) {
        try {
            // 1. 检查配置是否启用
            boolean enabled = isEngineEnabledInConfig(documentType, engine);
            if (!enabled) {
                logger.debug("{}引擎已禁用 - DocumentType: {}", engine, documentType);
                return false;
            }

            // 2. 优先使用后台健康检查服务（零等待）
            if (engineHealthCheckService != null) {
                boolean available = engineHealthCheckService.isEngineAvailable(engine);
                logger.debug("⚡ 从健康检查服务获取引擎状态 - {}: {}", engine, available);
                return available;
            } else {
                // 3. 降级：健康检查服务未启用，实时检查
                logger.warn("⚠️ 健康检查服务未启用，降级为实时检查 - {}", engine);
                boolean available = checkEngineServiceDirectly(engine);
                return available;
            }

        } catch (Exception e) {
            logger.error("检查引擎 {} 可用性时发生异常", engine, e);
            return false;
        }
    }

    /**
     * 直接检查引擎服务状态（降级方案）
     */
    private boolean checkEngineServiceDirectly(ConvertEngine engine) {
        try {
            switch (engine) {
                case LIBREOFFICE:
                    return libreOfficeConverter.isServiceAvailable();
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("直接检查引擎 {} 服务状态失败", engine, e);
            return false;
        }
    }

    /**
     * 检查引擎在配置中是否启用
     */
    private boolean isEngineEnabledInConfig(DocumentType documentType, ConvertEngine engine) {
        switch (engine) {
            case LIBREOFFICE:
                return isLibreOfficeEnabled(documentType);
            default:
                return false;
        }
    }

    /**
     * 使用指定引擎进行转换（带密码支持）
     */
    private boolean convertWithEngine(ConvertEngine engine, DocumentType documentType, String sourceFilePath,
            String targetFilePath,
            String sourceFormat, String targetFormat, String password) throws Exception {
        switch (engine) {
            case LIBREOFFICE:
                // LibreOffice传递密码参数
                return libreOfficeConverter.convertDocument(sourceFilePath, targetFilePath,
                        sourceFormat, targetFormat,
                        documentType.getType().toUpperCase(), password);
            default:
                throw new IllegalArgumentException("不支持的转换引擎: " + engine);
        }
    }
 
    /**
     * 获取转换引擎状态信息
     */
    public EngineStatus getEngineStatus(DocumentType documentType) {
        EngineStatus status = new EngineStatus();
        status.setLibreOfficeEnabled(isLibreOfficeEnabled(documentType));
        status.setLibreOfficeAvailable(isLibreOfficeEnabled(documentType) && libreOfficeConverter.isServiceAvailable());

        // 获取LibreOffice详细状态
        if (isLibreOfficeEnabled(documentType)) {
            try {
                status.setLibreOfficeStatus(libreOfficeConverter.getLibreOfficeStatus());
                logger.debug("LibreOffice详细状态: {}", status.getLibreOfficeStatus());
            } catch (Exception e) {
                logger.warn("获取LibreOffice详细状态失败", e);
            }
        }
        status.setFallbackEnabled(isFallbackEnabled(documentType));
        status.setPriorityConfig(getEnginePriorityConfig(documentType));
        status.setFileSizeThreshold(getFileSizeThreshold(documentType));

        return status;
    }

    /**
     * 根据文档类型检查LibreOffice是否启用
     */
    private boolean isLibreOfficeEnabled(DocumentType documentType) {
        switch (documentType) {
            case WORD:
                return wordLibreOfficeEnabled;
            case PPT:
                return pptLibreOfficeEnabled;
            case EXCEL:
                return excelLibreOfficeEnabled;
            case VISIO:
                return visioLibreOfficeEnabled;
            default:
                throw new IllegalArgumentException("不支持的文档类型: " + documentType);
        }
    }
    /**
     * 根据文档类型获取文件大小阈值
     */
    private long getFileSizeThreshold(DocumentType documentType) {
        switch (documentType) {
            case WORD:
                return wordFileSizeThreshold;
            case PPT:
                return pptFileSizeThreshold;
            case EXCEL:
                return excelFileSizeThreshold;
            case VISIO:
                return visioFileSizeThreshold;
            default:
                throw new IllegalArgumentException("不支持的文档类型: " + documentType);
        }
    }

    /**
     * 根据文档类型获取引擎优先级配置
     */
    private String getEnginePriorityConfig(DocumentType documentType) {
        switch (documentType) {
            case WORD:
                return wordEnginePriorityConfig;
            case PPT:
                return pptEnginePriorityConfig;
            case EXCEL:
                return excelEnginePriorityConfig;
            case VISIO:
                return visioEnginePriorityConfig;
            default:
                throw new IllegalArgumentException("不支持的文档类型: " + documentType);
        }
    }

    /**
     * 根据文档类型检查是否启用故障转移
     */
    private boolean isFallbackEnabled(DocumentType documentType) {
        switch (documentType) {
            case WORD:
                return wordEnableFallback;
            case PPT:
                return pptEnableFallback;
            case EXCEL:
                return excelEnableFallback;
            case VISIO:
                return visioEnableFallback;
            default:
                throw new IllegalArgumentException("不支持的文档类型: " + documentType);
        }
    }
}