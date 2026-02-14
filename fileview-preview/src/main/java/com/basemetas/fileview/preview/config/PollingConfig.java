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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 轮询策略配置类
 * 
 * 配置智能轮询的各种参数，支持动态调整
 * 
 * @author 夫子
 */
@Configuration
@ConfigurationProperties(prefix = "fileview.preview.polling")
public class PollingConfig {
    
    /**
     * 默认超时时间（秒）
     */
    private int defaultTimeout = 20;
    
    /**
     * 最大超时时间（秒）
     */
    private int maxTimeout = 100;
    
    /**
     * 默认检查间隔（毫秒）
     */
    private int defaultInterval = 1000;
    
    /**
     * 最小检查间隔（毫秒）
     */
    private int minInterval = 500;
    
    /**
     * 最大检查间隔（毫秒）
     */
    private int maxInterval = 5000;
    
    /**
     * 智能轮询策略配置
     */
    private SmartPollingStrategy smartStrategy = new SmartPollingStrategy();
    
    // Getters and Setters
    public int getDefaultTimeout() {
        return defaultTimeout;
    }
    
    public void setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
    
    public int getMaxTimeout() {
        return maxTimeout;
    }
    
    public void setMaxTimeout(int maxTimeout) {
        this.maxTimeout = maxTimeout;
    }
    
    public int getDefaultInterval() {
        return defaultInterval;
    }
    
    public void setDefaultInterval(int defaultInterval) {
        this.defaultInterval = defaultInterval;
    }
    
    public int getMinInterval() {
        return minInterval;
    }
    
    public void setMinInterval(int minInterval) {
        this.minInterval = minInterval;
    }
    
    public int getMaxInterval() {
        return maxInterval;
    }
    
    public void setMaxInterval(int maxInterval) {
        this.maxInterval = maxInterval;
    }
    
    public SmartPollingStrategy getSmartStrategy() {
        return smartStrategy;
    }
    
    public void setSmartStrategy(SmartPollingStrategy smartStrategy) {
        this.smartStrategy = smartStrategy;
    }
    
    /**
     * 智能轮询策略配置
     */
    public static class SmartPollingStrategy {
        /**
         * 第一阶段尝试次数（快速轮询）
         */
        private int phase1Attempts = 10;
        
        /**
         * 第一阶段间隔（毫秒）
         */
        private int phase1Interval = 1000;
        
        /**
         * 第二阶段尝试次数（中等轮询）
         */
        private int phase2Attempts = 20;
        
        /**
         * 第二阶段间隔（毫秒）
         */
        private int phase2Interval = 2000;
        
        /**
         * 第三阶段间隔（毫秒）
         */
        private int phase3Interval = 5000;
        
        // Getters and Setters
        public int getPhase1Attempts() {
            return phase1Attempts;
        }
        
        public void setPhase1Attempts(int phase1Attempts) {
            this.phase1Attempts = phase1Attempts;
        }
        
        public int getPhase1Interval() {
            return phase1Interval;
        }
        
        public void setPhase1Interval(int phase1Interval) {
            this.phase1Interval = phase1Interval;
        }
        
        public int getPhase2Attempts() {
            return phase2Attempts;
        }
        
        public void setPhase2Attempts(int phase2Attempts) {
            this.phase2Attempts = phase2Attempts;
        }
        
        public int getPhase2Interval() {
            return phase2Interval;
        }
        
        public void setPhase2Interval(int phase2Interval) {
            this.phase2Interval = phase2Interval;
        }
        
        public int getPhase3Interval() {
            return phase3Interval;
        }
        
        public void setPhase3Interval(int phase3Interval) {
            this.phase3Interval = phase3Interval;
        }
    }
}