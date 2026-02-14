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
package com.basemetas.fileview.preview.service.storage;

import java.io.InputStream;

/**
 * 文件存储服务接口
 * 
 * 提供统一的文件存储抽象，支持多种存储后端：
 * - 本地存储（Local）
 * - 远程文件服务器（Remote）
 * - 对象存储（OSS/S3/MinIO）
 * - 网络文件系统（NFS/CIFS）
 * 
 * 使用策略模式，通过配置动态选择存储实现
 * 
 * @author 夫子
 */
public interface FileStorageService {
    
    /**
     * 获取文件的访问URL
     * 
     * @param filePath 文件逻辑路径（相对路径或标识符）
     * @return 可通过HTTP访问的完整URL
     */
    String getFileUrl(String filePath);
    
    /**
     * 获取文件的访问URL（带 fileId 参数，用于从 Redis 读取缓存的 baseUrl）
     * 
     * @param filePath 文件逻辑路径
     * @param fileId 文件ID（可选，用于读取缓存的 baseUrl）
     * @return 可通过HTTP访问的完整URL
     */
    default String getFileUrl(String filePath, String fileId) {
        // 默认实现：调用原有方法
        return getFileUrl(filePath);
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param filePath 文件逻辑路径
     * @return true表示文件存在，false表示不存在
     */
    boolean fileExists(String filePath);
    
    /**
     * 获取文件的物理路径（适用于本地存储）
     * 
     * @param filePath 文件逻辑路径
     * @return 文件在文件系统中的绝对路径，如果不支持则返回null
     */
    String getPhysicalPath(String filePath);
    
    /**
     * 获取文件输入流
     * 
     * @param filePath 文件逻辑路径
     * @return 文件输入流
     * @throws java.io.IOException 如果文件不存在或无法访问
     */
    InputStream getFileStream(String filePath) throws java.io.IOException;
    
    /**
     * 保存文件
     * 
     * @param filePath 文件逻辑路径
     * @param fileData 文件数据
     * @return 保存后的访问URL
     * @throws java.io.IOException 如果保存失败
     */
    String saveFile(String filePath, byte[] fileData) throws java.io.IOException;
    
    /**
     * 删除文件
     * 
     * @param filePath 文件逻辑路径
     * @return true表示删除成功，false表示失败
     */
    boolean deleteFile(String filePath);
    
    /**
     * 获取文件大小
     * 
     * @param filePath 文件逻辑路径
     * @return 文件大小（字节），如果文件不存在返回null
     */
    Long getFileSize(String filePath);
    
    /**
     * 获取存储类型
     * 
     * @return 存储类型标识（local/remote/oss/s3等）
     */
    String getStorageType();
}
