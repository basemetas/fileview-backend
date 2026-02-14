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
package com.basemetas.fileview.convert.strategy.model;

import java.util.Date;

/**
 * 压缩文件条目信息类
 * 包含压缩文件内单个条目的详细信息
 */
public class ArchiveEntryInfo {
    
    private String name;
    private long size;
    private long compressedSize;
    private boolean isDirectory;
    private String fileType;
    private String method;
    private long crc;
    private Date lastModified;
    private String contentPreview;
    private boolean encrypted;  // 是否加密
    private String fullPath;    // 解压后的真实路径（绝对路径）
    // 移除了nestedArchiveInfo字段（性能考虑，不再递归解析嵌套压缩文件）
    
    public ArchiveEntryInfo() {
        this.size = 0;
        this.compressedSize = 0;
        this.isDirectory = false;
        this.crc = 0;
    }
    
    // Getters and Setters
    
    /**
     * 获取条目名称（包含路径）
     */
    public String getName() { 
        return name; 
    }
    
    /**
     * 设置条目名称（包含路径）
     */
    public void setName(String name) { 
        this.name = name; 
    }
    
    /**
     * 获取原始大小（字节）
     */
    public long getSize() { 
        return size; 
    }
    
    /**
     * 设置原始大小（字节）
     */
    public void setSize(long size) { 
        this.size = size; 
    }
    
    /**
     * 获取压缩后大小（字节）
     */
    public long getCompressedSize() { 
        return compressedSize; 
    }
    
    /**
     * 设置压缩后大小（字节）
     */
    public void setCompressedSize(long compressedSize) { 
        this.compressedSize = compressedSize; 
    }
    
    /**
     * 是否为目录
     */
    public boolean isDirectory() { 
        return isDirectory; 
    }
    
    /**
     * 设置是否为目录
     */
    public void setDirectory(boolean directory) { 
        isDirectory = directory; 
    }
    
    /**
     * 获取文件类型
     */
    public String getFileType() { 
        return fileType; 
    }
    
    /**
     * 设置文件类型
     */
    public void setFileType(String fileType) { 
        this.fileType = fileType; 
    }
    
    /**
     * 获取压缩方法
     */
    public String getMethod() { 
        return method; 
    }
    
    /**
     * 设置压缩方法
     */
    public void setMethod(String method) { 
        this.method = method; 
    }
    
    /**
     * 获取CRC校验值
     */
    public long getCrc() { 
        return crc; 
    }
    
    /**
     * 设置CRC校验值
     */
    public void setCrc(long crc) { 
        this.crc = crc; 
    }
    
    /**
     * 获取最后修改时间
     */
    public Date getLastModified() { 
        return lastModified; 
    }
    
    /**
     * 设置最后修改时间
     */
    public void setLastModified(Date lastModified) { 
        this.lastModified = lastModified; 
    }
    
    /**
     * 获取内容预览（仅限小文本文件）
     */
    public String getContentPreview() { 
        return contentPreview; 
    }
    
    /**
     * 设置内容预览（仅限小文本文件）
     */
    public void setContentPreview(String contentPreview) { 
        this.contentPreview = contentPreview; 
    }
    
    /**
     * 是否加密
     */
    public boolean isEncrypted() {
        return encrypted;
    }
    
    /**
     * 设置是否加密
     */
    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }
    
    /**
     * 获取解厊后的真实路径（绝对路径）
     */
    public String getFullPath() {
        return fullPath;
    }
    
    /**
     * 设置解厊后的真实路径（绝对路径）
     */
    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }
    
    // 移除了与嵌套压缩文件相关的getter和setter方法（性能考虑）
    
    /**
     * 计算压缩比
     */
    public double getCompressionRatio() {
        if (size == 0) {
            return 0.0;
        }
        return (double) compressedSize / size;
    }
    
    /**
     * 获取文件扩展名
     */
    public String getFileExtension() {
        if (name == null || isDirectory || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 获取简单文件名（不包含路径）
     */
    public String getSimpleName() {
        if (name == null) {
            return "";
        }
        
        String simpleName = name;
        
        // 处理目录路径（以/结尾）
        if (name.endsWith("/")) {
            simpleName = name.substring(0, name.length() - 1);
        }
        
        // 处理目录路径（以\结尾）
        if (simpleName.endsWith("\\")) {
            simpleName = simpleName.substring(0, simpleName.length() - 1);
        }
        
        // 提取文件名或目录名
        if (simpleName.contains("/")) {
            simpleName = simpleName.substring(simpleName.lastIndexOf("/") + 1);
        }
        if (simpleName.contains("\\")) {
            simpleName = simpleName.substring(simpleName.lastIndexOf("\\") + 1);
        }
        
        return simpleName;
    }
    
    /**
     * 获取相对于指定根目录的路径
     * 保留除根目录之外的路径信息
     * 
     * @param rootPath 根目录路径
     * @return 相对于根目录的路径
     */
    public String getRelativePath(String rootPath) {
        if (name == null || rootPath == null) {
            return name != null ? name : "";
        }
        
        // 如果name以rootPath开头，则移除rootPath部分
        if (name.startsWith(rootPath)) {
            String relativePath = name.substring(rootPath.length());
            // 移除开头的路径分隔符
            if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
        }
        
        // 如果没有匹配的根路径，返回完整名称
        return name;
    }
    
    /**
     * 格式化文件大小为人类可读的字符串
     */
    public String getFormattedSize() {
        return formatBytes(size);
    }
    
    /**
     * 格式化压缩后大小为人类可读的字符串
     */
    public String getFormattedCompressedSize() {
        return formatBytes(compressedSize);
    }
    
    /**
     * 将字节数格式化为人类可读的字符串
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return "ArchiveEntryInfo{" +
                "name='" + name + '\'' +
                ", size=" + size +
                ", compressedSize=" + compressedSize +
                ", isDirectory=" + isDirectory +
                ", fileType='" + fileType + '\'' +
                ", method='" + method + '\'' +
                ", crc=" + crc +
                ", lastModified=" + lastModified +
                ", encrypted=" + encrypted +
                ", fullPath='" + fullPath + '\'' +
                // 移除了nestedArchiveInfo的toString输出
                '}';
    }
}