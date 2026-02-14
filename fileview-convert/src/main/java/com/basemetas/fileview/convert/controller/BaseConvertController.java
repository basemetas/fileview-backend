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
package com.basemetas.fileview.convert.controller;

import com.basemetas.fileview.convert.model.response.ReturnResponse;
import com.basemetas.fileview.convert.service.mq.event.FileEventFactory;
import com.basemetas.fileview.convert.common.ValidateAndNormalized;
import com.basemetas.fileview.convert.service.mq.event.FileEvent;
import com.basemetas.fileview.convert.model.request.BaseConvertRequest;
import com.basemetas.fileview.convert.service.mq.producer.EventPublisher;
import com.basemetas.fileview.convert.service.mq.event.EventChannel;
import com.basemetas.fileview.convert.service.checker.EngineHealthCheckService;
import com.basemetas.fileview.convert.utils.EncodingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础转换控制器
 * 本地文件转换功能和健康检查
 * 
 * @version 2.0
 */
@RestController
@RequestMapping("/convert/api")
@CrossOrigin(origins = "*")
public class BaseConvertController {

    private static final Logger logger = LoggerFactory.getLogger(BaseConvertController.class);

    @Autowired
    protected EventPublisher eventPublisher;

    @Autowired
    protected FileEventFactory eventFactory;
    
    @Autowired
    private ValidateAndNormalized validator;
    
    @Autowired(required = false)
    private EngineHealthCheckService engineHealthCheckService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.info("Health check endpoint accessed");
        
        Map<String, Object> healthData = new HashMap<>();
        healthData.put("service", "running");
        healthData.put("timestamp", System.currentTimeMillis());
        
        // 如果引擎健康检查服务可用，添加引擎状态
        if (engineHealthCheckService != null) {
            healthData.put("engineHealthCheck", "enabled");
            healthData.put("imageMagick", engineHealthCheckService.isImageMagickAvailable());
        } else {
            healthData.put("engineHealthCheck", "disabled");
        }
        
        return ReturnResponse.success(healthData, "服务运行正常");
    }
    
    /**
     * 获取引擎健康报告
     */
    @GetMapping("/engine/health")
    public ResponseEntity<Map<String, Object>> engineHealth() {
        if (engineHealthCheckService == null) {
            return ReturnResponse.error(503, "引擎健康检查服务未启用");
        }
        
        String report = engineHealthCheckService.getHealthReport();
        Map<String, Object> data = new HashMap<>();
        data.put("report", report);
        data.put("imageMagick", engineHealthCheckService.isImageMagickAvailable());
        
        return ReturnResponse.success(data, "引擎健康报告");
    }

    /**
     * 本地文件转换入口
     * 支持所有文件格式的转换
     * 
     * 【性能优化】提前验证格式支持，快速失败，减少无效MQ消息
     */
    @PostMapping("/srvFile")
    public ResponseEntity<Map<String, Object>> srvFileConvert(@RequestBody BaseConvertRequest request) {
        try {
            logger.info("接收到统一文件转换请求 - {}", request);

            // 验证基础参数
            if (!request.isValidBasicParams()) {
                return ReturnResponse.badRequest("基础参数不完整，请检查 fileId, filePath, targetFormat");
            }           
            
            // 使用工具类验证文件名和文件路径
            ValidateAndNormalized.ValidationResult validationResult = validator.validateFileNameAndPath(
                request.getFilePath(),
                request.getTargetFormat()
            );           
            if (!validationResult.isSuccess()) {
                return ReturnResponse.badRequest(validationResult.getMessage());
            }       
            // 处理中文编码
            EncodingUtils.processRequestEncoding(request);
            
            // 使用 FileEventFactory 创建转换事件（已完善所有字段）
            FileEvent fileEvent = eventFactory.createConvertEvent(request,"srv-convert");          
            //发送事件到MQ
            Map<String, Object> headers = new HashMap<>();
            headers.put("fileId", fileEvent.getFileId());
            headers.put("fileType", fileEvent.getFileType());
            headers.put("eventType", fileEvent.getEventType().name());
            
            eventPublisher.publish(EventChannel.FILE_EVENTS, fileEvent, headers);
            
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", request.getFileId());
            data.put("eventId", fileEvent.getFileId());
            data.put("sourceFormat", fileEvent.getSourceFormat());
            data.put("targetFormat", request.getTargetFormat());
            data.put("targetPath", fileEvent.getFullTargetPath());

            logger.info("本地转换事件发送成功 - ID: {}, {} -> {}",
                    request.getFileId(), fileEvent.getSourceFormat(), request.getTargetFormat());
            return ReturnResponse.success(data, "文件转换事件发送成功，正在异步处理中");

        } catch (Exception e) {
            logger.error("本地文件转换失败", e);
            return ReturnResponse.error("转换失败: " + e.getMessage());
        }
    }

}
