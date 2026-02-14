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
package com.basemetas.fileview.preview.service.mq.producer;

import com.basemetas.fileview.preview.service.mq.event.EventChannel;
import java.util.Map;

/**
 * 统一事件发布接口，屏蔽底层RocketMQ / Redis Streams等实现差异。
 */
public interface EventPublisher {

    /**
     * 同步发布事件。
     *
     * @param channel 业务事件通道
     * @param payload 事件载荷（领域对象或统一事件结构）
     * @param headers 事件头信息（用于Topic/Tag路由及诊断）
     */
    void publish(EventChannel channel, Object payload, Map<String, Object> headers);

    /**
     * 异步发布事件，默认退化为同步实现。
     */
    default void publishAsync(EventChannel channel, Object payload, Map<String, Object> headers) {
        publish(channel, payload, headers);
    }
}
