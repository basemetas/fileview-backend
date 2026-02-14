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
 * 解压决策结果类
 * 用于表示是否需要解压文件的决策结果
 */
public class ExtractionDecision {
    private boolean shouldExtract;  // 是否需要解压
    private String reason;          // 决策原因
    
    public ExtractionDecision(boolean shouldExtract, String reason) {
        this.shouldExtract = shouldExtract;
        this.reason = reason;
    }
    
    public boolean isShouldExtract() {
        return shouldExtract;
    }
    
    public void setShouldExtract(boolean shouldExtract) {
        this.shouldExtract = shouldExtract;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public String toString() {
        return "ExtractionDecision{" +
                "shouldExtract=" + shouldExtract +
                ", reason='" + reason + '\'' +
                '}';
    }
}
