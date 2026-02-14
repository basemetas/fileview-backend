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

import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import com.basemetas.fileview.convert.annotation.ConvertStrategy;
import com.basemetas.fileview.convert.strategy.impl.converter.Cad2xConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.Set;

/**
 * CAD 文件转换策略
 * 使用 Cad2xConverter 进行独立转换，不依赖统一引擎选择器
 * 类似压缩文件策略的独立处理模式
 * 
 * 支持的 CAD 格式：DWG, DXF, DWF
 * 支持的目标格式：PDF, PNG, JPG, JPEG, SVG, DXF
 * 
 * @author 夫子
 */
@Component
@ConvertStrategy(
    category = FileCategory.CAD,
    name = "CAD文件转换",
    description = "支持 DWG、DXF、DWF 等 CAD 文件转换为 PDF、PNG、JPG 等格式",
    priority = 100
)
public class CadConvertStrategy implements FileConvertStrategy {
    private static final Logger logger = LoggerFactory.getLogger(CadConvertStrategy.class);

    private final Cad2xConverter cad2xConverter;
    private final FileTypeMapper fileTypeMapper;

    public CadConvertStrategy(Cad2xConverter cad2xConverter, FileTypeMapper fileTypeMapper) {
        this.cad2xConverter = cad2xConverter;
        this.fileTypeMapper = fileTypeMapper;
    }

    @Override
    public boolean convert(String filePath, String targetFilePath) {
        logger.info("开始 CAD 文件转换(简化版) - 源文件: {}, 目标文件: {}", filePath, targetFilePath);
        
        // 从目标文件路径提取格式
        String targetFormat = extractFileExtension(targetFilePath);
        String sourceFormat = extractFileExtension(filePath);
        
        return performConvert(filePath, targetFilePath, sourceFormat, targetFormat);
    }

    @Override
    public boolean convert(String filePath, String targetPath, String targetFileName, String targetFormat) {
        logger.info("开始 CAD 文件转换 - 源文件: {}, 目标格式: {}", filePath, targetFormat);
        
        // 构建完整的目标文件路径
        String fullTargetPath = buildTargetFilePath(targetPath, targetFileName, targetFormat);
        String sourceFormat = extractFileExtension(filePath);
        
        return performConvert(filePath, fullTargetPath, sourceFormat, targetFormat);
    }

    /**
     * 执行实际的转换逻辑
     */
    private boolean performConvert(String sourcePath, String targetPath, String sourceFormat, String targetFormat) {
        try {
            // 1. 检查格式支持
            if (!isConversionSupported(sourceFormat, targetFormat)) {
                logger.error("不支持的转换: {} -> {}", sourceFormat, targetFormat);
                return false;
            }

            // 2. 检查服务可用性
            if (!cad2xConverter.isServiceAvailable()) {
                logger.error("CAD2X 转换服务不可用，请检查配置和环境");
                return false;
            }

            // 3. 检查源文件存在
            File sourceFile = new File(sourcePath);
            if (!sourceFile.exists()) {
                logger.error("源文件不存在: {}", sourcePath);
                return false;
            }

            // 4. 确保目标目录存在
            File targetFile = new File(targetPath);
            File targetDir = targetFile.getParentFile();
            if (targetDir != null && !targetDir.exists()) {
                boolean dirCreated = targetDir.mkdirs();
                if (!dirCreated) {
                    logger.warn("无法创建目标目录: {}", targetDir.getAbsolutePath());
                }
            }

            // 5. 执行转换
            boolean success = cad2xConverter.convert(sourcePath, targetPath, sourceFormat, targetFormat);

            if (success) {
                // 6. 验证输出文件
                if (targetFile.exists() && targetFile.length() > 0) {
                    logger.info("CAD 文件转换成功 - 目标文件: {}, 大小: {} bytes", 
                        targetPath, targetFile.length());
                    return true;
                } else {
                    logger.error("转换完成但目标文件不存在或为空");
                    return false;
                }
            } else {
                logger.error("CAD 文件转换失败");
                return false;
            }

        } catch (Exception e) {
            logger.error("CAD 转换系统异常", e);
            return false;
        }
    }

    @Override
    public boolean isConversionSupported(String sourceFormat, String targetFormat) {
        if (sourceFormat == null || targetFormat == null) {
            return false;
        }

        String srcFormat = sourceFormat.toLowerCase().trim();
        String tgtFormat = targetFormat.toLowerCase().trim();

        // 检查源格式是否属于 CAD 类别
        Set<String> supportedSources = fileTypeMapper.getFormatsByCategory(FileCategory.CAD);
        if (!supportedSources.contains(srcFormat)) {
            logger.debug("不支持的 CAD 源格式: {}", srcFormat);
            return false;
        }

        // 检查目标格式是否支持
        return fileTypeMapper.isConversionSupportedByExtension(srcFormat, srcFormat, tgtFormat);
    }

    @Override
    public Set<String> getSupportedSourceFormats() {
        return fileTypeMapper.getFormatsByCategory(FileCategory.CAD);
    }

    @Override
    public Set<String> getSupportedTargetFormats() {
        return fileTypeMapper.getSupportedTargetFormats(FileCategory.CAD);
    }

    /**
     * 构建目标文件完整路径
     */
    private String buildTargetFilePath(String targetPath, String targetFileName, String targetFormat) {
        // 确保路径以分隔符结尾
        String normalizedPath = targetPath;
        if (!normalizedPath.endsWith(File.separator) && !normalizedPath.endsWith("/")) {
            normalizedPath += File.separator;
        }

        // 构建完整文件名
        String fullFileName = targetFileName;
        String formatLower = targetFormat.toLowerCase();

        if (!targetFileName.toLowerCase().endsWith("." + formatLower)) {
            fullFileName += "." + formatLower;
        }

        return normalizedPath + fullFileName;
    }

    /**
     * 提取文件扩展名
     */
    private String extractFileExtension(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }

        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }

        return "";
    }
}
