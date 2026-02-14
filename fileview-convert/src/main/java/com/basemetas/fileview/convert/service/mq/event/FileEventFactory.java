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
package com.basemetas.fileview.convert.service.mq.event;

import com.basemetas.fileview.convert.config.StorageConfig;
import com.basemetas.fileview.convert.config.FileTypeMapper;
import com.basemetas.fileview.convert.model.request.BaseConvertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.basemetas.fileview.convert.utils.FileUtils;
import java.io.File;

/**
 * 文件事件工厂类
 * 
 * 统一创建FileEvent对象，消除Controller层的重复代码
 * 
 * @author 夫子
 * @version 1.0
 */
@Component
public class FileEventFactory {
    @Autowired
    private FileTypeMapper fileTypeMapper;

    @Autowired
    private FileUtils fileUtils;
    
    @Autowired
    private StorageConfig storageConfig;

    private static final Logger logger = LoggerFactory.getLogger(FileEventFactory.class);

    /**
     * 从BaseConvertRequest创建FileEvent
     * 
     * 【性能优化】提前完善所有字段，避免Consumer阶段重复计算：
     * 1. fileType - 提前计算（避免Consumer第173-208行的重复逻辑）
     * 2. sourceFormat - 提取源文件扩展名
     * 3. fullTargetPath - 提前生成完整目标路径
     * 4. businessParams/convertParams - 透传参数
     * 
     * 【增强功能】自动处理目标文件名：如果targetFileName为空，则从源文件名提取
     * 
     * @param request       转换请求对象
     * @param sourceService 来源服务标识
     * @return FileEvent对象
     */
    public FileEvent createConvertEvent(BaseConvertRequest request, String sourceService) {
        if (request == null) {
            logger.error("创建FileEvent失败：request为空");
            return null;
        }
        FileEvent fileEvent = new FileEvent();
        String filePath = request.getFilePath();
        
        // 生成唯一的事件ID
        String fileId = fileUtils.generateFileId(request.getFileId(), request.getFileName(), filePath);
        fileEvent.setFileId(fileId);
        
        // 【性能优化1】提前提取源文件格式（扩展名）
        String sourceFormat = fileTypeMapper.extractExtension(filePath);
        fileEvent.setSourceFormat(sourceFormat);
        
        // 【性能优化2】提前计算fileType（避免Consumer重复计算）
        String fileType = fileTypeMapper.getStrategyType(sourceFormat);
        fileEvent.setFileType(fileType);
        
        // 设置文件路径信息
        fileEvent.setFilePath(filePath);
        
        // 【增强功能】如果目标路径为空，则使用默认配置的路径
        String targetPath = request.getTargetPath();
        if (targetPath == null || targetPath.trim().isEmpty()) {
            targetPath = storageConfig.getConvertTargetDir();
            logger.debug("目标路径为空，使用默认配置路径: {}", targetPath);
        }
        fileEvent.setTargetPath(targetPath);
        
        // 【增强功能】如果目标文件名为空，则从源文件名提取
        String targetFileName = request.getTargetFileName();
        if (targetFileName == null || targetFileName.trim().isEmpty()) {
            // 从源文件路径提取不带扩展名的文件名
            targetFileName = fileUtils.getFileNameWithoutExtension(new File(filePath).getName());
            logger.info("目标文件名为空，从源文件路径提取: {} -> {}", filePath, targetFileName);
        }
        fileEvent.setTargetFileName(targetFileName);       
        fileEvent.setTargetFormat(request.getTargetFormat());       
        // 【性能优化3】提前生成完整的目标文件路径（避免Consumer转换后再拼接）
        String fullTargetPath = fileUtils.buildFullTargetPath(
            targetPath,  // 使用处理后的目标路径
            targetFileName,  // 使用处理后的目标文件名
            request.getTargetFormat()
        );
        fileEvent.setFullTargetPath(fullTargetPath);    
        // 设置事件类型和来源
        fileEvent.setEventType(FileEvent.EventType.CONVERT_REQUESTED);
        fileEvent.setSourceService(sourceService != null ? sourceService : "convert-service");      
        logger.debug("创建转换事件 - EventID: {}, FileType: {}, SourceFormat: {}, SourceFile: {}, TargetFormat: {}, FullTargetPath: {}",
                fileId, fileType, sourceFormat, request.getFilePath(), request.getTargetFormat(), fullTargetPath);
        return fileEvent;
    }   
}