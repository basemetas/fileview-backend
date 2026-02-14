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
package com.basemetas.fileview.preview.service.storage.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.basemetas.fileview.preview.service.storage.FileStorageService;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * 远程文件存储服务实现
 * 
 * 适用场景：
 * - 文件存储在独立的文件服务器
 * - 分布式部署环境
 * - 需要统一的文件管理服务
 * 
 * @author 夫子
 */
@Service
@ConditionalOnProperty(name = "fileview.storage.type", havingValue = "remote")
public class RemoteFileStorageService implements FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(RemoteFileStorageService.class);
    
    @Value("${fileview.storage.remote.server-url}")
    private String serverUrl;
    
    @Value("${fileview.storage.remote.access-key:}")
    private String accessKey;
    
    @Value("${fileview.storage.remote.secret-key:}")
    private String secretKey;
    
    @Value("${fileview.storage.remote.timeout:30000}")
    private int timeout;
    
    private RestTemplate restTemplate;
    
    @PostConstruct
    public void init() {
        restTemplate = new RestTemplate();
        logger.info("远程文件存储服务初始化完成 - 服务器: {}", serverUrl);
    }
    
    @Override
    public String getFileUrl(String filePath) {
        try {
            String encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            // 修复：使用正确的预览服务端点
            String url = serverUrl + "/preview/api/files/" + encodedPath + "?t=" + System.currentTimeMillis();
            
            if (accessKey != null && !accessKey.trim().isEmpty()) {
                url += "&accessKey=" + accessKey;
            }
            
            logger.debug("生成远程文件URL: {} -> {}", filePath, url);
            return url;
        } catch (Exception e) {
            logger.error("生成远程文件URL失败: {}", filePath, e);
            return null;
        }
    }
    
    @Override
    public boolean fileExists(String filePath) {
        try {
            String url = serverUrl + "/api/files/exists?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Boolean> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Boolean.class
            );
            
            boolean exists = response.getBody() != null && response.getBody();
            logger.debug("检查远程文件存在: {} -> {}", filePath, exists);
            return exists;
            
        } catch (Exception e) {
            logger.error("检查远程文件失败: {}", filePath, e);
            return false;
        }
    }
    
    @Override
    public String getPhysicalPath(String filePath) {
        return null;
    }
    
    @Override
    public InputStream getFileStream(String filePath) throws IOException {
        try {
            String url = serverUrl + "/api/files/download?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                byte[].class
            );
            
            if (response.getBody() == null) {
                throw new IOException("远程文件内容为空: " + filePath);
            }
            
            logger.debug("获取远程文件流成功: {} (大小: {} bytes)", filePath, response.getBody().length);
            return new ByteArrayInputStream(response.getBody());
            
        } catch (Exception e) {
            logger.error("获取远程文件流失败: {}", filePath, e);
            throw new IOException("无法获取远程文件: " + filePath, e);
        }
    }
    
    @Override
    public String saveFile(String filePath, byte[] fileData) throws IOException {
        try {
            String url = serverUrl + "/api/files/upload?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            HttpEntity<byte[]> request = new HttpEntity<>(fileData, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                logger.info("文件上传到远程服务器成功: {} (大小: {} bytes)", filePath, fileData.length);
                return getFileUrl(filePath);
            } else {
                throw new IOException("上传文件失败，状态码: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            logger.error("上传文件到远程服务器失败: {}", filePath, e);
            throw new IOException("无法上传文件: " + filePath, e);
        }
    }
    
    @Override
    public boolean deleteFile(String filePath) {
        try {
            String url = serverUrl + "/api/files/delete?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Boolean> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                Boolean.class
            );
            
            boolean deleted = response.getBody() != null && response.getBody();
            logger.info("删除远程文件: {} -> {}", filePath, deleted ? "成功" : "失败");
            return deleted;
            
        } catch (Exception e) {
            logger.error("删除远程文件失败: {}", filePath, e);
            return false;
        }
    }
    
    @Override
    public Long getFileSize(String filePath) {
        try {
            String url = serverUrl + "/api/files/size?path=" + URLEncoder.encode(filePath, StandardCharsets.UTF_8);
            
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Long> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Long.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            logger.warn("获取远程文件大小失败: {}", filePath, e);
            return null;
        }
    }
    
    @Override
    public String getStorageType() {
        return "remote";
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        
        if (accessKey != null && !accessKey.trim().isEmpty()) {
            headers.set("X-Access-Key", accessKey);
        }
        if (secretKey != null && !secretKey.trim().isEmpty()) {
            headers.set("X-Secret-Key", secretKey);
        }
        
        return headers;
    }
}
