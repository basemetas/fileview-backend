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
 * 格式信息   
 */
public class FormatInfo {
      private final String sourceFormat;
        private final String defaultTargetFormat;
        private final String category;
        private final String description;
        private final boolean needsConversion;
        
        public FormatInfo(String sourceFormat, String defaultTargetFormat, 
                         String category, String description, boolean needsConversion) {
            this.sourceFormat = sourceFormat;
            this.defaultTargetFormat = defaultTargetFormat;
            this.category = category;
            this.description = description;
            this.needsConversion = needsConversion;
        }
        
        public String getSourceFormat() {
            return sourceFormat;
        }
        
        public String getDefaultTargetFormat() {
            return defaultTargetFormat;
        }
        
        public String getCategory() {
            return category;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean needsConversion() {
            return needsConversion;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s) -> %s [%s]", 
                sourceFormat, description, defaultTargetFormat, category);
        }

}
