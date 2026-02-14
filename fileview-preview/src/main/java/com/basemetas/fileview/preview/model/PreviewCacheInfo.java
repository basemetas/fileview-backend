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
package com.basemetas.fileview.preview.model;

import java.util.Map;

public class PreviewCacheInfo {
  private String fileId;
        private String previewUrl;
        private String originalFilePath;
        private String originalFileName;
        private String originalFileFormat;
        private String previewFileFormat;
        private Long originalFileSize;
        private Long previewFileSize;
        private boolean conversionRequired;
        private String mode; // DIRECT 或 CONVERT
        private long cachedAt; // 缓存时间戳
        private long expiresAt; // 过期时间戳
        private String status; // 转换状态：SUCCESS, FAILED, IN_PROGRESS
        private Boolean encrypted; // 是否为加密文件
        private String errorCode; // 错误代码，用于标识特定失败原因
        
        // 多页文件支持
        private boolean isMultiPage;        // 是否为多页文件
        private int totalPages;             // 总页数
        private String pagesDirectory;      // 多页文件存储目录
        private Map<Integer, String> pageUrls; // 每页的URL映射
        
        // 构造函数
        public PreviewCacheInfo() {
            this.cachedAt = System.currentTimeMillis();
        }
        
        public PreviewCacheInfo(String fileId, String previewUrl) {
            this();
            this.fileId = fileId;
            this.previewUrl = previewUrl;
        }
        
        // Getters and Setters
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        
        public String getPreviewUrl() { return previewUrl; }
        public void setPreviewUrl(String previewUrl) { this.previewUrl = previewUrl; }
        
        public String getOriginalFilePath() { return originalFilePath; }
        public void setOriginalFilePath(String originalFilePath) { this.originalFilePath = originalFilePath; }
        
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

        public String getOriginalFileFormat() { return originalFileFormat; }
        public void setOriginalFileFormat(String originalFileFormat) { this.originalFileFormat = originalFileFormat; }
        
        public String getPreviewFileFormat() { return previewFileFormat; }
        public void setPreviewFileFormat(String previewFileFormat) { this.previewFileFormat = previewFileFormat; }
        
        public Long getOriginalFileSize() { return originalFileSize; }
        public void setOriginalFileSize(Long originalFileSize) { this.originalFileSize = originalFileSize; }
        
        public Long getPreviewFileSize() { return previewFileSize; }
        public void setPreviewFileSize(Long previewFileSize) { this.previewFileSize = previewFileSize; }
        
        public boolean isConversionRequired() { return conversionRequired; }
        public void setConversionRequired(boolean conversionRequired) { this.conversionRequired = conversionRequired; }
        
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        
        public long getCachedAt() { return cachedAt; }
        public void setCachedAt(long cachedAt) { this.cachedAt = cachedAt; }
        
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Boolean getEncrypted() { return encrypted; }
        public void setEncrypted(Boolean encrypted) { this.encrypted = encrypted; }
            
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public boolean isMultiPage() { return isMultiPage; }
        public void setMultiPage(boolean multiPage) { isMultiPage = multiPage; }
        
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        
        public String getPagesDirectory() { return pagesDirectory; }
        public void setPagesDirectory(String pagesDirectory) { this.pagesDirectory = pagesDirectory; }
        
        public Map<Integer, String> getPageUrls() { return pageUrls; }
        public void setPageUrls(Map<Integer, String> pageUrls) { this.pageUrls = pageUrls; }
        
        @Override
        public String toString() {
            return "PreviewCacheInfo{" +
                    "fileId='" + fileId + '\'' +
                    ", previewUrl='" + previewUrl + '\'' +
                    ", originalFileFormat='" + originalFileFormat + '\'' +
                    ", previewFileFormat='" + previewFileFormat + '\'' +
                    ", mode='" + mode + '\'' +
                    ", status='" + status + '\'' +
                    ", conversionRequired=" + conversionRequired +
                    ", cachedAt=" + cachedAt +
                    ", expiresAt=" + expiresAt +
                    '}';
        }
}
