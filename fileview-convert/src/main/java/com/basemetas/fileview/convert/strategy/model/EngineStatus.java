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
/**
 * 引擎状态信息类
 * 
 * 封装所有转换引擎的状态信息，包括启用状态、可用性、详细状态等
 * 统一不同引擎选择器的状态信息格式
 * 
 * @author 夫子
 */
public class EngineStatus {
    private boolean libreOfficeEnabled;
    private boolean libreOfficeAvailable;
    private LibreOfficeStatus libreOfficeStatus;
    private boolean fallbackEnabled;
    private String priorityConfig;
    private long fileSizeThreshold;  // 文件大小阈值
    
    // Getters and Setters
    public boolean isLibreOfficeEnabled() { return libreOfficeEnabled; }
    public void setLibreOfficeEnabled(boolean libreOfficeEnabled) { this.libreOfficeEnabled = libreOfficeEnabled; }
    
    public boolean isLibreOfficeAvailable() { return libreOfficeAvailable; }
    public void setLibreOfficeAvailable(boolean libreOfficeAvailable) { this.libreOfficeAvailable = libreOfficeAvailable; }
    
    public LibreOfficeStatus getLibreOfficeStatus() { return libreOfficeStatus; }
    public void setLibreOfficeStatus(LibreOfficeStatus libreOfficeStatus) { this.libreOfficeStatus = libreOfficeStatus; }
    
    public boolean isFallbackEnabled() { return fallbackEnabled; }
    public void setFallbackEnabled(boolean fallbackEnabled) { this.fallbackEnabled = fallbackEnabled; }
    
    public String getPriorityConfig() { return priorityConfig; }
    public void setPriorityConfig(String priorityConfig) { this.priorityConfig = priorityConfig; }
    
    public long getFileSizeThreshold() { return fileSizeThreshold; }
    public void setFileSizeThreshold(long fileSizeThreshold) { this.fileSizeThreshold = fileSizeThreshold; }
}