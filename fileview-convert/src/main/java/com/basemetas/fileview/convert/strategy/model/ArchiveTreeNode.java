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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 压缩文件树形节点类
 * 用于表示压缩文件中的目录和文件的树形结构
 */
public class ArchiveTreeNode {
    
    private String name;
    private String fullPath;
    private boolean isDirectory;
    private long size;
    private long compressedSize;
    private String fileType;
    private String method;
    private long crc;
    private Date lastModified;
    private String contentPreview;
    // 移除了nestedArchiveInfo字段（性能考虑，不再递归解析嵌套压缩文件）
    private List<ArchiveTreeNode> children;
    
    public ArchiveTreeNode() {
        this.children = new ArrayList<>();
        this.size = 0;
        this.compressedSize = 0;
        this.crc = 0;
    }
    
    public ArchiveTreeNode(ArchiveEntryInfo entryInfo) {
        this();
        this.name = entryInfo.getSimpleName();
        this.fullPath = entryInfo.getName();  // 使用压缩包内的相对路径
        this.isDirectory = entryInfo.isDirectory();
        this.size = entryInfo.getSize();
        this.compressedSize = entryInfo.getCompressedSize();
        this.fileType = entryInfo.getFileType();
        this.method = entryInfo.getMethod();
        this.crc = entryInfo.getCrc();
        this.lastModified = entryInfo.getLastModified();
        this.contentPreview = entryInfo.getContentPreview();
        // 移除了nestedArchiveInfo的赋值（性能考虑）
    }
    
    // Getters and Setters
    
    /**
     * 获取节点名称（不包含路径）
     */
    public String getName() { 
        return name; 
    }
    
    /**
     * 设置节点名称（不包含路径）
     */
    public void setName(String name) { 
        this.name = name; 
    }
    
    /**
     * 获取完整路径
     */
    public String getFullPath() { 
        return fullPath; 
    }
    
    /**
     * 设置完整路径
     */
    public void setFullPath(String fullPath) { 
        this.fullPath = fullPath; 
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
    
    // 移除了与嵌套压缩文件相关的getter和setter方法（性能考虑）
    
    /**
     * 获取子节点列表
     */
    public List<ArchiveTreeNode> getChildren() { 
        return children; 
    }
    
    /**
     * 设置子节点列表
     */
    public void setChildren(List<ArchiveTreeNode> children) { 
        this.children = children; 
    }
    
    /**
     * 添加子节点
     */
    public void addChild(ArchiveTreeNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
    
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
        return "ArchiveTreeNode{" +
                "name='" + name + '\'' +
                ", fullPath='" + fullPath + '\'' +
                ", isDirectory=" + isDirectory +
                ", size=" + size +
                ", fileType='" + fileType + '\'' +
                ", childrenCount=" + (children != null ? children.size() : 0) +
                // 移除了nestedArchiveInfo的toString输出
                '}';
    }
}