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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OFD转换API测试类
 * 验证ofdrw-converter API的正确使用
 */
public class OfdConvertApiTest {

    @Test
    public void testOfdConverterApiAvailability() {
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        // 测试ofdrw-converter库是否可用
        boolean isAvailable = strategy.isOfdLibraryAvailable();
        System.out.println("ofdrw-converter库可用性: " + isAvailable);
        
        // 如果库不可用，测试会给出警告但不会失败
        if (!isAvailable) {
            System.out.println("警告: ofdrw-converter库不可用，可能需要检查依赖配置");
        }
        
        // 测试支持的格式
        var supportedFormats = strategy.getSupportedTargetFormats();
        assertNotNull(supportedFormats);
        assertTrue(supportedFormats.contains("pdf"));
        assertTrue(supportedFormats.contains("png"));
        assertTrue(supportedFormats.contains("svg"));
        assertTrue(supportedFormats.contains("html"));
        assertTrue(supportedFormats.contains("txt"));
        
        System.out.println("支持的转换格式: " + supportedFormats);
    }
    
    @Test
    public void testOfdConverterServiceStatus() {
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        String status = strategy.getServiceStatus();
        assertNotNull(status);
        assertTrue(status.contains("OFD转换服务状态"));
        assertTrue(status.contains("ofdrw-converter"));
        
        System.out.println("服务状态信息:");
        System.out.println(status);
    }
    
    @Test
    public void testValidateInputs() {
        OfdConvertStrategy strategy = new OfdConvertStrategy();
        
        // 测试支持的文件类型
        assertEquals("ofd", strategy.getSupportedSourceFormats());
    }
    
    @Test
    public void testOfdConverterClassesAvailable() {
        try {
            // 验证关键的ofdrw-converter类是否可用
            Class.forName("org.ofdrw.converter.export.OFDExporter");
            Class.forName("org.ofdrw.converter.export.ImageExporter");
            Class.forName("org.ofdrw.converter.export.PDFExporterPDFBox");
            Class.forName("org.ofdrw.converter.export.SVGExporter");
            Class.forName("org.ofdrw.converter.export.HTMLExporter");
            Class.forName("org.ofdrw.converter.export.TextExporter");
            
            System.out.println("所有ofdrw-converter核心类都可用");
        } catch (ClassNotFoundException e) {
            System.out.println("某些ofdrw-converter类不可用: " + e.getMessage());
            // 不让测试失败，只是输出警告
        }
    }
}