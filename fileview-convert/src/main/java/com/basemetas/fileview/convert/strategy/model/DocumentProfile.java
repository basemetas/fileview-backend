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

import java.util.Arrays;

/**
 * 文档特征分析结果
 * 
 * 用于存储文档分析后的特征信息，包括文件大小、格式复杂度等
 * 为引擎选择提供决策依据
 * 
 * @author 夫子
 */
public class DocumentProfile {
    // Word文档特征
    private long fileSize;
    private String fileName;
    private String sourceFormat;
    private String targetFormat;
    private DocumentComplexity complexity;

    // PPT文档特征
    private String filePath;
    private boolean isLargeFile;
    private boolean isComplexFormat;
    private boolean hasAnimations;
    private boolean hasMultimedia;
    private boolean hasTransitions;

    // Excel文档特征
    private boolean hasFormulas;
    private boolean hasCharts;

    public DocumentProfile() {
        // 默认构造函数
    }

    // Word文档构造函数
    public DocumentProfile(long fileSize, String fileName, String sourceFormat, String targetFormat,
            DocumentComplexity complexity) {
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.sourceFormat = sourceFormat != null ? sourceFormat.toLowerCase() : "";
        this.targetFormat = targetFormat != null ? targetFormat.toLowerCase() : "";
        this.complexity = complexity;
    }

    // PPT文档构造函数
    public DocumentProfile(String filePath, String sourceFormat, String targetFormat, long fileSize) {
        this.filePath = filePath;
        this.sourceFormat = sourceFormat != null ? sourceFormat.toLowerCase() : "";
        this.targetFormat = targetFormat != null ? targetFormat.toLowerCase() : "";
        this.fileSize = fileSize;
        this.isLargeFile = fileSize > 20971520; // 20MB
        this.isComplexFormat = isComplexPptFormat(sourceFormat);
        this.hasAnimations = estimateHasAnimations(sourceFormat);
        this.hasMultimedia = estimateHasMultimedia(sourceFormat);
        this.hasTransitions = estimateHasTransitions(sourceFormat);
    }

    // Excel文档构造函数
    public DocumentProfile(String filePath, String sourceFormat, String targetFormat, long fileSize, boolean isExcel) {
        this.filePath = filePath;
        this.sourceFormat = sourceFormat != null ? sourceFormat.toLowerCase() : "";
        this.targetFormat = targetFormat != null ? targetFormat.toLowerCase() : "";
        this.fileSize = fileSize;
        this.isLargeFile = fileSize > 10485760; // 10MB
        this.isComplexFormat = isComplexExcelFormat(sourceFormat);
        this.hasFormulas = estimateHasFormulas(sourceFormat);
        this.hasCharts = estimateHasCharts(sourceFormat);
    }

    private boolean isComplexPptFormat(String format) {
        // 宏启用和模板文件可能包含复杂特性
        return Arrays.asList("pptm", "potm", "ppsm").contains(format != null ? format.toLowerCase() : "");
    }

    private boolean estimateHasAnimations(String format) {
        // 新格式更可能包含动画效果
        return Arrays.asList("pptx", "pptm", "ppsx", "ppsm").contains(format != null ? format.toLowerCase() : "");
    }

    private boolean estimateHasMultimedia(String format) {
        // 演示文稿格式可能包含音频视频
        return Arrays.asList("ppt", "pptx", "pptm", "pps", "ppsx", "ppsm")
                .contains(format != null ? format.toLowerCase() : "");
    }

    private boolean estimateHasTransitions(String format) {
        // 放映格式更可能包含切换效果
        return Arrays.asList("pps", "ppsx", "ppsm").contains(format != null ? format.toLowerCase() : "");
    }

    private boolean isComplexExcelFormat(String format) {
        return Arrays.asList("xlsm", "xltm", "xlsb").contains(format != null ? format.toLowerCase() : "");
    }

    private boolean estimateHasFormulas(String format) {
        // 宏启用的文件可能包含复杂公式
        return "xlsm".equalsIgnoreCase(format) || "xltm".equalsIgnoreCase(format);
    }

    private boolean estimateHasCharts(String format) {
        // 二进制格式和模板文件可能包含图表
        return Arrays.asList("xlsb", "xlsx", "xlsm").contains(format != null ? format.toLowerCase() : "");
    }

    // Getters and Setters
    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat != null ? sourceFormat.toLowerCase() : "";
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public void setTargetFormat(String targetFormat) {
        this.targetFormat = targetFormat != null ? targetFormat.toLowerCase() : "";
    }

    public DocumentComplexity getComplexity() {
        return complexity;
    }

    public void setComplexity(DocumentComplexity complexity) {
        this.complexity = complexity;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isLargeFile() {
        return isLargeFile;
    }

    public void setLargeFile(boolean largeFile) {
        isLargeFile = largeFile;
    }

    public boolean isComplexFormat() {
        return isComplexFormat;
    }

    public void setComplexFormat(boolean complexFormat) {
        isComplexFormat = complexFormat;
    }

    public boolean isHasAnimations() {
        return hasAnimations;
    }

    public void setHasAnimations(boolean hasAnimations) {
        this.hasAnimations = hasAnimations;
    }

    public boolean isHasMultimedia() {
        return hasMultimedia;
    }

    public void setHasMultimedia(boolean hasMultimedia) {
        this.hasMultimedia = hasMultimedia;
    }

    public boolean isHasTransitions() {
        return hasTransitions;
    }

    public void setHasTransitions(boolean hasTransitions) {
        this.hasTransitions = hasTransitions;
    }

    public boolean isHasFormulas() {
        return hasFormulas;
    }

    public void setHasFormulas(boolean hasFormulas) {
        this.hasFormulas = hasFormulas;
    }

    public boolean isHasCharts() {
        return hasCharts;
    }

    public void setHasCharts(boolean hasCharts) {
        this.hasCharts = hasCharts;
    }

    /**
     * 文档复杂度枚举
     */
    public enum DocumentComplexity {
        /**
         * 低复杂度
         */
        LOW,
        /**
         * 中等复杂度
         */
        MEDIUM,
        /**
         * 高复杂度
         */
        HIGH
    }
}