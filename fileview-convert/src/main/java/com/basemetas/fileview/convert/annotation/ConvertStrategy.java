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
package com.basemetas.fileview.convert.annotation;
import com.basemetas.fileview.convert.config.FileCategory;
import org.springframework.stereotype.Component;
import java.lang.annotation.*;

/**
 * 文件转换策略注解
 * 
 * 用于标记和配置文件转换策略实现类
 * 自动注册策略到 FileConvertContext
 * 
 * 使用示例：
 * <pre>
 * @ConvertStrategy(category = FileCategory.IMAGE)
 * public class ImageConvertStrategy implements FileConvertStrategy {
 *     // 实现代码
 * }
 * </pre>
 * 
 * @author 夫子
 * @version 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ConvertStrategy {
    
    /**
     * 文件类别
     * 
     * 指定该策略处理的文件类别
     * 
     * @return 文件类别枚举
     */
    FileCategory category();
    
    /**
     * 策略名称（可选）
     * 
     * 默认使用 FileCategory 的 strategyType
     * 
     * @return 策略名称
     */
    String name() default "";
    
    /**
     * 策略描述（可选）
     * 
     * @return 策略描述信息
     */
    String description() default "";
    
    /**
     * 优先级（可选）
     * 
     * 数值越大优先级越高，默认为0
     * 用于同一类别有多个策略实现时的选择
     * 
     * @return 优先级数值
     */
    int priority() default 0;
}
