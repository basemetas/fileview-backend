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
package com.basemetas.fileview.preview.model.archive;

/**
 * 压缩文件路径信息封装类
 */
public class ArchivePathInfo {
    private String archivePath;  // 压缩文件路径
    private String internalPath; // 压缩包内文件路径
    
    public ArchivePathInfo(String archivePath, String internalPath) {
        this.archivePath = archivePath;
        this.internalPath = internalPath;
    }
    
    public String getArchivePath() {
        return archivePath;
    }
    
    public void setArchivePath(String archivePath) {
        this.archivePath = archivePath;
    }
    
    public String getInternalPath() {
        return internalPath;
    }
    
    public void setInternalPath(String internalPath) {
        this.internalPath = internalPath;
    }
    
    @Override
    public String toString() {
        return "ArchivePathInfo{" +
                "archivePath='" + archivePath + '\'' +
                ", internalPath='" + internalPath + '\'' +
                '}';
    }
}
