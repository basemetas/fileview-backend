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
package com.basemetas.fileview.convert.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.File;

/**
 * 文件转换请求基础模型类
 * 
 * 提供所有文件格式转换的通用参数封装
 * 各种具体格式的转换请求可以继承此类并扩展特定参数
 * 
 * @author 夫子
 * @version 2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseConvertRequest {
    
    // ========== 基础必需参数 ==========
    
    /**
     * 文件ID - 用于标识文件
     * 非必填参数
     */
    private String fileId;
    
    /**
     * 源文件名 - 原始文件名称（包含扩展名）
     * 可选参数，如果为空则从filePath中提取
     */
    private String fileName;
    
    /**
     * 源文件路径 - 待转换文件的完整路径
     * 必填参数
     */
    private String filePath;
    
    /**
     * 目标文件路径 - 转换后文件存放的目录路径
     * 非必填参数
     */
    private String targetPath;
    
    /**
     * 目标文件名 - 转换后的文件名（不含扩展名）
     * 非必填参数
     */
    private String targetFileName;
    
    /**
     * 目标格式 - 转换后的文件格式（扩展名）
     * 必填参数，如: pdf, png, html等
     */
    private String targetFormat;
    
    // ========== 自动检测参数 ==========
    
    /**
     * 源文件格式 - 从filePath自动提取
     * 自动填充，也可手动设置
     */
    private String sourceFormat;
    
    /**
     * 文件密码 - 用于加密文件的解密
     * 可选参数，如果文件需要密码才能打开则必填
     */
    private String filePassword;
    
    // ========== 构造函数 ==========
    
    /**
     * 默认构造函数
     */
    public BaseConvertRequest() {
    }
    
    /**
     * 完整参数构造函数
     * 
     * @param fileId 文件ID
     * @param fileName 源文件名
     * @param filePath 源文件路径
     * @param targetPath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     */
    public BaseConvertRequest(String fileId, String fileName, String filePath, 
                             String targetPath, String targetFileName, String targetFormat) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.targetPath = targetPath;
        this.targetFileName = targetFileName;
        this.targetFormat = targetFormat;
        // 自动提取源格式
        this.sourceFormat = extractFileExtension(filePath);
        // 如果fileName为空，从filePath提取
        if (this.fileName == null && filePath != null) {
            this.fileName = new File(filePath).getName();
        }
    }
    
    /**
     * 基础参数构造函数（不含fileName）
     * 
     * @param fileId 文件ID
     * @param filePath 源文件路径
     * @param targetPath 目标文件路径
     * @param targetFileName 目标文件名
     * @param targetFormat 目标格式
     */
    public BaseConvertRequest(String fileId, String filePath, String targetPath, 
                             String targetFileName, String targetFormat) {
        this(fileId, null, filePath, targetPath, targetFileName, targetFormat);
    }
    
    // ========== Getters and Setters ==========
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
        // 自动更新源格式
        if (filePath != null) {
            this.sourceFormat = extractFileExtension(filePath);
            // 如果fileName为空，自动提取
            if (this.fileName == null) {
                this.fileName = new File(filePath).getName();
            }
        }
    }
    
    public String getTargetPath() {
        return targetPath;
    }
    
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }
    
    public String getTargetFileName() {
        return targetFileName;
    }
    
    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }
    
    public String getTargetFormat() {
        return targetFormat;
    }
    
    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat;
    }
    
    public String getSourceFormat() {
        return sourceFormat;
    }
    
    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }
    
    public String getFilePassword() {
        return filePassword;
    }
    
    public void setFilePassword(String filePassword) {
        this.filePassword = filePassword;
    }
    
    // ========== 业务方法 ==========
    
    /**
     * 验证基础参数是否有效
     * 
     * @return true-参数有效, false-参数无效
     */
    public boolean isValidBasicParams() {
        return filePath != null && !filePath.trim().isEmpty() &&
               targetFormat != null && !targetFormat.trim().isEmpty();
        // targetPath 和 targetFileName 不再是必需参数，
        // 如果为空将使用默认值
    }
    
    /**
     * 获取完整目标文件路径（包含扩展名）
     * 
     * @return 完整的目标文件路径，格式: targetPath/targetFileName.targetFormat
     */
    public String getFullTargetPath() {
        if (targetPath == null || targetFileName == null || targetFormat == null) {
            return null;
        }
        // 确保路径分隔符正确
        String separator = targetPath.endsWith("/") || targetPath.endsWith("\\") ? "" : "/";
        return targetPath + separator + targetFileName + "." + targetFormat;
    }
      
    /**
     * 从文件路径提取扩展名
     * 
     * @param filePath 文件路径
     * @return 文件扩展名（小写），如果无法提取则返回空字符串
     */
    protected String extractFileExtension(String filePath) {
        if (filePath == null || !filePath.contains(".")) {
            return "";
        }
        
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        
        return "";
    }
    
    // ========== 对象方法 ==========
    
    @Override
    public String toString() {
        return String.format(
            "BaseConvertRequest{fileId='%s', fileName='%s', sourceFormat='%s', targetFormat='%s', filePath='%s', targetPath='%s', hasPassword=%s}",
            fileId, fileName, sourceFormat, targetFormat, filePath, targetPath, (filePassword != null && !filePassword.isEmpty())
        );
    }
    
    /**
     * 复制当前请求对象
     * 
     * @return 新的请求对象副本
     */
    public BaseConvertRequest copy() {
        BaseConvertRequest copy = new BaseConvertRequest();
        copy.fileId = this.fileId;
        copy.fileName = this.fileName;
        copy.filePath = this.filePath;
        copy.targetPath = this.targetPath;
        copy.targetFileName = this.targetFileName;
        copy.targetFormat = this.targetFormat;
        copy.sourceFormat = this.sourceFormat;
        copy.filePassword = this.filePassword;
        return copy;
    }
}
