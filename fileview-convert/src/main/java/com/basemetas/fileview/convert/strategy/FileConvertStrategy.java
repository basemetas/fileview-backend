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

import java.util.Map;
import java.util.Set;

/**
 * 文件转换策略接口
 * 
 * 所有文件转换策略实现类必须：
 * 1. 实现此接口
 * 2. 使用 @ConvertStrategy 注解标记
 * 
 * 支持扩展参数传递：
 * - 通过 convertWithParams 方法可以传递额外参数（如密码、水印等）
 * - 默认实现会忽略扩展参数，策略类可按需重写此方法
 * 
 * @author 夫子
 * @version 2.1
 */
public interface FileConvertStrategy {
    /**
     * 转换文件
     * @param filePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @return 转换是否成功
     */
    boolean convert(String filePath, String targetFilePath);
    
    /**
     * 转换文件（扩展版本）
     * @param filePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @return 转换是否成功
     */
    boolean convert(String filePath, String targetFilePath, String targetFileName, String targetFormat);
    
    /**
     * 转换文件（带扩展参数）
     * @param filePath 源文件路径
     * @param targetFilePath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @param convertParams 转换参数（如密码等扩展参数）
     * @return 转换是否成功
     */
    default boolean convertWithParams(String filePath, String targetFilePath, String targetFileName, 
                                      String targetFormat, Map<String, Object> convertParams) {
        // 默认实现：忽略扩展参数，调用基础转换方法
        return convert(filePath, targetFilePath, targetFileName, targetFormat);
    }
    
    /**
     * 检查指定格式是否支持
     * 
     * @param sourceFormat 源文件格式
     * @param targetFormat 目标文件格式
     * @return 是否支持该格式转换
     */
    boolean isConversionSupported(String sourceFormat, String targetFormat);
    
    /**
     * 获取支持的源文件格式列表
     * 
     * @return 支持的源文件格式集合
     */
    Set<String> getSupportedSourceFormats();
    
    /**
     * 获取支持的目标文件格式列表
     * 
     * @return 支持的目标文件格式集合
     */
    Set<String> getSupportedTargetFormats();
}