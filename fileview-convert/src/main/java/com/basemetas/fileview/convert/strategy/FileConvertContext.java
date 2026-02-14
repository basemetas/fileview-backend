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
package com.basemetas.fileview.convert.strategy;

import com.basemetas.fileview.convert.annotation.ConvertStrategy;
import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Comparator;

/**
 * 文件转换策略上下文
 * 
 * 通过注解自动注册和管理转换策略
 * 支持通过文件扩展名或文件类别获取策略
 * 
 * @author 夫子
 * @version 2.0
 */
@Component
public class FileConvertContext {
    
    private static final Logger logger = LoggerFactory.getLogger(FileConvertContext.class);
    
    /**
     * 策略类型 -> 策略实现 的映射
     * Key: 策略类型字符串（如"image", "tdf"等）
     * Value: 对应的策略实现
     */
    private final Map<String, FileConvertStrategy> strategyMap = new HashMap<>();
    
    /**
     * 文件类别 -> 策略实现 的映射
     * Key: 文件类别枚举
     * Value: 对应的策略实现
     */
    private final Map<FileCategory, FileConvertStrategy> categoryStrategyMap = new EnumMap<>(FileCategory.class);
    
    private final FileTypeMapper fileTypeMapper;

    @Autowired
    public FileConvertContext(List<FileConvertStrategy> strategies, FileTypeMapper fileTypeMapper) {
        this.fileTypeMapper = fileTypeMapper;
        registerStrategies(strategies);
        logRegisteredStrategies();
    }

    /**
     * 自动注册所有带 @ConvertStrategy 注解的策略实现
     * 
     * @param strategies Spring 自动注入的所有策略实现
     */
    private void registerStrategies(List<FileConvertStrategy> strategies) {
        logger.info("开始注册文件转换策略...");
        
        // 按优先级排序（优先级高的排在前面）
        strategies.stream()
            .sorted(Comparator.comparing(strategy -> {
                ConvertStrategy annotation = strategy.getClass().getAnnotation(ConvertStrategy.class);
                return annotation != null ? -annotation.priority() : 0; // 负号使高优先级排前面
            }))
            .forEach(strategy -> {
                ConvertStrategy annotation = strategy.getClass().getAnnotation(ConvertStrategy.class);
                
                if (annotation == null) {
                    logger.warn("策略 {} 未标记 @ConvertStrategy 注解，跳过注册", 
                               strategy.getClass().getSimpleName());
                    return;
                }
                
                FileCategory category = annotation.category();
                String strategyType = category.getStrategyType();
                String strategyName = annotation.name().isEmpty() ? strategyType : annotation.name();
                
                // 注册到策略类型映射
                strategyMap.put(strategyType.toLowerCase(), strategy);
                if (!strategyName.equals(strategyType)) {
                    strategyMap.put(strategyName.toLowerCase(), strategy);
                }          
                // 注册到文件类别映射
                categoryStrategyMap.put(category, strategy);
            });
        
        logger.info("策略注册完成，共注册 {} 个策略", categoryStrategyMap.size());
    }
    
    /**
     * 记录已注册的策略信息
     */
    private void logRegisteredStrategies() {
        logger.info("========== 已注册的转换策略 ==========");
        categoryStrategyMap.forEach((category, strategy) -> {
            logger.info("[{}] {} -> {}",
                       category.getStrategyType(),
                       category.getDescription(),
                       strategy.getClass().getSimpleName());
        });
        logger.info("====================================");
    }

    /**
     * 根据文件类型获取对应的转换策略
     * 
     * 支持多种查找方式：
     * 1. 直接通过策略类型名称（如"image", "tdf"）
     * 2. 通过文件扩展名（如"png", "docx"）
     * 3. 通过文件类别枚举
     * 
     * @param fileType 文件类型、扩展名或策略类型
     * @return 对应的转换策略，如果不支持则返回null
     */
    public FileConvertStrategy getStrategy(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return null;
        }
        
        String lowerFileType = fileType.toLowerCase();
        
