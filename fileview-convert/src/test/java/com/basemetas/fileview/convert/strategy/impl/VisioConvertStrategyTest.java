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
package com.basemetas.fileview.convert.strategy.impl;

import com.basemetas.fileview.convert.FileConvertApplication;
import com.basemetas.fileview.convert.strategy.FileConvertStrategy;
import com.basemetas.fileview.convert.strategy.FileConvertContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FileConvertApplication.class)
class VisioConvertStrategyTest {

    @Autowired
    private FileConvertContext fileConvertContext;

    @Test
    void testVisioStrategyRegistration() {
        // 测试Visio策略是否被正确注册
        FileConvertStrategy visioStrategy = fileConvertContext.getStrategy("visio");
        assertNotNull(visioStrategy, "Visio转换策略应该被注册");
        assertTrue(visioStrategy instanceof VisioConvertStrategy, "Visio策略应该是VisioConvertStrategy类型");
    }

    @Test
    void testVisioSupportedFormats() {
        FileConvertStrategy visioStrategy = fileConvertContext.getStrategy("visio");
        assertNotNull(visioStrategy);

        // 测试支持的源格式
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vsdx"), "应该支持vsdx格式");
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vsdm"), "应该支持vsdm格式");
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vssm"), "应该支持vssm格式");
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vssx"), "应该支持vssx格式");
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vstm"), "应该支持vstm格式");
        assertTrue(visioStrategy.getSupportedSourceFormats().contains("vstx"), "应该支持vstx格式");

        // 测试支持的目标格式
        assertTrue(visioStrategy.getSupportedTargetFormats().contains("pdf"), "应该支持pdf格式");
        assertTrue(visioStrategy.getSupportedTargetFormats().contains("png"), "应该支持png格式");
        assertTrue(visioStrategy.getSupportedTargetFormats().contains("jpg"), "应该支持jpg格式");
    }

    @Test
    void testVisioFormatSupport() {
        FileConvertStrategy visioStrategy = fileConvertContext.getStrategy("visio");
        assertNotNull(visioStrategy);

        // 测试格式支持检查
        assertTrue(visioStrategy.isConversionSupported("vsdx", "pdf"), "应该支持vsdx到pdf的转换");
        assertTrue(visioStrategy.isConversionSupported("vsdm", "png"), "应该支持vsdm到png的转换");
        assertTrue(visioStrategy.isConversionSupported("vssx", "jpg"), "应该支持vssx到jpg的转换");
    }
}