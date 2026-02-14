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
package com.basemetas.fileview.convert.service.checker;

import com.basemetas.fileview.convert.strategy.impl.converter.Cad2xConverter;
import com.basemetas.fileview.convert.strategy.impl.converter.ConvertEngineSelector.ConvertEngine;
import com.basemetas.fileview.convert.strategy.impl.converter.LibreOfficeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 转换引擎健康检查服务
 * 
 * 独立于转换流程,定期检查所有引擎的可用性状态
 * 转换流程直接读取 Redis 状态,无需等待检查
 * 
 * 分布式支持:
 * 1. 使用 Redis 分布式锁避免重复检查
 * 2. 状态存储在 Redis 中,所有节点共享
 * 3. 单节点执行检查,其他节点读取结果
 * 
 * 优势:
 * 1. 转换流程零等待(< 1ms)
 * 2. 主动感知引擎状态变化
 * 3. 统一的健康检查逻辑
 * 4. 支持引擎状态监控和告警
 * 5. 集群环境下避免重复检查
 * 
 * 注意:需要 Redis 支持,如果 Redis 不可用,此服务将不会加载
 * 
 * @author 夫子
 */
@Service
public class EngineHealthCheckService {
    
    private static final Logger logger = LoggerFactory.getLogger(EngineHealthCheckService.class);  
    @Autowired
    private LibreOfficeConverter libreOfficeConverter;
    
    @Autowired(required = false)
    private Cad2xConverter cad2xConverter;
    
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    
    /**
     * Redis 键前缀
     */
    private static final String REDIS_KEY_PREFIX = "engine:health:";
    
    /**
     * Redis 锁前缀
     */
    private static final String REDIS_LOCK_PREFIX = "engine:health:lock:";
    
    /**
     * ImageMagick 状态键
     */
    private static final String IMAGEMAGICK_KEY = "imagemagick";
    
    /**
     * CAD2X 状态键
     */
    private static final String CAD2X_KEY = "cad2x";
    
    /**
     * 引擎状态缓存时间（秒）
     * 默认 60 秒，超过此时间未更新的状态将被视为过期
     */
    @Value("${convert.engine.health.check.ttl:60}")
    private int statusTtl;
    
    /**
     * 分布式锁超时时间（秒）
     * 默认 10 秒，防止节点崩溃后锁无法释放
     */
    @Value("${convert.engine.health.check.lock-timeout:10}")
    private int lockTimeout;
    
    /**
     * 是否启用健康检查
     */
    @Value("${convert.engine.health.check.enabled:true}")
    private boolean healthCheckEnabled;
    
    /**
     * 初始化方法 - 立即检查一次所有引擎
     */
    @PostConstruct
    public void init() {
        logger.info("📦 EngineHealthCheckService 初始化开始...");
        
        // 检查 Redis 是否可用
        if (redisTemplate == null) {
            logger.error("❌ Redis 不可用 - StringRedisTemplate 未注入，健康检查服务将无法使用 Redis 分布式功能");
            logger.warn("⚠️ 健康检查服务将禁用，转换流程将降级为实时检查");
            healthCheckEnabled = false;
            return;
        }
        
        if (!healthCheckEnabled) {
            logger.info("❌ 引擎健康检查服务已禁用 (配置: convert.engine.health.check.enabled=false)");
            return;
        }
        
        logger.info("🏥 引擎健康检查服务初始化 - 状态TTL: {}s, 锁超时: {}s", statusTtl, lockTimeout);
        logger.info("🏥 分布式支持已启用 - 使用 Redis 共享状态和分布式锁");
        logger.info("🕒 定时任务配置: cron = ${convert.engine.health.check.cron:0/30 * * * * ?}");
        
        // 立即执行首次检查
        checkAllEngines();
        
        logger.info("✅ EngineHealthCheckService 初始化完成");
    }
    
    /**
     * 定时健康检查任务
     * 默认每30秒执行一次
     */
    @Scheduled(cron = "${convert.engine.health.check.cron:0/30 * * * * ?}")
    public void scheduledHealthCheck() {
        if (!healthCheckEnabled) {
            return;
        }
        
        checkAllEngines();
    }
    
