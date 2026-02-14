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
import com.basemetas.fileview.convert.common.exception.UnsupportedException;
import com.basemetas.fileview.convert.service.TempFileHttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.basemetas.fileview.convert.strategy.model.ConvertResult;
import com.basemetas.fileview.convert.strategy.model.EngineStatus;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.Map;

/**
 * Excel文档转换策略实现类
 * 
 * 支持多种转换引擎：
 * 
 * 当前仅使用 LibreOffice 命令行作为转换引擎。
 * 
 * 支持Excel格式（共16种源格式）：
 * - Microsoft Excel: xls, xlsx, xlsm, xlsb, xlt, xltm, xltx
 * - 开放文档: ods, ots, fods, sxc
 * - 其他格式: csv, et, ett, numbers, xml
 * 
 * 特性：
 * - 智能引擎选择和故障转移
 * - 支持多种输出格式（pdf, png, jpg, csv, xlsx等）
 * - 临时文件HTTP访问服务
 * - 完善的错误处理和日志记录
 * - Excel专用优化（公式、图表、宏支持）
 * 
 * @author 夫子
 */
@ConvertStrategy(
    category = FileCategory.SPREADSHEET,
    description = "Excel表格转换（LibreOffice）",
    priority = 100
)
public class ExcelConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ExcelConvertStrategy.class);
    
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
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.SPREADSHEET);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.SPREADSHEET);
        
        logger.info("Excel转换策略初始化完成 - 类型: SPREADSHEET, 支持源格式({}种): {}", 
                   sourceFormats.size(), sourceFormats);
        logger.info("支持目标格式({}种): {}", targetFormats.size(), targetFormats);
    }

    @Override
    public boolean convert(String sourceFilePath, String targetFilePath) {
        logger.warn("使用已废弃的Excel转换方法，请使用带格式参数的转换方法");
        return false;
    }

    /**
     * 转换Excel文件（增强版，支持多引擎智能选择）
     * @param sourceFilePath 源文件路径
     * @param targetPath 目标路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @return 转换是否成功
     */
    @Override
    public boolean convert(String sourceFilePath, String targetPath, String targetFileName, String targetFormat) {
        logger.info("开始Excel多引擎转换 - 源文件: {}, 目标路径: {}, 目标文件名: {}, 目标格式: {}", 
                   sourceFilePath, targetPath, targetFileName, targetFormat);
        
        try {
            // 1. 使用统一工具类验证参数
            ValidateAndNormalized.ValidationResult validationResult = 
                validateAndNormalized.validateConversionParameters(sourceFilePath, targetPath, targetFileName, targetFormat);
            
            if (!validationResult.isSuccess()) {
                logger.error("参数验证失败: {}", validationResult.getMessage());
                return false;
            }
            
            // 获取验证后的源文件路径
            String correctedSourceFilePath = validationResult.getCorrectedPath();
            
            // 2. 构建完整的目标文件路径
            String targetFilePath = validateAndNormalized.buildTargetFilePathEnhanced(targetPath, targetFileName, targetFormat);
            
            // 3. 检测源文件格式
            String sourceFormat = validateAndNormalized.extractFileExtension(correctedSourceFilePath);
            if (sourceFormat == null || sourceFormat.isEmpty()) {
                logger.error("无法检测Excel源文件格式: {}", correctedSourceFilePath);
                return false;
            }
            
            // 4. 使用格式注册中心验证格式支持
            if (!fileTypeMapper.isConversionSupported(FileCategory.SPREADSHEET, sourceFormat, targetFormat)) {
                logger.error("不支持的Excel格式转换: {} -> {}", sourceFormat, targetFormat);
                logger.error("支持的源格式: {}", fileTypeMapper.getSupportedSourceFormats(FileCategory.SPREADSHEET));
                logger.error("支持的目标格式: {}", fileTypeMapper.getSupportedTargetFormats(FileCategory.SPREADSHEET));
                return false;
            }
            
            // 5. 使用引擎选择器进行智能转换
                ConvertResult result = engineSelector.convertDocument(
                ConvertEngineSelector.DocumentType.EXCEL, correctedSourceFilePath, targetFilePath, sourceFormat, targetFormat);
            
            if (result.isSuccess()) {
                logger.info("✅ Excel文档转换成功 - 源文件: {}, 目标文件: {}, 使用引擎: {}", 
                           correctedSourceFilePath, targetFilePath, result.getEngine() != null ? result.getEngine().getDescription() : "未知引擎");
                return true;
            } else {
                logger.error("❌ Excel文档转换失败 - 源文件: {}, 错误: {}", 
                            correctedSourceFilePath, result.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Excel转换异常 - 源文件: {}", sourceFilePath, e);
            return false;
        }
    }
    
    /**
     * 转换Excel文件（支持扩展参数，如密码）
     * @param filePath 源文件路径
     * @param targetFilePath 目标路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     * @param convertParams 转换参数（可包含密码等）
     * @return 转换是否成功
     */
    @Override
    public boolean convertWithParams(String filePath, String targetFilePath, String targetFileName, 
                                      String targetFormat, Map<String, Object> convertParams) {
        logger.info("开始Excel文档转换（带参数） - 源文件: {}, 目标路径: {}, 文件名: {}, 格式: {}", 
                   filePath, targetFilePath, targetFileName, targetFormat);
        
        // 提取密码参数
        String password = null;
        if (convertParams != null && convertParams.containsKey("password")) {
            password = (String) convertParams.get("password");
            logger.info("🔑 检测到密码参数，将传递给转换引擎");
        }
        
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
            
            // 3. 检测源文件格式
            String sourceFormat = validateAndNormalized.extractFileExtension(correctedSourcePath);
            if (sourceFormat == null || sourceFormat.isEmpty()) {
                logger.error("无法检测Excel源文件格式: {}", correctedSourcePath);
                return false;
            }
            
            // 4. 使用格式注册中心验证格式支持
            if (!fileTypeMapper.isConversionSupported(FileCategory.SPREADSHEET, sourceFormat, targetFormat)) {
                logger.error("不支持的Excel格式转换: {} -> {}", sourceFormat, targetFormat);
                return false;
            }
            
            // 5. 使用智能引擎选择器进行转换（传递密码）
            ConvertResult result = engineSelector.convertDocument(
                ConvertEngineSelector.DocumentType.EXCEL, correctedSourcePath, fullTargetPath, 
                sourceFormat, targetFormat, password
            );
            
            if (result.isSuccess()) {
                logger.info("✅ Excel文档转换成功 - 源文件: {}, 目标文件: {}, 使用引擎: {}", 
                           correctedSourcePath, fullTargetPath, 
                           result.getEngine() != null ? result.getEngine().getDescription() : "未知引擎");
                return true;
            } else {
                // 🔑 关键修复：如果 ConvertResult 包含 errorCode，抛出异常传递
                String errorCode = result.getErrorCode();
                if (errorCode != null && !errorCode.isEmpty()) {
                    logger.warn("⚠️ Excel转换失败，抛出引擎不支持异常 - ErrorCode: {}, Message: {}", 
                               errorCode, result.getMessage());
                    throw new UnsupportedException(
                        errorCode, result.getMessage()
                    );
                }
                logger.error("❌ Excel文档转换失败 - 源文件: {}, 错误: {}", correctedSourcePath, result.getMessage());
                return false;
            }
            
        } catch (UnsupportedException e) {
            // 🔑 关键修复：引擎不支持异常需要重新抛出
            logger.warn("⚠️ Excel转换策略 catch 到引擎不支持异常，重新抛出 - ErrorCode: {}", e.getErrorCode());
            throw e; // 重新抛出，让上层捕获
        } catch (Exception e) {
            logger.error("Excel转换异常 - 源文件: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 检查指定格式是否支持
     * 
     * @param sourceFormat 源文件格式
     * @param targetFormat 目标文件格式
     * @return 是否支持该格式转换
     */
    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        return fileTypeMapper.isConversionSupported(FileCategory.SPREADSHEET, sourceFormat, targetFormat);
    }
    
    /**
     * 获取支持的源文件格式列表
     * 
     * @return 支持的源文件格式集合
     */
    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getSupportedSourceFormats(FileCategory.SPREADSHEET);
    }
    
    /**
     * 获取支持的目标文件格式列表
     * 
     * @return 支持的目标文件格式集合
     */
    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.SPREADSHEET);
    }
    
    /**
     * 获取转换引擎状态信息
     * 
     * @return 引擎状态信息
     */
    public EngineStatus getEngineStatus() {
        return engineSelector.getEngineStatus(ConvertEngineSelector.DocumentType.EXCEL);
    }
}