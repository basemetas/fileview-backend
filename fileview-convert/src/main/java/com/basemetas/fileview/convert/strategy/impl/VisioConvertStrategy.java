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
import com.basemetas.fileview.convert.strategy.impl.converter.ConvertEngineSelector;
import com.basemetas.fileview.convert.service.TempFileHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.basemetas.fileview.convert.strategy.model.ConvertResult;
import com.basemetas.fileview.convert.strategy.model.EngineStatus;
import jakarta.annotation.PostConstruct;
import java.util.Set;

/**
 * Visio流程图转换策略实现类
 * 
 * 当前仅使用 LibreOffice 命令行作为转换引擎。
 * 
 * 支持Visio格式（共6种源格式）：
 * - Microsoft Visio: vsdm, vsdx, vssm, vssx, vstm, vstx
 * - 其他格式: bpmn, xmind
 * 
 * 特性：
 * - 智能引擎选择和故障转移
 * - 支持多种输出格式（pdf, png, jpg等）
 * - 临时文件HTTP访问服务
 * - 完善的错误处理和日志记录
 * - Visio专用优化
 * 
 * @author 夫子
 */
@ConvertStrategy(
    category = FileCategory.VISIO,
    description = "Visio流程图转换（LibreOffice）",
    priority = 100
)
public class VisioConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(VisioConvertStrategy.class);
    
    @Autowired
    private ConvertEngineSelector engineSelector;
    
    @Autowired
    private TempFileHttpService tempFileHttpService;
    
    @Autowired
    private FileTypeMapper fileTypeMapper;
    
    @Autowired
    private ValidateAndNormalized validateAndNormalized;
    
    @PostConstruct
    public void init() {
        // 启动临时文件HTTP服务的清理任务
        tempFileHttpService.startCleanupTask();
        
        // 从格式注册中心获取格式信息
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.VISIO);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.VISIO);
        
        logger.info("Visio转换策略初始化完成 - 类型: VISIO, 支持源格式({}种): {}", 
                   sourceFormats.size(), sourceFormats);
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);
    }

    @Override
    public boolean convert(String sourceFilePath, String targetFilePath) {
        String targetFormat = validateAndNormalized.extractFileExtension(targetFilePath);        
        if (targetFormat.isEmpty()) {
            targetFormat = "pdf"; // 默认转换为PDF
        }       
        return convert(sourceFilePath, targetFilePath, 
                      new java.io.File(targetFilePath).getName().replaceAll("\\.[^.]+$", ""), 
                      targetFormat);
    }

    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.VISIO, sourceFormat, targetFormat);
    }

    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.VISIO);
    }

    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.VISIO);
    }

    /**
     * 转换文件（带缓存支持）
     * @param filePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @return 转换是否成功
     */
    @Override
    public boolean convert(String filePath, String targetFilePath, String targetFileName, String targetFormat) {
        logger.info("开始Visio流程图转换 - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}", 
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
            
            // 2. 检测源文件格式
            String sourceFormat = validateAndNormalized.extractFileExtension(correctedSourcePath);
            
            // 3. 使用格式注册中心验证格式支持
            if (!fileTypeMapper.isConversionSupported(FileCategory.VISIO, sourceFormat, targetFormat)) {
                logger.error("不支持的Visio流程图转换: {} -> {}", sourceFormat, targetFormat);
                logger.error("支持的源格式: {}", fileTypeMapper.getSupportedSourceFormats(FileCategory.VISIO));
                logger.error("支持的目标格式: {}", fileTypeMapper.getSupportedTargetFormats(FileCategory.VISIO));
                return false;
            }
            
            // 4. 构建完整的目标文件路径
            String fullTargetPath = validateAndNormalized.buildTargetFilePathEnhanced(targetFilePath, targetFileName, targetFormat);
            
            // 5. 确保目标目录存在
            if (!validateAndNormalized.ensureTargetDirectory(fullTargetPath)) {
                logger.error("无法创建或访问目标目录");
                return false;
            }
            
            // 6. 使用智能引擎选择器进行转换
            ConvertResult result = engineSelector.convertDocument(
                ConvertEngineSelector.DocumentType.VISIO, correctedSourcePath, fullTargetPath, sourceFormat, targetFormat
            );
            
            if (result.isSuccess()) {
                logger.info("Visio流程图转换成功 - 源文件: {}, 目标文件: {}, 使用引擎: {}", 
                           correctedSourcePath, fullTargetPath, result.getEngine() != null ? result.getEngine().getDescription() : "未知引擎");
                return true;
            } else {
                logger.error("Visio流程图转换失败 - 错误: {}", result.getErrorMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Visio流程图转换异常 - 源文件: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 获取转换引擎状态
     */
    public EngineStatus getEngineStatus() {
        return engineSelector.getEngineStatus(ConvertEngineSelector.DocumentType.VISIO);
    }
    
    /**
     * 检查指定格式是否支持
     */
    public boolean isFormatSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.VISIO, sourceFormat, targetFormat);
    }
}