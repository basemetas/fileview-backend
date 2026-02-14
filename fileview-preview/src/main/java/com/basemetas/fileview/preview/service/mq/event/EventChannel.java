
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
package com.basemetas.fileview.preview.service.mq.event;

/**
 * 事件通道定义，用于抽象预览服务与其他服务之间的消息流向。
 */
public enum EventChannel {

    /**
     * 预览服务 -> 转换服务 的文件事件（原 file-events Topic）。
     */
    FILE_EVENTS,

    /**
     * 转换服务 -> 预览服务 的转换结果事件（原 preview-events Topic）。
     */
    PREVIEW_EVENTS,

    /**
     * 转换服务内部或其他服务使用的转换结果事件（原 convert-events Topic）。
     */
    CONVERT_EVENTS,

    /**
     * 预览服务 -> 下载子系统 的下载任务事件（原 download-tasks Topic）。
     */
    DOWNLOAD_TASKS
}
