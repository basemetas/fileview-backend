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
package com.basemetas.fileview.preview.config;

/**
 * 文件类别枚举
 * 定义支持的文件分类及其对应的转换策略类型
 */
public enum FileCategory {
    /**
     * 图片文件类别
     */
    IMAGE("image", "图片文件"),
    
    /**
     * 压缩文件类别
     */
    ARCHIVE("archive", "压缩文件"),
    
    /**
     * OFD文件类别
     */
    OFD("ofd", "OFD文件"),

    /**
     * PDF文件类别
     */
    PDF("PDF", "PDF文件"),
    
    /**
     * 文档文件类别（Word等文本文档）
     */
    DOCUMENT("document", "文档文件"),
    
    /**
     * 表格文件类别（Excel等）
     */
    SPREADSHEET("spreadsheet", "表格文件"),
    
    /**
     * 演示文件类别（PowerPoint等）
     */
    PRESENTATION("presentation", "演示文件"),
    
    /**
     * 流程图文件类别（Visio等）
     */
    VISIO("visio", "流程图文件"),
    
    /**
     * 音视频文件类别
     */
    AV("media", "音视频文件"),
    
    /**
     * CAD文件类别
     */
    CAD("cad", "CAD文件"),
    
    /**
     * 3D文件类别
     */
    THREE_D("model3d", "3D文件");
    
    /**
     * 对应的转换策略类型标识
     */
    private final String strategyType;
    
    /**
     * 文件类别描述
     */
    private final String description;
    
    FileCategory(String strategyType, String description) {
        this.strategyType = strategyType;
        this.description = description;
    }
    
    public String getStrategyType() {
        return strategyType;
    }
    
    public String getDescription() {
        return description;
    }
}
