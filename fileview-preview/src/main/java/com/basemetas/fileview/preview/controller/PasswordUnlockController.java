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
package com.basemetas.fileview.preview.controller;

import com.basemetas.fileview.preview.model.response.ReturnResponse;
import com.basemetas.fileview.preview.service.password.PasswordUnlockService;
import com.basemetas.fileview.preview.service.password.FilePasswordValidator;
import com.basemetas.fileview.preview.config.FileFormatConfig;
import com.basemetas.fileview.preview.utils.ClientIdExtractor;
import com.basemetas.fileview.preview.utils.FileUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 密码解锁控制器
 * 处理加密文件的密码验证与解锁
 */
@RestController
@RequestMapping("/preview/api/password")
@CrossOrigin(origins = "*")
public class PasswordUnlockController {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordUnlockController.class);
    
    @Autowired
    private ClientIdExtractor clientIdExtractor;
    
    @Autowired
    private PasswordUnlockService passwordUnlockService;
  
    @Autowired
    private FilePasswordValidator filePasswordValidator;
    
    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private FileFormatConfig fileFormatConfig;
    
    /**
     * 解锁加密文件
     * POST /preview/password/unlock
     * 
     * 请求体: { "password": "...", "originalFilePath": "/path/to/file.zip" }
     * 请求头: X-Client-Id: <uuid>
     */
    @PostMapping("/unlock")
    public ResponseEntity<Map<String, Object>> unlockFile(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 提取并验证 clientId
            String clientId = clientIdExtractor.extractAndValidateClientId(httpRequest);
            if (clientId == null) {
                logger.warn("⚠️ 缺少或无效的 clientId");
                return ReturnResponse.badRequest("缺少或无效的客户端ID (X-Client-Id)");
            }
            
            // 2. 提取参数
            String password = request.get("password");
            String originalFilePath = request.get("originalFilePath");    
            
            if (password == null || password.trim().isEmpty()) {
                return ReturnResponse.badRequest("password 不能为空");
            }
            
            if (originalFilePath == null || originalFilePath.trim().isEmpty()) {
                return ReturnResponse.badRequest("originalFilePath 不能为空");
            }
            
            // 3. 从文件路径生成 fileId
            String localFileId = fileUtils.generateFileIdFromFileUrl(originalFilePath);
            
            logger.info("🔓 密码解锁请求 - FileId: {}, ClientId: {}, FilePath: {}", localFileId, clientId, originalFilePath);
            
            // 4. 从文件路径提取文件格式
            String fileFormat = extractFileFormat(originalFilePath);
            if (fileFormat == null) {
                logger.warn("⚠️ 无法从文件路径提取格式 - FilePath: {}", originalFilePath);
                return ReturnResponse.error("无法识别文件格式");
            }
            
            // 5. 验证密码
            boolean passwordValid = verifyPassword(originalFilePath, fileFormat, password);
            
            // 构建统一的响应格式（与 validateArchivePassword 一致）
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", localFileId);
            response.put("filePath", originalFilePath);
            response.put("fileFormat", fileFormat);
            response.put("archiveFormat", fileFormat);
            response.put("encrypted", true); // 能走到这里说明文件已加密
            response.put("passwordRequired", true);
            response.put("processingDuration", System.currentTimeMillis() - startTime);
            
            if (!passwordValid) {
                // 密码错误
                logger.warn("❌ 密码错误 - FileId: {}, ClientId: {}", localFileId, clientId);
                response.put("valid", false);
                response.put("passwordCorrect", false);
                response.put("message", "密码错误");
                return ReturnResponse.success(response, "密码错误");
            }
            
            // 6. 密码正确 - 标记为已解锁
            passwordUnlockService.markUnlocked(localFileId, clientId, password);
            
            // 7. 构建成功响应
            response.put("valid", true);
            response.put("passwordCorrect", true);
            response.put("message", "密码验证成功");
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ 密码解锁成功 - FileId: {}, ClientId: {}, 耗时: {}ms", 
                       localFileId, clientId, duration);
            
            return ReturnResponse.success(response, "密码验证成功");
            
        } catch (Exception e) {
            logger.error("💥 密码解锁异常", e);
            return ReturnResponse.error("密码解锁异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证密码（内部方法）
     * 复用 FilePasswordValidator 验证逻辑，避免代码重复
     * 
     * @param filePath 文件路径
     * @param fileFormat 文件格式
     * @param password 待验证的密码
     * @return 密码是否正确
     */
    private boolean verifyPassword(String filePath, String fileFormat, String password) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                logger.warn("⚠️ 文件路径为空，无法验证密码");
                return false;
            }
            
            // 仅对压缩包格式验证密码
            if ("zip".equalsIgnoreCase(fileFormat) || 
                "rar".equalsIgnoreCase(fileFormat) || 
                "7z".equalsIgnoreCase(fileFormat)) {
                
                logger.debug("🔐 开始验证压缩包密码 - FilePath: {}, Format: {}", filePath, fileFormat);
                
                // 复用 FilePasswordValidator 的验证逻辑
                FilePasswordValidator.PasswordValidationResult result = 
                        filePasswordValidator.validatePassword(filePath, password);
                
                // 如果文件加密，检查密码是否正确
                if (result.isEncrypted()) {
                    boolean isCorrect = result.isPasswordCorrect() != null && result.isPasswordCorrect();
                    logger.info("🔑 密码验证结果 - FilePath: {}, 正确: {}", filePath, isCorrect);
                    return isCorrect;
                } else {
                    // 文件未加密，不需要密码
                    logger.debug("🔓 文件未加密 - FilePath: {}", filePath);
                    return false;
                }
            }
            
            // Office：使用验证器尽可能准确校验密码
            if (fileFormatConfig.isOfficeDocument(fileFormat)) {
                FilePasswordValidator.PasswordValidationResult result =
                        filePasswordValidator.validatePassword(filePath, password, fileFormat);
                if (!result.isEncrypted()) {
                    logger.warn("⚠️ Office/WPS文件未加密，无需密码 - FilePath: {}", filePath);
                    return false;
                }
                if (result.isPasswordCorrect() != null) {
                    boolean correct = result.isPasswordCorrect();
                    logger.info("🔑 Office/WPS密码验证结果 - FilePath: {}, 正确: {}", filePath, correct);
                    return correct;
                }
                // 对于旧式加密的 DOC/PPT/WPS，FilePasswordValidator 只做加密检测，无法直接判断密码正确性
                if (fileFormatConfig.isLegacyEncryptedFormat(fileFormat)) {
                    logger.warn("⚠️ {} 旧式加密无法在预览侧直接验证密码，放行交由转换引擎验证 - FilePath: {}",
                            fileFormat.toUpperCase(), filePath);
                    return true;
                }
                if ("pdf".equalsIgnoreCase(fileFormat)) {
                    try (org.apache.pdfbox.pdmodel.PDDocument doc =
                             org.apache.pdfbox.pdmodel.PDDocument.load(new java.io.File(filePath), password)) {
                        logger.info("✅ PDF密码验证成功 - FilePath: {}", filePath);
                        return true;
                    } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                        logger.warn("❌ PDF密码错误 - FilePath: {}", filePath);
                        return false;
                    } catch (java.io.IOException e) {
                        logger.warn("⚠️ PDF密码验证IO异常，拒绝通过 - FilePath: {} - {}", filePath, e.getMessage());
                        return false;
                    }
                }
                logger.warn("⚠️ Office/PDF无法判定密码正确性，拒绝通过 - FilePath: {}", filePath);
                return false;
            }
            // 其他文件类型不支持密码验证
            logger.warn("⚠️ 不支持的文件格式密码验证 - Format: {}", fileFormat);
            return false;
        } catch (Exception e) {
            logger.error("❗ 密码验证异常 - FilePath: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 从文件路径提取文件格式
     * 
     * @param filePath 文件路径
     * @return 文件格式（小写，不包含点），如 "zip"、"rar"、"7z"
     */
    private String extractFileFormat(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        
        // 找到最后一个点的位置
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
            return null;
        }
        
        // 提取扩展名（不包含点）
        String extension = filePath.substring(lastDotIndex + 1).toLowerCase();
        
        // 过滤掉路径分隔符（防止路径中没有扩展名的情况）
        if (extension.contains("/") || extension.contains("\\")) {
            return null;
        }
        
        return extension;
    }
    
    /**
     * 检查解锁状态
     * GET /preview/api/password/status?fileId=xxx
     * 请求头: X-Client-Id: <uuid>
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkUnlockStatus(
            @RequestParam String fileId,
            HttpServletRequest httpRequest) {
        
        try {
            String clientId = clientIdExtractor.extractAndValidateClientId(httpRequest);
            if (clientId == null) {
                return ReturnResponse.badRequest("缺少或无效的客户端ID (X-Client-Id)");
            }
            
            boolean unlocked = passwordUnlockService.isUnlocked(fileId, clientId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("unlocked", unlocked);
            
            return ReturnResponse.success(data);
            
        } catch (Exception e) {
            logger.error("检查解锁状态异常", e);
            return ReturnResponse.error("检查解锁状态异常: " + e.getMessage());
        }
    }
}
