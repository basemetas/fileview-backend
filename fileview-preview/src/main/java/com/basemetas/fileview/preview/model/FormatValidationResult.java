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
/**
 * 格式验证结果
 */
public class FormatValidationResult {
  private final boolean valid;
        private final String message;
        private final String suggestedTargetFormat;
        
        public FormatValidationResult(boolean valid, String message, String suggestedTargetFormat) {
            this.valid = valid;
            this.message = message;
            this.suggestedTargetFormat = suggestedTargetFormat;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getSuggestedTargetFormat() {
            return suggestedTargetFormat;
        }
        
        public boolean needsConversion() {
            return valid && suggestedTargetFormat != null;
        }
}
