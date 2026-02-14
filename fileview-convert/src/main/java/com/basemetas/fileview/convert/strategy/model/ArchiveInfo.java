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
import java.util.List;

/**
 * 压缩文件信息类
 * 包含压缩文件的基本信息和统计数据
 */
public class ArchiveInfo {
    
    private String fileName;
    private String filePath;
    private long fileSize;
    private Date lastModified;
    private String archiveFormat;
    private int totalEntries;
    private int fileCount;
    private int directoryCount;
    private double compressionRatio;
    private List<ArchiveEntryInfo> entries;
    
    public ArchiveInfo() {
        this.compressionRatio = 0.0;
    }
    
    // Getters and Setters
    
    /**
     * 获取文件名
     */
    public String getFileName() { 
        return fileName; 
    }
    
    /**
     * 设置文件名
     */
    public void setFileName(String fileName) { 
        this.fileName = fileName; 
    }
    
    /**
     * 获取文件路径
     */
    public String getFilePath() { 
        return filePath; 
    }
    
    /**
     * 设置文件路径
     */
    public void setFilePath(String filePath) { 
        this.filePath = filePath; 
    }
    
    /**
     * 获取文件大小（字节）
     */
    public long getFileSize() { 
        return fileSize; 
    }
    
    /**
     * 设置文件大小（字节）
     */
    public void setFileSize(long fileSize) { 
        this.fileSize = fileSize; 
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
     * 获取压缩文件格式
     */
    public String getArchiveFormat() { 
        return archiveFormat; 
    }
    
    /**
     * 设置压缩文件格式
     */
    public void setArchiveFormat(String archiveFormat) { 
        this.archiveFormat = archiveFormat; 
    }
    
    /**
     * 获取总条目数
     */
    public int getTotalEntries() { 
        return totalEntries; 
    }
    
    /**
     * 设置总条目数
     */
    public void setTotalEntries(int totalEntries) { 
        this.totalEntries = totalEntries; 
    }
    
    /**
     * 获取文件数量
     */
    public int getFileCount() { 
        return fileCount; 
    }
    
    /**
     * 设置文件数量
     */
    public void setFileCount(int fileCount) { 
        this.fileCount = fileCount; 
    }
    
    /**
     * 获取目录数量
     */
    public int getDirectoryCount() { 
        return directoryCount; 
    }
    
    /**
     * 设置目录数量
     */
    public void setDirectoryCount(int directoryCount) { 
        this.directoryCount = directoryCount; 
    }
    
    /**
     * 获取压缩比
     */
    public double getCompressionRatio() { 
        return compressionRatio; 
    }
    
    /**
     * 设置压缩比
     */
    public void setCompressionRatio(double compressionRatio) { 
        this.compressionRatio = compressionRatio; 
    }
    
    /**
     * 获取条目列表
     */
    public List<ArchiveEntryInfo> getEntries() { 
        return entries; 
    }
    
    /**
     * 设置条目列表
     */
    public void setEntries(List<ArchiveEntryInfo> entries) { 
        this.entries = entries; 
    }
    
    @Override
    public String toString() {
        return "ArchiveInfo{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", archiveFormat='" + archiveFormat + '\'' +
                ", totalEntries=" + totalEntries +
                ", fileCount=" + fileCount +
                ", directoryCount=" + directoryCount +
                ", compressionRatio=" + compressionRatio +
                '}';
    }
}