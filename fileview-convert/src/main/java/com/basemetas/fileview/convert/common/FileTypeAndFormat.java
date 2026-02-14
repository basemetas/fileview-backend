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
package com.basemetas.fileview.convert.common;

import com.basemetas.fileview.convert.config.FileTypeMapper;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;

/**
 * 文件类型和格式处理工具类
 * 提供文件路径处理、目录创建、格式检测等功能
 */
@Component
public class FileTypeAndFormat {
    
    private final FileTypeMapper fileTypeMapper;
    
    public FileTypeAndFormat(FileTypeMapper fileTypeMapper) {
        this.fileTypeMapper = fileTypeMapper;
    }

    /**
     * 从文件路径中提取文件扩展名
     * 委托给FileTypeMapper处理，支持特殊格式如tar.gz
     * 
     * @param filePath 文件路径
     * @return 文件扩展名（小写）
     */
    public String getFileExtension(String filePath) {
        return fileTypeMapper.extractExtension(filePath);
    }
    /**
     * 构建目标文件完整路径
     * 
     * @param targetPath 目标目录路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标文件格式
     * @return 完整的目标文件路径
     */
    public String buildTargetFilePath(String targetPath, String targetFileName, String targetFormat) {
        String normalizedTargetPath = targetPath;
        if (!normalizedTargetPath.endsWith("/") && !normalizedTargetPath.endsWith("\\")) {
            normalizedTargetPath += File.separator;
        }
        return normalizedTargetPath + targetFileName + "." + targetFormat;
    }
    /**
     * 创建目标目录
     * 
     * @param targetPath 目标目录路径
     * @throws IOException 如果目录创建失败
     */
    public void createTargetDirectory(String targetPath) throws IOException {
        File targetDir = new File(targetPath);
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                throw new IOException("Failed to create target directory: " + targetDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * 检测压缩文件格式
     * 使用FileTypeMapper统一管理扩展名识别
     * 
     * @param filePath 文件路径
     * @return 压缩文件格式，如果无法识别则返回"unknown"
     */
    public String detectArchiveFormat(String filePath) {
        String extension = fileTypeMapper.extractExtension(filePath);
        return fileTypeMapper.isSupported(extension) ? extension : "unknown";
    }
    
    /**
     * 检测OFD文件格式
     * 
     * @param filePath 文件路径
     * @return 如果是OFD文件返回"ofd"，否则返回"unknown"
     */
    public String detectOfdFormat(String filePath) {
        String extension = fileTypeMapper.extractExtension(filePath);
        return "ofd".equals(extension) ? "ofd" : "unknown";
    }
    
    /**
     * 检查是否为OFD文件
     * 
     * @param filePath 文件路径
     * @return true表示是OFD文件，false表示不是
     */
    public boolean isOfdFile(String filePath) {
        return "ofd".equals(detectOfdFormat(filePath));
    }
}