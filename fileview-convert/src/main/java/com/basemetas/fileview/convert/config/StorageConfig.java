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
package com.basemetas.fileview.convert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置
 * 
 * 用于配置文件转换相关的存储路径
 * 支持从application.yml中读取配置
 * 
 * @author 夫子
 */
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {
    
    /**
     * 转换目标文件存放目录
     * 默认：/var/app/fileview-backend/fileTemp/target
     */
    private String convertTargetDir = "/var/app/fileview-backend/fileTemp/target";
    
    /**
     * 临时文件目录
     * 默认：/var/app/fileview-backend/fileTemp/temps
     */
    private String tempDir = "/var/app/fileview-backend/fileTemp/temps";
    
    /**
     * 上传文件目录
     * 默认：/var/app/fileview-backend/fileTemp/uploads
     */
    private String uploadDir = "/var/app/fileview-backend/fileTemp/uploads";
    
    /**
     * 下载文件目录
     * 默认：/var/app/fileview-backend/fileTemp/downloads
     */
    private String downloadDir = "/var/app/fileview-backend/fileTemp/downloads";
    
    /**
     * 压缩文件解压目录
     * 默认：/var/app/fileview-backend/fileTemp/uncompress
     */
    private String uncompressDir = "/var/app/fileview-backend/fileTemp/uncompress";
    
    /**
     * 最大文件大小（MB）
     * 默认：100MB
     */
    private int maxFileSizeMb = 100;
    
    // Getters and Setters
    
    public String getConvertTargetDir() {
        return convertTargetDir;
    }
    
    public void setConvertTargetDir(String convertTargetDir) {
        this.convertTargetDir = convertTargetDir;
    }
    
    public String getTempDir() {
        return tempDir;
    }
    
    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }
    
    public String getUploadDir() {
        return uploadDir;
    }
    
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
    
    public String getDownloadDir() {
        return downloadDir;
    }
    
    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }
    
    public String getUncompressDir() {
        return uncompressDir;
    }
    
    public void setUncompressDir(String uncompressDir) {
        this.uncompressDir = uncompressDir;
    }
    
    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }
    
    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }
    
    /**
     * 获取最大文件大小（字节）
     */
    public long getMaxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }
    
    @Override
    public String toString() {
        return "StorageConfig{" +
                "convertTargetDir='" + convertTargetDir + '\'' +
                ", tempDir='" + tempDir + '\'' +
                ", uploadDir='" + uploadDir + '\'' +
                ", downloadDir='" + downloadDir + '\'' +
                ", uncompressDir='" + uncompressDir + '\'' +
                ", maxFileSizeMb=" + maxFileSizeMb +
                '}';
    }
}