    /**
     * 检查所有引擎的健康状态
     */
    private void checkAllEngines() {
        if (redisTemplate == null) {
            logger.warn("⚠️ Redis 不可用，跳过健康检查");
            return;
        }
        
        String lockKey = REDIS_LOCK_PREFIX + "check-all";
        
        try {
            // 尝试获取分布式锁
            Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, String.valueOf(System.currentTimeMillis()), lockTimeout, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(lockAcquired)) {
                try {
                    long startTime = System.currentTimeMillis();           
                    // 检查所有引擎
                    checkEngine(ConvertEngine.LIBREOFFICE);
                    // 检查 ImageMagick
                    checkImageMagick();                 
                    // 检查 CAD2X
                    checkCad2x();                  
                    long duration = System.currentTimeMillis() - startTime;
                    
                    // 记录检查结果
                    logger.info("✅ 引擎健康检查完成 - 耗时: {}ms, 状态: LibreOffice={}, ImageMagick={}, CAD2X={}",
                               duration,
                               getEngineStatus(ConvertEngine.LIBREOFFICE),
                               getImageMagickStatus(),
                               getCad2xStatus());
                } finally {
                    // 释放锁
                    redisTemplate.delete(lockKey);
                }
            } else {
                logger.debug("⏸️ 其他节点正在执行健康检查，跳过本次检查");
            }
        } catch (Exception e) {
            logger.error("❌ 引擎健康检查失败 - Redis 操作异常", e);
        }
    }
    
    /**
     * 检查单个引擎状态
     */
    private void checkEngine(ConvertEngine engine) {
        try {
            boolean available = checkEngineStatus(engine);
            
            // 检测状态变化
            Boolean previousStatus = getEngineStatus(engine);
            if (previousStatus != null && previousStatus != available) {
                logger.warn("⚠️ 引擎状态变化 - {}: {} → {}", 
                           engine.getDescription(), 
                           previousStatus ? "可用" : "不可用",
                           available ? "可用" : "不可用");
            }
            
            // 更新 Redis 状态
            setEngineStatus(engine, available);
            
        } catch (Exception e) {
            logger.error("检查引擎 {} 状态时发生异常", engine.getDescription(), e);
            // 异常时标记为不可用
            setEngineStatus(engine, false);
        }
    }
    
    /**
     * 检查单个引擎的实际服务状态
     */
    private boolean checkEngineStatus(ConvertEngine engine) {
        switch (engine) {
            case LIBREOFFICE:
                return libreOfficeConverter.isServiceAvailable();
            default:
                logger.warn("未知的引擎类型: {}", engine);
                return false;
        }
    }
    
    /**
     * 快速获取引擎状态（转换流程调用）
     * 
     * @param engine 引擎类型
     * @return 是否可用
     */
    public boolean isEngineAvailable(ConvertEngine engine) {
        if (!healthCheckEnabled) {
            // 健康检查禁用时，实时检查
            return checkEngineStatus(engine);
        }
        
        // 从 Redis 读取状态（极快，< 1ms）
        Boolean status = getEngineStatus(engine);
        
        // 🐞 修复：当 Redis 中没有状态时（应用启动初期），降级为实时检查
        if (status == null) {
            logger.warn("⚠️ Redis 中未找到引擎 {} 状态，降级为实时检查", engine.getDescription());
            return checkEngineStatus(engine);
        }
        
        return status;
    }
    
    /**
     * 从 Redis 获取引擎状态
     */
    private Boolean getEngineStatus(ConvertEngine engine) {
        try {
            String key = REDIS_KEY_PREFIX + engine.getCode();
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Boolean.parseBoolean(value) : null;
        } catch (Exception e) {
            logger.error("从 Redis 读取引擎 {} 状态失败", engine.getCode(), e);
            return null;
        }
    }
    
    /**
     * 将引擎状态存储到 Redis
     */
    private void setEngineStatus(ConvertEngine engine, boolean available) {
        try {
            String key = REDIS_KEY_PREFIX + engine.getCode();
            redisTemplate.opsForValue().set(key, String.valueOf(available), statusTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("将引擎 {} 状态写入 Redis 失败", engine.getCode(), e);
        }
    }
    
    /**
     * 强制立即检查指定引擎
     */
    public boolean forceCheckEngine(ConvertEngine engine) {
        logger.info("🔍 强制检查引擎: {}", engine.getDescription());
        checkEngine(engine);
        Boolean status = getEngineStatus(engine);
        return status != null ? status : false;
    }
    
    /**
     * 获取所有引擎的状态报告
     */
    public String getHealthReport() {
        StringBuilder report = new StringBuilder();
        report.append("引擎健康状态报告:\n");
        
        for (ConvertEngine engine : ConvertEngine.values()) {
            Boolean available = getEngineStatus(engine);
            Long ttl = getEngineStatusTtl(engine);
            
            report.append(String.format("  - %s: %s (TTL: %s)\n",
                                       engine.getDescription(),
                                       available != null ? (available ? "✅ 可用" : "❌ 不可用") : "⚪ 未检查",
                                       ttl != null ? ttl + "秒" : "未设置"));
        }
        
        return report.toString();
    }
    
    /**
     * 获取引擎状态的剩余TTL
     */
    private Long getEngineStatusTtl(ConvertEngine engine) {
        try {
            String key = REDIS_KEY_PREFIX + engine.getCode();
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查 ImageMagick 状态
     */
    private void checkImageMagick() {
        try {
            boolean available = ImageMagickChecker.isAvailable();
            
            // 检测状态变化
            Boolean previousStatus = getImageMagickStatus();
            if (previousStatus != null && previousStatus != available) {
                logger.warn("⚠️ ImageMagick 状态变化: {} → {}",
                           previousStatus ? "可用" : "不可用",
                           available ? "可用" : "不可用");
                
                // 如果从可用变为不可用，输出安装建议
                if (previousStatus && !available) {
                    logger.warn("ImageMagick 不可用，安装建议:\n{}", ImageMagickChecker.getInstallationSuggestions());
                }
            }
            
            // 首次检查时输出版本信息
            if (previousStatus == null && available) {
                String version = ImageMagickChecker.getVersion();
                if (version != null) {
                    logger.info("✅ ImageMagick 可用 - {}", version);
                }
            }
            
            // 更新 Redis 状态
            setImageMagickStatus(available);
            
        } catch (Exception e) {
            logger.error("检查 ImageMagick 状态时发生异常", e);
            setImageMagickStatus(false);
        }
    }
    
    /**
     * 获取 ImageMagick 状态（快速，供外部调用）
     * 
     * @return 是否可用
     */
    public boolean isImageMagickAvailable() {
        if (!healthCheckEnabled) {
            // 健康检查禁用时，实时检查
            return ImageMagickChecker.isAvailable();
        }
        
        // 从 Redis 读取状态（极快，< 1ms）
        Boolean status = getImageMagickStatus();
        
        // 当 Redis 中没有状态时，降级为实时检查
        if (status == null) {
            logger.warn("⚠️ Redis 中未找到 ImageMagick 状态，降级为实时检查");
            return ImageMagickChecker.isAvailable();
        }
        
        return status;
    }
    
    /**
     * 从 Redis 获取 ImageMagick 状态
     */
    private Boolean getImageMagickStatus() {
        try {
            String key = REDIS_KEY_PREFIX + IMAGEMAGICK_KEY;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Boolean.parseBoolean(value) : null;
        } catch (Exception e) {
            logger.error("从 Redis 读取 ImageMagick 状态失败", e);
            return null;
        }
    }
    
    /**
     * 将 ImageMagick 状态存储到 Redis
     */
    private void setImageMagickStatus(boolean available) {
        try {
            String key = REDIS_KEY_PREFIX + IMAGEMAGICK_KEY;
            redisTemplate.opsForValue().set(key, String.valueOf(available), statusTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("将 ImageMagick 状态写入 Redis 失败", e);
        }
    }
    
    /**
     * 强制立即检查 ImageMagick
     */
    public boolean forceCheckImageMagick() {
        logger.info("🔍 强制检查 ImageMagick");
        checkImageMagick();
        Boolean status = getImageMagickStatus();
        return status != null ? status : false;
    }
    
    /**
     * 检查 CAD2X 状态
     */
    private void checkCad2x() {
        try {
            if (cad2xConverter == null) {
                logger.debug("CAD2X 转换器未注入，跳过检查");
                setCad2xStatus(false);
                return;
            }
            
            boolean available = cad2xConverter.isServiceAvailable();
            
            // 检测状态变化
            Boolean previousStatus = getCad2xStatus();
            if (previousStatus != null && previousStatus != available) {
                logger.warn("⚠️ CAD2X 状态变化: {} → {}",
                           previousStatus ? "可用" : "不可用",
                           available ? "可用" : "不可用");
            }
            
            // 首次检查时输出状态信息
            if (previousStatus == null && available) {
                logger.info("✅ CAD2X 可用");
            }
            
            // 更新 Redis 状态
            setCad2xStatus(available);
            
        } catch (Exception e) {
            logger.error("检查 CAD2X 状态时发生异常", e);
            setCad2xStatus(false);
        }
    }
    
    /**
     * 获取 CAD2X 状态（快速，供外部调用）
     * 
     * @return 是否可用
     */
    public boolean isCad2xAvailable() {
        if (!healthCheckEnabled) {
            // 健康检查禁用时，实时检查
            return cad2xConverter != null && cad2xConverter.isServiceAvailable();
        }
        
        // 从 Redis 读取状态（极快，< 1ms）
        Boolean status = getCad2xStatus();
        
        // 当 Redis 中没有状态时，降级为实时检查
        if (status == null) {
            logger.warn("⚠️ Redis 中未找到 CAD2X 状态，降级为实时检查");
            return cad2xConverter != null && cad2xConverter.isServiceAvailable();
        }
        
        return status;
    }
    
    /**
     * 从 Redis 获取 CAD2X 状态
     */
    private Boolean getCad2xStatus() {
        try {
            String key = REDIS_KEY_PREFIX + CAD2X_KEY;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Boolean.parseBoolean(value) : null;
        } catch (Exception e) {
            logger.error("从 Redis 读取 CAD2X 状态失败", e);
            return null;
        }
    }
    
    /**
     * 将 CAD2X 状态存储到 Redis
     */
    private void setCad2xStatus(boolean available) {
        try {
            String key = REDIS_KEY_PREFIX + CAD2X_KEY;
            redisTemplate.opsForValue().set(key, String.valueOf(available), statusTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("将 CAD2X 状态写入 Redis 失败", e);
        }
    }
    
    /**
     * 强制立即检查 CAD2X
     */
    public boolean forceCheckCad2x() {
        logger.info("🔍 强制检查 CAD2X");
        checkCad2x();
        Boolean status = getCad2xStatus();
        return status != null ? status : false;
    }
}