        // 1. 首先尝试直接匹配策略类型
        FileConvertStrategy strategy = strategyMap.get(lowerFileType);
        if (strategy != null) {
            logger.debug("通过策略类型匹配: {} -> {}", fileType, strategy.getClass().getSimpleName());
            return strategy;
        }
        
        // 2. 通过 FileTypeMapper 将扩展名映射到文件类别，再获取策略
        FileCategory category = fileTypeMapper.getFileCategory(lowerFileType);
        if (category != null) {
            strategy = categoryStrategyMap.get(category);
            if (strategy != null) {
                logger.debug("通过扩展名映射: {} -> {} -> {}", 
                           fileType, category, strategy.getClass().getSimpleName());
                return strategy;
            }
        }
        
        logger.warn("未找到对应的转换策略: {}", fileType);
        return null;
    }
    
    /**
     * 根据文件类别获取对应的转换策略
     * 
     * @param category 文件类别
     * @return 对应的转换策略，如果不支持则返回null
     */
    public FileConvertStrategy getStrategyByCategory(FileCategory category) {
        if (category == null) {
            return null;
        }
        return categoryStrategyMap.get(category);
    }
   
    /**
     * 转换文件，指定目标格式（带扩展参数）
     * 
     * @param fileType 文件类型或扩展名
     * @param filePath 源文件路径
     * @param targetPath 目标路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     * @param convertParams 转换参数（如密码等）
     * @return 转换是否成功
     * @throws IllegalArgumentException 如果文件类型不支持
     */
    public boolean convertFileWithParams(String fileType, String filePath, String targetPath, 
                                         String targetFileName, String targetFormat, 
                                         Map<String, Object> convertParams) {
        FileConvertStrategy strategy = getStrategy(fileType);
        if (strategy != null) {
            logger.info("使用策略 {} 转换文件: {} -> {}.{}", 
                       strategy.getClass().getSimpleName(), filePath, targetFileName, targetFormat);
            
            // 调用策略的 convertWithParams 方法（所有策略都支持）
            if (convertParams != null && !convertParams.isEmpty()) {
                return strategy.convertWithParams(filePath, targetPath, targetFileName, targetFormat, convertParams);
            } else {
                return strategy.convert(filePath, targetPath, targetFileName, targetFormat);
            }
        }
        throw new IllegalArgumentException("不支持的文件类型: " + fileType);
    }
    
    /**
     * 转换文件，指定目标格式
     * 
     * @param fileType 文件类型或扩展名
     * @param filePath 源文件路径
     * @param targetPath 目标路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     * @return 转换是否成功
     * @throws IllegalArgumentException 如果文件类型不支持
     */
    public boolean convertFile(String fileType, String filePath, String targetPath, 
                               String targetFileName, String targetFormat) {
        return convertFileWithParams(fileType, filePath, targetPath, targetFileName, targetFormat, null);
    }
    
    /**
     * 根据文件路径自动检测文件类型并转换
     * 使用 FileTypeMapper 提取文件扩展名（支持特殊格式如tar.gz）
     * 
     * @param filePath 源文件路径
     * @param targetPath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     * @return 转换是否成功
     * @throws IllegalArgumentException 如果无法确定文件类型
     */
    public boolean convertFileByPath(String filePath, String targetPath, 
                                     String targetFileName, String targetFormat) {
        String extension = fileTypeMapper.extractExtension(filePath);
        
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("无法从路径确定文件类型: " + filePath);
        }
        
        logger.info("自动检测文件类型: {} -> 扩展名: {}", filePath, extension);
        return convertFile(extension, filePath, targetPath, targetFileName, targetFormat);
    }
    
    /**
     * 检查指定文件类型是否支持
     * 
     * @param fileType 文件类型或扩展名
     * @return true表示支持，false表示不支持
     */
    public boolean isSupported(String fileType) {
        return getStrategy(fileType) != null;
    }
    
    /**
     * 获取所有已注册的策略数量
     * 
     * @return 策略数量
     */
    public int getRegisteredStrategyCount() {
        return categoryStrategyMap.size();
    }
}