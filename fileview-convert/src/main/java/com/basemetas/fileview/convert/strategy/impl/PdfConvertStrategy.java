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
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import java.util.Set;

/**
 * PDF文件转换策略实现类
 * 
 * 支持PDF格式转换
 * 
 * @author 夫子
 */
@ConvertStrategy(
    category =  FileCategory.PDF,
    description = "PDF文件转换（PDFBox）",
    priority = 50
)
public class PdfConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(PdfConvertStrategy.class);
    
    @Autowired
    private FileTypeMapper fileTypeMapper;
    
    @Autowired
    private ValidateAndNormalized validateAndNormalized;
    
    @PostConstruct
    public void init() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.DOCUMENT);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.DOCUMENT);
        
        logger.info("PDF转换策略初始化完成 - 类型: DOCUMENT, 支持源格式({}种): {}", 
                   sourceFormats.size(), sourceFormats);
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);
    }

    @Override
    public boolean convert(String sourceFilePath, String targetFilePath) {
        logger.info("Converting PDF file from {} to {}", sourceFilePath, targetFilePath);
        // PDF转换逻辑实现
        // 这里只是一个示例，实际实现可能需要使用Apache PDFBox或其他PDF处理库
        try {
            // 模拟转换过程
            Thread.sleep(1000);
            logger.info("PDF file converted successfully");
            return true;
        } catch (Exception e) {
            logger.error("Failed to convert PDF file", e);
            return false;
        }
    }
    
    /**
     * 转换文件
     * @param filePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @return 转换是否成功
     */
    @Override
    public boolean convert(String filePath, String targetFilePath,String targetFileName,String targetFormat){
        logger.info("开始PDF文档转换 - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}", 
                   filePath, targetFilePath, targetFileName, targetFormat);
        
        try {
            // 1. 使用统一工具类验证参数
            ValidateAndNormalized.ValidationResult validationResult = 
                validateAndNormalized.validateConversionParameters(filePath, targetFilePath, targetFileName, targetFormat);
            
            if (!validationResult.isSuccess()) {
                logger.error("参数验证失败: {}", validationResult.getMessage());
                return false;
            }
            
            // 获取验证后的源文件路径
            String correctedSourcePath = validationResult.getCorrectedPath();
            
            // 2. 构建完整的目标文件路径
            String fullTargetPath = validateAndNormalized.buildTargetFilePathEnhanced(targetFilePath, targetFileName, targetFormat);
            
            // 3. 确保目标目录存在
            if (!validateAndNormalized.ensureTargetDirectory(fullTargetPath)) {
                logger.error("无法创建或访问目标目录");
                return false;
            }
            
            // 4. 执行转换
            return convert(correctedSourcePath, fullTargetPath);
            
        } catch (Exception e) {
            logger.error("PDF文档转换异常 - 源文件: {}", filePath, e);
            return false;
        }
    }
     
    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.DOCUMENT, sourceFormat, targetFormat);
    }
    
    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.DOCUMENT);
    }
    
    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.DOCUMENT);
    }
}