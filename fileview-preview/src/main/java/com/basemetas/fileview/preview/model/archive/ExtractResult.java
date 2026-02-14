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

import java.util.HashMap;
import java.util.Map;

public class ExtractResult {
    private boolean success;
    private String relativePath;
    private String absolutePath;
    private String errorMessage;
    private boolean requiresPassword;
    private String errorCode;
    private String archiveFormat;
    
    private ExtractResult(boolean success, String relativePath, String absolutePath, String errorMessage,
                          boolean requiresPassword, String errorCode, String archiveFormat) {
        this.success = success;
        this.relativePath = relativePath;
        this.absolutePath = absolutePath;
        this.errorMessage = errorMessage;
        this.requiresPassword = requiresPassword;
        this.errorCode = errorCode;
        this.archiveFormat = archiveFormat;
    }
        
        public static ExtractResult success(String relativePath, String absolutePath) {
            return new ExtractResult(true, relativePath, absolutePath, null, false, null, null);
        }
        
        public static ExtractResult failure(String errorMessage) {
            return new ExtractResult(false, null, null, errorMessage, false, null, null);
        }
        
        public static ExtractResult passwordRequired(String archiveFormat, String message) {
            return new ExtractResult(false, null, null, message, true, "PASSWORD_REQUIRED", archiveFormat);
        }
        
        public static ExtractResult wrongPassword(String archiveFormat, String message) {
            return new ExtractResult(false, null, null, message, true, "WRONG_PASSWORD", archiveFormat);
        }
        
        public static ExtractResult unsupportedEncryption(String archiveFormat, String message) {
            return new ExtractResult(false, null, null, message, true, "UNSUPPORTED_ENCRYPTION", archiveFormat);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getRelativePath() {
            return relativePath;
        }
        
        public String getAbsolutePath() {
            return absolutePath;
        }
        
        public String getExtractedFilePath() {
            return relativePath;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public boolean isRequiresPassword() {
            return requiresPassword;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public String getArchiveFormat() {
            return archiveFormat;
        }
        
        public Map<String, Object> toResponseMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                result.put("relativePath", relativePath);
                result.put("absolutePath", absolutePath);
            } else {
                result.put("error", errorMessage);
                if (requiresPassword) {
                    result.put("requiresPassword", true);
                    result.put("errorCode", errorCode);
                    if (archiveFormat != null) {
                        result.put("archiveFormat", archiveFormat);
                    }
                }
            }
            return result;
        }
}
