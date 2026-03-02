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
package com.basemetas.fileview.preview.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

/**
 * 环境检测工具类
 * 提供运行环境及外部工具的检测功能
 * 
 * @author 夫子
 */
public class EnvironmentUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentUtils.class);
    
    // 缓存WSL2检测结果，避免重复检测
    private static volatile Boolean cachedWslResult = null;
    
    // 缓存外部7z命令检测结果
    private static volatile Boolean cached7zAvailable = null;

    // 缓存 SevenZipJBinding native 支持结果
    private static volatile Boolean cachedNativeSevenZipSupported = null;
    
    /**
     * 检测是否为 WSL2 环境（参考转换模块 SevenZipParserService）
     * 使用多重验证机制确保检测精确性
     * 
     * 该方法会缓存检测结果，避免重复文件IO操作
     * 
     * @return true 如果运行在WSL2环境，否则返回false
     */
    public static boolean isWslEnvironment() {
        // 使用双重检查锁定优化性能
        if (cachedWslResult != null) {
            return cachedWslResult;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cachedWslResult != null) {
                return cachedWslResult;
            }
            
            cachedWslResult = detectWslEnvironment();
            if (cachedWslResult) {
                logger.info("🔍 检测到WSL2运行环境");
            }
            return cachedWslResult;
        }
    }
    
    /**
     * 检查外部 7z 命令是否可用
     * 
     * 该方法会缓存检测结果，避免重复进程调用
     * 
     * @return true 如果外部7z命令可用，否则返回false
     */
    public static boolean isExternal7zAvailable() {
        // 使用双重检查锁定优化性能
        if (cached7zAvailable != null) {
            return cached7zAvailable;
        }
        
        synchronized (EnvironmentUtils.class) {
            if (cached7zAvailable != null) {
                return cached7zAvailable;
            }
            
            cached7zAvailable = detectExternal7z();
            if (cached7zAvailable) {
                logger.debug("✅ 外部7z命令可用");
            } else {
                logger.debug("❌ 外部7z命令不可用");
            }
            return cached7zAvailable;
        }
    }
    
    /**
     * 实际执行WSL2环境检测
     */
    private static boolean detectWslEnvironment() {
        try {
            // 方法1: 检查 /proc/version
            File procVersion = new File("/proc/version");
            if (procVersion.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(procVersion))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String lowerLine = line.toLowerCase();
                        if (lowerLine.contains("microsoft") || lowerLine.contains("wsl")) {
                            logger.debug("🔍 WSL2环境检测(procVersion): {}", line);
                            return true;
                        }
                    }
                }
            }
            
            // 方法2: 检查 /proc/sys/kernel/osrelease
            File osRelease = new File("/proc/sys/kernel/osrelease");
            if (osRelease.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(osRelease))) {
                    String line = reader.readLine();
                    if (line != null && line.toLowerCase().contains("microsoft")) {
                        logger.debug("🔍 WSL2环境检测(osrelease): {}", line);
                        return true;
                    }
                }
            }
            
            // 方法3: 检查环境变量 WSL_DISTRO_NAME 或 WSL_INTEROP
            String wslDistro = System.getenv("WSL_DISTRO_NAME");
            String wslInterop = System.getenv("WSL_INTEROP");
            if (wslDistro != null || wslInterop != null) {
                logger.debug("🔍 WSL2环境检测(env): WSL_DISTRO_NAME={}, WSL_INTEROP={}", wslDistro, wslInterop);
                return true;
            }
        } catch (Exception e) {
            logger.debug("⚠️ WSL2环境检测失败: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 实际执行外部7z命令检测
     */
    private static boolean detectExternal7z() {
        logger.debug("🔍 检查外部7z命令是否可用...");
        try {
            ProcessBuilder pb = new ProcessBuilder("7z");
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                logger.debug("❌ 外部7z命令检查超时");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.debug("❌ 外部7z命令不可用 - ErrorType: {}, ErrorMessage: {}", 
                e.getClass().getName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 强制重新检测WSL2环境（用于测试）
     * 
     * @return true 如果运行在WSL2环境，否则返回false
     */
    public static boolean forceDetectWslEnvironment() {
        synchronized (EnvironmentUtils.class) {
            cachedWslResult = null;
            return isWslEnvironment();
        }
    }
    
    /**
     * 强制重新检测外部7z命令（用于测试）
     * 
     * @return true 如果外部7z命令可用，否则返回false
     */
    public static boolean forceDetectExternal7z() {
        synchronized (EnvironmentUtils.class) {
            cached7zAvailable = null;
            return isExternal7zAvailable();
        }
    }

    /**
     * 检测当前 JVM 运行架构是否在 SevenZipJBinding 原生库支持列表内。
     * <p>
     * SevenZipJBinding 当前仅支持以下平台（来自 JAR 内嵌 native 库）：
     * Linux-amd64 / Linux-i386 / Mac-x86_64 / Windows-amd64 / Windows-x86
     * <p>
     * ARM64（aarch64）、ARM32、RISC-V 等架构均不支持，应跳过 native 初始化，
     * 直接使用外部 7z 命令作为替代方案。
     *
     * @return true 表示当前平台支持 SevenZipJBinding native 库
     */
    public static boolean isNativeSevenZipSupported() {
        if (cachedNativeSevenZipSupported != null) {
            return cachedNativeSevenZipSupported;
        }
        synchronized (EnvironmentUtils.class) {
            if (cachedNativeSevenZipSupported != null) {
                return cachedNativeSevenZipSupported;
            }
            String arch = System.getProperty("os.arch", "").toLowerCase();
            String os = System.getProperty("os.name", "").toLowerCase();
            boolean supported;
            if (os.contains("linux")) {
                supported = arch.equals("amd64") || arch.equals("x86_64")
                        || arch.equals("i386") || arch.equals("i686");
            } else if (os.contains("mac")) {
                supported = arch.equals("x86_64") || arch.equals("amd64");
            } else if (os.contains("windows")) {
                supported = arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x86");
            } else {
                supported = false;
            }
            cachedNativeSevenZipSupported = supported;
            if (!supported) {
                logger.info("ℹ️ 当前平台不支持 SevenZipJBinding native 库 - os.name: {}, os.arch: {}"
                        + "，将使用外部7z命令作为替代方案", os, arch);
            } else {
                logger.debug("✅ 当前平台支持 SevenZipJBinding native 库 - os.name: {}, os.arch: {}", os, arch);
            }
            return supported;
        }
    }
}
