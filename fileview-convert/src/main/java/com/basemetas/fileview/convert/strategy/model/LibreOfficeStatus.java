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
 * LibreOffice状态信息类
 * 
 * 用于封装LibreOffice转换器的状态信息
 * 
 * @author 夫子
 */
public class LibreOfficeStatus {
    boolean enabled;
    boolean available;
    String command;
    String version;
    int timeout;
    boolean headless;
    boolean invisible;
    String tempDir;
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public boolean isAvailable() { return available; }
    public String getCommand() { return command; }
    public String getVersion() { return version; }
    public int getTimeout() { return timeout; }
    public boolean isHeadless() { return headless; }
    public boolean isInvisible() { return invisible; }
    public String getTempDir() { return tempDir; }
    
    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setCommand(String command) { this.command = command; }
    public void setVersion(String version) { this.version = version; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public void setInvisible(boolean invisible) { this.invisible = invisible; }
    public void setTempDir(String tempDir) { this.tempDir = tempDir; }
}